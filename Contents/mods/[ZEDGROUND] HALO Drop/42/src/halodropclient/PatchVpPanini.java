package halodropclient;

import me.zed_0xff.zombie_buddy.Patch;

/**
 * Strength of Viewpoint's curved (Panini) projection: viewpoint.render.Panini.strength() (javap names only).
 * Raised during a Halo Drop freefall, the player's setting itself is not touched: see Lens.
 */
@Patch(className = "viewpoint.render.Panini", methodName = "strength")
public class PatchVpPanini {
    @Patch.OnExit
    public static void exit(@Patch.Return(readOnly = false) float ret) {
        ret = Lens.panini(ret);
    }
}
