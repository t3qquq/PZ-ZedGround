package t3zedballistics.ads;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import zombie.characters.IsoPlayer;

public final class VpCompat {
    private static volatile boolean kickLogged;
    private static volatile boolean swayLogged;
    private static volatile boolean hipLogged;
    private static volatile boolean eyeLogged;
    private static boolean placeTried;
    private static boolean proneTried;
    private static Field fProneOnly;
    private static Field fKept;
    private static Field fEyeX;
    private static Field fEyeY;
    private static Field fEyeZ;
    private static Field fAmProne;
    private static Method mAmShare;

    public static boolean skipViewpointKick() {
        try {
            if (ADS.S.enabled && VP.ok) {
                boolean var0 = ADS.eShared > 0.02F || ADS.S.recoilAmount > 0.0F;
                if (var0 && !kickLogged) {
                    kickLogged = true;
                    Log.info(
                        "Viewpoint 0.1.6 camera recoil / weapon recoil: replaced by this mod's recoil"
                            + (ADS.S.recoilAmount > 0.0F ? "" : " while the sights are up")
                    );
                }

                return var0;
            } else {
                return false;
            }
        } catch (Throwable var1) {
            return false;
        }
    }

    public static double sway(double var0) {
        try {
            if (ADS.S.enabled && VP.ok) {
                float var2 = ADS.viewE();
                if (!(var2 > 0.0F)) {
                    return var0;
                }

                if (!swayLogged && var2 > 0.5F) {
                    swayLogged = true;
                    Log.info("Viewpoint 0.1.6 gunplay: crosshair sway off while the sights are up (shots go where the sights point)");
                }

                return var2 >= 1.0F ? 0.0 : var0 * (1.0 - var2);
            } else {
                return var0;
            }
        } catch (Throwable var3) {
            return var0;
        }
    }

    static boolean viewpointSpreadsHip() {
        boolean var0 = VP.gunplayOn();
        if (var0 && !hipLogged) {
            hipLogged = true;
            Log.info("Viewpoint 0.1.6 gunplay is on: its aim ring spreads hip fire, so this mod's hip-fire cone is not added");
        }

        return var0;
    }

    static float eyeE() {
        try {
            if (ADS.S.enabled && VP.ok) {
                IsoPlayer var0 = IsoPlayer.players[0];
                if (var0 != null && var0.getVehicle() == null) {
                    float var1 = ADS.eShared;
                    return var1 > 0.0F ? Math.min(1.0F, var1) : 0.0F;
                } else {
                    return 0.0F;
                }
            } else {
                return 0.0F;
            }
        } catch (Throwable var2) {
            return 0.0F;
        }
    }

    public static boolean steadyEye(boolean var0) {
        return var0 ? true : eyeE() > 0.0F;
    }

    public static float headFollow(float var0) {
        float var1 = eyeE();
        if (!(var1 > 0.0F)) {
            return var0;
        }

        if (!eyeLogged && var1 > 0.5F && var0 > 0.0F) {
            eyeLogged = true;
            Log.info("eye: Viewpoint's 'Head movement' (" + var0 + ") held at 0 while the sights are up (steady eye, as Viewpoint 0.1.5)");
        }

        return var0 * (1.0F - var1);
    }

    static boolean amProne() {
        if (!proneTried) {
            proneTried = true;

            try {
                fProneOnly = acc(VP.cls("com.viewpointmovement.core.CameraBridge").getDeclaredField("prone"));
                Log.info("weapon rest: Advanced Movement prone found");
            } catch (Throwable var2) {
                fProneOnly = null;
            }
        }

        if (fProneOnly == null) {
            return false;
        }

        try {
            return fProneOnly.getBoolean(null);
        } catch (Throwable var1) {
            fProneOnly = null;
            Log.error("weapon rest (prone flag)", var1);
            return false;
        }
    }

    public static void eyePlaced(Object var0, Object var1) {
        try {
            IsoPlayer var2 = IsoPlayer.players[0];
            if (var0 == null || var0 != var2 || var1 == null || var2.getVehicle() != null) {
                return;
            }

            if (!placeTried) {
                placeTried = true;

                try {
                    fKept = acc(VP.cls("viewpoint.input.Eye").getDeclaredField("kept"));
                } catch (Throwable var16) {
                    fKept = null;
                }

                try {
                    Class var3 = var1.getClass();
                    fEyeX = var3.getField("eyeX");
                    fEyeZ = var3.getField("eyeZ");
                } catch (Throwable var15) {
                    fEyeX = null;
                }

                try {
                    fEyeY = var1.getClass().getField("eyeY");
                } catch (Throwable var14) {
                    fEyeY = null;
                }

                try {
                    Class var18 = VP.cls("com.viewpointmovement.core.CameraBridge");
                    mAmShare = var18.getDeclaredMethod("ownShare");
                    mAmShare.setAccessible(true);
                    fAmProne = acc(var18.getDeclaredField("prone"));
                } catch (Throwable var13) {
                    mAmShare = null;
                    fAmProne = null;
                }
            }

            if (fKept != null) {
                try {
                    float[] var19 = (float[])fKept.get(null);
                    if (var19 != null && var19.length >= 2 && Float.isFinite(var19[0])) {
                        EyeTap.offX = var2.getX() - var19[0];
                        EyeTap.offZ = var2.getY() - var19[1];
                    }
                } catch (Throwable var12) {
                    fKept = null;
                    Log.error("eye placement (Eye.kept)", var12);
                }
            }

            float var20 = eyeE();
            if (!(var20 > 0.0F) || mAmShare == null || fEyeX == null || VP.amLeanX == null) {
                return;
            }

            if (fAmProne != null) {
                try {
                    if (fAmProne.getBoolean(null)) {
                        return;
                    }
                } catch (Throwable var11) {
                    fAmProne = null;
                    Log.error("eye placement (prone flag)", var11);
                }
            }

            float var4 = ((Number)mAmShare.invoke(null)).floatValue();
            float var5 = Math.max(0.0F, 1.0F - var4) * var20;
            float var6 = VP.amLeanX.getFloat(null);
            float var7 = VP.amLeanY.getFloat(null);
            float var8 = var6 * var5;
            float var9 = var7 * var5;
            float var10 = 0.0F;
            if (ADS.leanUsedValid) {
                var8 += var20 * (ADS.leanUsedX - var6);
                var9 += var20 * (ADS.leanUsedY - var7);
                if (fEyeY != null && VP.amLeanDrop != null && VP.amDip != null) {
                    var10 = var20 * (ADS.leanUsedDrop - (VP.amLeanDrop.getFloat(null) + VP.amDip.getFloat(null)));
                }
            }

            if (var8 == 0.0F && var9 == 0.0F && var10 == 0.0F) {
                return;
            }

            fEyeX.setFloat(var1, fEyeX.getFloat(var1) - var8);
            fEyeZ.setFloat(var1, fEyeZ.getFloat(var1) - var9);
            if (var10 != 0.0F) {
                fEyeY.setFloat(var1, fEyeY.getFloat(var1) - var10);
            }
        } catch (Throwable var17) {
            mAmShare = null;
            Log.error("eye placement (lean while aiming)", var17);
        }
    }

    private static Field acc(Field var0) {
        var0.setAccessible(true);
        return var0;
    }

    private VpCompat() {
    }
}
