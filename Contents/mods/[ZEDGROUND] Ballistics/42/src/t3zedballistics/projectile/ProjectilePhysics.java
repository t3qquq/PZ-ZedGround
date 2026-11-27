package t3zedballistics.projectile;

import t3zedballistics.V3;

/**
 * Deterministic, allocation-free projectile integration in metric space.
 *
 *   gravity : du/dt = -g                       (g = 9.81 * sandbox GravityScale * profile gravityScale)
 *   drag    : dv/dt = -k * |v| * v              (quadratic, k in 1/m; solved implicitly per step so it is
 *                                                unconditionally stable: v' = v / (1 + k |v| h))
 *   position: trapezoid of start/end velocity    (exact for constant gravity)
 *
 * The same function is used by the client simulation and by the server's trajectory sanity checks.
 */
public final class ProjectilePhysics {

    /**
     * Advances (pos, vel) by h seconds. prev receives the old position so the caller can sweep
     * prev -> pos for collisions.
     */
    public static void step(V3 pos, V3 vel, V3 prev, double h, double gravity, double drag) {
        prev.set(pos);
        double vx0 = vel.x, vy0 = vel.y, vu0 = vel.u;
        double vx = vx0, vy = vy0, vu = vu0 - gravity * h;
        if (drag > 0) {
            double speed = Math.sqrt(vx * vx + vy * vy + vu * vu);
            double s = 1.0 / (1.0 + drag * speed * h);
            vx *= s; vy *= s; vu *= s;
        }
        pos.x += (vx0 + vx) * 0.5 * h;
        pos.y += (vy0 + vy) * 0.5 * h;
        pos.u += (vu0 + vu) * 0.5 * h;
        vel.set(vx, vy, vu);
    }

    /**
     * Elevation (radians) the bore needs above the line of sight so that a round with muzzle speed v0
     * and quadratic drag k returns to the sight line at distance zero (flat-fire approximation:
     * time of flight t = (e^{kZ} - 1) / (k v0), drop = g t^2 / 2).
     */
    public static double zeroAngle(double v0, double gravity, double drag, double zero) {
        if (zero <= 0 || v0 <= 0 || gravity <= 0) return 0;
        double t = drag > 1e-9 ? (Math.exp(drag * zero) - 1.0) / (drag * v0) : zero / v0;
        double drop = 0.5 * gravity * t * t;
        return Math.atan2(drop, zero);
    }

    /** Rotates a unit direction upwards by angle (radians) keeping its heading. */
    public static void pitchUp(V3 dir, double angle) {
        if (angle == 0) return;
        double h = Math.hypot(dir.x, dir.y);
        double pitch = Math.atan2(dir.u, h) + angle;
        double yaw = Math.atan2(dir.y, dir.x);
        double cp = Math.cos(pitch);
        dir.set(Math.cos(yaw) * cp, Math.sin(yaw) * cp, Math.sin(pitch));
    }

    /**
     * Deflects a unit direction by small angles (radians) to its right and up, using an orthonormal
     * basis around it. Works for any direction including straight up/down.
     */
    public static void deflect(V3 dir, double right, double up) {
        if (right == 0 && up == 0) return;
        // right = dir x worldUp, up = right x dir
        double rx = dir.y, ry = -dir.x, ru = 0;
        double rl = Math.hypot(rx, ry);
        if (rl < 1e-9) { rx = 1; ry = 0; rl = 1; }
        rx /= rl; ry /= rl;
        double ux = ry * dir.u - ru * dir.y;
        double uy = ru * dir.x - rx * dir.u;
        double uu = rx * dir.y - ry * dir.x;
        double tr = Math.tan(right), tu = Math.tan(up);
        dir.set(dir.x + rx * tr + ux * tu, dir.y + ry * tr + uy * tu, dir.u + ru * tr + uu * tu);
        dir.normalize();
    }

    private ProjectilePhysics() {}
}
