#!/usr/bin/env bash
# Builds the client Java part of Round Manager (the view lock: Viewpoint held in 1st/3rd person, no isometric view).
#   source : Contents/mods/[ZEDGROUND] Round Manager/42/src/t3roundview/*.java
#   output : Contents/mods/[ZEDGROUND] Round Manager/42/media/java/client/RoundManagerView.jar
# Loaded by ZombieBuddy at server join (mod.info javaJarFile / javaPkgName), like the Blue Zone wall jar.
# Reaches the game and Viewpoint by reflection only, so it compiles against nothing but the compile-only stub
# ./stubs/Patch.java (ZombieBuddy).
# Usage: tools/client-java/build_roundmanager_view.sh
# Needs a JDK 21+.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
repo="$(cd "$here/../.." && pwd)"
mod="$repo/Contents/mods/[ZEDGROUND] Round Manager/42"
out="$mod/media/java/client"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/stubs" "$tmp/classes" "$out"
javac --release 21 -d "$tmp/stubs" "$here/stubs/Patch.java"
javac --release 21 -Xlint:all -cp "$tmp/stubs" -d "$tmp/classes" "$mod/src/t3roundview/"*.java

# ZombieBuddy inlines @Patch advice into the target class (another package): what the advice calls must be public
fail=0
for pc in "$tmp/classes/t3roundview/"Patch*.class; do
    name="t3roundview.$(basename "$pc" .class)"
    while read -r owner meth; do
        cls="${owner//\//.}"
        javap -cp "$tmp/classes" "$cls" | grep -q "^public .*class " || { echo "NOT PUBLIC class $cls (called from $name)"; fail=1; }
        javap -cp "$tmp/classes" "$cls" | grep -qE "^  public static .* $meth\(" || { echo "NOT PUBLIC method $cls.$meth (called from $name)"; fail=1; }
    done < <(javap -c -cp "$tmp/classes" "$name" | sed -n 's/.*invokestatic .*Method \(t3roundview\/[A-Za-z0-9_$]*\)\.\([A-Za-z0-9_]*\):.*/\1 \2/p' | sort -u)
done
[ "$fail" = 0 ] || { echo "build stopped: advice calls non-public members"; exit 1; }

ver="$(sed -n 's/.*VERSION = "\(.*\)".*/\1/p' "$mod/src/t3roundview/Main.java")"
printf 'Implementation-Title: ZEDGROUND Round Manager View Lock\nImplementation-Version: %s\n' "$ver" > "$tmp/manifest.txt"
jar --create --file "$out/RoundManagerView.jar" --manifest "$tmp/manifest.txt" -C "$tmp/classes" .
echo "built RoundManagerView.jar $ver"
jar --list --file "$out/RoundManagerView.jar"
