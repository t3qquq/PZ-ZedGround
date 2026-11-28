package t3gunworks.ads;

import t3gunworks.config.BallisticsConfig;
import zombie.characters.CharacterStat;
import zombie.characters.IsoPlayer;
import zombie.characters.Stats;
import zombie.characters.skills.PerkFactory;

/**
 * ZEDGROUND gunplay numbers for the absorbed ADS code. Everything that decides where a shot lands comes from
 * the server's sandbox (BallisticsConfig, page GunWorks), never from a player's own options:
 *
 *   sway      0.0045 rad x weapon swayMul x stability(player) x ADS engagement x steady/gasp x rest
 *             stability = Aiming curve SwayAimingL0 -> L10 x endurance x fatigue x panic x moving/sneaking
 *   steady    cuts the sway by SteadyCutL0 -> L10 (ADS: 0.88 for everyone);
 *             breath lasts SteadyHoldBase + SteadyHoldPerFitness * Fitness s (ADS: 5),
 *             refills in SteadyRefillBase - SteadyRefillPerFitness * Fitness s (ADS: 6)
 *   recoil    ADS's kick x Aiming curve RecoilAimingL0 -> L10 (ADS: none);
 *             sideways kick x 1 -> RecoilRampMax over RecoilRampTime s of a held burst
 *   handling  inertia, raise speed, weapon rest, hidden crosshair, third-person sights, eye box,
 *             steady zoom, scope zoom and weapon collision: sandbox values written over ADS.Settings (enforce)
 */
public final class Gunplay {
    /** A pause longer than this ends a burst (ADS counts a "string" of shots with the same gap). */
    private static final long BURST_GAP_NS = 350_000_000L;
    private static long burstStart, lastShot;
    private static float cachedStability = 1.0F;
    private static long stabilityAt;
    private static boolean announced;

    private Gunplay() {}

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    private static double clamp01(double v) { return Double.isFinite(v) ? Math.max(0, Math.min(1, v)) : 0; }

    private static double aimingT(IsoPlayer p) {
        return p == null ? 0 : Math.max(0, Math.min(10, p.getPerkLevel(PerkFactory.Perks.Aiming))) / 10.0;
    }

    private static int fitness(IsoPlayer p) {
        return p == null ? 0 : Math.max(0, Math.min(10, p.getPerkLevel(PerkFactory.Perks.Fitness)));
    }

    /** Sway multiplier before steady aim. Recomputed at most every 100 ms (stats change slowly). */
    static float stability(IsoPlayer p) {
        long now = System.nanoTime();
        if (now - stabilityAt < 100_000_000L) return cachedStability;
        stabilityAt = now;
        if (p == null) return cachedStability = 1.0F;
        double f = BallisticsConfig.gunplay ? lerp(BallisticsConfig.swayAimingL0, BallisticsConfig.swayAimingL10, aimingT(p)) : 1.0;
        Stats st = p.getStats();
        if (st != null) {
            f *= 1 + (1 - clamp01(st.get(CharacterStat.ENDURANCE))) * 0.9;
            f *= 1 + clamp01(st.get(CharacterStat.FATIGUE)) * 0.5;
            f *= 1 + clamp01(st.get(CharacterStat.PANIC) / 100.0) * 0.8;
        }
        if (p.isPlayerMoving()) f *= 1.7;
        else if (p.isSneaking()) f *= 0.8;
        cachedStability = (float) Math.max(0, Math.min(8, f));
        announce(p);
        return cachedStability;
    }

    /** Share of the sway steady aim removes. */
    static float steadyCut(IsoPlayer p) {
        if (!BallisticsConfig.gunplay) return 0.88F;
        return (float) clamp01(lerp(BallisticsConfig.steadyCutL0, BallisticsConfig.steadyCutL10, aimingT(p)));
    }

    static float holdSeconds(IsoPlayer p) {
        if (!BallisticsConfig.gunplay) return 5.0F;
        return (float) Math.max(0.5, BallisticsConfig.steadyHoldBase + BallisticsConfig.steadyHoldPerFitness * fitness(p));
    }

    static float refillSeconds(IsoPlayer p) {
        if (!BallisticsConfig.gunplay) return 6.0F;
        return (float) Math.max(0.5, BallisticsConfig.steadyRefillBase - BallisticsConfig.steadyRefillPerFitness * fitness(p));
    }

    /** Recoil multiplier for one shot. */
    static float recoil(IsoPlayer p) {
        if (!BallisticsConfig.gunplay) return 1.0F;
        return (float) Math.max(0, lerp(BallisticsConfig.recoilAimingL0, BallisticsConfig.recoilAimingL10, aimingT(p)));
    }

    /** Call once per shot (onFire); returns the sideways kick multiplier of the burst so far. */
    static float sideRamp(long now) {
        if (lastShot == 0 || now - lastShot > BURST_GAP_NS) burstStart = now;
        lastShot = now;
        if (!BallisticsConfig.gunplay) return 1.0F;
        double t = BallisticsConfig.recoilRampTime > 0 ? Math.min(1.0, (now - burstStart) / 1e9 / BallisticsConfig.recoilRampTime) : 1.0;
        return (float) lerp(1.0, BallisticsConfig.recoilRampMax, t);
    }

    static double burstSeconds() { return (lastShot - burstStart) / 1e9; }

    /** Writes the server's gunplay values over a player's ADS settings (called whenever they are applied and each frame). */
    static void enforce(ADS.Settings s) {
        if (s == null) return;
        s.sway = true;
        s.swayAmount = 1.0F;
        s.cameraRecoil = true;
        s.recoilAmount = 1.0F;
        s.steady = true;
        s.hipSpread = false;
        s.inertia = BallisticsConfig.adsInertia;
        s.speedMul = (float) BallisticsConfig.adsRaiseSpeed;
        s.rest = BallisticsConfig.adsRest;
        s.hideCrosshair = BallisticsConfig.adsHideCrosshair;
        s.thirdAds = BallisticsConfig.adsThirdPerson;
        s.eyeBox = BallisticsConfig.adsEyeBox;
        s.steadyZoom = (float) BallisticsConfig.adsSteadyZoom;
        s.zoomMul = (float) BallisticsConfig.adsScopeZoom;
        Presence.Config c = Presence.C;
        if (c != null && c.enabled != BallisticsConfig.adsWeaponCollision) {
            c.enabled = BallisticsConfig.adsWeaponCollision;
            if (!c.enabled) Presence.reset();
        }
    }

    private static void announce(IsoPlayer p) {
        if (announced) return;
        announced = true;
        Log.info(String.format("gunplay: aiming=%d fitness=%d sway x%.2f recoil x%.2f steady cut %.2f hold %.1fs refill %.1fs",
            p.getPerkLevel(PerkFactory.Perks.Aiming), fitness(p), cachedStability, recoil(p), steadyCut(p), holdSeconds(p), refillSeconds(p)));
    }

    static void logShot(IsoPlayer p, float recoil, float side) {
        if (!BallisticsConfig.logVerbose()) return;
        Log.info(String.format("gunplay shot: aiming=%d recoil x%.2f side x%.2f (burst %.2fs)",
            p.getPerkLevel(PerkFactory.Perks.Aiming), recoil, side, burstSeconds()));
    }
}
