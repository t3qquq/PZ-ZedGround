package t3gunworks.network;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.List;

import t3gunworks.Log;
import t3gunworks.combat.TargetedWound;
import t3gunworks.config.BallisticsConfig;
import zombie.CombatManager;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.characters.animals.IsoAnimal;
import zombie.network.GameServer;
import zombie.network.fields.hit.Hit;
import zombie.network.packets.hit.PlayerHit;

/**
 * Server side of the 42.22 hit packet (zombie.network.packets.hit.PlayerHitPacket).
 *
 * Since 42.22 the server decides hits on players, animals and vehicles itself: it rolls vanilla's hit
 * chance and damage again and ignores what the shooter sent (only zombie hits keep the shooter's
 * result, CombatManager.isHitRolledByClient). A projectile has already decided what it hit, where and
 * with how much energy, so for hits that carry True Ballistics' marker the server keeps the shooter's
 * verdict - exactly what vanilla does for zombies and what 42.21 did for every target - and then runs
 * its usual validation (anti-cheat, ServerValidator).
 *
 * All of this happens inside PlayerHitPacket.processServer, on the server's main thread:
 *
 *   enter              the extra bits of every hit are read into a table and cleared, so vanilla only
 *                      ever sees its own values and relays clean packets to the other clients
 *   isHitRolledByClient   true for every target while a marked packet is processed
 *   getHitData         hit chance 100 while a marked packet is processed (vehicles)
 *   Hit.roll           a head hit keeps the shooter's head value (helmet, blood) - vanilla recomputes
 *                      it as 0 for firearms
 *   Hit.process        the victim is marked for the exact wound location (TargetedWound)
 *   exit               table cleared
 */
public final class ServerHits {
    private static final IdentityHashMap<Object, Integer> RAW = new IdentityHashMap<>();
    private static boolean packetBallistic;
    private static Field packetsField, hitsField, hitDataField;
    private static Method subPacket;
    private static boolean resolved, usable;

    public static long ballisticPackets;

    private ServerHits() {}

    /** PlayerHitPacket.processServer entry. */
    public static void packetEnter(Object playerHitPacket) {
        packetBallistic = false;
        RAW.clear();
        if (!GameServer.server || playerHitPacket == null || !resolve(playerHitPacket)) return;
        try {
            List<?> packets = (List<?>) packetsField.get(playerHitPacket);
            if (packets == null) return;
            for (Object entry : packets) {
                if (entry == null) continue;
                if (subPacket == null) {
                    subPacket = entry.getClass().getDeclaredMethod("playerHitPacket");
                    subPacket.setAccessible(true);
                }
                if (!(subPacket.invoke(entry) instanceof PlayerHit p)) continue;
                List<?> hits = (List<?>) hitsField.get(p);
                if (hits == null) continue;
                for (Object h : hits) {
                    int raw = TargetedWound.raw(h);
                    if (raw < 0 || (raw & ~TargetedWound.HEAD_MASK) == 0) continue;
                    if (TargetedWound.isBallistic(raw)) packetBallistic = true;
                    RAW.put(h, raw);
                    TargetedWound.strip(h, raw);
                }
            }
            if (packetBallistic) ballisticPackets++;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.errorOnce("reading a hit packet on the server (vanilla hit rules apply)", e);
            packetBallistic = false;
        }
    }

    /** PlayerHitPacket.processServer exit. */
    public static void packetExit() {
        packetBallistic = false;
        RAW.clear();
    }

    /** True while the server processes a packet whose hits were decided by projectiles. */
    public static boolean ballisticPacket() { return packetBallistic; }

    /** hitHead byte a hit arrived with (before the extra bits were cleared), or -1. */
    public static int rawOf(Object hit) {
        if (RAW.isEmpty()) return -1;
        Integer v = RAW.get(hit);
        return v != null ? v : -1;
    }

    /** Hit.roll exit: vanilla recomputes the head value on the server (0 for firearms); keep the shooter's. */
    public static void rolled(Object hit, boolean kept) {
        if (!kept || !packetBallistic || hitDataField == null) return;
        int raw = rawOf(hit);
        if (raw < 0 || !TargetedWound.isBallistic(raw) || (raw & TargetedWound.HEAD_MASK) == 0) return;
        try {
            if (hitDataField.get(hit) instanceof CombatManager.HitData d && d.hitHead() == 0) {
                hitDataField.set(hit, new CombatManager.HitData((byte) (raw & TargetedWound.HEAD_MASK), d.hitLegs(), d.isIgnoreDamage(),
                    d.isPiercedTargetDamageReduction(), d.isValidHitLocation()));
            }
        } catch (IllegalAccessException | RuntimeException e) {
            Log.errorOnce("keeping the head hit of a projectile", e);
        }
    }

    /** Hit.process entry: the wound of a player lands on the body part the round hit. */
    public static void processEnter(Object hit, Object target) {
        if (!packetBallistic || !BallisticsConfig.hitLocations) return;
        int type = TargetedWound.partOf(rawOf(hit));
        if (type >= 0 && target instanceof IsoPlayer p && !(p instanceof IsoAnimal)) TargetedWound.begin((IsoGameCharacter) p, type);
    }

    private static boolean resolve(Object playerHitPacket) {
        if (!resolved) {
            resolved = true;
            try {
                packetsField = playerHitPacket.getClass().getDeclaredField("packets");
                packetsField.setAccessible(true);
                hitsField = PlayerHit.class.getDeclaredField("hits");
                hitsField.setAccessible(true);
                usable = TargetedWound.available();
                try {
                    hitDataField = Hit.class.getDeclaredField("hitData");
                    hitDataField.setAccessible(true);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    Log.warn("Hit.hitData not found (" + e + "); head hits of projectiles use vanilla's server-side head value");
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                usable = false;
                Log.error("PlayerHitPacket.packets / PlayerHit.hits not found; the server applies vanilla's own hit rolls to projectile hits", e);
            }
        }
        return usable;
    }

    /** The hits of a sub-packet (validation), or null. */
    @SuppressWarnings("unchecked")
    public static List<Hit> hitsOf(PlayerHit p) {
        try {
            if (hitsField == null) {
                hitsField = PlayerHit.class.getDeclaredField("hits");
                hitsField.setAccessible(true);
            }
            return (List<Hit>) hitsField.get(p);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.errorOnce("reading the hits of a packet", e);
            return null;
        }
    }
}
