package t3gunworks;

import me.zed_0xff.zombie_buddy.Patch;

/**
 * ZombieBuddy advice for exact bullet wound locations on players (see t3gunworks.combat.TargetedWound).
 * Targets verified in Project Zomboid 42.22.2:
 *
 *   zombie.characters.BodyDamage.BodyDamage.DamageFromWeapon(HandWeapon, int)   - -1 = random part
 *   zombie.network.fields.hit.Hit.process(IsoGameCharacter, IsoGameCharacter, HandWeapon)
 *       - applies one hit of a packet on the server (also matches the two-argument helper it calls last)
 *   zombie.characters.BodyDamage.BodyDamage.applyDamageFromWeapon(int, float, int, float)
 *       - part, damage, damage type (6 = bullet), pain; bullet damage overflow (combat.DamageOverflow)
 *   zombie.network.fields.hit.Hit.roll(IsoGameCharacter, IsoGameCharacter, HandWeapon)
 *       - the server recomputes the hit's head value here (42.21: WeaponHit.process / isHitHead / isHitLegs)
 */
public final class WoundPatches {

    @Patch(className = "zombie.characters.BodyDamage.BodyDamage", methodName = "DamageFromWeapon")
    public static class DamageFromWeapon {
        @Patch.OnEnter
        public static void enter(@Patch.This Object self, @Patch.Argument(value = 1, readOnly = false) int part) {
            part = Hooks.woundPart(self, part);
        }
    }

    @Patch(className = "zombie.characters.BodyDamage.BodyDamage", methodName = "applyDamageFromWeapon")
    public static class ApplyDamageFromWeapon {
        @Patch.OnEnter
        public static void enter(@Patch.This Object self, @Patch.Argument(0) int part, @Patch.Argument(1) float damage,
                                 @Patch.Argument(2) int damageType) {
            Hooks.overflowEnter(self, part, damage, damageType);
        }

        @Patch.OnExit(onThrowable = Throwable.class)
        public static void exit(@Patch.This Object self) {
            Hooks.overflowExit(self);
        }
    }

    @Patch(className = "zombie.network.fields.hit.Hit", methodName = "process")
    public static class HitProcess {
        @Patch.OnEnter
        public static void enter(@Patch.This Object self, @Patch.Argument(1) Object target) {
            Hooks.hitProcessEnter(self, target);
        }

        @Patch.OnExit(onThrowable = Throwable.class)
        public static void exit() {
            Hooks.hitProcessExit();
        }
    }

    @Patch(className = "zombie.network.fields.hit.Hit", methodName = "roll")
    public static class HitRoll {
        @Patch.OnExit
        public static void exit(@Patch.This Object self, @Patch.Return boolean kept) {
            Hooks.hitRolled(self, kept);
        }
    }

    private WoundPatches() {}
}
