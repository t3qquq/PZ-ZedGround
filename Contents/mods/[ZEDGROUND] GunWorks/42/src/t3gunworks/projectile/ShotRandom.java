package t3gunworks.projectile;

/**
 * Deterministic random numbers for one shot (SplitMix64). The seed is built from the shooter's online
 * ID and the shot sequence number, so the same shot always produces the same dispersion and pellet
 * pattern - useful for replay/verification, and the server's sanity checks use the same generator.
 */
public final class ShotRandom {
    private long state;

    public ShotRandom seed(long shooterId, long shotSeq) {
        state = shooterId * 0x9E3779B97F4A7C15L ^ (shotSeq + 0x632BE59BD9B4E019L);
        next(); // decorrelate nearby seeds
        return this;
    }

    public long next() {
        long z = (state += 0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Uniform in [0,1). */
    public double uniform() { return (next() >>> 11) * 0x1.0p-53; }

    private double spare;
    private boolean hasSpare;

    /** Standard normal (Box-Muller), clamped to +-3 so no shot lands absurdly wide. */
    public double gaussian() {
        double g;
        if (hasSpare) { hasSpare = false; g = spare; }
        else {
            double u1 = Math.max(1e-12, uniform()), u2 = uniform();
            double r = Math.sqrt(-2 * Math.log(u1));
            g = r * Math.cos(2 * Math.PI * u2);
            spare = r * Math.sin(2 * Math.PI * u2);
            hasSpare = true;
        }
        return Math.max(-3, Math.min(3, g));
    }

    /** Uniform point in a cone of the given half-angle: writes (right, up) angles into out. */
    public void cone(double halfAngle, double[] out) {
        double r = halfAngle * Math.sqrt(uniform());
        double a = 2 * Math.PI * uniform();
        out[0] = r * Math.cos(a);
        out[1] = r * Math.sin(a);
    }
}
