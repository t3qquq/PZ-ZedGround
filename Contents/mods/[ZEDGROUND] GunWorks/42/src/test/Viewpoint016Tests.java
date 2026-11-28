import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import t3gunworks.V3;
import t3gunworks.aiming.ViewpointBridge;

/** Runs TB's Viewpoint bridge against the real Viewpoint 0.1.6 classes (no game running). */
public class Viewpoint016Tests {
    static int pass, fail;
    static void check(boolean ok, String what) { if (ok) pass++; else { fail++; System.out.println("FAIL " + what); } }

    public static void main(String[] a) throws Exception {
        ViewpointBridge.init();
        check(ViewpointBridge.available, "bridge available with Viewpoint 0.1.6");
        check("0.1.6+".equals(ViewpointBridge.version), "detected 0.1.6 layout, got " + ViewpointBridge.version);
        check(!ViewpointBridge.gunplayAiming(), "gunplay not aiming while no frame was aimed");
        double[] s = new double[2];
        ViewpointBridge.sway(s);
        check(s[0] == 0 && s[1] == 0, "no sway while idle");

        // TB's swayed direction must equal Viewpoint's own shot line (gunplay.Sight.set + turned)
        Class<?> sight = Class.forName("viewpoint.gunplay.Sight");
        Constructor<?> ctor = sight.getDeclaredConstructor();
        ctor.setAccessible(true);
        Method set = sight.getDeclaredMethod("set", double[].class, double.class, double.class, double.class, double.class);
        set.setAccessible(true);
        Field fx = sight.getDeclaredField("fx"), fy = sight.getDeclaredField("fy"), fu = sight.getDeclaredField("fu");
        fx.setAccessible(true); fy.setAccessible(true); fu.setAccessible(true);
        java.util.Random r = new java.util.Random(7);
        double worst = 0;
        V3 out = new V3();
        for (int i = 0; i < 2000; i++) {
            float yaw = (float) ((r.nextDouble() * 2 - 1) * Math.PI), pitch = (float) ((r.nextDouble() * 2 - 1) * 1.4);
            double right = (r.nextDouble() * 2 - 1) * 0.08, up = (r.nextDouble() * 2 - 1) * 0.08;
            Object o = ctor.newInstance();
            set.invoke(o, new double[]{0, 0, 0}, (double) yaw, (double) pitch, right, up);
            ViewpointBridge.direction(yaw, pitch, right, up, out);
            double d = Math.abs(out.x - fx.getDouble(o)) + Math.abs(out.y - fy.getDouble(o)) + Math.abs(out.u - fu.getDouble(o));
            worst = Math.max(worst, d);
        }
        check(worst < 1e-6, "swayed direction matches Viewpoint's Sight (worst " + worst + ")");
        ViewpointBridge.direction(0.3f, 0.2f, 0, 0, out);
        V3 plain = new V3();
        ViewpointBridge.direction(0.3f, 0.2f, plain);
        check(Math.abs(out.x - plain.x) + Math.abs(out.y - plain.y) + Math.abs(out.u - plain.u) < 1e-9, "zero sway = view direction");
        System.out.println("passed " + pass + ", failed " + fail);
        if (fail > 0) System.exit(1);
    }
}
