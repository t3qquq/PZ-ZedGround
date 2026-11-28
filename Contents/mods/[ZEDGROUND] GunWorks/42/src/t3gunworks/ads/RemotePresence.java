package t3gunworks.ads;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.util.vector.Matrix4f;
import zombie.characters.IsoPlayer;
import zombie.core.math.PZMath;
import zombie.core.skinnedmodel.animation.AnimationPlayer;
import zombie.core.skinnedmodel.model.Model;
import zombie.core.skinnedmodel.model.ModelInstance;
import zombie.core.skinnedmodel.model.SkinningBone;
import zombie.core.skinnedmodel.model.SkinningData;
import zombie.inventory.InventoryItem;
import zombie.inventory.types.HandWeapon;
import zombie.iso.Vector3;
import zombie.network.GameClient;

final class RemotePresence {
    static final int MAX = 12;
    static final float RANGE = 40.0F;
    private static volatile Map<Object, RemotePresence.State> byAnim = new IdentityHashMap<>();
    private static final HashMap<IsoPlayer, RemotePresence.State> states = new HashMap<>();
    private static final ArrayList<IsoPlayer> cand = new ArrayList<>();
    private static final ArrayList<Object> lastKeys = new ArrayList<>();
    private static boolean announced;
    private static final HashMap<SkinningData, RemotePresence.Binding> bindings = new HashMap<>();

    static void tick(IsoPlayer var0) {
        if (Presence.C.enabled && Presence.C.others && var0 != null) {
            cand.clear();

            try {
                if (GameClient.client && GameClient.instance != null) {
                    ArrayList var11 = GameClient.instance.getPlayers();

                    for (int var2 = 0; var2 < var11.size(); var2++) {
                        consider((IsoPlayer)var11.get(var2), var0);
                    }
                } else {
                    for (int var1 = 1; var1 < IsoPlayer.players.length; var1++) {
                        consider(IsoPlayer.players[var1], var0);
                    }
                }
            } catch (Throwable var10) {
                Log.error("weapon presence (other players)", var10);
                clear();
                return;
            }

            if (cand.size() > 12) {
                float var12 = var0.getX();
                float var15 = var0.getY();
                cand.sort((var2x, var3x) -> Float.compare(d2(var2x, var12, var15), d2(var3x, var12, var15)));

                while (cand.size() > 12) {
                    cand.remove(cand.size() - 1);
                }
            }

            states.keySet().removeIf(var0x -> !cand.contains(var0x));

            for (int var13 = 0; var13 < cand.size(); var13++) {
                IsoPlayer var16 = cand.get(var13);
                if (!states.containsKey(var16)) {
                    states.put(var16, new RemotePresence.State(var16));
                }
            }

            int var14 = 0;

            for (RemotePresence.State var3 : states.values()) {
                if (var3.pl.getAnimationPlayer() != null) {
                    var14++;
                }
            }

            boolean var18 = lastKeys.size() != var14;
            if (!var18) {
                for (RemotePresence.State var4 : states.values()) {
                    AnimationPlayer var5 = var4.pl.getAnimationPlayer();
                    if (var5 != null && !lastKeys.contains(var5)) {
                        var18 = true;
                        break;
                    }
                }
            }

            if (var18) {
                IdentityHashMap var20 = new IdentityHashMap();
                lastKeys.clear();

                for (RemotePresence.State var24 : states.values()) {
                    AnimationPlayer var6 = var24.pl.getAnimationPlayer();
                    if (var6 != null) {
                        var20.put(var6, var24);
                        lastKeys.add(var6);
                    }
                }

                byAnim = var20;
                if (!announced && !var20.isEmpty()) {
                    announced = true;
                    Log.info("Physical Weapon Presence: other players' guns handled locally (no network traffic)");
                }
            }

            for (RemotePresence.State var23 : states.values()) {
                boolean var25;
                synchronized (var23) {
                    var25 = var23.snapActive && System.nanoTime() - var23.snapNs < 200000000L;
                    if (var25) {
                        System.arraycopy(var23.snap, 0, var23.snapR, 0, var23.snapR.length);
                    }
                }

                try {
                    if (var25) {
                        PresenceProbe.run(var23.snapR, var23.res);
                    } else if (var23.res.need > 0.0F || var23.res.hitType != 0) {
                        PresenceProbe.clear(var23.res);
                    }
                } catch (Throwable var8) {
                    Log.error("weapon presence probe (other players)", var8);
                    PresenceProbe.clear(var23.res);
                }
            }
        } else {
            clear();
        }
    }

    private static float d2(IsoPlayer var0, float var1, float var2) {
        float var3 = var0.getX() - var1;
        float var4 = var0.getY() - var2;
        return var3 * var3 + var4 * var4;
    }

    private static void consider(IsoPlayer var0, IsoPlayer var1) {
        if (var0 != null && var0 != var1 && !var0.isDead() && var0.getVehicle() == null) {
            if (!(Math.abs(var0.getZ() - var1.getZ()) > 1.5F)) {
                float var2 = var0.getX() - var1.getX();
                float var3 = var0.getY() - var1.getY();
                if (!(var2 * var2 + var3 * var3 > 1600.0F)) {
                    InventoryItem var4 = var0.getPrimaryHandItem();
                    if (var4 instanceof HandWeapon && ((HandWeapon)var4).isAimedFirearm()) {
                        cand.add(var0);
                    }
                }
            }
        }
    }

    static void clear() {
        if (!states.isEmpty()) {
            states.clear();
        }

        if (!byAnim.isEmpty()) {
            byAnim = new IdentityHashMap<>();
        }

        lastKeys.clear();
    }

    static void posed(Object var0) {
        Map var1 = byAnim;
        if (!var1.isEmpty()) {
            RemotePresence.State var2 = (RemotePresence.State)var1.get(var0);
            if (var2 != null) {
                try {
                    synchronized (var2) {
                        pose(var2, (AnimationPlayer)var0);
                    }
                } catch (Throwable var6) {
                    Log.error("weapon presence pose (other players)", var6);
                    var2.a = 0.0F;
                    var2.spr[0] = var2.spr[1] = 0.0F;
                    var2.shotFix = false;
                }
            }
        }
    }

    private static void pose(RemotePresence.State var0, AnimationPlayer var1) {
        IsoPlayer var2 = var0.pl;
        long var3 = System.nanoTime();
        float var5 = var0.lastNs == 0L ? 0.016F : Math.min(0.1F, (float)(var3 - var0.lastNs) / 1.0E9F);
        var0.lastNs = var3;
        ModelInstance var6 = var2.primaryHandModel;
        InventoryItem var7 = var2.getPrimaryHandItem();
        if (var7 != var0.item) {
            var0.item = var7;
            var0.a = 0.0F;
            var0.target = 0.0F;
            var0.spr[0] = var0.spr[1] = 0.0F;
            var0.dims.invalidate();
            PresenceProbe.clear(var0.res);
        }

        boolean var8 = var6 != null && var2.isAiming() && !var2.isDead() && Presence.C.enabled && Presence.C.others;
        var0.aimingNow = var8;
        float var9 = var3 - var0.res.stampNs < 250000000L ? var0.res.need : -1.0F;
        float var10 = var8 && var0.poseReady ? var0.pose.solve(var9, 1.0F) : 0.0F;
        if (var10 < 0.02F) {
            var10 = 0.0F;
        }

        if (var10 > var0.target || var10 < var0.target - 0.06F) {
            var0.target = var10;
        }

        float var11 = 1.0F / Math.max(0.25F, Presence.C.smoothing);
        Presence.spring(var0.spr, var0.target, (var0.target > var0.a ? 30.0F : 12.0F) * var11, var5);
        float var12 = var0.spr[0];
        if (!Float.isFinite(var12) || !Float.isFinite(var0.spr[1])) {
            var12 = 0.0F;
            var0.spr[1] = 0.0F;
        }

        if (var12 < 0.0F) {
            var12 = 0.0F;
            var0.spr[1] = Math.max(0.0F, var0.spr[1]);
        }

        if (var12 > 1.0F) {
            var12 = 1.0F;
            var0.spr[1] = Math.min(0.0F, var0.spr[1]);
        }

        if (var0.target == 0.0F && var12 < 1.0E-4F && Math.abs(var0.spr[1]) < 0.001F) {
            var12 = 0.0F;
            var0.spr[1] = 0.0F;
        }

        var0.spr[0] = var12;
        var0.a = var12;
        var0.poseReady = false;
        if (var6 == null || !ADS.weaponXfrm(var6, var0.W, var0.A)) {
            var0.shotFix = false;
            var0.snapActive = false;
        } else if (!var8 && !(var12 > 1.0E-4F)) {
            var0.shotFix = false;
            var0.snapActive = false;
        } else {
            float var13 = var0.W.transformDirection(var0.tv.set(0.0F, 1.0F, 0.0F)).length();
            var0.info.key = var7 instanceof InventoryItem ? var7.getFullType() : "?";
            var0.info.family = familyOf(var7);
            if (var13 > 1.0E-6F && var0.dims.resolve(var6, var13, var0.info)) {
                WeaponDims var14 = var0.dims;
                float var15 = var14.lengthM(var13);
                float var16 = var14.rearY;
                float var17 = var14.muzzleY;
                boolean var18 = "pistol".equals(var0.info.family) || var15 < 0.36F;
                var0.W.transformPosition(var0.tv.set(var14.boreX, var16 + 0.35F * (var17 - var16), var14.boreZ), var0.P);
                var0.W.transformPosition(var0.tv.set(var14.boreX, var17, var14.boreZ), var0.Mz);
                var0.W.transformPosition(var0.tv.set(var14.boreX, var16, var14.boreZ), var0.R0);
                var0.F.set(var0.Mz).sub(var0.R0);
                if (var0.F.lengthSquared() > 1.0E-10F && Float.isFinite(var0.F.x) && Float.isFinite(var0.P.x)) {
                    var0.F.normalize();
                    if (var18) {
                        var0.P.set(var0.R0);
                    }

                    float var19 = (float)Math.sqrt((var0.Mz.x - var0.P.x) * (var0.Mz.x - var0.P.x) + (var0.Mz.z - var0.P.z) * (var0.Mz.z - var0.P.z)) * 1.5F;
                    var0.pose.configure(var15, var19, var18, Presence.C.strength, (float)Math.sqrt(var0.R0.x * var0.R0.x + var0.R0.z * var0.R0.z) * 1.5F);
                    var0.poseReady = true;
                    float var20 = var1.getRenderedAngle();
                    float var21 = var2.getX();
                    float var22 = var2.getY();
                    float var23 = var2.getZ();
                    if (var8) {
                        synchronized (var0) {
                            var0.snap[0] = var21;
                            var0.snap[1] = var22;
                            var0.snap[2] = var23;
                            world(var0, var0.R0, var21, var22, var23, var20);
                            var0.snap[3] = var0.iv.x;
                            var0.snap[4] = var0.iv.y;
                            var0.snap[5] = var0.iv.z * 2.4494896F;
                            world(var0, var0.Mz, var21, var22, var23, var20);
                            var0.snap[6] = var0.iv.x;
                            var0.snap[7] = var0.iv.y;
                            var0.snap[8] = var0.iv.z * 2.4494896F;
                            var0.snap[9] = var18 ? 0.05F : 0.045F;
                            var0.snap[10] = Presence.C.startDist + PresencePose.extraStart(var15, var18);
                            var0.snapNs = var3;
                            var0.snapActive = true;
                        }
                    } else {
                        var0.snapActive = false;
                    }

                    var0.imx = var0.snap[6];
                    var0.imy = var0.snap[7];
                    var0.imz = var0.snap[8] / 2.4494896F;
                    var0.iv.set(var0.F.x, var0.F.y, var0.F.z);
                    Model.vectorToWorldCoords(0.0F, 0.0F, 0.0F, var20, var0.iv);
                    var0.idx = var0.iv.x;
                    var0.idy = var0.iv.y;
                    var0.idz = var0.iv.z;
                    if (!(var12 > 1.0E-4F)) {
                        var0.shotFix = false;
                    } else {
                        float var24 = var0.res.normX;
                        float var25 = var0.res.normY;
                        if (var24 == 0.0F && var25 == 0.0F) {
                            var0.N.set(0.0F, 0.0F, 0.0F);
                        } else {
                            ADS.worldToCmsSafe(var24, var25, 0.0F, var20, var0.N, var0.iv);
                            if (var0.N.lengthSquared() > 1.0E-8F) {
                                var0.N.normalize();
                            }
                        }

                        if (!var0.pose.prepare(var12, var0.F, var0.N, 1.0F)) {
                            var0.shotFix = false;
                        } else {
                            var0.pose.correction(var0.P, 1.0F, var0.CM).transformDirection(var0.tv.set(var0.F));
                            var0.iv.set(var0.tv.x, var0.tv.y, var0.tv.z);
                            Model.vectorToWorldCoords(0.0F, 0.0F, 0.0F, var20, var0.iv);
                            var0.cdx = var0.iv.x;
                            var0.cdy = var0.iv.y;
                            var0.cdz = var0.iv.z;
                            var0.shotFix = true;
                            RemotePresence.Binding var26 = binding(var1);
                            if (var26 != null) {
                                if (!var0.rig.apply(var1, var26.rig, var0.pose.correction(var0.P, 1.0F, var0.CM))) {
                                    for (int var27 = 0; var27 < var26.bones.length; var27++) {
                                        float var28 = var0.pose.pistol ? 1.0F : var26.weights[var27];
                                        if (var28 > 0.0F) {
                                            var0.pose.correction(var0.P, var28, var0.CM);
                                            Matrix4f var29 = var1.getModelTransformAt(var26.bones[var27]);
                                            PZMath.convertMatrix(var29, var0.TM);
                                            var0.TM.transpose();
                                            var0.CM.mul(var0.TM, var0.TM);
                                            var0.TM.transpose();
                                            PZMath.convertMatrix(var0.TM, var29);
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    var0.shotFix = false;
                }
            } else {
                var0.shotFix = false;
            }
        }
    }

    private static void world(RemotePresence.State var0, Vector3f var1, float var2, float var3, float var4, float var5) {
        var0.iv.set(var1.x, var1.y, var1.z);
        Model.vectorToWorldCoords(var2, var3, var4, var5, var0.iv);
    }

    static String familyOf(Object var0) {
        if (!(var0 instanceof HandWeapon var1)) {
            return "carbine";
        } else {
            String var2 = var1.getSwingAnim();
            if ("Handgun".equals(var2)) {
                return "pistol";
            } else {
                String var3 = (var1.getFullType() + " " + var1.getDisplayName()).toLowerCase(Locale.ROOT);
                if (var3.contains("shotgun")) {
                    return "shotgun";
                } else {
                    return var3.contains("sniper") ? "sniper" : "carbine";
                }
            }
        }
    }

    static RemotePresence.Binding binding(AnimationPlayer var0) {
        SkinningData var1 = var0.getSkinningData();
        if (var1 == null) {
            return null;
        }

        synchronized (bindings) {
            RemotePresence.Binding var3 = bindings.get(var1);
            if (var3 != null) {
                return var3;
            }

            String[] var4 = new String[]{"Bip01_L_Clavicle", "Bip01_R_Clavicle", "Bip01_Prop1", "Bip01_Prop2"};
            int[] var5 = new int[var4.length];

            for (int var6 = 0; var6 < var5.length; var6++) {
                var5[var6] = var0.getSkinningBoneIndex(var4[var6], -1);
            }

            int var17 = var1.numBones();
            int[] var7 = new int[var17];
            float[] var8 = new float[var17];
            int var9 = 0;

            for (int var10 = 0; var10 < var17; var10++) {
                int var11 = -1;

                for (SkinningBone var12 = var1.getBoneAt(var10); var12 != null && var11 < 0; var12 = var12.parent) {
                    for (int var13 = 0; var13 < var5.length; var13++) {
                        if (var5[var13] >= 0 && var12.index == var5[var13]) {
                            var11 = var13;
                            break;
                        }
                    }
                }

                if (var11 >= 0) {
                    var7[var9] = var10;
                    var8[var9] = var11 >= 2 ? 1.0F : ADS.presenceWeight(var1.getBoneAt(var10).name);
                    var9++;
                }
            }

            var3 = new RemotePresence.Binding();
            var3.bones = Arrays.copyOf(var7, var9);
            var3.weights = Arrays.copyOf(var8, var9);
            var3.rig = ArmRig.build(var0, var3.bones);
            if (bindings.size() > 16) {
                bindings.clear();
            }

            bindings.put(var1, var3);
            return var3;
        }
    }

    static void afterMuzzle(Object var0, Object var1, Object var2) {
        Map var3 = byAnim;
        if (!var3.isEmpty() && var1 instanceof Vector3 && var2 instanceof Vector3) {
            for (RemotePresence.State var5 : (Iterable<RemotePresence.State>) var3.values()) {
                if (var5.shotFix && var5.pl.getBallisticsController() == var0) {
                    synchronized (var5) {
                        Presence.undoShot(
                            var5.pl,
                            (Vector3)var1,
                            (Vector3)var2,
                            var5.cdx,
                            var5.cdy,
                            var5.cdz,
                            var5.idx,
                            var5.idy,
                            var5.idz,
                            var5.imx,
                            var5.imy,
                            var5.imz,
                            var5.SC,
                            var5.SI,
                            var5.SD,
                            var5.SQ
                        );
                        return;
                    }
                }
            }
        }
    }

    private RemotePresence() {
    }

    static final class Binding {
        int[] bones;
        float[] weights;
        ArmRig.Rig rig;
    }

    static final class State {
        final IsoPlayer pl;
        final PresencePose pose = new PresencePose();
        final WeaponDims dims = new WeaponDims();
        final WeaponDims.Info info = new WeaponDims.Info();
        final PresenceProbe.Result res = new PresenceProbe.Result();
        final float[] snap = new float[11];
        final float[] snapR = new float[11];
        final float[] spr = new float[2];
        final org.joml.Matrix4f W = new org.joml.Matrix4f();
        final org.joml.Matrix4f A = new org.joml.Matrix4f();
        final org.joml.Matrix4f CM = new org.joml.Matrix4f();
        final org.joml.Matrix4f TM = new org.joml.Matrix4f();
        final Vector3f P = new Vector3f();
        final Vector3f F = new Vector3f();
        final Vector3f Mz = new Vector3f();
        final Vector3f R0 = new Vector3f();
        final Vector3f N = new Vector3f();
        final Vector3f tv = new Vector3f();
        final Vector3 iv = new Vector3();
        final Vector3f SC = new Vector3f();
        final Vector3f SI = new Vector3f();
        final Vector3f SD = new Vector3f();
        final Quaternionf SQ = new Quaternionf();
        final ArmRig.Solver rig = new ArmRig.Solver();
        volatile boolean snapActive;
        volatile boolean shotFix;
        volatile boolean aimingNow;
        long snapNs;
        long lastNs;
        float a;
        float target;
        boolean poseReady;
        Object item;
        volatile float imx;
        volatile float imy;
        volatile float imz;
        volatile float idx;
        volatile float idy;
        volatile float idz;
        volatile float cdx;
        volatile float cdy;
        volatile float cdz;

        State(IsoPlayer var1) {
            this.pl = var1;
        }
    }
}
