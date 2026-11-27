package t3zedballistics.api;

import java.util.concurrent.CopyOnWriteArrayList;

import t3zedballistics.Log;
import zombie.characters.IsoGameCharacter;
import zombie.inventory.types.HandWeapon;

/**
 * Confirmed-impact event contract for add-ons (wound, gore and armour mods). Added in 1.3.0.
 *
 * True Ballistics stays the only system that flies rounds and resolves hits. This class only reports
 * what the projectile system already knows at the moment a round damages a character; listeners must
 * not fire rounds, roll hits, change damage or touch penetration.
 *
 * <h2>When events fire</h2>
 * <ul>
 *   <li><b>Player rounds</b> - on the machine that resolves the round (single player, or the shooting
 *       client in multiplayer), from {@code DamageBridge.hitCharacter}, once per pellet that reaches a
 *       body and passes the PvP / MaxHitCount checks. Phase {@link #PHASE_APPLIED}: vanilla's
 *       {@code target.Hit(...)} has already run, {@link Impact#damageApplied} and {@link Impact#killed}
 *       are set.</li>
 *   <li><b>NPC rounds</b> ({@code ZGB_npcFire}) - on the authoritative side (single player or the
 *       dedicated server) when the round touches a body, before the NPC mod's Lua applies its damage.
 *       Phase {@link #PHASE_REPORTED}: damageApplied is NaN.</li>
 * </ul>
 * In multiplayer a player round's event therefore fires on the shooter's client only. The server
 * learns the hit from vanilla's hit packet; {@link #decodeBodyPartType(Object)} recovers the exact body
 * part from it when {@link #requestZombiePartEncoding()} was called (see there).
 *
 * <h2>Ordering, ownership and idempotency</h2>
 * Listeners run synchronously on the game thread, in registration order, after vanilla applied the
 * hit. Each event carries a process-unique {@link Impact#impactId}; one impact produces exactly one
 * event. While vanilla's {@code target.Hit(...)} for a player round is running, {@link #current()}
 * returns the impact being applied (phase {@link #PHASE_APPLYING}), so code hooked into the vanilla
 * damage path (for example an {@code IsoZombie.Hit} patch) can recognise a ballistic hit and leave
 * it to its ImpactEvents listener instead of counting it twice.
 *
 * <h2>Data</h2>
 * Only values the projectile system really has. Metric space: x, y in tiles (= metres), u = height in
 * metres ({@code z * 2.4494896}). The {@link Impact} object is reused: copy what you keep.
 * Listener exceptions are caught and logged once; they never reach the game or other listeners.
 */
public final class ImpactEvents {

    public static final int PHASE_APPLYING = 0, PHASE_APPLIED = 1, PHASE_REPORTED = 2;

    /** Listener for confirmed ballistic impacts. */
    public interface Listener {
        void onConfirmedBallisticImpact(Impact impact);
    }

    /** One round touching one body. Reused between events; valid only during the callback. */
    public static final class Impact {
        /** Process-unique, increasing id of this impact. */
        public long impactId;
        public int phase;
        /** Shooter: an IsoPlayer for player rounds, the NPC character for NPC rounds (may be null). */
        public IsoGameCharacter shooter;
        public IsoGameCharacter target;
        public HandWeapon weapon;
        /** Shot sequence number on this machine (one trigger pull), pellet index within it, pellet count. */
        public long shotSeq;
        public int pellet, pelletCount;
        /** zombie.core.physics.RagdollBodyPart ordinal the capsule stands for (t3zedballistics.collision.HitVolumes constants). */
        public int ragdollPart = -1;
        /** zombie.characters.BodyDamage.BodyPartType ordinal of the exact spot (hands, feet, neck, groin included), -1 unknown. */
        public int bodyPartType = -1;
        /** Entry point of the round into the hit capsule (metric). */
        public double hitX, hitY, hitU;
        /** Unit flight direction at impact (metric). */
        public double dirX, dirY, dirU;
        /**
         * Where the straight continuation of the path leaves the same capsule (metric), valid when
         * {@link #hasCapsuleExit}. This is geometry only; whether the round actually exits is
         * {@link #penetrates}.
         */
        public boolean hasCapsuleExit;
        public double capsuleExitX, capsuleExitY, capsuleExitU;
        /** Speed at impact and muzzle speed after the sandbox velocity scale (m/s), and their ratio. */
        public double speed, muzzleSpeed, speedRatio;
        /**
         * Kinetic energy at impact in real-world joules: the profile's unscaled muzzle velocity times
         * speedRatio, with the profile's projectile mass. Independent of the sandbox velocity scale.
         */
        public double energyJoules;
        public double massGrams;
        /** Profile category text (e.g. "buckshot", "full-power rifle") and penetration budget of the round. */
        public String category;
        public int penetrationBudget;
        /** The round already passed through another body before this one. */
        public boolean pierced;
        /** The round keeps flying through this body after the impact (penetration). */
        public boolean penetrates;
        /** Damage vanilla's roll produced after falloff and location split, and what Hit() returned (NaN when not applied). */
        public float damageRolled = Float.NaN, damageApplied = Float.NaN;
        public boolean killed;
        /** Stress-test rounds never produce events; NPC rounds set this. */
        public boolean npc;

        void clear() {
            phase = 0; shooter = null; target = null; weapon = null; shotSeq = 0; pellet = 0; pelletCount = 1;
            ragdollPart = -1; bodyPartType = -1; hitX = hitY = hitU = 0; dirX = dirY = dirU = 0;
            hasCapsuleExit = false; capsuleExitX = capsuleExitY = capsuleExitU = 0;
            speed = muzzleSpeed = speedRatio = 0; energyJoules = 0; massGrams = 0; category = null;
            penetrationBudget = 0; pierced = false; penetrates = false; damageRolled = Float.NaN;
            damageApplied = Float.NaN; killed = false; npc = false;
        }
    }

    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Impact SCRATCH = new Impact();
    private static long nextId = 1;
    private static Impact current;
    private static volatile boolean zombiePartEncoding;

    public static void addListener(Listener l) { if (l != null && !LISTENERS.contains(l)) LISTENERS.add(l); }

    public static void removeListener(Listener l) { LISTENERS.remove(l); }

    public static boolean hasListeners() { return !LISTENERS.isEmpty(); }

    /** The player-round impact whose vanilla Hit() is running right now (game thread), else null. */
    public static Impact current() { return current; }

    /**
     * Asks True Ballistics to also write the exact BodyPartType of zombie hits into the multiplayer hit
     * packet (the spare bits of Hit.hitHead that player hits use; 42.21: WeaponHit.hitPart). It is only
     * done while the server has confirmed it runs True Ballistics' Java part, because only then does
     * the server read vanilla's head/legs flag through the masking patch. Call it on clients and the
     * server alike; it changes nothing else.
     */
    public static void requestZombiePartEncoding() { zombiePartEncoding = true; }

    public static boolean zombiePartEncodingRequested() { return zombiePartEncoding; }

    /** BodyPartType stored in a received zombie.network.fields.hit.Hit, or -1. */
    public static int decodeBodyPartType(Object weaponHit) { return t3zedballistics.combat.TargetedWound.decode(weaponHit); }

    // ------------------------------------------------------------------------- internal (True Ballistics)

    /** Starts an impact; returns the reused object to fill. Game thread. */
    public static Impact begin() {
        Impact i = SCRATCH;
        i.clear();
        i.impactId = nextId++;
        return i;
    }

    /** Marks i as the impact being applied by vanilla's Hit(). */
    public static void applying(Impact i) {
        i.phase = PHASE_APPLYING;
        current = i;
    }

    /** Vanilla's Hit() finished (also on exceptions). */
    public static void applied() { current = null; }

    /** Delivers i to every listener with the given phase. */
    public static void fire(Impact i, int phase) {
        current = null;
        if (LISTENERS.isEmpty()) return;
        i.phase = phase;
        for (Listener l : LISTENERS) {
            try {
                l.onConfirmedBallisticImpact(i);
            } catch (Throwable t) {
                Log.errorOnce("impact listener " + l.getClass().getName(), t);
            }
        }
    }

    private ImpactEvents() {}
}
