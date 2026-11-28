package t3zedballistics.ads;

import java.util.Locale;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.IsoWorld;
import java.util.List;

final class Rest {
    static final int NONE = 0;
    static final int PRONE = 1;
    static final int SURFACE = 2;
    static final int BIPOD = 3;
    static final String[] NAMES = new String[]{"none", "prone", "surface", "bipod"};
    static final float SUPPORT_AT = 0.62F;
    static final float GUN_BELOW = 0.05F;
    static final float TOO_LOW = 0.03F;
    static final float TOO_HIGH = 0.12F;
    static final float WINDOW_SILL_H = 0.85F;
    static final long ENTER_NS = 150000000L;
    static final long LEAVE_NS = 120000000L;
    static volatile boolean surface;
    static volatile boolean prone;
    static volatile boolean bipodDeployed;
    static volatile float surfaceH = -1.0F;
    static volatile String surfaceKind = "";
    private static long seenNs;
    private static long lostNs;
    private static boolean raw;

    static void tick(float[] var0) {
        prone = VpCompat.amProne();
        boolean var1 = false;
        if (var0 != null) {
            try {
                var1 = probe(var0);
            } catch (Throwable var4) {
                Log.error("weapon rest probe", var4);
            }
        } else {
            surfaceH = -1.0F;
            surfaceKind = "";
        }

        long var2 = System.nanoTime();
        if (var1 != raw) {
            raw = var1;
            if (var1) {
                seenNs = var2;
            } else {
                lostNs = var2;
            }
        }

        if (raw && !surface && var2 - seenNs >= 150000000L) {
            surface = true;
            Log.dev("rest: on " + surfaceKind + String.format(Locale.ROOT, " (%.2f m)", surfaceH));
        }

        if (!raw && surface && var2 - lostNs >= 120000000L) {
            surface = false;
            Log.dev("rest: lifted");
        }
    }

    static void clear() {
        surface = false;
        raw = false;
        surfaceH = -1.0F;
        surfaceKind = "";
    }

    static int state() {
        boolean var0 = surface || prone;
        if (bipodDeployed && var0) {
            return 3;
        } else if (surface) {
            return 2;
        } else {
            return prone ? 1 : 0;
        }
    }

    static float swayCut(int var0) {
        return var0 == 3 ? 0.95F : (var0 == 2 ? 0.85F : (var0 == 1 ? 0.5F : 0.0F));
    }

    static float recoilMul(int var0) {
        return var0 == 3 ? 0.5F : (var0 == 2 ? 0.75F : (var0 == 1 ? 0.85F : 1.0F));
    }

    private static boolean probe(float[] var0) {
        float var1 = var0[0];
        float var2 = var0[1];
        float var3 = var0[2];
        float var4 = var0[3] + (var0[6] - var0[3]) * 0.62F;
        float var5 = var0[4] + (var0[7] - var0[4]) * 0.62F;
        float var6 = var0[5] + (var0[8] - var0[5]) * 0.62F;
        int var7 = (int)Math.floor(var3 + 0.001F);
        float var8 = var7 * 2.4494896F;
        float var9 = var6 - 0.05F - var8;
        IsoCell var10 = IsoWorld.instance != null ? IsoWorld.instance.currentCell : null;
        if (var10 == null) {
            return false;
        }

        float var11 = -1.0F;
        String var12 = "";
        IsoGridSquare var13 = var10.getGridSquare((int)Math.floor(var4), (int)Math.floor(var5), var7);
        if (var13 != null) {
            List var14 = var13.getObjects();

            for (int var15 = 0; var14 != null && var15 < var14.size(); var15++) {
                IsoObject var16 = (IsoObject)var14.get(var15);
                if (var16 != null) {
                    float var17 = var16.getSurfaceOffsetNoTable();
                    if (var17 > 0.0F) {
                        float var18 = var17 / 96.0F * 2.4494896F;
                        if (fits(var9, var18) && var18 > var11) {
                            var11 = var18;
                            var12 = "surface";
                        }
                    }
                }
            }
        }

        IsoGridSquare var24 = var10.getGridSquare((int)Math.floor(var1), (int)Math.floor(var2), var7);
        if (var24 != null && var13 != null && var24 != var13) {
            IsoGridSquare var25 = var24;

            for (int var26 = 0; var26 < 2 && var25 != var13; var26++) {
                int var27 = var25.getX();
                int var28 = var25.getY();
                int var19 = Integer.signum(var13.getX() - var27);
                int var20 = Integer.signum(var13.getY() - var28);
                IsoGridSquare var21 = var19 != 0 ? var10.getGridSquare(var27 + var19, var28, var7) : var10.getGridSquare(var27, var28 + var20, var7);
                if (var19 != 0 && var20 != 0 && !lineCrossesX(var1, var2, var4, var5, var27, var19)) {
                    var21 = var10.getGridSquare(var27, var28 + var20, var7);
                }

                if (var21 == null) {
                    break;
                }

                float var22 = -1.0F;
                String var23 = "";
                if (var25.isWindowTo(var21)) {
                    var22 = 0.85F;
                    var23 = "window sill";
                } else if (var25.isHoppableTo(var21)) {
                    var22 = 1.05F;
                    var23 = "low wall / fence";
                }

                if (var22 > 0.0F && fits(var9, var22) && var22 > var11) {
                    var11 = var22;
                    var12 = var23;
                }

                var25 = var21;
            }
        }

        surfaceH = var11;
        surfaceKind = var12;
        return var11 > 0.0F;
    }

    private static boolean fits(float var0, float var1) {
        return var0 >= var1 - 0.03F && var0 <= var1 + 0.12F;
    }

    private static boolean lineCrossesX(float var0, float var1, float var2, float var3, int var4, int var5) {
        float var6 = var5 > 0 ? var4 + 1 : var4;
        float var7 = Math.abs(var2 - var0) > 1.0E-6F ? (var6 - var0) / (var2 - var0) : 2.0F;
        float var8 = var1 + (var3 - var1) * var7;
        return var8 >= Math.floor(var1) && var8 <= Math.floor(var1) + 1.0;
    }

    private Rest() {
    }
}
