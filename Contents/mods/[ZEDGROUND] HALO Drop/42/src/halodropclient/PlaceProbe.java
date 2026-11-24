package halodropclient;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Measurement for the ~1 tile jump at the ghost handoff (run 11-24 night, 0.6.1): where does Viewpoint actually draw
 * the real C-17, compared with its physics origin (BaseVehicle.getWorldPos(0,0,0)) that the ghost starts from?
 *
 * PatchVpVehiclePlaced calls placed() after viewpoint.models.VehicleCapture.placed(...) on the game thread. We read
 * VehicleCapture's private static Matrix4f `place` and Vector3f `ahead` (names from javap; values only, no Viewpoint code
 * is used) and the frame's camX/camY/camZ (world tiles / floors), and compute where the physics origin would land with
 * the ghost's axes: render = (-(px - camX), (pz - camZ) x 2.44949, -(py - camY)). The difference is split along the
 * vehicle's nose (fwd), its right (side) and up, in tiles. A pure forward difference ~ speed x time means Viewpoint
 * draws the vehicle ahead of physics.
 *
 * Logs: every SAMPLE_MS while a C-17 is drawn ("placement sample"), and GhostPlane prints the last sample at the
 * handoff ("handoff compare"). Any reflection failure switches the probe off after one log line.
 */
public final class PlaceProbe {
    static final String TAG = "[HaloDrop][Place] ";
    static final float FLOOR_H = 2.44949f;
    static final long SAMPLE_MS = 500L;

    private static boolean resolved, broken;
    private static Field fPlace, fAhead, fCamX, fCamY, fCamZ;
    private static Method mMatGet, mVecX, mVecY, mVecZ, mScript, mWorldPos, mFwd, mUp;
    private static Class<?> cVec;
    private static long nextSample;

    // last C-17 sample (game thread writes, render thread reads at handoff; one log line, a torn read is harmless)
    static volatile long lastMs;
    static volatile String lastLine = "none";

    static void log(String s) { System.out.println(TAG + s); }

    private static void resolve(Object frame, Object vehicle) throws Exception {
        ClassLoader cl = frame.getClass().getClassLoader();
        Class<?> vc = Class.forName("viewpoint.models.VehicleCapture", false, cl);
        fPlace = vc.getDeclaredField("place");
        fPlace.setAccessible(true);
        fAhead = vc.getDeclaredField("ahead");
        fAhead.setAccessible(true);
        Class<?> fc = frame.getClass();
        fCamX = fc.getField("camX");
        fCamY = fc.getField("camY");
        fCamZ = fc.getField("camZ");
        Class<?> mat = fPlace.getType();
        mMatGet = mat.getMethod("get", float[].class);
        cVec = fAhead.getType();
        mVecX = cVec.getMethod("x");
        mVecY = cVec.getMethod("y");
        mVecZ = cVec.getMethod("z");
        Class<?> bv = vehicle.getClass();
        while (bv != null && !bv.getName().equals("zombie.vehicles.BaseVehicle")) bv = bv.getSuperclass();
        if (bv == null) throw new IllegalStateException("vehicle is not a BaseVehicle: " + vehicle.getClass());
        mScript = bv.getMethod("getScriptName");
        mWorldPos = bv.getMethod("getWorldPos", float.class, float.class, float.class, cVec);
        mFwd = bv.getMethod("getForwardVector", cVec);
        mUp = bv.getMethod("getUpVector", cVec);
        resolved = true;
        log("probe resolved (VehicleCapture.place/ahead, Frame.camX/Y/Z, BaseVehicle.getWorldPos/getForwardVector)");
    }

    private static float f(Method m, Object o) throws Exception { return ((Number) m.invoke(o)).floatValue(); }

    public static void placed(Object frame, Object vehicle) {
        if (broken || frame == null || vehicle == null) return;
        try {
            if (!resolved) resolve(frame, vehicle);
            Object name = mScript.invoke(vehicle);
            if (!(name instanceof String) || !((String) name).endsWith(GhostPlane.PLANE_SUFFIX)) return;
            long now = System.currentTimeMillis();
            float[] m = (float[]) mMatGet.invoke(fPlace.get(null), (Object) new float[16]);
            Object ah = fAhead.get(null);
            Object p = mWorldPos.invoke(vehicle, 0f, 0f, 0f, cVec.getConstructor().newInstance());
            Object fv = mFwd.invoke(vehicle, cVec.getConstructor().newInstance());
            Object uv = mUp.invoke(vehicle, cVec.getConstructor().newInstance());
            float camX = fCamX.getFloat(frame), camY = fCamY.getFloat(frame), camZ = fCamZ.getFloat(frame);
            float px = f(mVecX, p), py = f(mVecY, p), pz = f(mVecZ, p);
            // physics origin in the ghost's render axes, camera-relative
            float ex = -(px - camX), ey = (pz - camZ) * FLOOR_H, ez = -(py - camY);
            float dx = m[12] - ex, dy = m[13] - ey, dz = m[14] - ez;
            // vehicle axes in render space: physics (x, y, z) -> (-x, y, -z)
            float nx = -f(mVecX, fv), ny = f(mVecY, fv), nz = -f(mVecZ, fv);
            float ux = -f(mVecX, uv), uy = f(mVecY, uv), uz = -f(mVecZ, uv);
            float rx = uy * nz - uz * ny, ry = uz * nx - ux * nz, rz = ux * ny - uy * nx;
            float fwd = dx * nx + dy * ny + dz * nz, side = dx * rx + dy * ry + dz * rz, up = dx * ux + dy * uy + dz * uz;
            String line = "place=(" + r(m[12]) + "," + r(m[13]) + "," + r(m[14]) + ") physics->render=(" + r(ex) + "," + r(ey) + "," + r(ez) + ")"
                    + " diff fwd=" + r(fwd) + " side=" + r(side) + " up=" + r(up) + " tiles |diff|=" + r((float) Math.sqrt(dx * dx + dy * dy + dz * dz))
                    + " ahead=(" + r(f(mVecX, ah)) + "," + r(f(mVecY, ah)) + "," + r(f(mVecZ, ah)) + ")"
                    + " physics=(" + r(px) + "," + r(py) + "," + r(pz) + ") cam=(" + r(camX) + "," + r(camY) + "," + r(camZ) + ")"
                    + " placeAxes x=(" + r(m[0]) + "," + r(m[1]) + "," + r(m[2]) + ") y=(" + r(m[4]) + "," + r(m[5]) + "," + r(m[6]) + ") z=(" + r(m[8]) + "," + r(m[9]) + "," + r(m[10]) + ")";
            lastMs = now;
            lastLine = line;
            if (now >= nextSample) {
                nextSample = now + SAMPLE_MS;
                log("placement sample: " + line);
            }
        } catch (Throwable t) {
            broken = true;
            log("probe FAILED, measurement off (nothing else changes): " + t);
        }
    }

    /** one line for GhostPlane at the handoff */
    static String last() {
        long age = lastMs == 0 ? -1 : System.currentTimeMillis() - lastMs;
        return "last Viewpoint placement " + (age < 0 ? "never seen" : age + "ms ago: " + lastLine);
    }

    static String r(float v) { return String.valueOf(Math.round(v * 100) / 100.0); }
}
