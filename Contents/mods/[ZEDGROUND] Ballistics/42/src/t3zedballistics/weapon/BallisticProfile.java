package t3zedballistics.weapon;

/**
 * Resolved ballistic description of one firearm + ammunition combination. Immutable once built and
 * cached by WeaponBallisticsResolver, so per-shot lookups allocate nothing.
 *
 * All values are in real-world units; the sandbox scales (VelocityScale, GravityScale, DragScale) are
 * applied when a projectile is spawned, not here, so changing a sandbox value never needs a cache flush.
 */
public final class BallisticProfile {
    public final String category;        // human readable, e.g. "intermediate rifle"
    public final double muzzleVelocity;  // m/s before VelocityScale
    public final double drag;            // 1/m, quadratic drag coefficient (dv/dt = -drag * v * |v|)
    public final double gravityScale;    // multiplies the sandbox gravity
    public final double massGrams;       // used for kinetic energy reporting
    public final int pelletCount;        // >1 for buckshot
    public final double pelletSpreadDeg; // cone half-angle for pellets
    public final double baseSpreadMrad;  // 0 = derive from the weapon's HitChance
    public final double damageMultiplier;
    public final double maxRange;        // m, 0 = derive from weapon MaxRange * sandbox multiplier
    public final double lifetime;        // s, 0 = sandbox value
    public final int penetration;        // how many bodies the round may pass through
    public final boolean tracer;

    public BallisticProfile(String category, double muzzleVelocity, double drag, double gravityScale, double massGrams,
                            int pelletCount, double pelletSpreadDeg, double baseSpreadMrad, double damageMultiplier,
                            double maxRange, double lifetime, int penetration, boolean tracer) {
        this.category = category;
        this.muzzleVelocity = muzzleVelocity;
        this.drag = drag;
        this.gravityScale = gravityScale;
        this.massGrams = massGrams;
        this.pelletCount = Math.max(1, pelletCount);
        this.pelletSpreadDeg = pelletSpreadDeg;
        this.baseSpreadMrad = baseSpreadMrad;
        this.damageMultiplier = damageMultiplier;
        this.maxRange = maxRange;
        this.lifetime = lifetime;
        this.penetration = Math.max(0, penetration);
        this.tracer = tracer;
    }

    /** Kinetic energy in joules at a given speed. */
    public double energy(double speed) { return 0.5 * (massGrams / 1000.0) * speed * speed; }

    @Override public String toString() {
        return category + " v0=" + muzzleVelocity + " drag=" + drag + " g*" + gravityScale + " mass=" + massGrams
            + "g pellets=" + pelletCount + " spread=" + pelletSpreadDeg + "deg base=" + baseSpreadMrad + "mrad dmg*"
            + damageMultiplier + " range=" + maxRange + " pen=" + penetration + (tracer ? " tracer" : "");
    }
}
