package t3gunworks.ads;

public final class EyeTap {
    static volatile boolean valid;
    static volatile float offX;
    static volatile float offZ;
    static volatile float height;
    static volatile float lean;

    public static void updated(Object var0) {
        if (var0 != null && VP.emOffX != null) {
            try {
                offX = VP.emOffX.getFloat(var0);
                offZ = VP.emOffZ.getFloat(var0);
                height = VP.emHeight.getFloat(var0);
                lean = VP.emLean.getFloat(var0);
                valid = height > 0.2F && Float.isFinite(offX);
            } catch (Throwable var2) {
                valid = false;
            }
        }
    }

    private EyeTap() {
    }
}
