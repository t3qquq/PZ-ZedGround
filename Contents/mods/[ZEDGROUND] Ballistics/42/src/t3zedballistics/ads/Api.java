package t3zedballistics.ads;

import java.util.ArrayList;
import java.util.Locale;
import me.zed_0xff.zombie_buddy.Exposer.LuaClass;
import se.krka.kahlua.integration.annotations.LuaMethod;
import se.krka.kahlua.vm.KahluaTable;
import zombie.characters.IsoPlayer;
import zombie.core.skinnedmodel.model.ModelInstance;
import zombie.core.textures.Texture;
import zombie.scripting.objects.ModelAttachment;


public final class Api {
    @LuaMethod(name = "PVADS_version", global = true)
    public static String version() {
        return "1.9.1";
    }

    @LuaMethod(name = "PVADS_available", global = true)
    public static Boolean available() {
        return VP.ok;
    }

    @LuaMethod(name = "PVADS_viewOn", global = true)
    public static Boolean viewOn() {
        return VP.viewOn();
    }

    @LuaMethod(name = "PVADS_firstPerson", global = true)
    public static Boolean firstPerson() {
        return VP.firstPerson();
    }

    @LuaMethod(name = "PVADS_setWant", global = true)
    public static void setWant(Object var0) {
        ADS.setWant(bool(var0, false));
    }

    @LuaMethod(name = "PVADS_setSuspended", global = true)
    public static void setSuspended(Object var0) {
        ADS.suspended = bool(var0, false);
    }

    @LuaMethod(name = "PVADS_setClickAim", global = true)
    public static void setClickAim(Object var0) {
        AimClick.enabled = bool(var0, false);
        if (!AimClick.enabled) {
            AimClick.clear();
        }
    }

    @LuaMethod(name = "PVADS_setAimAllowed", global = true)
    public static void setAimAllowed(Object var0, Object var1) {
        AimClick.blockedWhy = var1 != null ? var1.toString() : "";
        AimClick.allowed = bool(var0, false);
    }

    @LuaMethod(name = "PVADS_optionsReady", global = true)
    public static Boolean optionsReady() {
        return Options.init();
    }

    @LuaMethod(name = "PVADS_getOptions", global = true)
    public static KahluaTable getOptions(KahluaTable var0) {
        Options.fill(var0);
        return var0;
    }

    @LuaMethod(name = "PVADS_optionsSignature", global = true)
    public static Double optionsSignature() {
        return Options.signature();
    }

    @LuaMethod(name = "PVADS_aimLocked", global = true)
    public static Boolean aimLocked() {
        return AimClick.locked;
    }

    @LuaMethod(name = "PVADS_clearAimLock", global = true)
    public static void clearAimLock() {
        AimClick.clear();
    }

    @LuaMethod(name = "PVADS_canAds", global = true)
    public static Boolean canAds() {
        return ADS.canAds();
    }

    @LuaMethod(name = "PVADS_setDeveloper", global = true)
    public static void setDeveloper(Object var0) {
        Log.dev = bool(var0, false);
    }

    @LuaMethod(name = "PVADS_setSwayFactor", global = true)
    public static void setSwayFactor(Object var0) {
        // ZEDGROUND: ignored. The sway multiplier is computed in Java from the server's sandbox (Gunplay.stability).
    }

    @LuaMethod(name = "PVADS_setOpticShape", global = true)
    public static void setOpticShape(Object var0, Object var1, Object var2, Object var3) {
        if (var0 != null) {
            ADS.opticShapes.put(ADS.shortName(var0.toString()), new float[]{(float)num(var1, 0.03), (float)num(var2, 0.12), (float)num(var3, 0.0)});
        }
    }

    @LuaMethod(name = "PVADS_setScopeMag", global = true)
    public static void setScopeMag(Object var0, Object var1) {
        ADS.setMagLevel((float)Math.max(0.0, Math.min(25.0, num(var0, 0.0))), bool(var1, false));
    }

    @LuaMethod(name = "PVADS_setZero", global = true)
    public static void setZero(Object var0) {
        ADS.zeroLive = (float)Math.max(0.0, Math.min(1000.0, num(var0, 0.0)));
    }

    @LuaMethod(name = "PVADS_clearProfile", global = true)
    public static void clearProfile() {
        ADS.P.valid = false;
        Sight.clear();
        OpticProfiles.forget();
    }

    @LuaMethod(name = "PVADS_setProfile", global = true)
    public static void setProfile(KahluaTable var0) {
        if (var0 == null) {
            ADS.P.valid = false;
            OpticProfiles.forget();
        } else {
            ADS.Profile var1 = ADS.P;
            String var2 = str(var0, "weaponId", "");
            String var3 = str(var0, "opticId", "");
            if (!var2.equals(var1.weaponId) || !var3.equals(var1.opticIdStr)) {
                OpticProfiles.forget();
            }

            var1.opticIdStr = var3;
            var1.weaponId = var2;
            String var4 = str(var0, "mode", "iron");
            var1.mode = "scope".equals(var4) ? 2 : ("reflex".equals(var4) ? 1 : 0);
            var1.sx = f(var0, "sx", 0.0F);
            var1.sy = f(var0, "sy", 0.0F);
            var1.sz = f(var0, "sz", 0.03F);
            var1.dx = f(var0, "dx", 0.0F);
            var1.dy = f(var0, "dy", 1.0F);
            var1.dz = f(var0, "dz", 0.0F);
            var1.opticHeight = f(var0, "opticHeight", 0.03F);
            var1.opticWindow = f(var0, "opticWindow", 0.012F);
            var1.opticLength = f(var0, "opticLength", 0.12F);
            var1.adjX = f(var0, "adjX", 0.0F);
            var1.adjY = f(var0, "adjY", 0.0F);
            var1.adjZ = f(var0, "adjZ", 0.0F);
            var1.adjPitch = f(var0, "adjPitch", 0.0F);
            var1.adjYaw = f(var0, "adjYaw", 0.0F);
            var1.adjRoll = f(var0, "adjRoll", 0.0F);
            var1.relief = f(var0, "relief", 0.12F);
            var1.minRelief = clamp(f(var0, "minRelief", 0.0F), 0.0F, 0.4F);
            var1.ironAuto = b(var0, "ironAuto", true);
            var1.adsTime = Math.max(0.05F, f(var0, "adsTime", 0.25F));
            var1.fovMul = clamp(f(var0, "fovMul", 0.9F), 0.3F, 1.2F);
            var1.mag = clamp(f(var0, "mag", 1.0F), 1.0F, 25.0F);
            var1.swayMul = f(var0, "swayMul", 1.0F);
            var1.recoilMul = f(var0, "recoilMul", 1.0F);
            var1.kickMul = f(var0, "kickMul", 1.0F);
            var1.climb = f(var0, "climb", 1.0F);
            var1.side = f(var0, "side", 0.5F);
            var1.recover = f(var0, "recover", 0.4F);
            var1.gunKick = f(var0, "gunKick", 3.0F);
            var1.gunBack = f(var0, "gunBack", 0.02F);
            var1.hipCone = f(var0, "hipCone", 2.5F);
            var1.bead = b(var0, "bead", false);
            float var5 = f(var0, "glassX0", Float.NaN);
            var1.glass = Float.isNaN(var5)
                ? null
                : new float[]{
                    var5,
                    f(var0, "glassX1", 0.0F),
                    f(var0, "glassZ0", 0.0F),
                    f(var0, "glassZ1", 0.0F),
                    f(var0, "glassY", 0.0F),
                    b(var0, "glassRound", false) ? 1.0F : 0.0F
                };
            var1.glassMesh = null;
            var1.glassVar = null;
            var1.glassVarPip = null;
            var1.glassVarPoly = null;
            var1.glassPoly = floats(var0.rawget("glassPoly"));
            var1.pipLocked = b(var0, "pipLocked", false);
            if (var0.rawget("glassVariants") instanceof KahluaTable var7) {
                ArrayList var8 = new ArrayList();
                ArrayList var9 = new ArrayList();
                ArrayList var10 = new ArrayList();
                ArrayList var11 = new ArrayList();

                for (int var12 = 1; var12 <= var7.len(); var12++) {
                    Object var13 = var7.rawget(var12);
                    if (var13 instanceof KahluaTable) {
                        Object var14 = ((KahluaTable)var13).rawget("mesh");
                        Object var15 = ((KahluaTable)var13).rawget("glass");
                        if (var14 instanceof String && var15 instanceof KahluaTable && ((KahluaTable)var15).len() >= 5) {
                            KahluaTable var16 = (KahluaTable)var15;
                            float[] var17 = new float[6];

                            for (int var18 = 0; var18 < 5; var18++) {
                                Object var19 = var16.rawget(var18 + 1);
                                var17[var18] = var19 instanceof Number ? ((Number)var19).floatValue() : 0.0F;
                            }

                            var17[5] = Boolean.TRUE.equals(var16.rawget(6)) ? 1.0F : 0.0F;
                            KahluaTable var33 = (KahluaTable)var13;
                            var10.add(
                                new float[]{
                                    f(var33, "pipScale", Float.NaN),
                                    f(var33, "pipX", Float.NaN),
                                    f(var33, "pipY", Float.NaN),
                                    f(var33, "pipCorner", Float.NaN),
                                    f(var33, "pipAspect", Float.NaN)
                                }
                            );
                            var8.add(meshKey((String)var14));
                            var9.add(var17);
                            var11.add(floats(var33.rawget("poly")));
                        }
                    }
                }

                if (!var8.isEmpty()) {
                    var1.glassMesh = (String[]) var8.toArray(new String[0]);
                    var1.glassVar = (float[][]) var9.toArray(new float[0][]);
                    var1.glassVarPip = (float[][]) var10.toArray(new float[0][]);
                    var1.glassVarPoly = (float[][]) var11.toArray(new float[0][]);
                }
            }

            var1.ironMesh = null;
            var1.ironVar = null;
            var1.ironLocked = b(var0, "ironLocked", false);
            if (var0.rawget("ironVariants") instanceof KahluaTable var21) {
                ArrayList var23 = new ArrayList();
                ArrayList var25 = new ArrayList();

                for (int var27 = 1; var27 <= var21.len(); var27++) {
                    if (var21.rawget(var27) instanceof KahluaTable var31) {
                        Object var32 = var31.rawget("mesh");
                        if (var32 instanceof String) {
                            var23.add(meshKey((String)var32));
                            var25.add(
                                new float[]{
                                    f(var31, "adjX", 0.0F),
                                    f(var31, "adjY", 0.0F),
                                    f(var31, "adjZ", 0.0F),
                                    f(var31, "adjPitch", 0.0F),
                                    f(var31, "adjYaw", 0.0F),
                                    f(var31, "minRelief", 0.0F)
                                }
                            );
                        }
                    }
                }

                if (!var23.isEmpty()) {
                    var1.ironMesh = (String[]) var23.toArray(new String[0]);
                    var1.ironVar = (float[][]) var25.toArray(new float[0][]);
                }
            }

            var1.lensScale = f(var0, "lensScale", 1.0F);
            var1.windowScale = f(var0, "windowScale", 1.0F);
            var1.zero = clamp(f(var0, "zero", 0.0F), 0.0F, 1000.0F);
            var1.family = str(var0, "family", "carbine");
            var1.genLength = clamp(f(var0, "genLength", 0.0F), 0.0F, 5.0F);
            var1.genMuzzleY = clamp(f(var0, "genMuzzleY", 0.0F), 0.0F, 5.0F);
            var1.lengthOverrideM = clamp(f(var0, "weaponLength", 0.0F), 0.0F, 2.5F);
            var1.muzzleOffsetM = clamp(f(var0, "muzzleOffset", 0.0F), -0.5F, 0.8F);
            var1.collisionRadiusM = clamp(f(var0, "collisionRadius", 0.0F), 0.0F, 0.2F);
            var1.poseScale = clamp(f(var0, "compressedPoseScale", 1.0F), 0.3F, 1.8F);
            var1.pipX = clamp(f(var0, "pipX", 0.0F), -1.0F, 1.0F);
            var1.pipY = clamp(f(var0, "pipY", 0.0F), -1.0F, 1.0F);
            var1.pipScale = clamp(f(var0, "pipScale", 1.0F), 0.3F, 2.0F);
            var1.pipZoom = clamp(f(var0, "pipZoom", 1.0F), 0.3F, 3.0F);
            var1.pipRetX = clamp(f(var0, "pipRetX", 0.0F), -1.0F, 1.0F);
            var1.pipRetY = clamp(f(var0, "pipRetY", 0.0F), -1.0F, 1.0F);
            var1.pipDepth = clamp(f(var0, "pipDepth", 0.0F), -0.3F, 0.5F);
            var1.pipZ = clamp(f(var0, "pipZ", 0.0F), -0.3F, 0.3F);
            var1.pipCorner = clamp(f(var0, "pipCorner", -1.0F), -1.0F, 1.0F);
            var1.pipAspect = clamp(f(var0, "pipAspect", 1.0F), 0.4F, 2.5F);
            var1.reticleRoll = clamp(f(var0, "reticleRoll", 0.0F), -180.0F, 180.0F);
            var1.opticRear = null;
            var1.opticFront = null;
            var1.userWeapon = null;
            Object var22 = var0.rawget("userWeapon");
            if (var22 instanceof KahluaTable) {
                float[] var24 = OpticProfiles.blank();
                boolean var26 = false;

                for (int var28 = 0; var28 < OpticProfiles.N; var28++) {
                    Object var30 = ((KahluaTable)var22).rawget(OpticProfiles.FIELDS[var28]);
                    if (var30 instanceof Number) {
                        var24[var28] = ((Number)var30).floatValue();
                        var26 = true;
                    }
                }

                if (var26) {
                    var1.userWeapon = var24;
                }
            }

            var1.basePipLocked = var1.pipLocked;
            var1.base = OpticProfiles.read(var1);
            var1.serial++;
            Presence.DIMS.invalidate();
            ADS.setMagLevel(0.0F, true);
            ADS.zeroLive = 0.0F;
            var1.valid = true;
            Log.dev("profile " + var1.weaponId + " mode=" + var4 + " relief=" + var1.relief + " mag=" + var1.mag);
        }
    }

    @LuaMethod(name = "PVADS_setOpticModelProfile", global = true)
    public static void setOpticModelProfile(Object var0, KahluaTable var1) {
        if (var0 != null) {
            OpticProfiles.setShipped(var0.toString(), var1);
        }
    }

    @LuaMethod(name = "PVADS_opticKey", global = true)
    public static String opticKey() {
        return OpticProfiles.curKey;
    }

    @LuaMethod(name = "PVADS_opticLabel", global = true)
    public static String opticLabel() {
        return OpticProfiles.label();
    }

    @LuaMethod(name = "PVADS_opticSource", global = true)
    public static String opticSource() {
        return OpticProfiles.curSource;
    }

    @LuaMethod(name = "PVADS_isOpticField", global = true)
    public static Boolean isOpticField(Object var0) {
        return var0 != null && OpticProfiles.isField(var0.toString());
    }

    @LuaMethod(name = "PVADS_effective", global = true)
    public static Double effective(Object var0) {
        float var1 = var0 == null ? Float.NaN : OpticProfiles.effective(var0.toString());
        return Float.isNaN(var1) ? null : (double)var1;
    }

    @LuaMethod(name = "PVADS_userOpticSet", global = true)
    public static Boolean userOpticSet(Object var0, Object var1) {
        return var0 != null && var1 instanceof Number && OpticProfiles.userSet(var0.toString(), ((Number)var1).floatValue());
    }

    @LuaMethod(name = "PVADS_userOpticClear", global = true)
    public static Boolean userOpticClear() {
        return OpticProfiles.userClear();
    }

    @LuaMethod(name = "PVADS_userOpticUndo", global = true)
    public static String userOpticUndo() {
        return OpticProfiles.undo();
    }

    @LuaMethod(name = "PVADS_userOpticSave", global = true)
    public static Boolean userOpticSave() {
        return OpticProfiles.save();
    }

    @LuaMethod(name = "PVADS_userOpticDirty", global = true)
    public static Boolean userOpticDirty() {
        return OpticProfiles.dirty();
    }

    @LuaMethod(name = "PVADS_userOpticFields", global = true)
    public static KahluaTable userOpticFields(KahluaTable var0) {
        OpticProfiles.fillUser(var0);
        return var0;
    }

    @LuaMethod(name = "PVADS_opticProfileFile", global = true)
    public static String opticProfileFile() {
        try {
            return OpticProfiles.file().getPath();
        } catch (Throwable var1) {
            return "?";
        }
    }

    @LuaMethod(name = "PVADS_setSettings", global = true)
    public static void setSettings(KahluaTable var0) {
        if (var0 != null) {
            ADS.Settings var1 = ADS.S;
            var1.enabled = b(var0, "enabled", true);
            var1.hideCrosshair = b(var0, "hideCrosshair", true);
            var1.adsSharp = b(var0, "adsSharp", true);
            var1.thirdAds = b(var0, "thirdAds", true);
            var1.closeClip = b(var0, "closeClip", true);
            var1.sway = b(var0, "sway", true);
            var1.cameraRecoil = b(var0, "cameraRecoil", true);
            var1.eyeBox = b(var0, "eyeBox", true);
            var1.inertia = b(var0, "inertia", true);
            var1.steady = b(var0, "steady", true);
            var1.rest = b(var0, "rest", true);
            var1.steadyZoom = clamp(f(var0, "steadyZoom", 1.2F), 1.0F, 1.6F);
            var1.speedMul = clamp(f(var0, "speedMul", 1.0F), 0.25F, 4.0F);
            var1.adsSens = clamp(f(var0, "adsSens", 0.85F), 0.1F, 2.0F);
            var1.scopeSens = clamp(f(var0, "scopeSens", 1.0F), 0.1F, 3.0F);
            var1.swayAmount = clamp(f(var0, "swayAmount", 1.0F), 0.0F, 3.0F);
            var1.recoilAmount = clamp(f(var0, "recoilAmount", 1.0F), 0.0F, 3.0F);
            var1.lensSize = clamp(f(var0, "lensSize", 0.84F), 0.3F, 1.2F);
            var1.reliefMul = clamp(f(var0, "ironDistance", 1.0F), 0.8F, 1.6F);
            var1.zoomMul = clamp(f(var0, "scopeZoom", 1.5F), 1.0F, 2.5F);
            var1.pip = b(var0, "pip", true);
            var1.hipSpread = b(var0, "hipSpread", true);
            Pip.quality = Math.max(0, Math.min(2, (int)f(var0, "pipQuality", 1.0F)));
            Presence.Config var2 = Presence.C;
            boolean var3 = var2.enabled;
            var2.enabled = b(var0, "presence", true);
            var2.startDist = clamp(f(var0, "presenceStart", 0.08F), 0.0F, 0.3F);
            var2.strength = clamp(f(var0, "presenceStrength", 1.0F), 0.5F, 1.5F);
            var2.smoothing = clamp(f(var0, "presenceSmoothing", 1.0F), 0.5F, 2.0F);
            var2.lowReady = b(var0, "lowReady", false);
            var2.lowReadyAmount = clamp(f(var0, "lowReadyAmount", 0.5F), 0.1F, 1.0F);
            var2.debug = b(var0, "presenceDebug", false);
            var2.others = b(var0, "presenceOthers", true);
            if (var3 && !var2.enabled) {
                Presence.reset();
            }
            Gunplay.enforce(var1);
        }
    }

    public static Double viewPitchDeg() {
        return Math.toDegrees(VP.pitch());
    }

    public static void setRemoteAim(Object var0, Object var1, Object var2) {
        try {
            RemoteAim.set((int)num(var0, -1.0), (float)num(var1, 0.0), bool(var2, false));
        } catch (Throwable var4) {
            Log.error("aim sync", var4);
        }
    }

    @LuaMethod(name = "PVADS_setSteady", global = true)
    public static void setSteady(Object var0) {
        ADS.steadyHeld = bool(var0, false);
    }

    @LuaMethod(name = "PVADS_getSteady", global = true)
    public static KahluaTable getSteady(KahluaTable var0) {
        if (var0 == null) {
            return null;
        }

        var0.rawset("steady", (double)ADS.steadyShared);
        var0.rawset("breath", (double)ADS.breathShared);
        return var0;
    }

    @LuaMethod(name = "PVADS_setBipod", global = true)
    public static void setBipod(Object var0) {
        Rest.bipodDeployed = bool(var0, false);
    }

    @LuaMethod(name = "PVADS_getRest", global = true)
    public static KahluaTable getRest(KahluaTable var0) {
        if (var0 == null) {
            return null;
        }

        var0.rawset("state", (double)Rest.state());
        var0.rawset("used", (double)ADS.restShared);
        var0.rawset("prone", Rest.prone);
        var0.rawset("surface", Rest.surface);
        var0.rawset("surfaceH", (double)Rest.surfaceH);
        var0.rawset("kind", Rest.surfaceKind);
        var0.rawset("sightsUp", ADS.eShared > 0.9F);
        return var0;
    }

    @LuaMethod(name = "PVADS_presenceTick", global = true)
    public static void presenceTick() {
        Presence.tick();
    }

    @LuaMethod(name = "PVADS_getPresence", global = true)
    public static KahluaTable getPresence(KahluaTable var0) {
        if (var0 == null) {
            return null;
        }

        var0.rawset("enabled", Presence.C.enabled);
        var0.rawset("debug", Presence.C.debug);
        var0.rawset("amount", (double)Presence.amount());
        var0.rawset("viewBlock", (double)Presence.viewBlock());
        var0.rawset("adsBlocked", Presence.adsBlocked());
        var0.rawset("need", (double)PresenceProbe.LOCAL.need);
        var0.rawset("type", PresenceProbe.TYPE_NAMES[Math.max(0, Math.min(5, PresenceProbe.LOCAL.hitType))]);
        var0.rawset("hitDist", (double)PresenceProbe.LOCAL.hitDist);
        var0.rawset("valid", Presence.dbgValid);
        var0.rawset("length", (double)Presence.dbgLen);
        var0.rawset("source", Presence.dbgSrc);
        var0.rawset("pitch", (double)Presence.dbgPitch);
        var0.rawset("yaw", (double)Presence.dbgYaw);
        var0.rawset("family", ADS.P.family);
        float[] var1 = Presence.dbgScreen;
        var0.rawset("rx", (double)var1[0]);
        var0.rawset("ry", (double)var1[1]);
        var0.rawset("mx", (double)var1[2]);
        var0.rawset("my", (double)var1[3]);
        var0.rawset("hx", (double)var1[4]);
        var0.rawset("hy", (double)var1[5]);
        var0.rawset("px", (double)var1[6]);
        var0.rawset("py", (double)var1[7]);
        return var0;
    }

    @LuaMethod(name = "PVADS_setPipReticle", global = true)
    public static void setPipReticle(Object var0, Object var1, Object var2, Object var3, Object var4, Object var5, Object var6, Object var7) {
        Pip.retTex = var0 instanceof Texture ? (Texture)var0 : null;
        Pip.glowTex = var1 instanceof Texture ? (Texture)var1 : null;
        Pip.retR = (float)num(var3, 1.0);
        Pip.retG = (float)num(var4, 0.0);
        Pip.retB = (float)num(var5, 0.0);
        Pip.retBright = (float)num(var6, 1.0);
        Pip.dotPx = (float)num(var7, 10.0);
        Pip.retMode = (int)num(var2, 0.0);
    }

    @LuaMethod(name = "PVADS_getState", global = true)
    public static KahluaTable getState(KahluaTable var0) {
        if (var0 == null) {
            return null;
        }

        var0.rawset("t", (double)ADS.viewE());
        var0.rawset("tIntent", (double)ADS.eShared);
        var0.rawset("state", (double)ADS.state);
        var0.rawset("visible", ADS.ovVisible);
        var0.rawset("lensUp", ADS.lensUp);
        var0.rawset("mode", (double)ADS.ovMode);
        var0.rawset("mag", (double)ADS.P.mag);
        var0.rawset("magNow", (double)ADS.magNow());
        var0.rawset("zero", (double)ADS.zeroNow());
        var0.rawset("dotX", (double)ADS.ovDotX);
        var0.rawset("dotY", (double)ADS.ovDotY);
        var0.rawset("winX", (double)ADS.ovWinX);
        var0.rawset("winY", (double)ADS.ovWinY);
        var0.rawset("winR", (double)ADS.ovWinR);
        var0.rawset("lensX", (double)ADS.ovLensX);
        var0.rawset("lensY", (double)ADS.ovLensY);
        var0.rawset("lensR", (double)ADS.ovLensR);
        var0.rawset("pip", ADS.pipActive() && Pip.drewLastFrame);
        var0.rawset("pipMode", ADS.pipNow());
        var0.rawset("pipX", (double)Pip.lensCx);
        var0.rawset("pipY", (double)Pip.lensCy);
        var0.rawset("pipR", (double)Pip.lensR);
        var0.rawset("eyeBox", (double)ADS.ovEyeBox);
        var0.rawset("misalign", (double)ADS.ovMisalign);
        var0.rawset("fovIn", (double)ADS.fovIn);
        var0.rawset("fovOut", (double)ADS.fovOut);
        var0.rawset("issue", ADS.lastIssue);
        return var0;
    }

    @LuaMethod(name = "PVADS_probe", global = true)
    public static String probe() {
        StringBuilder var0 = new StringBuilder();

        try {
            IsoPlayer var1 = IsoPlayer.players[0];
            if (var1 == null) {
                return "no local player";
            }

            ModelInstance var2 = var1.primaryHandModel;
            var0.append("viewpoint=").append(VP.ok).append(" firstPerson=").append(VP.firstPerson());
            if (var2 == null) {
                return var0.append(" weaponModel=none").toString();
            }

            var0.append(" weaponModel=").append(var2.modelScript != null ? var2.modelScript.getName() : "?");
            var0.append(" parentBone=").append(var2.parentBoneName).append(" attachSelf=").append(var2.attachmentNameSelf);
            if (var2.modelScript != null) {
                var0.append(" attachments=[");

                for (int var3 = 0; var3 < var2.modelScript.getAttachmentCount(); var3++) {
                    ModelAttachment var4 = var2.modelScript.getAttachment(var3);
                    if (var3 > 0) {
                        var0.append(",");
                    }

                    var0.append(var4.getId());
                }

                var0.append("]");
            }

            var0.append(" parts=[");

            for (int var6 = 0; var6 < var2.sub.size(); var6++) {
                ModelInstance var8 = (ModelInstance)var2.sub.get(var6);
                if (var6 > 0) {
                    var0.append(",");
                }

                var0.append(var8.modelScript != null ? var8.modelScript.getName() : "?").append("@").append(var8.attachmentNameParent);
            }

            var0.append("] optic=");
            ModelInstance var7 = ADS.findOptic(var2);
            var0.append(var7 != null && var7.modelScript != null ? var7.modelScript.getName() : "none");
            var0.append(" state=").append(ADS.state).append(" t=").append(String.format("%.2f", ADS.eShared));
            var0.append(" misalign=").append(String.format("%.2f", ADS.ovMisalign));
            if (!ADS.lastIssue.isEmpty()) {
                var0.append(" issue=").append(ADS.lastIssue);
            }
        } catch (Throwable var5) {
            var0.append(" error=").append(var5);
        }

        return var0.toString();
    }

    static boolean bool(Object var0, boolean var1) {
        return var0 instanceof Boolean ? (Boolean)var0 : var1;
    }

    static double num(Object var0, double var1) {
        return var0 instanceof Number ? ((Number)var0).doubleValue() : var1;
    }

    static String meshKey(String var0) {
        return var0 == null ? "" : var0.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
    }

    static float[] floats(Object var0) {
        if (!(var0 instanceof KahluaTable var1)) {
            return null;
        } else {
            int var2 = var1.len();
            if (var2 >= 6 && var2 <= 48 && (var2 & 1) == 0) {
                float[] var3 = new float[var2];

                for (int var4 = 0; var4 < var2; var4++) {
                    Object var5 = var1.rawget(var4 + 1);
                    if (!(var5 instanceof Number)) {
                        return null;
                    }

                    var3[var4] = ((Number)var5).floatValue();
                }

                return var3;
            } else {
                return null;
            }
        }
    }

    static float f(KahluaTable var0, String var1, float var2) {
        Object var3 = var0.rawget(var1);
        return var3 instanceof Number ? ((Number)var3).floatValue() : var2;
    }

    static boolean b(KahluaTable var0, String var1, boolean var2) {
        Object var3 = var0.rawget(var1);
        return var3 instanceof Boolean ? (Boolean)var3 : var2;
    }

    static String str(KahluaTable var0, String var1, String var2) {
        Object var3 = var0.rawget(var1);
        return var3 != null ? var3.toString() : var2;
    }

    static float clamp(float var0, float var1, float var2) {
        return var0 < var1 ? var1 : (var0 > var2 ? var2 : var0);
    }

    private Api() {
    }
}
