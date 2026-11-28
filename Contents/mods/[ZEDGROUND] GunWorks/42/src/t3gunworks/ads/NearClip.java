package t3gunworks.ads;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.joml.Matrix4f;

public final class NearClip {
    static final float VP_NEAR = 0.05F;
    static final float FAR = 400.0F;
    static final float NEAR = 0.012F;
    private static Field frameScene;
    private static Field sceneProjection;
    private static Field ctxScene;
    private static Method progSet4;
    private static Method progHas;
    private static Method irisPut;
    private static Field irisProjection;
    private static volatile boolean broken;
    private static volatile boolean irisBroken;

    static boolean wanted() {
        return !broken && ADS.S.closeClip && ADS.eShared > 0.3F && VP.firstPerson();
    }

    public static void afterSnapshot(Object var0) {
        if (var0 != null && wanted()) {
            try {
                if (frameScene == null) {
                    frameScene = var0.getClass().getField("scene");
                }

                Object var1 = frameScene.get(var0);
                Matrix4f var2 = projection(var1);
                if (var2 == null) {
                    return;
                }

                float var3 = farOf(var2);
                var2.m22((var3 + 0.012F) / (0.012F - var3));
                var2.m32(2.0F * var3 * 0.012F / (0.012F - var3));
            } catch (Throwable var4) {
                fail("projection", var4);
            }
        }
    }

    public static void afterCamera(Object var0, Object var1) {
        resetCamera(var0, var1);
    }

    public static void afterPackUniforms(Object var0, Object var1) {
        resetCamera(var1, var0);
    }

    public static void afterIrisCamera(Object var0, Object var1) {
        if (!irisBroken && var0 != null && var1 != null) {
            try {
                float var2 = nearOf(var1);
                if (Math.abs(var2 - 0.05F) < 1.0E-4F) {
                    return;
                }

                if (irisPut == null) {
                    for (Method var6 : var0.getClass().getDeclaredMethods()) {
                        Class[] var7 = var6.getParameterTypes();
                        if (var6.getName().equals("put") && var7.length == 2 && var7[0] == String.class && var7[1] == float[].class) {
                            irisPut = var6;
                            break;
                        }
                    }

                    if (irisPut == null) {
                        throw new NoSuchMethodException("IrisUniforms.put(String,float...)");
                    }

                    irisPut.setAccessible(true);
                }

                irisPut.invoke(var0, "near", new float[]{var2});
            } catch (Throwable var8) {
                irisFail("iris near", var8);
            }
        }
    }

    public static void afterIrisMatrices(Object var0, Object var1) {
        if (!irisBroken && var0 != null && var1 != null) {
            try {
                float var2 = nearOf(var1);
                if (Math.abs(var2 - 0.05F) < 1.0E-4F) {
                    return;
                }

                if (irisProjection == null) {
                    irisProjection = var0.getClass().getDeclaredField("projection");
                    irisProjection.setAccessible(true);
                }

                Matrix4f var3 = (Matrix4f)irisProjection.get(var0);
                float var4 = var3.m22() + 1.0F;
                if (Math.abs(var4) < 1.0E-9F) {
                    return;
                }

                float var5 = var3.m32() / var4;
                var3.m22((var5 + var2) / (var2 - var5));
                var3.m32(2.0F * var5 * var2 / (var2 - var5));
            } catch (Throwable var6) {
                irisFail("iris projection", var6);
            }
        }
    }

    private static void irisFail(String var0, Throwable var1) {
        irisBroken = true;
        Log.error("near clip (" + var0 + "); shader-pack near plane left at Viewpoint's for this session", var1);
    }

    private static void resetCamera(Object var0, Object var1) {
        if (!broken && var0 != null && var1 != null) {
            try {
                Matrix4f var2 = projection(scene(var0));
                if (var2 == null) {
                    return;
                }

                float var3 = near(var2);
                if (Math.abs(var3 - 0.05F) < 1.0E-4F) {
                    return;
                }

                if (progSet4 == null) {
                    progSet4 = var1.getClass().getMethod("set", String.class, float.class, float.class, float.class, float.class);
                    progHas = var1.getClass().getMethod("has", String.class);
                }

                if (!(Boolean)progHas.invoke(var1, "uCamera")) {
                    return;
                }

                progSet4.invoke(var1, "uCamera", var3, farOf(var2), 1.0F / var2.m00(), 1.0F / var2.m11());
            } catch (Throwable var4) {
                fail("uCamera", var4);
            }
        }
    }

    static float farOf(Matrix4f var0) {
        float var1 = var0.m22() + 1.0F;
        if (!(Math.abs(var1) > 1.0E-9F)) {
            return 400.0F;
        }

        float var2 = var0.m32() / var1;
        return var2 > 1.0F && var2 < 1000000.0F && Float.isFinite(var2) ? var2 : 400.0F;
    }

    private static float nearOf(Object var0) throws Exception {
        Matrix4f var1 = projection(scene(var0));
        return var1 == null ? 0.05F : near(var1);
    }

    private static float near(Matrix4f var0) {
        float var1 = var0.m22() - 1.0F;
        return Math.abs(var1) < 1.0E-9F ? 0.05F : var0.m32() / var1;
    }

    private static Object scene(Object var0) throws Exception {
        if (ctxScene == null) {
            ctxScene = var0.getClass().getField("scene");
        }

        return ctxScene.get(var0);
    }

    private static Matrix4f projection(Object var0) throws Exception {
        if (var0 == null) {
            return null;
        }

        if (sceneProjection == null) {
            sceneProjection = var0.getClass().getField("projection");
        }

        return (Matrix4f)sceneProjection.get(var0);
    }

    private static void fail(String var0, Throwable var1) {
        broken = true;
        Log.error("near clip (" + var0 + "); close-clip disabled for this session", var1);
    }

    private NearClip() {
    }
}
