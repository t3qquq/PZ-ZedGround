package t3zedballistics;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Logging. Normal players see a handful of start-up lines; everything per-shot is behind the debug
 * switches in BallisticsConfig so it costs nothing in normal play. Per-frame failures are logged once
 * per key so a broken hook can never flood the console.
 */
public final class Log {
    private static final String TAG = "[ZedBallistics] ";
    private static final Set<String> once = ConcurrentHashMap.newKeySet();

    public static void info(String msg) { System.out.println(TAG + msg); }

    public static void warn(String msg) { System.out.println(TAG + "WARN " + msg); }

    public static void error(String msg, Throwable t) {
        System.err.println(TAG + "ERROR " + msg + (t != null ? ": " + t : ""));
        if (t != null) t.printStackTrace();
    }

    /** Logs the first occurrence of an error with this key, then stays silent for that key. */
    public static void errorOnce(String key, Throwable t) {
        if (once.add(key)) error(key + " (further errors of this kind are suppressed)", t);
    }

    public static void warnOnce(String key, String msg) {
        if (once.add(key)) warn(msg);
    }

    private Log() {}
}
