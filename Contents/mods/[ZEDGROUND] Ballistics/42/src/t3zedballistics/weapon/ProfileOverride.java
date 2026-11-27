package t3zedballistics.weapon;

import java.util.Map;

/**
 * A partial profile registered by a compatibility pack. Only the fields that were set override the
 * resolved values; null means "keep what the lower layer said".
 */
public final class ProfileOverride {
    public String category;
    public Double muzzleVelocity, drag, gravityScale, massGrams, pelletSpreadDeg, baseSpreadMrad,
        damageMultiplier, maxRange, lifetime;
    public Integer pelletCount, penetration;
    public Boolean tracer;

    /** Builds an override from a plain key/value map (Lua tables are converted to this by LuaApi). */
    public static ProfileOverride fromMap(Map<String, Object> m) {
        ProfileOverride o = new ProfileOverride();
        if (m == null) return o;
        Object c = m.get("category");
        if (c != null) o.category = c.toString();
        o.muzzleVelocity = d(m, "muzzleVelocity", 20, 2000);
        o.drag = d(m, "drag", 0, 0.1);
        o.gravityScale = d(m, "gravityScale", 0, 10);
        o.massGrams = d(m, "projectileMass", 0.1, 500);
        o.pelletSpreadDeg = d(m, "spread", 0, 30);
        o.baseSpreadMrad = d(m, "baseSpread", 0, 100);
        o.damageMultiplier = d(m, "damageMultiplier", 0, 10);
        o.maxRange = d(m, "maximumRange", 1, 1000);
        o.lifetime = d(m, "projectileLifetime", 0.1, 20);
        Double pc = d(m, "pelletCount", 1, 32);
        o.pelletCount = pc == null ? null : (int) Math.round(pc);
        Double pen = d(m, "penetration", 0, 5);
        o.penetration = pen == null ? null : (int) Math.round(pen);
        Object t = m.get("tracer");
        if (t instanceof Boolean b) o.tracer = b;
        return o;
    }

    private static Double d(Map<String, Object> m, String k, double min, double max) {
        Object v = m.get(k);
        if (!(v instanceof Number n)) return null;
        double x = n.doubleValue();
        if (!Double.isFinite(x)) return null;
        return Math.max(min, Math.min(max, x));
    }

    BallisticProfile applyTo(BallisticProfile p) {
        return new BallisticProfile(
            category != null ? category : p.category,
            muzzleVelocity != null ? muzzleVelocity : p.muzzleVelocity,
            drag != null ? drag : p.drag,
            gravityScale != null ? gravityScale : p.gravityScale,
            massGrams != null ? massGrams : p.massGrams,
            pelletCount != null ? pelletCount : p.pelletCount,
            pelletSpreadDeg != null ? pelletSpreadDeg : p.pelletSpreadDeg,
            baseSpreadMrad != null ? baseSpreadMrad : p.baseSpreadMrad,
            damageMultiplier != null ? damageMultiplier : p.damageMultiplier,
            maxRange != null ? maxRange : p.maxRange,
            lifetime != null ? lifetime : p.lifetime,
            penetration != null ? penetration : p.penetration,
            tracer != null ? tracer : p.tracer);
    }
}
