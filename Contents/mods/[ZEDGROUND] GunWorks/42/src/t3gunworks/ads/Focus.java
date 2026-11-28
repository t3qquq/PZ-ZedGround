package t3gunworks.ads;

import java.lang.reflect.Field;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public final class Focus {
    private static final float[] FAR = new float[]{400.0F, 0.0F, 0.0F, 1.0F};
    private static Field target;
    private static boolean failed;

    public static void afterDraw(Object var0) {
        if (!failed && var0 != null && ADS.sharpFocus()) {
            try {
                if (target == null) {
                    target = var0.getClass().getField("target");
                }

                if (!"focus".equals(target.get(var0))) {
                    return;
                }

                boolean var1 = GL11.glIsEnabled(3089);
                if (var1) {
                    GL11.glDisable(3089);
                }

                GL30.glClearBufferfv(6144, 0, FAR);
                if (var1) {
                    GL11.glEnable(3089);
                }
            } catch (Throwable var2) {
                failed = true;
                Log.error("ADS depth-of-field focus override (disabled for this session)", var2);
            }
        }
    }

    private Focus() {
    }
}
