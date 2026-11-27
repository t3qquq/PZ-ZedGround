package t3zedballistics.api;

import java.util.Map;

import t3zedballistics.V3;
import t3zedballistics.config.BallisticsConfig;
import t3zedballistics.network.BallisticsNetwork;
import t3zedballistics.projectile.BallisticsManager;
import t3zedballistics.projectile.ShotGroup;
import t3zedballistics.weapon.BallisticProfile;
import t3zedballistics.weapon.ProfileOverride;
import t3zedballistics.weapon.WeaponBallisticsResolver;
import zombie.characters.IsoPlayer;
import zombie.inventory.types.HandWeapon;

/**
 * Small Java API for other Viewpoint firearm mods (ADS/scope mods, weapon packs, suppressor or armour
 * mods). Lua mods use the same functions through ZGB_* globals (see media/lua/shared/ZedBallistics/ZedBallistics_Core.lua).
 *
 * Gameplay-affecting registrations must be made identically on server and clients (shared Lua or a
 * jar loaded on both) - the server uses the same profiles to validate hit distances.
 */
public final class BallisticsAPI {

    public static final String VERSION = "1.4.6-zg2";

    /** Resolved ballistic profile of a weapon (ammo name + stats + registered overrides). */
    public static BallisticProfile getWeaponProfile(HandWeapon weapon) {
        return WeaponBallisticsResolver.resolve(weapon);
    }

    /**
     * Registers per-weapon values. Keys: muzzleVelocity (m/s), gravityScale, drag (1/m), projectileMass (g),
     * penetration (bodies), damageMultiplier, maximumRange (m), projectileLifetime (s), pelletCount,
     * spread (pellet cone half-angle, degrees), baseSpread (mrad), tracer (boolean), category (text).
     */
    public static void registerWeaponProfile(String fullType, Map<String, Object> values) {
        WeaponBallisticsResolver.registerWeapon(fullType, ProfileOverride.fromMap(values));
    }

    /** Same keys as registerWeaponProfile, applied to every weapon using this ammo item / ammo type. */
    public static void registerAmmoProfile(String ammo, Map<String, Object> values) {
        WeaponBallisticsResolver.registerAmmo(ammo, ProfileOverride.fromMap(values));
    }

    /**
     * Fires a round from the player's current aim with the given weapon as if the trigger was pulled,
     * without playing sounds or animations (for scripted weapons). Damage and networking are the same as
     * for a real shot. In multiplayer the server treats it as a real trigger pull (its hit packet makes the
     * server's vanilla shot handling consume a round; 42.22 has no shot IDs any more). Returns false if no shot was produced.
     */
    public static boolean fireProjectile(IsoPlayer player, HandWeapon weapon) {
        if (player == null || weapon == null || !player.isLocalPlayer()) return false;
        ShotGroup g = BallisticsManager.INSTANCE.spawnShot(player, weapon, false);
        return g != null;
    }

    /** Current zero distance in metres (0 = bore aimed straight at the reticle point). */
    public static double getCurrentZero() { return zeroOverride >= 0 ? zeroOverride : BallisticsConfig.zeroDistance; }

    /**
     * Lets an ADS/scope mod set the zero of the sight in use (metres, 0..1000). Pass a negative value to
     * return to the sandbox default. Client-side only: it changes where the shooter's own rounds go.
     */
    public static void setCurrentZero(double meters) {
        zeroOverride = meters < 0 ? -1 : Math.min(1000, meters);
        BallisticsConfig.zeroDistance = zeroOverride >= 0 ? zeroOverride : sandboxZero;
    }

    private static double zeroOverride = -1;
    private static double sandboxZero = 0;

    /** Called after the sandbox refresh so an ADS override survives it. */
    public static void reapplyZero() {
        sandboxZero = BallisticsConfig.zeroDistance;
        if (zeroOverride >= 0) BallisticsConfig.zeroDistance = zeroOverride;
    }

    /** Whether rounds currently fly with the full ballistic range (server confirmed in multiplayer). */
    public static boolean fullRange() { return !BallisticsNetwork.vanillaRangeOnly(); }

    private static volatile BodyPartFilter bodyPartFilter;

    /**
     * Installs the filter that removes missing body parts from hit volumes (1.3.0). One filter at a
     * time; null removes it. A filter that throws is ignored for that character.
     */
    public static void setBodyPartFilter(BodyPartFilter f) { bodyPartFilter = f; }

    public static BodyPartFilter getBodyPartFilter() { return bodyPartFilter; }

    /** Number of rounds in flight on this client. */
    public static int activeProjectiles() { return BallisticsManager.INSTANCE.activeCount(); }

    /** Copies the position of every live round into out (up to out.length); returns the count. */
    public static int projectilePositions(V3[] out) {
        var list = BallisticsManager.INSTANCE.activeProjectiles();
        int n = Math.min(out.length, list.size());
        for (int i = 0; i < n; i++) {
            if (out[i] == null) out[i] = new V3();
            out[i].set(list.get(i).position);
        }
        return n;
    }

    private BallisticsAPI() {}
}
