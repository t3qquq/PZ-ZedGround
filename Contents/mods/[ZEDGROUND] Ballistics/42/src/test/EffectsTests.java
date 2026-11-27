import t3zedballistics.V3;
import t3zedballistics.collision.WorldCollision;
import t3zedballistics.effects.ImpactEffects;
import t3zedballistics.effects.ImpactMaterial;
import t3zedballistics.weapon.AmmoBallisticsResolver;
import t3zedballistics.weapon.BallisticProfile;

/** Offline checks for 1.4.0 bullet holes / impact debris: surface normals, materials, simulation, relay. */
public class EffectsTests {
    static int passed, failed;
    static void check(boolean ok, String what) { if (ok) passed++; else { failed++; System.out.println("FAIL " + what); } }
    static void near(double a, double b, double tol, String what) { check(Math.abs(a - b) <= tol, what + " (got " + a + ", want " + b + ")"); }

    public static void main(String[] args) throws Exception {
        normals();
        materials();
        effects();
        System.out.println("passed " + passed + ", failed " + failed);
        if (failed > 0) System.exit(1);
    }

    static void normals() {
        VtbTests.World w = new VtbTests.World();
        w.wallX = 5;
        WorldCollision.Hit h = new WorldCollision.Hit();
        // east-bound round hits the wall on the edge between tiles 5 and 6
        check(WorldCollision.trace(new V3(2.5, 2.5, 1.4), new V3(9.5, 2.5, 1.4), w, h, null), "wall hit east-bound");
        check(h.kind == WorldCollision.HIT_WALL, "kind wall");
        near(h.nx, -1, 1e-12, "east-bound wall normal faces west");
        check(h.ny == 0 && h.nu == 0 && h.edgeDx == 1 && h.edgeDy == 0, "east-bound edge +x");
        near(h.point.x, 6, 1e-9, "hit on the edge x=6");
        // west-bound
        check(WorldCollision.trace(new V3(9.5, 2.5, 1.4), new V3(2.5, 2.5, 1.4), w, h, null), "wall hit west-bound");
        near(h.nx, 1, 1e-12, "west-bound wall normal faces east");
        check(h.edgeDx == -1, "west-bound edge -x");
        // floor
        VtbTests.World open = new VtbTests.World();
        check(WorldCollision.trace(new V3(2.5, 2.5, 1.4), new V3(6.5, 2.5, -1.0), open, h, null), "floor hit");
        check(h.kind == WorldCollision.HIT_FLOOR && h.nu == 1 && h.nx == 0 && h.ny == 0, "floor normal up");
        // ceiling
        open.ceilingLevel = 1;
        check(WorldCollision.trace(new V3(2.5, 2.5, 1.4), new V3(4.5, 2.5, 4.0), open, h, null), "ceiling hit");
        check(h.kind == WorldCollision.HIT_CEILING && h.nu == -1, "ceiling normal down");
        // tree trunk: radial, facing the round
        VtbTests.World t = new VtbTests.World();
        t.treeX = 5; t.treeY = 2;
        check(WorldCollision.trace(new V3(1.5, 2.55, 1.2), new V3(9.5, 2.55, 1.2), t, h, null), "tree hit");
        check(h.kind == WorldCollision.HIT_TREE, "kind tree");
        near(Math.hypot(h.nx, h.ny), 1, 1e-9, "tree normal unit");
        check(h.nx < -0.8 && h.nu == 0, "tree normal faces the shooter");
    }

    static void materials() {
        check(ImpactMaterial.fromSpriteName("blends_natural_01_16") == ImpactMaterial.DIRT, "natural ground = dirt");
        check(ImpactMaterial.fromSpriteName("blends_street_01_5") == ImpactMaterial.CONCRETE, "street = concrete");
        check(ImpactMaterial.fromSpriteName("floors_interior_carpet_01_3") == ImpactMaterial.CARPET, "carpet");
        check(ImpactMaterial.fromSpriteName("vegetation_trees_01_8") == ImpactMaterial.WOOD_SOLID, "tree = solid wood");
        check(ImpactMaterial.fromSpriteName("fencing_chainlink_01_4") == ImpactMaterial.METAL, "chain link = metal");
        check(ImpactMaterial.fromSpriteName("walls_interior_house_01_3") == ImpactMaterial.PLASTER, "interior wall = plaster");
        check(ImpactMaterial.fromSpriteName("floors_exterior_natural_01_5") == ImpactMaterial.DIRT, "exterior natural floor = dirt");
        check(ImpactMaterial.fromSpriteName("") == -1 && ImpactMaterial.fromSpriteName(null) == -1, "no name");
        check(ImpactMaterial.NAMES.length == 27 && ImpactMaterial.NAMES[ImpactMaterial.SNOW].equals("Snow"), "names follow MaterialType");
        for (int m = 0; m < ImpactMaterial.NAMES.length; m++) {
            try {
                zombie.iso.enums.MaterialType.valueOf(ImpactMaterial.NAMES[m]);
            } catch (IllegalArgumentException e) {
                check(false, "material name " + ImpactMaterial.NAMES[m] + " is a game MaterialType");
            }
        }
        check(ImpactMaterial.family(ImpactMaterial.BRICK) == ImpactMaterial.F_MASONRY, "brick is masonry");
        check(ImpactMaterial.family(ImpactMaterial.METAL_SOLID) == ImpactMaterial.F_METAL, "metal family");
        check(ImpactMaterial.family(ImpactMaterial.GRASS) == ImpactMaterial.F_EARTH, "grass family");
    }

    static void effects() throws Exception {
        ImpactEffects fx = ImpactEffects.INSTANCE;
        fx.clear();
        BallisticProfile rifle = AmmoBallisticsResolver.fromAmmoName("base:bullets_556");
        V3 dir = new V3(1, 0, 0);
        // a wall hit from a rifle, sent to the network
        fx.impact(new V3(6, 2.5, 1.4), -1, 0, 0, dir, WorldCollision.HIT_WALL, 5, 2, 0, 1, 0, ImpactMaterial.CONCRETE, rifle, 1.0, true);
        Object scene = new Object();
        fx.frame(scene, 0f, 0f, 0f);
        float[] snap = snapshot(scene);
        check(snap != null && snap[0] == 1, "one hole captured");
        check(snap != null && snap[1] > 3, "concrete throws debris (" + (snap == null ? 0 : snap[1]) + ")");
        // the hole sits on the surface, just in front of it, normal mirrored into render space (x -> -x)
        if (snap != null) {
            near(-snap[2], 6 - 0.004, 1e-4, "hole on the wall (render x = camX - x)");
            near(snap[5], 1, 1e-6, "render normal x");
            check(snap[8] > 0.004 && snap[8] < 0.05, "rifle crater size plausible (" + snap[8] + " m)");
        }
        // simulate 4 s: everything settles on/above the floor and expires
        double minU = Double.MAX_VALUE;
        for (int i = 0; i < 400; i++) {
            Thread.sleep(0);
            fxStep(fx, 0.01);
            minU = Math.min(minU, minParticleU(fx));
        }
        check(minU >= -1e-9, "debris never falls through the floor (min " + minU + ")");
        check(particles(fx) == 0, "debris expires");

        // relay: what this client sends recreates the same hole elsewhere
        double[] net = fx.takeForNetwork();
        check(net != null && net.length == 13, "one impact queued for the relay");
        fx.clear();
        if (net != null) fx.remote(net, 1);
        fx.frame(scene, 0f, 0f, 0f);
        snap = snapshot(scene);
        check(snap != null && snap[0] == 1, "remote impact makes a hole");
        check(fx.takeForNetwork() == null, "remote impacts are not sent on again");

        // cap: oldest holes are replaced
        fx.clear();
        int old = ImpactEffects.maxHoles;
        ImpactEffects.maxHoles = 10;
        for (int i = 0; i < 25; i++)
            fx.impact(new V3(6, 2.5 + i * 0.1, 1.4), -1, 0, 0, dir, WorldCollision.HIT_WALL, 5, 2, 0, 1, 0, ImpactMaterial.WOOD, rifle, 1.0, false);
        fx.frame(scene, 0f, 0f, 0f);
        snap = snapshot(scene);
        check(snap != null && snap[0] == 10, "hole cap respected (" + (snap == null ? -1 : snap[0]) + ")");
        ImpactEffects.maxHoles = old;

        // disabled: nothing is made
        fx.clear();
        ImpactEffects.enabled = false;
        fx.impact(new V3(6, 2.5, 1.4), -1, 0, 0, dir, WorldCollision.HIT_WALL, 5, 2, 0, 1, 0, ImpactMaterial.WOOD, rifle, 1.0, true);
        check(fx.takeForNetwork() == null && particles(fx) == 0, "off means off");
        ImpactEffects.enabled = true;
        fx.clear();
    }

    // package-private access through reflection (test only)
    static float[] snapshot(Object scene) throws Exception {
        java.lang.reflect.Field f = ImpactEffects.class.getDeclaredField("snapshots");
        f.setAccessible(true);
        return ((java.util.Map<Object, float[]>) f.get(null)).get(scene);
    }
    static void fxStep(ImpactEffects fx, double dt) throws Exception {
        java.lang.reflect.Method m = ImpactEffects.class.getDeclaredMethod("simulate", double.class);
        m.setAccessible(true);
        m.invoke(fx, dt);
    }
    static int particles(ImpactEffects fx) throws Exception {
        java.lang.reflect.Field f = ImpactEffects.class.getDeclaredField("particleCount");
        f.setAccessible(true);
        return f.getInt(fx);
    }
    static double minParticleU(ImpactEffects fx) throws Exception {
        java.lang.reflect.Field f = ImpactEffects.class.getDeclaredField("pu");
        f.setAccessible(true);
        double[] pu = (double[]) f.get(fx);
        int n = particles(fx);
        double m = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) m = Math.min(m, pu[i]);
        return m;
    }
}
