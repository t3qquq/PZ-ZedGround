package t3gunworks.api;

import java.util.concurrent.CopyOnWriteArrayList;

import t3gunworks.Log;

/**
 * Lets other mods put their own objects in a round's way (added in 1.4.1). Project Viewpoint: Physics Props uses it
 * so a shot prop is pushed by the round's momentum.
 *
 * <p>Every straight piece of a round's flight (on the machine that flies it: single player, or the shooter's client)
 * first asks each registered {@link Target} where along it the round would touch one of its objects. When the earliest
 * of those comes before the wall, body or vehicle the round would otherwise reach, that target's {@link Target#hit}
 * runs and decides whether the round stops there. Test rounds never ask.
 *
 * <p>Metric space as in {@link ImpactEvents}: x, y in tiles (= metres), u = height in metres (z * 2.4494896). Calls
 * come on the game thread; exceptions are caught and logged once.
 */
public final class ObjectHits {

    /** Objects that can stand in a round's way. */
    public interface Target {
        /** Where along a to b (0..1) the round first touches one of this mod's objects, or a negative number. */
        double sweep(double ax, double ay, double au, double bx, double by, double bu);

        /**
         * The round reached what the last {@link #sweep} found, at (x, y, u), flying along the unit direction (dirX,
         * dirY, dirU) at speed m/s with a projectile of massGrams; shooter is the IsoGameCharacter who fired (may be
         * null). Return true when the round stops there, false when it flies on.
         */
        boolean hit(double x, double y, double u, double dirX, double dirY, double dirU, double speed, double massGrams,
                    Object shooter);
    }

    /**
     * Optional (added in 1.4.4): a mod that models a tile's furniture precisely (and reports it through its
     * {@link Target}) can claim the tile, so True Ballistics drops its own coarse furniture box there (a box inset
     * 0.15 m from the tile edges, 0.9 m or 1.9 m tall) and only the mod's real shape stops rounds. Trees, walls,
     * doors, windows and floors are never affected.
     */
    public interface Tiles {
        /** True when this mod's objects represent all the solid furniture on tile (x, y, level z). */
        boolean covers(int x, int y, int z);
    }

    private static final CopyOnWriteArrayList<Target> TARGETS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<Tiles> TILES = new CopyOnWriteArrayList<>();
    private static boolean tilesFailed;

    public static void registerTiles(Tiles t) {
        if (t != null && !TILES.contains(t)) {
            TILES.add(t);
            Log.info("object hits: " + t + " models furniture tiles");
        }
    }

    public static void unregisterTiles(Tiles t) { TILES.remove(t); }

    /** True when a registered mod claims tile (x, y, z) (see {@link Tiles}). Game thread. */
    public static boolean covers(int x, int y, int z) {
        if (TILES.isEmpty()) return false;
        for (Tiles t : TILES) {
            try {
                if (t.covers(x, y, z)) return true;
            } catch (Throwable e) {
                if (!tilesFailed) { tilesFailed = true; Log.error("object hits covers (" + t + ")", e); }
            }
        }
        return false;
    }
    private static Target last;
    private static boolean sweepFailed, hitFailed;

    public static void register(Target t) {
        if (t != null && !TARGETS.contains(t)) {
            TARGETS.add(t);
            Log.info("object hits: " + t + " registered");
        }
    }

    public static void unregister(Target t) { TARGETS.remove(t); }

    public static boolean active() { return !TARGETS.isEmpty(); }

    /** The earliest touch along a to b over every target (0..1), or -1; remembers whose it is for {@link #hit}. */
    public static double sweep(double ax, double ay, double au, double bx, double by, double bu) {
        double best = -1;
        last = null;
        for (Target t : TARGETS) {
            double r;
            try {
                r = t.sweep(ax, ay, au, bx, by, bu);
            } catch (Throwable e) {
                if (!sweepFailed) { sweepFailed = true; Log.error("object hits sweep (" + t + ")", e); }
                continue;
            }
            if (r >= 0 && r <= 1 && (best < 0 || r < best)) { best = r; last = t; }
        }
        return best;
    }

    /**
     * Like {@link #sweep} but for looking only (the aim ray through the crosshair): the remembered target is
     * left as it was, so a following {@link #hit} still belongs to the round's own last sweep.
     */
    public static double peek(double ax, double ay, double au, double bx, double by, double bu) {
        Target keep = last;
        try {
            return sweep(ax, ay, au, bx, by, bu);
        } finally {
            last = keep;
        }
    }

    /** Hands the hit to the target the last {@link #sweep} found. True when the round stops. */
    public static boolean hit(double x, double y, double u, double dirX, double dirY, double dirU, double speed, double massGrams,
                              Object shooter) {
        Target t = last;
        last = null;
        if (t == null) return false;
        try {
            return t.hit(x, y, u, dirX, dirY, dirU, speed, massGrams, shooter);
        } catch (Throwable e) {
            if (!hitFailed) { hitFailed = true; Log.error("object hits hit (" + t + ")", e); }
            return true;
        }
    }

    private ObjectHits() {}
}
