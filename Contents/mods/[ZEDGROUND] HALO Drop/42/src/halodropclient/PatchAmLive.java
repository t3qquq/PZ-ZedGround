package halodropclient;

import me.zed_0xff.zombie_buddy.Patch;

/**
 * Viewpoint Advanced Movement decides each frame whether it takes input:
 * com.viewpointmovement.core.InputController.computeLive(IsoPlayer), private static boolean (javap names only, AM 1.4.18).
 * Answered false during a Halo Drop (aboard to landing): see AmGate.
 * If Advanced Movement is not installed the target class never loads and this patch does nothing.
 */
@Patch(className = "com.viewpointmovement.core.InputController", methodName = "computeLive")
public class PatchAmLive {
    @Patch.OnExit
    public static void exit(@Patch.Return(readOnly = false) boolean ret) {
        ret = AmGate.live(ret);
    }
}
