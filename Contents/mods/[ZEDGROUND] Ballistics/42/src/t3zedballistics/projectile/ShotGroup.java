package t3zedballistics.projectile;

import java.util.ArrayList;

import zombie.characters.IsoPlayer;
import zombie.inventory.types.HandWeapon;
import zombie.iso.IsoObject;
import zombie.network.fields.hit.TracerInfo;
import zombie.network.fields.hit.Hit;
import t3zedballistics.weapon.BallisticProfile;

/**
 * One trigger pull: all pellets of that round, what they hit, and the network hits that must be reported
 * for it. Mirrors vanilla's per-attack CombatManager.TargetHitList bookkeeping so that the hit packets the
 * server receives look exactly like vanilla's (42.22: one PlayerHit packet with one entry per target, or one
 * "hit nothing" entry).
 */
public final class ShotGroup {
    public static final int MAX_TARGETS = 12;

    public long seq;
    public IsoPlayer shooter;
    public HandWeapon weapon;
    public BallisticProfile profile;
    public int pelletsAlive;
    public long firedNanos;
    /** The Viewpoint snapshot this shot was aimed on (lag compensation, CharacterHistory); 0 = none. */
    public long seenNanos;
    public boolean test;          // stress-test rounds: no damage, no network
    public boolean tracer;

    public int characterHits;     // capped at weapon MaxHitCount like vanilla
    public boolean conditionHit;  // vanilla runs weapon condition loss once when an attack hits
    public boolean xpAwarded;     // shotguns award XP once per shot (vanilla)

    public final IsoObject[] targets = new IsoObject[MAX_TARGETS];
    @SuppressWarnings({"unchecked", "rawtypes"})
    public final ArrayList<Hit>[] hits = new ArrayList[MAX_TARGETS];
    public final boolean[] crit = new boolean[MAX_TARGETS];
    public final boolean[] helmetFall = new boolean[MAX_TARGETS];
    public int targetCount;
    public final ArrayList<TracerInfo> tracers = new ArrayList<>(8);

    public ShotGroup() {
        for (int i = 0; i < MAX_TARGETS; i++) hits[i] = new ArrayList<>(4);
    }

    public void reset() {
        shooter = null;
        weapon = null;
        profile = null;
        pelletsAlive = 0;
        seenNanos = 0;
        test = false;
        tracer = false;
        characterHits = 0;
        conditionHit = false;
        xpAwarded = false;
        for (int i = 0; i < targetCount; i++) {
            targets[i] = null;
            hits[i].clear();
            crit[i] = false;
            helmetFall[i] = false;
        }
        targetCount = 0;
        tracers.clear();
    }

    /** Records a network hit on target (merged with earlier pellets that hit the same target). */
    public void addHit(IsoObject target, Hit hit, boolean isCrit, boolean helmet) {
        for (int i = 0; i < targetCount; i++) {
            if (targets[i] == target) {
                hits[i].add(hit);
                crit[i] |= isCrit;
                helmetFall[i] |= helmet;
                return;
            }
        }
        if (targetCount >= MAX_TARGETS) return;
        targets[targetCount] = target;
        hits[targetCount].add(hit);
        crit[targetCount] = isCrit;
        helmetFall[targetCount] = helmet;
        targetCount++;
    }

    public void addTracer(TracerInfo t) {
        if (tracers.size() < 8) tracers.add(t);
    }

    public boolean complete() { return pelletsAlive <= 0; }
}
