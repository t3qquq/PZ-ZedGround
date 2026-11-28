package t3gunworks;

import java.lang.reflect.Method;

import t3gunworks.aiming.ViewpointBridge;
import t3gunworks.api.BallisticsAPI;
import t3gunworks.combat.VanillaCombat;
import t3gunworks.config.BallisticsConfig;
import zombie.network.GameServer;

/**
 * ZombieBuddy entry point (javaPkgName = t3gunworks). Runs once when the jar is loaded, on clients and on
 * dedicated servers. Patches (ClientPatches, ServerPatches, ViewpointPatches) are applied by ZombieBuddy
 * itself, which only scans classes directly in package t3gunworks (not sub-packages); this class checks that
 * their targets exist in the running game and reports, feature by feature, what is active.
 */
public class Main {

    public static void main(String[] args) {
        Log.info("ZEDGROUND GunWorks " + BallisticsAPI.VERSION + " (Viewpoint True Ballistics 1.4.6 for PZ 42.22) loading ("
            + (GameServer.server ? "dedicated server" : "client") + ")");
        try {
            BallisticsConfig.loadLocal();
        } catch (Throwable t) {
            Log.error("settings could not be loaded; defaults in use", t);
        }
        try {
            VanillaCombat.init();
        } catch (Throwable t) {
            Log.error("vanilla combat bridge failed", t);
        }

        boolean ok = true;
        ok &= check("zombie.CombatManager", "attackCollisionCheck", "Firearm hook", true);
        ok &= check("zombie.CombatManager", "calculateHitInfoList", "Vanilla hit suppression", true);
        ok &= check("zombie.CombatManager", "CheckObjectHit", "Vanilla adjacent-object hit suppression", true);
        ok &= check("zombie.network.packets.INetworkPacket", "send", "Network hit hook", true);
        ok &= check("zombie.CombatManager", "update", "Projectile update hook", true);
        check("zombie.CombatManager", "addTracerEffect", "Vanilla tracer suppression", false);
        check("zombie.network.anticheats.AntiCheatHitWeapon", "isDistanceExceeded", "Server weapon-range widening", false);
        check("zombie.network.anticheats.AntiCheatHitLongDistance", "validate", "Server long-distance widening", false);
        check("zombie.network.packets.hit.PlayerHitZombiePacket", "isConsistent", "Server zombie-hit validation", false);
        check("zombie.network.packets.hit.PlayerHitPlayerPacket", "isConsistent", "Server PvP-hit validation", false);
        check("zombie.characters.BodyDamage.BodyDamage", "DamageFromWeapon", "Exact player wound location", false);
        check("zombie.network.fields.hit.Hit", "process", "Exact player wound location (multiplayer)", false);
        check("zombie.characters.BodyDamage.BodyDamage", "applyDamageFromWeapon", "Bullet damage overflow", false);
        check("zombie.network.fields.hit.Hit", "roll", "Head hits of projectiles (multiplayer)", false);
        check("zombie.network.packets.hit.PlayerHitPacket", "processServer", "Server keeps projectile hits (hit packet)", false);
        check("zombie.CombatManager", "isHitRolledByClient", "Server keeps projectile hits (players, animals)", false);
        check("zombie.CombatManager", "getHitData", "Server keeps projectile hits (vehicles)", false);
        if (!ok) {
            Hooks.disabled = true;
            Hooks.disabledReason = "a required game method is missing (game update?)";
            Log.error("True Ballistics disabled: " + Hooks.disabledReason + ". Vanilla firearm combat is used.", null);
        }

        if (!GameServer.server) {
            ViewpointBridge.init();
            if (ViewpointBridge.available) {
                // Project Viewpoint: Recoil & ADS 1.9.1, absorbed (t3gunworks.ads; hooks in AdsPatches).
                try {
                    t3gunworks.ads.Log.info("Recoil & ADS 1.9.1 (absorbed into ZEDGROUND GunWorks) loading");
                    t3gunworks.ads.VP.init();
                } catch (Throwable t) {
                    Log.error("absorbed ADS failed to start; sights, recoil and sway are off", t);
                }
            } else {
                Log.warn("Viewpoint not available: absorbed ADS stays off.");
            }
            if (ViewpointBridge.available) {
                check("viewpoint.input.CrosshairAim", "snapshot", "Viewpoint camera hook", false);
                check("viewpoint.render.WorldRenderer", "drawWeather", "Viewpoint 3D debug drawing", false);
                if (!"0.1.5".equals(ViewpointBridge.version))
                {
                    check("viewpoint.gunplay.Shot", "begin", "Viewpoint Gunplay hand-off (projectile decides the hit)", false);
                    check("viewpoint.gunplay.Gunplay", "active", "Viewpoint Gunplay override (TB crosshair, ring and target colour)", false);
                }
            }
        }
        Log.info("Ready. Sandbox defaults: " + BallisticsConfig.describe());
    }

    /** Verifies that a patch target exists; logs the feature state. */
    private static boolean check(String cls, String method, String feature, boolean required) {
        try {
            Class<?> c = Class.forName(cls, false, Main.class.getClassLoader());
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(method)) {
                    Log.info(feature + ": target found (" + c.getSimpleName() + "." + method + ").");
                    return true;
                }
            }
            Log.warn(feature + " unavailable: " + cls + "." + method + " not found" + (required ? "" : "; feature disabled"));
        } catch (ClassNotFoundException | LinkageError e) {
            Log.warn(feature + " unavailable: " + cls + " not found (" + e + ")" + (required ? "" : "; feature disabled"));
        }
        return false;
    }
}
