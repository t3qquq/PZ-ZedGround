import t3zedballistics.V3;
import t3zedballistics.api.ImpactEvents;
import t3zedballistics.collision.HitVolumes;

/** Offline checks for the 1.3.0 impact-event additions: capsule exit geometry and listener delivery. */
public class ImpactEventsTests {
    static int passed, failed;
    static void check(boolean ok, String what) { if (ok) passed++; else { failed++; System.out.println("FAIL: " + what); } }
    static void near(double a, double b, double tol, String what) { check(Math.abs(a - b) <= tol, what + " (got " + a + ", want " + b + ")"); }

    public static void main(String[] args) {
        HitVolumes v = new HitVolumes();
        v.add(0, 0, 1.05, 0, 0, 1.42, 0.16, HitVolumes.SPINE);
        int[] part = new int[2];
        V3 a = new V3(-2, 0, 1.2), b = new V3(2, 0, 1.2);
        double t = v.test(a, b, part);
        check(t > 0, "segment enters torso capsule");
        check(v.lastIndex == 0, "lastIndex set");
        V3 entry = new V3().lerp(a, b, t), exit = new V3();
        near(entry.x, -0.16, 0.01, "entry on near side");
        check(v.exitPoint(v.lastIndex, entry, 1, 0, 0, exit), "exit found");
        near(exit.x, 0.16, 0.01, "exit on far side");
        near(exit.u, 1.2, 1e-6, "exit at same height");
        // oblique through the chest, 45 degrees
        double s = Math.sqrt(0.5);
        V3 a2 = new V3(-2 * s, -2 * s, 1.3), b2 = new V3(2 * s, 2 * s, 1.3);
        double t2 = v.test(a2, b2, part);
        V3 e2 = new V3().lerp(a2, b2, t2), x2 = new V3();
        check(v.exitPoint(0, e2, s, s, 0, x2), "oblique exit");
        near(x2.dist(e2), 0.32, 0.02, "oblique chord = diameter through the axis");
        // grazing line misses the far intersection gracefully
        check(!v.exitPoint(5, e2, 1, 0, 0, x2), "invalid capsule index");
        check(v.test(new V3(-2, 0.5, 1.2), new V3(2, 0.5, 1.2), part) < 0 && v.lastIndex == -1, "miss resets lastIndex");

        // body part filter: removing hand + forearm keeps the upper arm and torso
        HitVolumes h = new HitVolumes();
        h.add(0, 0, 1.05, 0, 0, 1.42, 0.16, HitVolumes.SPINE);                         // T_TORSO_UPPER
        h.add(0.2, 0, 1.4, 0.2, 0, 1.1, 0.055, HitVolumes.L_UPPER_ARM);                  // T_UPPERARM_L
        h.add(0.2, 0, 1.1, 0.2, 0, 0.85, 0.045, HitVolumes.L_LOWER_ARM);                 // T_FOREARM_L
        h.add(0.2, 0, 0.85, 0.2, 0, 0.75, 0.045, HitVolumes.L_LOWER_ARM, HitVolumes.T_HAND_L, HitVolumes.T_HAND_L, 0);
        h.add(0, 0, 0.82, 0, 0, 1.02, 0.18, HitVolumes.PELVIS, HitVolumes.T_GROIN, HitVolumes.T_TORSO_LOWER, 0.35);
        h.removeMissing((1L << HitVolumes.T_HAND_L) | (1L << HitVolumes.T_FOREARM_L));
        check(h.count == 3, "hand and forearm capsules removed (" + h.count + ")");
        check(h.part[1] == HitVolumes.L_UPPER_ARM && h.part[2] == HitVolumes.PELVIS, "order kept");
        h.removeMissing(1L << HitVolumes.T_GROIN);
        check(h.count == 3 && h.typeA[2] == HitVolumes.T_TORSO_LOWER && h.typeB[2] == HitVolumes.T_TORSO_LOWER, "split capsule keeps the remaining part");
        h.removeMissing(0);
        check(h.count == 3, "empty mask is a no-op");

        // listener delivery, ordering, isolation from throwing listeners
        final StringBuilder order = new StringBuilder();
        ImpactEvents.Listener boom = i -> { order.append('x'); throw new RuntimeException("listener failure"); };
        ImpactEvents.Listener ok = i -> order.append(i.phase == ImpactEvents.PHASE_APPLIED ? 'a' : '?');
        ImpactEvents.addListener(boom);
        ImpactEvents.addListener(ok);
        ImpactEvents.addListener(ok); // duplicate ignored
        ImpactEvents.Impact i = ImpactEvents.begin();
        long id1 = i.impactId;
        ImpactEvents.applying(i);
        check(ImpactEvents.current() == i, "current() during Hit");
        ImpactEvents.applied();
        check(ImpactEvents.current() == null, "current() cleared");
        ImpactEvents.fire(i, ImpactEvents.PHASE_APPLIED);
        check(order.toString().equals("xa"), "listeners in order, failure isolated: " + order);
        ImpactEvents.Impact j = ImpactEvents.begin();
        check(j.impactId == id1 + 1 && Float.isNaN(j.damageApplied), "ids increase, object cleared");
        ImpactEvents.removeListener(boom);
        ImpactEvents.removeListener(ok);
        check(!ImpactEvents.hasListeners(), "listeners removed");
        check(!ImpactEvents.zombiePartEncodingRequested(), "encoding off by default");
        System.out.println("passed " + passed + ", failed " + failed);
        System.exit(failed == 0 ? 0 : 1);
    }
}
