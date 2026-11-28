package t3zedballistics.aiming;

import t3zedballistics.Log;
import t3zedballistics.ads.Sight;
import zombie.network.GameServer;

/**
 * Link to the absorbed Recoil &amp; ADS code (t3zedballistics.ads).
 *
 * While the sights are raised, ADS publishes the line the player actually looks along - the gun's sight
 * axis after sway, recoil kick and weapon inertia, i.e. exactly where the red dot / reticle is drawn - plus the
 * sight's zero distance (Sight.ray(double[7]): origin x,y,u; unit direction x,y,u; zero metres).
 * True Ballistics then aims along that line instead of the screen centre, and zeroes to that sight.
 * Client only: on a dedicated server this always reports "no sight" without loading the ADS code.
 */
public final class AdsSightBridge {
    private static boolean failed, announced;

    public static boolean available() {
        return !GameServer.server && !failed;
    }

    /** Fills out (length >= 7) and returns true when the ADS sights are up and fresh. */
    public static boolean ray(double[] out) {
        if (!available()) return false;
        try {
            if (!announced) {
                announced = true;
                Log.info("ADS sight link ready: aiming follows the raised sights and their zero.");
            }
            return Sight.ray(out);
        } catch (Throwable t) {
            failed = true;
            Log.error("ADS sight link failed; aiming falls back to the screen centre", t);
            return false;
        }
    }

    private AdsSightBridge() {}
}
