import java.lang.reflect.Field;
import java.lang.reflect.Method;

import zombie.core.properties.PropertyContainer;
import zombie.iso.IsoGridSquare;
import zombie.iso.SpriteDetails.IsoFlagType;

/**
 * 1.4.6 PzWorld edge classification: a Blocked vision test is only a wall when the edge carries one. The game's
 * CalculateVisionBlocked also sets the vision bit for a "solid" / "blocksight" square entered, which is a body
 * inside the tile, not a wall on its edge. Squares are allocated without the game running (Unsafe) and given a
 * PropertyContainer, which is all wallOnEdge reads.
 */
public class EdgeTests {
    static int passed, failed;
    static void check(boolean ok, String what) { if (ok) passed++; else { failed++; System.out.println("FAIL " + what); } }

    static IsoGridSquare square(IsoFlagType... flags) throws Exception {
        Field u = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        u.setAccessible(true);
        IsoGridSquare s = (IsoGridSquare) ((sun.misc.Unsafe) u.get(null)).allocateInstance(IsoGridSquare.class);
        Field f = IsoGridSquare.class.getDeclaredField("properties");
        f.setAccessible(true);
        PropertyContainer pc = new PropertyContainer();
        for (IsoFlagType t : flags) pc.set(t);
        f.set(s, pc);
        return s;
    }

    public static void main(String[] args) throws Exception {
        Method wallOnEdge = t3gunworks.collision.PzWorld.class.getDeclaredMethod("wallOnEdge", IsoGridSquare.class, IsoGridSquare.class, int.class, int.class);
        wallOnEdge.setAccessible(true);
        IsoGridSquare plain = square(), solid = square(IsoFlagType.solid), shelf = square(IsoFlagType.solidtrans, IsoFlagType.blocksight);
        IsoGridSquare wallN = square(IsoFlagType.collideN), wallW = square(IsoFlagType.collideW);
        IsoGridSquare windowN = square(IsoFlagType.collideN, IsoFlagType.transparentN);
        IsoGridSquare doorW = square(IsoFlagType.collideW, IsoFlagType.doorW);

        // the N wall belongs to the square south of it, the W wall to the square east of it
        check((boolean) wallOnEdge.invoke(null, wallN, plain, 0, -1), "going north out of a square with a N wall: wall");
        check(!(boolean) wallOnEdge.invoke(null, plain, wallN, 0, -1), "going north into a square whose N wall is on its far side: no wall");
        check((boolean) wallOnEdge.invoke(null, plain, wallN, 0, 1), "going south into a square with a N wall: wall");
        check((boolean) wallOnEdge.invoke(null, wallW, plain, -1, 0), "going west out of a square with a W wall: wall");
        check((boolean) wallOnEdge.invoke(null, plain, wallW, 1, 0), "going east into a square with a W wall: wall");
        check(!(boolean) wallOnEdge.invoke(null, plain, wallW, 0, 1), "a W wall is not on the N/S edge");
        check(!(boolean) wallOnEdge.invoke(null, windowN, plain, 0, -1), "a window (collide + transparent) is not a wall");
        check(!(boolean) wallOnEdge.invoke(null, doorW, plain, -1, 0), "a door frame (collide + door) is not a wall");
        check(!(boolean) wallOnEdge.invoke(null, plain, solid, 1, 0), "a solid object on the square entered is not a wall");
        check(!(boolean) wallOnEdge.invoke(null, plain, shelf, 0, 1), "a sight-blocking shelf on the square entered is not a wall");
        check(!(boolean) wallOnEdge.invoke(null, null, solid, 1, 0), "unloaded origin: no wall flag to read");

        System.out.println("passed " + passed + ", failed " + failed);
        if (failed > 0) System.exit(1);
    }
}
