package halodropclient;

import me.zed_0xff.zombie_buddy.Patch;

/**
 * Draws the jump plane past the point where the real vehicle is removed (GhostPlane), in Viewpoint's own pass:
 * viewpoint.render.AddonDraws.afterTranslucent(FrameContext) - the same point Blue Zone's wall uses (javap, jar of 11-15).
 * If Viewpoint is not installed the target class never loads and this patch does nothing.
 */
@Patch(className = "viewpoint.render.AddonDraws", methodName = "afterTranslucent")
public class PatchVpGhost {
    @Patch.OnExit
    public static void exit(@Patch.Argument(0) Object frame) {
        GhostPlane.afterTranslucent(frame);
    }
}
