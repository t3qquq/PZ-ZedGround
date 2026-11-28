package t3gunworks.debug;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import t3gunworks.BallisticsUnits;
import t3gunworks.Log;
import t3gunworks.V3;
import t3gunworks.config.BallisticsConfig;
import t3gunworks.projectile.BallisticProjectile;
import zombie.debug.LineDrawer;

/**
 * Developer visualisation and statistics. Costs nothing unless a debug switch is on or tracer rounds are
 * in flight.
 *
 * Colours: camera ray cyan, muzzle white, barrel direction green, flight path yellow, impact red,
 * hit capsule magenta, window crossings light blue, blocked barrel orange, tracer rounds warm white.
 *
 * Lines are recorded on the main thread in metric space. In Viewpoint they are converted per frame to
 * Viewpoint's render space in capture() and drawn on the render thread by DebugDraw; in the vanilla
 * isometric view they are submitted to the game's own LineDrawer.
 */
public final class BallisticsDebug {
    private static final int MAX_LINES = 4096;
    private static final float[] lines = new float[MAX_LINES * 10]; // x1 y1 u1 x2 y2 u2 r g b a
    private static final long[] expiry = new long[MAX_LINES];
    private static int count;

    /** Render-space snapshot per Viewpoint scene (written on main thread, read on render thread). */
    static final Map<Object, float[]> snapshots = Collections.synchronizedMap(new WeakHashMap<>());

    public static boolean enabled() {
        return BallisticsConfig.debugTrajectories || BallisticsConfig.debugImpacts || BallisticsConfig.debugHitboxes;
    }

    public static void line(V3 a, V3 b, float r, float g, float bl, float alpha) {
        line(a.x, a.y, a.u, b.x, b.y, b.u, r, g, bl, alpha, BallisticsConfig.debugLifetime);
    }

    public static synchronized void line(double x1, double y1, double u1, double x2, double y2, double u2,
                                         float r, float g, float b, float a, double seconds) {
        if (count >= MAX_LINES) drop(0);
        int o = count * 10;
        lines[o] = (float) x1; lines[o + 1] = (float) y1; lines[o + 2] = (float) u1;
        lines[o + 3] = (float) x2; lines[o + 4] = (float) y2; lines[o + 5] = (float) u2;
        lines[o + 6] = r; lines[o + 7] = g; lines[o + 8] = b; lines[o + 9] = a;
        expiry[count] = System.nanoTime() + (long) (seconds * 1e9);
        count++;
    }

    /** Small 3D cross marking a point. */
    public static void point(V3 p, double size, float r, float g, float b) {
        double s = size * 0.5;
        double life = BallisticsConfig.debugLifetime;
        line(p.x - s, p.y, p.u, p.x + s, p.y, p.u, r, g, b, 1f, life);
        line(p.x, p.y - s, p.u, p.x, p.y + s, p.u, r, g, b, 1f, life);
        line(p.x, p.y, p.u - s, p.x, p.y, p.u + s, r, g, b, 1f, life);
    }

    private static void drop(int i) {
        count--;
        if (i != count) {
            System.arraycopy(lines, count * 10, lines, i * 10, 10);
            expiry[i] = expiry[count];
        }
    }

    /** Main thread, once per game tick. */
    public static synchronized void tick(boolean viewpointActive) {
        long now = System.nanoTime();
        for (int i = count - 1; i >= 0; i--) if (expiry[i] < now) drop(i);
        if (!viewpointActive && count > 0) {
            try {
                for (int i = 0; i < count; i++) {
                    int o = i * 10;
                    LineDrawer.addLine(lines[o], lines[o + 1], (float) BallisticsUnits.metersToLevels(lines[o + 2]),
                        lines[o + 3], lines[o + 4], (float) BallisticsUnits.metersToLevels(lines[o + 5]),
                        lines[o + 6], lines[o + 7], lines[o + 8], lines[o + 9]);
                }
            } catch (RuntimeException e) {
                Log.errorOnce("iso debug lines", e);
            }
        }
    }

    /**
     * Main thread, from the CrosshairAim.snapshot advice: converts debug lines and visible tracer rounds
     * into Viewpoint render space for this frame (render = (camX - x, (z - camZ) * sqrt6, camY - y)).
     */
    public static void capture(Object scene, float camX, float camY, float camZ, ArrayList<BallisticProjectile> active,
                               ArrayList<BallisticProjectile> npc) {
        if (scene == null) return;
        int tracers = 0;
        for (int i = 0; i < active.size(); i++) if (active.get(i).tracer) tracers++;
        tracers += npc.size();
        float[] out;
        synchronized (BallisticsDebug.class) {
            int n = count + tracers;
            if (n == 0) { snapshots.remove(scene); return; }
            out = new float[n * 10];
            double cu = BallisticsUnits.levelsToMeters(camZ);
            for (int i = 0; i < count; i++) {
                int o = i * 10;
                toRender(lines, o, out, o, camX, camY, cu);
            }
            int k = count;
            for (int i = 0, all = active.size() + npc.size(); i < all; i++) {
                BallisticProjectile p = i < active.size() ? active.get(i) : npc.get(i - active.size());
                if (!p.tracer) continue;
                double v = Math.max(1, p.speed());
                double streak = Math.min(4.0, p.traveled);
                int o = k * 10;
                double tx = p.position.x - p.velocity.x / v * streak, ty = p.position.y - p.velocity.y / v * streak;
                out[o] = (float) (camX - tx);
                out[o + 1] = (float) ((p.position.u - p.velocity.u / v * streak) + t3gunworks.aiming.ViewpointBridge.lift(tx, ty) - cu);
                out[o + 2] = (float) (camY - ty);
                out[o + 3] = (float) (camX - p.position.x);
                out[o + 4] = (float) (p.position.u + t3gunworks.aiming.ViewpointBridge.lift(p.position.x, p.position.y) - cu);
                out[o + 5] = (float) (camY - p.position.y);
                out[o + 6] = 1f; out[o + 7] = 0.85f; out[o + 8] = 0.5f; out[o + 9] = 0.9f;
                k++;
            }
        }
        snapshots.put(scene, out);
    }

    private static void toRender(float[] src, int s, float[] dst, int d, float camX, float camY, double cu) {
        dst[d] = camX - src[s];
        dst[d + 1] = (float) (src[s + 2] + t3gunworks.aiming.ViewpointBridge.lift(src[s], src[s + 1]) - cu);
        dst[d + 2] = camY - src[s + 1];
        dst[d + 3] = camX - src[s + 3];
        dst[d + 4] = (float) (src[s + 5] + t3gunworks.aiming.ViewpointBridge.lift(src[s + 3], src[s + 4]) - cu);
        dst[d + 5] = camY - src[s + 4];
        System.arraycopy(src, s + 6, dst, d + 6, 4);
    }

    /** True while a frame snapshot is still held (so the next capture can clear it). */
    public static boolean hasSnapshots() { return !snapshots.isEmpty(); }

    public static synchronized void clear() {
        count = 0;
        snapshots.clear();
    }

    private BallisticsDebug() {}
}
