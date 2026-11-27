package t3zedballistics.effects;

import java.util.Locale;

import t3zedballistics.collision.WorldCollision;
import zombie.core.properties.PropertyContainer;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import java.util.List;

/**
 * What a round hit, as one of the game's own surface materials (zombie.iso.enums.MaterialType - the same
 * names the bullet-impact sound uses through ParameterBulletHitSurface).
 *
 * Order of evidence:
 *   1. the "MaterialType" sprite property of the object that was hit (about 4300 vanilla sprites carry one:
 *      many walls, doors, furniture, fences, appliances)
 *   2. the square's merged "MaterialType" property (what vanilla's impact sound uses)
 *   3. the sprite name (floors and ground tiles have no property: "blends_natural" is earth, "street" is
 *      asphalt/concrete, "carpet" is carpet, ...)
 *   4. a sensible default per hit kind (ground outside = dirt, trees = solid wood, walls = plaster/concrete)
 */
public final class ImpactMaterial {
    // ids follow zombie.iso.enums.MaterialType order
    public static final int DEFAULT = 0, CONCRETE = 3, PLASTER = 4, STONE = 5, WOOD = 6, WOOD_SOLID = 7, BRICK = 8,
        METAL = 9, METAL_LARGE = 10, METAL_LIGHT = 11, METAL_SOLID = 12, GLASS = 13, GLASS_LIGHT = 14, GLASS_SOLID = 15,
        CINDERBLOCK = 16, PLASTIC = 17, CERAMIC = 18, RUBBER = 19, FABRIC = 20, CARPET = 21, DIRT = 22, GRASS = 23,
        GRAVEL = 24, SAND = 25, SNOW = 26;

    public static final String[] NAMES = {
        "Default", "Flesh", "Flesh_Hollow", "Concrete", "Plaster", "Stone", "Wood", "Wood_Solid", "Brick", "Metal",
        "Metal_Large", "Metal_Light", "Metal_Solid", "Glass", "Glass_Light", "Glass_Solid", "Cinderblock", "Plastic",
        "Ceramic", "Rubber", "Fabric", "Carpet", "Dirt", "Grass", "Gravel", "Sand", "Snow"
    };

    /** Broad families used for the look of holes and particles. */
    public static final int F_MASONRY = 0, F_WOOD = 1, F_METAL = 2, F_EARTH = 3, F_SOFT = 4, F_GLASS = 5, F_CERAMIC = 6;

    public static int family(int m) {
        return switch (m) {
            case WOOD, WOOD_SOLID -> F_WOOD;
            case METAL, METAL_LARGE, METAL_LIGHT, METAL_SOLID -> F_METAL;
            case DIRT, GRASS, GRAVEL, SAND, SNOW -> F_EARTH;
            case FABRIC, CARPET, RUBBER, PLASTIC -> F_SOFT;
            case GLASS, GLASS_LIGHT, GLASS_SOLID -> F_GLASS;
            case CERAMIC -> F_CERAMIC;
            default -> F_MASONRY;
        };
    }

    /** Material of a world hit. sq is the tile the round was in; far is the tile across the wall edge (may be null). */
    public static int resolve(int kind, IsoGridSquare sq, IsoGridSquare far, int edgeDx, int edgeDy) {
        try {
            int m;
            switch (kind) {
                case WorldCollision.HIT_WALL, WorldCollision.HIT_LOW -> {
                    // the wall object sits on the west / north edge of its square
                    IsoGridSquare owner = (edgeDx > 0 || edgeDy > 0) ? far : sq;
                    boolean north = edgeDy != 0;
                    if (owner != null) {
                        IsoObject wall = owner.getWall(north);
                        m = ofObject(wall);
                        if (m >= 0) return m;
                    }
                    m = ofSquareObjects(owner, true);
                    if (m >= 0) return m;
                    m = ofSquareObjects(owner == sq ? far : sq, true);
                    if (m >= 0) return m;
                    return kind == WorldCollision.HIT_LOW ? WOOD : PLASTER;
                }
                case WorldCollision.HIT_FLOOR, WorldCollision.HIT_CEILING -> {
                    IsoGridSquare s = sq;
                    if (kind == WorldCollision.HIT_CEILING && sq != null && sq.getCell() != null) {
                        s = sq.getCell().getGridSquare(sq.getX(), sq.getY(), sq.getZ() + 1);
                    }
                    if (s != null) {
                        IsoObject floor = s.getFloor();
                        m = ofObject(floor);
                        if (m >= 0) return m;
                    }
                    if (kind == WorldCollision.HIT_CEILING) return PLASTER;
                    return s != null && s.getZ() > 0 ? WOOD : DIRT;
                }
                case WorldCollision.HIT_TREE -> {
                    return WOOD_SOLID;
                }
                case WorldCollision.HIT_FURNITURE -> {
                    m = ofSquareObjects(sq, false);
                    return m >= 0 ? m : WOOD;
                }
                default -> {
                    return DEFAULT;
                }
            }
        } catch (RuntimeException e) {
            return DEFAULT;
        }
    }

    private static int ofSquareObjects(IsoGridSquare s, boolean wallsOnly) {
        if (s == null) return -1;
        List<IsoObject> objs = s.getObjects();
        if (objs != null) {
            for (int i = objs.size() - 1; i >= 0; i--) {
                IsoObject o = objs.get(i);
                if (o == null || o == s.getFloor()) continue;
                String n = o.getSpriteName();
                if (wallsOnly && (n == null || !(n.contains("wall") || n.contains("door") || n.contains("fenc")
                        || n.contains("garage") || n.contains("window")))) continue;
                int m = ofObject(o);
                if (m >= 0) return m;
            }
        }
        PropertyContainer p = s.getProperties();
        return p == null ? -1 : byName(p.get("MaterialType"));
    }

    private static int ofObject(IsoObject o) {
        if (o == null) return -1;
        PropertyContainer p = o.getProperties();
        int m = p == null ? -1 : byName(p.get("MaterialType"));
        if (m >= 0) return m;
        return fromSpriteName(o.getSpriteName());
    }

    static int byName(String s) {
        if (s == null || s.isBlank()) return -1;
        s = s.trim();
        for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equalsIgnoreCase(s)) return i == 1 || i == 2 ? -1 : i;
        return -1;
    }

    /** Material from a vanilla sprite (tile sheet) name, or -1. Pure function (unit-tested). */
    public static int fromSpriteName(String name) {
        if (name == null || name.isEmpty()) return -1;
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("snow")) return SNOW;
        if (n.contains("tilesandwood")) return n.contains("floor") ? WOOD : -1;
        if (n.contains("tilesandstone")) return STONE;
        if (n.contains("sand") || n.contains("beach")) return SAND;
        if (n.contains("gravel")) return GRAVEL;
        if (n.contains("carpet") || n.contains("rug")) return CARPET;
        if (n.contains("glass")) return GLASS;
        if (n.contains("brick")) return BRICK;
        if (n.contains("cinder")) return CINDERBLOCK;
        if (n.contains("street") || n.contains("road") || n.contains("asphalt") || n.contains("sidewalk")
                || n.contains("parking") || n.contains("concrete")) return CONCRETE;
        if (n.contains("trees") || n.contains("tree_") || n.startsWith("log") || n.contains("_log")) return WOOD_SOLID;
        if (n.contains("metal") || n.contains("chainlink") || n.contains("industry") || n.contains("industrial")
                || n.contains("garage") || n.contains("container") || n.contains("appliances")) return METAL;
        if (n.contains("wood") || n.contains("plank") || n.contains("barricade")) return WOOD;
        if (n.contains("tile") && n.contains("floor")) return CERAMIC;
        if (n.contains("stone") || n.contains("rock")) return STONE;
        if (n.contains("blends_natural") || n.contains("natural") || n.contains("dirt") || n.contains("farm")) return DIRT;
        if (n.contains("grass") || n.contains("vegetation")) return GRASS;
        if (n.contains("walls_interior")) return PLASTER;
        if (n.contains("walls_exterior")) return WOOD;
        if (n.startsWith("floors_interior")) return WOOD;
        return -1;
    }

    private ImpactMaterial() {}
}
