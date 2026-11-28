package t3zedballistics.ads;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.util.vector.Matrix4f;
import zombie.characters.IsoPlayer;
import zombie.core.math.PZMath;
import zombie.core.skinnedmodel.animation.AnimationPlayer;
import zombie.core.skinnedmodel.model.ModelInstance;
import zombie.network.GameClient;

final class RemoteAim {
    static final long STALE_NS = 2500000000L;
    static final float TAU = 0.12F;
    static final float MAX_DEG = 75.0F;
    private static final HashMap<Integer, RemoteAim.State> byId = new HashMap<>();
    private static volatile Map<Object, RemoteAim.State> byAnim = new IdentityHashMap<>();
    static volatile int received;
    static volatile int applied;
    private static boolean announced;

    static void set(int var0, float var1, boolean var2) {
        if (Float.isFinite(var1)) {
            IsoPlayer var3 = find(var0);
            if (var3 != null) {
                RemoteAim.State var4 = byId.get(var0);
                boolean var5 = false;
                if (var4 == null || var4.pl != var3) {
                    var4 = new RemoteAim.State(var3, var0);
                    byId.put(var0, var4);
                    var5 = true;
                }

                var4.targetDeg = Math.max(-75.0F, Math.min(75.0F, var1));
                var4.aiming = var2;
                var4.stampNs = System.nanoTime();
                received++;
                long var6 = System.nanoTime();
                if (byId.size() > 1 && byId.values().removeIf(var2x -> var2x != null && var6 - var2x.stampNs > 60000000000L)) {
                    var5 = true;
                }

                Map var8 = byAnim;
                if (!var5) {
                    for (RemoteAim.State var10 : byId.values()) {
                        AnimationPlayer var11 = var10.pl.getAnimationPlayer();
                        if (var11 != null && var8.get(var11) != var10) {
                            var5 = true;
                            break;
                        }
                    }
                }

                if (var5) {
                    IdentityHashMap var13 = new IdentityHashMap();

                    for (RemoteAim.State var15 : byId.values()) {
                        AnimationPlayer var12 = var15.pl.getAnimationPlayer();
                        if (var12 != null) {
                            var13.put(var12, var15);
                        }
                    }

                    byAnim = var13;
                    if (!announced) {
                        announced = true;
                        Log.info("aim sync: other players' aim pitch received");
                    }
                }
            }
        }
    }

    static void clear() {
        byId.clear();
        byAnim = new IdentityHashMap<>();
    }

    private static IsoPlayer find(int var0) {
        try {
            if (GameClient.client && GameClient.instance != null) {
                ArrayList var1 = GameClient.instance.getPlayers();

                for (int var2 = 0; var2 < var1.size(); var2++) {
                    IsoPlayer var3 = (IsoPlayer)var1.get(var2);
                    if (var3 != null && var3.getOnlineID() == var0) {
                        return var3;
                    }
                }
            }
        } catch (Throwable var4) {
            Log.error("aim sync: player lookup", var4);
        }

        return null;
    }

    static void posed(Object var0) {
        Map var1 = byAnim;
        if (!var1.isEmpty()) {
            RemoteAim.State var2 = (RemoteAim.State)var1.get(var0);
            if (var2 != null) {
                try {
                    pose(var2, (AnimationPlayer)var0);
                } catch (Throwable var4) {
                    Log.error("aim sync pose", var4);
                    var2.cur = 0.0F;
                }
            }
        }
    }

    private static void pose(RemoteAim.State var0, AnimationPlayer var1) {
        long var2 = System.nanoTime();
        float var4 = var0.lastNs == 0L ? 0.016F : Math.min(0.1F, (float)(var2 - var0.lastNs) / 1.0E9F);
        var0.lastNs = var2;
        IsoPlayer var5 = var0.pl;
        boolean var6 = var2 - var0.stampNs < 2500000000L;
        float var7 = var6 && var0.aiming && var5.isAiming() && !var5.isDead() && var5.getVehicle() == null ? var0.targetDeg : 0.0F;
        var0.cur = var0.cur + (var7 - var0.cur) * (1.0F - (float)Math.exp(-var4 / 0.12F));
        if (!Float.isFinite(var0.cur)) {
            var0.cur = 0.0F;
        }

        if (Math.abs(var0.cur) < 0.3F) {
            if (var7 == 0.0F) {
                var0.cur = 0.0F;
            }
        } else {
            ModelInstance var8 = var5.primaryHandModel;
            if (var8 != null && ADS.weaponXfrm(var8, var0.W, var0.A)) {
                RemotePresence.Binding var9 = RemotePresence.binding(var1);
                if (var9 != null && var9.rig != null && var9.bones.length != 0) {
                    var0.W.transformDirection(var0.F.set(0.0F, 1.0F, 0.0F));
                    if (var0.F.lengthSquared() > 1.0E-10F && Float.isFinite(var0.F.x)) {
                        var0.F.normalize();
                        float var10 = (float)Math.asin(Math.max(-1.0F, Math.min(1.0F, var0.F.y)));
                        float var11 = (float)Math.toRadians(var0.cur) - var10;
                        float var12 = (float)Math.toRadians(75.0);
                        if (var11 > var12) {
                            var11 = var12;
                        } else if (var11 < -var12) {
                            var11 = -var12;
                        }

                        if (!(Math.abs(var11) < 1.0E-4F)) {
                            var0.F.cross(0.0F, 1.0F, 0.0F, var0.R);
                            if (!(var0.R.lengthSquared() < 1.0E-8F)) {
                                var0.R.normalize();
                                int var13 = var9.rig.clavL >= 0 ? var9.rig.clavL : var9.rig.upL;
                                int var14 = var9.rig.clavR >= 0 ? var9.rig.clavR : var9.rig.upR;
                                if (var13 >= 0 && var14 >= 0) {
                                    origin(var1, var13, var0.piv, var0.B);
                                    origin(var1, var14, var0.t, var0.B);
                                    var0.piv.add(var0.t).mul(0.5F);
                                    var0.q.identity().rotateAxis(var11, var0.R.x, var0.R.y, var0.R.z);
                                    var0.X.identity().translate(var0.piv).rotate(var0.q).translate(-var0.piv.x, -var0.piv.y, -var0.piv.z);

                                    for (int var15 = 0; var15 < var9.bones.length; var15++) {
                                        Matrix4f var16 = var1.getModelTransformAt(var9.bones[var15]);
                                        PZMath.convertMatrix(var16, var0.B);
                                        var0.B.transpose();
                                        var0.X.mul(var0.B, var0.B);
                                        var0.B.transpose();
                                        PZMath.convertMatrix(var0.B, var16);
                                    }

                                    applied++;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static void origin(AnimationPlayer var0, int var1, Vector3f var2, org.joml.Matrix4f var3) {
        PZMath.convertMatrix(var0.getModelTransformAt(var1), var3);
        var3.transpose();
        var3.getTranslation(var2);
    }

    private RemoteAim() {
    }

    static final class State {
        final IsoPlayer pl;
        final int id;
        volatile float targetDeg;
        volatile boolean aiming;
        volatile long stampNs;
        float cur;
        long lastNs;
        final org.joml.Matrix4f W = new org.joml.Matrix4f();
        final org.joml.Matrix4f A = new org.joml.Matrix4f();
        final org.joml.Matrix4f X = new org.joml.Matrix4f();
        final org.joml.Matrix4f B = new org.joml.Matrix4f();
        final Vector3f F = new Vector3f();
        final Vector3f R = new Vector3f();
        final Vector3f piv = new Vector3f();
        final Vector3f t = new Vector3f();
        final Quaternionf q = new Quaternionf();

        State(IsoPlayer var1, int var2) {
            this.pl = var1;
            this.id = var2;
        }
    }
}
