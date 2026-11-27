package t3zedballistics.aiming;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import t3zedballistics.Log;

/**
 * Optional link to Project Viewpoint ADS & Optics (mod id ProjectViewpointADS, package pvads).
 *
 * While the sights are raised, ADS publishes the line the player actually looks along - the gun's sight
 * axis after recoil kick and weapon inertia, i.e. exactly where the red dot / reticle is drawn - plus the
 * sight's zero distance (pvads.Sight.ray(double[7]): origin x,y,u; unit direction x,y,u; zero metres).
 * True Ballistics then aims along that line instead of the screen centre, and zeroes to that sight.
 *
 * Looked up by reflection once; no compile-time dependency in either direction. If ADS is not installed
 * (or is an older version without pvads.Sight) this simply reports "no sight" forever.
 */
public final class AdsSightBridge {
    private static Method ray;
    private static boolean resolved, available;

    public static boolean available() {
        resolve();
        return available;
    }

    /** Fills out (length >= 7) and returns true when the ADS sights are up and fresh. */
    public static boolean ray(double[] out) {
        resolve();
        if (!available) return false;
        try {
            return Boolean.TRUE.equals(ray.invoke(null, (Object) out));
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
            available = false;
            Log.error("ADS sight bridge failed; aiming falls back to the screen centre", e instanceof InvocationTargetException ite ? ite.getCause() : e);
            return false;
        }
    }

    private static void resolve() {
        if (resolved) return;
        resolved = true;
        try {
            Class<?> c;
            try {
                c = Class.forName("pvads.Sight", true, AdsSightBridge.class.getClassLoader());
            } catch (ClassNotFoundException e) {
                ClassLoader ctx = Thread.currentThread().getContextClassLoader();
                if (ctx == null) throw e;
                c = Class.forName("pvads.Sight", true, ctx);
            }
            ray = c.getMethod("ray", double[].class);
            if (ray.getReturnType() != boolean.class) throw new NoSuchMethodException("pvads.Sight.ray must return boolean");
            available = true;
            Log.info("Project Viewpoint ADS sight bridge ready: aiming follows the raised sights and their zero.");
        } catch (ClassNotFoundException e) {
            available = false;
            Log.info("Project Viewpoint ADS (with pvads.Sight) not present; aiming uses Viewpoint's view ray.");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            available = false;
            Log.warn("Project Viewpoint ADS found but its sight API is incompatible (" + e + "); aiming uses Viewpoint's view ray.");
        }
    }

    private AdsSightBridge() {}
}
