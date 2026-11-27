package t3zedballistics;

import me.zed_0xff.zombie_buddy.Patch;

/**
 * Dedicated-server hooks (also present in single player / hosts, where they do nothing unless
 * GameServer.server is true). Verified in Project Zomboid 42.22.2.
 *
 *   zombie.network.anticheats.AntiCheatHitWeapon.isDistanceExceeded(HandWeapon, float)   (private)
 *       - vanilla rejects hits beyond weapon MaxRange + 5; widened to the ballistic range for firearms
 *   zombie.network.anticheats.AntiCheatHitLongDistance.validate(UdpConnection, INetworkPacket)
 *       - vanilla rejects hits beyond the connection's relevant range; widened the same way for firearms
 *   zombie.network.packets.hit.PlayerHitZombiePacket.isConsistent(IConnection)
 *   zombie.network.packets.hit.PlayerHitPlayerPacket.isConsistent(IConnection)
 *       - True Ballistics' line-of-fire / damage validation (ServerValidator); a failed check removes the hit
 *   zombie.network.packets.hit.PlayerHitPacket.processServer(PacketType, UdpConnection)      (42.22)
 *   zombie.CombatManager.isHitRolledByClient(IsoMovingObject)                                (42.22, static)
 *   zombie.CombatManager.getHitData(IsoLivingCharacter, HandWeapon, IsoMovingObject, float)  (42.22)
 *       - since 42.22 the server rolls hit chance and damage for players, animals and vehicles itself; for
 *         hits a projectile decided it keeps the shooter's verdict, as it does for zombies (ServerHits)
 */
public final class ServerPatches {

    @Patch(className = "zombie.network.anticheats.AntiCheatHitWeapon", methodName = "isDistanceExceeded")
    public static class WeaponDistance {
        @Patch.OnExit
        public static void exit(@Patch.Argument(0) Object weapon, @Patch.Argument(1) float distance,
                                @Patch.Return(readOnly = false) boolean exceeded) {
            exceeded = Hooks.widenWeaponDistance(weapon, distance, exceeded);
        }
    }

    @Patch(className = "zombie.network.anticheats.AntiCheatHitLongDistance", methodName = "validate")
    public static class LongDistance {
        @Patch.OnExit
        public static void exit(@Patch.Argument(1) Object packet, @Patch.Return(readOnly = false) String result) {
            result = Hooks.widenLongDistance(packet, result);
        }
    }

    @Patch(className = "zombie.network.packets.hit.PlayerHitZombiePacket", methodName = "isConsistent")
    public static class ZombieHitConsistent {
        @Patch.OnExit
        public static void exit(@Patch.This Object packet, @Patch.Return(readOnly = false) boolean consistent) {
            consistent = Hooks.validateHit(packet, consistent);
        }
    }

    @Patch(className = "zombie.network.packets.hit.PlayerHitPlayerPacket", methodName = "isConsistent")
    public static class PlayerHitConsistent {
        @Patch.OnExit
        public static void exit(@Patch.This Object packet, @Patch.Return(readOnly = false) boolean consistent) {
            consistent = Hooks.validateHit(packet, consistent);
        }
    }

    @Patch(className = "zombie.network.packets.hit.PlayerHitPacket", methodName = "processServer")
    public static class HitPacketProcess {
        @Patch.OnEnter
        public static void enter(@Patch.This Object packet) {
            Hooks.hitPacketEnter(packet);
        }

        @Patch.OnExit(onThrowable = Throwable.class)
        public static void exit() {
            Hooks.hitPacketExit();
        }
    }

    @Patch(className = "zombie.CombatManager", methodName = "isHitRolledByClient")
    public static class HitRolledByClient {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) boolean byClient) {
            byClient = Hooks.hitRolledByClient(byClient);
        }
    }

    @Patch(className = "zombie.CombatManager", methodName = "getHitData")
    public static class HitChance {
        @Patch.OnEnter
        public static void enter(@Patch.Argument(value = 3, readOnly = false) float hitChance) {
            hitChance = Hooks.hitChance(hitChance);
        }
    }

    private ServerPatches() {}
}
