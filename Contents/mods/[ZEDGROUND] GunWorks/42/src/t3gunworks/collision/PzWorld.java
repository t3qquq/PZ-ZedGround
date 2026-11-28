package t3gunworks.collision;

import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoWorld;
import zombie.core.properties.PropertyContainer;
import zombie.iso.LosUtil;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.objects.IsoWindow;

/**
 * WorldQuery over Project Zomboid's authoritative map state (IsoCell / IsoGridSquare), not rendered
 * meshes, so client and server agree and Viewpoint's renderer is not involved.
 *
 * Edges use IsoGridSquare.testVisionAdjacent(dx, dy, 0, false, false), the same per-edge test vanilla
 * line-of-sight (LosUtil.lineClear) is built on. It already knows walls, doors (open/closed), windows,
 * curtains and barricades. Window glass and hoppable fences are then refined with getWindowTo /
 * isHoppableTo / getWindowFrameTo, and a Blocked result is only a wall when the edge really carries one
 * (see blocked()): the test is also Blocked by a solid object standing on the square entered.
 */
public final class PzWorld implements WorldQuery {
    /** Window sill / opening heights in metres above the floor (glass and empty frames). */
    public static final double WINDOW_SILL = 0.8, OPENING_TOP = 2.1;

    private IsoCell cell;
    private Object lastWindow;

    /** Binds to the current cell; call once per tick / validation before tracing. */
    public PzWorld bind() {
        IsoWorld w = IsoWorld.instance;
        cell = w != null ? w.getCell() : null;
        return this;
    }

    public boolean ready() { return cell != null; }

    private IsoGridSquare sq(int x, int y, int z) {
        return cell == null ? null : cell.getGridSquare(x, y, z);
    }

    @Override
    public int edge(int x, int y, int z, int dx, int dy) {
        IsoGridSquare a = sq(x, y, z);
        IsoGridSquare b = sq(x + dx, y + dy, z);
        if (a == null && b == null) return EDGE_CLEAR;
        LosUtil.TestResults r = a != null
            ? a.testVisionAdjacent(dx, dy, 0, false, false)
            : b.testVisionAdjacent(-dx, -dy, 0, false, false);
        switch (r) {
            case ClearThroughClosedDoor:
                return EDGE_SOLID;
            case Blocked:
                return blocked(a, b, dx, dy);
            case ClearThroughWindow: {
                IsoWindow win = (a != null && b != null) ? a.getWindowTo(b) : null;
                if (win != null && !win.isDestroyed() && !win.isSmashed()) {
                    lastWindow = win;
                    return EDGE_WINDOW;
                }
                return EDGE_FRAME;
            }
            case ClearThroughOpenDoor:
                return EDGE_DOORWAY;
            default:
                if (a != null && b != null) {
                    if (a.getWindowFrameTo(b) != null) return EDGE_FRAME;
                    if (a.isHoppableTo(b)) return EDGE_LOW;
                }
                return EDGE_CLEAR;
        }
    }

    /**
     * testVisionAdjacent says Blocked for more than walls: it is also Blocked when the square entered carries the
     * "solid" or "blocksight" flag (a dumpster, a tall shelf, a vending machine - IsoGridSquare.CalculateVisionBlocked),
     * at every height. Those are bodies standing inside the tile, not a wall on its edge: they are left to
     * {@link #cell} (the furniture box, or a mod's real shape when it claims the tile), so a round over them passes
     * and one into them stops at the object, not 15 cm in front of it at the tile edge. A wall is recognised by its
     * collide flags on the edge; closed doors, barricades, boarded windows and the like by the object on the edge.
     */
    private int blocked(IsoGridSquare a, IsoGridSquare b, int dx, int dy) {
        if (wallOnEdge(a, b, dx, dy)) return EDGE_SOLID;
        if (a == null || b == null) return EDGE_SOLID; // unloaded neighbour: nothing to look at, keep it solid
        if (a.getDoorTo(b) != null) return EDGE_SOLID;           // a closed door (an open one is ClearThroughOpenDoor)
        IsoWindow win = a.getWindowTo(b);
        if (win != null) {
            if (win.isBarricaded()) return EDGE_SOLID;
            if (!win.isDestroyed() && !win.isSmashed()) {        // glass behind a closed curtain
                lastWindow = win;
                return EDGE_WINDOW;
            }
            return EDGE_FRAME;
        }
        if (a.getWindowFrameTo(b) != null) return EDGE_FRAME;
        if (a.getWindowThumpableTo(b) != null) return EDGE_SOLID; // player-built window: boarded or unknown, keep solid
        boolean body = b.getProperties().has(IsoFlagType.solid) || b.getProperties().has(IsoFlagType.blocksight)
            || a.getProperties().has(IsoFlagType.solid) || a.getProperties().has(IsoFlagType.blocksight);
        return body ? EDGE_CLEAR : EDGE_SOLID;                   // something else (an unknown blocker): stays a wall
    }

    /** A wall (or a solid fence) on the edge between a and b: the collide flag without transparency or a door. */
    private static boolean wallOnEdge(IsoGridSquare a, IsoGridSquare b, int dx, int dy) {
        // the N edge belongs to the square south of it, the W edge to the square east of it (as the game stores them)
        if (dx < 0) return wall(a, IsoFlagType.collideW, IsoFlagType.transparentW, IsoFlagType.doorW);
        if (dx > 0) return wall(b, IsoFlagType.collideW, IsoFlagType.transparentW, IsoFlagType.doorW);
        if (dy < 0) return wall(a, IsoFlagType.collideN, IsoFlagType.transparentN, IsoFlagType.doorN);
        return wall(b, IsoFlagType.collideN, IsoFlagType.transparentN, IsoFlagType.doorN);
    }

    private static boolean wall(IsoGridSquare s, IsoFlagType collide, IsoFlagType transparent, IsoFlagType door) {
        if (s == null) return false;
        PropertyContainer p = s.getProperties();
        return p.has(collide) && !p.has(transparent) && !p.has(door);
    }

    @Override
    public Object lastWindow() { return lastWindow; }

    @Override
    public boolean solidFloor(int x, int y, int z) {
        IsoGridSquare s = sq(x, y, z);
        if (s == null) return z <= 0; // unloaded ground / earth below the map stops rounds
        return s.TreatAsSolidFloor();
    }

    @Override
    public int cell(int x, int y, int z) {
        IsoGridSquare s = sq(x, y, z);
        if (s == null) return CELL_EMPTY;
        if (s.getTree() != null) return CELL_TREE;
        if ((s.isSolid() || s.isSolidTrans()) && t3gunworks.api.ObjectHits.covers(x, y, z)) return CELL_EMPTY; // a mod's real shape
        if (s.isSolid()) return CELL_SOLID;
        if (s.isSolidTrans()) {
            // a sight-blocking solidtrans object (tall shelf, vending machine) is man-high; the rest waist-high.
            if (s.getProperties().has(IsoFlagType.blocksight)) return CELL_SOLID;
            // solidtrans is also set next to windows and hoppable fences; those edges are handled above
            if (!s.isAdjacentToWindow() && !s.isAdjacentToHoppable()) return CELL_LOW_SOLID;
        }
        return CELL_EMPTY;
    }

    @Override
    public boolean exists(int x, int y, int z) { return sq(x, y, z) != null; }

    public IsoGridSquare square(int x, int y, int z) { return sq(x, y, z); }

    public IsoCell isoCell() { return cell; }
}
