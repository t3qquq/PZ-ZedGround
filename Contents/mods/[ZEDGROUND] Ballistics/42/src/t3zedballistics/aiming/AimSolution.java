package t3zedballistics.aiming;

import t3zedballistics.V3;

/** Everything decided at the moment of firing. Reused; filled by AimProvider. */
public final class AimSolution {
    public final V3 camera = new V3();      // aim ray origin (eye in FPS, shoulder camera in TPS)
    public final V3 forward = new V3();     // camera forward (unit)
    public final V3 aimPoint = new V3();    // what the reticle is on
    public final V3 muzzle = new V3();      // where the round starts
    public final V3 barrel = new V3();      // barrel direction from the model (may be zero)
    public final V3 direction = new V3();   // final launch direction after zero, spread is applied per pellet
    public boolean aimHit;                  // the camera ray hit something (else aimPoint is at max range)
    public boolean muzzleBlocked;           // the barrel is behind an obstacle (near-wall rule)
    public final V3 blockPoint = new V3();  // where the barrel path is blocked
    public int muzzleSource;
    public double sigma;                    // dispersion (rad, per axis)
    public double zeroAngle;                // rad
    public boolean viewpoint;               // aim came from Viewpoint's camera (else vanilla iso aim)
    public boolean thirdPerson;
    public boolean gunplay;                 // aimed by Viewpoint 0.1.6's Gunplay Rework crosshair (its sway is the spread)
    public boolean adsSight;                // aimed along Project Viewpoint ADS's raised sight line
    public double zeroDistance;             // m, zero actually used for this shot
}
