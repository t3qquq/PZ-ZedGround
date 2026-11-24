package halodropclient;

import me.zed_0xff.zombie_buddy.Patch;

/**
 * Measurement only (0.6.3): after Viewpoint places a vehicle for drawing -
 * viewpoint.models.VehicleCapture.placed(Frame, ModelSlotRenderData, BaseVehicle), private static void (javap names
 * only) - record where it put a Halo Drop C-17, to compare with the physics origin the ghost plane starts from.
 * Changes nothing. If Viewpoint is not installed the target class never loads and this patch does nothing.
 */
@Patch(className = "viewpoint.models.VehicleCapture", methodName = "placed")
public class PatchVpVehiclePlaced {
    @Patch.OnExit
    public static void exit(@Patch.Argument(0) Object frame, @Patch.Argument(2) Object vehicle) {
        PlaceProbe.placed(frame, vehicle);
    }
}
