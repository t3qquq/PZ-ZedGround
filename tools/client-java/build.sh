#!/usr/bin/env bash
# Builds the optional client Java part of Halo Drop (loaded by ZombieBuddy when the player has it).
#   source : Contents/mods/[ZEDGROUND] HALO Drop/42/src/halodropclient/*.java (+ resources: *.mesh, *.tex in the same folder)
#   output : Contents/mods/[ZEDGROUND] HALO Drop/42/media/java/client/HaloDropClient.jar
# Needs a JDK 21+ (javac, jar, javap). Compiles against the compile-only stubs ./stubs/Patch.java (ZombieBuddy's legacy
# @Patch) and ./stubs/GL33.java (LWJGL), and Viewpoint's public API (viewpoint.api.*, GhostPlane since 0.6.0).
# None of them is shipped. Other game and Viewpoint classes are reached by reflection.
# Usage: tools/client-java/build.sh <path to Viewpoint.jar>
#   e.g. "/e/Steam/steamapps/workshop/content/108600/3809306528/mods/Viewpoint/42/media/java/client/Viewpoint.jar"
# GhostPlane's mesh/texture (c17.mesh, c17.tex) are made by tools/client-java/ghost_mesh.py from the C-17 fbx/png.
set -euo pipefail
vp="${1:?path to Viewpoint.jar}"
here="$(cd "$(dirname "$0")" && pwd)"
repo="$(cd "$here/../.." && pwd)"
mod="$repo/Contents/mods/[ZEDGROUND] HALO Drop/42"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/stubs" "$tmp/classes" "$mod/media/java/client"
javac --release 21 -d "$tmp/stubs" "$here/stubs/Patch.java" "$here/stubs/GL33.java"
javac --release 21 -Xlint:all -cp "$tmp/stubs:$vp" -d "$tmp/classes" "$mod/src/halodropclient/"*.java
for r in "$mod/src/halodropclient/"*.mesh "$mod/src/halodropclient/"*.tex; do if [ -e "$r" ]; then cp "$r" "$tmp/classes/halodropclient/"; fi; done

# ZombieBuddy inlines @Patch advice into the target class (another package), so every class and method the
# advice calls must be public (0.1.0 broke on this at runtime: IllegalAccessError on every character render).
fail=0
for pc in "$tmp/classes/halodropclient/"Patch*.class; do
    name="halodropclient.$(basename "$pc" .class)"
    while read -r owner meth; do
        cls="${owner//\//.}"
        javap -cp "$tmp/classes" "$cls" | grep -q "^public .*class " || { echo "NOT PUBLIC class $cls (called from $name)"; fail=1; }
        javap -cp "$tmp/classes" "$cls" | grep -qE "^  public static .* $meth\(" || { echo "NOT PUBLIC method $cls.$meth (called from $name)"; fail=1; }
    done < <(javap -c -cp "$tmp/classes" "$name" | sed -n 's/.*invokestatic .*Method \(halodropclient\/[A-Za-z0-9_$]*\)\.\([A-Za-z0-9_]*\):.*/\1 \2/p' | sort -u)
done
[ "$fail" = 0 ] || { echo "build stopped: advice calls non-public members"; exit 1; }

ver="$(sed -n 's/.*VERSION = "\(.*\)".*/\1/p' "$mod/src/halodropclient/Main.java")"
printf 'Implementation-Title: Halo Drop client\nImplementation-Version: %s\n' "$ver" > "$tmp/manifest.txt"
jar --create --file "$mod/media/java/client/HaloDropClient.jar" --manifest "$tmp/manifest.txt" -C "$tmp/classes" .
echo "built HaloDropClient.jar $ver"
jar --list --file "$mod/media/java/client/HaloDropClient.jar"
