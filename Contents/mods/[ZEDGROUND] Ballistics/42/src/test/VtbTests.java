import t3zedballistics.BallisticsUnits;
import t3zedballistics.V3;
import t3zedballistics.collision.HitVolumes;
import t3zedballistics.collision.SegmentMath;
import t3zedballistics.collision.WorldCollision;
import t3zedballistics.collision.WorldQuery;
import t3zedballistics.config.BallisticsConfig;
import t3zedballistics.projectile.ProjectilePhysics;
import t3zedballistics.projectile.ShotRandom;
import t3zedballistics.weapon.AmmoBallisticsResolver;
import t3zedballistics.weapon.BallisticProfile;

/**
 * Offline tests for the pure parts of the mod (no game running): calibre recognition, physics,
 * tile traversal, hitboxes, deterministic randomness. Run: java -cp build/classes:test-classes VtbTests
 */
public class VtbTests {
    static int passed, failed;

    static void check(boolean ok, String what) {
        if (ok) passed++;
        else { failed++; System.out.println("FAIL: " + what); }
    }

    static void near(double a, double b, double tol, String what) {
        check(Math.abs(a - b) <= tol, what + " (got " + a + ", want " + b + " +-" + tol + ")");
    }

    // ------------------------------------------------------------------ synthetic world
    static final class World implements WorldQuery {
        // walls on the east edge of tile x=wallX (between wallX and wallX+1), for all y, level 0
        int wallX = Integer.MIN_VALUE, windowX = Integer.MIN_VALUE, lowX = Integer.MIN_VALUE, frameX = Integer.MIN_VALUE;
        int ceilingLevel = Integer.MAX_VALUE; // floor of this level is solid everywhere
        boolean ground = true;                // floor of level 0 solid
        int treeX = Integer.MIN_VALUE, treeY;
        Object window = "window";

        public int edge(int x, int y, int z, int dx, int dy) {
            if (dx == 0) return EDGE_CLEAR;
            int edgeX = dx > 0 ? x : x - 1; // edge between edgeX and edgeX+1
            if (edgeX == wallX) return EDGE_SOLID;
            if (edgeX == windowX) return EDGE_WINDOW;
            if (edgeX == lowX) return EDGE_LOW;
            if (edgeX == frameX) return EDGE_FRAME;
            return EDGE_CLEAR;
        }
        public Object lastWindow() { return window; }
        public boolean solidFloor(int x, int y, int z) { return (z == 0 && ground) || z == ceilingLevel; }
        public int cell(int x, int y, int z) { return (x == treeX && y == treeY && z == 0) ? CELL_TREE : CELL_EMPTY; }
        public boolean exists(int x, int y, int z) { return true; }
    }

    public static void main(String[] args) {
        calibres();
        physics();
        world();
        hitboxes();
        randomness();
        System.out.println("passed " + passed + ", failed " + failed);
        if (failed > 0) System.exit(1);
    }

    static void calibres() {
        check(AmmoBallisticsResolver.fromAmmoName("base:bullets_9mm").category.equals("handgun"), "9mm is a handgun");
        check(AmmoBallisticsResolver.fromAmmoName("Base.Bullets45").category.equals("handgun"), ".45 is a handgun");
        check(AmmoBallisticsResolver.fromAmmoName("base:bullets_44").category.equals("magnum handgun"), ".44 magnum");
        check(AmmoBallisticsResolver.fromAmmoName("base:bullets_357").category.equals("magnum handgun"), ".357 magnum");
        check(AmmoBallisticsResolver.fromAmmoName("base:bullets_556").category.equals("intermediate rifle"), "5.56");
        check(AmmoBallisticsResolver.fromAmmoName("Base.545x39Bullets").category.equals("intermediate rifle"), "5.45x39 (not .45)");
        check(AmmoBallisticsResolver.fromAmmoName("Base.762x39Bullets").category.equals("intermediate rifle"), "7.62x39");
        check(AmmoBallisticsResolver.fromAmmoName("Base.762x54rBullets").category.equals("full-power rifle"), "7.62x54R");
        check(AmmoBallisticsResolver.fromAmmoName("Base.762x25Bullets").category.equals("handgun"), "7.62x25 Tokarev");
        check(AmmoBallisticsResolver.fromAmmoName("base:bullets_308").category.equals("full-power rifle"), ".308");
        check(AmmoBallisticsResolver.fromAmmoName("base:bullets_3030").category.equals("full-power rifle"), ".30-30");
        check(AmmoBallisticsResolver.fromAmmoName("base:shotgun_shells").category.equals("shotgun"), "shells");
        check(AmmoBallisticsResolver.fromAmmoName("Base.12gSlug").category.equals("shotgun slug"), "slug");
        check(AmmoBallisticsResolver.fromAmmoName("Base.22LRBullets").category.equals("rimfire"), ".22");
        check(AmmoBallisticsResolver.fromAmmoName("Base.223Bullets").category.equals("intermediate rifle"), ".223 is not .22");
        check(AmmoBallisticsResolver.fromAmmoName("Base.9x39Bullets").category.equals("subsonic rifle"), "9x39");
        check(AmmoBallisticsResolver.fromAmmoName("Base.SomethingWeird") == null, "unknown ammo is unknown");
        BallisticProfile unknownRifle = AmmoBallisticsResolver.resolve("Base.SomethingWeird", 1, 40, 3.0, 0);
        check(unknownRifle.category.equals("full-power rifle"), "unknown long-range heavy gun -> full-power rifle");
        BallisticProfile unknownPistol = AmmoBallisticsResolver.resolve(null, 1, 15, 1.0, 0);
        check(unknownPistol.category.equals("handgun"), "no ammo, short range -> handgun");
        BallisticProfile pump = AmmoBallisticsResolver.resolve("base:shotgun_shells", 6, 15, 2.2, 0.6);
        check(pump.pelletCount == 6 && Math.abs(pump.pelletSpreadDeg - 0.6) < 1e-9, "pump takes ProjectileCount and ProjectileSpread");
        BallisticProfile multi = AmmoBallisticsResolver.resolve("base:bullets_9mm", 5, 15, 1.0, 1.0);
        check(multi.pelletCount == 5, "a multi-projectile weapon fires pellets whatever the ammo name");
    }

    static void physics() {
        // No drag: drop after t seconds must equal g t^2 / 2 (trapezoid is exact for constant gravity)
        V3 p = new V3(0, 0, 10), v = new V3(300, 0, 0), prev = new V3();
        double t = 0;
        while (t < 0.5 - 1e-12) { ProjectilePhysics.step(p, v, prev, 1.0 / 60, 9.81, 0); t += 1.0 / 60; }
        near(10 - p.u, 0.5 * 9.81 * 0.25, 1e-9, "drop after 0.5 s without drag");
        near(p.x, 150, 1e-9, "distance after 0.5 s without drag");

        // Same result regardless of step size (determinism / frame-rate independence)
        V3 a = new V3(0, 0, 0), av = new V3(900, 0, 30), b = new V3(0, 0, 0), bv = new V3(900, 0, 30);
        for (int i = 0; i < 30; i++) ProjectilePhysics.step(a, av, prev, 1.0 / 30, 9.81, 0.001);
        for (int i = 0; i < 240; i++) ProjectilePhysics.step(b, bv, prev, 1.0 / 240, 9.81, 0.001);
        near(a.x, b.x, b.x * 0.002, "range is (almost) frame-rate independent with drag");
        near(a.u, b.u, 0.2, "height is (almost) frame-rate independent with drag");

        // Drag slows a rifle round: v(x) ~ v0 e^{-kx}
        V3 r = new V3(0, 0, 0), rv = new V3(900, 0, 0);
        while (r.x < 100) ProjectilePhysics.step(r, rv, prev, 1.0 / 1000, 0, 0.001);
        near(rv.length(), 900 * Math.exp(-0.001 * r.x), 3, "quadratic drag matches v0*e^-kx");

        // Zeroing: a round fired with the zero angle crosses the sight line again at the zero distance
        double v0 = 360, g = 9.81, k = 0.001, zero = 50;
        double ang = ProjectilePhysics.zeroAngle(v0, g, k, zero);
        V3 d = new V3(1, 0, 0);
        ProjectilePhysics.pitchUp(d, ang);
        V3 z = new V3(0, 0, 0), zv = new V3(d.x * v0, 0, d.u * v0);
        while (z.x < zero) ProjectilePhysics.step(z, zv, prev, 1.0 / 2000, g, k);
        near(z.u, 0, 0.01, "zeroed round returns to the line of sight at the zero distance");
        check(ProjectilePhysics.zeroAngle(v0, g, k, 0) == 0, "zero 0 means no elevation");

        // Deflect keeps unit length and deviates by the requested angle
        V3 f = new V3(0.6, 0.8, 0);
        ProjectilePhysics.deflect(f, 0.01, 0);
        near(f.length(), 1, 1e-12, "deflect keeps unit length");
        near(Math.acos(Math.min(1, f.dot(new V3(0.6, 0.8, 0)))), 0.01, 1e-6, "deflect angle");
        V3 up = new V3(0, 0, 1);
        ProjectilePhysics.deflect(up, 0.02, 0.0);
        check(up.finite() && Math.abs(up.length() - 1) < 1e-9, "deflect works straight up");

        // Units
        near(BallisticsUnits.levelsToMeters(1), 2.4494896, 1e-9, "one level is sqrt(6) m");
        check(BallisticsUnits.levelOf(2.44) == 0 && BallisticsUnits.levelOf(2.45) == 1 && BallisticsUnits.levelOf(-0.1) == -1, "levelOf");
    }

    static void world() {
        WorldCollision.Hit hit = new WorldCollision.Hit();
        WorldCollision.Crossings cross = new WorldCollision.Crossings();
        World w = new World();
        w.wallX = 10;

        // Straight shot into a wall: impact exactly on x = 11
        check(WorldCollision.trace(new V3(0.5, 0.5, 1.5), new V3(30.5, 0.5, 1.5), w, hit, cross), "wall is hit");
        near(hit.point.x, 11.0, 1e-9, "wall impact point");
        check(hit.kind == WorldCollision.HIT_WALL, "wall kind");

        // Tunneling: one huge segment still stops at the wall
        check(WorldCollision.trace(new V3(0.5, 0.5, 1.5), new V3(5000.5, 37.2, 1.5), w, hit, null), "no tunneling over 5 km");
        near(hit.point.x, 11.0, 1e-9, "no tunneling impact point");

        // Shooting away from the wall: nothing
        check(!WorldCollision.trace(new V3(9.5, 0.5, 1.5), new V3(0.2, 3.5, 1.4), w, hit, null), "away from the wall is clear");

        // Window: crossing recorded, round continues (glass between sill 0.8 m and lintel 2.1 m)
        w = new World();
        w.windowX = 4;
        cross.clear();
        check(!WorldCollision.trace(new V3(0.5, 0.5, 1.4), new V3(9.5, 0.5, 1.4), w, hit, cross), "window lets the round through");
        check(cross.count == 1 && Math.abs(cross.t[0] - (5.0 - 0.5) / 9.0) < 1e-9, "window crossing recorded at the edge");
        // ... but below the sill it's wall
        check(WorldCollision.trace(new V3(0.5, 0.5, 0.5), new V3(9.5, 0.5, 0.5), w, hit, cross), "below the window sill is wall");
        // aim ray (crossings == null) treats glass as transparent
        check(!WorldCollision.trace(new V3(0.5, 0.5, 1.4), new V3(9.5, 0.5, 1.4), w, hit, null), "aim ray sees through glass");

        // Low fence: stops low rounds only
        w = new World();
        w.lowX = 3;
        check(WorldCollision.trace(new V3(0.5, 0.5, 0.6), new V3(9.5, 0.5, 0.6), w, hit, null), "low fence stops a low round");
        check(!WorldCollision.trace(new V3(0.5, 0.5, 1.4), new V3(9.5, 0.5, 1.4), w, hit, null), "low fence passes a chest-high round");

        // Empty window frame: open between sill and lintel only
        w = new World();
        w.frameX = 2;
        check(!WorldCollision.trace(new V3(0.5, 0.5, 1.2), new V3(6.5, 0.5, 1.2), w, hit, null), "empty frame passes");
        check(WorldCollision.trace(new V3(0.5, 0.5, 2.3), new V3(6.5, 0.5, 2.3), w, hit, null), "above the lintel is wall");

        // Shooting down from an upper floor hits the ground at the right spot
        w = new World();
        V3 a = new V3(0.5, 0.5, BallisticsUnits.levelsToMeters(1) + 1.4), b = new V3(20.5, 0.5, -5);
        check(WorldCollision.trace(a, b, w, hit, null), "downward shot hits the ground");
        check(hit.kind == WorldCollision.HIT_FLOOR && Math.abs(hit.point.u) < 1e-9, "ground impact at height 0");
        double tGround = a.u / (a.u - b.u);
        near(hit.point.x, 0.5 + 20 * tGround, 1e-9, "ground impact position");

        // Ceiling
        w = new World();
        w.ceilingLevel = 1;
        check(WorldCollision.trace(new V3(0.5, 0.5, 1.5), new V3(10.5, 0.5, 5.0), w, hit, null), "upward shot hits the ceiling");
        check(hit.kind == WorldCollision.HIT_CEILING && Math.abs(hit.point.u - BallisticsUnits.LEVEL_HEIGHT_M) < 1e-9, "ceiling height");

        // Tree trunk
        w = new World();
        w.treeX = 5; w.treeY = 0;
        check(WorldCollision.trace(new V3(0.5, 0.5, 1.2), new V3(9.5, 0.5, 1.2), w, hit, null), "tree trunk stops the round");
        near(hit.point.x, 5.5 - 0.18, 1e-9, "trunk surface");
        check(!WorldCollision.trace(new V3(0.5, 0.9, 1.2), new V3(9.5, 0.9, 1.2), w, hit, null), "missing the trunk by 22 cm passes");

        // Furniture boxes respect the setting
        BallisticsConfig.furnitureCollision = true;
    }

    static void hitboxes() {
        HitVolumes v = new HitVolumes();
        v.standing(10, 10, 0, 1, 0);
        int[] part = new int[1];
        // straight at the head
        double t = v.test(new V3(0, 10, 1.63), new V3(20, 10, 1.63), part);
        check(t > 0 && part[0] == HitVolumes.HEAD, "head shot is HEAD");
        near(t * 20, 10 - 0.12, 1e-6, "head entry point");
        // chest
        t = v.test(new V3(0, 10, 1.25), new V3(20, 10, 1.25), part);
        check(t > 0 && part[0] == HitVolumes.SPINE, "chest shot is torso");
        // a few centimetres past the head: miss (acceptance test 9)
        t = v.test(new V3(0, 10.2, 1.70), new V3(20, 10.2, 1.70), part);
        check(t < 0, "missing by several centimetres is a MISS");
        // over the head
        check(v.test(new V3(0, 10, 1.95), new V3(20, 10, 1.95), part) < 0, "over the head misses");
        // leg
        t = v.test(new V3(0, 10.12, 0.3), new V3(20, 10.12, 0.3), part);
        check(t > 0 && (part[0] == HitVolumes.L_LOWER_LEG || part[0] == HitVolumes.R_LOWER_LEG), "shin shot is a lower leg");

        // exact wound locations (BodyPartType) for player hits
        int[] pt = new int[2];
        v.test(new V3(0, 10, 1.63), new V3(20, 10, 1.63), pt);
        check(pt[1] == HitVolumes.T_HEAD, "head shot wounds the Head");
        v.test(new V3(0, 10, 1.25), new V3(20, 10, 1.25), pt);
        check(pt[1] == HitVolumes.T_TORSO_UPPER, "chest shot wounds Torso_Upper");
        v.test(new V3(0, 10.12, 0.08), new V3(20, 10.12, 0.08), pt);
        check(pt[0] == HitVolumes.L_LOWER_LEG && pt[1] == HitVolumes.T_FOOT_L, "left foot shot wounds Foot_L (got " + pt[1] + ")");
        v.test(new V3(0, 9.88, 0.08), new V3(20, 9.88, 0.08), pt);
        check(pt[1] == HitVolumes.T_FOOT_R, "right foot shot wounds Foot_R (got " + pt[1] + ")");
        v.test(new V3(0, 10.12, 0.3), new V3(20, 10.12, 0.3), pt);
        check(pt[1] == HitVolumes.T_LOWERLEG_L, "left shin shot wounds LowerLeg_L (got " + pt[1] + ")");
        v.test(new V3(0, 10.12, 0.65), new V3(20, 10.12, 0.65), pt);
        check(pt[1] == HitVolumes.T_UPPERLEG_L, "left thigh shot wounds UpperLeg_L (got " + pt[1] + ")");
        v.test(new V3(0, 10, 0.9), new V3(20, 10, 0.9), pt);
        check(pt[1] == HitVolumes.T_GROIN, "groin-height shot wounds Groin (got " + pt[1] + ")");

        // hit packet (42.22: zombie.network.fields.hit.Hit, hitHead byte) carries the exact part and the
        // projectile marker without disturbing vanilla's head value
        zombie.network.fields.hit.Hit wh = new zombie.network.fields.hit.Hit();
        wh.set(0f, 0f, 0f, new zombie.CombatManager.HitData((byte) 1, false, false, false, true), false, 10f);
        t3zedballistics.combat.TargetedWound.markBallistic(wh);
        t3zedballistics.combat.TargetedWound.encode(wh, HitVolumes.T_FOOT_R);
        int rawHead = t3zedballistics.combat.TargetedWound.raw(wh);
        check(t3zedballistics.combat.TargetedWound.decode(wh) == HitVolumes.T_FOOT_R, "hit packet round-trips the body part");
        check(t3zedballistics.combat.TargetedWound.isBallistic(rawHead), "projectile marker set");
        check((rawHead & t3zedballistics.combat.TargetedWound.HEAD_MASK) == 1, "head value kept next to the part bits");
        t3zedballistics.combat.TargetedWound.strip(wh, rawHead);
        check(t3zedballistics.combat.TargetedWound.raw(wh) == 1, "stripping leaves vanilla's head value only");
        zombie.network.fields.hit.Hit plain = new zombie.network.fields.hit.Hit();
        plain.set(0f, 0f, 0f, new zombie.CombatManager.HitData((byte) 3, true, false, false, true), false, 10f);
        check(t3zedballistics.combat.TargetedWound.decode(plain) == -1, "vanilla packet has no part");
        check(!t3zedballistics.combat.TargetedWound.isBallistic(t3zedballistics.combat.TargetedWound.raw(plain)), "vanilla packet has no projectile marker");
        for (int k = 0; k <= 16; k++) {
            for (int head = 0; head <= 3; head++) {
                zombie.network.fields.hit.Hit h = new zombie.network.fields.hit.Hit();
                h.set(0f, 0f, 0f, new zombie.CombatManager.HitData((byte) head, false, false, false, true), false, 1f);
                t3zedballistics.combat.TargetedWound.encode(h, k);
                int r = t3zedballistics.combat.TargetedWound.raw(h);
                check(t3zedballistics.combat.TargetedWound.decode(h) == k && (r & t3zedballistics.combat.TargetedWound.HEAD_MASK) == head
                    && !t3zedballistics.combat.TargetedWound.isBallistic(r), "part " + k + " head " + head + " round-trips");
            }
        }

        // Segment math sanity
        double[] tmp = new double[2];
        double d2 = SegmentMath.closest(new V3(0, 0, 0), new V3(10, 0, 0), new V3(5, 1, -1), new V3(5, 1, 1), tmp);
        near(d2, 1, 1e-12, "closest distance of crossing segments");
        near(tmp[0], 0.5, 1e-12, "closest parameter");
    }

    static void randomness() {
        ShotRandom r1 = new ShotRandom().seed(7, 1052), r2 = new ShotRandom().seed(7, 1052), r3 = new ShotRandom().seed(8, 1052);
        boolean same = true, diff = false;
        for (int i = 0; i < 100; i++) {
            double a = r1.gaussian(), b = r2.gaussian(), c = r3.gaussian();
            same &= a == b;
            diff |= a != c;
            check(Math.abs(a) <= 3, "gaussian clamped to 3 sigma");
        }
        check(same, "same seed -> same spread pattern");
        check(diff, "different shooter -> different spread");
        double[] c = new double[2];
        double max = 0;
        for (int i = 0; i < 1000; i++) { r1.cone(0.02, c); max = Math.max(max, Math.hypot(c[0], c[1])); }
        check(max <= 0.02 + 1e-12 && max > 0.018, "pellet cone respects its half-angle");
    }
}
