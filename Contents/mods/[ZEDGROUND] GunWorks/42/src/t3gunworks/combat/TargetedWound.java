package t3gunworks.combat;

import java.lang.reflect.Field;

import t3gunworks.Log;
import zombie.characters.BodyDamage.BodyDamage;
import zombie.characters.IsoGameCharacter;
import zombie.network.fields.hit.Hit;

/**
 * Makes a bullet wound land on the body part the round actually hit.
 *
 * Vanilla never targets a player's body part with a firearm: IsoPlayer.Hit ends in
 * BodyDamage.DamageFromWeapon(weapon, -1), and -1 means "pick a random part". In multiplayer that call
 * runs on the server while it processes the shooter's hit packet, which only says head / legs / other.
 *
 *   single player - DamageBridge marks the victim and the BodyPartType right before calling Hit
 *   multiplayer   - the shooter writes the BodyPartType into spare bits of the hit packet's hitHead byte
 *                   (vanilla 42.22 uses only the values 0..3); the server reads and clears them when the
 *                   packet is processed (ServerHits) and marks the victim while vanilla's Hit.process
 *                   applies the hit
 *   both          - BodyDamage.DamageFromWeapon(weapon, -1) for the marked victim gets that part instead
 *
 * The extra bits never reach vanilla code on a server that runs this jar, so vanilla's head logic
 * (blood, helmet falling off) is unchanged.
 */
public final class TargetedWound {
    private static final ThreadLocal<Pending> PENDING = ThreadLocal.withInitial(Pending::new);
    private static Field hitPartField;
    private static boolean fieldResolved;

    private static final class Pending {
        IsoGameCharacter victim;
        int type = -1;
    }

    private TargetedWound() {}

    /** Marks the next random-part wound on victim as the given BodyPartType ordinal (0..16). */
    public static void begin(IsoGameCharacter victim, int bodyPartType) {
        Pending p = PENDING.get();
        p.victim = bodyPartType >= 0 && bodyPartType <= 16 ? victim : null;
        p.type = bodyPartType;
    }

    public static void end() {
        Pending p = PENDING.get();
        p.victim = null;
        p.type = -1;
    }

    /** BodyDamage.DamageFromWeapon entry: replaces a random part (-1) for the marked victim. */
    public static int resolve(Object bodyDamage, int part) {
        if (part != -1) return part;
        Pending p = PENDING.get();
        if (p.victim == null || !(bodyDamage instanceof BodyDamage bd)) return part;
        if (bd.getParentChar() != p.victim) return part;
        return p.type;
    }

    // ----------------------------------------------------------------------------- hit packet bits
    //
    // Project Zomboid 42.22 hit packets: every hit is a zombie.network.fields.hit.Hit whose hitHead byte
    // only ever holds 0..3 in vanilla (bits 0-1). True Ballistics uses the rest of that byte:
    //   bits 2-6  BodyPartType ordinal + 1 (0 = no exact part)
    //   bit  7    "this hit was decided by a projectile" (the server keeps the shooter's verdict and damage)
    // A server that runs this jar reads and clears the extra bits before vanilla processes the packet
    // (ServerHits). They are only written once the server confirmed it runs the jar.

    public static final int HEAD_MASK = 0x3, BALLISTIC_BIT = 0x80;
    private static final int PART_SHIFT = 2, PART_MASK = 0x1F;

    /** Shooter side: marks a hit as decided by a projectile. */
    public static void markBallistic(Hit hit) {
        Field f = field();
        if (hit == null || f == null) return;
        try {
            f.setByte(hit, (byte) ((f.getByte(hit) & 0xFF) | BALLISTIC_BIT));
        } catch (IllegalAccessException | RuntimeException e) {
            Log.errorOnce("marking a ballistic hit", e);
        }
    }

    /** Shooter side: stores the BodyPartType in the hit packet next to vanilla's head value. */
    public static void encode(Hit hit, int bodyPartType) {
        if (hit == null || bodyPartType < 0 || bodyPartType > 16) return;
        Field f = field();
        if (f == null) return;
        try {
            int v = f.getByte(hit) & 0xFF & ~(PART_MASK << PART_SHIFT);
            f.setByte(hit, (byte) (v | ((bodyPartType + 1) << PART_SHIFT)));
        } catch (IllegalAccessException | RuntimeException e) {
            Log.errorOnce("writing the hit body part", e);
        }
    }

    /** Raw hitHead byte (0..255) of a hit, or -1 when it cannot be read. */
    public static int raw(Object hit) {
        Field f = field();
        if (f == null || !(hit instanceof Hit)) return -1;
        try {
            return f.getByte(hit) & 0xFF;
        } catch (IllegalAccessException | RuntimeException e) {
            Log.errorOnce("reading the hit body part", e);
            return -1;
        }
    }

    /** Server side: leaves only vanilla's own bits in the hit. */
    public static void strip(Object hit, int raw) {
        Field f = field();
        if (f == null || !(hit instanceof Hit)) return;
        try {
            f.setByte(hit, (byte) (raw & HEAD_MASK));
        } catch (IllegalAccessException | RuntimeException e) {
            Log.errorOnce("clearing the hit body part", e);
        }
    }

    public static boolean isBallistic(int raw) { return raw >= 0 && (raw & BALLISTIC_BIT) != 0; }

    /** BodyPartType in a raw hitHead byte, or -1. */
    public static int partOf(int raw) {
        if (raw < 0) return -1;
        int v = (raw >>> PART_SHIFT) & PART_MASK;
        return v >= 1 && v <= 17 ? v - 1 : -1;
    }

    /** BodyPartType stored in a hit packet, or -1 (a vanilla packet, or no exact part). */
    public static int decode(Object hit) {
        int remembered = t3gunworks.network.ServerHits.rawOf(hit);
        return partOf(remembered >= 0 ? remembered : raw(hit));
    }

    private static Field field() {
        if (!fieldResolved) {
            fieldResolved = true;
            try {
                Field f = Hit.class.getDeclaredField("hitHead");
                f.setAccessible(true);
                hitPartField = f;
            } catch (ReflectiveOperationException | RuntimeException e) {
                Log.warn("Hit.hitHead not found (" + e + "); player wounds use vanilla's random body part in multiplayer");
            }
        }
        return hitPartField;
    }

    public static boolean available() { return field() != null; }
}
