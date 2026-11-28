package t3gunworks.ads;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import zombie.core.Core;
import zombie.core.textures.Texture;

public final class Pip {
    static final float PIP_NEAR = 1.0F;
    static final float FAR = 400.0F;
    static final float REFLEX_GLASS = 0.03F;
    static volatile boolean broken;
    static volatile boolean drewLastFrame;
    static volatile float lensCx;
    static volatile float lensCy;
    static volatile float lensR;
    private static Field fWrFrame;
    private static Field fWrTemporal;
    private static Field fWrIsolate;
    private static Field fOutDraw;
    private static Field fOutRead;
    private static Field fViewport;
    private static Field fHistIdx;
    private static Field fHistValid;
    private static Field fLastNanos;
    private static Field fPrevX;
    private static Field fPrevY;
    private static Field fPrevZ;
    private static Field fPrevVP;
    private static Field fSdEye;
    private static Field fSdFrame;
    private static Field fSdView;
    private static Field fFrameScene;
    private static Field fSceneProj;
    private static Method mBegin;
    private static Method mOutlines;
    private static Method mTranslucent;
    private static Method mIndirect;
    private static Method mWeather;
    private static Method mFinish;
    private static int fbo;
    private static int colorTex;
    private static int depthRb;
    private static int texW;
    private static int texH;
    private static boolean keepBigger;
    private static boolean passOneToOne = true;
    private static int prog;
    private static int vao;
    private static int uTex;
    private static int uCentre;
    private static int uRadius;
    private static int uSize;
    private static int uAxis;
    private static int uPic;
    private static int uRetRoll;
    private static volatile float picSX = 1.0F;
    private static volatile float picSY = 1.0F;
    private static int uRetMode;
    private static int uRet;
    private static int uRetUV;
    private static int uHasGlow;
    private static int uGlow;
    private static int uGlowUV;
    private static int uRetR;
    private static int uRetCol;
    private static int uReflex;
    private static int uWinHalf;
    private static int uRetPos;
    private static int uMain;
    private static int uKeyed;
    private static int uKeyUV;
    private static int uPx;
    private static int uRound;
    private static int uFade;
    private static int uRetOff;
    private static int uCorner;
    private static int uPolyN;
    private static int uPoly;
    private static float retOffX;
    private static float retOffY;
    private static int copyFbo;
    private static int copyTex;
    private static int copyW;
    private static int copyH;
    private static boolean copyBroken;
    private static int measureTick;
    private static ByteBuffer rbMain;
    private static ByteBuffer rbClean;
    private static final Map<String, float[]> CORR = new ConcurrentHashMap<>();
    static volatile String reflexKey = "";
    static volatile float wantCx;
    static volatile float wantCy;
    static volatile float wantR;
    static volatile float wantAx;
    static volatile float wantAy;
    private static Field fSdFailed;
    static volatile float[] geom;
    private static final Map<Object, float[]> FRAME_GEOM = Collections.synchronizedMap(new WeakHashMap<>());
    private static final float[] NO_GEOM = new float[0];
    static volatile float[] meshEye;
    static volatile float[] meshPoly;
    static final int POLY_MAX = 24;
    private static final float[] polyPx = new float[48];
    private static int polyN;
    private static boolean dmValid;
    static volatile boolean reflexMode;
    static volatile boolean reflexRound;
    static final float CORNER_DEFAULT = 0.45F;
    static volatile boolean reflexExact;
    static volatile float reflexDepth;
    static final boolean GLASS_SEARCH_ON = false;
    static volatile Object meshInst;
    static volatile Object meshObj;
    private static Field fScModels;
    private static Field fMdMeshes;
    private static Field fMdInst;
    private static Field fMdValues;
    private static Field fMdCount;
    private static boolean drawnBroken;
    static volatile int drawnHits;
    static volatile int drawnMisses;
    private static final Matrix4f DM = new Matrix4f();
    private static final Vector3f DV = new Vector3f();
    private static final Vector3f DD = new Vector3f();
    private static final Vector3f DR = new Vector3f();
    private static int mdStride = 60;
    static final float PICK_MAX = 0.6F;
    private static String pickLast = "";
    static volatile int frameMisses;
    private static long lastDiag;
    static volatile Texture retTex;
    static volatile Texture glowTex;
    static volatile int retMode;
    static volatile float retR;
    static volatile float retG;
    static volatile float retB;
    static volatile float retBright = 1.0F;
    static volatile float dotPx = 10.0F;
    private static final Matrix4f VPM = new Matrix4f();
    private static final Vector4f V4 = new Vector4f();
    static volatile int quality = 1;
    private static int frameNo;
    private static int lastW;
    private static int lastH;
    private static boolean lastPassOk;
    private static boolean lastReflex;
    static volatile boolean inPass;
    private static int passQ;
    private static int[] passSc;
    private static int passW;
    private static int passH;
    private static boolean scissorBroken;
    private static boolean scissorSet;
    private static Field fFcWidth;
    private static Field fFcHeight;
    private static long costNanos;
    private static long costLogAt;
    private static int costFrames;
    private static boolean costLogged;
    private static Field fWrTargets;
    private static Field fTgW;
    private static Field fTgH;
    private static Field fFcPipe;
    private static Field fPlUpscaler;
    private static Field fPlScale;
    private static Field fPlTaa;
    private static boolean sizeBroken;
    private static boolean sizeLogged;
    private static boolean scaleWarned;
    private static boolean taaLogged;
    static final float GLASS_SEARCH = 2.2F;
    private static long lastCorrLog;

    static float corrFor(String var0) {
        float[] var1 = var0 != null ? CORR.get(var0) : null;
        return var1 == null ? 0.0F : var1[0];
    }

    static boolean wanted() {
        return !broken && ADS.pipActive();
    }

    public static void afterDrawFrame(Object var0) {
        drewLastFrame = false;
        if (wanted() && var0 != null) {
            try {
                bind(var0);
                if (fSdFailed.getBoolean(null)) {
                    return;
                }

                Object var1 = fWrFrame.get(null);
                int[] var2 = (int[])fViewport.get(var1);
                int var3 = var2[2];
                int var4 = var2[3];
                if (var3 <= 0 || var4 <= 0) {
                    return;
                }

                Object var5 = fSdFrame.get(var0);
                Object var6 = fFrameScene.get(var5);
                Matrix4f var7 = (Matrix4f)fSceneProj.get(var6);
                Matrix4f var8 = (Matrix4f)fSdView.get(null);
                float[] var9 = (float[])fSdEye.get(var0);
                boolean var10 = reflexMode;
                float var11 = 1.0F;
                if (var10) {
                    float[] var12 = fromDrawnModel(var6, var9);
                    if (var12 == null) {
                        return;
                    }

                    float var13 = var12[0] - var9[0];
                    float var14 = var12[1] - var9[1];
                    float var15 = var12[2] - var9[2];
                    var11 = Math.max(
                        0.05F, Math.min(1.0F, (float)Math.sqrt(var13 * var13 + var14 * var14 + var15 * var15) + reflexDepth * 1.5F + 0.03F + ADS.P.pipDepth)
                    );
                }

                float var48 = var10 ? 1.0F : ADS.pipZoom() * ADS.P.pipZoom;
                if (!(var48 > 1.0F)) {
                    var48 = 1.0F;
                }

                ensureTargets(var3, var4);
                if (fbo == 0) {
                    return;
                }

                int var49 = fOutDraw.getInt(var1);
                int var50 = fOutRead.getInt(var1);
                int var51 = quality;
                frameNo++;
                if (var51 == 0 && (frameNo & 1) == 1 && lastPassOk && lastW == var3 && lastH == var4 && lastReflex == var10) {
                    composite(var2[0], var2[1], var3, var4, var48, var7, var8, var9, var5, var6, var49, var50);
                    drewLastFrame = true;
                    return;
                }

                Object var16 = IrisPip.pipeline();
                boolean var17 = var16 != null;
                int[] var18 = !var17 && !var10 ? lensScissor(var6, var9, var7, var8, var3, var4, var48, var10) : null;
                int[] var19 = var17 ? null : passSize(var1, var3, var4);
                int var20 = var19 != null ? var19[0] : var3;
                int var21 = var19 != null ? var19[1] : var4;
                if (var20 > texW || var21 > texH) {
                    keepBigger = true;
                    ensureTargets(Math.max(var3, var20), Math.max(var4, var21));
                    if (fbo == 0) {
                        return;
                    }
                }

                keepBigger = var20 > var3 || var21 > var4;
                boolean var22 = var19 != null && var19.length > 2 && var19[2] == 1;
                int var23 = var2[0];
                int var24 = var2[1];
                Object var25 = fWrTemporal.get(null);
                int var26 = fHistIdx.getInt(var25);
                boolean var27 = fHistValid.getBoolean(var25);
                long var28 = fLastNanos.getLong(var25);
                double var30 = fPrevX.getDouble(var25);
                double var32 = fPrevY.getDouble(var25);
                double var34 = fPrevZ.getDouble(var25);
                Matrix4f var36 = new Matrix4f((Matrix4f)fPrevVP.get(var25));
                Matrix4f var37 = new Matrix4f(var7);
                int var38 = fWrIsolate.getInt(null);
                boolean var39 = false;
                boolean var40 = false;
                long var41 = 0L;

                try {
                    if (var17 && !IrisPip.broken) {
                        var40 = true;
                        IrisPip.enter(var16);
                    }

                    var7.m00(var37.m00() * var48);
                    var7.m11(var37.m11() * var48);
                    var7.m22((400.0F + var11) / (var11 - 400.0F));
                    var7.m32(800.0F * var11 / (var11 - 400.0F));
                    if (var38 == 0 && !var22) {
                        fWrIsolate.setInt(null, 1);
                    }

                    if (var22) {
                        fHistValid.setBoolean(var25, false);
                    }

                    fLastNanos.setLong(var25, 0L);
                    GL30.glBindFramebuffer(36009, fbo);
                    GL30.glBindFramebuffer(36008, fbo);
                    GL11.glDisable(3089);
                    GL11.glViewport(0, 0, var20, var21);
                    if (var18 != null) {
                        GL11.glScissor(var18[0], var18[1], var18[2], var18[3]);
                        GL11.glEnable(3089);
                    }

                    passQ = var51;
                    passSc = var18;
                    passW = var3;
                    passH = var4;
                    var41 = System.nanoTime();
                    inPass = true;
                    if ((Boolean)mBegin.invoke(null, var6, var8, var9[0], var9[1], var9[2])) {
                        if (var51 >= 2 || var17) {
                            mOutlines.invoke(null);
                        }

                        mTranslucent.invoke(null, var6);
                        if (mIndirect != null) {
                            mIndirect.invoke(null, var6);
                        }

                        if (var51 >= 2 || var17) {
                            mWeather.invoke(null, var6, var9[0], var9[1], var9[2]);
                        }

                        mFinish.invoke(null, var6);
                        var39 = true;
                    }
                } finally {
                    inPass = false;
                    scissorOff();
                    if (var41 != 0L) {
                        cost(System.nanoTime() - var41);
                    }

                    if (var40) {
                        IrisPip.exit();
                    }

                    GL11.glDisable(3089);
                    var7.set(var37);
                    fWrIsolate.setInt(null, var38);
                    fHistIdx.setInt(var25, var26);
                    fHistValid.setBoolean(var25, var27);
                    fLastNanos.setLong(var25, var28);
                    fPrevX.setDouble(var25, var30);
                    fPrevY.setDouble(var25, var32);
                    fPrevZ.setDouble(var25, var34);
                    ((Matrix4f)fPrevVP.get(var25)).set(var36);
                    fOutDraw.setInt(var1, var49);
                    fOutRead.setInt(var1, var50);
                    var2[0] = var23;
                    var2[1] = var24;
                    var2[2] = var3;
                    var2[3] = var4;
                    GL30.glBindFramebuffer(36009, var49);
                    GL30.glBindFramebuffer(36008, var50);
                    GL11.glViewport(var23, var24, var3, var4);
                }

                lastPassOk = var39;
                lastW = var3;
                lastH = var4;
                lastReflex = var10;
                if (var39) {
                    picSX = (float)var20 / texW;
                    picSY = (float)var21 / texH;
                    passOneToOne = var20 == var3 && var21 == var4;
                }

                if (!var39) {
                    return;
                }

                composite(var23, var24, var3, var4, var48, var7, var8, var9, var5, var6, var49, var50);
                drewLastFrame = true;
            } catch (Throwable var47) {
                broken = true;
                Log.error("picture-in-picture scope (switched off for this session; normal scopes still work)", var47);
            }
        }
    }

    private static void pickNote(String var0) {
        if (!var0.equals(pickLast)) {
            pickLast = var0;
            Log.info(var0);
        }
    }

    private static float[] fromDrawnModel(Object var0, float[] var1) {
        float[] var2 = meshEye;
        Object var3 = meshInst;
        Object var4 = meshObj;
        if (var2 != null && var0 != null && !drawnBroken) {
            try {
                if (fScModels == null) {
                    fScModels = acc(var0.getClass().getField("models"));
                    Class var5 = VP.cls("viewpoint.render.ModelDraws");
                    fMdMeshes = acc(var5.getDeclaredField("meshes"));
                    fMdInst = acc(var5.getDeclaredField("instances"));
                    fMdValues = acc(var5.getDeclaredField("values"));
                    fMdCount = acc(var5.getDeclaredField("count"));

                    try {
                        mdStride = acc(var5.getDeclaredField("VALUES")).getInt(null);
                    } catch (Throwable var24) {
                        mdStride = 60;
                    }

                    if (mdStride < 16) {
                        mdStride = 60;
                    }

                    Log.info("PiP: Viewpoint model records are " + mdStride + " floats");
                }

                Object var26 = fScModels.get(var0);
                if (var26 == null) {
                    return null;
                }

                int var6 = fMdCount.getInt(var26);
                Object[] var7 = (Object[])fMdMeshes.get(var26);
                Object[] var8 = (Object[])fMdInst.get(var26);
                float[] var9 = (float[])fMdValues.get(var26);
                int var10 = mdStride;
                float[] var11 = geom;
                boolean var12 = var1 != null && var11 != null && var11.length >= 3;
                float var13 = var12 ? var1[0] + var11[0] : 0.0F;
                float var14 = var12 ? var1[1] + var11[1] : 0.0F;
                float var15 = var12 ? var1[2] + var11[2] : 0.0F;
                int var16 = -1;
                int var17 = 0;
                float var18 = Float.MAX_VALUE;
                boolean var19 = false;

                for (int var20 = 0; var20 < var6 && var20 < var8.length && var20 < var7.length; var20++) {
                    boolean var21 = var3 != null && var8[var20] == var3;
                    boolean var22 = var4 != null && var7[var20] == var4;
                    if ((var21 || var22) && var9.length >= var20 * var10 + 16) {
                        var17++;
                        float var23 = 0.0F;
                        if (var12) {
                            DM.set(var9, var20 * var10);
                            DM.transformPosition(DV.set(var2[0], var2[1], var2[2]));
                            var23 = DV.distance(var13, var14, var15);
                            if (!Float.isFinite(var23)) {
                                continue;
                            }
                        }

                        if (var21 && !var12) {
                            var16 = var20;
                            var19 = true;
                            break;
                        }

                        if (var23 < var18 || var21 && !var19 && var23 < 0.6F) {
                            var18 = var23;
                            var16 = var20;
                            var19 = var21;
                            if (var21) {
                                break;
                            }
                        }
                    }
                }

                if (var16 >= 0 && var12 && var18 > 0.6F) {
                    drawnMisses++;
                    pickNote(
                        "PiP: the nearest drawn gun with this mesh is "
                            + String.format(Locale.ROOT, "%.2f", var18 * 1.5F)
                            + " m from the posed one ("
                            + var17
                            + " drawn): not ours, lens on the posed gun"
                    );
                    return null;
                } else if (var16 < 0) {
                    drawnMisses++;
                    pickNote("PiP: gun not among the drawn models (" + var6 + " drawn): lens on the posed gun");
                    return null;
                } else {
                    pickNote(
                        "PiP: lens on the drawn gun found by "
                            + (var19 ? "instance" : "mesh")
                            + (var17 > 1 ? " (" + var17 + " drawn with this mesh, nearest " + String.format(Locale.ROOT, "%.2f", var18 * 1.5F) + " m)" : "")
                    );
                    DM.set(var9, var16 * var10);
                    dmValid = true;
                    DM.transformPosition(DV.set(var2[0], var2[1], var2[2]));
                    DM.transformDirection(DD.set(var2[3], var2[4], var2[5])).normalize();
                    float var27 = DM.transformDirection(DR.set(var2[6], 0.0F, 0.0F)).length();
                    float var28 = var2.length > 7 ? DM.transformDirection(DR.set(0.0F, 0.0F, var2[7])).length() : var27;
                    drawnHits++;
                    return new float[]{DV.x, DV.y, DV.z, DD.x, DD.y, DD.z, var27, var28};
                }
            } catch (Throwable var25) {
                drawnBroken = true;
                Log.error("PiP: reading the drawn gun (lens falls back to the posed position)", var25);
                return null;
            }
        } else {
            return null;
        }
    }

    public static void snapshotted(Object var0) {
        if (var0 != null) {
            float[] var1 = geom;
            FRAME_GEOM.put(var0, var1 != null ? var1 : NO_GEOM);
        }
    }

    private static void composite(
        int var0, int var1, int var2, int var3, float var4, Matrix4f var5, Matrix4f var6, float[] var7, Object var8, Object var9, int var10, int var11
    ) {
        if (prog == 0) {
            buildProgram();
        }

        int var12 = Math.max(1, Core.getInstance().getScreenWidth());
        int var13 = Math.max(1, Core.getInstance().getScreenHeight());
        float var14 = (float)var2 / var12;
        float var15 = (float)var3 / var13;
        float var21 = 0.0F;
        float var22 = 0.0F;
        float var23 = 0.0F;
        dmValid = false;
        polyN = 0;
        float[] var24 = fromDrawnModel(var9, var7);
        float[] var25 = var8 != null ? FRAME_GEOM.get(var8) : null;
        boolean var26 = var25 != null;
        if (var25 == null) {
            frameMisses++;
            var25 = geom;
        }

        if (var25 != null && var25.length == 0) {
            var25 = null;
        }

        if (var24 != null) {
            var25 = new float[]{var24[0] - var7[0], var24[1] - var7[1], var24[2] - var7[2], var24[3], var24[4], var24[5], var24[6], var24[7]};
        }

        boolean var27 = reflexMode;
        if (!var27 || var24 != null) {
            float var16;
            float var17;
            float var18;
            float var19;
            float var20;
            if (var25 != null) {
                VPM.set(var5).mul(var6);
                float var28 = ADS.P.pipZ;
                V4.set(var7[0] + var25[0] + var25[3] * var28, var7[1] + var25[1] + var25[4] * var28, var7[2] + var25[2] + var25[5] * var28, 1.0F);
                VPM.transform(V4);
                if (!(V4.w > 1.0E-4F)) {
                    return;
                }

                var16 = var0 + (V4.x / V4.w * 0.5F + 0.5F) * var2;
                var17 = var1 + (V4.y / V4.w * 0.5F + 0.5F) * var3;
                var18 = var25[6] / V4.w * var5.m11() * 0.5F * var3;
                var21 = (var25.length > 7 ? var25[7] : var25[6]) / V4.w * var5.m11() * 0.5F * var3;
                V4.set(var25[3], var25[4], var25[5], 0.0F);
                VPM.transform(V4);
                if (V4.w > 1.0E-4F) {
                    var19 = V4.x / V4.w * 0.5F * var2 * var4;
                    var20 = V4.y / V4.w * 0.5F * var3 * var4;
                    var22 = var0 + (V4.x / V4.w * 0.5F + 0.5F) * var2;
                    var23 = var1 + (V4.y / V4.w * 0.5F + 0.5F) * var3;
                } else {
                    var19 = 0.0F;
                    var20 = 0.0F;
                    var22 = var16;
                    var23 = var17;
                }

                if (var27) {
                    var19 = var16 - var0 - var2 * 0.5F;
                    var20 = var17 - var1 - var3 * 0.5F;
                }
            } else {
                var16 = var0 + wantCx * var14;
                var17 = var1 + (var3 - wantCy * var15);
                var18 = wantR * var15;
                float var49 = ADS.P.pipZ;
                if (var49 != 0.0F) {
                    var18 *= 0.3F / Math.max(0.05F, 0.3F + var49);
                }

                var19 = wantAx * var14 * var4;
                var20 = -wantAy * var15 * var4;
            }

            ADS.Profile var50 = ADS.P;
            float var29 = ADS.pip(0);
            float var30 = var29 > 0.05F ? var29 : 1.0F;
            var18 *= var30;
            var21 *= var30;
            if (var27 && !reflexRound) {
                float var31 = ADS.pip(4);
                if (var31 > 0.3F && var31 < 3.0F) {
                    var21 *= var31;
                }
            }

            float var56 = ADS.pip(1);
            float var32 = ADS.pip(2);
            var16 += var56 * var18;
            var17 += var32 * var18;
            retOffX = var50.pipRetX * var18;
            retOffY = var50.pipRetY * var18;
            if (var18 > 2.0F && !(var18 > 2.0F * var3)) {
                float[] var51 = meshPoly;
                if (var27 && var51 != null && dmValid && var51.length >= 9) {
                    int var52 = Math.min(24, var51.length / 3);
                    VPM.set(var5).mul(var6);
                    boolean var54 = true;
                    double var57 = 0.0;
                    double var33 = 0.0;

                    for (int var35 = 0; var35 < var52; var35++) {
                        DM.transformPosition(DV.set(var51[var35 * 3], var51[var35 * 3 + 1], var51[var35 * 3 + 2]));
                        V4.set(DV.x, DV.y, DV.z, 1.0F);
                        VPM.transform(V4);
                        if (!(V4.w > 1.0E-4F)) {
                            var54 = false;
                            break;
                        }

                        polyPx[var35 * 2] = var0 + (V4.x / V4.w * 0.5F + 0.5F) * var2;
                        polyPx[var35 * 2 + 1] = var1 + (V4.y / V4.w * 0.5F + 0.5F) * var3;
                        var57 += polyPx[var35 * 2];
                        var33 += polyPx[var35 * 2 + 1];
                    }

                    if (var54) {
                        double var58 = var57 / var52;
                        var33 /= var52;
                        float var65 = ADS.pip(0);
                        float var36 = var65 > 0.05F ? var65 : 1.0F;
                        float var37 = ADS.pip(1) * var18;
                        float var38 = ADS.pip(2) * var18;

                        for (int var39 = 0; var39 < var52; var39++) {
                            polyPx[var39 * 2] = (float)var58 + (polyPx[var39 * 2] - (float)var58) * var36 + var37;
                            polyPx[var39 * 2 + 1] = (float)var33 + (polyPx[var39 * 2 + 1] - (float)var33) * var36 + var38;
                        }

                        double var70 = 0.0;

                        for (int var41 = 0; var41 < var52; var41++) {
                            int var42 = (var41 + 1) % var52;
                            var70 += polyPx[var41 * 2] * polyPx[var42 * 2 + 1] - polyPx[var42 * 2] * polyPx[var41 * 2 + 1];
                        }

                        if (var70 < 0.0) {
                            for (int var71 = 0; var71 < var52 / 2; var71++) {
                                int var72 = var52 - 1 - var71;
                                float var43 = polyPx[var71 * 2];
                                float var44 = polyPx[var71 * 2 + 1];
                                polyPx[var71 * 2] = polyPx[var72 * 2];
                                polyPx[var71 * 2 + 1] = polyPx[var72 * 2 + 1];
                                polyPx[var72 * 2] = var43;
                                polyPx[var72 * 2 + 1] = var44;
                            }
                        }

                        polyN = var52;
                    }
                }

                boolean var53 = false;
                var30 = var18;
                var56 = var21;
                if (var27) {
                }

                GL11.glDisable(2929);
                GL11.glDisable(2884);
                GL11.glDisable(3089);
                GL11.glEnable(3042);
                GL11.glBlendFunc(770, 771);
                GL11.glDepthMask(false);
                GL20.glUseProgram(prog);
                GL13.glActiveTexture(33984);
                GL11.glBindTexture(3553, colorTex);
                GL20.glUniform1i(uTex, 0);
                GL20.glUniform2f(uCentre, var16, var17);
                GL20.glUniform1f(uRadius, var18);
                GL20.glUniform2f(uSize, var2, var3);
                GL20.glUniform2f(uPic, picSX, picSY);
                GL20.glUniform1f(uRetRoll, (float)Math.toRadians(ADS.P.reticleRoll));
                GL20.glUniform2f(uAxis, var19, var20);
                GL20.glUniform1i(uReflex, var27 ? 1 : 0);
                GL20.glUniform2f(uWinHalf, var30, var56);
                GL20.glUniform1i(uPolyN, polyN);
                if (polyN > 0) {
                    GL20.glUniform2fv(uPoly, Arrays.copyOf(polyPx, polyN * 2));
                }

                GL20.glUniform1i(uKeyed, var53 ? 1 : 0);
                GL20.glUniform1i(uRound, var27 && reflexRound ? 1 : 0);
                var32 = ADS.pip(3);
                float var63 = var32 >= 0.0F ? var32 : 0.45F;
                GL20.glUniform1f(uCorner, var27 && !reflexRound ? Math.max(0.0F, Math.min(1.0F, var63)) * Math.min(var30, var56) : 0.0F);
                var32 = ADS.turnRate;
                var63 = Math.max(0.0F, Math.min(1.0F, (var32 - 1.5F) / 2.5F));
                GL20.glUniform1f(uFade, var27 ? 1.0F - var63 * var63 * (3.0F - 2.0F * var63) : 1.0F);
                GL20.glUniform2f(uKeyUV, (var22 - var0) / var2, (var23 - var1) / var3);
                GL20.glUniform2f(uPx, 1.0F / var2, 1.0F / var3);
                if (var53) {
                    GL13.glActiveTexture(33987);
                    GL11.glBindTexture(3553, copyTex);
                    GL20.glUniform1i(uMain, 3);
                    GL13.glActiveTexture(33984);
                }

                GL20.glUniform2f(uRetPos, var22 + retOffX, var23 + retOffY);
                GL20.glUniform2f(uRetOff, retOffX, retOffY);
                Texture var34 = retTex;
                Texture var66 = glowTex;
                int var67 = retMode;
                if (var34 == null || var34.getID() <= 0) {
                    var67 = 0;
                }

                GL20.glUniform1i(uRetMode, var67);
                if (var67 != 0) {
                    GL13.glActiveTexture(33985);
                    GL11.glBindTexture(3553, var34.getID());
                    GL20.glUniform1i(uRet, 1);
                    GL20.glUniform4f(uRetUV, var34.getXStart(), var34.getYStart(), var34.getXEnd(), var34.getYEnd());
                    boolean var68 = var66 != null && var66.getID() > 0 && var67 == 1;
                    GL20.glUniform1i(uHasGlow, var68 ? 1 : 0);
                    if (var68) {
                        GL13.glActiveTexture(33986);
                        GL11.glBindTexture(3553, var66.getID());
                        GL20.glUniform1i(uGlow, 2);
                        GL20.glUniform4f(uGlowUV, var66.getXStart(), var66.getYStart(), var66.getXEnd(), var66.getYEnd());
                    }

                    GL20.glUniform1f(
                        uRetR,
                        var27
                            ? (var67 == 2 ? Math.max(5.0F, dotPx * var15 * 0.8F) : Math.max(14.0F, dotPx * var15 * 2.8F))
                            : (var67 == 2 ? Math.max(3.0F, dotPx * var15 * 0.5F) : var18 * 0.98F)
                    );
                    GL20.glUniform4f(uRetCol, retR, retG, retB, Math.min(1.0F, retBright));
                }

                GL30.glBindVertexArray(vao);
                GL11.glDrawArrays(4, 0, 3);
                GL30.glBindVertexArray(0);
                if (var53) {
                    GL13.glActiveTexture(33987);
                    GL11.glBindTexture(3553, 0);
                    GL13.glActiveTexture(33984);
                }

                if (var67 != 0) {
                    GL13.glActiveTexture(33986);
                    GL11.glBindTexture(3553, 0);
                    GL13.glActiveTexture(33985);
                    GL11.glBindTexture(3553, 0);
                    GL13.glActiveTexture(33984);
                }

                GL11.glBindTexture(3553, 0);
                GL20.glUseProgram(0);
                lensCx = (var16 - var0) / var14;
                lensCy = (var3 - (var17 - var1)) / var15;
                lensR = var18 / var15;
                long var69 = System.nanoTime();
                if (var69 - lastDiag > 3000000000L) {
                    lastDiag = var69;
                    Log.info(
                        String.format(
                            Locale.ROOT,
                            "PiP frame: drawnModel=%b (hits %d, misses %d) matched=%b misses=%d lens=(%.0f,%.0f) r=%.0f of %dx%d (centre %.0f,%.0f) axisShift=(%.0f,%.0f) zoom=%.2f eye=(%.4f,%.4f,%.4f)%s",
                            var24 != null,
                            drawnHits,
                            drawnMisses,
                            var26,
                            frameMisses,
                            var16 - var0,
                            var17 - var1,
                            var18,
                            var2,
                            var3,
                            var2 * 0.5F,
                            var3 * 0.5F,
                            var19,
                            var20,
                            var4,
                            var7[0],
                            var7[1],
                            var7[2],
                            var25 != null
                                ? String.format(
                                    Locale.ROOT,
                                    " rel=(%.4f,%.4f,%.4f) axis=(%.3f,%.3f,%.3f) ap=%.4f",
                                    var25[0],
                                    var25[1],
                                    var25[2],
                                    var25[3],
                                    var25[4],
                                    var25[5],
                                    var25[6]
                                )
                                : " (no measured eyepiece)"
                        )
                    );
                }
            }
        }
    }

    private static void buildProgram() {
        String var0 = "#version 330\nvoid main() { vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2); gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0); }\n";
        String var1 = "#version 330\nuniform sampler2D uTex; uniform vec2 uPic; uniform float uRetRoll; uniform vec2 uCentre; uniform float uRadius; uniform vec2 uSize; uniform vec2 uAxis;\nuniform int uRetMode; uniform sampler2D uRet; uniform vec4 uRetUV; uniform int uHasGlow; uniform sampler2D uGlow;\nuniform vec4 uGlowUV; uniform float uRetR; uniform vec4 uRetCol;\nuniform int uReflex; uniform vec2 uWinHalf; uniform vec2 uRetPos; uniform vec2 uRetOff;\nuniform sampler2D uMain; uniform int uKeyed; uniform vec2 uKeyUV; uniform vec2 uPx; uniform int uRound; uniform float uFade; uniform float uCorner;\nuniform int uPolyN; uniform vec2 uPoly[24];\nfloat polySd(vec2 p) { float d = -1.0e9; for (int i = 0; i < 24; i++) { if (i >= uPolyN) break; vec2 a = uPoly[i]; vec2 b = uPoly[(i + 1 < uPolyN) ? i + 1 : 0]; vec2 e = b - a; vec2 n = normalize(vec2(e.y, -e.x)); d = max(d, dot(p - a, n)); } return d; }\nout vec4 fragColor;\nvoid main() {\n  if (uReflex != 0) {\n    vec2 dw = abs(gl_FragCoord.xy - uCentre);\n    float cr = min(uCorner, min(uWinHalf.x, uWinHalf.y));\n    vec2 qb = dw - (uWinHalf - vec2(cr));\n    float sd = length(max(qb, 0.0)) + min(max(qb.x, qb.y), 0.0) - cr;\n    bool poly = uPolyN >= 3;\n    if (poly) { sd = polySd(gl_FragCoord.xy); if (sd > 0.0) discard; }\n    else if (uRound == 0 && sd > 0.0) discard;\n    vec2 uv = (gl_FragCoord.xy - uCentre + uAxis + uSize * 0.5) / uSize;\n    vec3 cw = texture(uTex, uv * uPic).rgb;\n    float e = clamp(-sd / 1.5, 0.0, 1.0);\n    if (uRound != 0 && !poly) { vec2 en = dw / uWinHalf; float rr = length(en); if (rr > 1.0) discard; e = clamp((1.0 - rr) * min(uWinHalf.x, uWinHalf.y) / 1.5, 0.0, 1.0); }\n    float a = 0.0;\n    vec2 q = (gl_FragCoord.xy - uRetPos) / uRetR;\n    q = vec2(cos(uRetRoll) * q.x - sin(uRetRoll) * q.y, sin(uRetRoll) * q.x + cos(uRetRoll) * q.y);\n    if (uRetMode != 0 && abs(q.x) <= 1.0 && abs(q.y) <= 1.0) {\n      vec2 f = vec2(q.x * 0.5 + 0.5, 0.5 - q.y * 0.5);\n      a = texture(uRet, mix(uRetUV.xy, uRetUV.zw, f)).a;\n      if (uHasGlow != 0) a = max(a, texture(uGlow, mix(uGlowUV.xy, uGlowUV.zw, f)).a);\n    }\n    a = clamp(a * uRetCol.a, 0.0, 1.0);\n    vec3 lit = uRetCol.rgb * 1.25;\n    vec2 gq = (gl_FragCoord.xy - uCentre) / max(uWinHalf, vec2(1.0));\n    float rim = (uRound != 0 && !poly) ? length(gq) : clamp(1.0 + sd / max(1.0, min(uWinHalf.x, uWinHalf.y)), 0.0, 1.0);\n    vec3 gl = cw * vec3(0.93, 0.98, 0.96) * (1.0 - 0.28 * smoothstep(0.55, 1.0, rim));\n    gl += vec3(0.06) * smoothstep(0.35, 0.0, length(gq - vec2(-0.45, 0.5)));\n    if (uKeyed == 0) { fragColor = vec4(mix(gl, lit, a), e * max(uFade, a)); return; }\n    vec3 key = vec3(0.0), keyW = vec3(0.0);\n    for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) {\n      vec2 o = uKeyUV + vec2(float(i), float(j)) * uPx * 3.0;\n      key += texture(uMain, o).rgb; keyW += texture(uTex, o * uPic).rgb;\n    }\n    key /= 9.0; keyW /= 9.0;\n    vec3 m = texture(uMain, uv).rgb;\n    float glass = (1.0 - smoothstep(0.07, 0.12, length(m - key))) * smoothstep(0.02, 0.05, length(m - cw));\n    if (length(key - keyW) < 0.05) { if (a < 0.02) discard; fragColor = vec4(lit, a * e); return; }\n    if (glass < 0.01) discard;\n    fragColor = vec4(mix(cw * vec3(0.97, 1.0, 0.98), lit, a), glass * e);\n    return;\n  }\n  vec2 d = gl_FragCoord.xy - uCentre; float r = length(d);\n  if (r > uRadius) discard;\n  vec3 c = texture(uTex, (d + uAxis + uSize * 0.5) / uSize * uPic).rgb;\n  if (uRetMode != 0) {\n    vec2 q = (d - uRetOff) / uRetR;\n    q = vec2(cos(uRetRoll) * q.x - sin(uRetRoll) * q.y, sin(uRetRoll) * q.x + cos(uRetRoll) * q.y);\n    if (abs(q.x) <= 1.0 && abs(q.y) <= 1.0) {\n      vec2 f = vec2(q.x * 0.5 + 0.5, 0.5 - q.y * 0.5);\n      float a = texture(uRet, mix(uRetUV.xy, uRetUV.zw, f)).a;\n      if (uRetMode == 1) c = mix(c, vec3(0.03), a);\n      else c = mix(c, uRetCol.rgb, a * uRetCol.a);\n      if (uHasGlow != 0) c = mix(c, uRetCol.rgb, texture(uGlow, mix(uGlowUV.xy, uGlowUV.zw, f)).a * uRetCol.a * 0.9);\n    }\n  }\n  c *= 0.35 + 0.65 * smoothstep(uRadius, uRadius * 0.8, r);\n  fragColor = vec4(c, clamp(uRadius - r, 0.0, 1.0));\n}\n";
        int var2 = shader(35633, var0);
        int var3 = shader(35632, var1);
        int var4 = GL20.glCreateProgram();
        GL20.glAttachShader(var4, var2);
        GL20.glAttachShader(var4, var3);
        GL20.glLinkProgram(var4);
        if (GL20.glGetProgrami(var4, 35714) == 0) {
            throw new IllegalStateException("PiP program link: " + GL20.glGetProgramInfoLog(var4));
        }

        GL20.glDeleteShader(var2);
        GL20.glDeleteShader(var3);
        uTex = GL20.glGetUniformLocation(var4, "uTex");
        uPic = GL20.glGetUniformLocation(var4, "uPic");
        uRetRoll = GL20.glGetUniformLocation(var4, "uRetRoll");
        uCentre = GL20.glGetUniformLocation(var4, "uCentre");
        uRadius = GL20.glGetUniformLocation(var4, "uRadius");
        uSize = GL20.glGetUniformLocation(var4, "uSize");
        uAxis = GL20.glGetUniformLocation(var4, "uAxis");
        uRetMode = GL20.glGetUniformLocation(var4, "uRetMode");
        uRet = GL20.glGetUniformLocation(var4, "uRet");
        uRetUV = GL20.glGetUniformLocation(var4, "uRetUV");
        uHasGlow = GL20.glGetUniformLocation(var4, "uHasGlow");
        uGlow = GL20.glGetUniformLocation(var4, "uGlow");
        uGlowUV = GL20.glGetUniformLocation(var4, "uGlowUV");
        uRetR = GL20.glGetUniformLocation(var4, "uRetR");
        uRetCol = GL20.glGetUniformLocation(var4, "uRetCol");
        uReflex = GL20.glGetUniformLocation(var4, "uReflex");
        uWinHalf = GL20.glGetUniformLocation(var4, "uWinHalf");
        uRetPos = GL20.glGetUniformLocation(var4, "uRetPos");
        uRetOff = GL20.glGetUniformLocation(var4, "uRetOff");
        uMain = GL20.glGetUniformLocation(var4, "uMain");
        uKeyed = GL20.glGetUniformLocation(var4, "uKeyed");
        uKeyUV = GL20.glGetUniformLocation(var4, "uKeyUV");
        uPx = GL20.glGetUniformLocation(var4, "uPx");
        uRound = GL20.glGetUniformLocation(var4, "uRound");
        uFade = GL20.glGetUniformLocation(var4, "uFade");
        uCorner = GL20.glGetUniformLocation(var4, "uCorner");
        uPolyN = GL20.glGetUniformLocation(var4, "uPolyN");
        uPoly = GL20.glGetUniformLocation(var4, "uPoly");
        vao = GL30.glGenVertexArrays();
        prog = var4;
    }

    private static int shader(int var0, String var1) {
        int var2 = GL20.glCreateShader(var0);
        GL20.glShaderSource(var2, var1);
        GL20.glCompileShader(var2);
        if (GL20.glGetShaderi(var2, 35713) == 0) {
            throw new IllegalStateException("PiP shader: " + GL20.glGetShaderInfoLog(var2));
        } else {
            return var2;
        }
    }

    private static void ensureTargets(int var0, int var1) {
        if (fbo == 0 || texW != var0 || texH != var1) {
            if (fbo == 0 || !keepBigger || texW < var0 || texH < var1 || texW > var0 + 64 || texH > var1 + 64) {
                int var2 = GL11.glGetInteger(32873);
                if (fbo != 0) {
                    GL30.glDeleteFramebuffers(fbo);
                    GL11.glDeleteTextures(colorTex);
                    GL30.glDeleteRenderbuffers(depthRb);
                    fbo = 0;
                }

                colorTex = GL11.glGenTextures();
                GL11.glBindTexture(3553, colorTex);
                GL11.glTexImage2D(3553, 0, 32856, var0, var1, 0, 6408, 5121, (ByteBuffer)null);
                GL11.glTexParameteri(3553, 10241, 9729);
                GL11.glTexParameteri(3553, 10240, 9729);
                GL11.glTexParameteri(3553, 10242, 33069);
                GL11.glTexParameteri(3553, 10243, 33069);
                GL11.glBindTexture(3553, var2);
                depthRb = GL30.glGenRenderbuffers();
                GL30.glBindRenderbuffer(36161, depthRb);
                GL30.glRenderbufferStorage(36161, 35056, var0, var1);
                GL30.glBindRenderbuffer(36161, 0);
                int var3 = GL11.glGetInteger(36006);
                int var4 = GL11.glGetInteger(36010);
                int var5 = GL30.glGenFramebuffers();
                GL30.glBindFramebuffer(36160, var5);
                GL30.glFramebufferTexture2D(36160, 36064, 3553, colorTex, 0);
                GL30.glFramebufferRenderbuffer(36160, 33306, 36161, depthRb);
                int var6 = GL30.glCheckFramebufferStatus(36160);
                GL30.glBindFramebuffer(36009, var3);
                GL30.glBindFramebuffer(36008, var4);
                if (var6 != 36053) {
                    throw new IllegalStateException("PiP framebuffer incomplete: 0x" + Integer.toHexString(var6));
                }

                fbo = var5;
                texW = var0;
                texH = var1;
                Log.info("PiP scope target " + var0 + "x" + var1);
            }
        }
    }

    private static int[] passSize(Object var0, int var1, int var2) {
        if (sizeBroken) {
            return null;
        }

        try {
            if (fWrTargets == null) {
                Class var3 = VP.cls("viewpoint.render.WorldRenderer");
                fWrTargets = acc(var3.getDeclaredField("targets"));
                Class var4 = VP.cls("viewpoint.render.Targets");
                fTgW = acc(var4.getDeclaredField("width"));
                fTgH = acc(var4.getDeclaredField("height"));
                fFcPipe = acc(var0.getClass().getField("pipeline"));
                Class var5 = VP.cls("viewpoint.platform.Pipeline");
                fPlUpscaler = acc(var5.getField("upscaler"));
                fPlScale = acc(var5.getField("renderScale"));
                fPlTaa = acc(var5.getField("taa"));
            }

            Object var13 = fWrTargets.get(null);
            Object var14 = fFcPipe.get(var0);
            if (var13 != null && var14 != null) {
                int var15 = fPlScale.getInt(var14);
                boolean var6 = fPlTaa.getBoolean(var14);
                int var7 = fTgW.getInt(var13);
                int var8 = fTgH.getInt(var13);
                if (fPlUpscaler.getInt(var14) == 0) {
                    if (var6 && var15 < 100 && var7 > 0 && var8 > 0 && var7 < var1 && var8 < var2) {
                        if (!taaLogged) {
                            taaLogged = true;
                            Log.info(
                                "PiP: TAA at render scale " + var15 + " % - the lens pass keeps TAA on (clean frame) so Viewpoint's buffers are not rebuilt"
                            );
                        }

                        return new int[]{var1, var2, 1};
                    } else {
                        return null;
                    }
                } else if (var7 > 0 && var8 > 0) {
                    float var9 = var15 / Math.max(var15, 100.0F);
                    int var10 = fitSide(var7, var9, var1);
                    int var11 = fitSide(var8, var9, var2);
                    if (var10 > 0 && var11 > 0) {
                        if (var10 == var1 && var11 == var2) {
                            return null;
                        }

                        if (!sizeLogged) {
                            sizeLogged = true;
                            Log.info(
                                "PiP: upscaling on (render scale "
                                    + var15
                                    + " %) - lens pass drawn at "
                                    + var10
                                    + "x"
                                    + var11
                                    + " -> "
                                    + var7
                                    + "x"
                                    + var8
                                    + " (the main frame's render size), so Viewpoint's buffers and the upscaler are kept"
                            );
                        }

                        return new int[]{var10, var11, 0};
                    } else {
                        if (!scaleWarned) {
                            scaleWarned = true;
                            Log.info(
                                "PiP: upscaling at render scale "
                                    + var15
                                    + " % - no lens size matches the upscaler's "
                                    + var7
                                    + "x"
                                    + var8
                                    + " (Viewpoint rebuilds its buffers in the lens pass)"
                            );
                        }

                        return null;
                    }
                } else {
                    return null;
                }
            } else {
                return null;
            }
        } catch (Throwable var12) {
            sizeBroken = true;
            Log.error("PiP: lens pass size with upscaling (lens drawn at full size)", var12);
            return null;
        }
    }

    static int fitSide(int var0, float var1, int var2) {
        if (var1 > 0.0F && var0 > 0) {
            if (Math.max(1, Math.round(var2 * var1)) == var0) {
                return var2;
            }

            int var3 = Math.round(var0 / var1);
            int var4 = 0;

            for (int var5 = 0; var5 <= 4; var5++) {
                for (int var9 : new int[]{var3 - var5, var3 + var5}) {
                    if (var9 >= 1
                        && var9 <= 2 * var2
                        && Math.max(1, Math.round(var9 * var1)) == var0
                        && (var4 == 0 || Math.abs(var9 - var2) < Math.abs(var4 - var2))) {
                        var4 = var9;
                    }
                }

                if (var4 != 0) {
                    return var4;
                }
            }

            return 0;
        } else {
            return 0;
        }
    }

    public static boolean skipInPass() {
        return inPass;
    }

    public static boolean lightPass() {
        return inPass && passQ < 2;
    }

    public static void scissorOn(Object var0) {
        if (inPass && passSc != null && !scissorBroken && var0 != null) {
            try {
                if (fFcWidth == null) {
                    fFcWidth = acc(var0.getClass().getField("width"));
                    fFcHeight = acc(var0.getClass().getField("height"));
                }

                int var1 = fFcWidth.getInt(var0);
                int var2 = fFcHeight.getInt(var0);
                if (var1 <= 0 || var2 <= 0 || passW <= 0 || passH <= 0) {
                    return;
                }

                float var3 = (float)var1 / passW;
                float var4 = (float)var2 / passH;
                int var5 = (int)Math.floor(passSc[0] * var3) - 2;
                int var6 = (int)Math.floor(passSc[1] * var4) - 2;
                int var7 = (int)Math.ceil((passSc[0] + passSc[2]) * var3) + 2;
                int var8 = (int)Math.ceil((passSc[1] + passSc[3]) * var4) + 2;
                var5 = Math.max(0, var5);
                var6 = Math.max(0, var6);
                var7 = Math.min(var1, var7);
                var8 = Math.min(var2, var8);
                if (var7 <= var5 || var8 <= var6) {
                    return;
                }

                GL11.glScissor(var5, var6, var7 - var5, var8 - var6);
                GL11.glEnable(3089);
                scissorSet = true;
            } catch (Throwable var9) {
                scissorBroken = true;
                Log.error("PiP: lens area limit (the lens pass draws the whole picture)", var9);
            }
        }
    }

    public static void scissorOff() {
        if (scissorSet) {
            scissorSet = false;
            GL11.glDisable(3089);
        }
    }

    private static void cost(long var0) {
        costNanos += var0;
        costFrames++;
        long var2 = System.currentTimeMillis();
        if (costLogAt == 0L) {
            costLogAt = var2 + 3000L;
        }

        if (var2 >= costLogAt) {
            float var4 = (float)costNanos / 1000000.0F / Math.max(1, costFrames);
            String var5 = String.format(
                Locale.ROOT,
                "PiP: lens pass %.2f ms per drawn frame (CPU side, %d frames, quality %s; shadow maps, far shadows and mouse pick reused from the main pass)",
                var4,
                costFrames,
                quality >= 2 ? "High" : (quality == 1 ? "Mid" : "Low")
            );
            if (!costLogged) {
                costLogged = true;
                Log.info(var5);
            } else {
                Log.dev(var5);
            }

            costNanos = 0L;
            costFrames = 0;
            costLogAt = var2 + 10000L;
        }
    }

    private static int[] lensScissor(Object var0, float[] var1, Matrix4f var2, Matrix4f var3, int var4, int var5, float var6, boolean var7) {
        float[] var8 = fromDrawnModel(var0, var1);
        if (var8 == null) {
            return null;
        }

        VPM.set(var2).mul(var3);
        V4.set(var8[0], var8[1], var8[2], 1.0F);
        VPM.transform(V4);
        if (!(V4.w > 1.0E-4F)) {
            return null;
        }

        float var9 = (V4.x / V4.w * 0.5F + 0.5F) * var4;
        float var10 = (V4.y / V4.w * 0.5F + 0.5F) * var5;
        float var11 = Math.max(var8[6], var8.length > 7 ? var8[7] : var8[6]) / V4.w * var2.m11() * 0.5F * var5;
        if (ADS.P.pipZ < 0.0F) {
            var11 *= V4.w / Math.max(0.02F, V4.w + ADS.P.pipZ);
        }

        float var12 = var9;
        float var13 = var10;
        if (!var7) {
            V4.set(var8[3], var8[4], var8[5], 0.0F);
            VPM.transform(V4);
            if (!(V4.w > 1.0E-4F)) {
                return null;
            }

            var12 = var4 * 0.5F + V4.x / V4.w * 0.5F * var4 * var6;
            var13 = var5 * 0.5F + V4.y / V4.w * 0.5F * var5 * var6;
        }

        var11 = var11 * Math.max(1.0F, ADS.pip(0)) * 1.15F + 32.0F;
        if (var11 > 4.0F && !(var11 > 2.0F * var5)) {
            int var14 = Math.max(0, (int)(var12 - var11));
            int var15 = Math.max(0, (int)(var13 - var11));
            int var16 = Math.min(var4, (int)(var12 + var11) + 1);
            int var17 = Math.min(var5, (int)(var13 + var11) + 1);
            return var16 - var14 >= 8 && var17 - var15 >= 8 ? new int[]{var14, var15, var16 - var14, var17 - var15} : null;
        } else {
            return null;
        }
    }

    private static boolean copyFrame(int var0, int var1, int var2, int var3, int var4, int var5) {
        if (copyBroken) {
            return false;
        }

        try {
            if (copyFbo == 0 || copyW != var2 || copyH != var3) {
                int var6 = GL11.glGetInteger(32873);
                if (copyFbo != 0) {
                    GL30.glDeleteFramebuffers(copyFbo);
                    GL11.glDeleteTextures(copyTex);
                    copyFbo = 0;
                }

                copyTex = GL11.glGenTextures();
                GL11.glBindTexture(3553, copyTex);
                GL11.glTexImage2D(3553, 0, 32856, var2, var3, 0, 6408, 5121, (ByteBuffer)null);
                GL11.glTexParameteri(3553, 10241, 9728);
                GL11.glTexParameteri(3553, 10240, 9728);
                GL11.glTexParameteri(3553, 10242, 33071);
                GL11.glTexParameteri(3553, 10243, 33071);
                GL11.glBindTexture(3553, var6);
                int var7 = GL30.glGenFramebuffers();
                GL30.glBindFramebuffer(36009, var7);
                GL30.glFramebufferTexture2D(36009, 36064, 3553, copyTex, 0);
                int var8 = GL30.glCheckFramebufferStatus(36009);
                GL30.glBindFramebuffer(36009, var4);
                if (var8 != 36053) {
                    throw new IllegalStateException("frame copy target incomplete: 0x" + Integer.toHexString(var8));
                }

                copyFbo = var7;
                copyW = var2;
                copyH = var3;
            }

            while (GL11.glGetError() != 0) {
            }

            GL30.glBindFramebuffer(36008, var5);
            GL30.glBindFramebuffer(36009, copyFbo);
            GL30.glBlitFramebuffer(var0, var1, var0 + var2, var1 + var3, 0, 0, var2, var3, 16384, 9728);
            int var11 = GL11.glGetError();
            GL30.glBindFramebuffer(36009, var4);
            GL30.glBindFramebuffer(36008, var5);
            if (var11 != 0) {
                throw new IllegalStateException("frame copy failed: GL error 0x" + Integer.toHexString(var11));
            } else {
                return true;
            }
        } catch (Throwable var10) {
            copyBroken = true;
            Log.error("open sight glass search off for this session (window drawn as a rectangle instead)", var10);

            try {
                GL30.glBindFramebuffer(36009, var4);
                GL30.glBindFramebuffer(36008, var5);
            } catch (Throwable var9) {
            }

            return false;
        }
    }

    private static void measureGlass(
        int var0, int var1, int var2, int var3, float var4, float var5, float var6, float var7, float var8, float var9, float var10, float var11, int var12
    ) {
        String var13 = reflexKey;
        if (var13 != null && !var13.isEmpty() && var11 > 0.0F && var10 > 1.0F) {
            int var14 = Math.max(0, (int)(var4 - var0 - var6));
            int var15 = Math.min(var2, (int)(var4 - var0 + var6));
            int var16 = Math.max(0, (int)(var5 - var1 - var7));
            int var17 = Math.min(var3, (int)(var5 - var1 + var7));
            int var18 = var15 - var14;
            int var19 = var17 - var16;
            if (var18 >= 8 && var19 >= 8 && var18 * var19 <= 600000) {
                int var20 = var18 * var19 * 4;
                if (rbMain == null || rbMain.capacity() < var20) {
                    rbMain = ByteBuffer.allocateDirect(var20).order(ByteOrder.nativeOrder());
                    rbClean = ByteBuffer.allocateDirect(var20).order(ByteOrder.nativeOrder());
                }

                rbMain.clear();
                rbClean.clear();
                GL11.glPixelStorei(3333, 4);
                GL30.glBindFramebuffer(36008, copyFbo);
                GL11.glReadPixels(var14, var16, var18, var19, 6408, 5121, rbMain);
                GL30.glBindFramebuffer(36008, fbo);
                GL11.glReadPixels(var14, var16, var18, var19, 6408, 5121, rbClean);
                GL30.glBindFramebuffer(36008, var12);
                int var21 = (int)(var8 - var0) - var14;
                int var22 = (int)(var9 - var1) - var16;
                float var23 = 0.0F;
                float var24 = 0.0F;
                float var25 = 0.0F;
                float var26 = 0.0F;
                float var27 = 0.0F;
                float var28 = 0.0F;
                int var29 = 0;

                for (int var30 = var22 - 3; var30 <= var22 + 3; var30++) {
                    for (int var31 = var21 - 3; var31 <= var21 + 3; var31++) {
                        if (var31 >= 0 && var30 >= 0 && var31 < var18 && var30 < var19) {
                            int var32 = (var30 * var18 + var31) * 4;
                            var23 += rbMain.get(var32) & 0xFF;
                            var24 += rbMain.get(var32 + 1) & 0xFF;
                            var25 += rbMain.get(var32 + 2) & 0xFF;
                            var26 += rbClean.get(var32) & 0xFF;
                            var27 += rbClean.get(var32 + 1) & 0xFF;
                            var28 += rbClean.get(var32 + 2) & 0xFF;
                            var29++;
                        }
                    }
                }

                if (var29 != 0) {
                    var23 /= var29 * 255.0F;
                    var24 /= var29 * 255.0F;
                    var25 /= var29 * 255.0F;
                    var26 /= var29 * 255.0F;
                    var27 /= var29 * 255.0F;
                    var28 /= var29 * 255.0F;
                    if (!(dist(var23, var24, var25, var26, var27, var28) < 0.05F)) {
                        double var53 = 0.0;
                        int var54 = 0;
                        int var33 = var18;
                        int var34 = -1;
                        int var35 = var19;
                        int var36 = -1;

                        for (int var37 = 0; var37 < var19; var37++) {
                            for (int var38 = 0; var38 < var18; var38++) {
                                int var39 = (var37 * var18 + var38) * 4;
                                float var40 = (rbMain.get(var39) & 0xFF) / 255.0F;
                                float var41 = (rbMain.get(var39 + 1) & 0xFF) / 255.0F;
                                float var42 = (rbMain.get(var39 + 2) & 0xFF) / 255.0F;
                                if (!(dist(var40, var41, var42, var23, var24, var25) > 0.095F)) {
                                    float var43 = (rbClean.get(var39) & 0xFF) / 255.0F;
                                    float var44 = (rbClean.get(var39 + 1) & 0xFF) / 255.0F;
                                    float var45 = (rbClean.get(var39 + 2) & 0xFF) / 255.0F;
                                    if (!(dist(var40, var41, var42, var43, var44, var45) < 0.035F)) {
                                        var53 += var37;
                                        var54++;
                                        if (var38 < var33) {
                                            var33 = var38;
                                        }

                                        if (var38 > var34) {
                                            var34 = var38;
                                        }

                                        if (var37 < var35) {
                                            var35 = var37;
                                        }

                                        if (var37 > var36) {
                                            var36 = var37;
                                        }
                                    }
                                }
                            }
                        }

                        if (var54 >= 40 && !(var54 > 0.8F * var18 * var19)) {
                            int var55 = (var33 <= 0 ? 1 : 0) + (var34 >= var18 - 1 ? 1 : 0) + (var35 <= 0 ? 1 : 0) + (var36 >= var19 - 1 ? 1 : 0);
                            if (var55 < 2) {
                                float var56 = var16 + var1 + 0.5F * (var35 + var36);
                                float var57 = var56 - var9;
                                float var58 = var11 / var10;
                                float[] var59 = CORR.computeIfAbsent(var13, var0x -> new float[2]);
                                float var60 = var57 * var58 * 0.5F;
                                float var61 = Math.max(0.004F, 1.5F * var11);
                                float var62 = Math.max(-var61, Math.min(var61, var59[0] + var60));
                                if (Math.abs(var57) > 1.5F) {
                                    var59[0] = var62;
                                }

                                long var63 = System.nanoTime();
                                if (var63 - (long)var59[1] > 0L && Math.abs(var57) > 3.0F && var63 - lastCorrLog > 2000000000L) {
                                    lastCorrLog = var63;
                                    Log.info(
                                        String.format(
                                            Locale.ROOT,
                                            "sight glass found: %s glass centre %.0f px from the reticle -> eye height correction %.4f (glass %dx%d px)",
                                            var13,
                                            var57,
                                            var59[0],
                                            var34 - var33 + 1,
                                            var36 - var35 + 1
                                        )
                                    );
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static float dist(float var0, float var1, float var2, float var3, float var4, float var5) {
        float var6 = var0 - var3;
        float var7 = var1 - var4;
        float var8 = var2 - var5;
        return (float)Math.sqrt(var6 * var6 + var7 * var7 + var8 * var8);
    }

    private static void bind(Object var0) throws Exception {
        if (mBegin == null) {
            Class var1 = VP.cls("viewpoint.render.WorldRenderer");
            fWrFrame = acc(var1.getDeclaredField("frame"));
            fWrTemporal = acc(var1.getDeclaredField("temporal"));
            fWrIsolate = acc(var1.getDeclaredField("isolate"));
            Class var2 = VP.cls("viewpoint.render.FrameContext");
            fOutDraw = acc(var2.getDeclaredField("outputDrawFbo"));
            fOutRead = acc(var2.getDeclaredField("outputReadFbo"));
            fViewport = acc(var2.getDeclaredField("viewport"));
            Class var3 = VP.cls("viewpoint.render.TemporalPass");
            fHistIdx = acc(var3.getDeclaredField("historyIndex"));
            fHistValid = acc(var3.getDeclaredField("historyValid"));
            fLastNanos = acc(var3.getDeclaredField("lastFrameNanos"));
            fPrevX = acc(var3.getDeclaredField("previousOriginX"));
            fPrevY = acc(var3.getDeclaredField("previousOriginY"));
            fPrevZ = acc(var3.getDeclaredField("previousOriginZ"));
            fPrevVP = acc(var3.getDeclaredField("previousViewProjection"));
            Class var4 = var0.getClass();
            fSdEye = acc(var4.getDeclaredField("eye"));
            fSdFrame = acc(var4.getDeclaredField("frame"));
            fSdView = acc(var4.getDeclaredField("view"));
            fSdFailed = acc(var4.getDeclaredField("failed"));
            Class var5 = VP.cls("viewpoint.core.Frame");
            fFrameScene = acc(var5.getField("scene"));
            Class var6 = VP.cls("viewpoint.render.SceneData");
            fSceneProj = acc(var6.getField("projection"));
            mBegin = acc(var1.getMethod("begin", var6, Matrix4f.class, float.class, float.class, float.class));
            mOutlines = acc(var1.getMethod("drawOutlines"));
            mTranslucent = acc(var1.getMethod("drawTranslucent", var6));

            try {
                mIndirect = acc(var1.getMethod("drawIndirect", var6));
            } catch (NoSuchMethodException var8) {
                mIndirect = null;
            }

            mWeather = acc(var1.getMethod("drawWeather", var6, float.class, float.class, float.class));
            mFinish = acc(var1.getMethod("finish", var6));
            Log.info("PiP scope: Viewpoint renderer bound");
        }
    }

    private static Field acc(Field var0) {
        var0.setAccessible(true);
        return var0;
    }

    private static Method acc(Method var0) {
        var0.setAccessible(true);
        return var0;
    }

    private Pip() {
    }
}
