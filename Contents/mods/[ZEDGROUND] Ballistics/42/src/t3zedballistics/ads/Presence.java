package t3zedballistics.ads;

import java.util.Locale;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import zombie.characters.IsoPlayer;
import zombie.core.math.PZMath;
import zombie.core.skinnedmodel.animation.AnimationPlayer;
import zombie.core.skinnedmodel.model.Model;
import zombie.core.skinnedmodel.model.ModelInstance;
import zombie.inventory.InventoryItem;
import zombie.inventory.types.HandWeapon;
import zombie.iso.Vector3;

public final class Presence {
    static final Presence.Config C = new Presence.Config();
    private static float a;
    private static float aVel;
    private static float target;
    private static volatile float amountShared;
    private static volatile float viewBlockShared;
    private static volatile boolean adsBlockedShared;
    private static float vb;
    private static float lr;
    private static float lrVel;
    private static boolean announced;
    private static int lastLoggedType = -1;
    private static boolean lastLoggedBlock;
    private static final float[] snapW = new float[11];
    private static final float[] snapR = new float[11];
    private static long snapNs;
    private static boolean snapActive;
    private static volatile boolean wantProbe;
    private static final Vector3f P = new Vector3f();
    private static final Vector3f F = new Vector3f();
    private static final Vector3f Mz = new Vector3f();
    private static final Vector3f R0 = new Vector3f();
    private static final Vector3f N = new Vector3f();
    private static final Vector3f tv = new Vector3f();
    private static final Matrix4f CM = new Matrix4f();
    private static final Matrix4f TM = new Matrix4f();
    private static final Vector3 iv = new Vector3();
    static final PresencePose POSE = new PresencePose();
    private static boolean lowReadyPose;
    static final WeaponDims DIMS = new WeaponDims();
    private static final WeaponDims.Info INFO = new WeaponDims.Info();
    private static boolean poseReady;
    private static float backFactor = 1.0F;
    private static volatile boolean shotFix;
    private static volatile float imx;
    private static volatile float imy;
    private static volatile float imz;
    private static volatile float idx;
    private static volatile float idy;
    private static volatile float idz;
    private static volatile float cdx;
    private static volatile float cdy;
    private static volatile float cdz;
    private static final float[] SPR = new float[2];
    private static final ArmRig.Solver RIG = new ArmRig.Solver();
    private static final Vector3f SC = new Vector3f();
    private static final Vector3f SI = new Vector3f();
    private static final Vector3f SD = new Vector3f();
    private static final Quaternionf SQ = new Quaternionf();
    static volatile boolean dbgValid;
    static volatile float dbgLen;
    static volatile float dbgPitch;
    static volatile float dbgYaw;
    static volatile String dbgSrc = "";
    static final float[] dbgScreen = new float[8];

    public static float amount() {
        return amountShared;
    }

    public static boolean adsBlocked() {
        return adsBlockedShared;
    }

    public static float viewBlock() {
        return viewBlockShared;
    }

    private static WeaponDims.Info localInfo() {
        ADS.Profile var0 = ADS.P;
        INFO.key = var0.weaponId;
        INFO.family = var0.family;
        INFO.genLength = var0.genLength;
        INFO.genMuzzleY = var0.genMuzzleY;
        INFO.overrideM = var0.lengthOverrideM;
        INFO.muzzleOffsetM = var0.muzzleOffsetM;
        return INFO;
    }

    static void frame(IsoPlayer var0, float var1, boolean var2) {
        if (C.enabled && var2) {
            if (!announced) {
                announced = true;
                Log.info("Physical Weapon Presence initialized");
            }

            float var3 = System.nanoTime() - PresenceProbe.LOCAL.stampNs < 250000000L ? PresenceProbe.LOCAL.need : -1.0F;
            float var4 = poseReady ? POSE.solve(var3, backFactor) : 0.0F;
            if (var4 < 0.02F) {
                var4 = 0.0F;
            }

            if (var4 > target || var4 < target - 0.06F) {
                target = var4;
            }

            float var5 = 1.0F / Math.max(0.25F, C.smoothing);
            float var6 = (target > a ? 30.0F : 12.0F) * var5;
            SPR[0] = a;
            SPR[1] = aVel;
            spring(SPR, target, var6, var1);
            a = SPR[0];
            aVel = SPR[1];
            if (!Float.isFinite(a) || !Float.isFinite(aVel)) {
                a = 0.0F;
                aVel = 0.0F;
            }

            if (a < 0.0F) {
                a = 0.0F;
                aVel = Math.max(0.0F, aVel);
            }

            if (a > 1.0F) {
                a = 1.0F;
                aVel = Math.min(0.0F, aVel);
            }

            if (target == 0.0F && a < 1.0E-4F && Math.abs(aVel) < 0.001F) {
                a = 0.0F;
                aVel = 0.0F;
            }

            boolean var7 = adsBlockedShared ? a > 0.22F : a > 0.4F;
            float var8 = PresencePose.smooth(PresencePose.clamp01((a - 0.12F) / 0.3F));
            vb = vb + (var8 - vb) * (1.0F - (float)Math.exp(-Math.max(0.0F, var1) * 18.0F));
            if (vb < 1.0E-4F && var8 == 0.0F) {
                vb = 0.0F;
            }

            amountShared = a;
            viewBlockShared = vb;
            adsBlockedShared = var7;
            wantProbe = true;
            if (C.debug) {
                debugLog(var7);
            }
        } else {
            reset();
        }
    }

    static void spring(float[] var0, float var1, float var2, float var3) {
        float var4 = Math.max(0.0F, Math.min(0.1F, var3));

        while (var4 > 1.0E-6F) {
            float var5 = Math.min(var4, 0.004166667F);
            var0[1] += (var2 * var2 * (var1 - var0[0]) - 2.0F * var2 * var0[1]) * var5;
            var0[0] += var0[1] * var5;
            var4 -= var5;
        }
    }

    static void reset() {
        lrVel = 0.0F;
        lr = 0.0F;
        vb = 0.0F;
        target = 0.0F;
        aVel = 0.0F;
        a = 0.0F;
        amountShared = 0.0F;
        viewBlockShared = 0.0F;
        adsBlockedShared = false;
        poseReady = false;
        wantProbe = false;
        shotFix = false;
        synchronized (snapW) {
            snapActive = false;
        }

        dbgValid = false;
    }

    static void idle() {
        poseReady = false;
        shotFix = false;
        synchronized (snapW) {
            snapActive = false;
        }
    }

    static void weaponChanged() {
        reset();
        DIMS.invalidate();
        PresenceProbe.clear(PresenceProbe.LOCAL);
    }

    static boolean prepare(IsoPlayer var0, ModelInstance var1, Matrix4f var2, float var3, float var4) {
        poseReady = false;
        if (!C.enabled) {
            return false;
        }

        float var5 = var2.transformDirection(tv.set(0.0F, 1.0F, 0.0F)).length();
        if (var5 > 1.0E-6F && DIMS.resolve(var1, var5, localInfo())) {
            float var6 = DIMS.lengthM(var5);
            float var7 = DIMS.rearY;
            float var8 = DIMS.muzzleY + DIMS.extraM / (var5 * 1.5F);
            boolean var9 = "pistol".equals(ADS.P.family) || var6 < 0.36F;
            var2.transformPosition(tv.set(DIMS.boreX, var7 + 0.35F * (var8 - var7), DIMS.boreZ), P);
            var2.transformPosition(tv.set(DIMS.boreX, var8, DIMS.boreZ), Mz);
            var2.transformPosition(tv.set(DIMS.boreX, var7, DIMS.boreZ), R0);
            F.set(Mz).sub(R0);
            if (F.lengthSquared() > 1.0E-10F && Float.isFinite(F.x) && Float.isFinite(P.x)) {
                F.normalize();
                if (var9) {
                    P.set(R0);
                }

                float var10 = (float)Math.sqrt((Mz.x - P.x) * (Mz.x - P.x) + (Mz.z - P.z) * (Mz.z - P.z)) * 1.5F;
                float var11 = (float)Math.sqrt(R0.x * R0.x + R0.z * R0.z) * 1.5F;
                POSE.configure(var6, var10, var9, C.strength * ADS.P.poseScale, var11);
                backFactor = 1.0F - 0.7F * var4;
                poseReady = true;
                float var12 = var0.getX();
                float var13 = var0.getY();
                float var14 = var0.getZ();
                synchronized (snapW) {
                    snapW[0] = var12;
                    snapW[1] = var13;
                    snapW[2] = var14;
                    toWorld(R0, var12, var13, var14, var3);
                    snapW[3] = iv.x;
                    snapW[4] = iv.y;
                    snapW[5] = iv.z * 2.4494896F;
                    toWorld(Mz, var12, var13, var14, var3);
                    snapW[6] = iv.x;
                    snapW[7] = iv.y;
                    snapW[8] = iv.z * 2.4494896F;
                    snapW[9] = ADS.P.collisionRadiusM > 0.0F ? ADS.P.collisionRadiusM : (var9 ? 0.05F : 0.045F);
                    snapW[10] = C.startDist + PresencePose.extraStart(var6, var9);
                    snapNs = System.nanoTime();
                    snapActive = true;
                }

                imx = snapW[6];
                imy = snapW[7];
                imz = snapW[8] / 2.4494896F;
                iv.set(F.x, F.y, F.z);
                Model.vectorToWorldCoords(0.0F, 0.0F, 0.0F, var3, iv);
                idx = iv.x;
                idy = iv.y;
                idz = iv.z;
                if (!(a > 1.0E-4F)) {
                    shotFix = false;
                    recordDebug(var0, var3, var6, false);
                    return false;
                }

                float var19 = PresenceProbe.LOCAL.normX;
                float var16 = PresenceProbe.LOCAL.normY;
                if (var19 == 0.0F && var16 == 0.0F) {
                    N.set(0.0F, 0.0F, 0.0F);
                } else {
                    ADS.worldToCms(var19, var16, 0.0F, var3, N);
                    if (N.lengthSquared() > 1.0E-8F) {
                        N.normalize();
                    }
                }

                lowReadyPose = false;
                boolean var17 = POSE.prepare(a, F, N, backFactor);
                if (var17) {
                    POSE.correction(P, 1.0F, CM);
                    CM.transformDirection(tv.set(F));
                    iv.set(tv.x, tv.y, tv.z);
                    Model.vectorToWorldCoords(0.0F, 0.0F, 0.0F, var3, iv);
                    cdx = iv.x;
                    cdy = iv.y;
                    cdz = iv.z;
                    shotFix = true;
                } else {
                    shotFix = false;
                }

                recordDebug(var0, var3, var6, var17);
                return var17;
            } else {
                shotFix = false;
                return false;
            }
        } else {
            shotFix = false;
            return false;
        }
    }

    private static void toWorld(Vector3f var0, float var1, float var2, float var3, float var4) {
        iv.set(var0.x, var0.y, var0.z);
        Model.vectorToWorldCoords(var1, var2, var3, var4, iv);
    }

    static Matrix4f fullCorrection() {
        return POSE.correction(P, 1.0F, CM);
    }

    static void applyToBones(AnimationPlayer var0, ArmRig.Rig var1, int[] var2, float[] var3) {
        if (!RIG.apply(var0, var1, POSE.correction(P, 1.0F, CM))) {
            boolean var4 = POSE.pistol && !lowReadyPose;

            for (int var5 = 0; var5 < var2.length; var5++) {
                float var6 = var4 ? 1.0F : var3[var5];
                if (var6 > 0.0F) {
                    POSE.correction(P, var6, CM);
                    org.lwjgl.util.vector.Matrix4f var7 = var0.getModelTransformAt(var2[var5]);
                    PZMath.convertMatrix(var7, TM);
                    TM.transpose();
                    CM.mul(TM, TM);
                    TM.transpose();
                    PZMath.convertMatrix(TM, var7);
                }
            }
        }
    }

    static boolean prepareLowReady(ModelInstance var0, Matrix4f var1, boolean var2, float var3) {
        float var4 = C.enabled && C.lowReady && var2 ? C.lowReadyAmount : 0.0F;
        SPR[0] = lr;
        SPR[1] = lrVel;
        spring(SPR, var4, 9.0F, var3);
        lr = SPR[0];
        lrVel = SPR[1];
        if (!Float.isFinite(lr)) {
            lr = 0.0F;
            lrVel = 0.0F;
        }

        if (var4 == 0.0F && Math.abs(lr) < 1.0E-4F && Math.abs(lrVel) < 0.001F) {
            lr = 0.0F;
            lrVel = 0.0F;
        }

        if (Math.abs(lr) > 1.0E-4F && var1 != null) {
            float var5 = var1.transformDirection(tv.set(0.0F, 1.0F, 0.0F)).length();
            if (var5 > 1.0E-6F && DIMS.resolve(var0, var5, localInfo())) {
                float var6 = DIMS.rearY;
                float var7 = DIMS.muzzleY;
                var1.transformPosition(tv.set(DIMS.boreX, var6 + 0.3F * (var7 - var6), DIMS.boreZ), P);
                var1.transformPosition(tv.set(DIMS.boreX, var7, DIMS.boreZ), Mz);
                var1.transformPosition(tv.set(DIMS.boreX, var6, DIMS.boreZ), R0);
                F.set(Mz).sub(R0);
                if (!(F.lengthSquared() > 1.0E-10F)) {
                    return false;
                }

                F.normalize();
                lowReadyPose = true;
                POSE.prepareLowered(F, (float)Math.toRadians(28.0) * lr);
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public static void tick() {
        try {
            RemotePresence.tick(IsoPlayer.players[0]);
        } catch (Throwable var4) {
            Log.error("weapon presence (other players)", var4);
            RemotePresence.clear();
        }

        if (C.enabled && wantProbe) {
            boolean var0;
            synchronized (snapW) {
                var0 = snapActive && System.nanoTime() - snapNs < 200000000L;
                if (var0) {
                    System.arraycopy(snapW, 0, snapR, 0, snapR.length);
                }
            }

            Rest.tick(var0 && ADS.restWanted() ? snapR : null);
            if (!var0) {
                PresenceProbe.clear(PresenceProbe.LOCAL);
            } else {
                try {
                    PresenceProbe.run(snapR, PresenceProbe.LOCAL);
                } catch (Throwable var3) {
                    Log.error("weapon presence probe", var3);
                    PresenceProbe.clear(PresenceProbe.LOCAL);
                }
            }
        } else {
            Rest.tick(null);
            if (PresenceProbe.LOCAL.need > 0.0F || PresenceProbe.LOCAL.hitType != 0) {
                PresenceProbe.clear(PresenceProbe.LOCAL);
            }
        }
    }

    static boolean allowed(IsoPlayer var0, ModelInstance var1) {
        if (!C.enabled || var0 == null || var1 == null || var0.isDead()) {
            return false;
        }

        if (!VP.viewOn() || VP.freeCam()) {
            return false;
        }

        if (var0.getVehicle() != null) {
            return false;
        }

        InventoryItem var2 = var0.getPrimaryHandItem();
        return var2 instanceof HandWeapon && ((HandWeapon)var2).isAimedFirearm();
    }

    public static void afterMuzzle(Object var0, Object var1, Object var2) {
        if (C.enabled && var1 instanceof Vector3 && var2 instanceof Vector3) {
            try {
                IsoPlayer var3 = IsoPlayer.players[0];
                if (var3 == null || var3.getBallisticsController() != var0) {
                    RemotePresence.afterMuzzle(var0, var1, var2);
                } else if (shotFix) {
                    undoShot(var3, (Vector3)var1, (Vector3)var2, cdx, cdy, cdz, idx, idy, idz, imx, imy, imz, SC, SI, SD, SQ);
                }
            } catch (Throwable var4) {
                Log.error("weapon presence shot", var4);
            }
        }
    }

    static void undoShot(
        IsoPlayer var0,
        Vector3 var1,
        Vector3 var2,
        float var3,
        float var4,
        float var5,
        float var6,
        float var7,
        float var8,
        float var9,
        float var10,
        float var11,
        Vector3f var12,
        Vector3f var13,
        Vector3f var14,
        Quaternionf var15
    ) {
        float var16 = 2.4494896F;
        var12.set(var3, var4, var5 * var16);
        var13.set(var6, var7, var8 * var16);
        if (var12.lengthSquared() > 1.0E-12F && var13.lengthSquared() > 1.0E-12F) {
            var12.normalize();
            var13.normalize();
            var15.rotationTo(var12, var13);
            var14.set(var2.x, var2.y, var2.z * var16);
            if (var14.lengthSquared() > 1.0E-12F) {
                var15.transform(var14);
                var2.x = var14.x;
                var2.y = var14.y;
                var2.z = var14.z / var16;
                float var17 = (float)Math.sqrt(var2.x * var2.x + var2.y * var2.y + var2.z * var2.z);
                if (var17 > 1.0E-6F) {
                    float var18 = var2.x / var17;
                    float var19 = var2.y / var17;
                    float var20 = var2.z / var17;
                    float var21 = -(var0.getX() * var18 + var0.getY() * var19 + var0.getZ() * var20);
                    float var22 = var18 * var9 + var19 * var10 + var20 * var11 + var21;
                    var1.x = var9 - var18 * var22;
                    var1.y = var10 - var19 * var22;
                    var1.z = var11 - var20 * var22;
                }
            }
        }
    }

    private static void recordDebug(IsoPlayer var0, float var1, float var2, boolean var3) {
        if (!C.debug) {
            dbgValid = false;
        } else {
            dbgLen = var2;
            dbgSrc = DIMS.source;
            dbgPitch = var3 ? POSE.pitchDeg() : 0.0F;
            dbgYaw = var3 ? POSE.yawDeg() : 0.0F;
            if (VP.firstPerson()) {
                ADS.screenOfCms(R0, var0, var1, dbgScreen, 0);
                ADS.screenOfCms(Mz, var0, var1, dbgScreen, 2);
                ADS.screenOfCms(P, var0, var1, dbgScreen, 6);
                if (PresenceProbe.LOCAL.hitType != 0) {
                    float var7 = var0.getX();
                    float var5 = var0.getY();
                    float var6 = var0.getZ();
                    ADS.worldToCms(PresenceProbe.LOCAL.hitX - var7, PresenceProbe.LOCAL.hitY - var5, PresenceProbe.LOCAL.hitU / 2.4494896F - var6, var1, tv);
                    ADS.screenOfCms(tv, var0, var1, dbgScreen, 4);
                } else {
                    dbgScreen[4] = dbgScreen[5] = -9999.0F;
                }

                dbgValid = true;
            } else {
                for (int var4 = 0; var4 < 8; var4++) {
                    dbgScreen[var4] = -9999.0F;
                }

                dbgValid = true;
            }
        }
    }

    private static void debugLog(boolean var0) {
        int var1 = PresenceProbe.LOCAL.hitType;
        if (var1 != lastLoggedType || var0 != lastLoggedBlock) {
            lastLoggedType = var1;
            lastLoggedBlock = var0;
            Log.info(
                String.format(
                    Locale.ROOT,
                    "presence: %s need %.2f m, amount %.2f, sight %s, length %.2f m (%s)",
                    PresenceProbe.TYPE_NAMES[Math.max(0, Math.min(5, var1))],
                    PresenceProbe.LOCAL.need,
                    a,
                    var0 ? "blocked" : "free",
                    dbgLen,
                    DIMS.source
                )
            );
        }
    }

    private Presence() {
    }

    static final class Config {
        volatile boolean enabled = true;
        volatile float startDist = 0.08F;
        volatile float strength = 1.0F;
        volatile float smoothing = 1.0F;
        volatile boolean lowReady = false;
        volatile float lowReadyAmount = 0.5F;
        volatile boolean debug = false;
        volatile boolean others = true;
    }
}
