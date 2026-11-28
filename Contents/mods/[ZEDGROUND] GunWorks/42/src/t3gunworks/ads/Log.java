package t3gunworks.ads;

import java.util.HashSet;

public final class Log {
    static volatile boolean dev = false;
    private static final HashSet<String> once = new HashSet<>();

    public static void info(String var0) {
        System.out.println("[GunWorks][ADS] " + var0);
    }

    static void dev(String var0) {
        if (dev) {
            System.out.println("[GunWorks][ADS][dev] " + var0);
        }
    }

    static void warn(String var0) {
        System.out.println("[GunWorks][ADS][warn] " + var0);
    }

    static void warnOnce(String var0, String var1) {
        synchronized (once) {
            if (!once.add(var0)) {
                return;
            }
        }

        warn(var1);
    }

    static void error(String var0, Throwable var1) {
        String var2 = "E:" + var0 + ":" + var1.getClass().getName() + ":" + firstFrame(var1);
        synchronized (once) {
            if (once.size() > 512 || !once.add(var2)) {
                return;
            }
        }

        System.out.println("[GunWorks][ADS][error] " + var0 + ": " + var1);
        if (dev) {
            var1.printStackTrace(System.out);
        }
    }

    private static String firstFrame(Throwable var0) {
        StackTraceElement[] var1 = var0.getStackTrace();
        return var1 != null && var1.length > 0 ? var1[0].getClassName() + "." + var1[0].getMethodName() + ":" + var1[0].getLineNumber() : "?";
    }

    private Log() {
    }
}
