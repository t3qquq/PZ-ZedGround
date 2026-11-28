package t3zedballistics.ads;

import java.util.Locale;
import java.util.Objects;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import zombie.core.skinnedmodel.model.ModelInstance;
import zombie.core.skinnedmodel.model.ModelInstanceRenderData;
import zombie.scripting.objects.ModelAttachment;

final class WeaponDims {
    float rearY;
    float muzzleY;
    float boreX;
    float boreZ;
    String source = "none";
    float extraM;
    private ModelInstance cachedFor;
    private int cachedParts = -1;
    private float cachedScale = Float.NaN;
    private String cachedKey;
    private String cachedFamily;
    private float cachedOverride;
    private float cachedGenLength;
    private final Matrix4f LO = new Matrix4f();
    private final Matrix4f LA = new Matrix4f();
    private final Vector3f tv = new Vector3f();

    static float familyLength(String var0) {
        if (var0 == null) {
            return 0.85F;
        }

        switch (var0) {
            case "pistol":
                return 0.22F;
            case "smg":
                return 0.55F;
            case "carbine":
                return 0.85F;
            case "assault":
                return 0.95F;
            case "shotgun":
                return 1.05F;
            case "battlerifle":
                return 1.05F;
            case "machinegun":
                return 1.15F;
            case "sniper":
                return 1.2F;
            default:
                return 0.85F;
        }
    }

    void invalidate() {
        this.cachedFor = null;
        this.cachedParts = -1;
        this.cachedKey = null;
    }

    boolean resolve(ModelInstance var1, float var2, WeaponDims.Info var3) {
        if (var1 != null && var3 != null && var2 > 1.0E-6F) {
            int var4 = partsSignature(var1);
            if (var1 == this.cachedFor
                && var4 == this.cachedParts
                && Math.abs(var2 - this.cachedScale) < 1.0E-4F
                && var3.overrideM == this.cachedOverride
                && var3.genLength == this.cachedGenLength
                && Objects.equals(var3.key, this.cachedKey)
                && Objects.equals(var3.family, this.cachedFamily)) {
                return !"none".equals(this.source);
            }

            this.cachedFor = var1;
            this.cachedParts = var4;
            this.cachedScale = var2;
            this.cachedKey = var3.key;
            this.cachedFamily = var3.family;
            this.cachedOverride = var3.overrideM;
            this.cachedGenLength = var3.genLength;
            this.extraM = var3.muzzleOffsetM;
            float var5 = var2 * 1.5F;
            ModelAttachment var6 = var1.getAttachmentById("muzzle");
            boolean var7 = var6 != null && var6.getOffset() != null;
            this.boreX = var7 ? var6.getOffset().x : 0.0F;
            this.boreZ = var7 ? var6.getOffset().z : 0.0F;
            float[] var8 = this.meshBounds(var1);
            if (var3.overrideM > 0.05F) {
                float var11 = var7 ? var6.getOffset().y : (var8 != null ? var8[1] : Float.NaN);
                if (Float.isNaN(var11)) {
                    var11 = var3.genMuzzleY > 0.0F ? var3.genMuzzleY : var3.overrideM / var5 * 0.6F;
                }

                this.muzzleY = var11;
                this.rearY = var11 - var3.overrideM / var5;
                this.source = "override";
                return this.log(var3);
            } else {
                if (var8 != null) {
                    float var9 = (var8[1] - var8[0]) * var5;
                    if (var9 > 0.1F && var9 < 2.2F) {
                        this.rearY = var8[0];
                        this.muzzleY = var8[1];
                        if (!var7) {
                            this.boreX = var8[2];
                            this.boreZ = var8[3];
                        }

                        this.source = "model";
                        return this.log(var3);
                    }
                }

                if (var3.genLength > 0.05F) {
                    this.muzzleY = var7 ? var6.getOffset().y : (var3.genMuzzleY > 0.0F ? var3.genMuzzleY : var3.genLength * 0.6F);
                    this.rearY = this.muzzleY - var3.genLength;
                    this.source = "profile";
                    return this.log(var3);
                } else {
                    float var10 = familyLength(var3.family);
                    this.muzzleY = var7 ? var6.getOffset().y : var10 / var5 * 0.6F;
                    this.rearY = this.muzzleY - var10 / var5;
                    this.source = "family " + var3.family;
                    return this.log(var3);
                }
            }
        } else {
            return false;
        }
    }

    private boolean log(WeaponDims.Info var1) {
        float var2 = (this.muzzleY - this.rearY) * this.cachedScale * 1.5F;
        Log.dev(String.format(Locale.ROOT, "presence: %s length %.2f m (%s), bore x %.3f z %.3f", var1.key, var2, this.source, this.boreX, this.boreZ));
        return true;
    }

    float lengthM(float var1) {
        return Math.max(0.1F, (this.muzzleY - this.rearY) * var1 * 1.5F + this.extraM);
    }

    private static int partsSignature(ModelInstance var0) {
        int var1 = var0.sub.size();

        for (int var2 = 0; var2 < var0.sub.size(); var2++) {
            var1 = var1 * 31 + System.identityHashCode(var0.sub.get(var2));
        }

        return var1;
    }

    private float[] meshBounds(ModelInstance var1) {
        float[] var2 = var1.model != null ? MeshGeo.verts(var1.model.mesh) : null;
        if (var2 != null && var2.length >= 9) {
            float var3 = Float.MAX_VALUE;
            float var4 = -Float.MAX_VALUE;

            for (byte var5 = 1; var5 < var2.length; var5 += 3) {
                var3 = Math.min(var3, var2[var5]);
                var4 = Math.max(var4, var2[var5]);
            }

            for (int var10 = 0; var10 < var1.sub.size(); var10++) {
                ModelInstance var6 = (ModelInstance)var1.sub.get(var10);
                if (var6 != null && var6.model != null && MeshGeo.captured(var6.model.mesh)) {
                    ModelAttachment var7 = var6.attachmentNameParent != null ? var1.getAttachmentById(var6.attachmentNameParent) : null;
                    if (var7 != null) {
                        float[] var8 = MeshGeo.verts(var6.model.mesh);
                        if (var8 != null) {
                            this.partTransform(var6, var7);

                            for (byte var9 = 0; var9 + 2 < var8.length; var9 += 3) {
                                this.LO.transformPosition(this.tv.set(var8[var9], var8[var9 + 1], var8[var9 + 2]));
                                var3 = Math.min(var3, this.tv.y);
                                var4 = Math.max(var4, this.tv.y);
                            }
                        }
                    }
                }
            }

            if (!(var4 > var3)) {
                return null;
            }

            float var11 = var4 - 0.15F * (var4 - var3);
            float var12 = 0.0F;
            float var13 = 0.0F;
            int var14 = 0;

            for (byte var15 = 0; var15 + 2 < var2.length; var15 += 3) {
                if (var2[var15 + 1] >= var11) {
                    var12 += var2[var15];
                    var13 += var2[var15 + 2];
                    var14++;
                }
            }

            return new float[]{var3, var4, var14 > 0 ? var12 / var14 : 0.0F, var14 > 0 ? var13 / var14 : 0.0F};
        } else {
            return null;
        }
    }

    private void partTransform(ModelInstance var1, ModelAttachment var2) {
        this.LO.identity();
        ModelInstanceRenderData.makeAttachmentTransform(var2, this.LA);
        this.LO.mul(this.LA);
        ModelAttachment var3 = var1.getAttachmentById(var1.attachmentNameSelf);
        if (var3 == null && var1.parentBoneName != null) {
            var3 = var1.getAttachmentById(var1.parentBoneName);
        }

        if (var3 != null) {
            ModelInstanceRenderData.makeAttachmentTransform(var3, this.LA);
            if (ModelInstanceRenderData.invertAttachmentSelfTransform) {
                this.LA.invert();
            }

            this.LO.mul(this.LA);
        }

        ModelInstanceRenderData.postMultiplyMeshTransform(this.LO, var1.model.mesh);
        if (var1.scale != 0.0F && var1.scale != 1.0F) {
            this.LO.scale(var1.scale);
        }
    }

    static final class Info {
        String key = "";
        String family = "carbine";
        float genLength;
        float genMuzzleY;
        float overrideM;
        float muzzleOffsetM;
    }
}
