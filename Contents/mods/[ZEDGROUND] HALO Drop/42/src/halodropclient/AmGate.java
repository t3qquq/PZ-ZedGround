package halodropclient;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Halo Drop x Viewpoint Advanced Movement (Workshop 3812702301, mod id ViewpointAdvancedMovement; names checked with javap
 * on its 1.4.18 jar only).
 *
 * Why (review 11-25): Advanced Movement (AM) does not know about Halo Drop. It takes its input whenever Viewpoint has the
 *   view and the mouse (com.viewpointmovement.core.InputController.computeLive), so aboard (hidden on the rig, on the
 *   floor) and in the air it still
 *     - moves the vanilla Interact key off E to F (Core.getKeyBinding is patched while AM is live; HaloDrop's
 *       getCore():getKey("Interact") goes through getKeyBinding, so the jump / canopy key became F and E leans instead),
 *     - leans on Q / E (LeanController has no height or air check: the spine bends over the dive pose, the camera shifts
 *       and rolls, and the lean is relayed to other players),
 *     - goes prone on a held Crouch (only refused in a vehicle, climbing or falling; HaloDrop keeps the fall speed at 0,
 *       so the game does not count the jumper as falling) and jumps on Space while aboard on the rig.
 *   Its pose code (PoseApplier.posed, every skeleton every frame) turns the head toward the view on its own (HaloDrop
 *   only skips Viewpoint's HeadLook / ArmsPitch, see VpPose) and lays a jump tuck on any character above the floor
 *   that the game does not count as falling, which is a Halo Drop jumper.
 * What: two patches, both only while Halo Drop runs; everything else stays AM's.
 *     PatchAmLive  InputController.computeLive(IsoPlayer) -> false while Lua publishes HaloDrop.AmBlock (ABOARD..CANOPY).
 *                  With AM not live: no key remap (Interact is E again), no lean, jump, prone or roll, no hidden keys.
 *     PatchAmPose  core.PoseApplier.posed(AnimationPlayer) skipped for a character with the anim variable HaloDropPose
 *                  (freefall, canopy), the same condition as VpPose. Hooks.posed itself still runs (AM's patch health
 *                  counter and its local frame step), only the bone changes are left out.
 * Lua -> Java: HaloDrop.AmBlock = { state, at } replaced every tick by HaloDropJumper.lua (amGateTick), read from
 *   LuaManager.env by reflection the same way as Lens. A state older than STALE_MS counts as none, so AM can never stay off.
 * If AM is not installed the target classes never load and the patches do nothing. If AM renames them, ZombieBuddy logs
 * that the patch found nothing and AM simply acts as before during a drop (nothing else breaks).
 * Public for the same reason as SkyLight: ZombieBuddy inlines the advice into AM's classes.
 */
public final class AmGate {
    static final String TAG = "[HaloDrop][AmGate] ";
    static final long REFRESH_NS = 8_000_000L;   // Lua state is re-read at most this often
    static final long STALE_MS = 1000L;

    private static boolean resolved, broken, reached;
    private static Field fEnv;
    private static Method mRawget;
    private static int errors;
    private static long blocked;

    private static volatile boolean started, active;
    private static volatile long lastRefresh;
    private static volatile String state = "";

    private AmGate() {}

    public static void log(String s) { System.out.println(TAG + s); }

    private static void fail(String where, Throwable t) {
        errors++;
        if (errors <= 5) log(where + " failed (" + errors + "): " + t);
        if (errors >= 20 && !broken) {
            broken = true;
            active = false;
            log("off after " + errors + " errors: Advanced Movement acts as usual during a drop");
        }
    }

    private static Object get(Object table, String key) throws Exception {
        return table == null ? null : mRawget.invoke(table, key);
    }

    /** Re-reads HaloDrop.AmBlock. Cheap between refreshes. */
    private static void refresh() {
        long n = System.nanoTime();
        if (started && n - lastRefresh < REFRESH_NS) return;
        lastRefresh = n;
        started = true;
        if (broken) return;
        try {
            if (!resolved) {
                ClassLoader cl = AmGate.class.getClassLoader();
                fEnv = Class.forName("zombie.Lua.LuaManager", false, cl).getField("env");
                mRawget = Class.forName("se.krka.kahlua.vm.KahluaTable", false, cl).getMethod("rawget", Object.class);
                resolved = true;
                log("state lookups resolved (LuaManager.env, KahluaTable.rawget)");
            }
            Object st = get(get(fEnv.get(null), "HaloDrop"), "AmBlock");
            boolean on = false, stale = false;
            String s = "";
            if (st != null) {
                Object at = get(st, "at");
                long age = at instanceof Number ? System.currentTimeMillis() - ((Number) at).longValue() : Long.MAX_VALUE;
                if (age <= STALE_MS) {
                    on = true;
                    Object v = get(st, "state");
                    s = v == null ? "?" : String.valueOf(v);
                } else {
                    stale = true;
                }
            }
            if (on != active) {
                active = on;
                if (on) {
                    blocked = 0;
                    log("Advanced Movement input off (Halo Drop state " + s + ")");
                } else {
                    log("Advanced Movement input back (" + (stale ? "Lua state went stale" : "Lua state gone")
                            + ", " + blocked + " live frames held off, last state " + state + ")");
                }
            } else if (on && !s.equals(state)) {
                log("still off, Halo Drop state " + state + " -> " + s);
            }
            state = s;
        } catch (Throwable t) {
            fail("state read", t);
        }
    }

    /** com.viewpointmovement.core.InputController.computeLive(IsoPlayer), on exit. Returns the value AM gets. */
    public static boolean live(boolean live) {
        try {
            if (!reached) {
                reached = true;
                log("patch reached: com.viewpointmovement.core.InputController.computeLive (AM live=" + live + ")");
            }
            refresh();
            if (live && active) {
                blocked++;
                return false;
            }
            return live;
        } catch (Throwable t) {
            fail("live", t);
            return live;
        }
    }
}
