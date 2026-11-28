package t3zedballistics.ads;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

final class IrisPip {
    private static Field fDrawn;
    private static Field fUniforms;
    private static Field fTargets;
    private static Field fBuffers;
    private static Field fTW;
    private static Field fTH;
    private static Field bTex;
    private static Field bRead;
    private static Field bFormat;
    private static Field bW;
    private static Field bH;
    private static Field bMip;
    private static Field bUsed;
    private static Field uPrevMV;
    private static Field uPrevP;
    private static Field uPrevFP;
    private static Field uPX;
    private static Field uPY;
    private static Field uPZ;
    private static Field uHas;
    private static Field uEye;
    private static Field uWet;
    private static Field uFrames;
    private static Field uSecs;
    private static Method mPipeline;
    private static Method mColour;
    static volatile boolean broken;
    static volatile boolean unbound;
    private static Object ownFor;
    private static int ownW;
    private static int ownH;
    private static int[][] own;
    private static int[] ownRead;
    private static boolean pHas;
    private static final Matrix4f pMV = new Matrix4f();
    private static final Matrix4f pP = new Matrix4f();
    private static final Matrix4f pFP = new Matrix4f();
    private static double pX;
    private static double pY;
    private static double pZ;
    private static Object curU;
    private static Object curT;
    private static int[][] mainTex;
    private static int[] mainRead;
    private static boolean[] swapped;
    private static boolean uSaved;
    private static final Matrix4f sMV = new Matrix4f();
    private static final Matrix4f sP = new Matrix4f();
    private static final Matrix4f sFP = new Matrix4f();
    private static double sX;
    private static double sY;
    private static double sZ;
    private static double sSecs;
    private static boolean sHas;
    private static float sEye;
    private static float sWet;
    private static long sFrames;
    private static boolean logged;

    static Object pipeline() {
        if (unbound) {
            return null;
        }

        try {
            bind();
            return !fDrawn.getBoolean(null) ? null : mPipeline.invoke(null);
        } catch (Throwable var1) {
            unbound = true;
            fail("detect", var1);
            return null;
        }
    }

    static void enter(Object var0) throws Exception {
        curU = fUniforms.get(var0);
        curT = fTargets.get(var0);
        Object[] var1 = (Object[])fBuffers.get(curT);
        int var2 = fTW.getInt(curT);
        int var3 = fTH.getInt(curT);
        if (own == null || ownFor != curT || ownW != var2 || ownH != var3 || own.length != var1.length) {
            freeOwn();
            own = new int[var1.length][];
            ownRead = new int[var1.length];
            int var4 = GL11.glGetInteger(34016);
            GL13.glActiveTexture(34015);
            int var5 = GL11.glGetInteger(32873);

            try {
                for (int var6 = 0; var6 < var1.length; var6++) {
                    Object var7 = var1[var6];
                    if (var7 != null && bUsed.getBoolean(var7)) {
                        Object var8 = bFormat.get(var7);
                        int var9 = bW.getInt(var7);
                        int var10 = bH.getInt(var7);
                        boolean var11 = bMip.getBoolean(var7);
                        own[var6] = new int[]{(Integer)mColour.invoke(null, var8, var9, var10, var11), (Integer)mColour.invoke(null, var8, var9, var10, var11)};
                    }
                }
            } finally {
                GL11.glBindTexture(3553, var5);
                GL13.glActiveTexture(var4);
            }

            ownFor = curT;
            ownW = var2;
            ownH = var3;
            pHas = false;
            if (!logged) {
                logged = true;
                Log.info("PiP scope: shader pack (Iris) mode, separate lens buffers " + var2 + "x" + var3);
            }
        }

        mainTex = new int[var1.length][];
        mainRead = new int[var1.length];
        swapped = new boolean[var1.length];

        for (int var15 = 0; var15 < var1.length; var15++) {
            if (own[var15] != null) {
                int[] var17 = (int[])bTex.get(var1[var15]);
                mainTex[var15] = new int[]{var17[0], var17[1]};
                mainRead[var15] = bRead.getInt(var1[var15]);
                var17[0] = own[var15][0];
                var17[1] = own[var15][1];
                bRead.setInt(var1[var15], ownRead[var15]);
                swapped[var15] = true;
            }
        }

        Object var16 = curU;
        sMV.set((Matrix4f)uPrevMV.get(var16));
        sP.set((Matrix4f)uPrevP.get(var16));
        sFP.set((Matrix4f)uPrevFP.get(var16));
        sX = uPX.getDouble(var16);
        sY = uPY.getDouble(var16);
        sZ = uPZ.getDouble(var16);
        sHas = uHas.getBoolean(var16);
        sEye = uEye.getFloat(var16);
        sWet = uWet.getFloat(var16);
        sFrames = uFrames.getLong(var16);
        sSecs = uSecs.getDouble(var16);
        uSaved = true;
        if (pHas) {
            ((Matrix4f)uPrevMV.get(var16)).set(pMV);
            ((Matrix4f)uPrevP.get(var16)).set(pP);
            ((Matrix4f)uPrevFP.get(var16)).set(pFP);
            uPX.setDouble(var16, pX);
            uPY.setDouble(var16, pY);
            uPZ.setDouble(var16, pZ);
        }

        uHas.setBoolean(var16, pHas);
    }

    static void exit() {
        try {
            if (swapped != null && curT != null) {
                Object[] var0 = (Object[])fBuffers.get(curT);
                boolean var1 = false;

                for (int var2 = 0; var2 < swapped.length && var2 < var0.length; var2++) {
                    if (swapped[var2]) {
                        int[] var3 = (int[])bTex.get(var0[var2]);
                        if (var3[0] == own[var2][0] && var3[1] == own[var2][1]) {
                            ownRead[var2] = bRead.getInt(var0[var2]);
                            var3[0] = mainTex[var2][0];
                            var3[1] = mainTex[var2][1];
                            bRead.setInt(var0[var2], mainRead[var2]);
                        } else {
                            var1 = true;
                        }
                    }
                }

                if (var1) {
                    own = null;
                    ownFor = null;
                }
            }

            if (uSaved && curU != null) {
                Object var9 = curU;
                pMV.set((Matrix4f)uPrevMV.get(var9));
                pP.set((Matrix4f)uPrevP.get(var9));
                pFP.set((Matrix4f)uPrevFP.get(var9));
                pX = uPX.getDouble(var9);
                pY = uPY.getDouble(var9);
                pZ = uPZ.getDouble(var9);
                pHas = uHas.getBoolean(var9);
                ((Matrix4f)uPrevMV.get(var9)).set(sMV);
                ((Matrix4f)uPrevP.get(var9)).set(sP);
                ((Matrix4f)uPrevFP.get(var9)).set(sFP);
                uPX.setDouble(var9, sX);
                uPY.setDouble(var9, sY);
                uPZ.setDouble(var9, sZ);
                uHas.setBoolean(var9, sHas);
                uEye.setFloat(var9, sEye);
                uWet.setFloat(var9, sWet);
                uFrames.setLong(var9, sFrames);
                uSecs.setDouble(var9, sSecs);
            }
        } catch (Throwable var7) {
            fail("restore", var7);
        } finally {
            swapped = null;
            mainTex = null;
            mainRead = null;
            uSaved = false;
            curU = null;
            curT = null;
        }
    }

    static void freeOwn() {
        if (own != null) {
            for (int[] var3 : own) {
                if (var3 != null) {
                    if (var3[0] != 0) {
                        GL11.glDeleteTextures(var3[0]);
                    }

                    if (var3[1] != 0) {
                        GL11.glDeleteTextures(var3[1]);
                    }
                }
            }
        }

        own = null;
        ownFor = null;
        pHas = false;
    }

    private static void bind() throws Exception {
        if (mPipeline == null) {
            Class var0 = VP.cls("viewpoint.render.WorldRenderer");
            fDrawn = acc(var0.getDeclaredField("irisDrawn"));
            Class var1 = VP.cls("viewpoint.render.IrisPipeline");
            fUniforms = acc(var1.getDeclaredField("uniforms"));
            fTargets = acc(var1.getDeclaredField("targets"));
            Class var2 = VP.cls("viewpoint.render.IrisTargets");
            fBuffers = acc(var2.getDeclaredField("buffers"));
            fTW = acc(var2.getDeclaredField("width"));
            fTH = acc(var2.getDeclaredField("height"));
            Class var3 = VP.cls("viewpoint.render.IrisTargets$Buffer");
            bTex = acc(var3.getDeclaredField("texture"));
            bRead = acc(var3.getDeclaredField("read"));
            bFormat = acc(var3.getDeclaredField("format"));
            bW = acc(var3.getDeclaredField("width"));
            bH = acc(var3.getDeclaredField("height"));
            bMip = acc(var3.getDeclaredField("mipmap"));
            bUsed = acc(var3.getDeclaredField("used"));
            Class var4 = VP.cls("viewpoint.render.IrisFormats$Format");
            mColour = acc(var2.getDeclaredMethod("colour", var4, int.class, int.class, boolean.class));
            Class var5 = VP.cls("viewpoint.render.IrisUniforms");
            uPrevMV = acc(var5.getDeclaredField("previousModelView"));
            uPrevP = acc(var5.getDeclaredField("previousProjection"));
            uPrevFP = acc(var5.getDeclaredField("previousFarProjection"));
            uPX = acc(var5.getDeclaredField("previousX"));
            uPY = acc(var5.getDeclaredField("previousY"));
            uPZ = acc(var5.getDeclaredField("previousZ"));
            uHas = acc(var5.getDeclaredField("hasPrevious"));
            uEye = acc(var5.getDeclaredField("eyeSkySmooth"));
            uWet = acc(var5.getDeclaredField("wetness"));
            uFrames = acc(var5.getDeclaredField("frames"));
            uSecs = acc(var5.getDeclaredField("seconds"));
            Class var6 = VP.cls("viewpoint.render.IrisMode");
            mPipeline = acc(var6.getDeclaredMethod("pipeline"));
        }
    }

    private static void fail(String var0, Throwable var1) {
        broken = true;
        Log.error("PiP scope shader-pack support (" + var0 + "); lens uses the shared pack buffers for this session", var1);
    }

    private static Field acc(Field var0) {
        var0.setAccessible(true);
        return var0;
    }

    private static Method acc(Method var0) {
        var0.setAccessible(true);
        return var0;
    }

    private IrisPip() {
    }
}
