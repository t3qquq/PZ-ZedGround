package t3gunworks.collision;

/**
 * What the tile traversal needs to know about the map. The game implementation is PzWorld; the unit
 * tests use a synthetic map. Coordinates are tile x/y and floor level z.
 */
public interface WorldQuery {
    int EDGE_CLEAR = 0;   // nothing on this edge (or an open door / empty frame / see-through fence)
    int EDGE_SOLID = 1;   // wall, closed door, barricade, curtain - stops a bullet
    int EDGE_WINDOW = 2;  // intact glass: breaks and lets the round through
    int EDGE_LOW = 3;     // low wall / hoppable fence: stops only rounds crossing below LOW_OBSTACLE_HEIGHT
    int EDGE_FRAME = 4;   // window frame without glass: open between the sill and the lintel, wall elsewhere
    int EDGE_DOORWAY = 5; // open door / door frame: open up to the lintel, wall above it

    int CELL_EMPTY = 0;
    int CELL_TREE = 1;      // trunk in the middle of the tile
    int CELL_SOLID = 2;     // tall solid furniture / object filling the tile
    int CELL_LOW_SOLID = 3; // counters, tables and other waist-high blockers

    /**
     * Edge between tile (x,y,z) and its neighbour (x+dx, y+dy, z); exactly one of dx/dy is +-1.
     * When EDGE_WINDOW is returned, {@link #lastWindow()} holds the window object. Intact glass is also
     * only open between the sill and the lintel (see PzWorld.WINDOW_SILL / OPENING_TOP).
     */
    int edge(int x, int y, int z, int dx, int dy);

    /** The window found by the last edge() call that returned EDGE_WINDOW. */
    Object lastWindow();

    /** True if the bottom of tile (x,y,z) is a solid floor (ground, building floor, roof). */
    boolean solidFloor(int x, int y, int z);

    /** What stands inside tile (x,y,z). */
    int cell(int x, int y, int z);

    /** True if tile (x,y,z) exists (is loaded). Used to stop rounds that leave the loaded world. */
    boolean exists(int x, int y, int z);
}
