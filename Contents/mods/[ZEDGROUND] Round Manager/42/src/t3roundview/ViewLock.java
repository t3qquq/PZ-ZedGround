package t3roundview;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Keeps Viewpoint in first or third person (no isometric view) while the Lua side asks for it.
 *
 * Viewpoint has its own "held view": viewpoint.core.View.heldView with HOLD_NONE 0 / HOLD_FIRST 1 / HOLD_THIRD 2 /
 * HOLD_OFF 3, consumed by viewpoint.ViewHold.frame (javap of Viewpoint 0.1.7: names only). This class writes that value
 * every frame, so Viewpoint itself switches in at join and refuses to stay isometric.
 *
 * Lua -> Java: the global table RoundManager.VIEW (RoundManagerView.lua)
 *   lock  boolean   hold the view (sandbox RoundManager.ViewLock)
 *   mode  string    "first" | "third"
 * Java -> Lua: written into the same table every frame (the heartbeat Lua uses to decide whether the lock works)
 *   jar      string   this jar's version
 *   enabled  boolean  viewpoint.core.View.enabled (Viewpoint is drawing the world: first or third person)
 *   third    boolean  viewpoint.core.View.thirdPerson
 *   at       number   System.currentTimeMillis() of the last frame (Lua getTimestampMs is the same clock)
 * Everything is reached by reflection, so the jar builds without the game or Viewpoint. If a name changes with a
 * Viewpoint update the lock turns itself off with one log line; Lua then sees no heartbeat.
 */
public final class ViewLock {
    private ViewLock() {}

    private static final int HOLD_NONE = 0, HOLD_FIRST = 1, HOLD_THIRD = 2;

    private static boolean resolved, broken, wasLocked, jarSent;
    private static Field fEnv, fHeld, fEnabled, fThird;
    private static Method mRawget, mRawset;
    private static int errors, lastHeld = -1;
    private static String lastLine = "", seenFrom = "";

    public static void log(String msg) {
        System.out.println("[RoundManagerView] " + msg);
    }

    private static boolean resolve() {
        if (resolved) return true;
        if (broken) return false;
        try {
            ClassLoader cl = ViewLock.class.getClassLoader();
            Class<?> lm = Class.forName("zombie.Lua.LuaManager", false, cl);
            fEnv = lm.getField("env");
            Class<?> kt = Class.forName("se.krka.kahlua.vm.KahluaTable", false, cl);
            mRawget = kt.getMethod("rawget", Object.class);
            mRawset = kt.getMethod("rawset", Object.class, Object.class);
            Class<?> view = Class.forName("viewpoint.core.View", false, cl);
            fHeld = view.getField("heldView");
            fEnabled = view.getField("enabled");
            fThird = view.getField("thirdPerson");
            resolved = true;
            log("lookups resolved (LuaManager.env, KahluaTable.rawget/rawset, View.heldView/enabled/thirdPerson)");
            return true;
        } catch (Throwable t) {
            broken = true;
            log("viewpoint hooks FAILED, view lock off: " + t);
            return false;
        }
    }

    private static Object get(Object table, String key) throws Exception {
        return table == null ? null : mRawget.invoke(table, key);
    }

    /** called every frame from the Viewpoint patches (game thread) */
    public static void frame(String from) {
        if (!resolve()) return;
        try {
            if (seenFrom.indexOf(from) < 0) {
                seenFrom = seenFrom + from + " ";
                log("first call from " + from);
            }
            Object view = get(get(fEnv.get(null), "RoundManager"), "VIEW");
            if (view == null) return;   // Lua not loaded yet (main menu) or Round Manager off

            boolean lock = Boolean.TRUE.equals(get(view, "lock"));
            boolean third = "third".equals(get(view, "mode"));
            if (lock) {
                int want = third ? HOLD_THIRD : HOLD_FIRST;
                fHeld.setInt(null, want);
                if (want != lastHeld) {
                    lastHeld = want;
                    log("hold -> " + (third ? "third" : "first") + " person");
                }
                wasLocked = true;
            } else if (wasLocked) {
                wasLocked = false;
                lastHeld = HOLD_NONE;
                fHeld.setInt(null, HOLD_NONE);
                log("hold released (lock off)");
            }

            boolean enabled = fEnabled.getBoolean(null);
            boolean nowThird = fThird.getBoolean(null);
            if (!jarSent) {
                jarSent = true;
                mRawset.invoke(view, "jar", Main.VERSION);
            }
            mRawset.invoke(view, "enabled", Boolean.valueOf(enabled));
            mRawset.invoke(view, "third", Boolean.valueOf(nowThird));
            mRawset.invoke(view, "at", Double.valueOf((double) System.currentTimeMillis()));

            String line = "enabled=" + enabled + " third=" + nowThird + " lock=" + lock;
            if (!line.equals(lastLine)) {
                lastLine = line;
                log("view: " + line);
            }
        } catch (Throwable t) {
            errors++;
            if (errors <= 5) log("frame failed (" + errors + "): " + t);
            if (errors >= 20) {
                broken = true;
                log("view lock off after " + errors + " errors");
            }
        }
    }
}
