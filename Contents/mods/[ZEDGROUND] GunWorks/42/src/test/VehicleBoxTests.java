import org.joml.Matrix3f;
import org.joml.Vector3f;

/**
 * 1.4.3 vehicle collision: the vanilla "Car Normal" collision boxes (template CarNormalCollision, scale 1.82), in
 * vehicle local space (x = side, y = up from the body origin, z = forward), through ProjectileCollision.boxEntry.
 */
public class VehicleBoxTests {
    static int passed, failed;
    static void check(boolean ok, String what) { if (ok) passed++; else { failed++; System.out.println("FAIL " + what); } }

    static final float S = 1.82f;
    static final float[][] BOXES = { // offset xyz, extents xyz, rotate xyz (degrees)
        {0f, 0.1575f, -0.0055f, 0.8681f, 0.3187f, 2.5385f, 0, 0, 0},     // lower body
        {0f, 0.3626f, 0.3113f, 0.8022f, 0.1758f, 0.4945f, 41f, 0, 0},     // windscreen slope
        {0f, 0.3846f, -0.602f, 0.8022f, 0.3956f, 0.1868f, 44f, 0, 0},     // rear window slope
        {0f, 0.4554f, -0.1758f, 0.8022f, 0.2747f, 0.7143f, 0, 0, 0},      // cabin
    };

    static double hit(float ax, float ay, float az, float bx, float by, float bz) throws Exception {
        java.lang.reflect.Method m = t3gunworks.collision.ProjectileCollision.class.getDeclaredMethod("boxEntry", Vector3f.class, Vector3f.class,
            Vector3f.class, Vector3f.class, Vector3f.class, Vector3f.class, Vector3f.class, Matrix3f.class);
        m.setAccessible(true);
        double best = -1;
        for (float[] b : BOXES) {
            double t = (double) m.invoke(null, new Vector3f(ax, ay, az), new Vector3f(bx, by, bz),
                new Vector3f(b[0], b[1], b[2]).mul(S), new Vector3f(b[6], b[7], b[8]), new Vector3f(b[3], b[4], b[5]).mul(S),
                new Vector3f(), new Vector3f(), new Matrix3f());
            if (t >= 0 && (best < 0 || t < best)) best = t;
        }
        return best;
    }

    public static void main(String[] a) throws Exception {
        float bodyTop = (0.1575f + 0.3187f / 2) * S;            // ~0.58 m above the origin
        float cabinTop = (0.4554f + 0.2747f / 2) * S;           // ~1.08 m
        // from the side into the doors: hit
        check(hit(3, 0.3f, 0, -3, 0.3f, 0) >= 0, "door height from the side hits");
        // across over the hood (front, z = +1.8 m), just above the body: passes
        check(hit(3, bodyTop + 0.08f, 1.8f, -3, bodyTop + 0.08f, 1.8f) < 0, "just over the hood passes");
        // same height through the cabin: hits
        check(hit(3, bodyTop + 0.08f, -0.3f, -3, bodyTop + 0.08f, -0.3f) >= 0, "same height through the cabin hits");
        // over the roof: passes; into the roof from above: hits
        check(hit(3, cabinTop + 0.05f, -0.3f, -3, cabinTop + 0.05f, -0.3f) < 0, "over the roof passes");
        check(hit(0, cabinTop + 1.5f, -0.3f, 0, cabinTop - 0.1f, -0.3f) >= 0, "down into the roof hits");
        // standing on the roof, shooting forward over the hood and away: the start is above the cabin, nothing hit
        check(hit(0, cabinTop + 1.4f, -0.3f, 0, cabinTop + 0.9f, 20f) < 0, "from the roof over the hood passes");
        // shooting along the windscreen slope from the front at cabin height: the slope box takes it
        double t = hit(0, bodyTop + 0.25f, 6f, 0, bodyTop + 0.25f, -6f);
        check(t > 0.3 && t < 0.47, "windscreen slope stops a round from the front before the cabin box (" + t + ")");
        // beside the car (0.2 m outside the side): passes
        float side = 0.8681f * S / 2 + 0.2f;
        check(hit(side, 0.3f, 4, side, 0.3f, -4) < 0, "0.2 m beside the car passes");
        System.out.println("passed " + passed + ", failed " + failed);
        if (failed > 0) System.exit(1);
    }
}
