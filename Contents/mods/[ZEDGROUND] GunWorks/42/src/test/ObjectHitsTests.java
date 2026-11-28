import java.lang.reflect.Method;

import t3gunworks.api.ObjectHits;
import t3gunworks.projectile.BallisticProjectile;
import t3gunworks.projectile.BallisticsManager;
import t3gunworks.projectile.ShotGroup;

/**
 * 1.4.1 t3gunworks.api.ObjectHits through the real BallisticsManager.simulate (no game world loaded, so only the
 * registered target can stop the round): nothing registered = nothing asked; a stopping target ends the round
 * where it touches; a fly-on target is hit once, not on every following piece of flight.
 */
public class ObjectHitsTests {
    static int passed, failed;
    static void check(boolean ok, String what) { if (ok) passed++; else { failed++; System.out.println("FAIL " + what); } }

    /** A slab between x = x0 and x0 + 0.3, all y, all heights. */
    static final class Slab implements ObjectHits.Target {
        final double x0; final boolean stops; int sweeps, hits; double hitX;
        Slab(double x0, boolean stops) { this.x0 = x0; this.stops = stops; }
        public double sweep(double ax, double ay, double au, double bx, double by, double bu) {
            sweeps++;
            if (ax <= x0 && bx >= x0 && bx != ax) return (x0 - ax) / (bx - ax);
            if (ax > x0 && ax < x0 + 0.3) return 0; // starting inside
            return -1;
        }
        public boolean hit(double x, double y, double u, double dx, double dy, double du, double speed, double mass, Object shooter) {
            hits++; hitX = x; return stops;
        }
    }

    public static void main(String[] a) throws Exception {
        zombie.core.random.RandStandard.INSTANCE.init(); // offline: the game's RNG (GameClient's static init uses it)
        Method sim = BallisticsManager.class.getDeclaredMethod("simulate", BallisticProjectile.class, double.class);
        sim.setAccessible(true);

        check(!ObjectHits.active(), "nothing registered: inactive");
        BallisticProjectile p = round();
        sim.invoke(BallisticsManager.INSTANCE, p, 0.05);
        check(p.position.x > 10, "without targets the round just flies (" + p.position.x + ")");

        Slab stop = new Slab(8, true);
        ObjectHits.register(stop);
        p = round();
        for (int i = 0; i < 10 && p.alive; i++) sim.invoke(BallisticsManager.INSTANCE, p, 1.0 / 60);
        check(stop.hits == 1, "stopping object hit once (" + stop.hits + ")");
        check(!p.alive, "round stopped");
        check(Math.abs(p.position.x - 8) < 1e-6, "stopped where it touched (" + p.position.x + ")");
        ObjectHits.unregister(stop);

        Slab through = new Slab(8, false);
        ObjectHits.register(through);
        p = round();
        for (int i = 0; i < 10 && p.alive; i++) sim.invoke(BallisticsManager.INSTANCE, p, 1.0 / 60);
        check(through.hits == 1, "fly-on object hit once, not once per step (" + through.hits + ")");
        check(p.position.x > 9, "round flew on past the object (" + p.position.x + ")");
        ObjectHits.unregister(through);
        check(!ObjectHits.active(), "unregistered: inactive again");

        // test rounds never ask
        Slab t = new Slab(8, true);
        ObjectHits.register(t);
        p = round();
        p.group.test = true;
        sim.invoke(BallisticsManager.INSTANCE, p, 0.05);
        check(t.sweeps == 0 && t.hits == 0, "test rounds ignore objects");
        ObjectHits.unregister(t);

        // the aim ray through the crosshair sees objects too (else the round converges on the wall behind them)
        t3gunworks.collision.ProjectileCollision col = new t3gunworks.collision.ProjectileCollision();
        t3gunworks.collision.ProjectileCollision.Result res = new t3gunworks.collision.ProjectileCollision.Result();
        t3gunworks.V3 start = new t3gunworks.V3(0, 0.5, 1.5), fwd = new t3gunworks.V3(1, 0, 0);
        check(!t3gunworks.aiming.SceneRay.sweep(col, start, fwd, 60, new zombie.characters.IsoGameCharacter[1], 0, res), "aim ray: nothing without targets");
        Slab aimSlab = new Slab(20, true);
        ObjectHits.register(aimSlab);
        boolean seen = t3gunworks.aiming.SceneRay.sweep(col, start, fwd, 60, new zombie.characters.IsoGameCharacter[1], 0, res);
        check(seen && Math.abs(res.point.x - 20) < 1e-6, "aim ray stops on the object (" + res.point.x + ")");
        check(aimSlab.hits == 0, "looking never counts as a hit");
        check(!ObjectHits.hit(0, 0, 0, 1, 0, 0, 300, 8, null), "peek leaves no pending hit behind");
        ObjectHits.unregister(aimSlab);

        // tiles claimed by a mod through a reflection proxy (the way Physics Props registers)
        check(!ObjectHits.covers(3, 4, 0), "no tiles claimed: nothing covered");
        Object tiles = java.lang.reflect.Proxy.newProxyInstance(ObjectHits.Tiles.class.getClassLoader(), new Class<?>[] {ObjectHits.Tiles.class},
            (proxy, m, args) -> switch (m.getName()) {
                case "covers" -> ((Integer) args[0]) == 3 && ((Integer) args[1]) == 4;
                case "toString" -> "test tiles";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> Boolean.FALSE;
            });
        ObjectHits.registerTiles((ObjectHits.Tiles) tiles);
        check(ObjectHits.covers(3, 4, 0) && !ObjectHits.covers(3, 5, 0), "claimed tile covered, others not");
        ObjectHits.unregisterTiles((ObjectHits.Tiles) tiles);
        check(!ObjectHits.covers(3, 4, 0), "unclaimed again");

        System.out.println("passed " + passed + ", failed " + failed);
        if (failed > 0) System.exit(1);
    }

    static BallisticProjectile round() {
        BallisticProjectile p = new BallisticProjectile();
        p.position.set(0, 0.5, 1.4); p.previousPosition.set(p.position); p.origin.set(p.position);
        p.velocity.set(360, 0, 0);
        p.muzzleVelocity = 360; p.gravity = 9.81; p.drag = 0.001; p.massGrams = 8;
        p.maxDistance = 500; p.maxLifetime = 3; p.alive = true;
        ShotGroup g = new ShotGroup();
        p.group = g;
        g.pelletsAlive = 1;
        return p;
    }
}
