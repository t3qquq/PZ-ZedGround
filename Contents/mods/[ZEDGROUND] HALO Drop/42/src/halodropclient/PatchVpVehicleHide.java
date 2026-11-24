package halodropclient;

import me.zed_0xff.zombie_buddy.Patch;

/**
 * Hides the real jump plane in Viewpoint once GhostPlane takes over (0.6.1): skips
 * viewpoint.models.VehicleCapture.capture(Frame, ModelSlotRenderData, BaseVehicle) (static boolean, javap names only)
 * for a C-17 next to the ghost start, so the two are not drawn together until the server's removal arrives.
 * A skipped capture returns false (not captured), the same as for any vehicle Viewpoint does not draw.
 * If Viewpoint is not installed the target class never loads and this patch does nothing.
 */
@Patch(className = "viewpoint.models.VehicleCapture", methodName = "capture")
public class PatchVpVehicleHide {
    @Patch.OnEnter(skipOn = true)
    public static boolean enter(@Patch.Argument(2) Object vehicle) {
        return GhostPlane.hideVehicle(vehicle);
    }
}
