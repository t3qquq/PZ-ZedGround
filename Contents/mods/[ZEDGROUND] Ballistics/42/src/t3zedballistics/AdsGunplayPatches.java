package t3zedballistics;

import me.zed_0xff.zombie_buddy.Patch;
import t3zedballistics.gunplay.AdsGunplay;

/**
 * Hooks into Project Viewpoint: Recoil &amp; ADS (package pvads, verified in 1.9.1) for ZEDGROUND gunplay
 * (t3zedballistics.gunplay.AdsGunplay). Without ADS these classes do not exist and ZombieBuddy's type matcher
 * never matches them; on a dedicated server AdsGunplay does nothing.
 *
 *   pvads.ADS.lookEffects(float)  - main thread, once per frame while ADS runs; applies sway and recoil
 *                                   springs to Viewpoint's view. Sway multiplier set on entry.
 *   pvads.ADS.steadyFrame(float)  - called from lookEffects; drains / refills the breath of steady aim.
 *   pvads.ADS.onFire(Object)      - one shot of the local player; adds this shot's kick.
 */
public final class AdsGunplayPatches {

    @Patch(className = "pvads.ADS", methodName = "lookEffects")
    public static class LookEffects {
        @Patch.OnEnter
        public static void enter() {
            AdsGunplay.lookEnter();
        }
    }

    @Patch(className = "pvads.ADS", methodName = "steadyFrame")
    public static class SteadyFrame {
        @Patch.OnEnter
        public static void enter() {
            AdsGunplay.steadyEnter();
        }

        @Patch.OnExit(onThrowable = Throwable.class)
        public static void exit() {
            AdsGunplay.steadyExit();
        }
    }

    @Patch(className = "pvads.ADS", methodName = "onFire")
    public static class OnFire {
        @Patch.OnEnter
        public static void enter(@Patch.Argument(0) Object who) {
            AdsGunplay.fireEnter(who);
        }

        @Patch.OnExit(onThrowable = Throwable.class)
        public static void exit() {
            AdsGunplay.fireExit();
        }
    }

    private AdsGunplayPatches() {}
}
