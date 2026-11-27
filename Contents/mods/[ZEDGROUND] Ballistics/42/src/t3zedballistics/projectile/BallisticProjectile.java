package t3zedballistics.projectile;

import t3zedballistics.V3;
import zombie.characters.IsoGameCharacter;

/**
 * A live round. A plain pooled data object - no IsoObject, nothing registered with the game world.
 * Positions/velocities are metric (see BallisticsUnits).
 */
public final class BallisticProjectile {
    public final V3 position = new V3();
    public final V3 previousPosition = new V3();
    public final V3 velocity = new V3();
    public final V3 origin = new V3();

    public double age;            // s
    public double maxLifetime;    // s
    public double traveled;       // m along the path
    public double maxDistance;    // m
    public double muzzleVelocity; // m/s after VelocityScale
    public double gravity;        // m/s^2 after all scales
    public double drag;           // 1/m after DragScale
    public double massGrams;
    public int penetrationLeft;
    public int pellet;
    public boolean tracer;
    public long shotSeq;

    /** Shooter plus bodies already passed through; never hit again by this round. */
    public final IsoGameCharacter[] ignore = new IsoGameCharacter[6];
    public int ignoreCount;

    public ShotGroup group;
    public boolean alive;

    // debug trail (only filled when trajectory debugging is on)
    public final float[] trail = new float[3 * 64];
    public int trailCount;

    public double speed() { return velocity.length(); }

    /** Kinetic energy in joules. */
    public double energy() {
        double v = speed();
        return 0.5 * (massGrams / 1000.0) * v * v;
    }

    public void addIgnore(IsoGameCharacter c) {
        if (ignoreCount < ignore.length) ignore[ignoreCount++] = c;
    }

    public void recordTrail() {
        if (trailCount >= trail.length / 3) return;
        trail[trailCount * 3] = (float) position.x;
        trail[trailCount * 3 + 1] = (float) position.y;
        trail[trailCount * 3 + 2] = (float) position.u;
        trailCount++;
    }

    void reset() {
        for (int i = 0; i < ignoreCount; i++) ignore[i] = null;
        ignoreCount = 0;
        group = null;
        alive = false;
        age = 0;
        traveled = 0;
        trailCount = 0;
        tracer = false;
        penetrationLeft = 0;
    }
}
