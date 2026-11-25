package halodropclient;

import me.zed_0xff.zombie_buddy.Patch;

/**
 * Viewpoint Advanced Movement turns bones after every skeleton is posed:
 * com.viewpointmovement.core.PoseApplier.posed(AnimationPlayer), public static void (javap names only, AM 1.4.18).
 * Skipped while the character holds a Halo Drop pose (freefall, canopy): see VpPose and AmGate.
 * If Advanced Movement is not installed the target class never loads and this patch does nothing.
 */
@Patch(className = "com.viewpointmovement.core.PoseApplier", methodName = "posed")
public class PatchAmPose {
    @Patch.OnEnter(skipOn = true)
    public static boolean enter(@Patch.Argument(0) Object animationPlayer) {
        return VpPose.skip(animationPlayer, 2);
    }
}
