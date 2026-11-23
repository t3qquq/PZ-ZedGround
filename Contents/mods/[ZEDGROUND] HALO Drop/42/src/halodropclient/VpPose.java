package halodropclient;

import java.lang.reflect.Method;

/**
 * Halo Drop: one arm sticking straight out during the dive in Viewpoint 1st/3rd person (run 11-19, never in isometric).
 *
 * Why: after vanilla poses a skeleton, Viewpoint turns bones on top of it for the camera: viewpoint.input.HeadLook.posed
 *   turns the neck, the head and the bones below them toward where the camera looks, and viewpoint.input.ArmsPitch.posed
 *   pitches the arm chains from the clavicles (class and member names checked with javap only). In the vanilla skeleton
 *   both clavicles hang from the neck, and Viewpoint works the turn out for an upright character. The Halo Drop poses
 *   lay the body flat or stand it on its head, so that turn swings the arms around the neck: one arm ends up straight
 *   out to the side, the other across the body. The clips themselves are left/right symmetric.
 * What: while the character has the anim variable HaloDropPose (freefall and canopy, set by HaloDropPose.lua), both
 *   methods are skipped, so the skeleton stays exactly as the Halo Drop clips posed it. Everyone else is untouched.
 * If Viewpoint is not installed the target classes never load and the patches do nothing. If Viewpoint renames the
 * methods, ZombieBuddy logs that the patch found nothing and the arm problem comes back (nothing else breaks).
 * Public for the same reason as SkyLight: ZombieBuddy inlines the advice into Viewpoint's class.
 *
 * Canopy bone probe (11-23, diagnostic): the canopy swing test clips move the root bone. Video alone could not tell
 *   whether the body really moved, so while the pose is "canopy" this logs every PROBE_MS the model-space position
 *   (AnimationPlayer.getModelTransformAt(i).m03/m13/m23, relative to the character, not yet turned to its facing) of
 *   Bip01 / Bip01_Head / Bip01_Pelvis / Bip01_L_Foot. Compare with the "canopy" HaloDropPose blend log (Lua) of the
 *   same moment: blend x -1 = A turn clip, +1 = D turn clip, 0 = centre clip.
 */
public final class VpPose {
    static final String TAG = "[HaloDrop][VpPose] ";
    static final String VAR = "HaloDropPose";

    private static boolean resolved, broken;
    private static Method mChar, mGetVar;
    private static int errors;
    private static final boolean[] last = new boolean[2];
    private static final boolean[] seen = new boolean[2];
    private static final int[] skipped = new int[2];
    private static final String[] NAME = { "HeadLook.posed", "ArmsPitch.posed" };

    static final long PROBE_MS = 250L;
    static final String[] PROBE_BONES = { "Bip01", "Bip01_Head", "Bip01_Pelvis", "Bip01_L_Foot" };
    private static boolean probeResolved, probeBroken;
    private static Method mBoneIndex, mModelAt;
    private static java.lang.reflect.Field f03, f13, f23;
    private static final int[] probeIdx = new int[PROBE_BONES.length];
    private static long nextProbe;
    private static int probeLines, probeErrors;

    private VpPose() {}

    /** Logs the canopy bone positions at most every PROBE_MS. Never throws; stops itself after repeated errors. */
    private static void probe(Object animationPlayer) {
        if (probeBroken) return;
        long now = System.currentTimeMillis();
        if (now < nextProbe) return;
        nextProbe = now + PROBE_MS;
        try {
            if (!probeResolved) {
                Class<?> c = animationPlayer.getClass();
                mBoneIndex = c.getMethod("getSkinningBoneIndex", String.class, int.class);
                mModelAt = c.getMethod("getModelTransformAt", int.class);
                for (int i = 0; i < PROBE_BONES.length; i++) {
                    probeIdx[i] = ((Number) mBoneIndex.invoke(animationPlayer, PROBE_BONES[i], -1)).intValue();
                }
                Object m = mModelAt.invoke(animationPlayer, probeIdx[0] < 0 ? 0 : probeIdx[0]);
                f03 = m.getClass().getField("m03");
                f13 = m.getClass().getField("m13");
                f23 = m.getClass().getField("m23");
                probeResolved = true;
                log("canopy probe ready: bones " + PROBE_BONES[0] + "=" + probeIdx[0] + " " + PROBE_BONES[1] + "=" + probeIdx[1]
                        + " " + PROBE_BONES[2] + "=" + probeIdx[2] + " " + PROBE_BONES[3] + "=" + probeIdx[3]
                        + " matrix=" + m.getClass().getName());
            }
            StringBuilder sb = new StringBuilder("canopy bones (model space x,y,z):");
            for (int i = 0; i < PROBE_BONES.length; i++) {
                if (probeIdx[i] < 0) { sb.append(' ').append(PROBE_BONES[i]).append("=missing"); continue; }
                Object m = mModelAt.invoke(animationPlayer, probeIdx[i]);
                sb.append(' ').append(PROBE_BONES[i]).append(String.format("=(%.3f,%.3f,%.3f)",
                        f03.getFloat(m), f13.getFloat(m), f23.getFloat(m)));
            }
            probeLines++;
            log(sb.toString());
        } catch (Throwable t) {
            probeErrors++;
            if (probeErrors <= 3) log("canopy probe failed (" + probeErrors + "): " + t);
            if (!probeResolved || probeErrors >= 10) {
                probeBroken = true;
                log("canopy probe off");
            }
        }
    }

    public static void log(String s) { System.out.println(TAG + s); }

    /** true = skip Viewpoint's bone turn for this skeleton. which: 0 head look, 1 arms pitch */
    public static boolean skip(Object animationPlayer, int which) {
        if (broken || animationPlayer == null) return false;
        try {
            if (!resolved) {
                ClassLoader cl = animationPlayer.getClass().getClassLoader();
                mChar = animationPlayer.getClass().getMethod("getIsoGameCharacter");
                mGetVar = Class.forName("zombie.characters.IsoGameCharacter", false, cl).getMethod("getVariableString", String.class);
                resolved = true;
                log("members resolved (AnimationPlayer.getIsoGameCharacter, IsoGameCharacter.getVariableString)");
            }
            if (!seen[which]) {
                seen[which] = true;
                log("patch reached: viewpoint.input." + NAME[which]);
            }
            Object chr = mChar.invoke(animationPlayer);
            if (chr == null) return false;
            Object v = mGetVar.invoke(chr, VAR);
            boolean on = v instanceof String && !((String) v).isEmpty();
            if (on) skipped[which]++;
            if (on && which == 0 && "canopy".equals(v)) probe(animationPlayer);
            if (on != last[which]) {
                last[which] = on;
                if (on) {
                    skipped[which] = 1;
                    log(NAME[which] + " skipped from now on (pose=" + v + ")");
                } else {
                    log(NAME[which] + " back to Viewpoint (skipped " + skipped[which] + " times)"
                            + (which == 0 ? ", canopy probe lines so far " + probeLines : ""));
                }
            }
            return on;
        } catch (Throwable t) {
            errors++;
            if (errors <= 5) log("skip check failed (" + errors + "): " + t);
            if (!resolved || errors >= 20) {
                broken = true;
                log("off: Viewpoint's bone turns are no longer skipped");
            }
            return false;
        }
    }
}
