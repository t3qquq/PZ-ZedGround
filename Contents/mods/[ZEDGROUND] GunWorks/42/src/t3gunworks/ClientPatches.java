package t3gunworks;

import me.zed_0xff.zombie_buddy.Patch;

/**
 * Client-side ZombieBuddy advice. Parameters that name game types are declared as Object so the advice
 * never links against them directly (ZombieBuddy's recommended pattern); bodies only forward to Hooks,
 * which catches its own errors. Targets verified in Project Zomboid 42.22.2 (projectzomboid.jar).
 *
 *   zombie.CombatManager.attackCollisionCheck(IsoGameCharacter, HandWeapon, SwipeStatePlayer, AttackType)
 *       - called once per trigger pull from SwipeStatePlayer's AttackCollisionCheck anim event, after the
 *         gun actually fired (ammo/jam checks happen before in ISReloadWeaponAction.attackHook)
 *   zombie.CombatManager.calculateHitInfoList(IsoGameCharacter)
 *       - vanilla's instant target selection; its result is discarded for ballistic shots
 *   (zombie.CombatManager.fireWeapon is deliberately NOT patched: it only draws vanilla's isometric
 *    tracer, and other mods - e.g. Project Viewpoint ADS - hook it for recoil.)
 *   zombie.CombatManager.CheckObjectHit(IsoGameCharacter, HandWeapon)      (private)
 *       - vanilla's "door/window/tree right in front" hit; skipped for ballistic shots
 *   zombie.network.packets.INetworkPacket.send(...)      (static, three overloads; 42.22)
 *       - attackCollisionCheck ends with INetworkPacket.send(PacketType.PlayerHit, owner, weapon, hit lists,
 *         tracers) (42.21: GameClient.sendPlayerHit); for a ballistic shot that instant "hit nothing" packet
 *         is dropped and the real result is sent when the round lands
 *   zombie.CombatManager.update(boolean)
 *       - per-frame update from GameWindow.logic; drives the projectiles
 */
public final class ClientPatches {

    @Patch(className = "zombie.CombatManager", methodName = "attackCollisionCheck")
    public static class AttackCollisionCheck {
        @Patch.OnEnter
        public static void enter(@Patch.Argument(0) Object owner, @Patch.Argument(1) Object weapon) {
            Hooks.attackEnter(owner, weapon);
        }

        @Patch.OnExit(onThrowable = Throwable.class)
        public static void exit(@Patch.Argument(0) Object owner) {
            Hooks.attackExit(owner);
        }
    }

    @Patch(className = "zombie.CombatManager", methodName = "calculateHitInfoList")
    public static class CalculateHitInfoList {
        @Patch.OnExit
        public static void exit(@Patch.Argument(0) Object owner) {
            Hooks.hitListBuilt(owner);
        }
    }

    /**
     * Vanilla's isometric tracer for the instant shot (both overloads, private). Its effect plays an impact
     * sound and shoots placed items on vanilla's guessed square, which would duplicate the projectile's own
     * impact, so it is skipped for ballistic shots. fireWeapon itself still runs (other mods hook it).
     */
    @Patch(className = "zombie.CombatManager", methodName = "addTracerEffect")
    public static class AddTracerEffect {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter() {
            return Hooks.skipVanillaTracer();
        }
    }

    @Patch(className = "zombie.CombatManager", methodName = "CheckObjectHit")
    public static class CheckObjectHit {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter() {
            return Hooks.skipAdjacentObjectHit();
        }
    }

    @Patch(className = "zombie.network.packets.INetworkPacket", methodName = "send")
    public static class SendPacket {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter(@Patch.Argument(0) Object a0, @Patch.Argument(1) Object a1) {
            return Hooks.skipInstantMissPacket(a0, a1);
        }
    }

    @Patch(className = "zombie.CombatManager", methodName = "update")
    public static class Update {
        @Patch.OnExit
        public static void exit() {
            Hooks.combatUpdate();
        }
    }

    private ClientPatches() {}
}
