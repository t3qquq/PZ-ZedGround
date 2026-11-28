package t3gunworks.ads;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import zombie.core.math.PZMath;
import zombie.core.skinnedmodel.animation.AnimationPlayer;
import zombie.core.skinnedmodel.model.SkinningBone;
import zombie.core.skinnedmodel.model.SkinningData;

final class ArmRig {
    static final byte FREE = 0;
    static final byte UP_L = 1;
    static final byte FORE_L = 2;
    static final byte HAND_L = 3;
    static final byte UP_R = 4;
    static final byte FORE_R = 5;
    static final byte HAND_R = 6;
    static final byte PROP = 7;

    static ArmRig.Rig build(AnimationPlayer var0, int[] var1) {
        ArmRig.Rig var2 = new ArmRig.Rig();
        var2.bones = var1;
        var2.group = new byte[var1.length];
        SkinningData var3 = var0.getSkinningData();
        if (var3 == null) {
            return var2;
        }

        var2.upL = var0.getSkinningBoneIndex("Bip01_L_UpperArm", -1);
        var2.foL = var0.getSkinningBoneIndex("Bip01_L_Forearm", -1);
        var2.haL = var0.getSkinningBoneIndex("Bip01_L_Hand", -1);
        var2.upR = var0.getSkinningBoneIndex("Bip01_R_UpperArm", -1);
        var2.foR = var0.getSkinningBoneIndex("Bip01_R_Forearm", -1);
        var2.haR = var0.getSkinningBoneIndex("Bip01_R_Hand", -1);
        var2.clavL = var0.getSkinningBoneIndex("Bip01_L_Clavicle", -1);
        var2.clavR = var0.getSkinningBoneIndex("Bip01_R_Clavicle", -1);
        var2.complete = var2.upL >= 0 && var2.foL >= 0 && var2.haL >= 0 && var2.upR >= 0 && var2.foR >= 0 && var2.haR >= 0;
        int var4 = var3.numBones();

        for (int var5 = 0; var5 < var1.length; var5++) {
            int var6 = var1[var5];
            byte var7 = 0;
            boolean var8 = false;
            if (var6 >= 0 && var6 < var4) {
                for (SkinningBone var9 = var3.getBoneAt(var6); var9 != null && var7 == 0; var9 = var9.parent) {
                    int var10 = var9.index;
                    if (var10 == var2.haL) {
                        var7 = 3;
                    } else if (var10 == var2.haR) {
                        var7 = 6;
                    } else if (var10 == var2.foL) {
                        var7 = 2;
                    } else if (var10 == var2.foR) {
                        var7 = 5;
                    } else if (var10 == var2.upL) {
                        var7 = 1;
                    } else if (var10 == var2.upR) {
                        var7 = 4;
                    }

                    if (var10 == var2.clavL || var10 == var2.clavR) {
                        var8 = true;
                    }
                }
            }

            if (var7 == 0 && !var8) {
                var7 = 7;
            }

            var2.group[var5] = var7;
        }

        return var2;
    }

    private ArmRig() {
    }

    static final class Rig {
        int[] bones;
        byte[] group;
        int upL = -1;
        int foL = -1;
        int haL = -1;
        int upR = -1;
        int foR = -1;
        int haR = -1;
        int clavL = -1;
        int clavR = -1;
        boolean complete;
    }

    static final class Solver {
        private final Matrix4f B = new Matrix4f();
        private final Matrix4f X = new Matrix4f();
        private final Matrix4f X1L = new Matrix4f();
        private final Matrix4f XFL = new Matrix4f();
        private final Matrix4f X1R = new Matrix4f();
        private final Matrix4f XFR = new Matrix4f();
        private final Vector3f S = new Vector3f();
        private final Vector3f E = new Vector3f();
        private final Vector3f H = new Vector3f();
        private final Vector3f H2 = new Vector3f();
        private final Vector3f E2 = new Vector3f();
        private final Vector3f dir = new Vector3f();
        private final Vector3f bend = new Vector3f();
        private final Vector3f v = new Vector3f();
        private final Vector3f w = new Vector3f();
        private final Quaternionf q = new Quaternionf();
        private final Quaternionf q2 = new Quaternionf();
        private static final Vector3f UP = new Vector3f(0.0F, 1.0F, 0.0F);
        static final float EPS = 1.0E-5F;
        static final float STRAIGHT = 0.02F;

        boolean apply(AnimationPlayer var1, ArmRig.Rig var2, Matrix4f var3) {
            if (var2 != null && var2.complete && var2.bones != null) {
                this.chain(var1, var2.upL, var2.foL, var2.haL, var3, this.X1L, this.XFL);
                this.chain(var1, var2.upR, var2.foR, var2.haR, var3, this.X1R, this.XFR);

                for (int var4 = 0; var4 < var2.bones.length; var4++) {
                    Matrix4f var5;
                    switch (var2.group[var4]) {
                        case 1:
                            var5 = this.X1L;
                            break;
                        case 2:
                            var5 = this.XFL;
                            break;
                        case 3:
                        case 6:
                        case 7:
                            var5 = var3;
                            break;
                        case 4:
                            var5 = this.X1R;
                            break;
                        case 5:
                            var5 = this.XFR;
                            break;
                        default:
                            continue;
                    }

                    org.lwjgl.util.vector.Matrix4f var6 = var1.getModelTransformAt(var2.bones[var4]);
                    PZMath.convertMatrix(var6, this.B);
                    this.B.transpose();
                    var5.mul(this.B, this.B);
                    this.B.transpose();
                    PZMath.convertMatrix(this.B, var6);
                }

                return true;
            } else {
                return false;
            }
        }

        private void origin(AnimationPlayer var1, int var2, Vector3f var3) {
            PZMath.convertMatrix(var1.getModelTransformAt(var2), this.B);
            this.B.transpose();
            this.B.getTranslation(var3);
        }

        private void chain(AnimationPlayer var1, int var2, int var3, int var4, Matrix4f var5, Matrix4f var6, Matrix4f var7) {
            this.origin(var1, var2, this.S);
            this.origin(var1, var3, this.E);
            this.origin(var1, var4, this.H);
            var5.transformPosition(this.H, this.H2);
            float var8 = this.E.distance(this.S);
            float var9 = this.H.distance(this.E);
            this.dir.set(this.H2).sub(this.S);
            float var10 = this.dir.length();
            if (var8 > 1.0E-5F && var9 > 1.0E-5F && var10 > 1.0E-5F && Float.isFinite(var10)) {
                float var11 = Math.max(Math.abs(var8 - var9) + 1.0E-4F, Math.min(var8 + var9 - 1.0E-4F, var10));
                float var12 = (var8 * var8 - var9 * var9 + var11 * var11) / (2.0F * var11);
                float var13 = (float)Math.sqrt(Math.max(0.0F, var8 * var8 - var12 * var12));
                this.dir.div(var10);
                this.v.set(this.H).sub(this.S);
                float var14 = this.v.lengthSquared();
                this.bend.set(this.E).sub(this.S);
                if (var14 > 1.0E-5F) {
                    this.bend.fma(-this.bend.dot(this.v) / var14, this.v);
                }

                if (this.bend.length() < 0.02F) {
                    this.bend.set(UP).negate();
                }

                this.bend.fma(-this.bend.dot(this.dir), this.dir);
                if (this.bend.lengthSquared() < 1.0E-8F) {
                    this.dir.cross(UP, this.bend);
                    if (this.bend.lengthSquared() < 1.0E-8F) {
                        this.bend.set(1.0F, 0.0F, 0.0F);
                    }
                }

                this.bend.normalize();
                this.E2.set(this.S).fma(var12, this.dir).fma(var13, this.bend);
                this.v.set(this.E).sub(this.S);
                this.w.set(this.E2).sub(this.S);
                this.q.rotationTo(this.v, this.w);
                var6.identity().translate(this.S).rotate(this.q).translate(-this.S.x, -this.S.y, -this.S.z);
                this.v.set(this.H).sub(this.E);
                this.q.transform(this.v);
                this.w.set(this.H2).sub(this.E2);
                if (this.w.lengthSquared() > 9.9999994E-11F) {
                    this.q2.rotationTo(this.v, this.w);
                } else {
                    this.q2.identity();
                }

                var7.identity().translate(this.E2).rotate(this.q2).translate(-this.E2.x, -this.E2.y, -this.E2.z).mul(var6);
            } else {
                var6.set(var5);
                var7.set(var5);
            }
        }
    }
}
