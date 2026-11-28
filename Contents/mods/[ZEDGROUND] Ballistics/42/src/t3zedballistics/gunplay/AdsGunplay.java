package t3zedballistics.gunplay;

import java.lang.reflect.Field;
import t3zedballistics.Log;
import t3zedballistics.config.BallisticsConfig;
import zombie.characters.CharacterStat;
import zombie.characters.IsoPlayer;
import zombie.characters.Stats;
import zombie.characters.skills.PerkFactory;
import zombie.network.GameServer;

/**
 * ZEDGROUND gunplay on top of Project Viewpoint: Recoil &amp; ADS (mod id ProjectViewpointADS, package pvads).
 *
 * ADS already moves Viewpoint's real view for sway and recoil (pvads.ADS.lookEffects calls VP.setYaw /
 * VP.setPitch), and pvads.Sight publishes the sight line after that motion, which AimProvider fires along.
 * So nothing here draws or aims by itself: it only feeds ADS's machinery different numbers, and the drawn
 * sight and the bullet stay on the same line by construction.
 *
 *   sway    pvads.ADS.lookEffects (enter)  ADS.swayFactor = stability(player) * steady correction
 *           - Aiming skill curve SwayAimingL0 -> SwayAimingL10 (ADS: 1.35 -> 0.70; Project Pinpoint: about 6x)
 *           - endurance, fatigue, panic, moving / sneaking (same factors as ADS's own Lua)
 *           - steady aim cuts the sway by SteadyCutL0 -> SteadyCutL10 instead of ADS's fixed 0.88
 *   breath  pvads.ADS.steadyFrame (enter/exit) the breath ADS drained or refilled this frame is rescaled
 *           - hold SteadyHoldBase + SteadyHoldPerFitness * Fitness s (ADS: 5 s)
 *           - refill SteadyRefillBase - SteadyRefillPerFitness * Fitness s (ADS: 6 s)
 *   recoil  pvads.ADS.onFire (enter/exit) the kick ADS added for this shot is rescaled
 *           - Aiming skill curve RecoilAimingL0 -> RecoilAimingL10 (ADS: none)
 *           - sideways kick grows over a held burst up to RecoilRampMax after RecoilRampTime s
 *
 * Fields are reached by reflection (pvads.ADS keeps them package-private); a missing field turns the
 * feature off with one warning instead of throwing inside ADS. Client only.
 */
public final class AdsGunplay {
    /** ADS's steady aim sway cut (pvads.ADS STEADY_SWAY_CUT, a compile-time constant there). */
    private static final float ADS_STEADY_CUT = 0.88f;
    /** ADS's breath hold and refill seconds (BREATH_HOLD, BREATH_REFILL). */
    private static final float ADS_HOLD = 5.0f, ADS_REFILL = 6.0f;
    /** A pause longer than this ends a burst (ADS counts a "string" of shots with the same gap). */
    private static final long BURST_GAP_NS = 350_000_000L;

    private static boolean resolved, available, announced;
    private static Field swayFactor, steadyE, breath, vKickV, hKickV, gKickV, gBackV, climbP, climbY;

    // per-call state (main thread)
    private static float breathBefore;
    private static boolean fireLocal;
    private static float vKv0, hKv0, gKv0, gBv0, cP0, cY0;
    private static long burstStart, lastShot;
    private static long lastLogMs;

    private AdsGunplay() {}

    /** True when gunplay should change ADS's numbers this frame. */
    private static boolean active() {
        if (GameServer.server || !BallisticsConfig.gunplay) return false;
        resolve();
        return available;
    }

    private static IsoPlayer local() {
        IsoPlayer[] ps = IsoPlayer.players;
        return ps != null && ps.length > 0 ? ps[0] : null;
    }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    private static double aimingT(IsoPlayer p) {
        int lvl = p.getPerkLevel(PerkFactory.Perks.Aiming);
        return Math.max(0, Math.min(10, lvl)) / 10.0;
    }

    private static int fitness(IsoPlayer p) {
        return Math.max(0, Math.min(10, p.getPerkLevel(PerkFactory.Perks.Fitness)));
    }

    /** Sway multiplier before steady aim: Aiming curve x condition x stance. */
    public static double stability(IsoPlayer p) {
        double f = lerp(BallisticsConfig.swayAimingL0, BallisticsConfig.swayAimingL10, aimingT(p));
        Stats st = p.getStats();
        if (st != null) {
            double endu = clamp01(st.get(CharacterStat.ENDURANCE));
            double fat = clamp01(st.get(CharacterStat.FATIGUE));
            double pan = clamp01(st.get(CharacterStat.PANIC) / 100.0);
            f *= 1 + (1 - endu) * 0.9;
            f *= 1 + fat * 0.5;
            f *= 1 + pan * 0.8;
        }
        if (p.isPlayerMoving()) f *= 1.7;
        else if (p.isSneaking()) f *= 0.8;
        return f;
    }

    /** Recoil multiplier from the Aiming skill. */
    public static double recoilMul(IsoPlayer p) {
        return lerp(BallisticsConfig.recoilAimingL0, BallisticsConfig.recoilAimingL10, aimingT(p));
    }

    private static double clamp01(double v) { return Double.isFinite(v) ? Math.max(0, Math.min(1, v)) : 0; }

    // ------------------------------------------------------------------ sway

    /** pvads.ADS.lookEffects(float) entry. */
    public static void lookEnter() {
        if (!active()) return;
        IsoPlayer p = local();
        if (p == null) return;
        try {
            double f = stability(p);
            // ADS applies (1 - 0.88 * steadyE); turn that into (1 - cut * steadyE) with our skill-based cut.
            float se = steadyE.getFloat(null);
            if (se > 0) {
                double cut = lerp(BallisticsConfig.steadyCutL0, BallisticsConfig.steadyCutL10, aimingT(p));
                f *= (1 - cut * se) / Math.max(0.05, 1 - ADS_STEADY_CUT * se);
            }
            swayFactor.setFloat(null, (float) Math.max(0, Math.min(8, f)));
            announce(p);
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail("sway", e);
        }
    }

    // ------------------------------------------------------------------ breath

    /** pvads.ADS.steadyFrame(float) entry. */
    public static void steadyEnter() {
        if (!active()) return;
        try {
            breathBefore = breath.getFloat(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail("breath", e);
        }
    }

    /** pvads.ADS.steadyFrame(float) exit. */
    public static void steadyExit() {
        if (!active()) return;
        IsoPlayer p = local();
        if (p == null) return;
        try {
            float after = breath.getFloat(null);
            float d = after - breathBefore;
            if (d == 0) return;
            int fit = fitness(p);
            double scale;
            if (d < 0) {
                double hold = Math.max(0.5, BallisticsConfig.steadyHoldBase + BallisticsConfig.steadyHoldPerFitness * fit);
                scale = ADS_HOLD / hold;
            } else {
                double refill = Math.max(0.5, BallisticsConfig.steadyRefillBase - BallisticsConfig.steadyRefillPerFitness * fit);
                scale = ADS_REFILL / refill;
            }
            float v = (float) (breathBefore + d * scale);
            breath.setFloat(null, Math.max(0f, Math.min(1f, v)));
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail("breath", e);
        }
    }

    // ------------------------------------------------------------------ recoil

    /** pvads.ADS.onFire(Object character) entry. */
    public static void fireEnter(Object who) {
        fireLocal = false;
        if (!active()) return;
        IsoPlayer p = local();
        if (p == null || who != p) return;
        try {
            vKv0 = vKickV.getFloat(null);
            hKv0 = hKickV.getFloat(null);
            gKv0 = gKickV.getFloat(null);
            gBv0 = gBackV.getFloat(null);
            cP0 = climbP.getFloat(null);
            cY0 = climbY.getFloat(null);
            long now = System.nanoTime();
            if (lastShot == 0 || now - lastShot > BURST_GAP_NS) burstStart = now;
            lastShot = now;
            fireLocal = true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail("recoil", e);
        }
    }

    /** pvads.ADS.onFire(Object character) exit. */
    public static void fireExit() {
        if (!fireLocal) return;
        fireLocal = false;
        IsoPlayer p = local();
        if (p == null) return;
        try {
            double v = recoilMul(p);
            double rampT = BallisticsConfig.recoilRampTime > 0
                ? Math.min(1.0, (lastShot - burstStart) / 1e9 / BallisticsConfig.recoilRampTime) : 1.0;
            double h = v * lerp(1.0, BallisticsConfig.recoilRampMax, rampT);
            scale(vKickV, vKv0, v);
            scale(climbP, cP0, v);
            scale(gKickV, gKv0, v);
            scale(gBackV, gBv0, v);
            scale(hKickV, hKv0, h);
            scale(climbY, cY0, h);
            if (BallisticsConfig.logVerbose()) {
                long ms = System.currentTimeMillis();
                if (ms - lastLogMs > 250) {
                    lastLogMs = ms;
                    Log.info(String.format("gunplay shot: aiming=%d recoil x%.2f side x%.2f (burst %.2fs)",
                        p.getPerkLevel(PerkFactory.Perks.Aiming), v, h, (lastShot - burstStart) / 1e9));
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail("recoil", e);
        }
    }

    /** Multiplies what ADS added to a field during this call. */
    private static void scale(Field f, float before, double mul) throws IllegalAccessException {
        float after = f.getFloat(null);
        f.setFloat(null, (float) (before + (after - before) * mul));
    }

    // ------------------------------------------------------------------ plumbing

    private static void announce(IsoPlayer p) {
        if (announced) return;
        announced = true;
        Log.info(String.format("ADS gunplay active: aiming=%d fitness=%d sway x%.2f recoil x%.2f (L0 %.2f/%.2f, L10 %.2f/%.2f)",
            p.getPerkLevel(PerkFactory.Perks.Aiming), fitness(p), stability(p), recoilMul(p),
            BallisticsConfig.swayAimingL0, BallisticsConfig.recoilAimingL0,
            BallisticsConfig.swayAimingL10, BallisticsConfig.recoilAimingL10));
    }

    private static void fail(String what, Throwable e) {
        available = false;
        Log.error("ADS gunplay (" + what + ") failed; ADS's own sway and recoil stay in use", e);
    }

    private static void resolve() {
        if (resolved) return;
        resolved = true;
        try {
            Class<?> c;
            try {
                c = Class.forName("pvads.ADS", false, AdsGunplay.class.getClassLoader());
            } catch (ClassNotFoundException e) {
                ClassLoader ctx = Thread.currentThread().getContextClassLoader();
                if (ctx == null) throw e;
                c = Class.forName("pvads.ADS", false, ctx);
            }
            swayFactor = field(c, "swayFactor");
            steadyE = field(c, "steadyE");
            breath = field(c, "breath");
            vKickV = field(c, "vKickV");
            hKickV = field(c, "hKickV");
            gKickV = field(c, "gKickV");
            gBackV = field(c, "gBackV");
            climbP = field(c, "climbP");
            climbY = field(c, "climbY");
            available = true;
            Log.info("ADS gunplay ready (Project Viewpoint: Recoil & ADS found).");
        } catch (ClassNotFoundException e) {
            available = false;
            Log.info("Project Viewpoint: Recoil & ADS not present; ADS gunplay off.");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            available = false;
            Log.warn("Project Viewpoint: Recoil & ADS found but its fields changed (" + e + "); ADS gunplay off.");
        }
    }

    private static Field field(Class<?> c, String name) throws NoSuchFieldException {
        Field f = c.getDeclaredField(name);
        if (f.getType() != float.class) throw new NoSuchFieldException(name + " is not a float");
        f.setAccessible(true);
        return f;
    }
}
