package t3zedballistics.weapon;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import t3zedballistics.Log;
import t3zedballistics.config.BallisticsConfig;
import zombie.inventory.types.HandWeapon;
import zombie.scripting.objects.AmmoType;

/**
 * Resolution order (first match wins per field, later layers override earlier ones):
 *
 *   category template from the ammo name / weapon stats   (AmmoBallisticsResolver)
 *     <- ammo override   (ZedBallistics.registerAmmoProfile)
 *     <- weapon override (ZedBallistics.registerWeaponProfile)
 *
 * Results are cached per weapon type + ammo type. Registering an override clears the cache.
 */
public final class WeaponBallisticsResolver {
    private static final Map<String, ProfileOverride> ammoOverrides = new ConcurrentHashMap<>();
    private static final Map<String, ProfileOverride> weaponOverrides = new ConcurrentHashMap<>();
    private static final Map<String, BallisticProfile> cache = new ConcurrentHashMap<>();

    public static void registerAmmo(String ammoKey, ProfileOverride o) {
        if (ammoKey == null || o == null) return;
        ammoOverrides.put(AmmoBallisticsResolver.normalise(ammoKey), o);
        cache.clear();
    }

    public static void registerWeapon(String fullType, ProfileOverride o) {
        if (fullType == null || o == null) return;
        weaponOverrides.put(fullType.toLowerCase(), o);
        cache.clear();
    }

    public static String ammoKey(HandWeapon w) {
        try {
            AmmoType a = w.getAmmoType();
            if (a == null) return null;
            String k = a.getItemKey();
            return k != null ? k : a.toString();
        } catch (Throwable t) {
            Log.errorOnce("reading ammo type of " + w.getFullType(), t);
            return null;
        }
    }

    public static BallisticProfile resolve(HandWeapon w) {
        String type = w.getFullType();
        String ammo = ammoKey(w);
        String cacheKey = type + "|" + ammo;
        BallisticProfile p = cache.get(cacheKey);
        if (p != null) return p;

        p = AmmoBallisticsResolver.resolve(ammo, w.getProjectileCount(), w.getMaxRange(), w.getMaxDamage(),
            w.getProjectileSpread());
        if (ammo != null) {
            ProfileOverride ao = ammoOverrides.get(AmmoBallisticsResolver.normalise(ammo));
            if (ao == null) {
                try {
                    AmmoType a = w.getAmmoType();
                    if (a != null) ao = ammoOverrides.get(AmmoBallisticsResolver.normalise(a.toString()));
                } catch (Throwable t) {
                    Log.errorOnce("reading ammo resource name of " + type, t);
                }
            }
            if (ao != null) p = ao.applyTo(p);
        }
        ProfileOverride wo = weaponOverrides.get(type.toLowerCase());
        if (wo != null) p = wo.applyTo(p);

        cache.put(cacheKey, p);
        if (BallisticsConfig.logVerbose()) Log.info("profile " + type + " [" + ammo + "] -> " + p);
        return p;
    }

    /**
     * How far rounds from this weapon fly before they are removed, in metres. In multiplayer, when the
     * server has not confirmed that it runs the True Ballistics server component, hits must stay inside
     * vanilla's anti-cheat distance (weapon MaxRange + 5) or the shooter would be kicked.
     */
    public static double maxDistance(HandWeapon w, BallisticProfile p, boolean vanillaRangeOnly) {
        double vanilla = Math.max(1.0, w.getMaxRange());
        if (vanillaRangeOnly) return vanilla + 1.0; // vanilla rejects beyond MaxRange + 5; keep slack for movement during flight
        double d = p.maxRange > 0 ? p.maxRange : vanilla * BallisticsConfig.maxDistanceMultiplier;
        return Math.min(d, BallisticsConfig.maxDistanceCap);
    }

    /** Server side: the furthest a legitimate ballistic hit from this weapon can be. */
    public static double serverMaxDistance(HandWeapon w) {
        return maxDistance(w, resolve(w), false);
    }

    public static void clearCache() { cache.clear(); }

    public static int overrideCount() { return ammoOverrides.size() + weaponOverrides.size(); }

    private WeaponBallisticsResolver() {}
}
