package halodropclient;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Halo Drop: wide-angle "fisheye" look during freefall in Viewpoint 1st/3rd person (11-23, never in isometric).
 *
 * Why: 3000 m of freefall looked flat. A wide lens close to the subject exaggerates depth (near things huge, far things
 *   tiny) and a curved projection bends the horizon, which reads as speed even when little moves on screen.
 * What: while Lua publishes HaloDrop.LensState (HaloDropLens.lua, from the jump until the canopy is open), three
 *   Viewpoint values are changed on the way out of Viewpoint's own methods. Nothing is written to Viewpoint's settings
 *   (the window on Delete keeps the player's values and they come back the moment the state is gone):
 *     viewpoint.core.View.fovY(boolean)             -> wider vertical field of view          (PatchVpFov)
 *     viewpoint.render.Panini.strength()            -> Viewpoint's own curved projection     (PatchVpPanini)
 *     viewpoint.camera.AddonLayers.add(CameraShot)  -> third-person camera pulled closer     (PatchVpCamera)
 *   Class and member names were checked with javap only (Viewpoint jar of 11-15); what each value does in game is
 *   unverified until the first run - the log lines below are there to settle it.
 * Lua -> Java: no bridge. HaloDrop.LensState is read from LuaManager.env by reflection, the same way Blue Zone's
 *   ZoneState reads BlueZone.C. Lua replaces the table as a whole every tick, so a reader sees one snapshot.
 *   Fields: fov (0..1, share of the way from the player's FOV to cap), cap (degrees), panini (strength),
 *   pull (0..1, how much of the size the wider FOV took away is given back by moving the camera in),
 *   at (getTimestampMs). A state older than STALE_MS counts as none, so the lens can never stick.
 * If Viewpoint is not installed the target classes never load and the patches do nothing. If Viewpoint renames a
 * method, ZombieBuddy logs that the patch found nothing and that part of the look is simply missing.
 * Public for the same reason as SkyLight: ZombieBuddy inlines the advice into Viewpoint's classes.
 */
public final class Lens {
    static final String TAG = "[HaloDrop][Lens] ";
    static final long REFRESH_NS = 8_000_000L;   // Lua state is re-read at most this often (the hooks run several times a frame)
    static final long STALE_MS = 1000L;
    static final float MAX_FOV_DEG = 140f;       // Viewpoint's own upper limit (View.MAX_FOV, javap -constants)
    static final long STATE_LOG_MS = 3000L;
    static final String[] HOOK = { "View.fovY", "Panini.strength", "AddonLayers.add" };

    private static boolean resolved, broken;
    private static Field fEnv;
    private static Method mRawget;
    private static int errors;

    private static volatile boolean started;
    private static volatile long lastRefresh;
    private static volatile float sFov, sCap, sPanini, sPull;
    private static volatile boolean active;

    private static final boolean[] seen = new boolean[3];
    private static final int[] calls = new int[3];
    private static volatile float lastBaseDeg, lastOutDeg, lastPaniniUser, lastPaniniOut, lastDist, lastBack;
    private static volatile boolean lastRad;
    private static long nextStateLog;

    private static boolean shotResolved, shotBroken;
    private static Field fDistance, fAddBack;

    private Lens() {}

    public static void log(String s) { System.out.println(TAG + s); }

    private static void fail(String where, Throwable t) {
        errors++;
        if (errors <= 5) log(where + " failed (" + errors + "): " + t);
        if (errors >= 20 && !broken) {
            broken = true;
            sFov = 0f; sPanini = 0f; sPull = 0f;
            log("off after " + errors + " errors: Viewpoint keeps its own view");
        }
    }

    private static Object get(Object table, String key) throws Exception {
        return table == null ? null : mRawget.invoke(table, key);
    }

    private static float num(Object table, String key) throws Exception {
        Object v = get(table, key);
        if (!(v instanceof Number)) return 0f;
        float f = ((Number) v).floatValue();
        return Float.isNaN(f) || Float.isInfinite(f) ? 0f : f;
    }

    private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }

    /** Re-reads HaloDrop.LensState. Called from every hook; cheap between refreshes. */
    private static void refresh() {
        long n = System.nanoTime();
        if (started && n - lastRefresh < REFRESH_NS) return;
        lastRefresh = n;
        started = true;
        if (broken) return;
        try {
            if (!resolved) {
                ClassLoader cl = Lens.class.getClassLoader();
                fEnv = Class.forName("zombie.Lua.LuaManager", false, cl).getField("env");
                mRawget = Class.forName("se.krka.kahlua.vm.KahluaTable", false, cl).getMethod("rawget", Object.class);
                resolved = true;
                log("state lookups resolved (LuaManager.env, KahluaTable.rawget)");
            }
            Object st = get(get(fEnv.get(null), "HaloDrop"), "LensState");
            float fov = 0f, cap = 0f, pan = 0f, pull = 0f;
            boolean stale = false;
            if (st != null) {
                Object at = get(st, "at");
                long age = at instanceof Number ? System.currentTimeMillis() - ((Number) at).longValue() : Long.MAX_VALUE;
                if (age <= STALE_MS) {
                    fov = clamp01(num(st, "fov"));
                    cap = num(st, "cap");
                    pan = Math.max(0f, num(st, "panini"));
                    pull = clamp01(num(st, "pull"));
                } else {
                    stale = true;
                }
            }
            sCap = cap; sFov = fov; sPanini = pan; sPull = pull;
            boolean on = fov > 0.001f || pan > 0.001f;
            long now = System.currentTimeMillis();
            if (on != active) {
                active = on;
                if (on) {
                    calls[0] = 0; calls[1] = 0; calls[2] = 0;
                    nextStateLog = now + 500L;
                    log("on: fov=" + fov + " cap=" + cap + " panini=" + pan + " pull=" + pull);
                } else {
                    log("off (" + (stale ? "Lua state went stale" : "Lua state gone") + "): " + summary());
                }
            } else if (on && now >= nextStateLog) {
                nextStateLog = now + STATE_LOG_MS;
                log("active: state fov=" + fov + " panini=" + pan + " pull=" + pull + " | " + summary());
                calls[0] = 0; calls[1] = 0; calls[2] = 0;
            }
        } catch (Throwable t) {
            fail("state read", t);
        }
    }

    private static String summary() {
        return "fovY " + lastBaseDeg + " -> " + lastOutDeg + " deg" + (lastRad ? " (Viewpoint works in radians)" : "")
            + " x" + calls[0] + ", panini " + lastPaniniUser + " -> " + lastPaniniOut + " x" + calls[1]
            + ", camera distance " + lastDist + " back " + lastBack + " x" + calls[2];
    }

    private static void reached(int which, String value) {
        if (seen[which]) return;
        seen[which] = true;
        log("patch reached: viewpoint " + HOOK[which] + " (" + value + ")");
    }

    /** viewpoint.core.View.fovY(boolean): the player's vertical FOV for this view. Returns the widened one. */
    public static float fov(float base) {
        try {
            refresh();
            reached(0, "player value " + base);
            float a = sFov;
            if (!(a > 0f) || !(base > 0f)) return base;
            boolean rad = base < 3.2f;   // degrees expected (View.MIN_FOV 30); handled either way
            float deg = rad ? (float) Math.toDegrees(base) : base;
            float cap = Math.min(sCap, MAX_FOV_DEG);
            if (!(cap > deg)) return base;
            float out = deg + (cap - deg) * a;
            lastRad = rad; lastBaseDeg = deg; lastOutDeg = out;
            calls[0]++;
            return rad ? (float) Math.toRadians(out) : out;
        } catch (Throwable t) {
            fail("fov", t);
            return base;
        }
    }

    /** viewpoint.render.Panini.strength(): never lowers what the player set. */
    public static float panini(float user) {
        try {
            refresh();
            reached(1, "player value " + user);
            float p = sPanini;
            if (!(p > 0f) || p <= user) return user;
            lastPaniniUser = user; lastPaniniOut = p;
            calls[1]++;
            return p;
        } catch (Throwable t) {
            fail("panini", t);
            return user;
        }
    }

    /**
     * viewpoint.camera.AddonLayers.add(CameraShot), after Viewpoint added its addon layers to the shot.
     * A wider FOV makes the character smaller; moving the camera in by distance * (1 - tan(old/2) / tan(new/2)) would
     * give the size back in full. pull is the share of that. First person has no camera distance, so nothing moves.
     */
    public static void shot(Object shot) {
        if (shot == null || shotBroken) return;
        try {
            refresh();
            if (!shotResolved) {
                fDistance = shot.getClass().getField("distance");
                fAddBack = shot.getClass().getField("addBack");
                shotResolved = true;
                log("camera shot fields resolved (CameraShot.distance, CameraShot.addBack)");
            }
            float dist = fDistance.getFloat(shot);
            reached(2, "distance " + dist + " addBack " + fAddBack.getFloat(shot));
            float pull = sPull, base = lastBaseDeg, out = lastOutDeg;
            if (!(pull > 0f) || !(sFov > 0f) || !(dist > 0f) || !(out > base) || !(base > 0f)) return;
            double ratio = Math.tan(Math.toRadians(base) * 0.5) / Math.tan(Math.toRadians(out) * 0.5);
            float back = (float) (-pull * dist * (1.0 - ratio));
            fAddBack.setFloat(shot, fAddBack.getFloat(shot) + back);
            lastDist = dist; lastBack = back;
            calls[2]++;
        } catch (Throwable t) {
            if (!shotResolved) {
                shotBroken = true;
                log("camera shot fields not found, camera stays where Viewpoint puts it: " + t);
            } else {
                fail("shot", t);
            }
        }
    }
}
