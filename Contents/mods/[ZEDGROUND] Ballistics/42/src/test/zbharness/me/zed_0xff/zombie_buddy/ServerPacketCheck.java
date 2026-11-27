package me.zed_0xff.zombie_buddy;

import java.lang.reflect.Field;

/** Part of OfflinePatchTest: the server side of a 42.22 hit packet, run after the patches are installed. */
public class ServerPacketCheck {
    /** A marked hit inside a real PlayerHitPacket, as the dedicated server sees it. */
    public static void run() throws Exception {
        boolean wasServer = zombie.network.GameServer.server;
        try {
            zombie.network.GameServer.server = true;
            zombie.network.fields.hit.Hit hit = new zombie.network.fields.hit.Hit();
            hit.set(0f, 0f, 0f, new zombie.CombatManager.HitData((byte) 1, false, false, false, true), false, 12f);
            t3zedballistics.combat.TargetedWound.markBallistic(hit);
            t3zedballistics.combat.TargetedWound.encode(hit, 14);
            int raw = t3zedballistics.combat.TargetedWound.raw(hit);
            zombie.network.fields.hit.Hit vanilla = new zombie.network.fields.hit.Hit();
            vanilla.set(0f, 0f, 0f, new zombie.CombatManager.HitData((byte) 2, false, false, false, true), false, 3f);

            zombie.network.packets.hit.PlayerHitPlayerPacket sub = new zombie.network.packets.hit.PlayerHitPlayerPacket();
            t3zedballistics.network.ServerHits.hitsOf(sub).add(hit);
            t3zedballistics.network.ServerHits.hitsOf(sub).add(vanilla);
            zombie.network.packets.hit.PlayerHitPacket packet = new zombie.network.packets.hit.PlayerHitPacket();
            Field packets = packet.getClass().getDeclaredField("packets");
            packets.setAccessible(true);
            java.lang.reflect.Constructor<?> entry = Class.forName("zombie.network.packets.hit.PlayerHitPacket$Packet").getDeclaredConstructors()[0];
            entry.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.List<Object> list = (java.util.List<Object>) packets.get(packet);
            list.add(entry.newInstance(zombie.network.PacketTypes.PacketType.PlayerHitPlayer, sub));

            t3zedballistics.Hooks.hitPacketEnter(packet);
            OfflinePatchTest.check(t3zedballistics.network.ServerHits.ballisticPacket(), "server recognises a projectile packet");
            OfflinePatchTest.check(t3zedballistics.combat.TargetedWound.raw(hit) == 1, "extra bits cleared before vanilla reads the hit (head value kept)");
            OfflinePatchTest.check(t3zedballistics.combat.TargetedWound.raw(vanilla) == 2, "a vanilla hit in the same packet is untouched");
            OfflinePatchTest.check(t3zedballistics.network.ServerHits.rawOf(hit) == raw && t3zedballistics.combat.TargetedWound.decode(hit) == 14,
                "the exact body part is remembered for the wound");
            OfflinePatchTest.check(t3zedballistics.Hooks.hitChance(37f) > 100f, "no second hit-chance roll for a projectile packet");
            OfflinePatchTest.check(zombie.CombatManager.isHitRolledByClient(null), "the shooter's verdict stands while the packet is processed");

            // vanilla's roll recomputes the head value as 0 for firearms; the hook restores the shooter's
            Field hitData = zombie.network.fields.hit.Hit.class.getDeclaredField("hitData");
            hitData.setAccessible(true);
            hitData.set(hit, new zombie.CombatManager.HitData((byte) 0, false, false, true, true));
            t3zedballistics.Hooks.hitRolled(hit, true);
            zombie.CombatManager.HitData kept = (zombie.CombatManager.HitData) hitData.get(hit);
            OfflinePatchTest.check(kept.hitHead() == 1 && kept.isPiercedTargetDamageReduction(), "head hit of a projectile survives the server's roll");
            hitData.set(vanilla, new zombie.CombatManager.HitData((byte) 0, false, false, false, true));
            t3zedballistics.Hooks.hitRolled(vanilla, true);
            OfflinePatchTest.check(((zombie.CombatManager.HitData) hitData.get(vanilla)).hitHead() == 0, "a vanilla hit keeps the server's own head value");

            t3zedballistics.Hooks.hitPacketExit();
            OfflinePatchTest.check(!t3zedballistics.network.ServerHits.ballisticPacket() && t3zedballistics.network.ServerHits.rawOf(hit) == -1, "state cleared after the packet");
            OfflinePatchTest.check(t3zedballistics.Hooks.hitChance(37f) == 37f && !zombie.CombatManager.isHitRolledByClient(null), "vanilla rules outside a projectile packet");

            // a packet without the marker changes nothing
            list.clear();
            zombie.network.packets.hit.PlayerHitPlayerPacket plain = new zombie.network.packets.hit.PlayerHitPlayerPacket();
            t3zedballistics.network.ServerHits.hitsOf(plain).add(vanilla);
            list.add(entry.newInstance(zombie.network.PacketTypes.PacketType.PlayerHitPlayer, plain));
            t3zedballistics.Hooks.hitPacketEnter(packet);
            OfflinePatchTest.check(!t3zedballistics.network.ServerHits.ballisticPacket() && t3zedballistics.combat.TargetedWound.raw(vanilla) == 2, "vanilla packet: vanilla rules");
            t3zedballistics.Hooks.hitPacketExit();
        } finally {
            zombie.network.GameServer.server = wasServer;
        }
    }
}
