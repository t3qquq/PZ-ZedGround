import java.lang.reflect.Field;
import java.lang.reflect.Method;

import t3zedballistics.V3;
import t3zedballistics.collision.CharacterHistory;
import t3zedballistics.collision.HitVolumes;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoZombie;

/**
 * 1.4.5 lag compensation: CharacterHistory interpolation, caps and resets, and that a rewound body is hit
 * by a round aimed where it was seen while the present body would be missed.
 */
public class LagCompTests {
    static int passed, failed;
    static void check(boolean ok, String what) { if (ok) passed++; else { failed++; System.out.println("FAIL " + what); } }
    static boolean near(double a, double b) { return Math.abs(a - b) < 1e-4; }

    static Field fx, fy, fz;
    static IsoGameCharacter zombie(float x, float y, float z) throws Exception {
        Field u = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        u.setAccessible(true);
        IsoGameCharacter c = (IsoGameCharacter) ((sun.misc.Unsafe) u.get(null)).allocateInstance(IsoZombie.class);
        move(c, x, y, z);
        return c;
    }
    static void move(IsoGameCharacter c, float x, float y, float z) throws Exception {
        if (fx == null) {
            Class<?> m = zombie.iso.IsoMovingObject.class;
            fx = m.getDeclaredField("x"); fy = m.getDeclaredField("y"); fz = m.getDeclaredField("z");
            fx.setAccessible(true); fy.setAccessible(true); fz.setAccessible(true);
        }
        fx.setFloat(c, x); fy.setFloat(c, y); fz.setFloat(c, z);
    }

    public static void main(String[] args) throws Exception {
        Method put = CharacterHistory.class.getDeclaredMethod("put", IsoGameCharacter.class, long.class, float.class, float.class, float.class);
        put.setAccessible(true);
        long ms = 1_000_000L;
        long base = 10_000 * ms;
        double[] o = new double[3];

        // a zombie crossing at 2 m/s along x, sampled every 50 ms
        IsoGameCharacter z = zombie(0, 0, 0);
        for (int i = 0; i <= 10; i++) put.invoke(null, z, base + i * 50 * ms, 10f + 0.1f * i, 20f, 0f);
        move(z, 11f, 20f, 0f);                       // now (t = base + 500 ms)
        long now = base + 500 * ms;

        check(CharacterHistory.offset(z, base + 250 * ms, now, o) && near(o[0], -0.5) && near(o[1], 0), "250 ms back: half a metre behind");
        check(CharacterHistory.offset(z, base + 275 * ms, now, o) && near(o[0], -0.45), "interpolates between samples");
        check(!CharacterHistory.offset(z, now, now, o) && o[0] == 0, "the present is not rewound");
        check(!CharacterHistory.offset(z, 0, now, o), "no seen time, no rewind");
        check(!CharacterHistory.offset(z, base + 600 * ms, now + 200 * ms, o), "after the newest sample: present");
        // cap: 0.5 s
        check(CharacterHistory.offset(z, base - 5000 * ms, now, o) && near(o[0], -1.0), "rewind capped at 0.5 s (oldest sample in range)");

        // a teleport is not motion
        IsoGameCharacter t = zombie(0, 0, 0);
        put.invoke(null, t, base, 0f, 0f, 0f);
        put.invoke(null, t, base + 50 * ms, 0.1f, 0f, 0f);
        move(t, 30f, 0f, 0f);
        check(!CharacterHistory.offset(t, base + 25 * ms, base + 60 * ms, o) && o[0] == 0, "teleport ignored");

        // unknown character
        check(!CharacterHistory.offset(zombie(1, 1, 0), base, now, o), "unknown character: no rewind");

        // a body shifted back by the offset is hit by a round through where it was seen
        HitVolumes v = new HitVolumes();
        v.add(11, 20, 0.9, 11, 20, 1.5, 0.16, HitVolumes.SPINE);      // torso now at x = 11
        V3 a = new V3(10.5, 10, 1.2), b = new V3(10.5, 30, 1.2);     // round along y through x = 10.5 (seen there)
        int[] part = new int[2];
        check(v.test(a, b, part) < 0, "present torso is missed (it walked on)");
        CharacterHistory.offset(z, base + 250 * ms, now, o);
        v.translate(o[0], o[1], o[2]);
        check(v.test(a, b, part) >= 0 && part[0] == HitVolumes.SPINE, "rewound torso is hit");

        System.out.println("passed " + passed + ", failed " + failed);
        if (failed > 0) System.exit(1);
    }
}
