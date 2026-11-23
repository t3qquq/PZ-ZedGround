package halodropclient;

import me.zed_0xff.zombie_buddy.Patch;

/**
 * Viewpoint's vertical field of view for the current view: viewpoint.core.View.fovY(boolean) (javap names only).
 * Widened during a Halo Drop freefall, the player's setting itself is not touched: see Lens.
 */
@Patch(className = "viewpoint.core.View", methodName = "fovY")
public class PatchVpFov {
    @Patch.OnExit
    public static void exit(@Patch.Return(readOnly = false) float ret) {
        ret = Lens.fov(ret);
    }
}
