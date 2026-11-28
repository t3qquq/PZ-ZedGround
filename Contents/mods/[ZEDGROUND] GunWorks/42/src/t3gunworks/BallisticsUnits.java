package t3gunworks;

/**
 * The one place where Project Zomboid / Viewpoint coordinates are converted to the metric space all
 * ballistics run in. Nothing else in the mod multiplies by these constants.
 *
 * Verified facts (PZ 42.21, Viewpoint 0.1.5a):
 *  - World X/Y are measured in tiles. Viewpoint renders one tile as one unit and treats characters as
 *    1.8 units tall (viewpoint.input.CrosshairAim.TALL), i.e. one tile is one metre.
 *  - World Z is measured in floor levels. Viewpoint, the vanilla native ballistics code
 *    (CombatManager.fireWeapon divides by 2.44949) and Controls.eye all use sqrt(6) = 2.4494896 units
 *    per level, so one level is 2.449 m high.
 *
 * Metric space ("M"): x = world x (m), y = world y (m), u = world z * sqrt(6) (m, up).
 */
public final class BallisticsUnits {
    /** Height of one PZ floor level in metres (== Viewpoint's LEVEL constant). */
    public static final double LEVEL_HEIGHT_M = 2.4494896;

    public static double tilesToMeters(double tiles) { return tiles; }

    public static double metersToTiles(double m) { return m; }

    public static double levelsToMeters(double levels) { return levels * LEVEL_HEIGHT_M; }

    public static double metersToLevels(double meters) { return meters / LEVEL_HEIGHT_M; }

    /** Index of the floor level containing a metric height. */
    public static int levelOf(double u) { return (int) Math.floor(u / LEVEL_HEIGHT_M); }

    /** Metric height of the floor of a level. */
    public static double floorOf(int level) { return level * LEVEL_HEIGHT_M; }

    private BallisticsUnits() {}
}
