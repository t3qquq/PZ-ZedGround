package t3gunworks;

import me.zed_0xff.zombie_buddy.Patch;
import me.zed_0xff.zombie_buddy.Patch.Argument;
import me.zed_0xff.zombie_buddy.Patch.OnEnter;
import me.zed_0xff.zombie_buddy.Patch.OnExit;
import me.zed_0xff.zombie_buddy.Patch.Return;
import me.zed_0xff.zombie_buddy.Patch.This;
import t3gunworks.ads.*;

/**
 * Project Viewpoint: Recoil &amp; ADS 1.9.1 hooks, absorbed into ZEDGROUND GunWorks (code in t3gunworks.ads).
 * This jar also loads on dedicated servers, where Viewpoint and the ADS code must never run: every advice on a
 * game class returns before touching t3gunworks.ads when GameServer.server is set (the JVM only loads a
 * class when the instruction using it runs). Advice on viewpoint.* classes never matches on a server.
 */
public final class AdsPatches {
    private AdsPatches() {
    }

    @Patch(className = "org.lwjglx.input.Mouse", methodName = "addButtonEvent")
    public static class AimButton {
        @OnEnter
        public static void enter(@Argument(0) int var0, @Argument(1) boolean var1) {
            if (zombie.network.GameServer.server) return;
            AimClick.button(var0, var1);
        }
    }

    @Patch(className = "zombie.characters.component.CharacterInputComponent", methodName = "isForceAim")
    public static class AimLock {
        @OnExit
        public static void exit(@This Object var0, @Return(readOnly = false) boolean var1) {
            if (!zombie.network.GameServer.server) var1 = AimClick.forceAim(var0, var1);
        }
    }

    @Patch(className = "viewpoint.input.CrosshairAim", methodName = "snapshot")
    public static class AimSnapshot {
        @OnEnter
        public static void enter(@Argument(1) Object var0) {
            AimPick.beforeSnapshot(var0);
        }
    }

    @Patch(className = "viewpoint.models.ModelCapture", methodName = "capture")
    public static class CaptureRefused {
        @OnEnter
        public static void enter(@Argument(1) Object var0) {
            SkipRefused.enter(var0);
        }

        @OnExit
        public static void exit() {
            SkipRefused.exit();
        }
    }

    // Viewpoint (current): the dot is drawn by OverWorldDrawer.draw(); SceneDrawer.drawCrosshair no longer exists and
    // SceneDrawer.crosshair(...) only computes the crosshair / ring positions and MousePick's aim shift (must keep running).
    @Patch(className = "viewpoint.OverWorldDrawer", methodName = "draw")
    public static class Crosshair {
        @OnEnter(skipOn = true)
        public static boolean enter() {
            return ADS.hideCrosshair();
        }
    }

    @Patch(className = "viewpoint.input.Camera", methodName = "eye")
    public static class Eye {
        @OnExit
        public static void exit(@Argument(0) Object var0, @Argument(2) float[] var1) {
            ADS.onEye(var0, var1);
        }
    }

    @Patch(className = "viewpoint.input.EyeMotion", methodName = "update")
    public static class EyeMotionUpdate {
        @OnEnter
        public static void enter(@Argument(value = 5, readOnly = false) boolean var0, @Argument(value = 6, readOnly = false) float var1) {
            var0 = VpCompat.steadyEye(var0);
            var1 = VpCompat.headFollow(var1);
        }

        @OnExit
        public static void exit(@This Object var0) {
            EyeTap.updated(var0);
        }
    }

    @Patch(className = "viewpoint.input.Eye", methodName = "place")
    public static class EyePlace {
        @OnExit
        public static void exit(@Argument(0) Object var0, @Argument(1) Object var1) {
            VpCompat.eyePlaced(var0, var1);
        }
    }

    @Patch(className = "zombie.CombatManager", methodName = "fireWeapon")
    public static class Fire {
        @OnEnter
        public static void enter(@Argument(1) Object var0) {
            if (zombie.network.GameServer.server) return;
            ADS.onFire(var0);
        }
    }

    @Patch(className = "viewpoint.core.View", methodName = "fovY")
    public static class Fov {
        @OnExit
        public static void exit(@Argument(0) boolean var0, @Return(readOnly = false) float var1) {
            var1 = ADS.fov(var1, var0);
        }
    }

    @Patch(className = "zombie.ai.states.SwipeStatePlayer", methodName = "OnAnimEvent_AttackCollisionCheck")
    public static class HipFire {
        @OnEnter
        public static void enter(@Argument(0) Object var0) {
            if (zombie.network.GameServer.server) return;
            HipSpread.enter(var0);
        }

        @OnExit
        public static void exit() {
            if (zombie.network.GameServer.server) return;
            HipSpread.exit();
        }
    }

    @Patch(className = "zombie.core.physics.BallisticsController", methodName = "calculateMuzzlePosition")
    public static class LeanMuzzle {
        @OnExit
        public static void exit(@This Object var0, @Argument(0) Object var1, @Argument(1) Object var2) {
            if (zombie.network.GameServer.server) return;
            Presence.afterMuzzle(var0, var1, var2);
            LeanShot.afterMuzzle(var0, var1);
        }
    }

    @Patch(className = "zombie.characters.IsoGameCharacter", methodName = "getAimOriginPosX")
    public static class LeanOriginX {
        @OnExit
        public static void exit(@This Object var0, @Return(readOnly = false) float var1) {
            if (!zombie.network.GameServer.server) var1 = LeanShot.originX(var0, var1);
        }
    }

    @Patch(className = "zombie.characters.IsoGameCharacter", methodName = "getAimOriginPosY")
    public static class LeanOriginY {
        @OnExit
        public static void exit(@This Object var0, @Return(readOnly = false) float var1) {
            if (!zombie.network.GameServer.server) var1 = LeanShot.originY(var0, var1);
        }
    }

    @Patch(className = "viewpoint.input.Look", methodName = "read")
    public static class LookRead {
        @OnEnter
        public static void enter() {
            ADS.lookBefore();
        }

        @OnExit
        public static void exit() {
            ADS.lookAfter();
        }
    }

    @Patch(className = "zombie.core.skinnedmodel.model.ModelMesh", methodName = "onLoadedX")
    public static class MeshLoaded {
        @OnEnter
        public static void enter(@This Object var0, @Argument(0) Object var1) {
            if (zombie.network.GameServer.server) return;
            MeshGeo.onLoadedX(var0, var1);
        }
    }

    @Patch(className = "viewpoint.render.FrameContext", methodName = "camera")
    public static class NearCamera {
        @OnExit
        public static void exit(@This Object var0, @Argument(0) Object var1) {
            NearClip.afterCamera(var0, var1);
        }
    }

    @Patch(className = "viewpoint.render.IrisUniforms", methodName = "camera")
    public static class NearIris {
        @OnExit
        public static void exit(@This Object var0, @Argument(0) Object var1) {
            NearClip.afterIrisCamera(var0, var1);
        }
    }

    @Patch(className = "viewpoint.render.IrisUniforms", methodName = "matrices")
    public static class NearIrisMatrices {
        @OnExit
        public static void exit(@This Object var0, @Argument(0) Object var1) {
            NearClip.afterIrisMatrices(var0, var1);
        }
    }

    @Patch(className = "viewpoint.render.PackStages", methodName = "uniforms")
    public static class NearPack {
        @OnExit
        public static void exit(@Argument(0) Object var0, @Argument(1) Object var1) {
            NearClip.afterPackUniforms(var0, var1);
        }
    }

    // Viewpoint (current): FP.snapshot(Frame) became FrameFill.fill(Frame, ...): the frame's projection is set and the
    // view captured there, so this runs at the same point with the same Frame as argument 0.
    @Patch(className = "viewpoint.FrameFill", methodName = "fill")
    public static class NearProjection {
        @OnExit
        public static void exit(@Argument(0) Object var0) {
            NearClip.afterSnapshot(var0);
            Pip.snapshotted(var0);
        }
    }

    @Patch(className = "viewpoint.render.PackStages", methodName = "draw")
    public static class PackDraw {
        @OnExit
        public static void exit(@Argument(0) Object var0) {
            Focus.afterDraw(var0);
        }
    }

    @Patch(className = "viewpoint.render.FarShadow", methodName = "draw")
    public static class PipFarShadow {
        @OnEnter(skipOn = true)
        public static boolean enter() {
            return Pip.skipInPass();
        }
    }

    @Patch(className = "viewpoint.SceneDrawer", methodName = "drawFrame")
    public static class PipFrame {
        @OnExit
        public static void exit(@This Object var0) {
            Pip.afterDrawFrame(var0);
        }
    }

    @Patch(className = "viewpoint.render.FrameGenPass", methodName = "capture")
    public static class PipFrameGenCapture {
        @OnEnter(skipOn = true)
        public static boolean enter() {
            return Pip.skipInPass();
        }
    }

    @Patch(className = "viewpoint.render.FrameGenPass", methodName = "midframe")
    public static class PipFrameGenMidframe {
        @OnEnter(skipOn = true)
        public static boolean enter() {
            return Pip.skipInPass();
        }
    }

    @Patch(className = "viewpoint.render.UpscalePass", methodName = "release")
    public static class PipKeepUpscaler {
        @OnEnter(skipOn = true)
        public static boolean enter() {
            return Pip.skipInPass();
        }
    }

    @Patch(className = "viewpoint.render.MousePick", methodName = "read")
    public static class PipMousePick {
        @OnEnter(skipOn = true)
        public static boolean enter() {
            return Pip.skipInPass();
        }
    }

    @Patch(className = "viewpoint.render.IndirectPass", methodName = "draw")
    public static class PipOcclusion {
        @OnEnter
        public static void enter(@Argument(value = 1, readOnly = false) boolean var0) {
            if (var0 && Pip.lightPass()) {
                var0 = false;
            }
        }
    }

    @Patch(className = "viewpoint.render.FarPass", methodName = "gbuffer")
    public static class PipScissorFar {
        @OnEnter
        public static void enter(@Argument(0) Object var0) {
            Pip.scissorOn(var0);
        }

        @OnExit
        public static void exit() {
            Pip.scissorOff();
        }
    }

    @Patch(className = "viewpoint.render.SurfacePass", methodName = "light")
    public static class PipScissorLight {
        @OnEnter
        public static void enter(@Argument(0) Object var0) {
            Pip.scissorOn(var0);
        }

        @OnExit
        public static void exit() {
            Pip.scissorOff();
        }
    }

    @Patch(className = "viewpoint.render.ModelPass", methodName = "gbuffer")
    public static class PipScissorModels {
        @OnEnter
        public static void enter(@Argument(0) Object var0) {
            Pip.scissorOn(var0);
        }

        @OnExit
        public static void exit() {
            Pip.scissorOff();
        }
    }

    @Patch(className = "viewpoint.render.SurfacePass", methodName = "gbuffer")
    public static class PipScissorSurfaces {
        @OnEnter
        public static void enter(@Argument(0) Object var0) {
            Pip.scissorOn(var0);
        }

        @OnExit
        public static void exit() {
            Pip.scissorOff();
        }
    }

    @Patch(className = "viewpoint.render.ShadowPass", methodName = "draw")
    public static class PipShadows {
        @OnEnter(skipOn = true)
        public static boolean enter() {
            return Pip.skipInPass();
        }
    }

    @Patch(className = "viewpoint.Hooks", methodName = "skeletonPosed")
    public static class Posed {
        @OnExit
        public static void exit(@Argument(0) Object var0) {
            ADS.posed(var0);
        }
    }

    @Patch(className = "viewpoint.gunplay.Sway", methodName = "right")
    public static class SwayRight {
        @OnExit
        public static void exit(@Return(readOnly = false) double var0) {
            var0 = VpCompat.sway(var0);
        }
    }

    @Patch(className = "viewpoint.gunplay.Sway", methodName = "up")
    public static class SwayUp {
        @OnExit
        public static void exit(@Return(readOnly = false) double var0) {
            var0 = VpCompat.sway(var0);
        }
    }

    @Patch(className = "zombie.iso.sprite.IsoReticle", methodName = "render")
    public static class VanillaReticle {
        @OnEnter(skipOn = true)
        public static boolean enter() {
            return !zombie.network.GameServer.server && ADS.hideCrosshair();
        }
    }

    @Patch(className = "viewpoint.camera.Impulses", methodName = "shot")
    public static class ViewpointKick {
        @OnEnter(skipOn = true)
        public static boolean enter() {
            return VpCompat.skipViewpointKick();
        }
    }
}
