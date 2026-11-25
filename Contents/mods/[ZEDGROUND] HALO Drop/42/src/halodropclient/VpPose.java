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
 *   11-25: the same condition also skips Viewpoint Advanced Movement's bone pass (PatchAmPose, which 2): its own head
 *   follow and jump tuck were laid over the drop poses. See AmGate.
 * If Viewpoint is not installed the target classes never load and the patches do nothing. If Viewpoint renames the
 * methods, ZombieBuddy logs that the patch found nothing and the arm problem comes back (nothing else breaks).
 * Public for the same reason as SkyLight: ZombieBuddy inlines the advice into Viewpoint's class.
 *
 * Logs (11-25): skip on/off is logged only for the local player. Before, every character Viewpoint posed flipped the
 *   state, so the on/off lines were printed ~4400 times a run. The canopy bone probe (11-23) is gone.
 */
public final class VpPose {
    static final String TAG = "[HaloDrop][VpPose] ";
    static final String VAR = "HaloDropPose";

    private static boolean resolved, broken;
    private static Method mChar, mGetVar;
    private static int errors;
    private static final boolean[] last = new boolean[3];
    private static final boolean[] seen = new boolean[3];
    private static final int[] skipped = new int[3];
    private static final String[] NAME = { "viewpoint.input.HeadLook.posed", "viewpoint.input.ArmsPitch.posed",
            "com.viewpointmovement.core.PoseApplier.posed (Advanced Movement)" };

    private static Method mLocal;

    private VpPose() {}

    public static void log(String s) { System.out.println(TAG + s); }

    /** true = skip the bone turn for this skeleton. which: 0 Viewpoint head look, 1 Viewpoint arms pitch, 2 Advanced Movement */
    public static boolean skip(Object animationPlayer, int which) {
        if (broken || animationPlayer == null) return false;
        try {
            if (!resolved) {
                ClassLoader cl = animationPlayer.getClass().getClassLoader();
                mChar = animationPlayer.getClass().getMethod("getIsoGameCharacter");
                mGetVar = Class.forName("zombie.characters.IsoGameCharacter", false, cl).getMethod("getVariableString", String.class);
                mLocal = Class.forName("zombie.characters.IsoPlayer", false, cl).getMethod("getInstance");
                resolved = true;
                log("members resolved (AnimationPlayer.getIsoGameCharacter, IsoGameCharacter.getVariableString)");
            }
            if (!seen[which]) {
                seen[which] = true;
                log("patch reached: " + NAME[which]);
            }
            Object chr = mChar.invoke(animationPlayer);
            if (chr == null) return false;
            Object v = mGetVar.invoke(chr, VAR);
            boolean on = v instanceof String && !((String) v).isEmpty();
            if ((on || last[which]) && chr == mLocal.invoke(null)) {   // no lookup for characters outside a Halo Drop pose
                if (on) skipped[which]++;
                if (on != last[which]) {
                    last[which] = on;
                    if (on) {
                        skipped[which] = 1;
                        log(NAME[which] + " skipped for the local player from now on (pose=" + v + ")");
                    } else {
                        log(NAME[which] + " back for the local player (skipped " + skipped[which] + " times)");
                    }
                }
            }
            return on;
        } catch (Throwable t) {
            errors++;
            if (errors <= 5) log("skip check failed (" + errors + "): " + t);
            if (!resolved || errors >= 20) {
                broken = true;
                log("off: Viewpoint's and Advanced Movement's bone turns are no longer skipped");
            }
            return false;
        }
    }
}
