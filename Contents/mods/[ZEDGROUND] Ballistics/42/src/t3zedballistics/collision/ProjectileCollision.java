package t3zedballistics.collision;

import java.util.ArrayList;
import java.util.Set;

import org.joml.Vector3f;

import t3zedballistics.BallisticsUnits;
import t3zedballistics.Log;
import t3zedballistics.V3;
import zombie.ai.states.FakeDeadZombieState;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoZombie;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoMovingObject;
import zombie.vehicles.BaseVehicle;

/**
 * One swept test of a projectile segment against everything a round can hit, nearest first:
 *
 *   world   - tile traversal (WorldCollision over PzWorld): walls, doors, floors, windows, trees, furniture
 *   people  - skeleton capsules (CharacterHitbox), only for characters standing in tiles next to the path
 *   vehicles- the game's own box test (BaseVehicle.getIntersectPoint), only for vehicles near the path
 *
 * Spatial filtering: characters are gathered from the IsoGridSquares the segment passes over (and their
 * neighbours), never from a scan of the whole map. Nothing here allocates per call.
 */
public final class ProjectileCollision {
    public static final int NONE = 0, WORLD = 1, CHARACTER = 2, VEHICLE = 3;

    public static final class Result {
        public int kind;
        public double t = 1.0;
        public final V3 point = new V3();
        public IsoGameCharacter character;
        public int bodyPart;
        /** Exact vanilla BodyPartType ordinal of the hit (feet, hands, neck, groin included), -1 if none. */
        public int bodyPartType = -1;
        public BaseVehicle vehicle;
        /** Character hits: where the straight path leaves the hit capsule (geometry only), see ImpactEvents. */
        public boolean hasCapsuleExit;
        public final V3 capsuleExit = new V3();
        public final WorldCollision.Hit world = new WorldCollision.Hit();
        public final WorldCollision.Crossings windows = new WorldCollision.Crossings();

        void clear() {
            kind = NONE; t = 1.0; character = null; vehicle = null; bodyPart = -1; bodyPartType = -1; hasCapsuleExit = false;
            windows.clear();
        }
    }

    private static final int MAX_CANDIDATES = 64;
    private static final double CHARACTER_BROAD_RADIUS = 1.1; // m, arms and lunges included
    private static final double VEHICLE_BROAD_RADIUS = 6.0;   // m, half a bus

    public final PzWorld world = new PzWorld();
    private final CharacterHitbox hitbox = new CharacterHitbox();
    private final HitVolumes volumes = new HitVolumes();
    private final IsoGameCharacter[] candidates = new IsoGameCharacter[MAX_CANDIDATES];
    private int candidateCount;
    private final int[] partOut = new int[2];
    private final V3 end = new V3();
    private final Vector3f va = new Vector3f(), vb = new Vector3f(), vr = new Vector3f();
    private final zombie.core.physics.Transform vxf = new zombie.core.physics.Transform();
    private final Vector3f la = new Vector3f(), lb = new Vector3f(), sa = new Vector3f(), sb = new Vector3f();
    private final org.joml.Matrix3f rot = new org.joml.Matrix3f();
    private static boolean vehicleBoxFailed;

    // statistics for ZGB_stats()
    public long charactersTested, volumesBuilt, rewound;

    /**
     * Lag compensation (CharacterHistory): when > 0, characters are tested where they were at this
     * System.nanoTime() instead of where they are now. Set by the caller around a sweep, 0 otherwise.
     */
    public long rewindTo;
    private final double[] back = new double[3];

    /**
     * @param ignore        characters this round must pass (the shooter, bodies it already went through)
     * @param ignoreCount   number of valid entries in ignore
     * @param collectWindows record glass crossed (live rounds); false = glass is transparent (aim ray)
     * @param hitCharacters false for the camera ray in "world only" queries
     */
    public boolean sweep(V3 a, V3 b, IsoGameCharacter[] ignore, int ignoreCount, boolean collectWindows,
                         boolean hitCharacters, boolean hitVehicles, Result out) {
        out.clear();
        if (!world.ready()) world.bind();
        if (!world.ready()) return false;

        // 1) static world
        boolean worldHit = WorldCollision.trace(a, b, world, out.world, collectWindows ? out.windows : null);
        double tMax = worldHit ? out.world.t : 1.0;
        if (worldHit) {
            out.kind = WORLD;
            out.t = tMax;
            out.point.set(out.world.point);
        }
        end.lerp(a, b, tMax);

        // 2) characters in front of the world hit
        if (hitCharacters) {
            long at = rewindTo, now = at > 0 ? System.nanoTime() : 0;
            gather(a, end, ignore, ignoreCount, at > 0 ? 2 : 1);
            for (int i = 0; i < candidateCount; i++) {
                IsoGameCharacter c = candidates[i];
                candidates[i] = null;
                boolean moved = at > 0 && CharacterHistory.offset(c, at, now, back);
                double ox = moved ? back[0] : 0, oy = moved ? back[1] : 0;
                double ou = moved ? BallisticsUnits.levelsToMeters(back[2]) : 0;
                double u0 = BallisticsUnits.levelsToMeters(c.getZ()) + ou;
                double uLo = Math.min(a.u, end.u), uHi = Math.max(a.u, end.u);
                if (uHi < u0 - 0.2 || uLo > u0 + CharacterHitbox.reach(c)) continue;
                if (SegmentMath.dist2D2(a, end, c.getX() + ox, c.getY() + oy) > CHARACTER_BROAD_RADIUS * CHARACTER_BROAD_RADIUS) continue;
                charactersTested++;
                hitbox.build(c, volumes);
                if (moved) {
                    volumes.translate(ox, oy, ou);
                    rewound++;
                }
                volumesBuilt++;
                double tc = volumes.test(a, end, partOut);
                if (tc >= 0) {
                    double t = tc * tMax; // volumes were tested against a->end
                    if (t < out.t || out.kind == NONE) {
                        out.kind = CHARACTER;
                        out.t = t;
                        out.character = c;
                        out.bodyPart = partOut[0];
                        out.bodyPartType = partOut[1];
                        out.point.lerp(a, b, t);
                        double len = a.dist(b);
                        out.hasCapsuleExit = len > 1e-9 && volumes.exitPoint(volumes.lastIndex, out.point,
                            (b.x - a.x) / len, (b.y - a.y) / len, (b.u - a.u) / len, out.capsuleExit);
                    }
                }
            }
            candidateCount = 0;
        }

        // 3) vehicles
        if (hitVehicles) testVehicles(a, b, out);
        return out.kind != NONE;
    }

    private void gather(V3 a, V3 b, IsoGameCharacter[] ignore, int ignoreCount, int ring) {
        candidateCount = 0;
        IsoCell cell = world.isoCell();
        if (cell == null) return;
        int zLo = BallisticsUnits.levelOf(Math.min(a.u, b.u));
        int zHi = BallisticsUnits.levelOf(Math.max(a.u, b.u));
        double len2d = Math.hypot(b.x - a.x, b.y - a.y);
        int samples = Math.max(1, (int) Math.ceil(len2d / 0.5));
        int lastX = Integer.MIN_VALUE, lastY = Integer.MIN_VALUE;
        for (int s = 0; s <= samples; s++) {
            double f = (double) s / samples;
            int cx = (int) Math.floor(a.x + (b.x - a.x) * f);
            int cy = (int) Math.floor(a.y + (b.y - a.y) * f);
            if (cx == lastX && cy == lastY) continue;
            lastX = cx;
            lastY = cy;
            for (int z = zLo; z <= zHi; z++) {
                for (int nx = cx - ring; nx <= cx + ring; nx++) {
                    for (int ny = cy - ring; ny <= cy + ring; ny++) {
                        IsoGridSquare sq = cell.getGridSquare(nx, ny, z);
                        if (sq == null) continue;
                        ArrayList<IsoMovingObject> list = sq.getMovingObjects();
                        for (int i = 0, n = list.size(); i < n; i++) {
                            IsoMovingObject o = list.get(i);
                            if (o instanceof IsoGameCharacter c && hittable(c) && !contains(ignore, ignoreCount, c)) addCandidate(c);
                        }
                    }
                }
            }
        }
    }

    /** Characters vanilla would never let a bullet touch (dead, fake-dead, admin ghosts, grapple corpses). */
    public static boolean hittable(IsoGameCharacter c) {
        if (c.isDead() || c.getCurrentSquare() == null) return false;
        if (c.isGodMod() && !c.isZombie()) return false;
        if (c instanceof IsoZombie z) {
            if (z.isReanimatedForGrappleOnly()) return false;
            if (z.isCurrentState(FakeDeadZombieState.instance())) return false;
        }
        String reaction = c.getHitReaction();
        return reaction == null || !reaction.contains("Death");
    }

    private static boolean contains(IsoGameCharacter[] arr, int n, IsoGameCharacter c) {
        for (int i = 0; i < n; i++) if (arr[i] == c) return true;
        return false;
    }

    private void addCandidate(IsoGameCharacter c) {
        for (int i = 0; i < candidateCount; i++) if (candidates[i] == c) return;
        if (candidateCount < MAX_CANDIDATES) candidates[candidateCount++] = c;
    }

    /**
     * The vehicle's collision shapes in 3D - the same ones the game builds its Bullet body from (chassis box at
     * centerOfMassOffset and/or the script's "physics box" list, all already scaled by the model scale), placed with
     * the vehicle's world transform (physics space: x = world x - offsetX, y = height in metres, z = world y - offsetY).
     *
     * Vanilla's BaseVehicle.getIntersectPoint cannot be used for this: its getLocalPos drops the height of the
     * point (local y is computed from height 0), so any shot passing over a car's footprint - over the hood,
     * from its roof, past it - counted as hitting the car; and it inflates the box by the centre-of-mass offset.
     *
     * Returns the entry fraction along a-b (0 when a starts inside), -1 for a miss, NaN when the data is missing.
     */
    private double vehicleBox(BaseVehicle v, V3 a, V3 b) {
        try {
            zombie.scripting.objects.VehicleScript sc = v.getScript();
            if (sc == null || zombie.core.physics.WorldSimulation.instance == null) return Double.NaN;
            Vector3f ext = sc.getExtents(), com = sc.getCenterOfMassOffset();
            if (ext == null || com == null || !(ext.x > 0) || !(ext.y > 0) || !(ext.z > 0)) return Double.NaN;
            v.getWorldTransform(vxf);
            float ox = zombie.core.physics.WorldSimulation.instance.offsetX, oy = zombie.core.physics.WorldSimulation.instance.offsetY;
            // physics space, relative to the vehicle origin, then into the vehicle's local axes
            la.set((float) (a.x - ox - vxf.origin.x), (float) (a.u - vxf.origin.y), (float) (a.y - oy - vxf.origin.z));
            lb.set((float) (b.x - ox - vxf.origin.x), (float) (b.u - vxf.origin.y), (float) (b.y - oy - vxf.origin.z));
            vxf.basis.transformTranspose(la);
            vxf.basis.transformTranspose(lb);
            if (!Float.isFinite(la.x + la.y + la.z + lb.x + lb.y + lb.z)) return Double.NaN;
            // The collision shapes the game gives Bullet (VehicleScript.toBullet): the chassis box when
            // useChassisPhysicsCollision (or no shapes), plus each "physics box" (offset, rotate in degrees, extents).
            // Cars use several boxes: lower body, windscreen and rear window slopes, cabin - so a round over the
            // hood or beside the roof line passes, and one into the body hits.
            double best = -1;
            int shapes = sc.getPhysicsShapeCount();
            boolean chassis = sc.useChassisPhysicsCollision() || shapes == 0;
            if (chassis) {
                Vector3f cs = sc.hasPhysicsChassisShape() ? sc.getPhysicsChassisShape() : ext;
                double t = slab(la.x - com.x, la.y - com.y, la.z - com.z, lb.x - com.x, lb.y - com.y, lb.z - com.z,
                    cs.x * 0.5, cs.y * 0.5, cs.z * 0.5);
                if (t >= 0) best = t;
            }
            boolean any = chassis;
            for (int i = 0; i < shapes; i++) {
                zombie.scripting.objects.VehicleScript.PhysicsShape sh = sc.getPhysicsShape(i);
                if (sh == null || sh.type != 1) continue; // spheres and meshes: covered by the box fallback below
                any = true;
                double t = boxEntry(la, lb, sh.offset, sh.rotate, sh.extents, sa, sb, rot);
                if (t >= 0 && (best < 0 || t < best)) best = t;
            }
            if (!any) {
                best = slab(la.x - com.x, la.y - com.y, la.z - com.z, lb.x - com.x, lb.y - com.y, lb.z - com.z,
                    ext.x * 0.5, ext.y * 0.5, ext.z * 0.5);
            }
            return best;
        } catch (RuntimeException | LinkageError e) {
            vehicleBoxFailed = true;
            Log.error("vehicle chassis box test unavailable; using the game's vehicle test", e);
            return Double.NaN;
        }
    }

    /**
     * Segment la-lb (vehicle local space, metres) against one "physics box": centre offset, rotation in degrees
     * (x, y, z), full extents. Entry fraction, 0 when starting inside, -1 for a miss. sa, sb, rot are scratch.
     */
    static double boxEntry(Vector3f la, Vector3f lb, Vector3f offset, Vector3f rotateDeg, Vector3f extents,
                           Vector3f sa, Vector3f sb, org.joml.Matrix3f rot) {
        sa.set(la).sub(offset);
        sb.set(lb).sub(offset);
        if (rotateDeg.x != 0 || rotateDeg.y != 0 || rotateDeg.z != 0) {
            rot.rotationXYZ((float) Math.toRadians(rotateDeg.x), (float) Math.toRadians(rotateDeg.y), (float) Math.toRadians(rotateDeg.z));
            rot.transformTranspose(sa);
            rot.transformTranspose(sb);
        }
        return slab(sa.x, sa.y, sa.z, sb.x, sb.y, sb.z, extents.x * 0.5, extents.y * 0.5, extents.z * 0.5);
    }

    /** Segment p0-p1 against the box |x|<=hx, |y|<=hy, |z|<=hz: entry fraction, 0 when starting inside, -1 for a miss. */
    static double slab(double x0, double y0, double z0, double x1, double y1, double z1, double hx, double hy, double hz) {
        double tn = 0, tf = 1;
        // x
        double d = x1 - x0;
        if (Math.abs(d) < 1e-12) { if (x0 < -hx || x0 > hx) return -1; }
        else {
            double t1 = (-hx - x0) / d, t2 = (hx - x0) / d;
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            if (t1 > tn) tn = t1;
            if (t2 < tf) tf = t2;
            if (tn > tf) return -1;
        }
        // y
        d = y1 - y0;
        if (Math.abs(d) < 1e-12) { if (y0 < -hy || y0 > hy) return -1; }
        else {
            double t1 = (-hy - y0) / d, t2 = (hy - y0) / d;
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            if (t1 > tn) tn = t1;
            if (t2 < tf) tf = t2;
            if (tn > tf) return -1;
        }
        // z
        d = z1 - z0;
        if (Math.abs(d) < 1e-12) { if (z0 < -hz || z0 > hz) return -1; }
        else {
            double t1 = (-hz - z0) / d, t2 = (hz - z0) / d;
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            if (t1 > tn) tn = t1;
            if (t2 < tf) tf = t2;
            if (tn > tf) return -1;
        }
        return tn;
    }

    /** The game's own (2D) vehicle test - only if the chassis data cannot be read. */
    private double vanillaVehicle(BaseVehicle v, V3 a, V3 b, double segLen) {
        va.set((float) a.x, (float) a.y, (float) BallisticsUnits.metersToLevels(a.u));
        vb.set((float) b.x, (float) b.y, (float) BallisticsUnits.metersToLevels(b.u));
        Vector3f p = v.getIntersectPoint(va, vb, vr);
        if (p == null) return -1;
        double px = p.x - a.x, py = p.y - a.y, pu = BallisticsUnits.levelsToMeters(p.z) - a.u;
        return segLen > 1e-9 ? Math.sqrt(px * px + py * py + pu * pu) / segLen : 0;
    }

    private void testVehicles(V3 a, V3 b, Result out) {
        IsoCell cell = world.isoCell();
        if (cell == null) return;
        Set<BaseVehicle> vehicles;
        try {
            vehicles = cell.getVehicles();
        } catch (RuntimeException e) {
            Log.errorOnce("listing vehicles", e);
            return;
        }
        if (vehicles == null || vehicles.isEmpty()) return;
        double segLen = a.dist(b);
        for (BaseVehicle v : vehicles) {
            if (v == null) continue;
            double vu = BallisticsUnits.levelsToMeters(v.getZ());
            if (Math.min(a.u, b.u) > vu + 4.0 || Math.max(a.u, b.u) < vu - 1.0) continue;
            if (SegmentMath.dist2D2(a, b, v.getX(), v.getY()) > VEHICLE_BROAD_RADIUS * VEHICLE_BROAD_RADIUS) continue;
            try {
                double t = vehicleBoxFailed ? Double.NaN : vehicleBox(v, a, b);
                if (Double.isNaN(t)) t = vanillaVehicle(v, a, b, segLen);
                if (t >= 0 && t <= 1.0 && (out.kind == NONE || t < out.t)) {
                    out.kind = VEHICLE;
                    out.t = t;
                    out.vehicle = v;
                    out.character = null;
                    out.hasCapsuleExit = false;
                    out.bodyPartType = -1;
                    out.point.lerp(a, b, t);
                }
            } catch (RuntimeException e) {
                Log.errorOnce("vehicle intersection test", e);
            }
        }
    }
}
