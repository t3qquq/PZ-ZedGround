package t3gunworks.aiming;

import t3gunworks.BallisticsUnits;
import t3gunworks.Log;
import t3gunworks.V3;
import t3gunworks.collision.ProjectileCollision;
import t3gunworks.config.BallisticsConfig;
import t3gunworks.projectile.ProjectilePhysics;
import t3gunworks.weapon.BallisticProfile;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.core.physics.BallisticsController;
import zombie.inventory.types.HandWeapon;
import zombie.iso.Vector3;

/**
 * Muzzle-to-camera convergence.
 *
 *   aimPoint  = first thing on the camera ray (camera + forward * d), or the ray's end at max range
 *   direction = normalize(aimPoint - muzzle), then raised by the zero angle
 *
 * Because the aim point is what the reticle is actually on, an over-the-shoulder camera has no parallax
 * error at short range, and in first person the round converges onto the reticle. The round still starts
 * at the muzzle, and the path from the body to the muzzle is checked first, so a camera that can see past
 * a corner never lets a barrel that is behind the corner shoot through it.
 */
public final class AimProvider {
    private final MuzzleResolver muzzles = new MuzzleResolver();
    private final ProjectileCollision.Result res = new ProjectileCollision.Result();
    private final IsoGameCharacter[] ignore = new IsoGameCharacter[1];
    private final V3 start = new V3(), end = new V3(), head = new V3(), chest = new V3();
    private final double[] sight = new double[7];
    private final double[] sway = new double[2];
    /** Old fixed ADS dispersion share; now BallisticsConfig.adsSpreadScale (sandbox AdsSpreadScale, default 0). */
    @Deprecated public static final double ADS_SPREAD = 0.5;

    public boolean solve(IsoPlayer p, HandWeapon w, BallisticProfile prof, double maxDistance, double v0,
                         double gravity, double drag, ProjectileCollision collision, AimSolution out) {
        ignore[0] = p;
        double x = p.getX(), y = p.getY(), u0 = BallisticsUnits.levelsToMeters(p.getZ());
        double eyeHeight = p.isSneaking() ? 1.15 : 1.55;
        out.viewpoint = ViewpointBridge.controls(p);
        out.thirdPerson = false;

        float yaw;
        out.adsSight = false;
        out.gunplay = false;
        double zero = BallisticsConfig.zeroDistance;
        if (out.viewpoint) {
            yaw = ViewpointBridge.yaw();
            // Viewpoint 0.1.6 Gunplay Rework: the crosshair wanders inside the aim ring and the round goes
            // where the crosshair is drawn, not the screen centre.
            out.gunplay = ViewpointBridge.gunplayAiming();
            ViewpointBridge.sway(sway);
            ViewpointBridge.direction(yaw, ViewpointBridge.pitch(), sway[0], sway[1], out.forward);
            if (!ViewpointBridge.camera(out.camera)) out.camera.set(x, y, u0 + eyeHeight);
            out.thirdPerson = ViewpointBridge.thirdPerson();
            // Project Viewpoint ADS: with the sights up, aim along the real sight line (where the dot /
            // reticle is drawn, including recoil kick and inertia) and use that sight's zero.
            if (!out.thirdPerson && AdsSightBridge.ray(sight)) {
                out.camera.set(sight[0], sight[1], sight[2]);
                out.forward.set(sight[3], sight[4], sight[5]).normalize();
                if (out.forward.finite() && out.camera.finite() && Math.hypot(Math.hypot(out.camera.x - x, out.camera.y - y), out.camera.u - (u0 + eyeHeight)) < 1.5) {
                    out.adsSight = true;
                    // ADS zeros are real-world distances (scopes 100-150 m). They assume real muzzle speeds;
                    // with the velocity scale below 1 the same zero makes a much higher arc (a 150 m zero at
                    // 0.4x flies ~18 cm high at 30 m - over a zombie's head). Scaling the zero by the velocity
                    // scale keeps the path within a few cm of the reticle out to the loaded-world edge.
                    if (sight[6] > 0) zero = sight[6] * Math.min(1.0, BallisticsConfig.velocityScale);
                } else {
                    // implausible data: fall back to Viewpoint's own ray
                    ViewpointBridge.direction(yaw, ViewpointBridge.pitch(), sway[0], sway[1], out.forward);
                    if (!ViewpointBridge.camera(out.camera)) out.camera.set(x, y, u0 + eyeHeight);
                }
            }
        } else {
            yaw = p.getLookAngleRadians();
            if (!isoForward(p, out.forward)) ViewpointBridge.direction(yaw, 0f, out.forward);
        }

        muzzles.resolve(p, yaw, out.muzzle, out.barrel);
        out.muzzleSource = muzzles.lastSource;

        if (out.viewpoint) {
            // Start the aim ray level with the head so a third-person camera cannot pick objects behind the player.
            start.set(out.camera);
            if (out.thirdPerson) {
                head.set(x - out.camera.x, y - out.camera.y, u0 + eyeHeight - out.camera.u);
                double t0 = Math.max(0, head.dot(out.forward));
                start.set(out.camera.x + out.forward.x * t0, out.camera.y + out.forward.y * t0, out.camera.u + out.forward.u * t0);
            }
            end.set(start.x + out.forward.x * maxDistance, start.y + out.forward.y * maxDistance, start.u + out.forward.u * maxDistance);
            // the crosshair ray in game space (Viewpoint's terrain relief lifts what is drawn, not the game)
            boolean hit = SceneRay.sweep(collision, start, out.forward, maxDistance, ignore, 1, res);
            out.aimHit = hit;
            if (hit) out.aimPoint.set(res.point);
            else out.aimPoint.set(end.x, end.y, end.u - ViewpointBridge.lift(end.x, end.y));
        } else {
            out.camera.set(out.muzzle);
            out.aimHit = false;
            out.aimPoint.set(out.muzzle.x + out.forward.x * maxDistance, out.muzzle.y + out.forward.y * maxDistance,
                out.muzzle.u + out.forward.u * maxDistance);
        }

        // Near-wall rule: the barrel must have a clear path from the body.
        chest.set(x, y, out.muzzle.u);
        out.muzzleBlocked = collision.sweep(chest, out.muzzle, ignore, 1, false, false, true, res);
        if (out.muzzleBlocked) out.blockPoint.set(res.point);

        out.direction.set(out.aimPoint.x - out.muzzle.x, out.aimPoint.y - out.muzzle.y, out.aimPoint.u - out.muzzle.u);
        double len = out.direction.length();
        out.direction.normalize();
        if (len < 0.25 || out.direction.dot(out.forward) < 0.1) {
            // Aim point practically at (or behind) the muzzle: shoot along the view.
            out.direction.set(out.forward);
        }

        out.zeroDistance = zero;
        out.zeroAngle = ProjectilePhysics.zeroAngle(v0, gravity, drag, zero);
        ProjectilePhysics.pitchUp(out.direction, out.zeroAngle);
        out.sigma = Spread.sigmaRadians(p, w, prof);
        // Through ADS sights the shooter's hold (sway, breathing, recoil) is already simulated by ADS and the
        // round follows that real sight line; ZEDGROUND gunplay drives that hold, so by default nothing is added.
        if (out.adsSight) out.sigma *= BallisticsConfig.adsSpreadScale;
        // Viewpoint 0.1.6 Gunplay Rework (hip fire): the crosshair's wander inside the aim ring already is the
        // skill/hit-chance dispersion, so the round leaves exactly along the crosshair and nothing is added.
        else if (out.gunplay) out.sigma = 0;
        return out.direction.finite() && out.muzzle.finite();
    }

    /** Vanilla's own iso aim: the B42 BallisticsController muzzle direction (z in levels). */
    private static boolean isoForward(IsoPlayer p, V3 out) {
        try {
            BallisticsController bc = p.getBallisticsController();
            if (bc == null) return false;
            Vector3 d = bc.getMuzzleDirection();
            if (d == null) return false;
            out.set(d.x, d.y, BallisticsUnits.levelsToMeters(d.z));
            if (out.length() < 1e-6 || !out.finite()) return false;
            out.normalize();
            return true;
        } catch (RuntimeException e) {
            Log.errorOnce("reading the vanilla aim direction", e);
            return false;
        }
    }
}
