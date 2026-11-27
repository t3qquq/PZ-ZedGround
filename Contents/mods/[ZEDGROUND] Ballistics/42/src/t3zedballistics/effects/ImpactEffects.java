package t3zedballistics.effects;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import t3zedballistics.BallisticsUnits;
import t3zedballistics.V3;
import t3zedballistics.aiming.ViewpointBridge;
import t3zedballistics.collision.PzWorld;
import t3zedballistics.collision.WorldCollision;
import t3zedballistics.collision.WorldQuery;
import t3zedballistics.weapon.BallisticProfile;
import zombie.iso.IsoGridSquare;

/**
 * Bullet holes and impact debris for rounds that hit the world (walls, floors, ground, trees, furniture).
 *
 *   - Bullet holes: a decal on the surface the round actually hit, sized from the calibre (the hole) and the
 *     material (the crater around it: concrete and brick spall wide, plaster chips, wood splinters, metal
 *     punches a clean hole with a bright rim, earth leaves a small divot). Holes stay until the surface is
 *     gone (door opened, wall destroyed, floor removed), they expire, or the cap is reached (oldest first).
 *   - Impact debris: what the impact throws out, by material - dust puffs (masonry/plaster dust coloured by
 *     the material), chips and splinters that fly and fall with gravity and bounce on the floor, sparks off
 *     metal (and sometimes stone/concrete), earth sprays and clods from the ground, glass glints, fibres.
 *     Amount and speed scale with the round's remaining energy, ejection follows the surface normal and the
 *     ricochet direction.
 *
 * Everything here is client-side presentation: it never changes what a round hits or damages. The main
 * thread spawns, simulates and captures per frame (game metric: x, y, up in metres); the render thread draws
 * the captured snapshot inside Viewpoint's 3D view (ImpactDraw). In multiplayer every client sees the holes
 * and debris of other players' rounds (relayed by ZGB_Client.lua / ZGB_Server.lua).
 */
public final class ImpactEffects {
    public static final ImpactEffects INSTANCE = new ImpactEffects();

    // ---------------------------------------------------------------- settings (settings.ini)
    public static volatile boolean enabled = true;
    public static volatile int maxHoles = 400;
    public static volatile double holeMinutes = 30.0;
    public static volatile double debrisAmount = 1.0;

    static final int HARD_MAX_HOLES = 2000, MAX_PARTICLES = 3000;
    static final double DRAW_DISTANCE = 90.0;
    static final double SURFACE_OFFSET = 0.004;
    static final double G = 9.81;

    // ---------------------------------------------------------------- holes (ring buffer, main thread)
    final double[] hx = new double[HARD_MAX_HOLES], hy = new double[HARD_MAX_HOLES], hu = new double[HARD_MAX_HOLES];
    final float[] hnx = new float[HARD_MAX_HOLES], hny = new float[HARD_MAX_HOLES], hnu = new float[HARD_MAX_HOLES];
    final float[] hSize = new float[HARD_MAX_HOLES], hCore = new float[HARD_MAX_HOLES], hSeed = new float[HARD_MAX_HOLES];
    final byte[] hFamily = new byte[HARD_MAX_HOLES], hKind = new byte[HARD_MAX_HOLES];
    final int[] hTx = new int[HARD_MAX_HOLES], hTy = new int[HARD_MAX_HOLES], hTz = new int[HARD_MAX_HOLES];
    final byte[] hEdx = new byte[HARD_MAX_HOLES], hEdy = new byte[HARD_MAX_HOLES];
    final long[] hBorn = new long[HARD_MAX_HOLES];
    int holeHead, holeCount;
    private int validateCursor;
    private long lastValidate;

    // ---------------------------------------------------------------- particles (main thread)
    static final int P_DUST = 0, P_CHIP = 1, P_SPARK = 2, P_SPLINTER = 3, P_GLINT = 4;
    final double[] px = new double[MAX_PARTICLES], py = new double[MAX_PARTICLES], pu = new double[MAX_PARTICLES];
    final double[] pvx = new double[MAX_PARTICLES], pvy = new double[MAX_PARTICLES], pvu = new double[MAX_PARTICLES];
    final float[] pAge = new float[MAX_PARTICLES], pLife = new float[MAX_PARTICLES], pSize = new float[MAX_PARTICLES],
        pGrow = new float[MAX_PARTICLES], pR = new float[MAX_PARTICLES], pG = new float[MAX_PARTICLES],
        pB = new float[MAX_PARTICLES], pA = new float[MAX_PARTICLES], pDrag = new float[MAX_PARTICLES],
        pGrav = new float[MAX_PARTICLES], pFloor = new float[MAX_PARTICLES];
    final byte[] pType = new byte[MAX_PARTICLES];
    int particleCount;

    private long lastNanos;
    private double pelletScale = 1.0;
    private final java.util.Random rnd = new java.util.Random();
    private final PzWorld world = new PzWorld();

    /** Snapshot per Viewpoint scene for the render thread: [holes..., particles...] (see capture). */
    static final Map<Object, float[]> snapshots = Collections.synchronizedMap(new WeakHashMap<>());
    static final int HOLE_FLOATS = 12, PARTICLE_FLOATS = 12;

    // ---------------------------------------------------------------- network queue (local player impacts)
    static final int NET_FLOATS = 13;
    private final double[] netQueue = new double[64 * NET_FLOATS];
    private int netCount;
    private long netLastSend;

    private ImpactEffects() {}

    // =================================================================================== spawning

    /**
     * A round hit the world. point / normal in game metric (x, y, up), dir = the round's flight direction
     * (unit), energyRatio = remaining energy / muzzle energy. network = true for the local player's own
     * rounds (other clients get it through the relay).
     */
    public void impact(V3 point, double nx, double ny, double nu, V3 dir, int kind, int tileX, int tileY, int tileZ,
                       int edgeDx, int edgeDy, int material, BallisticProfile prof, double energyRatio, boolean network) {
        if (!enabled || point == null || !point.finite() || kind == WorldCollision.HIT_VOID) return;
        double cal = calibreMm(prof);
        pelletScale = prof != null && prof.pelletCount > 1 ? 0.35 : 1.0; // a pellet throws far less than a bullet
        spawn(point.x, point.y, point.u, nx, ny, nu, dir.x, dir.y, dir.u, kind, tileX, tileY, tileZ, edgeDx, edgeDy,
            material, cal, energyRatio, true);
        if (network) queue(point, nx, ny, nu, dir, kind, material, cal, energyRatio);
    }

    /** Debris only (another mod's moving object: a hole would hang in the air once it moves). */
    public void debrisOnly(V3 point, V3 dir, int tileX, int tileY, int tileZ, int material, BallisticProfile prof, double energyRatio) {
        skipHole = true;
        try {
            impact(point, 0, 0, 0, dir, WorldCollision.HIT_FURNITURE, tileX, tileY, tileZ, 0, 0, material, prof, energyRatio, false);
        } finally {
            skipHole = false;
        }
    }

    private boolean skipHole;

    /** Hole + debris; also used for remote impacts (tile and edge derived from the point and normal). */
    void spawn(double x, double y, double u, double nx, double ny, double nu, double dx, double dy, double du,
               int kind, int tx, int ty, int tz, int edx, int edy, int material, double calMm, double energyRatio,
               boolean light) {
        double nl = Math.sqrt(nx * nx + ny * ny + nu * nu);
        if (!(nl > 1e-6)) {
            // no surface normal: face the round
            double dl = Math.sqrt(dx * dx + dy * dy + du * du);
            if (!(dl > 1e-6)) return;
            nx = -dx / dl; ny = -dy / dl; nu = -du / dl;
        } else {
            nx /= nl; ny /= nl; nu /= nl;
        }
        int family = ImpactMaterial.family(material);
        double e = Math.max(0.05, Math.min(1.0, energyRatio));
        double brightness = light ? lightAt(tx, ty, tz) : 0.6;
        if (!skipHole) addHole(x, y, u, nx, ny, nu, kind, tx, ty, tz, edx, edy, family, calMm, e);
        addDebris(x, y, u, nx, ny, nu, dx, dy, du, tz, material, family, calMm, e, brightness);
    }

    private void addHole(double x, double y, double u, double nx, double ny, double nu, int kind, int tx, int ty, int tz,
                         int edx, int edy, int family, double calMm, double e) {
        int cap = Math.max(0, Math.min(HARD_MAX_HOLES, maxHoles));
        if (cap == 0 || family == ImpactMaterial.F_GLASS) return;
        double hole = calMm / 2000.0; // radius of the hole itself (m)
        double crater = switch (family) {
            case ImpactMaterial.F_MASONRY -> 5.0 + 3.0 * e;   // spall: a 5.56 leaves a 4-5 cm crater in concrete
            case ImpactMaterial.F_WOOD -> 3.0 + 1.5 * e;      // splinters
            case ImpactMaterial.F_METAL -> 1.8;               // clean punch, bright rim
            case ImpactMaterial.F_EARTH -> 5.0 + 2.0 * e;     // divot
            case ImpactMaterial.F_CERAMIC -> 6.0;             // cracked tile
            default -> 1.6;                                   // fabric, carpet, plastic, rubber
        };
        double size = Math.max(0.006, hole * crater); // decal radius
        int i = holeHead;
        holeHead = (holeHead + 1) % cap;
        if (holeCount < cap) holeCount++;
        hx[i] = x + nx * SURFACE_OFFSET; hy[i] = y + ny * SURFACE_OFFSET; hu[i] = u + nu * SURFACE_OFFSET;
        hnx[i] = (float) nx; hny[i] = (float) ny; hnu[i] = (float) nu;
        hSize[i] = (float) size;
        hCore[i] = (float) Math.min(0.9, hole / size);
        hSeed[i] = rnd.nextFloat() * 100f;
        hFamily[i] = (byte) family;
        hKind[i] = (byte) kind;
        hTx[i] = tx; hTy[i] = ty; hTz[i] = tz;
        hEdx[i] = (byte) edx; hEdy[i] = (byte) edy;
        hBorn[i] = System.nanoTime();
    }

    private void addDebris(double x, double y, double u, double nx, double ny, double nu, double dx, double dy, double du,
                           int tz, int material, int family, double calMm, double e, double light) {
        double amount = Math.max(0, debrisAmount) * pelletScale * (0.55 + 0.45 * e) * Math.min(1.6, 0.6 + calMm / 12.0);
        pelletScale = 1.0;
        if (amount <= 0) return;
        // ricochet direction (the round's direction reflected on the surface)
        double dl = Math.sqrt(dx * dx + dy * dy + du * du);
        if (dl > 1e-9) { dx /= dl; dy /= dl; du /= dl; }
        double dn = dx * nx + dy * ny + du * nu;
        double rx = dx - 2 * dn * nx, ry = dy - 2 * dn * ny, ru = du - 2 * dn * nu;
        float floor = (float) (tz * BallisticsUnits.LEVEL_HEIGHT_M);
        float[] c = dustColour(material);
        float lr = (float) light;

        switch (family) {
            case ImpactMaterial.F_MASONRY -> {
                burst(P_DUST, n(7 * amount), x, y, u, nx, ny, nu, rx, ry, ru, 0.4, 1.4, 0.55, 0.06, 0.32, 1.4, 2.6,
                    c[0] * lr, c[1] * lr, c[2] * lr, 0.55f, 3.5f, 0.05f, floor);
                burst(P_CHIP, n(8 * amount), x, y, u, nx, ny, nu, rx, ry, ru, 2.0, 5.5, 0.9, 0.008, 0.0, 1.2, 2.0,
                    c[0] * lr * 0.8f, c[1] * lr * 0.8f, c[2] * lr * 0.8f, 1f, 0.4f, 1f, floor);
                if (material == ImpactMaterial.CONCRETE || material == ImpactMaterial.STONE || material == ImpactMaterial.BRICK
                        || material == ImpactMaterial.CINDERBLOCK) {
                    if (rnd.nextDouble() < 0.35 * e) sparks(n(3 * amount), x, y, u, nx, ny, nu, rx, ry, ru, floor);
                }
            }
            case ImpactMaterial.F_WOOD -> {
                burst(P_SPLINTER, n(8 * amount), x, y, u, nx, ny, nu, rx, ry, ru, 2.0, 5.0, 0.8, 0.012, 0.0, 1.0, 1.8,
                    c[0] * lr, c[1] * lr, c[2] * lr, 1f, 0.5f, 1f, floor);
                burst(P_DUST, n(3 * amount), x, y, u, nx, ny, nu, rx, ry, ru, 0.3, 1.0, 0.5, 0.05, 0.22, 1.0, 1.8,
                    c[0] * lr, c[1] * lr, c[2] * lr, 0.4f, 3.5f, 0.05f, floor);
            }
            case ImpactMaterial.F_METAL -> {
                sparks(n(12 * amount), x, y, u, nx, ny, nu, rx, ry, ru, floor);
                burst(P_DUST, n(2 * amount), x, y, u, nx, ny, nu, rx, ry, ru, 0.3, 0.8, 0.5, 0.04, 0.15, 0.6, 1.0,
                    0.45f * lr, 0.45f * lr, 0.45f * lr, 0.35f, 3.5f, 0.05f, floor);
            }
            case ImpactMaterial.F_EARTH -> {
                // a spray of earth thrown up and out, clods that fall back, and a low dust cloud
                burst(P_CHIP, n(12 * amount), x, y, u, nx, ny, nu, rx, ry, ru, 2.0, 5.0, 0.6, 0.012, 0.0, 0.9, 1.5,
                    c[0] * lr * 0.7f, c[1] * lr * 0.7f, c[2] * lr * 0.7f, 1f, 0.3f, 1f, floor);
                burst(P_DUST, n(6 * amount), x, y, u, nx, ny, nu, rx, ry, ru, 0.6, 1.8, 0.45, 0.07, 0.38, 1.2, 2.2,
                    c[0] * lr, c[1] * lr, c[2] * lr, 0.5f, 3.0f, 0.08f, floor);
            }
            case ImpactMaterial.F_GLASS -> burst(P_GLINT, n(8 * amount), x, y, u, nx, ny, nu, rx, ry, ru, 1.5, 4.0, 0.9,
                0.008, 0.0, 0.8, 1.4, 0.9f, 0.95f, 1.0f, 1f, 0.3f, 1f, floor);
            case ImpactMaterial.F_CERAMIC -> {
                burst(P_CHIP, n(9 * amount), x, y, u, nx, ny, nu, rx, ry, ru, 2.0, 5.0, 0.9, 0.009, 0.0, 1.0, 1.6,
                    0.9f * lr, 0.9f * lr, 0.88f * lr, 1f, 0.4f, 1f, floor);
                burst(P_DUST, n(3 * amount), x, y, u, nx, ny, nu, rx, ry, ru, 0.3, 1.0, 0.5, 0.05, 0.2, 0.9, 1.6,
                    0.85f * lr, 0.85f * lr, 0.83f * lr, 0.4f, 3.5f, 0.05f, floor);
            }
            default -> {
                burst(P_DUST, n(3 * amount), x, y, u, nx, ny, nu, rx, ry, ru, 0.2, 0.8, 0.5, 0.04, 0.14, 0.6, 1.2,
                    c[0] * lr, c[1] * lr, c[2] * lr, 0.35f, 3.5f, 0.05f, floor);
                burst(P_SPLINTER, n(4 * amount), x, y, u, nx, ny, nu, rx, ry, ru, 1.0, 2.5, 0.8, 0.008, 0.0, 0.7, 1.2,
                    c[0] * lr, c[1] * lr, c[2] * lr, 1f, 1.0f, 0.7f, floor);
            }
        }
    }

    private int n(double v) {
        int base = (int) Math.floor(v);
        return base + (rnd.nextDouble() < v - base ? 1 : 0);
    }

    private void sparks(int count, double x, double y, double u, double nx, double ny, double nu, double rx, double ry,
                        double ru, float floor) {
        burst(P_SPARK, count, x, y, u, nx, ny, nu, rx, ry, ru, 4.0, 11.0, 1.1, 0.006, 0.0, 0.25, 0.6,
            2.6f, 1.5f, 0.55f, 1f, 0.6f, 0.6f, floor);
    }

    /**
     * Emits count particles: direction = surface normal + share of the ricochet + random cone; speed in
     * [v0, v1]; spread = cone width; size s0 growing by grow per second (dust); life in [l0, l1].
     */
    private void burst(int type, int count, double x, double y, double u, double nx, double ny, double nu,
                       double rx, double ry, double ru, double v0, double v1, double spread, double s0, double grow,
                       double l0, double l1, float r, float g, float b, float a, float drag, float gravity, float floor) {
        for (int k = 0; k < count && particleCount < MAX_PARTICLES; k++) {
            int i = particleCount++;
            double mix = 0.35 + 0.4 * rnd.nextDouble();
            double ex = nx * (1 - mix) + rx * mix + rnd.nextGaussian() * spread * 0.5;
            double ey = ny * (1 - mix) + ry * mix + rnd.nextGaussian() * spread * 0.5;
            double eu = nu * (1 - mix) + ru * mix + rnd.nextGaussian() * spread * 0.5;
            // never into the surface
            double into = ex * nx + ey * ny + eu * nu;
            if (into < 0.05) { ex -= (into - 0.05) * nx; ey -= (into - 0.05) * ny; eu -= (into - 0.05) * nu; }
            double l = Math.sqrt(ex * ex + ey * ey + eu * eu);
            if (l < 1e-9) { ex = nx; ey = ny; eu = nu; l = 1; }
            double v = v0 + (v1 - v0) * rnd.nextDouble();
            px[i] = x + nx * 0.01; py[i] = y + ny * 0.01; pu[i] = u + nu * 0.01;
            pvx[i] = ex / l * v; pvy[i] = ey / l * v; pvu[i] = eu / l * v;
            pAge[i] = 0;
            pLife[i] = (float) (l0 + (l1 - l0) * rnd.nextDouble());
            pSize[i] = (float) (s0 * (0.7 + 0.6 * rnd.nextDouble()));
            pGrow[i] = (float) grow;
            float shade = type == P_SPARK || type == P_GLINT ? 1f : (float) (0.85 + 0.3 * rnd.nextDouble());
            pR[i] = r * shade; pG[i] = g * shade; pB[i] = b * shade; pA[i] = a;
            pDrag[i] = drag;
            pGrav[i] = gravity;
            pFloor[i] = floor;
            pType[i] = (byte) type;
        }
    }

    /** Dust / debris colour of a material (linear-ish, before scene light). */
    static float[] dustColour(int m) {
        return switch (m) {
            case ImpactMaterial.PLASTER -> new float[]{0.86f, 0.84f, 0.80f};
            case ImpactMaterial.BRICK -> new float[]{0.62f, 0.36f, 0.27f};
            case ImpactMaterial.STONE, ImpactMaterial.GRAVEL -> new float[]{0.55f, 0.53f, 0.50f};
            case ImpactMaterial.CINDERBLOCK -> new float[]{0.62f, 0.61f, 0.58f};
            case ImpactMaterial.WOOD, ImpactMaterial.WOOD_SOLID -> new float[]{0.62f, 0.46f, 0.30f};
            case ImpactMaterial.DIRT -> new float[]{0.42f, 0.33f, 0.22f};
            case ImpactMaterial.GRASS -> new float[]{0.36f, 0.34f, 0.20f};
            case ImpactMaterial.SAND -> new float[]{0.76f, 0.68f, 0.50f};
            case ImpactMaterial.SNOW -> new float[]{0.95f, 0.96f, 0.98f};
            case ImpactMaterial.CERAMIC -> new float[]{0.90f, 0.90f, 0.88f};
            case ImpactMaterial.CARPET, ImpactMaterial.FABRIC -> new float[]{0.50f, 0.48f, 0.46f};
            case ImpactMaterial.PLASTIC, ImpactMaterial.RUBBER -> new float[]{0.35f, 0.35f, 0.36f};
            case ImpactMaterial.METAL, ImpactMaterial.METAL_LARGE, ImpactMaterial.METAL_LIGHT, ImpactMaterial.METAL_SOLID ->
                new float[]{0.50f, 0.50f, 0.52f};
            default -> new float[]{0.62f, 0.60f, 0.57f}; // concrete
        };
    }

    /** Hole diameter in mm from the ammunition profile. */
    static double calibreMm(BallisticProfile prof) {
        if (prof == null) return 9.0;
        String c = prof.category == null ? "" : prof.category;
        if (prof.pelletCount > 1) return 8.4;            // 00 buck
        if (c.contains("slug")) return 18.5;
        if (c.contains("heavy")) return 12.7;
        if (c.contains("full-power")) return 7.8;
        if (c.contains("subsonic")) return 7.8;
        if (c.contains("intermediate")) return 5.7;
        if (c.contains("PDW")) return 5.7;
        if (c.contains("rimfire")) return 5.6;
        if (c.contains("magnum")) return 10.9;
        if (c.contains("handgun")) return 9.0;
        double m = prof.massGrams;
        return m > 30 ? 13 : m > 12 ? 9 : m > 6 ? 8 : 5.7;
    }

    private double lightAt(int x, int y, int z) {
        try {
            world.bind();
            IsoGridSquare s = world.square(x, y, z);
            if (s == null) return 0.6;
            double l = s.getLightLevel(0);
            return Double.isFinite(l) ? Math.max(0.06, Math.min(1.0, l)) : 0.6;
        } catch (RuntimeException | LinkageError e) {
            return 0.6;
        }
    }

    // =================================================================================== per frame

    /** Main thread, once per Viewpoint frame: simulate debris, expire holes, capture for the render thread. */
    public void frame(Object scene, float camX, float camY, float camZ) {
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min(0.25, (now - lastNanos) * 1e-9);
        lastNanos = now;
        if (!enabled) {
            if (holeCount > 0 || particleCount > 0) clear();
            snapshots.remove(scene);
            return;
        }
        simulate(dt);
        expireAndValidate(now);
        capture(scene, camX, camY, camZ, now);
    }

    void simulate(double dt) {
        if (dt <= 0) return;
        int w = 0;
        for (int i = 0; i < particleCount; i++) {
            pAge[i] += (float) dt;
            if (pAge[i] >= pLife[i]) continue;
            double k = Math.exp(-pDrag[i] * dt);
            pvx[i] *= k; pvy[i] *= k; pvu[i] *= k;
            pvu[i] -= G * pGrav[i] * dt;
            px[i] += pvx[i] * dt; py[i] += pvy[i] * dt; pu[i] += pvu[i] * dt;
            if (pType[i] == P_DUST) pSize[i] += pGrow[i] * (float) dt;
            if (pu[i] < pFloor[i]) {
                pu[i] = pFloor[i];
                if (pType[i] == P_SPARK) { pvu[i] = -pvu[i] * 0.35; pvx[i] *= 0.5; pvy[i] *= 0.5; }
                else { pvu[i] = -pvu[i] * 0.25; pvx[i] *= 0.35; pvy[i] *= 0.35; }
            }
            if (w != i) copyParticle(i, w);
            w++;
        }
        particleCount = w;
    }

    private void copyParticle(int s, int d) {
        px[d] = px[s]; py[d] = py[s]; pu[d] = pu[s];
        pvx[d] = pvx[s]; pvy[d] = pvy[s]; pvu[d] = pvu[s];
        pAge[d] = pAge[s]; pLife[d] = pLife[s]; pSize[d] = pSize[s]; pGrow[d] = pGrow[s];
        pR[d] = pR[s]; pG[d] = pG[s]; pB[d] = pB[s]; pA[d] = pA[s];
        pDrag[d] = pDrag[s]; pGrav[d] = pGrav[s]; pFloor[d] = pFloor[s]; pType[d] = pType[s];
    }

    /** Drops expired holes and, a few per frame, holes whose surface is gone (door opened, wall removed). */
    private void expireAndValidate(long now) {
        if (holeCount == 0) return;
        if (now - lastValidate < 100_000_000L) return;
        lastValidate = now;
        long maxAge = (long) (Math.max(0.5, holeMinutes) * 60e9);
        int cap = Math.max(1, Math.min(HARD_MAX_HOLES, maxHoles));
        try {
            world.bind();
        } catch (RuntimeException | LinkageError e) {
            return;
        }
        if (!world.ready()) return;
        for (int n = 0; n < 48 && holeCount > 0; n++) {
            validateCursor = (validateCursor + 1) % cap;
            int i = validateCursor;
            if (!live(i)) continue;
            if (now - hBorn[i] > maxAge || !surfaceStillThere(i)) hBorn[i] = 0;
        }
    }

    boolean live(int i) { return hBorn[i] != 0 && hSize[i] > 0; }

    private boolean surfaceStillThere(int i) {
        int x = hTx[i], y = hTy[i], z = hTz[i];
        if (!world.exists(x, y, z) && !world.exists(x + hEdx[i], y + hEdy[i], z)) return true; // unloaded: keep
        return switch (hKind[i]) {
            case WorldCollision.HIT_WALL, WorldCollision.HIT_LOW -> {
                int e = world.edge(x, y, z, hEdx[i], hEdy[i]);
                yield e == WorldQuery.EDGE_SOLID || e == WorldQuery.EDGE_LOW || e == WorldQuery.EDGE_FRAME || e == WorldQuery.EDGE_DOORWAY
                    || (e == WorldQuery.EDGE_CLEAR && !world.exists(x + hEdx[i], y + hEdy[i], z)); // solid earth
            }
            case WorldCollision.HIT_FLOOR -> world.solidFloor(x, y, z);
            case WorldCollision.HIT_CEILING -> world.solidFloor(x, y, z + 1);
            case WorldCollision.HIT_TREE -> world.cell(x, y, z) == WorldQuery.CELL_TREE;
            case WorldCollision.HIT_FURNITURE -> world.cell(x, y, z) != WorldQuery.CELL_EMPTY || t3zedballistics.api.ObjectHits.covers(x, y, z);
            default -> true;
        };
    }

    /**
     * Converts holes and particles to Viewpoint render space for this frame: render = (camX - x,
     * up + relief - camZ*sqrt6, camY - y), like the tracer snapshot.
     */
    void capture(Object scene, float camX, float camY, float camZ, long now) {
        if (scene == null) return;
        int holes = 0;
        int cap = Math.max(0, Math.min(HARD_MAX_HOLES, maxHoles));
        double d2 = DRAW_DISTANCE * DRAW_DISTANCE;
        for (int i = 0; i < cap; i++) {
            if (live(i) && sq(hx[i] - camX, hy[i] - camY) < d2) holes++;
        }
        int parts = particleCount;
        if (holes + parts == 0) { snapshots.remove(scene); return; }
        float[] out = new float[holes * HOLE_FLOATS + parts * PARTICLE_FLOATS + 2];
        out[0] = holes;
        out[1] = parts;
        double cu = BallisticsUnits.levelsToMeters(camZ);
        long maxAge = (long) (Math.max(0.5, holeMinutes) * 60e9);
        int o = 2;
        for (int i = 0; i < cap; i++) {
            if (!live(i) || sq(hx[i] - camX, hy[i] - camY) >= d2) continue;
            double lift = ViewpointBridge.lift(hx[i], hy[i]);
            out[o] = (float) (camX - hx[i]);
            out[o + 1] = (float) (hu[i] + lift - cu);
            out[o + 2] = (float) (camY - hy[i]);
            out[o + 3] = -hnx[i];
            out[o + 4] = hnu[i];
            out[o + 5] = -hny[i];
            out[o + 6] = hSize[i];
            out[o + 7] = hSeed[i];
            out[o + 8] = hFamily[i];
            double age = (now - hBorn[i]) / (double) maxAge;
            out[o + 9] = (float) (age > 0.85 ? Math.max(0, (1 - age) / 0.15) : 1.0); // fade out at the end
            out[o + 10] = hCore[i];
            out[o + 11] = 0;
            o += HOLE_FLOATS;
        }
        for (int i = 0; i < parts; i++) {
            double lift = ViewpointBridge.lift(px[i], py[i]);
            float t = pAge[i] / Math.max(1e-3f, pLife[i]);
            float fade = pType[i] == P_DUST ? (1 - t) * (1 - t) : (t > 0.7f ? (1 - t) / 0.3f : 1f);
            if (pType[i] == P_SPARK) fade = (1 - t);
            out[o] = (float) (camX - px[i]);
            out[o + 1] = (float) (pu[i] + lift - cu);
            out[o + 2] = (float) (camY - py[i]);
            out[o + 3] = (float) -pvx[i];
            out[o + 4] = (float) pvu[i];
            out[o + 5] = (float) -pvy[i];
            out[o + 6] = pSize[i];
            out[o + 7] = pR[i];
            out[o + 8] = pG[i];
            out[o + 9] = pB[i];
            out[o + 10] = pA[i] * fade;
            out[o + 11] = pType[i];
            o += PARTICLE_FLOATS;
        }
        snapshots.put(scene, out);
    }

    private static double sq(double a, double b) { return a * a + b * b; }

    public void clear() {
        for (int i = 0; i < HARD_MAX_HOLES; i++) hBorn[i] = 0;
        holeHead = 0;
        holeCount = 0;
        particleCount = 0;
        netCount = 0;
        snapshots.clear();
    }

    // =================================================================================== network

    private void queue(V3 p, double nx, double ny, double nu, V3 dir, int kind, int material, double cal, double e) {
        if (netCount >= netQueue.length / NET_FLOATS) return; // flood guard: excess impacts stay local
        int o = netCount++ * NET_FLOATS;
        netQueue[o] = round(p.x, 1000); netQueue[o + 1] = round(p.y, 1000); netQueue[o + 2] = round(p.u, 1000);
        netQueue[o + 3] = round(nx, 100); netQueue[o + 4] = round(ny, 100); netQueue[o + 5] = round(nu, 100);
        netQueue[o + 6] = round(dir.x, 100); netQueue[o + 7] = round(dir.y, 100); netQueue[o + 8] = round(dir.u, 100);
        netQueue[o + 9] = kind; netQueue[o + 10] = material; netQueue[o + 11] = round(cal, 10); netQueue[o + 12] = round(e, 100);
    }

    private static double round(double v, double q) { return Math.round(v * q) / q; }

    /** Pending local impacts for the relay (at most every 100 ms); null when there is nothing to send. */
    public double[] takeForNetwork() {
        if (netCount == 0) return null;
        long now = System.nanoTime();
        if (now - netLastSend < 100_000_000L) return null;
        netLastSend = now;
        double[] out = java.util.Arrays.copyOf(netQueue, netCount * NET_FLOATS);
        netCount = 0;
        return out;
    }

    /** Another player's impacts, as queued by their client (flat array, NET_FLOATS per impact). */
    public void remote(double[] d, int count) {
        if (!enabled || d == null) return;
        for (int k = 0; k < count; k++) {
            int o = k * NET_FLOATS;
            if (o + NET_FLOATS > d.length) break;
            double x = d[o], y = d[o + 1], u = d[o + 2];
            double nx = d[o + 3], ny = d[o + 4], nu = d[o + 5];
            if (!Double.isFinite(x + y + u + nx + ny + nu)) continue;
            int kind = (int) d[o + 9];
            if (kind <= WorldCollision.HIT_NONE || kind >= WorldCollision.HIT_VOID) continue;
            int material = (int) Math.max(0, Math.min(ImpactMaterial.NAMES.length - 1, d[o + 10]));
            // the tile the round was in: just in front of the surface
            int tx = (int) Math.floor(x + nx * 0.02), ty = (int) Math.floor(y + ny * 0.02);
            int tz = BallisticsUnits.levelOf(u + nu * 0.02);
            int edx = kind == WorldCollision.HIT_WALL || kind == WorldCollision.HIT_LOW ? (int) -Math.signum(Math.round(nx)) : 0;
            int edy = kind == WorldCollision.HIT_WALL || kind == WorldCollision.HIT_LOW ? (int) -Math.signum(Math.round(ny)) : 0;
            spawn(x, y, u, nx, ny, nu, d[o + 6], d[o + 7], d[o + 8], kind, tx, ty, tz, edx, edy, material,
                Math.max(4, Math.min(20, d[o + 11])), d[o + 12], true);
        }
    }

    // =================================================================================== settings

    public static String describe() {
        return "impactEffects=" + enabled + " bulletHoles=" + maxHoles + " bulletHoleMinutes=" + holeMinutes
            + " debrisAmount=" + debrisAmount;
    }
}
