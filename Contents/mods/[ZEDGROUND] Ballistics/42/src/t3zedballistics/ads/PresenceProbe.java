package t3zedballistics.ads;

import java.util.ArrayList;
import org.joml.Vector2f;
import zombie.iso.IsoCell;
import zombie.iso.IsoChunk;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoWorld;
import zombie.vehicles.BaseVehicle;

final class PresenceProbe {
    static final int NONE = 0;
    static final int WALL = 1;
    static final int DOOR = 2;
    static final int WINDOW = 3;
    static final int FURNITURE = 4;
    static final int VEHICLE = 5;
    static final String[] TYPE_NAMES = new String[]{"clear", "wall", "door", "window", "furniture", "vehicle"};
    static final float SOLIDTRANS_H = 0.95F;
    static final float VEHICLE_H = 1.15F;
    static final float LOW_EDGE_H = 1.05F;
    static final float MAX_SEG = 3.0F;
    static final PresenceProbe.Result LOCAL = new PresenceProbe.Result();
    private static float curStart;
    private static final Vector2f vtmp = new Vector2f();
    private static final ArrayList<BaseVehicle> nearVehicles = new ArrayList<>();
    private static float bestNeed;
    private static int bestType;
    private static float bestX;
    private static float bestY;
    private static float bestU;
    private static float bestNx;
    private static float bestNy;
    private static float bestD;

    static void run(float[] var0, PresenceProbe.Result var1) {
        bestNeed = -1.0F;
        bestType = 0;
        bestD = 0.0F;
        float var2 = var0[0];
        float var3 = var0[1];
        float var4 = var0[2];
        float var5 = var0[3];
        float var6 = var0[4];
        float var7 = var0[5];
        float var8 = var0[6];
        float var9 = var0[7];
        float var10 = var0[8];
        float var11 = var0[9];
        float var12 = var0[10];
        curStart = var12;
        float var13 = var8 - var5;
        float var14 = var9 - var6;
        float var15 = (float)Math.sqrt(var13 * var13 + var14 * var14);
        if (!(var15 > 0.02F)) {
            publish(var1);
        } else {
            float var16 = var13 / var15;
            float var17 = var14 / var15;
            float var18 = -var17;
            float var19 = var16;
            int var20 = (int)Math.floor(var4 + 1.0E-4F);
            float var21 = var20 * 2.4494896F;
            IsoCell var22 = IsoWorld.instance != null ? IsoWorld.instance.currentCell : null;
            if (var22 == null) {
                publish(var1);
            } else {
                gatherVehicles(var22, var2, var3, var20);
                float var23 = var8 + var16 * var12;
                float var24 = var9 + var17 * var12;
                segment(var22, var2, var3, var7, var23, var24, var10, var20, var21, var8, var9, var16, var17);

                for (byte var25 = -1; var25 <= 1; var25 += 2) {
                    float var26 = var18 * var11 * var25;
                    float var27 = var19 * var11 * var25;
                    if (reachable(var22, var2, var3, var5 + var26, var6 + var27, var20)) {
                        segment(
                            var22,
                            var5 + var26,
                            var6 + var27,
                            var7,
                            var23 + var26,
                            var24 + var27,
                            var10,
                            var20,
                            var21,
                            var8 + var26,
                            var9 + var27,
                            var16,
                            var17
                        );
                    }
                }

                publish(var1);
            }
        }
    }

    private static void publish(PresenceProbe.Result var0) {
        var0.need = bestNeed;
        var0.hitType = bestType;
        var0.hitX = bestX;
        var0.hitY = bestY;
        var0.hitU = bestU;
        var0.normX = bestNx;
        var0.normY = bestNy;
        var0.hitDist = bestD;
        var0.stampNs = System.nanoTime();
    }

    static void clear(PresenceProbe.Result var0) {
        var0.need = -1.0F;
        var0.hitType = 0;
        var0.normX = 0.0F;
        var0.normY = 0.0F;
        var0.hitDist = 0.0F;
        var0.stampNs = System.nanoTime();
    }

    private static void segment(
        IsoCell var0,
        float var1,
        float var2,
        float var3,
        float var4,
        float var5,
        float var6,
        int var7,
        float var8,
        float var9,
        float var10,
        float var11,
        float var12
    ) {
        float var13 = var4 - var1;
        float var14 = var5 - var2;
        float var15 = (float)Math.sqrt(var13 * var13 + var14 * var14);
        if (var15 > 1.0E-4F && !(var15 > 3.0F)) {
            int var16 = (int)Math.floor(var1);
            int var17 = (int)Math.floor(var2);
            int var18 = (int)Math.floor(var4);
            int var19 = (int)Math.floor(var5);
            int var20 = var13 > 0.0F ? 1 : (var13 < 0.0F ? -1 : 0);
            int var21 = var14 > 0.0F ? 1 : (var14 < 0.0F ? -1 : 0);
            float var22 = var20 == 0 ? Float.MAX_VALUE : (var20 > 0 ? var16 + 1 - var1 : var1 - var16) / Math.abs(var13);
            float var23 = var21 == 0 ? Float.MAX_VALUE : (var21 > 0 ? var17 + 1 - var2 : var2 - var17) / Math.abs(var14);
            float var24 = var20 == 0 ? Float.MAX_VALUE : 1.0F / Math.abs(var13);
            float var25 = var21 == 0 ? Float.MAX_VALUE : 1.0F / Math.abs(var14);
            IsoGridSquare var26 = var0.getGridSquare(var16, var17, var7);
            vehicles(var1, var2, var3, var4, var5, var6, var8, var9, var10, var11, var12);
            int var27 = 0;

            while ((var16 != var18 || var17 != var19) && var27++ < 16) {
                int var29 = var16;
                int var30 = var17;
                float var28;
                float var31;
                float var32;
                if (var22 < var23) {
                    var28 = var22;
                    var29 += var20;
                    var22 += var24;
                    var31 = -var20;
                    var32 = 0.0F;
                } else {
                    var28 = var23;
                    var30 += var21;
                    var23 += var25;
                    var31 = 0.0F;
                    var32 = -var21;
                }

                if (var28 > 1.0F) {
                    break;
                }

                IsoGridSquare var33 = var0.getGridSquare(var29, var30, var7);
                float var34 = var1 + var13 * var28;
                float var35 = var2 + var14 * var28;
                float var36 = var3 + (var6 - var3) * var28;
                if (var26 != null && var33 != null) {
                    byte var37 = 0;
                    if (var26.isBlockedTo(var33) && !lowEdgeUnder(var26, var33, var36 - var8)) {
                        var37 = (byte)(var26.isDoorTo(var33) ? 2 : (var26.isWindowTo(var33) ? 3 : 1));
                    } else if (!var33.HasTree() && (var33.isSolid() || var33.isSolidTrans() && var36 - var8 < 0.95F)) {
                        var37 = 4;
                    }

                    if (var37 != 0) {
                        hit(var37, var34, var35, var36, var31, var32, var9, var10, var11, var12, var15 * var28);
                        return;
                    }
                }

                var26 = var33;
                var16 = var29;
                var17 = var30;
            }
        }
    }

    private static boolean lowEdgeUnder(IsoGridSquare var0, IsoGridSquare var1, float var2) {
        return var2 < 1.05F ? false : var0.isHoppableTo(var1) && !var0.isWindowTo(var1) && !var0.isDoorTo(var1);
    }

    private static boolean reachable(IsoCell var0, float var1, float var2, float var3, float var4, int var5) {
        int var6 = (int)Math.floor(var1);
        int var7 = (int)Math.floor(var2);
        int var8 = (int)Math.floor(var3);
        int var9 = (int)Math.floor(var4);
        if (var6 == var8 && var7 == var9) {
            return true;
        }

        float var10 = var3 - var1;
        float var11 = var4 - var2;
        int var12 = var10 > 0.0F ? 1 : (var10 < 0.0F ? -1 : 0);
        int var13 = var11 > 0.0F ? 1 : (var11 < 0.0F ? -1 : 0);
        float var14 = var12 == 0 ? Float.MAX_VALUE : (var12 > 0 ? var6 + 1 - var1 : var1 - var6) / Math.abs(var10);
        float var15 = var13 == 0 ? Float.MAX_VALUE : (var13 > 0 ? var7 + 1 - var2 : var2 - var7) / Math.abs(var11);
        float var16 = var12 == 0 ? Float.MAX_VALUE : 1.0F / Math.abs(var10);
        float var17 = var13 == 0 ? Float.MAX_VALUE : 1.0F / Math.abs(var11);
        IsoGridSquare var18 = var0.getGridSquare(var6, var7, var5);
        int var19 = 0;

        while ((var6 != var8 || var7 != var9) && var19++ < 8) {
            if (var14 < var15) {
                var6 += var12;
                var14 += var16;
            } else {
                var7 += var13;
                var15 += var17;
            }

            IsoGridSquare var20 = var0.getGridSquare(var6, var7, var5);
            if (var18 != null && var20 != null && var18.isBlockedTo(var20) && !lowEdgeUnder(var18, var20, 1.05F)) {
                return false;
            }

            var18 = var20;
        }

        return true;
    }

    private static void hit(int var0, float var1, float var2, float var3, float var4, float var5, float var6, float var7, float var8, float var9, float var10) {
        float var11 = (var6 - var1) * var8 + (var7 - var2) * var9;
        float var12 = var11 + curStart;
        if (var12 > bestNeed) {
            bestNeed = var12;
            bestType = var0;
            bestX = var1;
            bestY = var2;
            bestU = var3;
            bestNx = var4;
            bestNy = var5;
            bestD = var10;
        }
    }

    private static void gatherVehicles(IsoCell var0, float var1, float var2, int var3) {
        nearVehicles.clear();
        int var4 = (int)Math.floor((var1 - 4.0F) / 8.0F);
        int var5 = (int)Math.floor((var2 - 4.0F) / 8.0F);
        int var6 = (int)Math.ceil((var1 + 4.0F) / 8.0F);
        int var7 = (int)Math.ceil((var2 + 4.0F) / 8.0F);

        for (int var8 = var5; var8 < var7; var8++) {
            for (int var9 = var4; var9 < var6; var9++) {
                IsoChunk var10 = var0.getChunk(var9, var8);
                if (var10 != null && var10.vehicles != null) {
                    for (int var11 = 0; var11 < var10.vehicles.size(); var11++) {
                        BaseVehicle var12 = (BaseVehicle)var10.vehicles.get(var11);
                        if (var12 != null && (int)Math.floor(var12.getZ() + 1.0E-4F) == var3 && !nearVehicles.contains(var12)) {
                            nearVehicles.add(var12);
                        }
                    }
                }
            }
        }
    }

    private static void vehicles(
        float var0, float var1, float var2, float var3, float var4, float var5, float var6, float var7, float var8, float var9, float var10
    ) {
        if (!nearVehicles.isEmpty()) {
            float var11 = var3 - var0;
            float var12 = var4 - var1;
            float var13 = var11 * var11 + var12 * var12;
            if (var13 > 1.0E-8F) {
                for (int var14 = 0; var14 < nearVehicles.size(); var14++) {
                    BaseVehicle var15 = nearVehicles.get(var14);
                    if (var15.intersectLineWithPoly(var0, var1, var3, var4, vtmp)) {
                        float var16 = ((vtmp.x - var0) * var11 + (vtmp.y - var1) * var12) / var13;
                        if (!(var16 < 0.0F) && !(var16 > 1.0F)) {
                            float var17 = var2 + (var5 - var2) * var16;
                            if (!(var17 - var6 >= 1.15F)) {
                                hit(5, vtmp.x, vtmp.y, var17, -var9, -var10, var7, var8, var9, var10, (float)Math.sqrt(var13) * var16);
                            }
                        }
                    }
                }
            }
        }
    }

    private PresenceProbe() {
    }

    static final class Result {
        volatile float need = -1.0F;
        volatile int hitType = 0;
        volatile float hitX;
        volatile float hitY;
        volatile float hitU;
        volatile float normX;
        volatile float normY;
        volatile float hitDist;
        volatile long stampNs;
    }
}
