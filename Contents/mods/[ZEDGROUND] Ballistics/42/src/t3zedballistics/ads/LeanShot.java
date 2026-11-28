package t3zedballistics.ads;

import zombie.characters.IsoPlayer;
import zombie.iso.Vector3;

public final class LeanShot {
    private static final float[] L = new float[4];
    private static volatile boolean broken;

    private static boolean lean(Object var0) {
        if (!broken && var0 != null && var0 == IsoPlayer.players[0]) {
            try {
                return !VP.movementOffsets(L, true) ? false : L[0] != 0.0F || L[1] != 0.0F;
            } catch (Throwable var2) {
                broken = true;
                Log.error("lean shot", var2);
                return false;
            }
        } else {
            return false;
        }
    }

    public static float originX(Object var0, float var1) {
        return lean(var0) ? var1 + L[0] : var1;
    }

    public static float originY(Object var0, float var1) {
        return lean(var0) ? var1 + L[1] : var1;
    }

    public static void afterMuzzle(Object var0, Object var1) {
        IsoPlayer var2 = IsoPlayer.players[0];
        if (var2 != null && var0 != null && var1 instanceof Vector3 && var2.getBallisticsController() == var0) {
            if (lean(var2)) {
                Vector3 var3 = (Vector3)var1;
                var3.x = var3.x + L[0];
                var3.y = var3.y + L[1];
                var3.z = var3.z - L[2] / 2.4494896F;
            }
        }
    }

    private LeanShot() {
    }
}
