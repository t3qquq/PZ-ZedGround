package t3zedballistics.ads;

public final class Sight {
    static final float MIN_PROGRESS = 0.85F;
    static final long MAX_AGE_NS = 150000000L;
    private static boolean valid;
    private static double ox;
    private static double oy;
    private static double ou;
    private static double dx;
    private static double dy;
    private static double du;
    private static float zero;
    private static float progress;
    private static long stamp;

    static synchronized void publish(double var0, double var2, double var4, double var6, double var8, double var10, float var12, float var13) {
        double var14 = Math.sqrt(var6 * var6 + var8 * var8 + var10 * var10);
        if (var14 > 1.0E-9 && Double.isFinite(var0) && Double.isFinite(var2) && Double.isFinite(var4)) {
            ox = var0;
            oy = var2;
            ou = var4;
            dx = var6 / var14;
            dy = var8 / var14;
            du = var10 / var14;
            zero = Float.isFinite(var12) ? Math.max(0.0F, var12) : 0.0F;
            progress = var13;
            stamp = System.nanoTime();
            valid = true;
        } else {
            valid = false;
        }
    }

    static synchronized void clear() {
        valid = false;
        progress = 0.0F;
    }

    public static synchronized boolean ray(double[] var0) {
        if (!valid || var0 == null || var0.length < 7) {
            return false;
        }

        if (progress < 0.85F) {
            return false;
        }

        if (System.nanoTime() - stamp > 150000000L) {
            return false;
        }

        var0[0] = ox;
        var0[1] = oy;
        var0[2] = ou;
        var0[3] = dx;
        var0[4] = dy;
        var0[5] = du;
        var0[6] = zero;
        return true;
    }

    public static float progress() {
        return ADS.eShared;
    }

    private Sight() {
    }
}
