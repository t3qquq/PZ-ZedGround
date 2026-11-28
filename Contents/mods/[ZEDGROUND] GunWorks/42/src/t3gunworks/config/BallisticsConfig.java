package t3gunworks.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

import t3gunworks.Log;
import zombie.SandboxOptions;
import zombie.ZomboidFileSystem;

/**
 * Two layers of settings.
 *
 * Gameplay values live in sandbox options (media/sandbox-options.txt, page "GunWorks"). In multiplayer the
 * server's values are synced to every client by the game, so everyone simulates the same ballistics and
 * a client cannot give itself flatter bullets.
 *
 * Personal/developer switches (debug drawing, logging) live in a small local file
 * Zomboid/ViewpointTrueBallistics/settings.ini that is created on first start.
 */
public final class BallisticsConfig {

    // ---- gameplay (sandbox; defaults used until the sandbox is available) ----
    public static volatile boolean enabled = true;
    public static volatile boolean useInIsoView = false;
    public static volatile double gravityScale = 1.0;
    public static volatile double velocityScale = 0.4;
    public static volatile double dragScale = 1.0;
    public static volatile double damageFalloff = 0.5;
    public static volatile double projectileLifetime = 3.0;
    public static volatile double maxDistanceMultiplier = 3.0;
    public static volatile double maxDistanceCap = 200.0;
    public static volatile boolean penetration = true;
    public static volatile boolean hitLocations = true;
    public static volatile double zeroDistance = 0.0;
    public static volatile double spreadScale = 1.0;
    public static volatile boolean serverValidation = true;
    public static volatile boolean furnitureCollision = true;
    /** Bullet damage an emptied body part cannot take any more still counts (combat.DamageOverflow). */
    public static volatile boolean overflowDamage = true;
    /** Share of a head wound that counts against the overall health (vanilla 0.6). */
    public static volatile double headDamageShare = 1.0;
    /** Server-wide switch for the shot / wound / network log lines (same output as the verbose + network switches). */
    public static volatile boolean debugLog = true;

    // ---- gunplay of the absorbed Recoil & ADS code (t3gunworks.ads.Gunplay); players cannot override these ----
    public static volatile boolean gunplay = true;
    /** Sway multiplier at Aiming 0 and 10 (ADS's own curve is 1.35 -> 0.70). */
    public static volatile double swayAimingL0 = 2.0, swayAimingL10 = 0.35;
    /** Recoil multiplier at Aiming 0 and 10 (ADS has none). */
    public static volatile double recoilAimingL0 = 1.8, recoilAimingL10 = 0.8;
    /** Sideways kick multiplier reached after recoilRampTime seconds of a held burst. */
    public static volatile double recoilRampMax = 1.7, recoilRampTime = 1.5;
    /** Share of the sway steady aim removes at Aiming 0 and 10 (ADS: 0.88 for everyone). */
    public static volatile double steadyCutL0 = 0.70, steadyCutL10 = 0.92;
    /** Breath hold seconds = base + perFitness * Fitness; refill seconds = base - perFitness * Fitness. */
    public static volatile double steadyHoldBase = 2.0, steadyHoldPerFitness = 0.3;
    public static volatile double steadyRefillBase = 6.0, steadyRefillPerFitness = 0.2;
    /** Random dispersion kept on top of ADS's simulated hold while the sights are up (was 0.5). */
    public static volatile double adsSpreadScale = 0.0;
    /** ADS handling, written over every player's ADS settings. */
    public static volatile boolean adsInertia = true, adsRest = true, adsHideCrosshair = true, adsThirdPerson = true,
        adsEyeBox = true, adsWeaponCollision = true;
    public static volatile double adsRaiseSpeed = 1.0, adsSteadyZoom = 1.2, adsScopeZoom = 1.5;

    // ---- local developer switches ----
    public static volatile boolean debugTrajectories = false;
    public static volatile boolean debugImpacts = false;
    public static volatile boolean debugNetwork = false;
    /** Live hit capsules of nearby characters (ZGB_debug("hitboxes", true)); this session only, not saved. */
    public static volatile boolean debugHitboxes = false;
    public static volatile boolean verbose = false;
    public static volatile int tracerEvery = 0;
    public static volatile double debugLifetime = 4.0;

    /** Physical constants that are not worth a setting. */
    public static final double GRAVITY = 9.81;           // m/s^2
    public static final double MIN_SPEED = 40.0;         // m/s, below this (or 25% of muzzle speed) a round is spent
    public static final double MAX_SEGMENT = 4.0;        // m, longest straight piece of a curved path
    public static final double LOW_OBSTACLE_HEIGHT = 1.0;// m, low walls / hoppable fences
    public static final double WINDOW_VELOCITY_KEEP = 0.85;

    private static final String[] SANDBOX_KEYS = {
        "Enabled", "UseInIsoView", "GravityScale", "VelocityScale", "DragScale", "DamageFalloff",
        "ProjectileLifetime", "MaxDistanceMultiplier", "MaxDistanceCap", "Penetration", "HitLocations",
        "ZeroDistance", "SpreadScale", "ServerValidation", "FurnitureCollision", "OverflowDamage",
        "HeadDamageShare", "DebugLog",
        "Gunplay", "SwayAimingL0", "SwayAimingL10", "RecoilAimingL0", "RecoilAimingL10", "RecoilRampMax",
        "RecoilRampTime", "SteadyCutL0", "SteadyCutL10", "SteadyHoldBase", "SteadyHoldPerFitness",
        "SteadyRefillBase", "SteadyRefillPerFitness", "AdsSpreadScale", "AdsInertia", "AdsRaiseSpeed", "AdsRest",
        "AdsHideCrosshair", "AdsThirdPerson", "AdsEyeBox", "AdsSteadyZoom", "AdsScopeZoom", "AdsWeaponCollision"
    };
    private static boolean sandboxWarned;

    /** Re-reads the sandbox values. Cheap (a few hash lookups); called about once a second. */
    public static void refreshSandbox() {
        try {
            SandboxOptions so = SandboxOptions.instance;
            if (so == null) return;
            enabled = bool(so, "Enabled", enabled);
            useInIsoView = bool(so, "UseInIsoView", useInIsoView);
            gravityScale = num(so, "GravityScale", gravityScale, 0, 10);
            velocityScale = num(so, "VelocityScale", velocityScale, 0.05, 3);
            dragScale = num(so, "DragScale", dragScale, 0, 10);
            damageFalloff = num(so, "DamageFalloff", damageFalloff, 0, 1);
            projectileLifetime = num(so, "ProjectileLifetime", projectileLifetime, 0.2, 20);
            maxDistanceMultiplier = num(so, "MaxDistanceMultiplier", maxDistanceMultiplier, 1, 20);
            maxDistanceCap = num(so, "MaxDistanceCap", maxDistanceCap, 10, 1000);
            penetration = bool(so, "Penetration", penetration);
            hitLocations = bool(so, "HitLocations", hitLocations);
            zeroDistance = num(so, "ZeroDistance", zeroDistance, 0, 1000);
            spreadScale = num(so, "SpreadScale", spreadScale, 0, 10);
            serverValidation = bool(so, "ServerValidation", serverValidation);
            furnitureCollision = bool(so, "FurnitureCollision", furnitureCollision);
            overflowDamage = bool(so, "OverflowDamage", overflowDamage);
            headDamageShare = num(so, "HeadDamageShare", headDamageShare, 0.1, 2);
            debugLog = bool(so, "DebugLog", debugLog);
            gunplay = bool(so, "Gunplay", gunplay);
            swayAimingL0 = num(so, "SwayAimingL0", swayAimingL0, 0, 6);
            swayAimingL10 = num(so, "SwayAimingL10", swayAimingL10, 0, 6);
            recoilAimingL0 = num(so, "RecoilAimingL0", recoilAimingL0, 0, 5);
            recoilAimingL10 = num(so, "RecoilAimingL10", recoilAimingL10, 0, 5);
            recoilRampMax = num(so, "RecoilRampMax", recoilRampMax, 1, 4);
            recoilRampTime = num(so, "RecoilRampTime", recoilRampTime, 0, 5);
            steadyCutL0 = num(so, "SteadyCutL0", steadyCutL0, 0, 1);
            steadyCutL10 = num(so, "SteadyCutL10", steadyCutL10, 0, 1);
            steadyHoldBase = num(so, "SteadyHoldBase", steadyHoldBase, 0.5, 15);
            steadyHoldPerFitness = num(so, "SteadyHoldPerFitness", steadyHoldPerFitness, 0, 2);
            steadyRefillBase = num(so, "SteadyRefillBase", steadyRefillBase, 0.5, 30);
            steadyRefillPerFitness = num(so, "SteadyRefillPerFitness", steadyRefillPerFitness, 0, 2);
            adsSpreadScale = num(so, "AdsSpreadScale", adsSpreadScale, 0, 1);
            adsInertia = bool(so, "AdsInertia", adsInertia);
            adsRaiseSpeed = num(so, "AdsRaiseSpeed", adsRaiseSpeed, 0.5, 2);
            adsRest = bool(so, "AdsRest", adsRest);
            adsHideCrosshair = bool(so, "AdsHideCrosshair", adsHideCrosshair);
            adsThirdPerson = bool(so, "AdsThirdPerson", adsThirdPerson);
            adsEyeBox = bool(so, "AdsEyeBox", adsEyeBox);
            adsSteadyZoom = num(so, "AdsSteadyZoom", adsSteadyZoom, 1, 1.5);
            adsScopeZoom = num(so, "AdsScopeZoom", adsScopeZoom, 1, 2.5);
            adsWeaponCollision = bool(so, "AdsWeaponCollision", adsWeaponCollision);
        } catch (Throwable t) {
            if (!sandboxWarned) { sandboxWarned = true; Log.error("reading sandbox options failed; defaults stay in use", t); }
        }
    }

    /** Shot / wound log lines: the local verbose switch or the sandbox DebugLog option. */
    public static boolean logVerbose() { return verbose || debugLog; }

    /** Network log lines: the local network switch or the sandbox DebugLog option. */
    public static boolean logNetwork() { return debugNetwork || debugLog; }

    private static Object raw(SandboxOptions so, String key) {
        SandboxOptions.SandboxOption o = so.getOptionByName("GunWorks." + key);
        if (o == null) return null;
        return o.asConfigOption().getValueAsObject();
    }

    private static boolean bool(SandboxOptions so, String key, boolean def) {
        Object v = raw(so, key);
        return v instanceof Boolean b ? b : def;
    }

    private static double num(SandboxOptions so, String key, double def, double min, double max) {
        Object v = raw(so, key);
        double d = v instanceof Number n ? n.doubleValue() : def;
        if (!Double.isFinite(d)) d = def;
        return Math.max(min, Math.min(max, d));
    }

    // ------------------------------------------------------------------ local file

    public static File localFile() {
        String dir = ZomboidFileSystem.instance.getCacheDir();
        return new File(new File(dir, "GunWorks"), "settings.ini");
    }

    public static void loadLocal() {
        try {
            File f = localFile();
            if (!f.exists()) { saveLocal(); return; }
            Properties p = new Properties();
            try (InputStream in = new FileInputStream(f)) { p.load(in); }
            debugTrajectories = pb(p, "debugTrajectories", debugTrajectories);
            debugImpacts = pb(p, "debugImpacts", debugImpacts);
            debugNetwork = pb(p, "debugNetwork", debugNetwork);
            verbose = pb(p, "verbose", verbose);
            tracerEvery = (int) pd(p, "tracerEvery", tracerEvery, 0, 100);
            debugLifetime = pd(p, "debugLifetime", debugLifetime, 0.5, 60);
            t3gunworks.effects.ImpactEffects.enabled = pb(p, "impactEffects", t3gunworks.effects.ImpactEffects.enabled);
            t3gunworks.effects.ImpactEffects.maxHoles = (int) pd(p, "bulletHoles", t3gunworks.effects.ImpactEffects.maxHoles, 0, 2000);
            t3gunworks.effects.ImpactEffects.holeMinutes = pd(p, "bulletHoleMinutes", t3gunworks.effects.ImpactEffects.holeMinutes, 0.5, 1440);
            t3gunworks.effects.ImpactEffects.debrisAmount = pd(p, "debrisAmount", t3gunworks.effects.ImpactEffects.debrisAmount, 0, 3);
            if (p.getProperty("impactEffects") == null) saveLocal(); // add the new keys to an older file
        } catch (Throwable t) {
            Log.error("could not read " + localFile(), t);
        }
    }

    public static void saveLocal() {
        try {
            File f = localFile();
            File parent = f.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                Log.warn("could not create " + parent);
                return;
            }
            Properties p = new Properties();
            p.setProperty("debugTrajectories", Boolean.toString(debugTrajectories));
            p.setProperty("debugImpacts", Boolean.toString(debugImpacts));
            p.setProperty("debugNetwork", Boolean.toString(debugNetwork));
            p.setProperty("verbose", Boolean.toString(verbose));
            p.setProperty("tracerEvery", Integer.toString(tracerEvery));
            p.setProperty("debugLifetime", Double.toString(debugLifetime));
            p.setProperty("impactEffects", Boolean.toString(t3gunworks.effects.ImpactEffects.enabled));
            p.setProperty("bulletHoles", Integer.toString(t3gunworks.effects.ImpactEffects.maxHoles));
            p.setProperty("bulletHoleMinutes", Double.toString(t3gunworks.effects.ImpactEffects.holeMinutes));
            p.setProperty("debrisAmount", Double.toString(t3gunworks.effects.ImpactEffects.debrisAmount));
            try (OutputStream out = new FileOutputStream(f)) {
                p.store(out, "Viewpoint True Ballistics - personal/developer settings. Gameplay values are sandbox options (page GunWorks).");
            }
        } catch (Throwable t) {
            Log.error("could not write " + localFile(), t);
        }
    }

    private static boolean pb(Properties p, String k, boolean def) {
        String v = p.getProperty(k);
        return v == null ? def : Boolean.parseBoolean(v.trim());
    }

    private static double pd(Properties p, String k, double def, double min, double max) {
        String v = p.getProperty(k);
        if (v == null) return def;
        try { return Math.max(min, Math.min(max, Double.parseDouble(v.trim()))); }
        catch (NumberFormatException e) { Log.warn("settings.ini: " + k + " is not a number"); return def; }
    }

    public static String describe() {
        return "enabled=" + enabled + " iso=" + useInIsoView + " gravity=" + gravityScale + " velocity=" + velocityScale
            + " drag=" + dragScale + " falloff=" + damageFalloff + " lifetime=" + projectileLifetime
            + " maxDist=" + maxDistanceMultiplier + "x/cap " + maxDistanceCap + " penetration=" + penetration
            + " hitLocations=" + hitLocations + " zero=" + zeroDistance + " spread=" + spreadScale
            + " serverValidation=" + serverValidation + " furniture=" + furnitureCollision
            + " overflow=" + overflowDamage + " headShare=" + headDamageShare + " debugLog=" + debugLog
            + " gunplay=" + gunplay + " sway L0/L10=" + swayAimingL0 + "/" + swayAimingL10
            + " recoil L0/L10=" + recoilAimingL0 + "/" + recoilAimingL10 + " ramp=" + recoilRampMax + "@" + recoilRampTime + "s"
            + " steadyCut=" + steadyCutL0 + "/" + steadyCutL10 + " hold=" + steadyHoldBase + "+" + steadyHoldPerFitness + "*fit"
            + " refill=" + steadyRefillBase + "-" + steadyRefillPerFitness + "*fit adsSpread=" + adsSpreadScale
            + " adsInertia=" + adsInertia + " raise=" + adsRaiseSpeed + " rest=" + adsRest + " hideCrosshair=" + adsHideCrosshair
            + " thirdAds=" + adsThirdPerson + " eyeBox=" + adsEyeBox + " steadyZoom=" + adsSteadyZoom + " scopeZoom=" + adsScopeZoom
            + " weaponCollision=" + adsWeaponCollision;
    }

    public static String[] sandboxKeys() { return SANDBOX_KEYS.clone(); }

    private BallisticsConfig() {}
}
