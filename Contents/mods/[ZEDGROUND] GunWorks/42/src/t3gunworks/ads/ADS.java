package t3gunworks.ads;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import zombie.GameTime;
import zombie.characters.IsoPlayer;
import zombie.core.Core;
import zombie.core.math.PZMath;
import zombie.core.skinnedmodel.animation.AnimationPlayer;
import zombie.core.skinnedmodel.model.Model;
import zombie.core.skinnedmodel.model.ModelInstance;
import zombie.core.skinnedmodel.model.ModelInstanceRenderData;
import zombie.core.skinnedmodel.model.ModelMesh;
import zombie.core.skinnedmodel.model.SkinningBone;
import zombie.core.skinnedmodel.model.SkinningData;
import zombie.inventory.InventoryItem;
import zombie.iso.Vector3;
import zombie.scripting.objects.ModelAttachment;

public final class ADS {
    public static final int IRON = 0;
    public static final int REFLEX = 1;
    public static final int SCOPE = 2;
    public static final int S_NORMAL = 0;
    public static final int S_ENTERING = 1;
    public static final int S_ADS = 2;
    public static final int S_EXITING = 3;
    static final ADS.Profile P = new ADS.Profile();
    static final ADS.Settings S = new ADS.Settings();
    static final HashMap<String, float[]> opticShapes = new HashMap<>();
    static volatile boolean want;
    static volatile boolean forcedFP;
    static volatile boolean suspended;
    static volatile float swayFactor = 1.0F;
    static volatile boolean steadyHeld;
    static volatile float steadyShared;
    static volatile float breathShared = 1.0F;
    private static float steadyE;
    private static float breath = 1.0F;
    private static float gasp;
    private static float restE;
    static volatile int restShared;
    private static boolean steadyLock;
    private static boolean steadyLogged;
    static final float BREATH_HOLD = 5.0F;
    static final float BREATH_REFILL = 6.0F;
    static final float STEADY_SWAY_CUT = 0.88F;
    static final float GASP_TIME = 1.6F;
    static final float GASP_SWAY = 0.7F;
    static float t;
    static float e;
    static volatile float eShared;
    static volatile int state = 0;
    static long lastNs;
    static ModelInstance lastWeaponInst;
    static Object lastWeaponItem;
    static long lastTelemetryNs;
    static boolean firstAlignLogged;
    static volatile String lastIssue = "";
    static volatile float eyeRX;
    static volatile float eyeRY;
    static volatile float eyeRZ;
    static volatile float camX;
    static volatile float camY;
    static volatile float camZ;
    static volatile int eyeSeq;
    static int eyeSeqUsed = -1;
    static final Vector3f eyeOff = new Vector3f(0.0F, 0.12F, 0.08F);
    static boolean eyeOffValid;
    static final float EYE_UP = 0.1F;
    static final float EYE_FORWARD = 0.12F;
    static final float EYE_SIDE = 0.0F;
    static final float EYE_LIFT = 0.0F;
    static Object ironVarLogged;
    static String slideInfo;
    static float ironMinRelief;
    static float ironFront;
    static final float IRON_LIFT = 0.002F;
    static final float GLASS_INSET = 0.78F;
    static final float BEAD_LIFT = 0.003F;
    static final float BEAD_RELIEF = 0.85F;
    static ModelInstance hiddenInst;
    static float hiddenScale = 1.0F;
    static volatile boolean lensUp;
    static float tubeEyeR;
    static volatile boolean reflexWin;
    static float reflexHx;
    static final float REFLEX_WIN = 0.11F;
    private static final Map<Object, float[]> slideVerts = Collections.synchronizedMap(new WeakHashMap<>());
    static final float PIP_LENS = 0.16F;
    static final float PIP_APERTURE = 0.85F;
    static volatile float fovIn = 70.0F;
    static volatile float fovOut = 70.0F;
    static volatile float fovInRad = 1.2F;
    static volatile float fovOutRad = 1.2F;
    static volatile float fovBaseRad = 1.2F;
    static float vKick;
    static float vKickV;
    static float hKick;
    static float hKickV;
    static float gKick;
    static float gKickV;
    static float gBack;
    static float gBackV;
    static float lastVKick;
    static float lastHKick;
    static float swayX;
    static float swayY;
    static float lastSwayX;
    static float lastSwayY;
    static float swayPhase;
    static float climbP;
    static float climbY;
    static int shotsInString;
    static long lastShotNs;
    static float lagYaw;
    static float lagPitch;
    static float prevYaw;
    static float prevPitch;
    static volatile float turnRate;
    static boolean prevValid;
    static volatile boolean ovVisible;
    static volatile int ovMode;
    static volatile float ovDotX;
    static volatile float ovDotY;
    static volatile float ovWinX;
    static volatile float ovWinY;
    static volatile float ovWinR;
    static volatile float ovLensX;
    static volatile float ovLensY;
    static volatile float ovLensR;
    static volatile float ovEyeBox = 1.0F;
    static volatile float ovMisalign;
    private static final Matrix4f W = new Matrix4f();
    private static final Matrix4f A = new Matrix4f();
    private static final Matrix4f M = new Matrix4f();
    private static final Matrix4f TM = new Matrix4f();
    private static final Matrix3f BS = new Matrix3f();
    private static final Matrix3f BD = new Matrix3f();
    private static final Matrix3f R3 = new Matrix3f();
    private static final Quaternionf Q = new Quaternionf();
    private static final Quaternionf QT = new Quaternionf();
    private static final Quaternionf QK = new Quaternionf();
    private static final Quaternionf QL = new Quaternionf();
    private static final Vector3f sw = new Vector3f();
    private static final Vector3f dw = new Vector3f();
    private static final Vector3f uw = new Vector3f();
    private static final Vector3f S0 = new Vector3f();
    private static final Vector3f F = new Vector3f();
    private static final Vector3f U = new Vector3f();
    private static final Vector3f E = new Vector3f();
    private static final Vector3f D = new Vector3f();
    private static final Vector3f Uo = new Vector3f();
    private static final Vector3f Ro = new Vector3f();
    private static final Vector3f Ud = new Vector3f();
    private static final Vector3f Rd = new Vector3f();
    private static final Vector3f Pt = new Vector3f();
    private static final Vector3f Pv = new Vector3f();
    private static final Vector3f Ff = new Vector3f();
    private static final Vector3f Sf = new Vector3f();
    private static final Vector3f tmp = new Vector3f();
    private static final Vector3 iv = new Vector3();
    private static final Vector3f UPV = new Vector3f(0.0F, 1.0F, 0.0F);
    private static SkinningData boundData;
    private static int[] armBones = new int[0];
    private static boolean[] armIsProp = new boolean[0];
    private static float[] armWeight = new float[0];
    private static ArmRig.Rig armRig;
    private static final HashMap<String, float[]> restPose = new HashMap<>();
    private static final HashSet<String> restLogged = new HashSet<>();
    private static Object restData;
    private static int restBones;
    private static int clavL = -1;
    private static int clavR = -1;
    private static boolean prevCan;
    private static boolean raising;
    private static boolean raiseHide;
    private static boolean raiseUsedLogged;
    private static boolean raiseHideLogged;
    private static long raiseNs;
    static volatile float raiseK;
    static volatile float raiseOff;
    static final float RAISE_SETTLED = 6.0F;
    static final float RAISE_FROM = 12.0F;
    static final float RAISE_FULL = 30.0F;
    static final long RAISE_WINDOW_NS = 900000000L;
    private static final Matrix4f RA = new Matrix4f();
    private static final Matrix4f RB = new Matrix4f();
    private static final Matrix4f RW = new Matrix4f();
    private static final Matrix4f RSX = new Matrix4f();
    private static final Quaternionf RQ0 = new Quaternionf();
    private static final Vector3f RV0 = new Vector3f();
    private static final Vector3f RV1 = new Vector3f();
    private static final Vector3f RVF = new Vector3f();
    private static final float[] R16 = new float[16];
    private static int[] nearBodyBones = new int[0];
    static final float NEAR_BODY_E = 0.25F;
    private static int headBone = -1;
    static final long MAG_EASE_NS = 160000000L;
    private static volatile float magFrom;
    private static volatile float magTo;
    private static volatile long magAt;
    static volatile float zeroLive;
    private static float lookY0;
    private static float lookP0;
    private static final Matrix4f AN = new Matrix4f();
    private static final Matrix4f ANI = new Matrix4f();
    private static final Matrix4f REL = new Matrix4f();
    private static final Vector3f RV = new Vector3f();
    private static final Vector3f RS = new Vector3f();
    private static final Quaternionf RQ = new Quaternionf();
    private static Quaternionf[] fq = new Quaternionf[0];
    private static Vector3f[] fv = new Vector3f[0];
    private static boolean armFilt;
    static final float ARM_TAU = 0.15F;
    private static final Vector3f SfI = new Vector3f();
    private static final Vector3f FfI = new Vector3f();
    private static final Matrix4f WP = new Matrix4f();
    private static final Matrix4f W2 = new Matrix4f();
    private static final Vector3f c1 = new Vector3f();
    private static final Vector3f c2 = new Vector3f();
    private static final Vector3f c3 = new Vector3f();
    private static int selfChecks;
    private static long lastSelfCheckNs;
    static volatile String ironSrc = "";
    static volatile String eyeSrc = "";
    static volatile float eyeShift;
    private static final float[] EM = new float[4];
    private static final float[] AM = new float[4];
    static volatile float leanUsedX;
    static volatile float leanUsedY;
    static volatile float leanUsedDrop;
    static volatile boolean leanUsedValid;
    static final Vector3f UPH = new Vector3f(0.0F, 1.0F, 0.0F);
    static volatile float ironDX;
    static volatile float ironDY;
    static volatile float ironDZ;
    private static final Vector3f tA = new Vector3f();
    private static final Vector3f tB = new Vector3f();
    private static final Vector3f tC = new Vector3f();
    private static ModelInstance tubeInst;
    private static float[] tubeVal;
    private static String tubeLogKey = "";
    private static final Matrix4f LO = new Matrix4f();
    private static final Matrix4f LA = new Matrix4f();
    private static final Vector3f tv = new Vector3f();
    private static Object slideHomeLogged;
    static final float CLEAR = 0.05F;
    private static Object clearMesh;
    private static float clearIn = -1.0F;
    private static float clearOut;
    private static float clearNear;
    private static final Vector3f cE = new Vector3f();
    private static final Vector3f cT = new Vector3f();
    private static final Vector3f cR = new Vector3f();
    private static final Vector3f cU = new Vector3f();
    private static final Vector3f clearSw = new Vector3f();
    private static final Vector3f clearDw = new Vector3f();
    static volatile int glassVarIdx = -1;
    static float[] tubeWin;
    static float[] tubePoly;
    static int glassPass = -1;
    static boolean tubeWinRound;
    static boolean tubeWinGlass;
    static float tubeFrontY;
    private static Object flipVoteMesh;
    private static boolean flipVoteDown;
    private static int flipVotes;
    static final int FLIP_VOTES = 6;
    static final int FLIP_VOTES_AGAINST = 30;
    private static final Vector3f dF = new Vector3f();
    private static final Vector3f dR = new Vector3f();
    private static final Vector3f dU = new Vector3f();
    private static final Vector3f dP = new Vector3f();
    static float slopeDrop;
    private static final String[] FEET = new String[]{"Bip01_L_Foot", "Bip01_R_Foot"};
    private static final String[] THIGHS = new String[]{"Bip01_L_Thigh", "Bip01_R_Thigh"};
    private static final Vector3 footV = new Vector3();
    private static final Vector3f fw = new Vector3f();
    private static final Vector3f rt = new Vector3f();
    private static final Vector3f up = new Vector3f();
    private static final Vector3f rel = new Vector3f();
    private static final Vector3f pr = new Vector3f();
    private static final float[] proj = new float[2];

    static boolean canAds() {
        return VP.firstPerson() || S.thirdAds && VP.viewOn() && VP.thirdPerson();
    }

    static void setWant(boolean var0) {
        want = var0;
        if (var0 && S.thirdAds && !forcedFP && VP.viewOn() && VP.thirdPerson() && VP.setThirdPerson(false)) {
            forcedFP = true;
            Log.dev("third person -> first person for ADS");
        }
    }

    static boolean restWanted() {
        return S.rest && eShared > 0.3F;
    }

    private static boolean raiseFrame(IsoPlayer var0, AnimationPlayer var1, ModelInstance var2, Object var3, boolean var4, boolean var5, long var6) {
        raiseHide = false;
        raiseK = 0.0F;
        boolean var8 = var5 && !prevCan;
        prevCan = var5;
        if (var4 && var2 != null && P.valid && var0.getVehicle() == null) {
            bindBones(var1);
            if (armBones.length == 0) {
                return false;
            }

            if (restData != boundData || restBones != armBones.length) {
                restPose.clear();
                restData = boundData;
                restBones = armBones.length;
                clavL = var1.getSkinningBoneIndex("Bip01_L_Clavicle", -1);
                clavR = var1.getSkinningBoneIndex("Bip01_R_Clavicle", -1);
            }

            if (var8 && t <= 0.001F) {
                raising = true;
                raiseNs = var6;
            }

            if (raising && var6 - raiseNs > 900000000L) {
                raising = false;
            }

            float var9 = animOffDeg(var1, var2);
            raiseOff = var9;
            if (!Float.isFinite(var9)) {
                return false;
            }

            String var10 = poseKey(var0, var3);
            String var11 = var0.getCurrentActionContextStateName();
            boolean var12 = var0.isAiming() && ("aim".equals(var11) || "strafe".equals(var11)) && !var0.isPerformingAttackAnimation();
            if (var9 < 6.0F && var12) {
                keepPose(var1, var10);
            }

            if (!raising) {
                return false;
            }

            if (var9 < 12.0F) {
                raising = false;
                return false;
            }

            float var13 = clamp01((var9 - 12.0F) / 18.0F);
            var13 = var13 * var13 * (3.0F - 2.0F * var13);
            float[] var14 = restPose.get(var10);
            if (var14 == null) {
                raiseHide = true;
                if (!raiseHideLogged) {
                    raiseHideLogged = true;
                    Log.info("raise: no settled aim pose kept yet for " + var10 + " - sights wait for the game's raise animation (arms hidden meanwhile)");
                }

                return true;
            } else {
                raiseK = var13;
                useRestPose(var1, var14, var13);
                if (!raiseUsedLogged) {
                    raiseUsedLogged = true;
                    Log.info(
                        String.format(
                            Locale.ROOT,
                            "raise: the game's raise animation had the gun %.0f deg off the view - arms start from the kept %s aim pose",
                            var9,
                            var10
                        )
                    );
                }

                return false;
            }
        } else {
            raising = false;
            return false;
        }
    }

    private static float animOffDeg(AnimationPlayer var0, ModelInstance var1) {
        if (!weaponXfrm(var1, RW, RSX)) {
            return Float.NaN;
        }

        boolean var2 = modelUpsideDown(var1);
        RVF.set(var2 ? -P.dx : P.dx, P.dy, var2 ? -P.dz : P.dz);
        if (RVF.lengthSquared() < 1.0E-8F) {
            RVF.set(0.0F, 1.0F, 0.0F);
        }

        RW.transformDirection(RVF);
        if (RVF.lengthSquared() < 1.0E-12F) {
            return Float.NaN;
        }

        RVF.normalize();
        viewDirCms(VP.yaw(), VP.pitch(), var0.getRenderedAngle(), RV0);
        return (float)Math.toDegrees(Math.acos(clamp(RVF.dot(RV0), -1.0F, 1.0F)));
    }

    private static String poseKey(IsoPlayer var0, Object var1) {
        String var2 = null;
        if (var1 instanceof InventoryItem) {
            var2 = ((InventoryItem)var1).getSwingAnim();
        }

        return (var2 != null && !var2.isEmpty() ? var2 : "gun") + (var0.isSneaking() ? " crouched" : " standing");
    }

    private static void keepPose(AnimationPlayer var0, String var1) {
        int var2 = armBones.length;
        float[] var3 = restPose.get(var1);
        if (var3 == null || var3.length != var2 * 16 + 1) {
            var3 = new float[var2 * 16 + 1];
            restPose.put(var1, var3);
        }

        for (int var4 = 0; var4 < var2; var4++) {
            PZMath.convertMatrix(var0.getModelTransformAt(armBones[var4]), RA);
            RA.transpose();
            RA.get(R16);
            System.arraycopy(R16, 0, var3, var4 * 16, 16);
        }

        var3[var2 * 16] = VP.pitch();
        if (restLogged.add(var1)) {
            Log.dev("raise: kept the settled " + var1 + " aim pose (" + var2 + " bones)");
        }
    }

    private static void useRestPose(AnimationPlayer var0, float[] var1, float var2) {
        int var3 = armBones.length;
        float var4 = var0.getRenderedAngle();
        float var5 = VP.yaw();
        viewDirCms(var5, var1[var3 * 16], var4, RV0);
        viewDirCms(var5, VP.pitch(), var4, RV1);
        RQ0.rotationTo(RV0, RV1);
        float var6 = 0.0F;
        float var7 = 0.0F;
        float var8 = 0.0F;
        int var9 = 0;

        for (int var10 = 0; var10 < var3; var10++) {
            int var11 = armBones[var10];
            if (var11 == clavL || var11 == clavR) {
                var6 += var1[var10 * 16 + 12];
                var7 += var1[var10 * 16 + 13];
                var8 += var1[var10 * 16 + 14];
                var9++;
            }
        }

        if (var9 > 0) {
            var6 /= var9;
            var7 /= var9;
            var8 /= var9;
        }

        RW.identity().rotateAround(RQ0, var6, var7, var8);

        for (int var12 = 0; var12 < var3; var12++) {
            System.arraycopy(var1, var12 * 16, R16, 0, 16);
            RB.set(R16);
            RW.mul(RB, RB);
            org.lwjgl.util.vector.Matrix4f var13 = var0.getModelTransformAt(armBones[var12]);
            PZMath.convertMatrix(var13, RA);
            RA.transpose();
            RA.lerp(RB, var2);
            RA.transpose();
            PZMath.convertMatrix(RA, var13);
        }
    }

    private static void raiseHideArms(AnimationPlayer var0) {
        if (raiseHide && var0 != null) {
            for (int var4 : armBones) {
                org.lwjgl.util.vector.Matrix4f var5 = var0.getModelTransformAt(var4);
                PZMath.convertMatrix(var5, RA);
                RA.transpose();
                RA.scale(0.0F);
                RA.transpose();
                PZMath.convertMatrix(RA, var5);
            }
        }
    }

    public static void posed(Object var0) {
        if (VP.ok) {
            try {
                IsoPlayer var1 = IsoPlayer.players[0];
                if (var0 == null) {
                    return;
                }

                if (var1 == null || var0 != var1.getAnimationPlayer()) {
                    RemoteAim.posed(var0);
                    RemotePresence.posed(var0);
                    return;
                }

                AnimationPlayer var2 = (AnimationPlayer)var0;
                long var3 = System.nanoTime();
                float var5 = lastNs == 0L ? 0.016F : Math.min(0.1F, (float)(var3 - lastNs) / 1.0E9F);
                lastNs = var3;
                boolean var6 = VP.firstPerson();
                ModelInstance var7 = var1.primaryHandModel;
                InventoryItem var8 = var1.getPrimaryHandItem();
                if (var8 != lastWeaponItem) {
                    lastWeaponItem = var8;
                    t = 0.0F;
                    Presence.weaponChanged();
                }

                if (var7 != lastWeaponInst) {
                    if (lastWeaponInst != null && var7 != null) {
                        Log.dev("held model instance replaced (same item: " + (var8 == lastWeaponItem) + ")");
                    }

                    lastWeaponInst = var7;
                }

                boolean var9 = S.enabled && var6 && want && var7 != null && P.valid && !var1.isDead();
                boolean var10 = raiseFrame(var1, var2, var7, var8, var6, var9, var3);
                float var11 = var9 ? 1.0F : 0.0F;
                if (var10 && var11 > t) {
                    var11 = t;
                }

                float var12 = Math.max(0.05F, P.adsTime / Math.max(0.2F, S.speedMul));
                if (!var6 || var7 == null) {
                    t = 0.0F;
                } else if (t < var11) {
                    t = Math.min(var11, t + var5 / var12);
                } else if (t > var11) {
                    t = Math.max(var11, t - var5 / (var12 * 0.75F));
                }

                e = t * t * (3.0F - 2.0F * t);
                eShared = e;
                state = t <= 0.0F ? 0 : (t >= 1.0F ? 2 : (var11 > 0.0F ? 1 : 3));
                if (forcedFP) {
                    if (VP.thirdPerson()) {
                        forcedFP = false;
                    } else if (!want && t <= 0.0F && !suspended) {
                        forcedFP = false;
                        VP.setThirdPerson(true);
                        Log.dev("ADS over -> back to third person");
                    }
                }

                lookEffects(var5);
                boolean var13 = Presence.allowed(var1, var7);
                Presence.frame(var1, var5, var13);
                boolean var14 = P.mode == 2 && var11 > 0.0F && t >= 0.97F && var6 && !Presence.adsBlocked();
                lensUp = var14;
                setWeaponHidden(null);
                HipSpread.failsafe();
                if (e <= 1.0E-4F) {
                    ovVisible = false;
                    Sight.clear();
                    if (var6 && var7 != null && !var1.isDead()) {
                        hipKick(var2, var7);
                    }

                    if (var7 != null) {
                        presenceHip(var1, var2, var7, var5, var6, var13);
                    }

                    raiseHideArms(var2);
                    return;
                }

                align(var1, var2, var7, var5);
                raiseHideArms(var2);
            } catch (Throwable var15) {
                Log.error("posed", var15);
                Presence.reset();
                setWeaponHidden(null);
                lensUp = false;
                Sight.clear();
                t = 0.0F;
                e = 0.0F;
                eShared = 0.0F;
                ovVisible = false;
            }
        }
    }

    public static void onEye(Object var0, float[] var1) {
        if (var1 != null && var1.length >= 3 && var0 != null) {
            float var2 = VP.camX(var0);
            if (!Float.isNaN(var2)) {
                camX = var2;
                camY = VP.camY(var0);
                camZ = VP.camZ(var0);
                eyeRX = var1[0];
                eyeRY = var1[1];
                eyeRZ = var1[2];
                eyeSeq++;
            }
        }
    }

    public static float fov(float var0, boolean var1) {
        if (!var1 && eShared > 0.0F) {
            boolean var2 = var0 > 3.2F;
            float var3 = var2 ? (float)Math.toRadians(var0) : var0;
            float var4 = viewE();
            float var5;
            if (P.mode != 2 || pipNow() && !reflexWin) {
                var5 = var3 * (1.0F + (P.fovMul - 1.0F) * var4);
            } else {
                float var6 = clamp01((var4 - 0.35F) / 0.65F);
                var6 = var6 * var6 * (3.0F - 2.0F * var6);
                float var7 = 2.0F * (float)Math.atan(Math.tan(var3 * 0.5F) / effMag());
                var5 = var3 + (var7 - var3) * var6;
            }

            fovBaseRad = var5;
            float var9 = steadyZoomNow();
            if (var9 > 1.0001F) {
                var5 = 2.0F * (float)Math.atan(Math.tan(var5 * 0.5F) / var9);
            }

            fovIn = var0;
            fovInRad = var3;
            fovOutRad = var5;
            fovOut = var2 ? (float)Math.toDegrees(var5) : var5;
            return fovOut;
        } else {
            fovIn = var0;
            fovOut = var0;
            fovBaseRad = var0 > 3.2F ? (float)Math.toRadians(var0) : var0;
            return var0;
        }
    }

    static float viewE() {
        return eShared * (1.0F - Presence.viewBlock());
    }

    static float effMag() {
        return P.mag >= 1.9F ? magNow() * S.zoomMul : P.mag;
    }

    static void setMagLevel(float var0, boolean var1) {
        float var2 = magNow();
        if (!(var0 > 0.0F)) {
            magFrom = 0.0F;
            magTo = 0.0F;
        } else {
            magAt = System.nanoTime();
            magFrom = var1 ? 0.0F : var2;
            magTo = var0;
        }
    }

    static float magNow() {
        float var0 = magTo;
        float var1 = magFrom;
        if (!(var0 > 0.0F)) {
            return P.mag;
        } else if (!(var1 > 0.0F)) {
            return var0;
        } else {
            float var2 = (float)(System.nanoTime() - magAt) / 1.6E8F;
            if (!(var2 >= 1.0F) && !(var2 < 0.0F)) {
                var2 = var2 * var2 * (3.0F - 2.0F * var2);
                return (float)Math.exp(Math.log(var1) + (Math.log(var0) - Math.log(var1)) * var2);
            } else {
                return var0;
            }
        }
    }

    static float zeroNow() {
        return zeroLive > 0.0F ? zeroLive : P.zero;
    }

    static boolean pipNow() {
        return S.pip && !Pip.broken && P.mode == 2 && VP.firstPerson();
    }

    static boolean pipActive() {
        return lensUp && pipNow();
    }

    static float pipZoom() {
        float var0 = effMag();
        if (fovInRad > 0.0F && fovOutRad > 0.0F && fovInRad < 3.1F && fovOutRad < 3.1F) {
            var0 *= (float)(Math.tan(fovOutRad * 0.5F) / Math.tan(fovInRad * 0.5F));
        }

        return var0;
    }

    public static boolean hideCrosshair() {
        return S.hideCrosshair && eShared > 0.02F;
    }

    public static boolean sharpFocus() {
        return S.adsSharp && eShared > 0.02F;
    }

    public static void lookBefore() {
        if (eShared > 0.0F) {
            lookY0 = VP.yaw();
            lookP0 = VP.pitch();
        }
    }

    public static void lookAfter() {
        float var0 = eShared;
        if (var0 > 0.0F) {
            boolean var1;
            try {
                var1 = GameTime.isGamePaused();
            } catch (Throwable var5) {
                var1 = false;
            }

            if (var1) {
                VP.setYaw(lookY0);
                VP.setPitch(lookP0);
            } else {
                var0 = viewE();
                float var2 = 1.0F + (S.adsSens - 1.0F) * var0;
                if (P.mode == 2 && fovInRad > 0.0F) {
                    float var3 = pipNow() && !reflexWin ? 1.0F / Math.max(1.0F, effMag()) : fovOutRad / fovInRad;
                    var2 *= 1.0F + (var3 * S.scopeSens - 1.0F) * clamp01((var0 - 0.35F) / 0.65F);
                }

                if (P.mode != 2) {
                    var2 /= steadyZoomNow();
                }

                if (!(Math.abs(var2 - 1.0F) < 1.0E-4F)) {
                    float var7 = wrap(VP.yaw() - lookY0);
                    float var4 = VP.pitch() - lookP0;
                    VP.setYaw(wrap(lookY0 + var7 * var2));
                    VP.setPitch(lookP0 + var4 * var2);
                }
            }
        }
    }

    public static void onFire(Object var0) {
        try {
            IsoPlayer var1 = IsoPlayer.players[0];
            if (var1 == null || var0 != var1) {
                return;
            }

            // ZEDGROUND: recoil strength from the Aiming skill curve (Gunplay); players cannot scale it.
            float var2 = Gunplay.recoil(var1);
            if (var2 <= 0.0F) {
                return;
            }

            if (S.rest) {
                var2 *= Rest.recoilMul(Rest.state());
            }

            float var3 = eShared;
            float var4 = 1.0F - var3;
            long var5 = System.nanoTime();
            shotsInString = var5 - lastShotNs < 350000000L ? Math.min(shotsInString + 1, 8) : 0;
            lastShotNs = var5;
            float gpSide = Gunplay.sideRamp(var5);
            Gunplay.logShot(var1, var2, var2 * gpSide);
            float var7 = 1.0F + 0.04F * shotsInString;
            float var8 = var1.isSneaking() ? 0.8F : 1.0F;
            float var9 = (float)Math.toRadians(P.gunKick) * P.recoilMul * var2 * (0.55F + 0.45F * var4);
            gKickV = gKickV + var9 / 0.027F * rnd(0.85F, 1.15F);
            gBackV = gBackV + P.gunBack * P.recoilMul * var2 / 0.027F;
            if (S.cameraRecoil) {
                float var10 = (float)Math.toRadians(P.climb) * P.kickMul * var2 * var7 * var8 * rnd(0.85F, 1.15F) * (1.0F + 0.15F * var4);
                float var11 = (float)Math.toRadians(P.side) * P.kickMul * var2 * gpSide * var8 * (rnd(-1.0F, 1.0F) * 0.75F + sideBias() * 0.6F);
                float var12 = clamp01(P.recover);
                climbP += var10 * (1.0F - var12);
                climbY += var11 * 0.7F;
                vKickV += var10 * var12 / 0.0318F;
                hKickV += var11 * 0.3F / 0.0318F;
            }
        } catch (Throwable var13) {
            Log.error("onFire", var13);
        }
    }

    private static float rnd(float var0, float var1) {
        return var0 + (float)Math.random() * (var1 - var0);
    }

    private static float sideBias() {
        int var0 = P.weaponId != null ? P.weaponId.hashCode() : 0;
        return (var0 >>> 3) % 200 / 100.0F - 1.0F;
    }

    private static void hipKick(AnimationPlayer var0, ModelInstance var1) {
        if (!(Math.abs(gKick) < 1.0E-4F) || !(Math.abs(gBack) < 1.0E-5F)) {
            if (var1 != null && weaponXfrm(var1, W)) {
                bindBones(var0);
                if (armBones.length != 0) {
                    boolean var2 = var1.model != null && Boolean.TRUE.equals(MeshGeo.flipOf(var1.model.mesh));
                    W.transformDirection(Ff.set(0.0F, 1.0F, 0.0F));
                    float var3 = Ff.length();
                    if (var3 > 1.0E-5F) {
                        Ff.normalize();
                        W.transformDirection(Ud.set(0.0F, 0.0F, var2 ? -1.0F : 1.0F)).normalize();
                        Ff.cross(Ud, Rd).normalize();
                        W.transformPosition(Pv.set(0.0F, 0.0F, 0.0F)).fma(-0.2F * var3, Ff);
                        QK.identity().rotateAxis(gKick, Rd.x, Rd.y, Rd.z);
                        A.identity()
                            .translate(Pv)
                            .rotate(QK)
                            .translate(-Pv.x, -Pv.y, -Pv.z)
                            .translate(-Ff.x * gBack * var3, -Ff.y * gBack * var3, -Ff.z * gBack * var3);

                        for (int var4 = 0; var4 < armBones.length; var4++) {
                            org.lwjgl.util.vector.Matrix4f var5 = var0.getModelTransformAt(armBones[var4]);
                            PZMath.convertMatrix(var5, TM);
                            TM.transpose();
                            A.mul(TM, TM);
                            TM.transpose();
                            PZMath.convertMatrix(TM, var5);
                        }
                    }
                }
            }
        }
    }

    private static void presenceHip(IsoPlayer var0, AnimationPlayer var1, ModelInstance var2, float var3, boolean var4, boolean var5) {
        if (!var5) {
            Presence.prepareLowReady(var2, null, false, var3);
        } else {
            bindBones(var1);
            if (armBones.length != 0 && weaponXfrm(var2, W)) {
                float var6 = var1.getRenderedAngle();
                boolean var7 = var0.isAiming();
                boolean var8 = false;
                if (var7) {
                    Presence.prepareLowReady(var2, null, false, var3);
                    if (Presence.prepare(var0, var2, W, var6, 0.0F)) {
                        Presence.applyToBones(var1, armRig, armBones, armWeight);
                    }
                } else {
                    Presence.idle();
                    var8 = Presence.prepareLowReady(var2, W, var4, var3);
                    if (var8) {
                        Presence.applyToBones(var1, armRig, armBones, armWeight);
                    }
                }
            }
        }
    }

    private static void lookEffects(float var0) {
        Gunplay.enforce(S);
        IsoPlayer gpPlayer = IsoPlayer.players[0];
        float var1 = 160.0F;
        float var2 = 22.0F;
        float var3 = 220.0F;
        float var4 = 26.0F;
        vKickV = vKickV + (-var1 * vKick - var2 * vKickV) * var0;
        vKick = vKick + vKickV * var0;
        hKickV = hKickV + (-var1 * hKick - var2 * hKickV) * var0;
        hKick = hKick + hKickV * var0;
        gKickV = gKickV + (-var3 * gKick - var4 * gKickV) * var0;
        gKick = gKick + gKickV * var0;
        gBackV = gBackV + (-var3 * gBack - var4 * gBackV) * var0;
        gBack = gBack + gBackV * var0;
        float var5 = VP.yaw();
        float var6 = VP.pitch();
        if (prevValid && var0 > 0.0F && S.inertia) {
            float var7 = wrap(var5 - prevYaw) / var0;
            float var8 = (var6 - prevPitch) / var0;
            turnRate = (float)Math.sqrt(var7 * var7 + var8 * var8);
            float var9 = clamp(-var7 * 0.012F, -0.05F, 0.05F);
            float var10 = clamp(-var8 * 0.012F, -0.04F, 0.04F);
            float var11 = 1.0F - (float)Math.exp(-var0 * 14.0F);
            lagYaw = lagYaw + (var9 - lagYaw) * var11;
            lagPitch = lagPitch + (var10 - lagPitch) * var11;
        } else {
            lagYaw = 0.0F;
            lagPitch = 0.0F;
            turnRate = 0.0F;
        }

        steadyFrame(var0);
        swayPhase += var0;
        // ZEDGROUND: stability and the steady cut come from the server's sandbox (Gunplay), not from Lua / player options.
        float var15 = S.sway ? 0.0045F * S.swayAmount * P.swayMul * Gunplay.stability(gpPlayer) * e : 0.0F;
        var15 *= (1.0F - Gunplay.steadyCut(gpPlayer) * steadyE) * (1.0F + 0.7F * gasp * gasp);
        int var18 = S.rest && e > 0.0F ? Rest.state() : 0;
        float var19 = Rest.swayCut(var18);
        restE = restE + (var19 - restE) * (1.0F - (float)Math.exp(-Math.max(0.0F, var0) / (var19 > restE ? 0.25F : 0.15F)));
        if (restE < 0.001F && var19 == 0.0F) {
            restE = 0.0F;
        }

        var15 *= 1.0F - restE;
        restShared = var18;
        swayX = var15 * ((float)Math.sin(swayPhase * 1.13F) * 0.72F + (float)Math.sin(swayPhase * 0.41F + 0.7F) * 0.28F);
        swayY = var15 * 0.65F * ((float)Math.sin(swayPhase * 1.71F + 1.3F) * 0.7F + (float)Math.sin(swayPhase * 0.57F) * 0.3F);
        float var20 = 1.0F - (float)Math.exp(-var0 / 0.05F);
        float var21 = climbP * var20;
        float var12 = climbY * var20;
        climbP -= var21;
        climbY -= var12;
        if (Math.abs(climbP) < 1.0E-6F) {
            climbP = 0.0F;
        }

        if (Math.abs(climbY) < 1.0E-6F) {
            climbY = 0.0F;
        }

        float var13 = swayX - lastSwayX + (hKick - lastHKick) + var12;
        float var14 = swayY - lastSwayY + (vKick - lastVKick) + var21;
        lastSwayX = swayX;
        lastSwayY = swayY;
        lastHKick = hKick;
        lastVKick = vKick;
        if (var13 != 0.0F || var14 != 0.0F) {
            var5 = wrap(var5 + var13);
            var6 += var14;
            VP.setYaw(var5);
            VP.setPitch(var6);
        }

        prevYaw = var5;
        prevPitch = var6;
        prevValid = true;
    }

    private static void steadyFrame(float var0) {
        boolean var1 = steadyHeld;
        if (!var1) {
            steadyLock = false;
        }

        boolean var2 = S.steady
            && S.sway
            && var1
            && !steadyLock
            && state == 2
            && t >= 1.0F
            && P.valid
            && VP.firstPerson()
            && !Presence.adsBlocked()
            && !suspended;
        boolean var3 = var2 && breath > 0.0F;
        if (var3) {
            breath -= var0 / Gunplay.holdSeconds(IsoPlayer.players[0]);
            if (breath <= 0.0F) {
                breath = 0.0F;
                steadyLock = true;
                var3 = false;
                gasp = 1.0F;
                Log.dev("steady aim: out of breath");
            }
        } else {
            breath = Math.min(1.0F, breath + var0 / Gunplay.refillSeconds(IsoPlayer.players[0]));
        }

        float var4 = var3 ? 1.0F : 0.0F;
        steadyE = steadyE + (var4 - steadyE) * (1.0F - (float)Math.exp(-Math.max(0.0F, var0) / (var3 ? 0.16F : 0.12F)));
        if (var4 == 0.0F && steadyE < 0.001F) {
            steadyE = 0.0F;
        }

        gasp = Math.max(0.0F, gasp - var0 / 1.6F);
        if (e <= 0.0F) {
            steadyE = 0.0F;
            gasp = 0.0F;
        }

        steadyShared = steadyE;
        breathShared = breath;
        if (var3 && !steadyLogged) {
            steadyLogged = true;
            Log.info(
                "steady aim: on ("
                    + (steadyZooms() ? (P.mode == 0 ? "iron sights" : "unmagnified optic") + " zoom " + S.steadyZoom + "x" : "magnified scope: sway only")
                    + ")"
            );
        }
    }

    static boolean steadyZooms() {
        return P.mode == 0 || P.mode == 1 || P.mode == 2 && P.mag < 1.9F;
    }

    static float steadyZoomNow() {
        float var0 = steadyShared;
        return var0 > 0.0F && steadyZooms() ? 1.0F + (Math.max(1.0F, S.steadyZoom) - 1.0F) * var0 : 1.0F;
    }

    private static void align(IsoPlayer var0, AnimationPlayer var1, ModelInstance var2, float var3) {
        if (!firstAlignLogged) {
            firstAlignLogged = true;
            Log.info(
                "first ADS frame: weapon="
                    + P.weaponId
                    + " model="
                    + (var2.modelScript != null ? var2.modelScript.getName() : "?")
                    + " parentBone="
                    + var2.parentBoneName
                    + " attachSelf="
                    + var2.attachmentNameSelf
                    + " attachParent="
                    + var2.attachmentNameParent
                    + " scale="
                    + var2.scale
                    + " invertSelf="
                    + ModelInstanceRenderData.invertAttachmentSelfTransform
            );
        }

        if (!weaponXfrm(var2, W)) {
            lastIssue = "weapon transform unavailable";
            Log.warnOnce("xfrm", "ADS: " + lastIssue);
            ovVisible = false;
        } else {
            bindBones(var1);
            if (armBones.length != 0 && headBone >= 0) {
                boolean var4 = modelUpsideDown(var2);
                int var5 = P.mode;
                tubeEyeR = 0.0F;
                ironFront = 0.0F;
                reflexWin = false;
                Pip.reflexMode = false;
                ModelInstance var6 = var5 == 0 ? null : findOptic(var2);
                OpticProfiles.apply(var2, var6);
                float var7 = P.opticWindow;
                if (var6 != null) {
                    ModelAttachment var8 = var2.getAttachmentById(var6.attachmentNameParent);
                    float var9 = P.opticHeight;
                    float[] var10 = var6.modelScript != null ? opticShapes.get(shortName(var6.modelScript.getName())) : null;
                    if (var10 != null) {
                        if (!P.opticHeightSet) {
                            var9 = var10[0];
                        }

                        if (var10[2] > 0.0F) {
                            var7 = var10[2];
                        }
                    }

                    float[] var11 = !(P.mag >= 1.9F) && !pipNow() ? null : opticTube(var2, var6, var8, var4);
                    if (P.mag < 1.9F && pipNow() && tubeWin != null) {
                        var11 = null;
                    }

                    float[] var12 = var11 == null && P.mag < 1.9F && pipNow() ? tubeWin : null;
                    tubeEyeR = var11 != null ? var11[6] : 0.0F;
                    Pip.meshEye = var11 != null
                        ? new float[]{var11[0], var11[1], var11[2], var11[3], var11[4], var11[5], var11[6] * 0.85F}
                        : (var12 != null ? new float[]{var12[0], var12[5], var12[2], 0.0F, 1.0F, 0.0F, var12[3] * 0.78F, var12[4] * 0.78F} : null);
                    Pip.meshPoly = var12 != null && tubeWinGlass ? tubePoly : null;
                    Pip.reflexMode = var12 != null;
                    Pip.reflexRound = var12 != null && tubeWinGlass && tubeWinRound;
                    Pip.reflexExact = var12 != null && tubeWinGlass;
                    Pip.reflexDepth = var12 != null ? Math.max(0.02F, tubeFrontY - var12[5]) : 0.0F;
                    reflexWin = var12 != null;
                    reflexHx = var12 != null ? (var12.length > 6 ? var12[6] : var12[3]) : 0.0F;
                    Pip.meshInst = var2;
                    Pip.meshObj = var2.model != null ? var2.model.mesh : null;
                    if (var11 != null) {
                        sw.set(var11[0], var11[1], var11[2]);
                        dw.set(var11[3], var11[4], var11[5]);
                        var7 = var11[6];
                    } else if (var12 != null) {
                        String var13 = var6.modelScript != null ? var6.modelScript.getName() : "?";
                        Pip.reflexKey = var13;
                        float var14 = tubeWinGlass ? 0.0F : Pip.corrFor(var13);
                        sw.set(var12[0], var12[1], var12[2] + (var4 ? -var14 : var14));
                        var7 = var12[3];
                    } else if (var8 != null) {
                        Vector3f var38 = var8.getOffset();
                        sw.set(var38.x, var38.y, var38.z + (var4 ? -var9 : var9));
                    } else {
                        sw.set(P.sx, P.sy, P.sz + var9);
                        lastIssue = "optic mount attachment '" + var6.attachmentNameParent + "' missing";
                    }

                    if (var11 == null) {
                        dw.set(0.0F, 1.0F, 0.0F);
                    }

                    ironSrc = var11 != null ? "optic measured" : "optic";
                    ironDZ = 0.0F;
                    ironDY = 0.0F;
                    ironDX = 0.0F;
                    float[] var39 = P.opticRear;
                    float[] var42 = P.opticFront;
                    if (var39 != null && var42 != null && var6.model != null) {
                        partMatrix(var6, var8, null, false, LO);
                        LO.transformPosition(tA.set(var39[0], var39[1], var39[2]));
                        LO.transformPosition(tB.set(var42[0], var42[1], var42[2]));
                        if (tB.distanceSquared(tA) > 1.0E-10F) {
                            sw.set(tA);
                            dw.set(tB).sub(tA).normalize();
                            ironSrc = "optic profile sight line";
                        }
                    }
                } else {
                    if (var5 != 0) {
                        var5 = 0;
                        lastIssue = "optic profile set but no optic model on the weapon";
                    }

                    float[] var27 = null;
                    slideInfo = null;
                    if (P.ironAuto && var2.model != null) {
                        ModelAttachment var30 = var2.getAttachmentById("muzzle");
                        ModelInstance var32 = slidePart(var2);
                        if (var32 != null) {
                            float[] var34 = slideVerts.get(var32.model.mesh);
                            if (var34 == null) {
                                ModelAttachment var36 = slideHome(var2, var32);
                                var34 = partVerts(var32, var36, slideHomeSelf(var32), true);
                                if (var34 != null) {
                                    slideVerts.put(var32.model.mesh, var34);
                                }
                            }

                            var27 = MeshGeo.ironsWith(var2.model.mesh, var32.model.mesh, var34, var30 != null ? var30.getOffset() : null, P.bead);
                            slideInfo = var32.modelScript.getName() + " at " + var32.attachmentNameParent + "/" + var32.attachmentNameSelf;
                        } else {
                            var27 = MeshGeo.irons(var2.model.mesh, var30 != null ? var30.getOffset() : null, P.bead);
                        }
                    }

                    if (var27 != null) {
                        sw.set(var27[0], var27[1], var27[2]);
                        dw.set(var27[3], var27[4], var27[5]);
                        ironFront = var27.length > 8 ? var27[8] : 0.0F;
                        ironSrc = (var27[6] > 1.5F ? "mesh rear+front" : "mesh front post") + (slideInfo != null ? " +slide " + slideInfo : "");
                        ironDX = var27[0] - P.sx;
                        ironDY = var27[1] - P.sy;
                        ironDZ = var27[2] - P.sz;
                    } else {
                        if (var4) {
                            sw.set(-P.sx, P.sy, -P.sz);
                            dw.set(-P.dx, P.dy, -P.dz);
                        } else {
                            sw.set(P.sx, P.sy, P.sz);
                            dw.set(P.dx, P.dy, P.dz);
                        }

                        ironSrc = !P.ironAuto
                            ? "manual"
                            : (var2.model != null && MeshGeo.captured(var2.model.mesh) ? "profile (mesh scan failed)" : "profile (mesh not captured)");
                        ironDZ = 0.0F;
                        ironDY = 0.0F;
                        ironDX = 0.0F;
                    }

                    if (dw.lengthSquared() < 1.0E-8F) {
                        dw.set(0.0F, 1.0F, 0.0F);
                    }
                }

                if (var5 == 0) {
                    float var28 = 0.002F + (P.bead ? 0.003F : 0.0F);
                    if (ironFront > 0.02F) {
                        tA.set(sw).fma(ironFront, dw);
                        sw.z += var4 ? -var28 : var28;
                        dw.set(tA).sub(sw).normalize();
                    } else {
                        sw.z += var4 ? -var28 : var28;
                    }
                }

                float var29 = P.adjX;
                float var31 = P.adjY;
                float var33 = P.adjZ;
                float var35 = P.adjPitch;
                float var37 = P.adjYaw;
                ironMinRelief = P.minRelief;
                if (var5 == 0 && P.ironMesh != null && !P.ironLocked && var2.modelScript != null && var2.modelScript.getMeshName() != null) {
                    String var40 = Api.meshKey(var2.modelScript.getMeshName());

                    for (int var43 = 0; var43 < P.ironMesh.length; var43++) {
                        if (P.ironMesh[var43].equals(var40)) {
                            float[] var15 = P.ironVar[var43];
                            var29 = var15[0];
                            var31 = var15[1];
                            var33 = var15[2];
                            var35 = var15[3];
                            var37 = var15[4];
                            ironMinRelief = var15.length > 5 ? var15[5] : 0.0F;
                            if (ironVarLogged != var2.modelScript) {
                                ironVarLogged = var2.modelScript;
                                Log.info("iron sights for model mesh " + var40);
                            }
                            break;
                        }
                    }
                }

                sw.add(var4 ? -var29 : var29, var31, var4 ? -var33 : var33);
                uw.set(0.0F, 0.0F, var4 ? -1.0F : 1.0F);
                if (var4) {
                    ironSrc = ironSrc + " (model top is -Z)";
                }

                if (var35 != 0.0F || var37 != 0.0F || P.adjRoll != 0.0F) {
                    Q.identity().rotateZ((float)Math.toRadians(var37)).rotateX((float)Math.toRadians(var35)).rotateY((float)Math.toRadians(P.adjRoll));
                    Q.transform(dw);
                    Q.transform(uw);
                }

                dw.normalize();
                W.transformPosition(sw, S0);
                W.transformDirection(dw, F);
                float var41 = W.transformDirection(tmp.set(0.0F, 1.0F, 0.0F)).length();
                F.normalize();
                W.transformDirection(uw, U).normalize();
                if (var41 > 1.0E-5F && Float.isFinite(S0.x)) {
                    org.lwjgl.util.vector.Matrix4f var44 = var1.getModelTransformAt(headBone);
                    float var45 = var1.getRenderedAngle();
                    viewDirCms(VP.yaw(), 0.0F, var45, tmp);
                    E.set(var44.m03, var44.m13, var44.m23).add(0.0F, 0.06666667F, 0.0F).fma(0.08F, tmp);
                    eyeOffValid = true;
                    boolean var16 = EyeTap.valid;
                    if (var16) {
                        EM[0] = EyeTap.offX;
                        EM[1] = EyeTap.offZ;
                        EM[2] = EyeTap.height;
                        EM[3] = EyeTap.lean;
                    }

                    boolean var17 = VP.movementOffsets(AM, var16 || !VP.amWritesEye);
                    leanUsedX = AM[0];
                    leanUsedY = AM[1];
                    leanUsedDrop = AM[2];
                    leanUsedValid = var17 && (var16 || !VP.amWritesEye);
                    if (!var16 && !VP.eyeState(EM)) {
                        eyeSrc = "head bone";
                        eyeShift = 0.0F;
                    } else {
                        float var18 = VP.yaw();
                        float var19 = 0.12F + EM[3];
                        float var20 = -EM[0] + AM[0] + (float)Math.cos(var18) * var19;
                        float var21 = -EM[1] + AM[1] + (float)Math.sin(var18) * var19;
                        worldToCms(var20, var21, (EM[2] - AM[2]) / 2.4494896F, var45, tA);
                        eyeShift = tA.distance(E);
                        E.set(tA);
                        eyeSrc = var16 ? "viewpoint+live lean" : "viewpoint";
                    }

                    viewDirCms(VP.yaw(), VP.pitch(), var45, D);
                    tB.set(UPV).fma(-D.dot(UPV), D).normalize();
                    D.cross(tB, tC).normalize();
                    E.fma(0.0F, tC).fma(0.0F, tB);
                    UPH.set(UPV);
                    if (AM[3] != 0.0F) {
                        float var46 = AM[3];
                        viewDirCms(VP.yaw() + (float) (Math.PI / 2), 0.0F, var45, tC);
                        tB.set(UPV).fma(-D.dot(UPV), D).normalize();
                        UPH.set(tB).mul((float)Math.cos(var46)).fma((float)Math.sin(var46), tC);
                    }

                    float var47 = P.relief * (var5 != 2 && !(P.relief >= 0.2F) ? S.reliefMul : 1.0F);
                    if (var5 == 0 && P.bead) {
                        var47 *= 0.85F;
                    }

                    if (var5 == 0 && ironMinRelief > var47) {
                        var47 = ironMinRelief;
                    }

                    if (var5 == 0 && var2.model != null) {
                        var47 = clearRelief(var2, var47);
                    }

                    if (reflexWin && reflexHx > 0.0F) {
                        float var48 = (float)Math.tan(Math.max(0.3F, fovBaseRad) * 0.5F);
                        float var51 = reflexHx / (0.22F * var48);
                        var47 = Math.max(reflexHx * 2.0F, Math.min(var47 * 1.4F, var51));
                    }

                    if (tubeEyeR > 0.0F && pipNow()) {
                        float var49 = (float)Math.tan(Math.max(0.3F, fovBaseRad) * 0.5F);
                        float var52 = tubeEyeR * 0.85F / (0.32F * var49);
                        var47 = Math.max(tubeEyeR * 1.2F, Math.min(var47, var52));
                    }

                    solve(S0, F, U, E, D, var47 * var41, e, M);
                    QK.identity().rotateAxis(gKick, Rd.x, Rd.y, Rd.z);
                    QL.identity().rotateAxis(lagYaw, Ud.x, Ud.y, Ud.z).rotateAxis(lagPitch, Rd.x, Rd.y, Rd.z);
                    QK.mul(QL);
                    float var50 = 0.3F;
                    if (reflexWin || tubeEyeR > 0.0F && pipNow()) {
                        var50 = 0.3F + (var47 - 0.3F) * e;
                    }

                    Pv.set(D).mul(-var50 * var41).add(Pt);
                    A.identity()
                        .translate(Pv)
                        .rotate(QK)
                        .translate(-Pv.x, -Pv.y, -Pv.z)
                        .translate(-D.x * gBack * var41, -D.y * gBack * var41, -D.z * gBack * var41);
                    A.mul(M, M);
                    M.mul(W, WP);
                    boolean var53 = Presence.prepare(var0, var2, WP, var45, e);
                    slopeDrop = slopeDrop(var0, var1, var45);
                    if (slopeDrop > 0.0F) {
                        M.translateLocal(0.0F, slopeDrop * e, 0.0F);
                    }

                    steadyArms(var1, var2, var3);
                    boolean var54 = lensUp && !pipNow();

                    for (int var22 = 0; var22 < armBones.length; var22++) {
                        int var23 = armBones[var22];
                        org.lwjgl.util.vector.Matrix4f var24 = var1.getModelTransformAt(var23);
                        PZMath.convertMatrix(var24, TM);
                        TM.transpose();
                        M.mul(TM, TM);
                        if (var54) {
                            TM.scale(0.0F);
                        }

                        TM.transpose();
                        PZMath.convertMatrix(TM, var24);
                    }

                    if (var53) {
                        Presence.applyToBones(var1, armRig, armBones, armWeight);
                    }

                    if (S.closeClip && e > 0.25F && VP.firstPerson()) {
                        for (int var25 : nearBodyBones) {
                            org.lwjgl.util.vector.Matrix4f var26 = var1.getModelTransformAt(var25);
                            PZMath.convertMatrix(var26, TM);
                            TM.transpose();
                            TM.scale(0.0F);
                            TM.transpose();
                            PZMath.convertMatrix(TM, var26);
                        }
                    }

                    if (slopeDrop > 0.0F) {
                        M.translateLocal(0.0F, -slopeDrop * e, 0.0F);
                    }

                    M.transformPosition(S0, Sf);
                    M.transformDirection(F, Ff).normalize();
                    SfI.set(Sf);
                    FfI.set(Ff);
                    if (var53) {
                        Matrix4f var56 = Presence.fullCorrection();
                        var56.transformPosition(Sf);
                        var56.transformDirection(Ff).normalize();
                    }

                    ovMisalign = (float)Math.toDegrees(Math.acos(Math.max(-1.0F, Math.min(1.0F, Ff.dot(D)))));
                    projectOverlay(var0, var45, var5, var7 * var41 * P.windowScale);
                    publishSight(var0, var45);
                    lastIssue = !lastIssue.startsWith("optic") && !lastIssue.startsWith("weapon") ? "" : lastIssue;
                    if (!var53) {
                        selfCheck(var0, var2, var45, var41);
                    }

                    if (Log.dev) {
                        telemetry(var0, var45, var41);
                    }
                } else {
                    lastIssue = "degenerate weapon transform";
                }
            } else {
                lastIssue = "arm/head bones not found";
                Log.warnOnce("bones", "ADS: " + lastIssue);
            }
        }
    }

    private static void steadyArms(AnimationPlayer var0, ModelInstance var1, float var2) {
        String var3 = null;
        if (var1.parent != null) {
            ModelAttachment var4 = var1.parent.getAttachmentById(var1.attachmentNameParent);
            if (var4 == null && var1.parentBoneName != null) {
                var4 = var1.parent.getAttachmentById(var1.parentBoneName);
            }

            var3 = var4 != null ? var4.getBone() : var1.parentBoneName;
        }

        int var9 = var3 != null ? var0.getSkinningBoneIndex(var3, -1) : -1;
        if (var9 >= 0 && !(e < 0.05F) && armBones.length != 0) {
            if (fq.length != armBones.length) {
                fq = new Quaternionf[armBones.length];
                fv = new Vector3f[armBones.length];

                for (int var5 = 0; var5 < fq.length; var5++) {
                    fq[var5] = new Quaternionf();
                    fv[var5] = new Vector3f();
                }

                armFilt = false;
            }

            PZMath.convertMatrix(var0.getModelTransformAt(var9), AN);
            AN.transpose();
            AN.invert(ANI);
            float var10 = armFilt ? 1.0F - (float)Math.exp(-Math.max(0.0F, var2) / 0.15F) : 1.0F;

            for (int var6 = 0; var6 < armBones.length; var6++) {
                int var7 = armBones[var6];
                if (var7 != var9 && !armIsProp[var6]) {
                    org.lwjgl.util.vector.Matrix4f var8 = var0.getModelTransformAt(var7);
                    PZMath.convertMatrix(var8, TM);
                    TM.transpose();
                    ANI.mul(TM, REL);
                    REL.getScale(RS);
                    REL.getNormalizedRotation(RQ);
                    REL.getTranslation(RV);
                    if (var10 >= 1.0F) {
                        fq[var6].set(RQ);
                        fv[var6].set(RV);
                    } else {
                        fq[var6].slerp(RQ, var10).normalize();
                        fv[var6].lerp(RV, var10);
                    }

                    REL.translationRotate(fv[var6].x, fv[var6].y, fv[var6].z, fq[var6]).scale(RS);
                    AN.mul(REL, TM);
                    TM.transpose();
                    PZMath.convertMatrix(TM, var8);
                }
            }

            armFilt = true;
        } else {
            armFilt = false;
        }
    }

    private static void publishSight(IsoPlayer var0, float var1) {
        iv.set(SfI.x, SfI.y, SfI.z);
        Model.vectorToWorldCoords(var0.getX(), var0.getY(), var0.getZ(), var1, iv);
        double var2 = iv.x;
        double var4 = iv.y;
        double var6 = iv.z * 2.4494896F;
        iv.set(FfI.x, FfI.y, FfI.z);
        Model.vectorToWorldCoords(0.0F, 0.0F, 0.0F, var1, iv);
        Sight.publish(var2, var4, var6, iv.x, iv.y, iv.z * 2.4494896F, zeroNow(), e * (1.0F - Presence.viewBlock()));
    }

    private static void selfCheck(IsoPlayer var0, ModelInstance var1, float var2, float var3) {
        if (!(e < 0.98F) && !lensUp) {
            long var4 = System.nanoTime();
            long var6 = Log.dev ? 500000000L : 3000000000L;
            if (var4 - lastSelfCheckNs >= var6 && (Log.dev || selfChecks < 15)) {
                lastSelfCheckNs = var4;
                selfChecks++;
                if (!weaponXfrm(var1, W2)) {
                    Log.info("CHECK weapon transform unavailable after edit");
                } else {
                    W2.transformDirection(dw, c1).normalize();
                    W2.transformPosition(sw, c2);
                    float var8 = (float)Math.toDegrees(Math.acos(clamp(c1.dot(D), -1.0F, 1.0F)));
                    float var9 = c2.distance(Pt);
                    float var10 = VP.yaw();
                    float var11 = VP.pitch();
                    float var12 = (float)Math.cos(var11);
                    c3.set(-((float)Math.cos(var10)) * var12, (float)Math.sin(var11), -((float)Math.sin(var10)) * var12);
                    cmsVecToRender(tA.set(c2).sub(E), var2, tA);
                    float var13 = tA.length();
                    float var14 = var13 > 1.0E-6F ? (float)Math.toDegrees(Math.acos(clamp(tA.dot(c3) / var13, -1.0F, 1.0F))) : -1.0F;
                    float var15 = (float)Math.toDegrees(Math.acos(clamp(F.dot(D), -1.0F, 1.0F)));
                    tB.set(E);
                    iv.set(tB.x, tB.y, tB.z);
                    Model.vectorToWorldCoords(var0.getX(), var0.getY(), var0.getZ(), var2, iv);
                    Log.info(
                        String.format(
                            "CHECK %s mode=%d irons=%s (vs offline %+.4f,%+.4f,%+.4f) aimPicksDropped=%d eye=%s (moved %.4f from head bone) lean=(%.3f,%.3f,%.3f) roll=%.1f bones=%d s=%.3f | gun axis vs view: before=%.1f after=%.2f deg | sight pos err=%.4f | sight from eye: %.3f at %.2f deg off-axis | fov %.2f->%.2f | eyeWorld=(%.3f,%.3f,%.3f) vpEyeR=(%.3f,%.3f,%.3f) vpCam=(%.3f,%.3f,%.3f) pl=(%.3f,%.3f,%.3f)",
                            P.weaponId,
                            P.mode,
                            ironSrc,
                            ironDX,
                            ironDY,
                            ironDZ,
                            AimPick.dropped,
                            eyeSrc,
                            eyeShift,
                            AM[0],
                            AM[1],
                            AM[2],
                            Math.toDegrees(AM[3]),
                            armBones.length,
                            var3,
                            var15,
                            var8,
                            var9,
                            var13,
                            var14,
                            fovIn,
                            fovOut,
                            iv.x,
                            iv.y,
                            iv.z,
                            eyeRX,
                            eyeRY,
                            eyeRZ,
                            camX,
                            camY,
                            camZ,
                            var0.getX(),
                            var0.getY(),
                            var0.getZ()
                        )
                    );
                }
            }
        }
    }

    private static void telemetry(IsoPlayer var0, float var1, float var2) {
        long var3 = System.nanoTime();
        if (var3 - lastTelemetryNs >= 500000000L) {
            lastTelemetryNs = var3;
            float var5 = VP.yaw();
            float var6 = VP.pitch();
            float var7 = (float)Math.cos(var6);
            tA.set(-((float)Math.cos(var5)) * var7, (float)Math.sin(var6), -((float)Math.sin(var5)) * var7);
            cmsDirToRender(Ff, var1, tB);
            cmsToRender(Sf, var0, var1, tC);
            tC.sub(eyeRX, eyeRY, eyeRZ);
            float var8 = tC.length();
            float var9 = var8 > 1.0E-6F ? (float)Math.toDegrees(Math.acos(clamp(tC.dot(tA) / var8, -1.0F, 1.0F))) : -1.0F;
            float var10 = (float)Math.toDegrees(Math.acos(clamp(tB.dot(tA), -1.0F, 1.0F)));
            float var11 = (float)Math.toDegrees(Math.acos(clamp(F.dot(D), -1.0F, 1.0F)));
            Log.dev(
                String.format(
                    "TEL e=%.2f mode=%d s=%.3f relief=%.3f | gunAxis vs view: before=%.1fdeg after(cms)=%.2fdeg after(render)=%.2fdeg | sight from eye: dist=%.3f offAxis=%.2fdeg | dot=(%.0f,%.0f) lens=(%.0f,%.0f) | ang=%.3f yaw=%.3f pitch=%.3f | slopeDrop=%.4f",
                    e,
                    P.mode,
                    var2,
                    P.relief * var2,
                    var11,
                    ovMisalign,
                    var10,
                    var8,
                    var9,
                    ovDotX,
                    ovDotY,
                    ovLensX,
                    ovLensY,
                    var1,
                    var5,
                    var6,
                    slopeDrop
                )
            );
            Log.dev(
                String.format(
                    "TEL F=(%.3f,%.3f,%.3f) U=(%.3f,%.3f,%.3f) D=(%.3f,%.3f,%.3f) S0=(%.3f,%.3f,%.3f) E=(%.3f,%.3f,%.3f) eyeOff=(%.3f,%.3f,%.3f) valid=%b | eyeR=(%.3f,%.3f,%.3f) cam=(%.2f,%.2f,%.2f) pl=(%.2f,%.2f,%.2f)",
                    F.x,
                    F.y,
                    F.z,
                    U.x,
                    U.y,
                    U.z,
                    D.x,
                    D.y,
                    D.z,
                    S0.x,
                    S0.y,
                    S0.z,
                    E.x,
                    E.y,
                    E.z,
                    eyeOff.x,
                    eyeOff.y,
                    eyeOff.z,
                    eyeOffValid,
                    eyeRX,
                    eyeRY,
                    eyeRZ,
                    camX,
                    camY,
                    camZ,
                    var0.getX(),
                    var0.getY(),
                    var0.getZ()
                )
            );
        }
    }

    static void solve(Vector3f var0, Vector3f var1, Vector3f var2, Vector3f var3, Vector3f var4, float var5, float var6, Matrix4f var7) {
        Ud.set(UPH).fma(-var4.dot(UPH), var4);
        if (Ud.lengthSquared() < 1.0E-6F) {
            Ud.set(0.0F, 0.0F, 1.0F).fma(-var4.z, var4);
        }

        Ud.normalize();
        var4.cross(Ud, Rd).normalize();
        Uo.set(var2).fma(-var1.dot(var2), var1);
        if (Uo.lengthSquared() < 1.0E-6F) {
            Uo.set(Ud);
        }

        Uo.normalize();
        var1.cross(Uo, Ro).normalize();
        BS.set(var1, Uo, Ro).transpose();
        BD.set(var4, Ud, Rd);
        BD.mul(BS, R3);
        Q.setFromNormalized(R3).normalize();
        QT.identity().slerp(Q, var6);
        Pt.set(var4).mul(var5).add(var3);
        Pt.sub(var0).mul(var6).add(var0);
        var7.identity().translate(Pt).rotate(QT).translate(-var0.x, -var0.y, -var0.z);
    }

    static boolean weaponXfrm(ModelInstance var0, Matrix4f var1) {
        return weaponXfrm(var0, var1, A);
    }

    static boolean weaponXfrm(ModelInstance var0, Matrix4f var1, Matrix4f var2) {
        ModelInstance var3 = var0.parent;
        if (var3 != null && var3.animPlayer != null) {
            var1.identity();
            ModelAttachment var4 = var3.getAttachmentById(var0.attachmentNameParent);
            if (var4 == null && var0.parentBoneName != null) {
                var4 = var3.getAttachmentById(var0.parentBoneName);
            }

            if (var4 == null) {
                if (var0.parentBoneName == null) {
                    return false;
                }

                ModelInstanceRenderData.applyBoneTransform(var3, var0.parentBoneName, var1);
            } else {
                ModelInstanceRenderData.applyBoneTransform(var3, var4.getBone(), var1);
                ModelInstanceRenderData.makeAttachmentTransform(var4, var2);
                var1.mul(var2);
            }

            ModelAttachment var5 = var0.getAttachmentById(var0.attachmentNameSelf);
            if (var5 == null && var0.parentBoneName != null) {
                var5 = var0.getAttachmentById(var0.parentBoneName);
            }

            if (var5 != null) {
                ModelInstanceRenderData.makeAttachmentTransform(var5, var2);
                if (ModelInstanceRenderData.invertAttachmentSelfTransform) {
                    var2.invert();
                }

                var1.mul(var2);
            }

            if (var0.model != null) {
                ModelInstanceRenderData.postMultiplyMeshTransform(var1, var0.model.mesh);
            }

            float var6 = var0 == hiddenInst ? hiddenScale : var0.scale;
            if (var6 != 1.0F) {
                var1.scale(var6);
            }

            return true;
        } else {
            return false;
        }
    }

    static float[] partVerts(ModelInstance var0, ModelAttachment var1) {
        return partVerts(var0, var1, null, false);
    }

    static float[] partVerts(ModelInstance var0, ModelAttachment var1, ModelAttachment var2, boolean var3) {
        float[] var4 = var0.model != null ? MeshGeo.verts(var0.model.mesh) : null;
        if (var4 == null) {
            return null;
        }

        partMatrix(var0, var1, var2, var3, LO);
        int var5 = var4.length / 3;
        float[] var6 = new float[var4.length];

        for (int var7 = 0; var7 < var5; var7++) {
            LO.transformPosition(tv.set(var4[var7 * 3], var4[var7 * 3 + 1], var4[var7 * 3 + 2]));
            var6[var7 * 3] = tv.x;
            var6[var7 * 3 + 1] = tv.y;
            var6[var7 * 3 + 2] = tv.z;
        }

        return var6;
    }

    static Matrix4f partMatrix(ModelInstance var0, ModelAttachment var1, ModelAttachment var2, boolean var3, Matrix4f var4) {
        Matrix4f var5 = var4;
        var5.identity();
        if (var1 != null) {
            ModelInstanceRenderData.makeAttachmentTransform(var1, LA);
            var5.mul(LA);
        }

        ModelAttachment var6 = var3 ? var2 : var0.getAttachmentById(var0.attachmentNameSelf);
        if (var6 == null && !var3 && var0.parentBoneName != null) {
            var6 = var0.getAttachmentById(var0.parentBoneName);
        }

        if (var6 != null) {
            ModelInstanceRenderData.makeAttachmentTransform(var6, LA);
            if (ModelInstanceRenderData.invertAttachmentSelfTransform) {
                LA.invert();
            }

            var5.mul(LA);
        }

        if (var0.model != null) {
            ModelInstanceRenderData.postMultiplyMeshTransform(var5, var0.model.mesh);
        }

        if (var0.scale != 0.0F && var0.scale != 1.0F) {
            var5.scale(var0.scale);
        }

        return var5;
    }

    static ModelAttachment slideHome(ModelInstance var0, ModelInstance var1) {
        String var2 = var1.attachmentNameParent;
        if (var2 == null) {
            return null;
        }

        if (!var2.toLowerCase(Locale.ROOT).contains("fire")) {
            return var0.getAttachmentById(var2);
        }

        String[] var3 = new String[]{var2.replaceAll("(?i)fired?", "Lock"), var2.replaceAll("(?i)fired?", "lock"), "slide_Lock", "slide_lock"};
        ModelAttachment var4 = null;

        for (String var8 : var3) {
            var4 = var0.getAttachmentById(var8);
            if (var4 != null) {
                break;
            }
        }

        if (slideHomeLogged != var0.modelScript) {
            slideHomeLogged = var0.modelScript;
            Log.info("iron sights: slide back (" + var2 + "): scanned in battery" + (var4 != null ? " at its lock attachment" : " where it is modelled"));
        }

        return var4;
    }

    static ModelAttachment slideHomeSelf(ModelInstance var0) {
        String var1 = var0.attachmentNameSelf;
        if (var1 == null) {
            return null;
        }

        if (!var1.toLowerCase(Locale.ROOT).contains("fire")) {
            return var0.getAttachmentById(var1);
        }

        String[] var2 = new String[]{var1.replaceAll("(?i)fired?", "Lock"), var1.replaceAll("(?i)fired?", "lock"), "slide_Lock", "slide_lock"};

        for (String var6 : var2) {
            ModelAttachment var7 = var0.getAttachmentById(var6);
            if (var7 != null) {
                return var7;
            }
        }

        return null;
    }

    static float clearRelief(ModelInstance var0, float var1) {
        float[] var2 = MeshGeo.verts(var0.model.mesh);
        if (var2 != null && var1 > 0.0F) {
            float[] var3 = null;
            ModelInstance var4 = slidePart(var0);
            if (var4 != null) {
                var3 = slideVerts.get(var4.model.mesh);
            }

            if (clearMesh == var0.model.mesh
                && Math.abs(clearIn - var1) < 1.0E-4F
                && clearSw.distanceSquared(sw) < 1.0E-10F
                && clearDw.distanceSquared(dw) < 1.0E-8F) {
                return clearOut;
            }

            cU.set(uw).fma(-uw.dot(dw), dw).normalize();
            cR.set(dw).cross(cU).normalize();
            float var5 = var1;
            float var6 = nearestAt(var2, var3, var1);
            if (var6 < 0.05F) {
                float var7 = var6;

                for (int var8 = 1; var8 <= 40; var8++) {
                    float var9 = 0.005F * var8;

                    for (byte var10 = 1; var10 >= -1; var10 -= 2) {
                        float var11 = var1 + var10 * var9;
                        if (!(var11 < 0.06F) && !(var11 > 0.42F)) {
                            float var12 = nearestAt(var2, var3, var11);
                            if (var12 >= 0.05F) {
                                var5 = var11;
                                var6 = var12;
                                var8 = 99;
                                break;
                            }

                            if (var12 > var7) {
                                var7 = var12;
                                var5 = var11;
                                var6 = var12;
                            }
                        }
                    }
                }

                if (clearMesh != var0.model.mesh) {
                    Log.info(
                        String.format(
                            Locale.ROOT,
                            "iron sights: %s clipped %.1f cm from the eye at %.3f; eye at %.3f (%.1f cm clear)",
                            var0.modelScript != null ? var0.modelScript.getName() : "?",
                            100.0F * nearestAt(var2, var3, var1),
                            var1,
                            var5,
                            100.0F * var6
                        )
                    );
                }
            }

            clearMesh = var0.model.mesh;
            clearIn = var1;
            clearOut = var5;
            clearNear = var6;
            clearSw.set(sw);
            clearDw.set(dw);
            return var5;
        } else {
            return var1;
        }
    }

    private static float nearestAt(float[] var0, float[] var1, float var2) {
        cE.set(sw).fma(-var2, dw);
        float var3 = nearestIn(var0, Float.MAX_VALUE);
        return var1 != null ? nearestIn(var1, var3) : var3;
    }

    private static float nearestIn(float[] var0, float var1) {
        for (byte var2 = 0; var2 + 2 < var0.length; var2 += 3) {
            cT.set(var0[var2], var0[var2 + 1], var0[var2 + 2]).sub(cE);
            float var3 = cT.dot(dw);
            if (!(var3 < 0.003F) && !(var3 > 0.25F) && !(var3 >= var1)) {
                float var4 = cT.dot(cR);
                float var5 = cT.dot(cU);
                if (!(Math.abs(var4) > 0.4F * var3 + 0.008F) && !(var5 > 0.01F) && !(var5 < -(0.4F * var3 + 0.01F))) {
                    var1 = var3;
                }
            }
        }

        return var1;
    }

    static ModelInstance slidePart(ModelInstance var0) {
        for (int var1 = 0; var1 < var0.sub.size(); var1++) {
            ModelInstance var2 = (ModelInstance)var0.sub.get(var1);
            if (var2 != null
                && var2.modelScript != null
                && var2.model != null
                && var2.modelScript.getName().toUpperCase(Locale.ROOT).contains("SLIDE")
                && MeshGeo.captured(var2.model.mesh)) {
                return var2;
            }
        }

        return null;
    }

    static float pip(int var0) {
        ADS.Profile var1 = P;
        int var2 = glassVarIdx;
        if (!var1.pipLocked && var1.glassVarPip != null && var2 >= 0 && var2 < var1.glassVarPip.length) {
            float var3 = var0 < var1.glassVarPip[var2].length ? var1.glassVarPip[var2][var0] : Float.NaN;
            if (!Float.isNaN(var3)) {
                return var3;
            }
        }

        return var0 == 0 ? var1.pipScale : (var0 == 1 ? var1.pipX : (var0 == 2 ? var1.pipY : (var0 == 3 ? var1.pipCorner : var1.pipAspect)));
    }

    static float[] glassWindow(float[] var0, float[] var1) {
        float var2 = Float.MAX_VALUE;
        float var3 = -Float.MAX_VALUE;
        float var4 = Float.MAX_VALUE;
        float var5 = Float.MAX_VALUE;
        float var6 = -Float.MAX_VALUE;

        for (byte var7 = 0; var7 + 2 < var1.length; var7 += 3) {
            var2 = Math.min(var2, var1[var7]);
            var3 = Math.max(var3, var1[var7]);
            var4 = Math.min(var4, var1[var7 + 1]);
            var5 = Math.min(var5, var1[var7 + 2]);
            var6 = Math.max(var6, var1[var7 + 2]);
        }

        for (int var21 = 0; var21 < 2; var21++) {
            float var8 = var21 == 0 ? -1.0F : 1.0F;
            float var9 = Float.MAX_VALUE;
            float var10 = -Float.MAX_VALUE;
            float var11 = 0.0F;
            float var12 = Float.MAX_VALUE;
            float var13 = -Float.MAX_VALUE;

            for (int var14 = 0; var14 < 4; var14++) {
                LO.transformPosition(tv.set((var14 & 1) == 0 ? var0[0] : var0[1], var8 * var0[4], var8 * ((var14 & 2) == 0 ? var0[2] : var0[3])));
                var9 = Math.min(var9, tv.x);
                var10 = Math.max(var10, tv.x);
                var12 = Math.min(var12, tv.z);
                var13 = Math.max(var13, tv.z);
                var11 += tv.y * 0.25F;
            }

            float var22 = 0.5F * (var9 + var10);
            float var15 = 0.5F * (var12 + var13);
            float var16 = 0.002F;
            if (var10 - var9 > 0.002F
                && var13 - var12 > 0.002F
                && !(var22 < var2 - var16)
                && !(var22 > var3 + var16)
                && !(var12 < var5 - var16)
                && !(var13 > var6 + var16)) {
                tubeWinRound = var0.length > 5 && var0[5] > 0.5F;
                tubeWinGlass = true;
                glassPass = var21;
                Log.info(
                    String.format(
                        Locale.ROOT,
                        "sight glass from profile: centre (%.4f,%.4f) half %.4f x %.4f%s (sight box x %.4f..%.4f z %.4f..%.4f)",
                        var22,
                        var15,
                        0.5F * (var10 - var9),
                        0.5F * (var13 - var12),
                        var21 == 0 ? "" : " [file axes]",
                        var2,
                        var3,
                        var5,
                        var6
                    )
                );
                float var17 = Math.max(0.5F * (var10 - var9), 0.4F * (var3 - var2));
                float var18 = 0.5F * (var10 - var9);
                float var19 = 0.5F * (var13 - var12);
                if (tubeWinRound) {
                    float var20 = 0.5F * (var18 + var19);
                    var18 = var20;
                    var19 = var20;
                }

                return new float[]{var22, Math.min(var4, var11), var15, var18, var19, var11, var17};
            }
        }

        Log.warnOnce("glass", "ADS: sight glass from the profile does not fit the loaded sight model; using its box instead");
        return null;
    }

    static float[] opticTube(ModelInstance var0, ModelInstance var1, ModelAttachment var2, boolean var3) {
        if (var1 == tubeInst) {
            return tubeVal;
        }

        tubeInst = var1;
        tubeVal = null;
        tubeWin = null;
        tubePoly = null;
        tubeWinGlass = false;
        tubeWinRound = false;
        glassVarIdx = -1;

        try {
            if (var2 == null) {
                return null;
            }

            float[] var4 = partVerts(var1, var2);
            if (var4 == null) {
                tubeInst = null;
                return null;
            }

            tubeVal = MeshGeo.tube(var4);
            tubeFrontY = -Float.MAX_VALUE;

            for (byte var5 = 1; var5 < var4.length; var5 += 3) {
                tubeFrontY = Math.max(tubeFrontY, var4[var5]);
            }

            float[] var11 = P.glass;
            if (P.glassMesh != null && var1.modelScript != null && var1.modelScript.getMeshName() != null) {
                String var6 = Api.meshKey(var1.modelScript.getMeshName());

                for (int var7 = 0; var7 < P.glassMesh.length; var7++) {
                    if (P.glassMesh[var7].equals(var6)) {
                        var11 = P.glassVar[var7];
                        glassVarIdx = var7;
                        Log.info("sight glass for model mesh " + var6);
                        break;
                    }
                }
            }

            glassPass = -1;
            tubePoly = null;
            tubeWin = var11 != null ? glassWindow(var11, var4) : null;
            if (tubeWin != null && var11 != null && glassPass >= 0) {
                float[] var12 = glassVarIdx >= 0 && P.glassVarPoly != null && glassVarIdx < P.glassVarPoly.length && P.glassVarPoly[glassVarIdx] != null
                    ? P.glassVarPoly[glassVarIdx]
                    : P.glassPoly;
                if (var12 != null) {
                    float var14 = glassPass == 0 ? -1.0F : 1.0F;
                    float[] var8 = new float[var12.length / 2 * 3];

                    for (int var9 = 0; var9 < var12.length / 2; var9++) {
                        LO.transformPosition(tv.set(var12[var9 * 2], var14 * var11[4], var14 * var12[var9 * 2 + 1]));
                        var8[var9 * 3] = tv.x;
                        var8[var9 * 3 + 1] = tv.y;
                        var8[var9 * 3 + 2] = tv.z;
                    }

                    tubePoly = var8;
                    Log.info("sight glass outline: " + var12.length / 2 + " points");
                }
            }

            if (tubeWin == null) {
                tubeWin = MeshGeo.window(var4, var3);
            }

            if (tubeWin != null) {
                Log.info(
                    String.format(
                        Locale.ROOT,
                        "sight window measured: %s -> centre (%.4f,%.4f,%.4f) half %.4f x %.4f",
                        var1.modelScript != null ? var1.modelScript.getName() : "?",
                        tubeWin[0],
                        tubeWin[5],
                        tubeWin[2],
                        tubeWin[3],
                        tubeWin[4]
                    )
                );
            }

            String var13 = (var1.modelScript != null ? var1.modelScript.getName() : "?") + (tubeVal == null ? "" : Arrays.toString(tubeVal));
            if (!var13.equals(tubeLogKey)) {
                tubeLogKey = var13;
                Log.info(
                    "scope measured: "
                        + (var1.modelScript != null ? var1.modelScript.getName() : "?")
                        + " -> "
                        + (
                            tubeVal == null
                                ? "not a tube (using the profile height)"
                                : String.format(
                                    Locale.ROOT,
                                    "eyepiece (%.4f,%.4f,%.4f) axis (%.3f,%.3f,%.3f) eyecup r=%.4f length %.3f, mount (%.4f,%.4f,%.4f)",
                                    tubeVal[0],
                                    tubeVal[1],
                                    tubeVal[2],
                                    tubeVal[3],
                                    tubeVal[4],
                                    tubeVal[5],
                                    tubeVal[6],
                                    tubeVal[7],
                                    var2.getOffset().x,
                                    var2.getOffset().y,
                                    var2.getOffset().z
                                )
                        )
                );
            }
        } catch (Throwable var10) {
            Log.error("scope measure", var10);
            tubeVal = null;
        }

        return tubeVal;
    }

    static boolean modelUpsideDown(ModelInstance var0) {
        ModelMesh var1 = var0.model != null ? var0.model.mesh : null;
        if (var1 == null) {
            return false;
        }

        Boolean var2 = MeshGeo.flipOf(var1);
        if (var2 != null) {
            return var2;
        }

        String var3 = null;
        ModelAttachment var4 = var0.getAttachmentById("muzzle");
        float[] var5 = MeshGeo.verts(var1);
        Boolean var6 = MeshGeo.shapeFlipShort(var5, var4 != null ? var4.getOffset() : null);
        if (var6 != null) {
            var2 = var6;
            var3 = "handgun shape";
        }

        if (var2 == null && e < 0.3F) {
            W.transformDirection(tv.set(0.0F, 0.0F, 1.0F));
            W.transformDirection(tA.set(0.0F, 1.0F, 0.0F));
            float var7 = tv.length();
            float var8 = tA.length();
            if (var7 > 1.0E-6F && var8 > 1.0E-6F && Math.abs(tv.y) > 0.75F * var7 && Math.abs(tA.y) < 0.5F * var8) {
                boolean var9 = tv.y < 0.0F;
                if (flipVoteMesh != var1 || flipVoteDown != var9) {
                    flipVoteMesh = var1;
                    flipVoteDown = var9;
                    flipVotes = 0;
                }

                Boolean var10 = var5 != null ? MeshGeo.guessFlipV(var5, var4 != null ? var4.getOffset() : null) : null;
                int var11 = var10 != null && var10 != var9 ? 30 : 6;
                if (++flipVotes >= var11) {
                    var2 = var9;
                    var3 = "held pose, " + var11 + " frames" + (var11 == 30 ? ", against its shape" : "");
                }
            }
        }

        if (var2 == null) {
            return false;
        }

        MeshGeo.setFlip(var1, var2);
        Log.info(
            "weapon model "
                + (var0.modelScript != null ? var0.modelScript.getName() : "?")
                + ": top is "
                + (var2 ? "-Z (upside down in its file; turned over)" : "+Z")
                + " (from "
                + var3
                + ")"
        );
        return var2;
    }

    static ModelInstance findOptic(ModelInstance var0) {
        ModelInstance var1 = null;

        for (int var2 = 0; var2 < var0.sub.size(); var2++) {
            ModelInstance var3 = (ModelInstance)var0.sub.get(var2);
            if (var3 != null) {
                if (var3.modelScript != null && opticShapes.containsKey(shortName(var3.modelScript.getName()))) {
                    return var3;
                }

                String var4 = var3.attachmentNameParent;
                if (var4 != null) {
                    String var5 = var4.toLowerCase();
                    if (var5.startsWith("scope") || var5.startsWith("reddot") || var5.contains("sight") || var5.contains("optic")) {
                        var1 = var3;
                    }
                }
            }
        }

        return var1;
    }

    private static void bindBones(AnimationPlayer var0) {
        SkinningData var1 = var0.getSkinningData();
        if (var1 == null) {
            armBones = new int[0];
        } else if (var1 != boundData) {
            boundData = var1;
            headBone = var0.getSkinningBoneIndex("Bip01_Head", -1);
            String[] var2 = new String[]{"Bip01_L_Clavicle", "Bip01_R_Clavicle", "Bip01_Prop1", "Bip01_Prop2"};
            int[] var3 = new int[var2.length];

            for (int var4 = 0; var4 < var3.length; var4++) {
                var3[var4] = var0.getSkinningBoneIndex(var2[var4], -1);
            }

            int var15 = var1.numBones();
            int[] var5 = new int[var15];
            int var6 = 0;

            for (int var7 = 0; var7 < var15; var7++) {
                SkinningBone var8 = var1.getBoneAt(var7);

                for (SkinningBone var9 = var8; var9 != null; var9 = var9.parent) {
                    boolean var10 = false;

                    for (int var14 : var3) {
                        if (var14 >= 0 && var9.index == var14) {
                            var10 = true;
                            break;
                        }
                    }

                    if (var10) {
                        var5[var6++] = var7;
                        break;
                    }
                }
            }

            armBones = Arrays.copyOf(var5, var6);
            String[] var16 = new String[]{"Bip01_Neck", "Bip01_Spine1", "Bip01_Spine2", "Bip01_L_Clavicle", "Bip01_R_Clavicle"};
            int[] var17 = new int[var16.length];
            int var18 = 0;

            for (String var25 : var16) {
                int var26 = var0.getSkinningBoneIndex(var25, -1);
                if (var26 >= 0) {
                    var17[var18++] = var26;
                }
            }

            nearBodyBones = Arrays.copyOf(var17, var18);
            armIsProp = new boolean[var6];

            for (int var20 = 0; var20 < var6; var20++) {
                for (SkinningBone var23 = var1.getBoneAt(armBones[var20]); var23 != null; var23 = var23.parent) {
                    if (var3[2] >= 0 && var23.index == var3[2] || var3[3] >= 0 && var23.index == var3[3]) {
                        armIsProp[var20] = true;
                        break;
                    }
                }
            }

            armWeight = new float[var6];

            for (int var21 = 0; var21 < var6; var21++) {
                armWeight[var21] = armIsProp[var21] ? 1.0F : presenceWeight(var1.getBoneAt(armBones[var21]).name);
            }

            armRig = ArmRig.build(var0, armBones);
            if (!armRig.complete) {
                Log.warnOnce("armrig", "ADS: arm joints (upper arm / forearm / hand) not all found - the weapon pose blends along the arms instead");
            }

            Log.dev("bound " + var6 + " arm/prop bones, head=" + headBone + ", arm rig " + (armRig.complete ? "complete" : "incomplete"));
        }
    }

    static float presenceWeight(String var0) {
        if (var0 == null) {
            return 1.0F;
        } else {
            String var1 = var0.toLowerCase(Locale.ROOT);
            if (var1.contains("clavicle")) {
                return 0.15F;
            } else if (var1.contains("upperarm")) {
                return 0.45F;
            } else {
                return !var1.contains("forearm") && !var1.contains("foretwist") ? 1.0F : 0.85F;
            }
        }
    }

    static void screenOfCms(Vector3f var0, IsoPlayer var1, float var2, float[] var3, int var4) {
        int var5 = Core.getInstance().getScreenWidth();
        int var6 = Core.getInstance().getScreenHeight();
        float var7 = VP.yaw();
        float var8 = VP.pitch();
        float var9 = (float)Math.cos(var8);
        dF.set(-((float)Math.cos(var7)) * var9, (float)Math.sin(var8), -((float)Math.sin(var7)) * var9);
        dF.cross(0.0F, 1.0F, 0.0F, dR).normalize();
        dR.cross(dF, dU).normalize();
        cmsToRender(var0, var1, var2, dP);
        dP.sub(eyeRX, eyeRY, eyeRZ);
        float var10 = dP.dot(dF);
        float var11 = fovOut > 3.2F ? (float)Math.toRadians(fovOut) : fovOut;
        float var12 = (float)Math.tan(Math.max(0.2F, var11) * 0.5F);
        float var13 = (float)var5 / Math.max(1, var6);
        if (var10 <= 1.0E-4F) {
            var3[var4] = -9999.0F;
            var3[var4 + 1] = -9999.0F;
        } else {
            var3[var4] = var5 * 0.5F * (1.0F + dP.dot(dR) / var10 / (var12 * var13));
            var3[var4 + 1] = var6 * 0.5F * (1.0F - dP.dot(dU) / var10 / var12);
        }
    }

    static float slopeDrop(IsoPlayer var0, AnimationPlayer var1, float var2) {
        if (VP.ok && var0.getVehicle() == null) {
            Object var3 = VP.relief();
            if (var3 == null) {
                return 0.0F;
            }

            float var4 = var0.getX();
            float var5 = var0.getY();
            float var6 = VP.reliefScene(var3, var4, var5);
            if (!Float.isFinite(var6)) {
                return 0.0F;
            }

            float var7 = 0.0F;

            for (int var8 = 0; var8 < 2; var8++) {
                int var9 = var1.getSkinningBoneIndex(FEET[var8], -1);
                if (var9 < 0 || var1.getSkinningBoneIndex(THIGHS[var8], -1) < 0) {
                    return 0.0F;
                }

                org.lwjgl.util.vector.Matrix4f var10 = var1.getModelTransformAt(var9);
                footV.set(var10.m03, var10.m13, var10.m23);
                Model.vectorToWorldCoords(var4, var5, var0.getZ(), var2, footV);
                float var11 = VP.reliefScene(var3, footV.x, footV.y);
                if (!Float.isFinite(var11)) {
                    return 0.0F;
                }

                float var12 = Math.max(-0.5F, Math.min(0.5F, (var11 - var6) / 1.5F));
                var7 = Math.min(var7, var12);
            }

            return -var7;
        } else {
            return 0.0F;
        }
    }

    static void worldToCms(float var0, float var1, float var2, float var3, Vector3f var4) {
        iv.set(var0 / 1.5F, var2 / 0.61237234F, var1 / 1.5F);
        iv.rotatey(-var3);
        var4.set(-iv.x, iv.y, iv.z);
    }

    static void worldToCmsSafe(float var0, float var1, float var2, float var3, Vector3f var4, Vector3 var5) {
        var5.set(var0 / 1.5F, var2 / 0.61237234F, var1 / 1.5F);
        var5.rotatey(-var3);
        var4.set(-var5.x, var5.y, var5.z);
    }

    static void cmsToRender(Vector3f var0, IsoPlayer var1, float var2, Vector3f var3) {
        iv.set(var0.x, var0.y, var0.z);
        Model.vectorToWorldCoords(var1.getX(), var1.getY(), var1.getZ(), var2, iv);
        var3.set(camX - iv.x, (iv.z - camZ) * 2.4494896F, camY - iv.y);
    }

    static void cmsVecToRender(Vector3f var0, float var1, Vector3f var2) {
        iv.set(var0.x, var0.y, var0.z);
        Model.vectorToWorldCoords(0.0F, 0.0F, 0.0F, var1, iv);
        var2.set(-iv.x, iv.z * 2.4494896F, -iv.y);
    }

    static void cmsDirToRender(Vector3f var0, float var1, Vector3f var2) {
        iv.set(var0.x, var0.y, var0.z);
        Model.vectorToWorldCoords(0.0F, 0.0F, 0.0F, var1, iv);
        var2.set(-iv.x, iv.z * 2.4494896F, -iv.y).normalize();
    }

    static void viewDirCms(float var0, float var1, float var2, Vector3f var3) {
        float var4 = (float)Math.cos(var1);
        float var5 = -((float)Math.cos(var0)) * var4;
        float var6 = (float)Math.sin(var1);
        float var7 = -((float)Math.sin(var0)) * var4;
        worldToCms(-var5, -var7, var6 / 2.4494896F, var2, var3);
        var3.normalize();
    }

    private static void projectOverlay(IsoPlayer var0, float var1, int var2, float var3) {
        int var4 = Core.getInstance().getScreenWidth();
        int var5 = Core.getInstance().getScreenHeight();
        float var6 = VP.yaw();
        float var7 = VP.pitch();
        float var8 = (float)Math.cos(var7);
        fw.set(-((float)Math.cos(var6)) * var8, (float)Math.sin(var7), -((float)Math.sin(var6)) * var8);
        fw.cross(0.0F, 1.0F, 0.0F, rt).normalize();
        rt.cross(fw, up).normalize();
        float var9 = (float)Math.tan(fovOutRad * 0.5F);
        float var10 = (float)var4 / Math.max(1, var5);
        cmsDirToRender(Ff, var1, pr);
        float var11 = pr.x;
        float var12 = pr.y;
        float var13 = pr.z;
        float[] var14 = project(pr.x, pr.y, pr.z, var9, var10, var4, var5);
        float var15 = var14[0];
        float var16 = var14[1];
        ovDotX = var15;
        ovDotY = var16;
        cmsVecToRender(rel.set(Sf).sub(E), var1, rel);
        float var17 = rel.dot(fw);
        var14 = project(rel.x, rel.y, rel.z, var9, var10, var4, var5);
        ovWinX = var14[0];
        ovWinY = var14[1];
        ovWinR = var17 > 0.001F ? var3 * 1.5F / var17 / var9 * (var5 * 0.5F) : 0.0F;
        float var18 = ovMisalign;
        ovEyeBox = S.eyeBox ? clamp01(1.0F - Math.max(0.0F, var18 - 0.15F) / 2.2F) : 1.0F;
        ovLensX = var15;
        ovLensY = var16;
        ovLensR = S.lensSize * P.lensScale * 0.5F * var5;
        float var27;
        float var28;
        float var29;
        if (tubeEyeR > 0.0F && ovWinR > 1.0F && ovWinX > -9000.0F) {
            var27 = Math.min(0.45F * var5, ovWinR * 0.85F);
            var28 = ovWinX;
            var29 = ovWinY;
        } else {
            float var19 = ovWinR > 1.0F ? ovWinR * P.lensScale * 1.25F : 0.2F * var5;
            var27 = Math.max(0.1F * var5, Math.min(0.3F * var5, var19));
            var28 = ovWinX > -9000.0F ? ovWinX : var15;
            var29 = ovWinY > -9000.0F ? ovWinY : var16;
            float var22 = var28 - var4 * 0.5F;
            float var23 = var29 - var5 * 0.5F;
            float var24 = (float)Math.sqrt(var22 * var22 + var23 * var23);
            float var25 = var27 * 0.5F;
            if (var24 > var25) {
                var22 *= var25 / var24;
                var23 *= var25 / var24;
            }

            var28 = var4 * 0.5F + var22;
            var29 = var5 * 0.5F + var23;
        }

        Pip.wantCx = var28;
        Pip.wantCy = var29;
        Pip.wantR = var27;
        Pip.geom = tubeEyeR > 0.0F && var17 > 0.001F ? new float[]{rel.x, rel.y, rel.z, var11, var12, var13, var3 * 1.5F * 0.85F} : null;
        Pip.wantAx = var15 > -9000.0F ? var15 - var4 * 0.5F : 0.0F;
        Pip.wantAy = var16 > -9000.0F ? var16 - var5 * 0.5F : 0.0F;
        ovMode = var2;
        ovVisible = true;
    }

    private static float[] project(float var0, float var1, float var2, float var3, float var4, int var5, int var6) {
        float var7 = var0 * fw.x + var1 * fw.y + var2 * fw.z;
        if (var7 <= 1.0E-5F) {
            proj[0] = -9999.0F;
            proj[1] = -9999.0F;
            return proj;
        } else {
            float var8 = (var0 * rt.x + var1 * rt.y + var2 * rt.z) / var7 / (var3 * var4);
            float var9 = (var0 * up.x + var1 * up.y + var2 * up.z) / var7 / var3;
            proj[0] = var5 * 0.5F * (1.0F + var8);
            proj[1] = var6 * 0.5F * (1.0F - var9);
            return proj;
        }
    }

    static String shortName(String var0) {
        if (var0 == null) {
            return "";
        }

        int var1 = var0.lastIndexOf(46);
        return (var1 >= 0 ? var0.substring(var1 + 1) : var0).toLowerCase(Locale.ROOT);
    }

    static float clamp01(float var0) {
        return var0 < 0.0F ? 0.0F : (var0 > 1.0F ? 1.0F : var0);
    }

    static float clamp(float var0, float var1, float var2) {
        return var0 < var1 ? var1 : (var0 > var2 ? var2 : var0);
    }

    static float wrap(float var0) {
        float var1 = (float) (Math.PI * 2);

        while (var0 > Math.PI) {
            var0 -= var1;
        }

        while (var0 < -Math.PI) {
            var0 += var1;
        }

        return var0;
    }

    static void setWeaponHidden(ModelInstance var0) {
        if (hiddenInst != var0) {
            if (hiddenInst != null) {
                hiddenInst.scale = hiddenScale;
                hiddenInst = null;
            }

            if (var0 != null) {
                hiddenScale = var0.scale;
                hiddenInst = var0;
                var0.scale = 0.0F;
            }
        }
    }

    static void reset() {
        Presence.reset();
        Sight.clear();
        setWeaponHidden(null);
        t = 0.0F;
        e = 0.0F;
        eShared = 0.0F;
        ovVisible = false;
        gBackV = 0.0F;
        gBack = 0.0F;
        gKickV = 0.0F;
        gKick = 0.0F;
        hKickV = 0.0F;
        hKick = 0.0F;
        vKickV = 0.0F;
        vKick = 0.0F;
        climbY = 0.0F;
        climbP = 0.0F;
    }

    private ADS() {
    }

    static final class Profile {
        volatile boolean valid;
        String weaponId = "";
        String opticIdStr = "";
        int mode = 0;
        float sx;
        float sy;
        float sz;
        float dx = 0.0F;
        float dy = 1.0F;
        float dz = 0.0F;
        float opticHeight = 0.03F;
        float opticWindow = 0.012F;
        float opticLength = 0.12F;
        float adjX;
        float adjY;
        float adjZ;
        float adjPitch;
        float adjYaw;
        float adjRoll;
        float relief = 0.12F;
        float adsTime = 0.25F;
        float fovMul = 0.9F;
        float mag = 1.0F;
        float swayMul = 1.0F;
        float recoilMul = 1.0F;
        float kickMul = 1.0F;
        float climb = 1.0F;
        float side = 0.5F;
        float recover = 0.4F;
        float gunKick = 3.0F;
        float gunBack = 0.02F;
        float hipCone = 2.5F;
        float lensScale = 1.0F;
        float windowScale = 1.0F;
        float zero;
        boolean ironAuto = true;
        boolean bead;
        float[] glass;
        String[] ironMesh;
        float[][] ironVar;
        boolean ironLocked;
        float minRelief;
        String[] glassMesh;
        float[][] glassVar;
        float[][] glassVarPip;
        float[] glassPoly;
        float[][] glassVarPoly;
        boolean pipLocked;
        String family = "carbine";
        float genLength;
        float genMuzzleY;
        float lengthOverrideM;
        float muzzleOffsetM;
        float collisionRadiusM;
        float poseScale = 1.0F;
        volatile float pipX;
        volatile float pipY;
        volatile float pipScale = 1.0F;
        volatile float pipZoom = 1.0F;
        volatile float pipRetX;
        volatile float pipRetY;
        volatile float pipDepth;
        volatile float pipZ;
        volatile float pipCorner = -1.0F;
        volatile float pipAspect = 1.0F;
        volatile float reticleRoll;
        boolean opticHeightSet;
        float[] opticRear;
        float[] opticFront;
        float[] base;
        boolean basePipLocked;
        float[] userWeapon;
        int serial;
    }

    static final class Settings {
        volatile boolean enabled = true;
        volatile boolean adsSharp = true;
        volatile boolean thirdAds = true;
        volatile boolean closeClip = true;
        volatile boolean hideCrosshair = true;
        volatile boolean sway = true;
        volatile boolean cameraRecoil = true;
        volatile boolean eyeBox = true;
        volatile boolean inertia = true;
        volatile float speedMul = 1.0F;
        volatile float adsSens = 0.85F;
        volatile float scopeSens = 1.0F;
        volatile float swayAmount = 1.0F;
        volatile float recoilAmount = 1.0F;
        volatile boolean steady = true;
        volatile boolean rest = true;
        volatile float steadyZoom = 1.2F;
        volatile float lensSize = 0.84F;
        volatile float zoomMul = 1.5F;
        volatile float reliefMul = 1.0F;
        volatile boolean pip = true;
        volatile boolean hipSpread = true;
    }
}
