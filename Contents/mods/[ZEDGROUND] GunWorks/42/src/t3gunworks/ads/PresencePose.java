package t3gunworks.ads;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class PresencePose {
    float backMax;
    float pitchMax;
    float yawMax;
    float lp;
    boolean pistol;
    static final float YAW_MAX = (float)Math.toRadians(14.0);
    static final float BODY_CLEAR = 0.14F;
    static final float HAND_CLEAR = 0.22F;
    private final Quaternionf Q = new Quaternionf();
    private final Quaternionf QW = new Quaternionf();
    private final Quaternionf QI = new Quaternionf();
    private final Vector3f Fh = new Vector3f();
    private final Vector3f Rh = new Vector3f();
    private final Vector3f Np = new Vector3f();
    private final Vector3f T = new Vector3f();
    private final Vector3f UP = new Vector3f(0.0F, 1.0F, 0.0F);
    private float lastPitch;
    private float lastYaw;
    private float lastBack;

    void configure(float var1, float var2, boolean var3, float var4, float var5) {
        this.pistol = var3;
        float var6 = Math.max(0.3F, Math.min(1.8F, var4));
        float var7 = Math.max(0.0F, var5 - (var3 ? 0.22F : 0.14F));
        if (var3) {
            this.backMax = Math.min(0.4F, var7) * var6;
            this.pitchMax = (float)Math.toRadians(60.0) * var6;
        } else {
            float var8 = clamp01((var1 - 0.6F) / 0.6F);
            this.backMax = Math.max(0.14F + 0.04F * var8, Math.min(0.3F, var7)) * var6;
            this.pitchMax = (float)Math.toRadians(40.0F + 18.0F * var8) * var6;
        }

        this.pitchMax = Math.min(this.pitchMax, (float)Math.toRadians(75.0));
        this.yawMax = YAW_MAX;
        this.lp = Math.max(0.05F, var2);
    }

    static float backShare(float var0) {
        return smooth(clamp01(var0 / 0.45F));
    }

    static float pitchShare(float var0) {
        return smooth(clamp01((var0 - 0.12F) / 0.88F));
    }

    float reduction(float var1, float var2) {
        float var3 = this.pitchMax * pitchShare(var1);
        return this.backMax * var2 * backShare(var1) + this.lp * (1.0F - (float)Math.cos(var3));
    }

    float solve(float var1, float var2) {
        if (!(var1 > 0.0F)) {
            return 0.0F;
        }

        if (this.reduction(1.0F, var2) <= var1) {
            return 1.0F;
        }

        float var3 = 0.0F;
        float var4 = 1.0F;

        for (int var5 = 0; var5 < 14; var5++) {
            float var6 = 0.5F * (var3 + var4);
            if (this.reduction(var6, var2) >= var1) {
                var4 = var6;
            } else {
                var3 = var6;
            }
        }

        return var4;
    }

    boolean prepare(float var1, Vector3f var2, Vector3f var3, float var4) {
        if (!(var1 > 1.0E-4F)) {
            this.lastPitch = this.lastYaw = this.lastBack = 0.0F;
            return false;
        }

        this.Fh.set(var2.x, 0.0F, var2.z);
        if (this.Fh.lengthSquared() < 1.0E-6F) {
            this.Fh.set(var2);
        } else {
            this.Fh.normalize();
        }

        var2.cross(this.UP, this.Rh);
        if (this.Rh.lengthSquared() < 1.0E-6F) {
            this.Rh.set(1.0F, 0.0F, 0.0F);
        } else {
            this.Rh.normalize();
        }

        float var5 = this.pitchMax * pitchShare(var1);
        float var6 = 0.0F;
        if (var3.lengthSquared() > 1.0E-6F) {
            this.Np.set(var3).fma(-var3.dot(var2), var2);
            float var7 = Math.min(1.0F, this.Np.length());
            if (var7 > 0.05F) {
                this.UP.cross(var2, this.T);
                float var8 = Math.signum(this.T.dot(this.Np));
                var6 = var8 * this.yawMax * var7 * clamp01(var1 / 0.6F);
            }
        }

        if (this.pistol) {
            var5 = -var5;
        }

        this.Q.identity().rotateAxis(var6, 0.0F, 1.0F, 0.0F).rotateAxis(var5, this.Rh.x, this.Rh.y, this.Rh.z);
        this.lastPitch = var5;
        this.lastYaw = var6;
        this.lastBack = this.backMax * var4 * backShare(var1) / 1.5F;
        return true;
    }

    void prepareLowered(Vector3f var1, float var2) {
        this.Fh.set(var1.x, 0.0F, var1.z);
        if (this.Fh.lengthSquared() < 1.0E-6F) {
            this.Fh.set(var1);
        } else {
            this.Fh.normalize();
        }

        var1.cross(this.UP, this.Rh);
        if (this.Rh.lengthSquared() < 1.0E-6F) {
            this.Rh.set(1.0F, 0.0F, 0.0F);
        } else {
            this.Rh.normalize();
        }

        this.Q.identity().rotateAxis(-var2, this.Rh.x, this.Rh.y, this.Rh.z);
        this.lastPitch = -var2;
        this.lastYaw = 0.0F;
        this.lastBack = 0.0F;
    }

    Matrix4f correction(Vector3f var1, float var2, Matrix4f var3) {
        if (var2 >= 0.999F) {
            this.QW.set(this.Q);
        } else {
            this.QI.identity().slerp(this.Q, var2, this.QW);
        }

        float var4 = this.lastBack * var2;
        var3.identity().translate(-this.Fh.x * var4, -this.Fh.y * var4, -this.Fh.z * var4).translate(var1).rotate(this.QW).translate(-var1.x, -var1.y, -var1.z);
        return var3;
    }

    float pitchDeg() {
        return (float)Math.toDegrees(this.lastPitch);
    }

    float yawDeg() {
        return (float)Math.toDegrees(this.lastYaw);
    }

    static float extraStart(float var0, boolean var1) {
        return var1 ? 0.0F : 0.03F + 0.04F * clamp01((var0 - 0.5F) / 0.7F);
    }

    static float clamp01(float var0) {
        return var0 < 0.0F ? 0.0F : (var0 > 1.0F ? 1.0F : var0);
    }

    static float smooth(float var0) {
        return var0 * var0 * (3.0F - 2.0F * var0);
    }
}
