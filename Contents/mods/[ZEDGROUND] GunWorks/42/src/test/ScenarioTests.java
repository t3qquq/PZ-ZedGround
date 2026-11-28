import t3gunworks.V3;
import t3gunworks.collision.HitVolumes;
import t3gunworks.collision.WorldCollision;
import t3gunworks.collision.WorldQuery;
import t3gunworks.projectile.ProjectilePhysics;
import t3gunworks.projectile.ShotRandom;

/**
 * Acceptance scenarios from the design brief, run on the same physics + collision code the mod uses
 * (the in-game loop in BallisticsManager.simulate is: step -> sweep prev->pos -> nearest impact).
 * Defaults: VelocityScale 0.4, gravity 9.81, profile drag values.
 */
public class ScenarioTests {
    static int passed, failed;
    static void check(boolean ok, String what) { if (ok) passed++; else { failed++; System.out.println("FAIL: " + what); } }

    static final double G = 9.81;
    static final double PISTOL_V = 360 * 0.4, PISTOL_K = 0.0016;
    static final double RIFLE_V = 900 * 0.4, RIFLE_K = 0.0010;

    /** Open field with optional wall between tile wallX and wallX+1 (all y), ground at 0. */
    static final class Field implements WorldQuery {
        int wallX = Integer.MIN_VALUE;
        public int edge(int x, int y, int z, int dx, int dy) {
            if (dx == 0) return EDGE_CLEAR;
            int e = dx > 0 ? x : x - 1;
            return e == wallX ? EDGE_SOLID : EDGE_CLEAR;
        }
        public Object lastWindow() { return null; }
        public boolean solidFloor(int x, int y, int z) { return z == 0; }
        public int cell(int x, int y, int z) { return CELL_EMPTY; }
        public boolean exists(int x, int y, int z) { return true; }
    }

    record Outcome(String what, double x, double u, double time, int part) {}

    /** Flies one round like BallisticsManager.simulate at 60 fps. */
    static Outcome fly(V3 muzzle, V3 dir, double v0, double k, WorldQuery w, HitVolumes target) {
        V3 p = new V3().set(muzzle), v = new V3(dir.x * v0, dir.y * v0, dir.u * v0), prev = new V3();
        WorldCollision.Hit hit = new WorldCollision.Hit();
        int[] part = new int[1];
        double age = 0, dt = 1.0 / 60;
        while (age < 3) {
            double remaining = dt;
            while (remaining > 1e-9) {
                double h = Math.min(remaining, 4.0 / Math.max(1, v.length()));
                ProjectilePhysics.step(p, v, prev, h, G, k);
                age += h; remaining -= h;
                boolean wh = WorldCollision.trace(prev, p, w, hit, null);
                double tw = wh ? hit.t : 1;
                V3 end = new V3().lerp(prev, p, tw);
                double tc = target != null ? target.test(prev, end, part) : -1;
                if (tc >= 0) { V3 at = new V3().lerp(prev, end, tc); return new Outcome("target", at.x, at.u, age, part[0]); }
                if (wh) return new Outcome(hit.kind == WorldCollision.HIT_FLOOR ? "ground" : "wall", hit.point.x, hit.point.u, age, -1);
            }
        }
        return new Outcome("expired", p.x, p.u, age, -1);
    }

    static V3 aimAt(V3 from, V3 to) { return new V3(to.x - from.x, to.y - from.y, to.u - from.u).normalize(); }

    public static void main(String[] a) {
        Field open = new Field();

        // Test 1: zombie 5 m away, FPS, aim at head -> head, almost no drop
        HitVolumes z5 = new HitVolumes();
        z5.standing(5.5, 0.5, 0, -1, 0);
        V3 eyeMuzzle = new V3(0.5, 0.5, 1.50);           // ADS: muzzle on the sight line
        V3 head5 = new V3(5.5, 0.5, 1.63);
        Outcome o = fly(eyeMuzzle, aimAt(eyeMuzzle, head5), PISTOL_V, PISTOL_K, open, z5);
        check(o.what.equals("target") && o.part == HitVolumes.HEAD, "T1 pistol head shot at 5 m hits the head (" + o + ")");
        double drop5 = 0.5 * G * o.time * o.time;
        check(drop5 < 0.02, "T1 drop at 5 m is under 2 cm (" + drop5 + ")");

        // Test 5: third-person shoulder camera 0.45 m to the right, 2.6 m behind; aim point from the camera
        // ray, round from the hip muzzle -> converges on the aimed point at 5 m
        V3 cam = new V3(-2.1, 0.5 - 0.45, 1.75);
        V3 fwd = aimAt(cam, head5);
        // the aim point is where the camera ray meets the target's head volume
        int[] part = new int[1];
        V3 rayEnd = new V3(cam.x + fwd.x * 50, cam.y + fwd.y * 50, cam.u + fwd.u * 50);
        double t = z5.test(cam, rayEnd, part);
        check(t >= 0 && part[0] == HitVolumes.HEAD, "T5 camera ray finds the head");
        V3 aimPoint = new V3().lerp(cam, rayEnd, t);
        V3 hip = new V3(0.95, 0.62, 1.20);
        o = fly(hip, aimAt(hip, aimPoint), PISTOL_V, PISTOL_K, open, z5);
        check(o.what.equals("target") && o.part == HitVolumes.HEAD, "T5 TPS shot from the muzzle converges on the head (" + o + ")");
        // without convergence (parallel to the camera ray) the same shot misses the head
        o = fly(hip, fwd, PISTOL_V, PISTOL_K, open, z5);
        check(!(o.what.equals("target") && o.part == HitVolumes.HEAD), "T5 control: a muzzle ray parallel to the camera misses the head");

        // Test 2: rifle at long range: noticeable travel time and drop, hits low when aiming at the head
        HitVolumes z80 = new HitVolumes();
        z80.standing(80.5, 0.5, 0, -1, 0);
        V3 head80 = new V3(80.5, 0.5, 1.63);
        o = fly(eyeMuzzle, aimAt(eyeMuzzle, head80), RIFLE_V, RIFLE_K, open, z80);
        check(o.time > 0.2, "T2 travel time at 80 m is noticeable (" + o.time + " s)");
        check(o.what.equals("target") && o.part != HitVolumes.HEAD, "T2 aiming at the head at 80 m hits lower (" + o + ")");
        // compensate: aim higher by the drop
        double hold = 0.5 * G * o.time * o.time;
        V3 held = new V3(80.5, 0.5, 1.63 + hold);
        o = fly(eyeMuzzle, aimAt(eyeMuzzle, held), RIFLE_V, RIFLE_K, open, z80);
        check(o.what.equals("target") && o.part == HitVolumes.HEAD, "T2 holding over by the drop connects (" + o + ")");
        // zeroing at 80 m makes the head shot land
        V3 zeroed = aimAt(eyeMuzzle, head80);
        ProjectilePhysics.pitchUp(zeroed, ProjectilePhysics.zeroAngle(RIFLE_V, G, RIFLE_K, 79.9));
        o = fly(eyeMuzzle, zeroed, RIFLE_V, RIFLE_K, open, z80);
        check(o.what.equals("target") && o.part == HitVolumes.HEAD, "T2 zeroed at 80 m, head shot lands (" + o + ")");

        // Test 3: barrel behind a wall corner while the camera sees the zombie -> the wall is hit.
        Field wall = new Field();
        wall.wallX = 1; // wall on x = 2
        V3 muzzleBehind = new V3(1.9, 0.5, 1.35);
        V3 chest = new V3(1.5, 0.5, 1.35);
        WorldCollision.Hit hit = new WorldCollision.Hit();
        boolean blocked = WorldCollision.trace(chest, muzzleBehind, wall, hit, null);
        check(!blocked, "T3 setup: muzzle still on the shooter's side");
        o = fly(muzzleBehind, aimAt(muzzleBehind, head5), PISTOL_V, PISTOL_K, wall, z5);
        check(o.what.equals("wall") && Math.abs(o.x - 2.0) < 1e-6, "T3 the round hits the wall, not the zombie (" + o + ")");
        V3 barrelThroughWall = new V3(2.3, 0.5, 1.35);
        check(WorldCollision.trace(chest, barrelThroughWall, wall, hit, null), "T3 a barrel poking through the wall is caught by the body->muzzle check");

        // Test 4: camera moves after firing -> nothing in the projectile depends on the camera (by construction)

        // Test 7: shotgun pellets: independent rounds in a cone
        ShotRandom r = new ShotRandom().seed(1, 1);
        double[] c = new double[2];
        int onTarget = 0, distinct = 0;
        double lastX = Double.NaN;
        HitVolumes z10 = new HitVolumes();
        z10.standing(10.5, 0.5, 0, -1, 0);
        for (int i = 0; i < 9; i++) {
            V3 d = aimAt(eyeMuzzle, new V3(10.5, 0.5, 1.25));
            r.cone(Math.toRadians(1.2), c);
            ProjectilePhysics.deflect(d, c[0], c[1]);
            o = fly(eyeMuzzle, d, 400 * 0.4, 0.006, open, z10);
            if (o.what.equals("target")) onTarget++;
            if (o.x != lastX) distinct++;
            lastX = o.x;
        }
        check(onTarget >= 6, "T7 most pellets of a centred shot hit a man-sized target at 10 m (" + onTarget + "/9)");
        check(distinct >= 8, "T7 pellets are individually simulated (different impacts)");

        // Test 9: miss by several centimetres -> MISS
        V3 justOver = new V3(5.5, 0.5, 1.72 + 0.12 + 0.05);
        o = fly(eyeMuzzle, aimAt(eyeMuzzle, justOver), PISTOL_V, PISTOL_K, open, z5);
        check(!o.what.equals("target"), "T9 a round 5 cm over the head is a miss (" + o + ")");

        // Test 10: shooting down from the first floor
        V3 upstairs = new V3(0.5, 0.5, 2.4494896 + 1.5);
        HitVolumes below = new HitVolumes();
        below.standing(12.5, 0.5, 0, -1, 0);
        o = fly(upstairs, aimAt(upstairs, new V3(12.5, 0.5, 1.2)), RIFLE_V, RIFLE_K, open, below);
        check(o.what.equals("target"), "T10 downward shot from an upper floor reaches the target (" + o + ")");
        o = fly(upstairs, aimAt(upstairs, new V3(8.0, 0.5, 0.0)), RIFLE_V, RIFLE_K, open, null);
        check(o.what.equals("ground") && Math.abs(o.x - 8.0) < 0.2, "T10 ground impact where aimed (" + o + ")");

        // Performance: 500 rounds simulated for one second at 60 fps on the pure code path
        long t0 = System.nanoTime();
        int segs = 0;
        WorldCollision.Hit h = new WorldCollision.Hit();
        V3 prev = new V3();
        for (int n = 0; n < 500; n++) {
            V3 p = new V3(0.5, 0.5, 1.5), v = new V3(360 * Math.cos(n * 0.01), 360 * Math.sin(n * 0.01), 2);
            for (int f = 0; f < 60; f++) {
                ProjectilePhysics.step(p, v, prev, 1.0 / 60, G, RIFLE_K);
                WorldCollision.trace(prev, p, open, h, null);
                z80.test(prev, p, part);
                segs++;
            }
        }
        double ms = (System.nanoTime() - t0) / 1e6;
        System.out.printf("perf: %d segments in %.1f ms (%.2f us per segment, %.2f ms per frame for 500 rounds)%n",
            segs, ms, ms * 1000 / segs, ms / 60);
        check(ms / 60 < 4.0, "500 rounds cost under 4 ms per frame on the pure path");

        System.out.println("passed " + passed + ", failed " + failed);
        if (failed > 0) System.exit(1);
    }
}
