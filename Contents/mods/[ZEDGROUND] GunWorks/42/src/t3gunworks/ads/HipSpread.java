package t3gunworks.ads;

import zombie.characters.IsoPlayer;
import zombie.characters.skills.PerkFactory.Perks;
import zombie.inventory.InventoryItem;
import zombie.inventory.types.HandWeapon;

public final class HipSpread {
    static volatile boolean active;
    static volatile float offYaw;
    static volatile float offPitch;
    static volatile long setNs;
    static float lastConeDeg;

    public static void enter(Object var0) {
        if (active) {
            restore();
        }

        try {
            if (!ADS.S.hipSpread || !ADS.S.enabled || !VP.ok || !VP.viewOn()) {
                return;
            }

            if (VpCompat.viewpointSpreadsHip()) {
                return;
            }

            IsoPlayer var1 = IsoPlayer.players[0];
            if (var1 == null || var0 != var1) {
                return;
            }

            InventoryItem var2 = var1.getPrimaryHandItem();
            if (!(var2 instanceof HandWeapon) || !((HandWeapon)var2).isRanged()) {
                return;
            }

            float var3 = 1.0F - ADS.viewE();
            if (var3 <= 0.02F) {
                return;
            }

            float var4 = ADS.P.hipCone * var3;
            float var5 = var1.isSprinting() ? 2.5F : (var1.isRunning() ? 2.0F : (var1.isPlayerMoving() ? 1.5F : 1.0F));
            int var6 = var1.getPerkLevel(Perks.Aiming);
            float var7 = Math.max(0.55F, 1.3F - 0.075F * var6);
            var4 *= var5 * var7;
            lastConeDeg = var4;
            if (!(var4 > 0.01F)) {
                return;
            }

            double var8 = Math.toRadians(var4) * Math.sqrt(Math.random());
            double var10 = Math.random() * Math.PI * 2.0;
            float var12 = VP.pitch();
            float var13 = (float)(var8 * Math.cos(var10) / Math.max(0.25, Math.cos(var12)));
            float var14 = (float)(var8 * Math.sin(var10) * 0.75);
            offYaw = var13;
            offPitch = var14;
            VP.setYaw(ADS.wrap(VP.yaw() + var13));
            VP.setPitch(var12 + var14);
            setNs = System.nanoTime();
            active = true;
        } catch (Throwable var15) {
            Log.error("hip spread", var15);
        }
    }

    public static void exit() {
        if (active) {
            restore();
        }
    }

    static void failsafe() {
        if (active && System.nanoTime() - setNs > 50000000L) {
            restore();
        }
    }

    private static void restore() {
        active = false;

        try {
            VP.setYaw(ADS.wrap(VP.yaw() - offYaw));
            VP.setPitch(VP.pitch() - offPitch);
        } catch (Throwable var1) {
            Log.error("hip spread restore", var1);
        }

        offYaw = 0.0F;
        offPitch = 0.0F;
    }

    private HipSpread() {
    }
}
