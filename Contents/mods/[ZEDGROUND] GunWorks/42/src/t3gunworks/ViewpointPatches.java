package t3gunworks;

import me.zed_0xff.zombie_buddy.Patch;

/**
 * Viewpoint hooks (verified in Viewpoint 0.1.5a and 0.1.6). Viewpoint is client-only; on a dedicated server
 * these classes do not exist and ZombieBuddy's type matcher simply never matches them.
 *
 *   viewpoint.input.CrosshairAim.snapshot(Frame, IsoCell, IsoGameCharacter)          (0.1.5a)
 *   viewpoint.input.CrosshairAim.snapshot(Frame, IsoGameCharacter, FarRelief$Lift)   (0.1.6)
 *       - main thread, once per drawn frame, right after Viewpoint placed the eye and third-person camera;
 *         the camera position used for the aim ray is read here. Both layouts have three arguments; the
 *         character is whichever of arguments 1 and 2 is the local player.
 *   viewpoint.gunplay.Gunplay.active(IsoGameCharacter, HandWeapon)                    (0.1.6)
 *       - Viewpoint's "Gunplay Rework" decides here whether it drives the aim (crosshair sway, aim ring, its
 *         own instant hit test). Forced false for firearms True Ballistics simulates.
 *   viewpoint.gunplay.Gunplay.snapshot()                                               (0.1.6)
 *       - main thread, once per frame right after CrosshairAim.snapshot; the crosshair's target colour is
 *         set from True Ballistics' aim ray here (t3gunworks.aiming.CrosshairOverride)
 *   viewpoint.gunplay.Gunplay.reticle(IsoReticle)                                      (0.1.6)
 *       - sizes the aim ring; it shows True Ballistics' dispersion instead
 *   viewpoint.gunplay.Shot.begin(IsoGameCharacter, HandWeapon, double)                (0.1.6)
 *       - Viewpoint's Gunplay Rework resolving a shot instantly (hit list, window smashing). Skipped for shots
 *         True Ballistics simulates, so the projectile alone decides what is hit; Viewpoint's crosshair and
 *         aim ring keep working and its own shot resolution still runs whenever True Ballistics does not.
 *   viewpoint.render.WorldRenderer.drawWeather(SceneData, float, float, float)
 *       - render thread, inside Viewpoint's 3D pass; debug lines and tracer rounds are drawn here
 */
public final class ViewpointPatches {

    @Patch(className = "viewpoint.input.CrosshairAim", methodName = "snapshot")
    public static class Snapshot {
        @Patch.OnExit
        public static void exit(@Patch.Argument(0) Object frame, @Patch.Argument(1) Object second, @Patch.Argument(2) Object third) {
            Hooks.viewpointSnapshot(frame, second, third);
        }
    }

    @Patch(className = "viewpoint.gunplay.Gunplay", methodName = "active")
    public static class GunplayActive {
        @Patch.OnExit
        public static void exit(@Patch.Argument(0) Object owner, @Patch.Argument(1) Object weapon,
                                @Patch.Return(readOnly = false) boolean active) {
            if (active) active = !Hooks.overrideGunplay(owner, weapon);
        }
    }

    @Patch(className = "viewpoint.gunplay.Gunplay", methodName = "snapshot")
    public static class GunplaySnapshot {
        @Patch.OnExit
        public static void exit() {
            Hooks.gunplayFrame();
        }
    }

    @Patch(className = "viewpoint.gunplay.Gunplay", methodName = "reticle")
    public static class GunplayReticle {
        @Patch.OnExit
        public static void exit(@Patch.Argument(0) Object reticle, @Patch.Return(readOnly = false) boolean skip) {
            skip = Hooks.gunplayReticle(reticle, skip);
        }
    }

    @Patch(className = "viewpoint.gunplay.Shot", methodName = "begin")
    public static class GunplayShot {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter(@Patch.Argument(0) Object owner, @Patch.Argument(1) Object weapon) {
            return Hooks.skipViewpointShot(owner, weapon);
        }
    }

    @Patch(className = "viewpoint.render.WorldRenderer", methodName = "drawWeather")
    public static class Draw {
        @Patch.OnExit
        public static void exit(@Patch.Argument(0) Object scene) {
            Hooks.viewpointDraw(scene);
        }
    }

    private ViewpointPatches() {}
}
