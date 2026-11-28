package t3gunworks.network;

import java.lang.reflect.Field;
import java.util.List;

import t3gunworks.BallisticsUnits;
import t3gunworks.Log;
import t3gunworks.V3;
import t3gunworks.collision.PzWorld;
import t3gunworks.collision.WorldCollision;
import t3gunworks.collision.WorldQuery;
import t3gunworks.config.BallisticsConfig;
import t3gunworks.weapon.WeaponBallisticsResolver;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.combat.CombatConfigKey;
import zombie.CombatManager;
import zombie.inventory.types.HandWeapon;
import zombie.network.GameServer;
import zombie.network.fields.hit.Hit;
import zombie.network.packets.hit.PlayerHit;

/**
 * Server side checks for firearm hit packets, on top of vanilla's anti-cheat. The server never trusts a
 * client's claim that a round hit; it checks that the hit was physically possible from what it knows:
 *
 *  1. line of fire - from the shooter's server-side position to the target there must be at least one
 *     path that does not cross a wall, closed door, floor or ceiling (windows, low fences, furniture and
 *     glass do not count, rounds go through/over those). A few start/end points are tried to absorb
 *     network position lag. This stops "shoot through walls" clients.
 *  2. damage sanity - no single hit may exceed what the weapon can do with the most generous vanilla
 *     multipliers.
 *  3. distance - vanilla's distance checks (weapon MaxRange + 5, relevant range) are widened to the
 *     ballistic maximum range by the anti-cheat patches, never further.
 *
 * A failed check removes the hit (the shot counts as a miss); it does not kick or ban.
 */
public final class ServerValidator {
    private static final PzWorld world = new PzWorld();
    private static final SolidOnly solid = new SolidOnly();
    private static final WorldCollision.Hit hit = new WorldCollision.Hit();
    private static final V3 a = new V3(), b = new V3();
    private static final double[] SHOOTER_HEIGHTS = {1.40, 1.05};
    private static final double[] TARGET_HEIGHTS = {1.55, 1.05, 0.45};
    private static final double[] LATERAL = {0.0, 0.45, -0.45};

    public static long checked, rejected;

    /**
     * Called from the isConsistent advice of PlayerHitZombiePacket / PlayerHitPlayerPacket (each is one
     * entry of a 42.22 PlayerHitPacket). A hit that fails is removed from the entry; the entry itself stays
     * valid, so the shot is still processed as a miss: vanilla's per-shot accounting runs (the round leaves
     * the magazine through OnWeaponSwingHitPoint) and other players still see the shot, but nothing is
     * damaged. Always returns true.
     */
    public static synchronized boolean validate(Object packet) {
        if (!GameServer.server || !BallisticsConfig.serverValidation) return true;
        if (!(packet instanceof PlayerHit p)) return true;
        try {
            HandWeapon w = p.getHandWeapon();
            IsoPlayer shooter = p.getPlayer();
            IsoGameCharacter target = p.getTarget() instanceof IsoGameCharacter c ? c : null;
            if (w == null || shooter == null || target == null || !w.isAimedFirearm()) return true;
            List<Hit> hits = ServerHits.hitsOf(p);
            if (hits == null || hits.isEmpty()) return true;
            checked++;

            String why = damageProblem(hits, w);
            if (why == null) why = lineOfFireProblem(shooter, target);
            if (why != null) {
                rejected++;
                hits.clear();
                Log.warn("rejected firearm hit from " + shooter.getUsername() + " on " + target.getClass().getSimpleName()
                    + "#" + target.getOnlineID() + ": " + why);
            }
            return true;
        } catch (RuntimeException e) {
            Log.errorOnce("server hit validation (hits are accepted while this fails)", e);
            return true;
        }
    }

    private static String damageProblem(List<Hit> hits, HandWeapon w) {
        Field f = damageField();
        if (f == null) return null;
        float head = Math.max(1f, CombatManager.getInstance().getCombatConfig().get(CombatConfigKey.HEAD_HIT_DAMAGE_SPLIT_MODIFIER));
        double cap = w.getMaxDamage() * head * WeaponBallisticsResolver.resolve(w).damageMultiplier * 5.0 + 1.0;
        for (int i = 0; i < hits.size(); i++) {
            Hit h = hits.get(i);
            if (h == null) continue;
            try {
                float d = f.getFloat(h);
                if (d > cap) return String.format("damage %.2f exceeds %.2f", d, cap);
            } catch (IllegalAccessException e) {
                return null;
            }
        }
        return null;
    }

    private static Field damageField;
    private static boolean damageFieldResolved;

    /** Hit.hitDamage: the damage the shooter's client reports for a hit (42.22; protected, no getter). */
    private static Field damageField() {
        if (!damageFieldResolved) {
            damageFieldResolved = true;
            try {
                Field f = Hit.class.getDeclaredField("hitDamage");
                f.setAccessible(true);
                damageField = f;
            } catch (ReflectiveOperationException | RuntimeException e) {
                Log.warn("Hit.hitDamage not found (" + e + "); the server-side damage check is off");
            }
        }
        return damageField;
    }

    private static String lineOfFireProblem(IsoPlayer shooter, IsoGameCharacter target) {
        world.bind();
        if (!world.ready()) return null;
        double su = BallisticsUnits.levelsToMeters(shooter.getZ()), tu = BallisticsUnits.levelsToMeters(target.getZ());
        double dx = target.getX() - shooter.getX(), dy = target.getY() - shooter.getY();
        double l = Math.hypot(dx, dy);
        double rx = l > 1e-6 ? -dy / l : 0, ry = l > 1e-6 ? dx / l : 0;
        if (!world.exists((int) Math.floor(shooter.getX()), (int) Math.floor(shooter.getY()), (int) Math.floor(shooter.getZ()))) return null;
        for (double sh : SHOOTER_HEIGHTS) {
            for (double lat : LATERAL) {
                a.set(shooter.getX() + rx * lat, shooter.getY() + ry * lat, su + sh);
                for (double th : TARGET_HEIGHTS) {
                    b.set(target.getX(), target.getY(), tu + (target.isProne() ? th * 0.35 : th));
                    if (!WorldCollision.trace(a, b, solid, hit, null)) return null; // one clear path is enough
                }
            }
        }
        return "no line of fire (blocked by " + kind(hit.kind) + " near " + hit.tileX + "," + hit.tileY + "," + hit.tileZ + ")";
    }

    private static String kind(int k) {
        return switch (k) {
            case WorldCollision.HIT_WALL -> "wall/door";
            case WorldCollision.HIT_FLOOR -> "floor";
            case WorldCollision.HIT_CEILING -> "ceiling";
            default -> "geometry";
        };
    }

    /** Only full-height obstructions count for validation. */
    private static final class SolidOnly implements WorldQuery {
        @Override public int edge(int x, int y, int z, int dx, int dy) {
            int e = world.edge(x, y, z, dx, dy);
            return e == EDGE_SOLID ? EDGE_SOLID : EDGE_CLEAR;
        }
        @Override public Object lastWindow() { return null; }
        @Override public boolean solidFloor(int x, int y, int z) { return world.solidFloor(x, y, z); }
        @Override public int cell(int x, int y, int z) { return CELL_EMPTY; }
        @Override public boolean exists(int x, int y, int z) { return world.exists(x, y, z); }
    }

    /** Ballistic range for the widened anti-cheat distance checks (metres). */
    public static double allowedDistance(HandWeapon w) {
        return WeaponBallisticsResolver.serverMaxDistance(w) + 5.0;
    }

    private ServerValidator() {}
}
