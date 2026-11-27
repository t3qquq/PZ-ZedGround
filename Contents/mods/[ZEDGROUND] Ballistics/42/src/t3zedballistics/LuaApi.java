package t3zedballistics;

import java.util.HashMap;
import java.util.Map;

import me.zed_0xff.zombie_buddy.Exposer;
import se.krka.kahlua.integration.annotations.LuaMethod;
import se.krka.kahlua.vm.KahluaTable;
import se.krka.kahlua.vm.KahluaTableIterator;
import t3zedballistics.aiming.ViewpointBridge;
import t3zedballistics.api.BallisticsAPI;
import t3zedballistics.config.BallisticsConfig;
import t3zedballistics.network.BallisticsNetwork;
import t3zedballistics.network.ServerValidator;
import t3zedballistics.projectile.BallisticsManager;
import t3zedballistics.weapon.WeaponBallisticsResolver;
import zombie.characters.IsoPlayer;
import zombie.inventory.InventoryItem;
import zombie.inventory.types.HandWeapon;

/**
 * Lua API. ZombieBuddy exposes this class to Lua as the table ZedBallisticsJ (@LuaClass), which works
 * whatever the load order. The @LuaMethod(global) names are only registered when the jar loads before
 * Lua's exposeAll - which is not the case for mods enabled for a server or a save - so the ZGB_* globals
 * are defined in media/lua/shared/ZedBallistics/ZedBallistics_Core.lua as thin wrappers around ZedBallisticsJ.
 * Values cross as Kahlua types (Double, Boolean, String, KahluaTable) and are converted defensively.
 */
@Exposer.LuaClass(name = "ZedBallisticsJ")
public final class LuaApi {

    @LuaMethod(name = "ZGB_version", global = true)
    public static String version() { return BallisticsAPI.VERSION; }

    /** True when the Java side is loaded in this process (client or server). */
    @LuaMethod(name = "ZGB_available", global = true)
    public static Boolean available() { return Boolean.TRUE; }

    @LuaMethod(name = "ZGB_registerWeaponProfile", global = true)
    public static void registerWeaponProfile(Object fullType, Object table) {
        if (fullType == null || !(table instanceof KahluaTable t)) { Log.warn("ZGB_registerWeaponProfile(type, table) expected"); return; }
        BallisticsAPI.registerWeaponProfile(fullType.toString(), toMap(t));
    }

    @LuaMethod(name = "ZGB_registerAmmoProfile", global = true)
    public static void registerAmmoProfile(Object ammo, Object table) {
        if (ammo == null || !(table instanceof KahluaTable t)) { Log.warn("ZGB_registerAmmoProfile(ammo, table) expected"); return; }
        BallisticsAPI.registerAmmoProfile(ammo.toString(), toMap(t));
    }

    /** Text description of the resolved profile of the weapon in the local player's hands. */
    @LuaMethod(name = "ZGB_describeHeldWeapon", global = true)
    public static String describeHeldWeapon() {
        IsoPlayer p = IsoPlayer.players[0];
        if (p == null) return "no player";
        InventoryItem it = p.getPrimaryHandItem();
        if (!(it instanceof HandWeapon w) || !w.isAimedFirearm()) return "not holding a firearm";
        return w.getFullType() + " [" + WeaponBallisticsResolver.ammoKey(w) + "] " + WeaponBallisticsResolver.resolve(w)
            + " | range " + String.format("%.0f", WeaponBallisticsResolver.maxDistance(w, WeaponBallisticsResolver.resolve(w),
            BallisticsNetwork.vanillaRangeOnly())) + "m";
    }

    @LuaMethod(name = "ZGB_setZero", global = true)
    public static void setZero(Object meters) {
        BallisticsAPI.setCurrentZero(meters instanceof Number n ? n.doubleValue() : -1);
    }

    @LuaMethod(name = "ZGB_getZero", global = true)
    public static Double getZero() { return BallisticsAPI.getCurrentZero(); }

    /**
     * ZGB_debug("trajectories"|"impacts"|"network"|"verbose", true/false) - saved to settings.ini.
     * ZGB_debug("hitboxes", true/false) - live hit capsules of nearby characters, this session only.
     */
    @LuaMethod(name = "ZGB_debug", global = true)
    public static String debug(Object what, Object on) {
        boolean b = Boolean.TRUE.equals(on);
        String w = what == null ? "" : what.toString().toLowerCase();
        switch (w) {
            case "trajectories" -> BallisticsConfig.debugTrajectories = b;
            case "impacts" -> BallisticsConfig.debugImpacts = b;
            case "network" -> BallisticsConfig.debugNetwork = b;
            case "verbose" -> BallisticsConfig.verbose = b;
            case "hitboxes" -> {
                BallisticsConfig.debugHitboxes = b;
                return "hitboxes = " + b + " (this session only)";
            }
            case "all" -> {
                BallisticsConfig.debugTrajectories = b;
                BallisticsConfig.debugImpacts = b;
                BallisticsConfig.debugNetwork = b;
                BallisticsConfig.verbose = b;
            }
            default -> { return "use trajectories | impacts | network | verbose | hitboxes | all"; }
        }
        BallisticsConfig.saveLocal();
        return w + " = " + b;
    }

    /** Every n-th round is a visible tracer (0 = off). Saved to settings.ini. */
    @LuaMethod(name = "ZGB_tracers", global = true)
    public static void tracers(Object every) {
        BallisticsConfig.tracerEvery = every instanceof Number n ? Math.max(0, Math.min(100, n.intValue())) : 0;
        BallisticsConfig.saveLocal();
    }

    /** Spawns n inert test rounds from the player's gun (no damage, no network) - performance testing only. */
    @LuaMethod(name = "ZGB_stress", global = true)
    public static String stress(Object count) {
        IsoPlayer p = IsoPlayer.players[0];
        if (p == null) return "no player";
        InventoryItem it = p.getPrimaryHandItem();
        if (!(it instanceof HandWeapon w) || !w.isAimedFirearm()) return "hold a firearm first";
        int n = count instanceof Number num ? Math.max(1, Math.min(2000, num.intValue())) : 100;
        BallisticsManager.INSTANCE.resetStats();
        int spawned = BallisticsManager.INSTANCE.stress(p, w, n);
        return "spawned " + spawned + " test rounds; check ZGB_stats() over the next seconds";
    }

    /**
     * Rounds this client fired whose results have not been sent to the server yet. The server only
     * consumes ammo when those packets arrive, so ZGB_Client.lua refuses to fire past the magazine while
     * rounds are pending (otherwise the server's "not enough ammo" anti-cheat could trigger).
     */
    @LuaMethod(name = "ZGB_pendingRounds", global = true)
    public static Double pendingRounds() {
        return (double) BallisticsManager.INSTANCE.net.pendingRealShots();
    }

    @LuaMethod(name = "ZGB_stats", global = true)
    public static String stats() {
        String s = BallisticsManager.INSTANCE.stats();
        if (zombie.network.GameServer.server) s += " | server checked=" + ServerValidator.checked + " rejected=" + ServerValidator.rejected;
        Log.info(s);
        return s;
    }

    @LuaMethod(name = "ZGB_status", global = true)
    public static String status() {
        String s = "True Ballistics " + BallisticsAPI.VERSION + (Hooks.disabled ? " DISABLED (" + Hooks.disabledReason + ")" : "")
            + " | hooks " + (Hooks.patchesLive ? "live" : "NOT RUNNING (patches not applied?)")
            + " | viewpoint bridge " + (ViewpointBridge.available ? "ok" : "unavailable")
            + " | server " + (BallisticsNetwork.serverConfirmed ? "confirmed " + BallisticsNetwork.serverVersion : "not confirmed")
            + " | overrides " + WeaponBallisticsResolver.overrideCount()
            + " | NPC rounds " + t3zedballistics.npc.NpcBallistics.INSTANCE.activeCount() + " live, " + t3zedballistics.npc.NpcBallistics.INSTANCE.fired + " fired"
            + " | " + BallisticsConfig.describe();
        Log.info(s);
        return s;
    }

    /** Client: the server answered the handshake (called from ZGB_Client.lua). */
    @LuaMethod(name = "ZGB_setServerConfirmed", global = true)
    public static void setServerConfirmed(Object confirmed, Object serverVersion) {
        BallisticsNetwork.serverConfirmed = Boolean.TRUE.equals(confirmed);
        BallisticsNetwork.serverVersion = serverVersion == null ? "" : serverVersion.toString();
        Log.info(BallisticsNetwork.serverConfirmed
            ? "server runs True Ballistics " + BallisticsNetwork.serverVersion + " - full ballistic range enabled"
            : "server does not run the True Ballistics Java component - hits limited to vanilla weapon range");
    }

    /** Server: answers the handshake (called from ZGB_Server.lua). */
    @LuaMethod(name = "ZGB_serverInfo", global = true)
    public static String serverInfo() { return BallisticsAPI.VERSION; }

    // ------------------------------------------------------------------ NPC mods (see t3zedballistics.npc.NpcBallistics)

    /**
     * Fires a real projectile for a scripted shooter (an NPC). target may be nil (then aimX/aimY/aimZ is the
     * point aimed at, z in levels). hitChance 0..1 is the NPC mod's own chance to hit a person at this
     * range; maxDistance caps the round (m). Returns the launch data (id, origin, directions, physics) for
     * client tracers, or nil.
     */
    @LuaMethod(name = "ZGB_npcFire", global = true)
    public static KahluaTable npcFire(Object shooter, Object target, Object aimX, Object aimY, Object aimZ, Object weapon,
                                      Object hitChance, Object maxDistance, Object authoritative, Object tracer) {
        try {
            return t3zedballistics.npc.NpcBallistics.INSTANCE.fire(shooter, target, d(aimX, 0), d(aimY, 0), d(aimZ, 0), weapon,
                d(hitChance, 0.5), d(maxDistance, 0), !Boolean.FALSE.equals(authoritative), Boolean.TRUE.equals(tracer));
        } catch (RuntimeException e) {
            Log.errorOnce("ZGB_npcFire", e);
            return null;
        }
    }

    /** Advances NPC rounds; returns the impacts of authoritative rounds since the last call (or nil). */
    @LuaMethod(name = "ZGB_npcTick", global = true)
    public static KahluaTable npcTick() {
        try {
            return t3zedballistics.npc.NpcBallistics.INSTANCE.tick();
        } catch (RuntimeException e) {
            Log.errorOnce("ZGB_npcTick", e);
            return null;
        }
    }

    /** Client: replays a server NPC launch (the table ZGB_npcFire returned) as visual tracer rounds. */
    @LuaMethod(name = "ZGB_npcVisual", global = true)
    public static void npcVisual(Object launch) {
        try {
            if (launch instanceof KahluaTable t) t3zedballistics.npc.NpcBallistics.INSTANCE.visual(t);
        } catch (RuntimeException e) {
            Log.errorOnce("ZGB_npcVisual", e);
        }
    }

    // ------------------------------------------------------------------ bullet holes / impact debris relay

    /** Client: this player's world impacts since the last call, flattened for ZGB_Client.lua to send; or nil. */
    @LuaMethod(name = "ZGB_takeImpacts", global = true)
    public static KahluaTable takeImpacts() {
        try {
            double[] d = t3zedballistics.effects.ImpactEffects.INSTANCE.takeForNetwork();
            if (d == null) return null;
            KahluaTable t = zombie.Lua.LuaManager.platform.newTable();
            for (int i = 0; i < d.length; i++) t.rawset((double) (i + 1), d[i]);
            return t;
        } catch (RuntimeException e) {
            Log.errorOnce("ZGB_takeImpacts", e);
            return null;
        }
    }

    /** Client: another player's impacts relayed by the server (the table ZGB_takeImpacts produced there). */
    @LuaMethod(name = "ZGB_remoteImpacts", global = true)
    public static void remoteImpacts(Object table) {
        try {
            if (!(table instanceof KahluaTable t)) return;
            int max = 64 * 13;
            double[] d = new double[max];
            int n = 0;
            while (n < max && t.rawget((double) (n + 1)) instanceof Number v) d[n++] = v.doubleValue();
            t3zedballistics.effects.ImpactEffects.INSTANCE.remote(d, n / 13);
        } catch (RuntimeException e) {
            Log.errorOnce("ZGB_remoteImpacts", e);
        }
    }

    /**
     * ZGB_effects() shows the settings; ZGB_effects("on"|"off"), ZGB_effects("holes", n), ZGB_effects("minutes", m),
     * ZGB_effects("debris", 0..3), ZGB_effects("clear"). Saved to settings.ini.
     */
    @LuaMethod(name = "ZGB_effects", global = true)
    public static String effects(Object what, Object value) {
        String w = what == null ? "" : what.toString().trim().toLowerCase(java.util.Locale.ROOT);
        switch (w) {
            case "on" -> t3zedballistics.effects.ImpactEffects.enabled = true;
            case "off" -> t3zedballistics.effects.ImpactEffects.enabled = false;
            case "holes" -> t3zedballistics.effects.ImpactEffects.maxHoles = (int) Math.max(0, Math.min(2000, d(value, 400)));
            case "minutes" -> t3zedballistics.effects.ImpactEffects.holeMinutes = Math.max(0.5, Math.min(1440, d(value, 30)));
            case "debris" -> t3zedballistics.effects.ImpactEffects.debrisAmount = Math.max(0, Math.min(3, d(value, 1)));
            case "clear" -> t3zedballistics.effects.ImpactEffects.INSTANCE.clear();
            case "" -> { return t3zedballistics.effects.ImpactEffects.describe(); }
            default -> { return "use on | off | holes <0-2000> | minutes <0.5-1440> | debris <0-3> | clear"; }
        }
        BallisticsConfig.saveLocal();
        return t3zedballistics.effects.ImpactEffects.describe();
    }

    private static double d(Object o, double def) {
        return o instanceof Number n && Double.isFinite(n.doubleValue()) ? n.doubleValue() : def;
    }

    /** Clears live rounds and per-session state (called on game start). */
    @LuaMethod(name = "ZGB_onGameStart", global = true)
    public static void onGameStart() {
        BallisticsManager.INSTANCE.reset();
        t3zedballistics.npc.NpcBallistics.INSTANCE.reset();
        t3zedballistics.effects.ImpactEffects.INSTANCE.clear();
        BallisticsNetwork.serverConfirmed = false;
        BallisticsConfig.refreshSandbox();
        BallisticsAPI.reapplyZero();
        Log.info("game start: " + BallisticsConfig.describe());
    }

    private static Map<String, Object> toMap(KahluaTable t) {
        Map<String, Object> m = new HashMap<>();
        KahluaTableIterator it = t.iterator();
        while (it.advance()) {
            Object k = it.getKey();
            if (k != null) m.put(k.toString(), it.getValue());
        }
        return m;
    }

    private LuaApi() {}
}
