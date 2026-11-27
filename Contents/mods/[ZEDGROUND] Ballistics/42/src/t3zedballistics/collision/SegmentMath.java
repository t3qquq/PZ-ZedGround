package t3zedballistics.collision;

import t3zedballistics.V3;

/** Allocation-free segment geometry used by the hitbox tests. */
public final class SegmentMath {

    /**
     * Closest approach between segment P(s)=p0+(p1-p0)s and Q(t)=q0+(q1-q0)t, s,t in [0,1].
     * Writes s into out[0], t into out[1] and returns the squared distance.
     */
    public static double closest(V3 p0, V3 p1, V3 q0, V3 q1, double[] out) {
        double d1x = p1.x - p0.x, d1y = p1.y - p0.y, d1u = p1.u - p0.u;
        double d2x = q1.x - q0.x, d2y = q1.y - q0.y, d2u = q1.u - q0.u;
        double rx = p0.x - q0.x, ry = p0.y - q0.y, ru = p0.u - q0.u;
        double a = d1x * d1x + d1y * d1y + d1u * d1u;
        double e = d2x * d2x + d2y * d2y + d2u * d2u;
        double f = d2x * rx + d2y * ry + d2u * ru;
        double s, t;
        if (a <= 1e-12 && e <= 1e-12) {
            s = 0; t = 0;
        } else if (a <= 1e-12) {
            s = 0; t = clamp(f / e);
        } else {
            double c = d1x * rx + d1y * ry + d1u * ru;
            if (e <= 1e-12) {
                t = 0; s = clamp(-c / a);
            } else {
                double b = d1x * d2x + d1y * d2y + d1u * d2u;
                double denom = a * e - b * b;
                s = denom > 1e-12 ? clamp((b * f - c * e) / denom) : 0;
                t = (b * s + f) / e;
                if (t < 0) { t = 0; s = clamp(-c / a); }
                else if (t > 1) { t = 1; s = clamp((b - c) / a); }
            }
        }
        double cx = p0.x + d1x * s - (q0.x + d2x * t);
        double cy = p0.y + d1y * s - (q0.y + d2y * t);
        double cu = p0.u + d1u * s - (q0.u + d2u * t);
        out[0] = s;
        out[1] = t;
        return cx * cx + cy * cy + cu * cu;
    }

    /**
     * Entry fraction of segment a->b into the capsule (q0,q1,r), or -1 if it misses. The entry point
     * is approximated from the closest approach, which is exact for spheres and for rounds crossing the
     * capsule square-on, and within a few millimetres otherwise.
     */
    public static double capsuleEntry(V3 a, V3 b, V3 q0, V3 q1, double r, double[] tmp) {
        double d2 = closest(a, b, q0, q1, tmp);
        if (d2 > r * r) return -1;
        double len = a.dist(b);
        if (len < 1e-9) return 0;
        double back = Math.sqrt(Math.max(0, r * r - d2)) / len;
        return Math.max(0, tmp[0] - back);
    }

    /** Squared 2D distance from point (px,py) to the horizontal projection of segment a->b. */
    public static double dist2D2(V3 a, V3 b, double px, double py) {
        double dx = b.x - a.x, dy = b.y - a.y;
        double l2 = dx * dx + dy * dy;
        double t = l2 > 1e-12 ? clamp(((px - a.x) * dx + (py - a.y) * dy) / l2) : 0;
        double cx = a.x + dx * t - px, cy = a.y + dy * t - py;
        return cx * cx + cy * cy;
    }

    static double clamp(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }

    private SegmentMath() {}
}
