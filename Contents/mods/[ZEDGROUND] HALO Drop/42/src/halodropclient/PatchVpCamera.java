package halodropclient;

import me.zed_0xff.zombie_buddy.Patch;

/**
 * Viewpoint adds its addon camera layers to the frame's shot in viewpoint.camera.AddonLayers.add(CameraShot)
 * (javap names only). Right after it, a Halo Drop freefall pulls the third-person camera closer: see Lens.
 */
@Patch(className = "viewpoint.camera.AddonLayers", methodName = "add")
public class PatchVpCamera {
    @Patch.OnExit
    public static void exit(@Patch.Argument(0) Object shot) {
        Lens.shot(shot);
    }
}
