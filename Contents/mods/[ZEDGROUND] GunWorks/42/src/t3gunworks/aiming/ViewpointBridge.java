package t3gunworks.aiming;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import t3gunworks.BallisticsUnits;
import t3gunworks.Log;
import t3gunworks.V3;
import zombie.characters.IsoPlayer;

/**
 * Read-only reflection bridge to Viewpoint (which has no public API). Every member is looked up once
 * at start-up; if a Viewpoint update renames something the bridge reports it and ballistics fall back to
 * the configured non-Viewpoint behaviour instead of crashing.
 *
 * Members used (verified in Viewpoint 0.1.5a and 0.1.6):
 *   viewpoint.core.View.enabled                    - first-person/3D view on
 *   viewpoint.input.Look.yaw / pitch / captured     - live mouse-look angles (radians)
 *   viewpoint.input.FreeCam.active                  - detached debug camera
 *   viewpoint.core.Frame.camX/camY/camZ/eyeX/eyeY/eyeZ/onCamera
 *   0.1.6:  viewpoint.core.View.thirdPerson / cameraX / cameraY / cameraUp   (camera moved to viewpoint.camera)
 *           viewpoint.gunplay.Gunplay.ON / swayRight() / swayUp() / fresh()  ("Gunplay Rework", optional)
 *   0.1.5a: viewpoint.input.ThirdPerson.active / offsetX / offsetY / offsetUp
 *
 * Camera position (same formula as viewpoint.input.CrosshairAim.snapshot):
 *   world x  = camX - eyeX + offsetX
 *   world y  = camY - eyeZ + offsetY
 *   height   = camZ * sqrt(6) + eyeY + offsetUp       (metres)
 *   (0.1.6 always adds View.camera*, 0.1.5a added ThirdPerson.offset* only in third person)
 * View direction for (yaw, pitch): (cos yaw * cos pitch, sin yaw * cos pitch, sin pitch) in (x, y, up).
 * With Viewpoint 0.1.6's Gunplay Rework the crosshair wanders inside the aim ring; the shot line is the view
 * direction turned by (swayRight, swayUp), exactly as viewpoint.gunplay.Sight.set does it.
 */
public final class ViewpointBridge {
    public static boolean available;

    private static Field viewEnabled, lookYaw, lookPitch, lookCaptured;
    private static Field tpActive, tpOffX, tpOffY, tpOffUp, freeCamActive;
    /** true: Viewpoint 0.1.6+ (camera offsets always applied); false: 0.1.5a (offsets only in third person) */
    private static boolean offsetsAlways;
    private static Object gunplayOn;
    private static Method looking; // 0.1.6: mouse captured or controller look held
    private static Method toggleGet, swayRightM, swayUpM, freshM;
    private static Field viewSwayRight, viewSwayUp;
    public static String version = "?";
    private static Field fCamX, fCamY, fCamZ, fEyeX, fEyeY, fEyeZ, fOnCamera;

    // latest camera snapshot (main thread only)
    private static final V3 camera = new V3();
    private static long cameraNanos;
    private static boolean cameraThirdPerson;

    public static void init() {
        try {
            Class<?> view = cls("viewpoint.core.View");
            Class<?> look = cls("viewpoint.input.Look");
            Class<?> frame = cls("viewpoint.core.Frame");
            viewEnabled = view.getField("enabled");
            lookYaw = look.getField("yaw");
            lookPitch = look.getField("pitch");
            lookCaptured = look.getField("captured");
            try { looking = look.getMethod("looking"); } catch (NoSuchMethodException e) { looking = null; }
            try {
                // Viewpoint 0.1.6+: third-person state and the camera offset live on View
                tpActive = view.getField("thirdPerson");
                tpOffX = view.getField("cameraX");
                tpOffY = view.getField("cameraY");
                tpOffUp = view.getField("cameraUp");
                offsetsAlways = true;
                version = "0.1.6+";
            } catch (NoSuchFieldException e) {
                Class<?> tp = cls("viewpoint.input.ThirdPerson"); // Viewpoint 0.1.5a
                tpActive = tp.getField("active");
                tpOffX = tp.getField("offsetX");
                tpOffY = tp.getField("offsetY");
                tpOffUp = tp.getField("offsetUp");
                offsetsAlways = false;
                version = "0.1.5";
            }
            fCamX = frame.getField("camX");
            fCamY = frame.getField("camY");
            fCamZ = frame.getField("camZ");
            fEyeX = frame.getField("eyeX");
            fEyeY = frame.getField("eyeY");
            fEyeZ = frame.getField("eyeZ");
            fOnCamera = frame.getField("onCamera");
            try {
                freeCamActive = cls("viewpoint.input.FreeCam").getField("active");
            } catch (ReflectiveOperationException e) {
                Log.warn("Viewpoint FreeCam not found (" + e + "); free-camera detection disabled");
            }
            initGunplay(view);
            available = true;
            Log.info("Viewpoint camera bridge ready (Viewpoint " + version + (gunplayOn != null ? ", Gunplay Rework supported" : "") + ").");
        } catch (ClassNotFoundException e) {
            available = false;
            Log.info("Viewpoint classes not present (" + e.getMessage() + "); Viewpoint aiming disabled in this process.");
        } catch (ReflectiveOperationException | RuntimeException e) {
            available = false;
            Log.error("Viewpoint found but incompatible; Viewpoint aiming disabled", e);
        }
    }

    // ---------------------------------------------------------------- terrain relief (0.1.6)
    private static Method reliefCurrent, liftScene;
    private static boolean reliefResolved;

    /**
     * Viewpoint 0.1.6 lifts the drawn world by its terrain relief (viewpoint.far.Relief.current(), a
     * FarRelief$Lift; scene(x, y) = metres added at that spot, 0 at the camera). The game itself is flat, so
     * anything True Ballistics draws at a game position must be lifted the same way. Main thread only.
     */
    public static double lift(double x, double y) {
        if (!available) return 0;
        if (!reliefResolved) {
            reliefResolved = true;
            try {
                reliefCurrent = cls("viewpoint.far.Relief").getMethod("current");
                liftScene = reliefCurrent.getReturnType().getMethod("scene", double.class, double.class);
                reliefCurrent.setAccessible(true);
                liftScene.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException e) {
                reliefCurrent = null; // Viewpoint without relief: flat
            }
        }
        if (reliefCurrent == null) return 0;
        try {
            Object lift = reliefCurrent.invoke(null);
            if (lift == null) return 0;
            double v = ((Number) liftScene.invoke(lift, x, y)).doubleValue();
            return Double.isFinite(v) ? v : 0;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.errorOnce("ViewpointBridge.lift", e);
            reliefCurrent = null;
            return 0;
        }
    }

    /** Viewpoint 0.1.6 "Gunplay Rework": crosshair sway inside the aim ring. Optional; absent in 0.1.5a. */
    private static void initGunplay(Class<?> view) {
        try {
            viewSwayRight = view.getField("swayRight");
            viewSwayUp = view.getField("swayUp");
        } catch (NoSuchFieldException | RuntimeException e) {
            viewSwayRight = viewSwayUp = null;
        }
        try {
            Class<?> g = cls("viewpoint.gunplay.Gunplay");
            Field on = g.getDeclaredField("ON");
            on.setAccessible(true);
            Object toggle = on.get(null);
            Method get = toggle.getClass().getMethod("get");
            get.setAccessible(true);
            Method r = g.getDeclaredMethod("swayRight"), u = g.getDeclaredMethod("swayUp"), f = g.getDeclaredMethod("fresh");
            r.setAccessible(true);
            u.setAccessible(true);
            f.setAccessible(true);
            get.invoke(toggle);
            gunplayOn = toggle;
            toggleGet = get;
            swayRightM = r;
            swayUpM = u;
            freshM = f;
        } catch (ClassNotFoundException e) {
            gunplayOn = null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            gunplayOn = null;
            Log.warn("Viewpoint Gunplay found but not readable (" + e + "); crosshair sway read from View only");
        }
    }

    /**
     * True when Viewpoint 0.1.6's Gunplay Rework is on and placed the crosshair this moment (it wanders inside
     * the aim ring, and that wander already is the shooter's skill/hit-chance dispersion).
     */
    public static boolean gunplayAiming() {
        if (!available || gunplayOn == null) return false;
        try {
            return Boolean.TRUE.equals(toggleGet.invoke(gunplayOn)) && Boolean.TRUE.equals(freshM.invoke(null));
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.errorOnce("ViewpointBridge.gunplayAiming", e);
            gunplayOn = null;
            return false;
        }
    }

    /** Current crosshair sway (right, up; tangent offsets of the view direction). Zero without Gunplay. */
    public static void sway(double[] out) {
        out[0] = 0;
        out[1] = 0;
        if (!available) return;
        try {
            if (gunplayOn != null) {
                out[0] = ((Number) swayRightM.invoke(null)).doubleValue();
                out[1] = ((Number) swayUpM.invoke(null)).doubleValue();
            } else if (viewSwayRight != null) {
                out[0] = viewSwayRight.getFloat(null);
                out[1] = viewSwayUp.getFloat(null);
            }
            if (!Double.isFinite(out[0]) || !Double.isFinite(out[1]) || Math.abs(out[0]) > 0.5 || Math.abs(out[1]) > 0.5) {
                out[0] = 0;
                out[1] = 0;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.errorOnce("ViewpointBridge.sway", e);
            out[0] = 0;
            out[1] = 0;
        }
    }

    private static Class<?> cls(String n) throws ClassNotFoundException {
        try {
            return Class.forName(n, false, ViewpointBridge.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            ClassLoader ctx = Thread.currentThread().getContextClassLoader();
            if (ctx == null) throw e;
            return Class.forName(n, false, ctx);
        }
    }

    /** True when Viewpoint is drawing the 3D view for this local player and the mouse drives the aim. */
    public static boolean controls(IsoPlayer p) {
        if (!available || p == null) return false;
        try {
            if (!viewEnabled.getBoolean(null)) return false;
            if (!lookCaptured.getBoolean(null) && !(looking != null && Boolean.TRUE.equals(looking.invoke(null)))) return false;
            if (freeCamActive != null && freeCamActive.getBoolean(null)) return false;
            return p == IsoPlayer.players[0] && p.getVehicle() == null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.errorOnce("ViewpointBridge.controls", e);
            return false;
        }
    }

    public static boolean thirdPerson() {
        if (!available) return false;
        try { return tpActive.getBoolean(null); }
        catch (IllegalAccessException | RuntimeException e) { Log.errorOnce("ViewpointBridge.thirdPerson", e); return false; }
    }

    public static float yaw() {
        try { return lookYaw.getFloat(null); }
        catch (IllegalAccessException | RuntimeException e) { Log.errorOnce("ViewpointBridge.yaw", e); return 0f; }
    }

    public static float pitch() {
        try { return lookPitch.getFloat(null); }
        catch (IllegalAccessException | RuntimeException e) { Log.errorOnce("ViewpointBridge.pitch", e); return 0f; }
    }

    /** Called from the CrosshairAim.snapshot advice (main thread) with Viewpoint's frame for this view. */
    public static void captureCamera(Object frame, Object character) {
        if (!available || frame == null || character != IsoPlayer.players[0]) return;
        try {
            if (fOnCamera.getBoolean(frame)) return; // free camera: not the player's eyes
            boolean tp = tpActive.getBoolean(null);
            float camX = fCamX.getFloat(frame), camY = fCamY.getFloat(frame), camZ = fCamZ.getFloat(frame);
            float eyeX = fEyeX.getFloat(frame), eyeY = fEyeY.getFloat(frame), eyeZ = fEyeZ.getFloat(frame);
            boolean off = tp || offsetsAlways;
            float ox = off ? tpOffX.getFloat(null) : 0f, oy = off ? tpOffY.getFloat(null) : 0f, ou = off ? tpOffUp.getFloat(null) : 0f;
            camera.set(camX - eyeX + ox, camY - eyeZ + oy, BallisticsUnits.levelsToMeters(camZ) + eyeY + ou);
            cameraThirdPerson = tp;
            cameraNanos = System.nanoTime();
        } catch (IllegalAccessException | RuntimeException e) {
            Log.errorOnce("ViewpointBridge.captureCamera", e);
        }
    }

    /** Copies the latest camera position into out; false if none was captured in the last second (a frame can take 250 ms). */
    public static boolean camera(V3 out) {
        if (cameraNanos == 0L || System.nanoTime() - cameraNanos > 1_000_000_000L) return false;
        out.set(camera);
        return true;
    }

    public static boolean cameraIsThirdPerson() { return cameraThirdPerson; }

    /** Viewpoint's view direction for the given angles, in metric (x, y, up). */
    public static void direction(float yaw, float pitch, V3 out) {
        double cp = Math.cos(pitch);
        out.set(Math.cos(yaw) * cp, Math.sin(yaw) * cp, Math.sin(pitch));
    }

    /**
     * The view direction turned by a crosshair offset (right, up), as viewpoint.gunplay.Sight.set:
     * forward + right * r + up * u with right = (-sin yaw, cos yaw, 0), up = (-cos yaw sin pitch, -sin yaw sin pitch, cos pitch).
     */
    public static void direction(float yaw, float pitch, double right, double up, V3 out) {
        double cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
        out.set(cy * cp - sy * right - cy * sp * up, sy * cp + cy * right - sy * sp * up, sp + cp * up);
        out.normalize();
    }

    private ViewpointBridge() {}
}
