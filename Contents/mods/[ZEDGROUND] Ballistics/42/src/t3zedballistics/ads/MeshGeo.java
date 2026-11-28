package t3zedballistics.ads;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import org.joml.Vector3f;
import zombie.core.skinnedmodel.model.ModelMesh;
import zombie.core.skinnedmodel.model.VertexBufferObject.VertexArray;
import zombie.core.skinnedmodel.model.VertexBufferObject.VertexType;

public final class MeshGeo {
    private static final Map<Object, float[]> VERTS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, float[]> IRONS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final float[] NONE = new float[0];
    private static Field fStatic;
    private static Field fVerts;
    private static boolean broken;
    static volatile int captured;
    private static final Map<Object, float[]> IRONS_MERGED = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Object[]> MERGED_FOR = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Boolean> FLIP = Collections.synchronizedMap(new WeakHashMap<>());
    static volatile boolean overTheTopOn = true;
    static final float SHORT_GUN = 0.25F;

    public static void onLoadedX(Object var0, Object var1) {
        if (!broken && var0 instanceof ModelMesh && var1 != null) {
            try {
                ModelMesh var2 = (ModelMesh)var0;
                String var3 = var2.getPath() != null ? var2.getPath().getPath() : null;
                if (var3 == null || !var3.toLowerCase(Locale.ROOT).contains("weapon")) {
                    return;
                }

                if (fStatic == null) {
                    Field var4 = var1.getClass().getDeclaredField("staticMeshes");
                    var4.setAccessible(true);
                    fStatic = var4;
                }

                List var12 = (List)fStatic.get(var1);
                if (var12 == null || var12.isEmpty()) {
                    return;
                }

                Object var5 = var12.get(0);
                if (fVerts == null) {
                    Field var6 = var5.getClass().getDeclaredField("verticesUnskinned");
                    var6.setAccessible(true);
                    fVerts = var6;
                }

                VertexArray var13 = (VertexArray)fVerts.get(var5);
                if (var13 == null) {
                    return;
                }

                int var7 = var13.format.indexOf(VertexType.VertexArray);
                int var8 = var13.numVertices;
                if (var7 < 0 || var8 <= 0 || var8 > 250000) {
                    return;
                }

                float[] var9 = new float[var8 * 3];

                for (int var10 = 0; var10 < var8; var10++) {
                    var9[var10 * 3] = var13.getElementFloat(var10, var7, 0);
                    var9[var10 * 3 + 1] = var13.getElementFloat(var10, var7, 1);
                    var9[var10 * 3 + 2] = var13.getElementFloat(var10, var7, 2);
                }

                VERTS.put(var2, var9);
                IRONS.remove(var2);
                captured++;
                Log.dev("mesh captured: " + var3 + " (" + var8 + " vertices)");
            } catch (Throwable var11) {
                broken = true;
                Log.error("mesh capture (runtime iron sights disabled, offline profiles used)", var11);
            }
        }
    }

    static boolean captured(Object var0) {
        return var0 != null && VERTS.containsKey(var0);
    }

    static float[] verts(Object var0) {
        return var0 == null ? null : VERTS.get(var0);
    }

    static float[] tube(float[] var0) {
        int var1 = var0.length / 3;
        if (var1 < 12) {
            return null;
        }

        float var2 = Float.MAX_VALUE;
        float var3 = -Float.MAX_VALUE;

        for (int var4 = 0; var4 < var1; var4++) {
            float var5 = var0[var4 * 3 + 1];
            var2 = Math.min(var2, var5);
            var3 = Math.max(var3, var5);
        }

        float var13 = var3 - var2;
        if (var13 > 0.05F && !(var13 > 0.6F)) {
            float[] var14 = endRing(var0, var1, var2, var13, true);
            float[] var6 = endRing(var0, var1, var3, var13, false);
            if (var14 != null && var6 != null) {
                float var7 = 0.5F * Math.min(var14[3], var14[4]);
                if (var7 > 0.003F && !(var7 > 0.06F)) {
                    float var8 = var6[0] - var14[0];
                    float var9 = var6[1] - var14[1];
                    float var10 = var6[2] - var14[2];
                    float var11 = (float)Math.sqrt(var8 * var8 + var9 * var9 + var10 * var10);
                    if (var11 > 0.03F && !(var9 / var11 < 0.97F)) {
                        float[] var12 = axisFit(var0, var1, var2, var13, var14);
                        if (var12 != null) {
                            var8 = var12[0];
                            var9 = var12[1];
                            var10 = var12[2];
                        }

                        if (var9 < 0.999F) {
                            var8 = 0.0F;
                            var9 = 1.0F;
                            var10 = 0.0F;
                        }

                        return new float[]{var14[0], var2, var14[2], var8, var9, var10, var7, var13};
                    } else {
                        return null;
                    }
                } else {
                    return null;
                }
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private static float[] axisFit(float[] var0, int var1, float var2, float var3, float[] var4) {
        byte var5 = 8;
        float[] var6 = new float[var5];
        float[] var7 = new float[var5];
        float[] var8 = new float[var5];
        float[] var9 = new float[var5];
        boolean[] var10 = new boolean[var5];
        float var11 = Float.MAX_VALUE;

        for (int var12 = 0; var12 < var5; var12++) {
            float var13 = var2 + var3 * (0.12F + 0.76F * var12 / (var5 - 1.0F));
            float var14 = 0.03F * var3;
            float[] var15 = medianSlice(var0, var1, var13 - var14, var13 + var14);
            if (var15 != null) {
                var10[var12] = true;
                var6[var12] = var15[0];
                var7[var12] = var15[1];
                var8[var12] = var15[2];
                var9[var12] = var15[3];
                var11 = Math.min(var11, var15[3]);
            }
        }

        double var31 = 0.0;
        double var32 = 0.0;
        double var16 = 0.0;
        int var18 = 0;

        for (int var19 = 0; var19 < var5; var19++) {
            if (var10[var19] && var9[var19] <= 1.5F * var11) {
                var31 += var6[var19];
                var32 += var7[var19];
                var16 += var8[var19];
                var18++;
            } else {
                var10[var19] = false;
            }
        }

        if (var18 < 3) {
            return null;
        }

        float var33 = (float)(var31 / var18);
        float var20 = (float)(var32 / var18);
        float var21 = (float)(var16 / var18);
        double var22 = 0.0;
        double var24 = 0.0;
        double var26 = 0.0;

        for (int var28 = 0; var28 < var5; var28++) {
            if (var10[var28]) {
                double var29 = var7[var28] - var20;
                var22 += var29 * var29;
                var24 += (var6[var28] - var33) * var29;
                var26 += (var8[var28] - var21) * var29;
            }
        }

        if (!(var22 > 1.0E-8)) {
            return null;
        }

        float var34 = (float)(var24 / var22);
        float var35 = (float)(var26 / var22);
        float var30 = (float)Math.sqrt(var34 * var34 + 1.0F + var35 * var35);
        return new float[]{var34 / var30, 1.0F / var30, var35 / var30};
    }

    private static float[] medianSlice(float[] var0, int var1, float var2, float var3) {
        int var4 = 0;

        for (int var5 = 0; var5 < var1; var5++) {
            float var6 = var0[var5 * 3 + 1];
            if (var6 >= var2 && var6 <= var3) {
                var4++;
            }
        }

        if (var4 < 6) {
            return null;
        }

        float[] var12 = new float[var4];
        float[] var13 = new float[var4];
        double var7 = 0.0;
        int var9 = 0;

        for (int var10 = 0; var10 < var1; var10++) {
            float var11 = var0[var10 * 3 + 1];
            if (!(var11 < var2) && !(var11 > var3)) {
                var12[var9] = var0[var10 * 3];
                var13[var9] = var0[var10 * 3 + 2];
                var7 += var11;
                var9++;
            }
        }

        Arrays.sort(var12);
        Arrays.sort(var13);
        float var14 = Math.max(var12[var4 - 1] - var12[0], var13[var4 - 1] - var13[0]);
        return new float[]{var12[var4 / 2], (float)(var7 / var4), var13[var4 / 2], var14};
    }

    private static float[] endRing(float[] var0, int var1, float var2, float var3, boolean var4) {
        float[] var5 = new float[]{0.0015F, 0.02F * var3, 0.05F * var3, 0.15F * var3};

        for (float var9 : var5) {
            float[] var10 = var4 ? slice(var0, var1, var2 - 1.0F, var2 + var9) : slice(var0, var1, var2 - var9, var2 + 1.0F);
            if (var10 != null && var10[5] >= 6.0F && Math.min(var10[3], var10[4]) > 0.004F) {
                return var10;
            }
        }

        return null;
    }

    private static float[] slice(float[] var0, int var1, float var2, float var3) {
        float var4 = Float.MAX_VALUE;
        float var5 = -Float.MAX_VALUE;
        float var6 = Float.MAX_VALUE;
        float var7 = -Float.MAX_VALUE;
        double var8 = 0.0;
        int var10 = 0;

        for (int var11 = 0; var11 < var1; var11++) {
            float var12 = var0[var11 * 3 + 1];
            if (!(var12 < var2) && !(var12 > var3)) {
                float var13 = var0[var11 * 3];
                float var14 = var0[var11 * 3 + 2];
                var4 = Math.min(var4, var13);
                var5 = Math.max(var5, var13);
                var6 = Math.min(var6, var14);
                var7 = Math.max(var7, var14);
                var8 += var12;
                var10++;
            }
        }

        return var10 < 4 ? null : new float[]{0.5F * (var4 + var5), (float)(var8 / var10), 0.5F * (var6 + var7), var5 - var4, var7 - var6, var10};
    }

    static float[] irons(Object var0, Vector3f var1) {
        return irons(var0, var1, false);
    }

    static float[] irons(Object var0, Vector3f var1, boolean var2) {
        if (var0 == null) {
            return null;
        }

        float[] var3 = IRONS.get(var0);
        if (var3 != null) {
            return var3.length == 0 ? null : var3;
        }

        float[] var4 = VERTS.get(var0);
        if (var4 == null) {
            return null;
        }

        float[] var5 = scanFlipAware(var4, var1, Boolean.TRUE.equals(FLIP.get(var0)), var2);
        IRONS.put(var0, var5 == null ? NONE : var5);
        return var5;
    }

    static float[] ironsWith(Object var0, Object var1, float[] var2, Vector3f var3, boolean var4) {
        if (var0 != null && var1 != null && var2 != null) {
            boolean var5 = Boolean.TRUE.equals(FLIP.get(var0));
            Object[] var6 = MERGED_FOR.get(var1);
            float[] var7 = var6 != null && var6[0] == var0 && var6[1].equals(var5) ? IRONS_MERGED.get(var1) : null;
            if (var7 != null) {
                return var7.length == 0 ? null : var7;
            }

            float[] var8 = VERTS.get(var0);
            if (var8 == null) {
                return null;
            }

            float[] var9 = new float[var8.length + var2.length];
            System.arraycopy(var8, 0, var9, 0, var8.length);
            System.arraycopy(var2, 0, var9, var8.length, var2.length);
            float[] var10 = scanFlipAware(var9, var3, var5, var4);
            IRONS_MERGED.put(var1, var10 == null ? NONE : var10);
            MERGED_FOR.put(var1, new Object[]{var0, var5});
            return var10;
        } else {
            return irons(var0, var3, var4);
        }
    }

    private static float[] scanFlipAware(float[] var0, Vector3f var1, boolean var2, boolean var3) {
        float[] var4 = null;

        try {
            if (var2) {
                float[] var5 = new float[var0.length];

                for (byte var6 = 0; var6 < var0.length; var6 += 3) {
                    var5[var6] = -var0[var6];
                    var5[var6 + 1] = var0[var6 + 1];
                    var5[var6 + 2] = -var0[var6 + 2];
                }

                Vector3f var8 = var1 != null ? new Vector3f(-var1.x, var1.y, -var1.z) : null;
                var4 = scan(var5, var8, var3);
                if (var4 != null) {
                    var4[0] = -var4[0];
                    var4[2] = -var4[2];
                    var4[3] = -var4[3];
                    var4[5] = -var4[5];
                }
            } else {
                var4 = scan(var0, var1, var3);
            }
        } catch (Throwable var7) {
            Log.error("iron scan", var7);
        }

        return var4;
    }

    static float[] window(float[] var0, boolean var1) {
        int var2 = var0.length / 3;
        if (var2 < 8) {
            return null;
        }

        float var3 = Float.MAX_VALUE;
        float var4 = -Float.MAX_VALUE;
        float var5 = Float.MAX_VALUE;
        float var6 = -Float.MAX_VALUE;
        float var7 = Float.MAX_VALUE;
        float var8 = -Float.MAX_VALUE;

        for (int var9 = 0; var9 < var2; var9++) {
            float var10 = var0[var9 * 3];
            float var11 = var0[var9 * 3 + 1];
            float var12 = var0[var9 * 3 + 2];
            var3 = Math.min(var3, var10);
            var4 = Math.max(var4, var10);
            var5 = Math.min(var5, var11);
            var6 = Math.max(var6, var11);
            var7 = Math.min(var7, var12);
            var8 = Math.max(var8, var12);
        }

        float var13 = var8 - var7;
        float var14 = var4 - var3;
        if (var13 > 0.004F && var14 > 0.004F && !(var13 > 0.2F) && !(var14 > 0.2F)) {
            float var15 = var1 ? var8 - 0.67F * var13 : var7 + 0.67F * var13;
            return new float[]{0.5F * (var3 + var4), var5, var15, 0.4F * var14, 0.28F * var13, 0.5F * (var5 + var6)};
        } else {
            return null;
        }
    }

    static Boolean flipOf(Object var0) {
        return var0 == null ? null : FLIP.get(var0);
    }

    static void setFlip(Object var0, boolean var1) {
        if (var0 != null) {
            Boolean var2 = FLIP.put(var0, var1);
            if (var2 == null || var2 != var1) {
                IRONS.remove(var0);
            }
        }
    }

    static Boolean shapeFlipShort(float[] var0, Vector3f var1) {
        if (var0 != null && var0.length >= 24) {
            int var2 = var0.length / 3;
            float var3 = Float.MAX_VALUE;
            float var4 = -Float.MAX_VALUE;

            for (int var5 = 0; var5 < var2; var5++) {
                float var6 = var0[var5 * 3 + 1];
                var3 = Math.min(var3, var6);
                var4 = Math.max(var4, var6);
            }

            if (!(var4 - var3 > 0.35F) && var4 > 0.0F) {
                double var10 = 0.0;
                int var7 = 0;

                for (int var8 = 0; var8 < var2; var8++) {
                    if (var0[var8 * 3 + 1] >= var4 - 0.01F) {
                        var10 += var0[var8 * 3 + 2];
                        var7++;
                    }
                }

                if (var7 == 0) {
                    return null;
                }

                Boolean var11 = flipAt(var0, var2, var3, var4, (float)(var10 / var7), 2.5F);
                if (var11 == null) {
                    return null;
                }

                if (var1 != null && Math.abs(var1.y - var4) <= 0.06F) {
                    Boolean var9 = flipAt(var0, var2, var3, var4, var1.z, 2.5F);
                    if (!var11.equals(var9)) {
                        return null;
                    }
                }

                return var11;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private static Boolean flipAt(float[] var0, int var1, float var2, float var3, float var4, float var5) {
        float var6 = 0.0F;
        float var7 = 0.0F;
        float var8 = var2 + 0.6F * (var3 - var2);

        for (int var9 = 0; var9 < var1; var9++) {
            if (!(var0[var9 * 3 + 1] > var8)) {
                float var10 = var0[var9 * 3 + 2] - var4;
                if (var10 > var6) {
                    var6 = var10;
                }

                if (-var10 > var7) {
                    var7 = -var10;
                }
            }
        }

        if (var6 > var7 * var5) {
            return Boolean.TRUE;
        } else {
            return var7 > var6 * var5 ? Boolean.FALSE : null;
        }
    }

    static Boolean guessFlip(Object var0, Vector3f var1) {
        return guessFlipV(VERTS.get(var0), var1);
    }

    static Boolean guessFlipV(float[] var0, Vector3f var1) {
        if (var0 == null) {
            return null;
        }

        int var2 = var0.length / 3;
        float var3 = Float.MAX_VALUE;
        float var4 = -Float.MAX_VALUE;

        for (int var5 = 0; var5 < var2; var5++) {
            float var6 = var0[var5 * 3 + 1];
            var3 = Math.min(var3, var6);
            var4 = Math.max(var4, var6);
        }

        float var11 = var1 != null ? var1.z : 0.0F;
        float var12 = 0.0F;
        float var7 = 0.0F;
        float var8 = var3 + 0.6F * (var4 - var3);

        for (int var9 = 0; var9 < var2; var9++) {
            if (!(var0[var9 * 3 + 1] > var8)) {
                float var10 = var0[var9 * 3 + 2] - var11;
                if (var10 > var12) {
                    var12 = var10;
                }

                if (-var10 > var7) {
                    var7 = -var10;
                }
            }
        }

        if (var12 > var7 * 1.5F) {
            return Boolean.TRUE;
        } else {
            return var7 > var12 * 1.5F ? Boolean.FALSE : null;
        }
    }

    static float[] scan(float[] var0, Vector3f var1) {
        return scan(var0, var1, false);
    }

    static float[] scan(float[] var0, Vector3f var1, boolean var2) {
        int var3 = var0.length / 3;
        if (var3 < 8) {
            return null;
        }

        float var4 = Float.MAX_VALUE;
        float var5 = -Float.MAX_VALUE;

        for (int var6 = 0; var6 < var3; var6++) {
            float var7 = var0[var6 * 3 + 1];
            if (var7 < var4) {
                var4 = var7;
            }

            if (var7 > var5) {
                var5 = var7;
            }
        }

        if (var5 > 0.0F && !(var5 < 1.5F * Math.abs(var4))) {
            float var8;
            float var23;
            float var24;
            if (var1 != null && Math.abs(var1.y - var5) <= 0.06F) {
                var23 = var1.x;
                var24 = var1.y;
                var8 = var1.z;
                int var25 = 0;
                double var27 = 0.0;

                for (int var30 = 0; var30 < var3; var30++) {
                    if (var0[var30 * 3 + 1] >= var5 - 0.01F) {
                        var27 += var0[var30 * 3];
                        var25++;
                    }
                }

                if (var25 > 0 && Math.abs(var27 / var25 - var23) > 0.003F && var5 - var4 < 0.25F) {
                    var23 = (float)(var27 / var25);
                }
            } else {
                var24 = var5;
                int var9 = 0;
                double var10 = 0.0;

                for (int var12 = 0; var12 < var3; var12++) {
                    if (var0[var12 * 3 + 1] >= var24 - 0.01F) {
                        var10 += var0[var12 * 3];
                        var9++;
                    }
                }

                if (var9 == 0) {
                    return null;
                }

                float[] var29 = new float[var9];
                int var13 = 0;

                for (int var14 = 0; var14 < var3; var14++) {
                    if (var0[var14 * 3 + 1] >= var24 - 0.01F) {
                        var29[var13++] = var0[var14 * 3 + 2];
                    }
                }

                Arrays.sort(var29);
                var23 = (float)(var10 / var9);
                var8 = var29[var9 / 2];
            }

            float var26 = var24 - var4;
            if (!(var26 > 0.05F)) {
                return null;
            }

            float var28 = 0.006F;
            if (!anyNear(var0, var3, var23, var28)) {
                var28 = 0.012F;
            }

            int var11 = -1;

            for (int var31 = 0; var31 < var3; var31++) {
                float var33 = var0[var31 * 3];
                float var35 = var0[var31 * 3 + 1];
                float var15 = var0[var31 * 3 + 2];
                if (!(Math.abs(var33 - var23) > var28)
                    && !(var35 < var4 + 0.58F * var26)
                    && !(var35 > var24 - 0.004F)
                    && (var11 < 0 || var15 > var0[var11 * 3 + 2])) {
                    var11 = var31;
                }
            }

            if (var11 < 0) {
                return null;
            }

            float var32 = var0[var11 * 3 + 1];
            float var34 = var0[var11 * 3 + 2];
            float var36 = var34 - var8;
            if (!(var36 < 0.003F) && !(var36 > 0.12F)) {
                int var37 = -1;
                boolean var16 = false;

                for (int var17 = 0; var17 < var3; var17++) {
                    float var18 = var0[var17 * 3];
                    float var19 = var0[var17 * 3 + 1];
                    float var20 = var0[var17 * 3 + 2];
                    if (!(Math.abs(var18 - var23) > var28) && !(var19 < var4 + 0.15F * var26) && !(var19 > var4 + 0.6F * var26)) {
                        float var21 = Math.abs(var20 - var34);
                        if (!(var21 > 0.006F)) {
                            boolean var22 = var21 <= 0.003F;
                            if (var37 < 0 || var22 && (!var16 || var19 > var0[var37 * 3 + 1])) {
                                var37 = var17;
                                var16 = var22;
                            }
                        }
                    }
                }

                float[] var38;
                if (var37 >= 0 && var32 > var0[var37 * 3 + 1] + 0.02F) {
                    float var39 = var0[var37 * 3 + 1];
                    float var41 = var0[var37 * 3 + 2];
                    float var42 = var32 - var39;
                    float var43 = var34 - var41;
                    float var44 = (float)Math.sqrt(var42 * var42 + var43 * var43);
                    var38 = new float[]{var23, var39, var41, 0.0F, var42 / var44, var43 / var44, 2.0F, var36, var44};
                } else {
                    var38 = new float[]{var23, var32 - 0.45F * var26, var34, 0.0F, 1.0F, 0.0F, 1.0F, var36, 0.45F * var26};
                }

                float[] var40 = overTheTopOn && var26 < 0.25F
                    ? overTheTop(var0, var3, var23, var28, var4, var32, var34, var38, Float.NaN)
                    : (var2 ? overTheTop(var0, var3, var23, var28, var4, var32, var34, var38, var4 + 0.3F * var26) : null);
                return var40 != null ? var40 : var38;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    static float[] overTheTop(float[] var0, int var1, float var2, float var3, float var4, float var5, float var6, float[] var7) {
        return overTheTop(var0, var1, var2, var3, var4, var5, var6, var7, Float.NaN);
    }

    static float[] overTheTop(float[] var0, int var1, float var2, float var3, float var4, float var5, float var6, float[] var7, float var8) {
        float var9 = var7[1];
        float var10 = var7[2];
        float var11 = var7[4];
        float var12 = var7[5];
        float var13 = !Float.isNaN(var8) ? var8 : Math.max(var4, var7[6] == 2.0F ? var9 - 0.06F : var5 - 0.2F);
        boolean var14 = false;

        for (int var15 = 0; var15 < var1 && !var14; var15++) {
            float var16 = var0[var15 * 3];
            float var17 = var0[var15 * 3 + 1];
            float var18 = var0[var15 * 3 + 2];
            if (!(Math.abs(var16 - var2) > var3) && !(var17 < var13) && !(var17 > var5 - 0.004F)) {
                float var19 = var10 + (var17 - var9) * (var12 / var11);
                if (var18 > var19 + 5.0E-4F) {
                    var14 = true;
                }
            }
        }

        if (!var14) {
            return null;
        }

        int var22 = -1;
        float var23 = -Float.MAX_VALUE;

        for (int var24 = 0; var24 < var1; var24++) {
            float var26 = var0[var24 * 3];
            float var28 = var0[var24 * 3 + 1];
            float var20 = var0[var24 * 3 + 2];
            if (!(Math.abs(var26 - var2) > var3) && !(var28 < var13) && !(var28 > var5 - 0.02F)) {
                float var21 = (var20 - var6) / (var5 - var28);
                if (var21 > var23) {
                    var23 = var21;
                    var22 = var24;
                }
            }
        }

        if (var22 >= 0 && !(Math.abs(var23) > 0.07F)) {
            float var25 = var0[var22 * 3 + 1];
            float var27 = var0[var22 * 3 + 2];
            float var29 = var5 - var25;
            float var30 = var6 - var27;
            float var31 = (float)Math.sqrt(var29 * var29 + var30 * var30);
            return new float[]{var2, var25, var27, 0.0F, var29 / var31, var30 / var31, 2.0F, var7[7], var31};
        } else {
            return null;
        }
    }

    private static boolean anyNear(float[] var0, int var1, float var2, float var3) {
        for (int var4 = 0; var4 < var1; var4++) {
            if (Math.abs(var0[var4 * 3] - var2) <= var3) {
                return true;
            }
        }

        return false;
    }

    private MeshGeo() {
    }
}
