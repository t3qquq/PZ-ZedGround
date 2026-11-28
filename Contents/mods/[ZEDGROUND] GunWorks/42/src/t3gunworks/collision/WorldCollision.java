package t3gunworks.collision;

import t3gunworks.BallisticsUnits;
import t3gunworks.V3;
import t3gunworks.config.BallisticsConfig;

/**
 * Swept segment vs. the tile world, using a 3D grid traversal (Amanatides & Woo) over tiles and floor
 * levels. Every tile edge, floor and ceiling the segment crosses is tested in order, so a round can never
 * tunnel through a wall however far it moves in one step, and the first blocking crossing is exact.
 *
 * Pure logic over a {@link WorldQuery}; allocates nothing.
 */
public final class WorldCollision {

    public static final int HIT_NONE = 0;
    public static final int HIT_WALL = 1;       // wall, closed door, barricade
    public static final int HIT_LOW = 2;        // low wall / fence
    public static final int HIT_FLOOR = 3;      // ground or building floor (moving down)
    public static final int HIT_CEILING = 4;    // underside of the level above (moving up)
    public static final int HIT_TREE = 5;
    public static final int HIT_FURNITURE = 6;
    public static final int HIT_VOID = 7;       // left the valid level range

    private static final double TREE_RADIUS = 0.18;
    private static final double TREE_HEIGHT = 6.0;
    private static final double FURNITURE_INSET = 0.15;
    private static final double SOLID_HEIGHT = 1.9;
    private static final double LOW_SOLID_HEIGHT = 0.9;
    private static final int MAX_STEPS = 8192;
    private static final int MIN_LEVEL = -32, MAX_LEVEL = 63;

    /** Result of a trace. */
    public static final class Hit {
        public int kind;
        public double t;               // fraction along the segment
        public final V3 point = new V3();
        public int tileX, tileY, tileZ; // tile used for impact effects (the one the round was in)
        /** Unit surface normal at the hit, facing the round (x, y, up). Zero for HIT_VOID. */
        public double nx, ny, nu;
        /** Wall hits: the edge crossed (+-1 on one axis); the tile on the far side holds the wall object. */
        public int edgeDx, edgeDy;

        public void clear() { kind = HIT_NONE; t = 1.0; nx = ny = nu = 0; edgeDx = edgeDy = 0; }
    }

    /** Windows crossed before the hit (in order), so the caller can break them. */
    public static final class Crossings {
        public final double[] t = new double[16];
        public final Object[] window = new Object[16];
        public int count;

        public void clear() {
            for (int i = 0; i < count; i++) window[i] = null;
            count = 0;
        }

        void add(double tt, Object w) {
            if (count < t.length) { t[count] = tt; window[count] = w; count++; }
        }
    }

    /**
     * Traces a -> b. Returns true if something solid was hit (details in out). Windows crossed are added
     * to crossings when it is not null; with crossings == null windows are treated as transparent glass
     * (used for the non-destructive camera aim ray).
     */
    public static boolean trace(V3 a, V3 b, WorldQuery w, Hit out, Crossings crossings) {
        out.clear();
        final double L = BallisticsUnits.LEVEL_HEIGHT_M;
        double dx = b.x - a.x, dy = b.y - a.y, du = b.u - a.u;

        int ix = (int) Math.floor(a.x), iy = (int) Math.floor(a.y), iz = BallisticsUnits.levelOf(a.u);
        int sx = dx > 0 ? 1 : (dx < 0 ? -1 : 0);
        int sy = dy > 0 ? 1 : (dy < 0 ? -1 : 0);
        int sz = du > 0 ? 1 : (du < 0 ? -1 : 0);

        double tMaxX = sx != 0 ? ((sx > 0 ? ix + 1 : ix) - a.x) / dx : Double.POSITIVE_INFINITY;
        double tMaxY = sy != 0 ? ((sy > 0 ? iy + 1 : iy) - a.y) / dy : Double.POSITIVE_INFINITY;
        double tMaxZ = sz != 0 ? (((sz > 0 ? iz + 1 : iz) * L) - a.u) / du : Double.POSITIVE_INFINITY;
        double tDX = sx != 0 ? 1.0 / Math.abs(dx) : Double.POSITIVE_INFINITY;
        double tDY = sy != 0 ? 1.0 / Math.abs(dy) : Double.POSITIVE_INFINITY;
        double tDZ = sz != 0 ? L / Math.abs(du) : Double.POSITIVE_INFINITY;

        double tEnter = 0.0;
        for (int step = 0; step < MAX_STEPS; step++) {
            if (iz < MIN_LEVEL || iz > MAX_LEVEL) return hit(out, HIT_VOID, tEnter, a, b, ix, iy, iz);

            double tExit = Math.min(Math.min(tMaxX, tMaxY), Math.min(tMaxZ, 1.0));

            // things standing inside this tile
            int c = w.cell(ix, iy, iz);
            if (c != WorldQuery.CELL_EMPTY) {
                double tc = cellFeature(c, a, dx, dy, du, ix, iy, iz, tEnter, tExit);
                if (tc >= 0) {
                    hit(out, c == WorldQuery.CELL_TREE ? HIT_TREE : HIT_FURNITURE, tc, a, b, ix, iy, iz);
                    featureNormal(out, c, dx, dy, du, ix, iy, iz);
                    return true;
                }
            }
            if (tExit >= 1.0) return false;

            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                double t = tMaxX;
                int e = w.edge(ix, iy, iz, sx, 0);
                if (edgeStops(e, w, a.u + du * t - iz * L, t, crossings)) {
                    return hitN(out, e == WorldQuery.EDGE_LOW ? HIT_LOW : HIT_WALL, t, a, b, ix, iy, iz, -sx, 0, 0, sx, 0);
                }
                ix += sx;
                if (iz < 0 && !w.exists(ix, iy, iz)) return hitN(out, HIT_WALL, t, a, b, ix - sx, iy, iz, -sx, 0, 0, sx, 0); // solid earth
                tEnter = t;
                tMaxX += tDX;
            } else if (tMaxY <= tMaxZ) {
                double t = tMaxY;
                int e = w.edge(ix, iy, iz, 0, sy);
                if (edgeStops(e, w, a.u + du * t - iz * L, t, crossings)) {
                    return hitN(out, e == WorldQuery.EDGE_LOW ? HIT_LOW : HIT_WALL, t, a, b, ix, iy, iz, 0, -sy, 0, 0, sy);
                }
                iy += sy;
                if (iz < 0 && !w.exists(ix, iy, iz)) return hitN(out, HIT_WALL, t, a, b, ix, iy - sy, iz, 0, -sy, 0, 0, sy); // solid earth
                tEnter = t;
                tMaxY += tDY;
            } else {
                double t = tMaxZ;
                if (sz > 0) {
                    if (w.solidFloor(ix, iy, iz + 1)) return hitN(out, HIT_CEILING, t, a, b, ix, iy, iz, 0, 0, -1, 0, 0);
                } else {
                    if (w.solidFloor(ix, iy, iz)) return hitN(out, HIT_FLOOR, t, a, b, ix, iy, iz, 0, 0, 1, 0, 0);
                }
                iz += sz;
                tEnter = t;
                tMaxZ += tDZ;
            }
        }
        return hit(out, HIT_VOID, tEnter, a, b, ix, iy, iz);
    }

    private static boolean edgeStops(int e, WorldQuery w, double heightAboveFloor, double t, Crossings crossings) {
        switch (e) {
            case WorldQuery.EDGE_SOLID:
                return true;
            case WorldQuery.EDGE_LOW:
                return heightAboveFloor < BallisticsConfig.LOW_OBSTACLE_HEIGHT;
            case WorldQuery.EDGE_WINDOW:
                if (heightAboveFloor < PzWorld.WINDOW_SILL || heightAboveFloor > PzWorld.OPENING_TOP) return true;
                if (crossings != null) crossings.add(t, w.lastWindow());
                return false;
            case WorldQuery.EDGE_FRAME:
                return heightAboveFloor < PzWorld.WINDOW_SILL || heightAboveFloor > PzWorld.OPENING_TOP;
            case WorldQuery.EDGE_DOORWAY:
                return heightAboveFloor > PzWorld.OPENING_TOP;
            default:
                return false;
        }
    }

    private static boolean hit(Hit out, int kind, double t, V3 a, V3 b, int x, int y, int z) {
        out.kind = kind;
        out.t = Math.max(0.0, Math.min(1.0, t));
        out.point.lerp(a, b, out.t);
        out.tileX = x;
        out.tileY = y;
        out.tileZ = z;
        return true;
    }

    private static boolean hitN(Hit out, int kind, double t, V3 a, V3 b, int x, int y, int z,
                                double nx, double ny, double nu, int edx, int edy) {
        hit(out, kind, t, a, b, x, y, z);
        out.nx = nx; out.ny = ny; out.nu = nu;
        out.edgeDx = edx; out.edgeDy = edy;
        return true;
    }

    /** Normal of a tree trunk (radial) or furniture box (the face entered) at out.point. */
    private static void featureNormal(Hit out, int c, double dx, double dy, double du, int ix, int iy, int iz) {
        V3 p = out.point;
        if (c == WorldQuery.CELL_TREE) {
            double rx = p.x - (ix + 0.5), ry = p.y - (iy + 0.5), l = Math.hypot(rx, ry);
            if (l < 1e-9) { rx = -dx; ry = -dy; l = Math.hypot(rx, ry); }
            if (l < 1e-9) { out.nu = du > 0 ? -1 : 1; return; }
            out.nx = rx / l; out.ny = ry / l;
            return;
        }
        double floor = iz * BallisticsUnits.LEVEL_HEIGHT_M;
        double top = floor + (c == WorldQuery.CELL_SOLID ? SOLID_HEIGHT : LOW_SOLID_HEIGHT);
        double x0 = ix + FURNITURE_INSET, x1 = ix + 1 - FURNITURE_INSET, y0 = iy + FURNITURE_INSET, y1 = iy + 1 - FURNITURE_INSET;
        // the face the point lies on (closest), preferring faces the round moves into
        double best = Double.POSITIVE_INFINITY;
        double d;
        if (dx > 0 && (d = Math.abs(p.x - x0)) < best) { best = d; out.nx = -1; out.ny = 0; out.nu = 0; }
        if (dx < 0 && (d = Math.abs(p.x - x1)) < best) { best = d; out.nx = 1; out.ny = 0; out.nu = 0; }
        if (dy > 0 && (d = Math.abs(p.y - y0)) < best) { best = d; out.nx = 0; out.ny = -1; out.nu = 0; }
        if (dy < 0 && (d = Math.abs(p.y - y1)) < best) { best = d; out.nx = 0; out.ny = 1; out.nu = 0; }
        if (du < 0 && (d = Math.abs(p.u - top)) < best) { best = d; out.nx = 0; out.ny = 0; out.nu = 1; }
        if (du > 0 && (d = Math.abs(p.u - floor)) < best) { out.nx = 0; out.ny = 0; out.nu = -1; }
    }

    /** First entry time into the feature of tile (ix,iy,iz) within [t0,t1], or -1. */
    private static double cellFeature(int c, V3 a, double dx, double dy, double du, int ix, int iy, int iz,
                                      double t0, double t1) {
        double floor = iz * BallisticsUnits.LEVEL_HEIGHT_M;
        if (c == WorldQuery.CELL_TREE) {
            return cylinder(a, dx, dy, du, ix + 0.5, iy + 0.5, TREE_RADIUS, floor, floor + TREE_HEIGHT, t0, t1);
        }
        if (!BallisticsConfig.furnitureCollision) return -1;
        double top = floor + (c == WorldQuery.CELL_SOLID ? SOLID_HEIGHT : LOW_SOLID_HEIGHT);
        return box(a, dx, dy, du, ix + FURNITURE_INSET, iy + FURNITURE_INSET, floor, ix + 1 - FURNITURE_INSET,
            iy + 1 - FURNITURE_INSET, top, t0, t1);
    }

    /** Segment p(t)=a+d*t vs vertical cylinder; first t in [t0,t1] or -1. */
    static double cylinder(V3 a, double dx, double dy, double du, double cx, double cy, double r,
                           double uMin, double uMax, double t0, double t1) {
        double ox = a.x - cx, oy = a.y - cy;
        double A = dx * dx + dy * dy;
        double tHit;
        if (A < 1e-12) {
            if (ox * ox + oy * oy > r * r) return -1;
            tHit = t0; // moving vertically inside the trunk
        } else {
            double B = 2 * (ox * dx + oy * dy);
            double C = ox * ox + oy * oy - r * r;
            double disc = B * B - 4 * A * C;
            if (disc < 0) return -1;
            double sq = Math.sqrt(disc);
            double tIn = (-B - sq) / (2 * A), tOut = (-B + sq) / (2 * A);
            if (tOut < t0 || tIn > t1) return -1;
            tHit = Math.max(tIn, t0);
        }
        double u = a.u + du * tHit;
        return (u >= uMin && u <= uMax && tHit <= t1) ? tHit : -1;
    }

    /** Segment vs axis-aligned box (slab test); first t in [t0,t1] or -1. */
    static double box(V3 a, double dx, double dy, double du, double x0, double y0, double u0,
                      double x1, double y1, double u1, double t0, double t1) {
        double tn = t0, tf = t1;
        // x slab
        if (Math.abs(dx) < 1e-12) { if (a.x < x0 || a.x > x1) return -1; }
        else {
            double ta = (x0 - a.x) / dx, tb = (x1 - a.x) / dx;
            tn = Math.max(tn, Math.min(ta, tb)); tf = Math.min(tf, Math.max(ta, tb));
            if (tn > tf) return -1;
        }
        // y slab
        if (Math.abs(dy) < 1e-12) { if (a.y < y0 || a.y > y1) return -1; }
        else {
            double ta = (y0 - a.y) / dy, tb = (y1 - a.y) / dy;
            tn = Math.max(tn, Math.min(ta, tb)); tf = Math.min(tf, Math.max(ta, tb));
            if (tn > tf) return -1;
        }
        // up slab
        if (Math.abs(du) < 1e-12) { if (a.u < u0 || a.u > u1) return -1; }
        else {
            double ta = (u0 - a.u) / du, tb = (u1 - a.u) / du;
            tn = Math.max(tn, Math.min(ta, tb)); tf = Math.min(tf, Math.max(ta, tb));
            if (tn > tf) return -1;
        }
        return tn;
    }

    private WorldCollision() {}
}
