package t3zedmanager;

import me.zed_0xff.zombie_buddy.Patch;

/**
 * viewpoint.FP.renderWorld(IsoCell) is asked every frame whether Viewpoint draws the world, in the isometric view too.
 * Second place the hold value is written from, so the heartbeat to Lua keeps going even if ViewHold.frame is not
 * called while the view is off.
 */
@Patch(className = "viewpoint.FP", methodName = "renderWorld")
public class PatchVpRender {
    @Patch.OnEnter
    public static void enter() {
        ViewLock.frame("render");
    }
}
