package t3gunworks;

/** Small mutable double vector in metric space (x, y, u=up). Reused, never allocated per frame. */
public final class V3 {
    public double x, y, u;

    public V3() {}

    public V3(double x, double y, double u) { this.x = x; this.y = y; this.u = u; }

    public V3 set(double x, double y, double u) { this.x = x; this.y = y; this.u = u; return this; }

    public V3 set(V3 o) { x = o.x; y = o.y; u = o.u; return this; }

    public double length() { return Math.sqrt(x * x + y * y + u * u); }

    public double dot(V3 o) { return x * o.x + y * o.y + u * o.u; }

    public V3 normalize() {
        double l = length();
        if (l > 1e-12) { x /= l; y /= l; u /= l; }
        return this;
    }

    public double dist(V3 o) {
        double dx = x - o.x, dy = y - o.y, du = u - o.u;
        return Math.sqrt(dx * dx + dy * dy + du * du);
    }

    /** this = a + (b - a) * t */
    public V3 lerp(V3 a, V3 b, double t) {
        x = a.x + (b.x - a.x) * t; y = a.y + (b.y - a.y) * t; u = a.u + (b.u - a.u) * t;
        return this;
    }

    public boolean finite() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(u);
    }

    @Override public String toString() { return String.format("(%.3f, %.3f, %.3f)", x, y, u); }
}
