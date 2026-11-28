package t3gunworks.ads;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;

public final class AimPick {
    static final double MIN_DIST = 1.2;
    private static Field aimHit;
    private static Method hx;
    private static Method hy;
    private static volatile boolean broken;
    static volatile int dropped;

    public static void beforeSnapshot(Object var0) {
        if (!broken && ADS.eShared > 0.02F && var0 != null && var0 == IsoPlayer.players[0]) {
            try {
                if (aimHit == null) {
                    Class var1 = VP.cls("viewpoint.render.MousePick");
                    aimHit = var1.getField("aimHit");
                }

                Object var8 = aimHit.get(null);
                if (var8 == null) {
                    return;
                }

                if (hx == null) {
                    hx = var8.getClass().getMethod("x");
                    hy = var8.getClass().getMethod("y");
                }

                IsoGameCharacter var2 = (IsoGameCharacter)var0;
                double var3 = ((Number)hx.invoke(var8)).doubleValue() - var2.getX();
                double var5 = ((Number)hy.invoke(var8)).doubleValue() - var2.getY();
                if (var3 * var3 + var5 * var5 < 1.44) {
                    aimHit.set(null, null);
                    dropped++;
                }
            } catch (Throwable var7) {
                broken = true;
                Log.error("aim pick (shots may converge on the gun while ADS)", var7);
            }
        }
    }

    private AimPick() {
    }
}
