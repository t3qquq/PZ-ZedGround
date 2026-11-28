package t3gunworks.collision;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

import zombie.characters.IsoGameCharacter;
import zombie.iso.IsoCell;
import zombie.iso.IsoMovingObject;

/**
 * Where characters were a moment ago, so a round is tested against the body the shooter actually saw.
 *
 * Why: a simulated round is not hitscan. Between the frame on screen and the round reaching the target pass
 *   - the frame latency (what is drawn is the last snapshot; the gun fires on the attack event after it), and
 *   - the flight time, which the sandbox velocity scale (default 0.4) makes 2.5x longer than real, and
 *     slow frames stretch further.
 * A walking zombie moves 0.1-0.4 m in that time, a sprinter a metre or more - more than a torso is wide. The
 * round then flies through the body that was under the crosshair ("bullets go through zombies, the hitboxes
 * don't match the models").
 *
 * Fix (lag compensation, as shooters do it): every tick the position of each character near the local player
 * is recorded. A player's round tests a character at its position at seen + real flight time, where "seen" is
 * the Viewpoint snapshot the shot was aimed on and "real flight time" is the round's age times the velocity
 * scale - the time the round would really have needed. Only the position is rewound (the skeleton's current
 * pose is shifted along); the rewind is capped. Rounds fired by NPCs or on the server are not rewound.
 *
 * Main thread only.
 */
public final class CharacterHistory {
    public static final long MAX_REWIND_NANOS = 500_000_000L;
    private static final int SAMPLES = 48;
    private static final long FORGET_NANOS = 2_000_000_000L;
    private static final double RANGE = 130.0;

    private static final class Track {
        final long[] t = new long[SAMPLES];
        final float[] x = new float[SAMPLES], y = new float[SAMPLES], z = new float[SAMPLES];
        int head = -1, n;
        long seen;

        void add(long now, float px, float py, float pz) {
            head = (head + 1) % SAMPLES;
            t[head] = now; x[head] = px; y[head] = py; z[head] = pz;
            if (n < SAMPLES) n++;
            seen = now;
        }
    }

    private static final Map<IsoGameCharacter, Track> tracks = new IdentityHashMap<>();
    private static long lastPrune;
    /** System.nanoTime() of the local player's latest Viewpoint frame snapshot (main thread). */
    private static volatile long snapshotNanos;
    private static final long SNAPSHOT_STALE_NANOS = 300_000_000L;

    /** Viewpoint took a frame snapshot for the local player: what the player is now shown. */
    public static void noteSnapshot(long now, zombie.characters.IsoGameCharacter viewer) {
        snapshotNanos = now;
        if (viewer == null || zombie.iso.IsoWorld.instance == null) return;
        // the positions this frame shows, stamped with the moment they were shown
        record(zombie.iso.IsoWorld.instance.getCell(), viewer.getX(), viewer.getY(), now);
    }

    /** True while Viewpoint draws for the local player (history is only kept then). */
    public static boolean wanted(long now) {
        long s = snapshotNanos;
        if (s == 0 || now - s > 1_000_000_000L) {
            if (!tracks.isEmpty()) tracks.clear();
            return false;
        }
        return true;
    }

    /**
     * The snapshot a shot fired now by `shooter` was aimed on: the local player's latest Viewpoint frame,
     * 0 (no compensation) for anyone else, outside Viewpoint or when no recent frame is known.
     */
    public static long seenFor(zombie.characters.IsoPlayer shooter, long fired) {
        if (shooter == null || shooter != zombie.characters.IsoPlayer.players[0] || !shooter.isLocalPlayer()) return 0;
        long s = snapshotNanos;
        if (s <= 0 || fired - s > SNAPSHOT_STALE_NANOS || s > fired) return 0;
        return s;
    }

    /** Records every character within RANGE of (cx, cy). Called once per tick. */
    public static void record(IsoCell cell, double cx, double cy, long now) {
        if (cell == null) return;
        double r2 = RANGE * RANGE;
        for (IsoMovingObject o : cell.getObjectList()) {
            if (!(o instanceof IsoGameCharacter c)) continue;
            float x = c.getX(), y = c.getY();
            double dx = x - cx, dy = y - cy;
            if (dx * dx + dy * dy > r2) continue;
            Track tr = tracks.get(c);
            if (tr == null) tracks.put(c, tr = new Track());
            tr.add(now, x, y, c.getZ());
        }
        if (now - lastPrune > 1_000_000_000L) {
            lastPrune = now;
            for (Iterator<Track> it = tracks.values().iterator(); it.hasNext(); ) {
                if (now - it.next().seen > FORGET_NANOS) it.remove();
            }
        }
    }

    public static void clear() { tracks.clear(); }

    /**
     * Writes where c was at time `at` (System.nanoTime) minus where it is now, in tiles/levels, to out
     * {dx, dy, dz}. Returns false (out zeroed) when nothing is known or `at` is not in the past.
     */
    public static boolean offset(IsoGameCharacter c, long at, long now, double[] out) {
        out[0] = out[1] = out[2] = 0;
        if (at <= 0 || at >= now) return false;
        Track tr = tracks.get(c);
        if (tr == null || tr.n == 0) return false;
        if (now - at > MAX_REWIND_NANOS) at = now - MAX_REWIND_NANOS;
        // newest sample at or before `at`, and the one after it
        int i = tr.head;
        int newer = -1;
        for (int k = 0; k < tr.n; k++) {
            if (tr.t[i] <= at) break;
            newer = i;
            i = (i - 1 + SAMPLES) % SAMPLES;
            if (k == tr.n - 1) i = -1;
        }
        double px, py, pz;
        if (i < 0) {
            // older than what is recorded: the oldest sample
            int o = newer;
            px = tr.x[o]; py = tr.y[o]; pz = tr.z[o];
        } else if (newer < 0) {
            return false; // `at` is after the newest sample: the present
        } else {
            long span = tr.t[newer] - tr.t[i];
            double f = span > 0 ? (double) (at - tr.t[i]) / span : 0;
            px = tr.x[i] + (tr.x[newer] - tr.x[i]) * f;
            py = tr.y[i] + (tr.y[newer] - tr.y[i]) * f;
            pz = tr.z[i] + (tr.z[newer] - tr.z[i]) * f;
        }
        out[0] = px - c.getX();
        out[1] = py - c.getY();
        out[2] = pz - c.getZ();
        // a teleport, a respawned id or a fall through floors is not motion to compensate
        if (out[0] * out[0] + out[1] * out[1] > 9.0 || Math.abs(out[2]) > 0.75) {
            out[0] = out[1] = out[2] = 0;
            return false;
        }
        return true;
    }

    /** Test hook: records one sample for c at time t. */
    static void put(IsoGameCharacter c, long t, float x, float y, float z) {
        Track tr = tracks.get(c);
        if (tr == null) tracks.put(c, tr = new Track());
        tr.add(t, x, y, z);
    }

    private CharacterHistory() {}
}
