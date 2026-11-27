package t3zedballistics.aiming;

import t3zedballistics.V3;
import t3zedballistics.collision.ProjectileCollision;
import zombie.characters.IsoGameCharacter;

/**
 * The aim ray through Viewpoint's crosshair, traced in game space.
 *
 * Viewpoint 0.1.6 draws the world lifted by its terrain relief (0 at the camera, more or less with distance
 * on hilly maps). A straight line on screen is therefore a curve in the flat game world: the point drawn at
 * scene height h above (x, y) is the game point at height h - lift(x, y). Tracing the straight scene ray
 * against the flat game world would aim above or below what is under the crosshair at range. Without relief
 * (lift 0 everywhere) this is exactly one straight sweep.
 */
public final class SceneRay {
    private static final V3 a = new V3(), b = new V3();

    /** Sweeps from start along unit forward for length metres (scene ray). Main thread only. */
    public static boolean sweep(ProjectileCollision collision, V3 start, V3 forward, double length,
                                IsoGameCharacter[] ignore, int ignoreCount, ProjectileCollision.Result res) {
        double endLift = ViewpointBridge.lift(start.x + forward.x * length, start.y + forward.y * length);
        double midLift = ViewpointBridge.lift(start.x + forward.x * length * 0.5, start.y + forward.y * length * 0.5);
        double startLift = ViewpointBridge.lift(start.x, start.y);
        // one straight sweep when nothing is lifted - unless other mods' objects are asked too: their own
        // searches cover the box around a piece (Physics Props skips static props when it is over 400 tiles),
        // so the aim ray then goes in short pieces
        if (endLift == 0 && midLift == 0 && startLift == 0 && !t3zedballistics.api.ObjectHits.active()) {
            b.set(start.x + forward.x * length, start.y + forward.y * length, start.u + forward.u * length);
            return piece(collision, start, b, ignore, ignoreCount, res);
        }
        double t = 0;
        a.set(start.x, start.y, start.u - startLift);
        int guard = 0;
        while (t < length && guard++ < 600) {
            double step = t < 20 ? 1.0 : (t < 80 ? 2.0 : 4.0);
            double t1 = Math.min(length, t + step);
            double x = start.x + forward.x * t1, y = start.y + forward.y * t1;
            b.set(x, y, start.u + forward.u * t1 - ViewpointBridge.lift(x, y));
            if (piece(collision, a, b, ignore, ignoreCount, res)) return true;
            a.set(b);
            t = t1;
        }
        return false;
    }

    /**
     * One straight piece: the world, bodies and vehicles, plus other mods' objects (t3zedballistics.api.ObjectHits, e.g.
     * Physics Props). Without the objects the aim point would be the wall behind a cone or chair, and a round
     * from the muzzle (beside and below the eye) would converge on that wall and pass beside the prop that
     * the crosshair is on.
     */
    private static boolean piece(ProjectileCollision collision, V3 from, V3 to, IsoGameCharacter[] ignore, int ignoreCount,
                                 ProjectileCollision.Result res) {
        boolean hit = collision.sweep(from, to, ignore, ignoreCount, false, true, true, res);
        if (!t3zedballistics.api.ObjectHits.active()) return hit;
        double tObj = t3zedballistics.api.ObjectHits.peek(from.x, from.y, from.u, to.x, to.y, to.u);
        if (tObj >= 0 && (!hit || tObj < res.t)) {
            res.kind = ProjectileCollision.WORLD;
            res.t = tObj;
            res.character = null;
            res.point.lerp(from, to, tObj);
            return true;
        }
        return hit;
    }

    private SceneRay() {}
}
