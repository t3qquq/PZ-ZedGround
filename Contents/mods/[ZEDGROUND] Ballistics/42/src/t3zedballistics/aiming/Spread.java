package t3zedballistics.aiming;

import t3zedballistics.config.BallisticsConfig;
import t3zedballistics.weapon.BallisticProfile;
import zombie.characters.IsoPlayer;
import zombie.characters.Moodles.Moodles;
import zombie.characters.skills.PerkFactory;
import zombie.inventory.types.HandWeapon;
import zombie.scripting.objects.MoodleType;

/**
 * Turns everything vanilla uses for hit chance into an angular dispersion, so skill and condition decide
 * where the round goes rather than whether a target "is hit".
 *
 *   effective accuracy = weapon HitChance + Aiming level * AimingPerkHitChanceModifier   (vanilla data)
 *   sigma (mrad)       = 1 + (100 - effective accuracy) * 0.1        (pistol L0 ~6, L10 ~2 mrad)
 *   x movement, stance, moodles (panic, stress, tired, endurance, drunk, pain), weapon condition
 *   + recoil bloom from rapid fire, decaying over ~0.25 s
 *   x sandbox SpreadScale
 *
 * The value is the standard deviation of a 2D normal distribution (per axis).
 */
public final class Spread {
    private static double bloomMrad;
    private static long bloomNanos;

    public static double sigmaRadians(IsoPlayer p, HandWeapon w, BallisticProfile prof) {
        double mrad;
        if (prof.baseSpreadMrad > 0) {
            mrad = prof.baseSpreadMrad;
        } else {
            int aiming = p.getPerkLevel(PerkFactory.Perks.Aiming);
            double acc = w.getHitChance() + aiming * w.getAimingPerkHitChanceModifier();
            acc = Math.max(0, Math.min(100, acc));
            mrad = 1.0 + (100.0 - acc) * 0.1;
        }

        double f = 1.0;
        if (p.isSprinting() || p.isRunning()) f *= 2.5;
        else if (p.isPlayerMoving()) f *= 1.5;
        if (p.isSneaking()) f *= 0.85;

        Moodles m = p.getMoodles();
        if (m != null) {
            f *= 1 + 0.15 * m.getMoodleLevel(MoodleType.PANIC);
            f *= 1 + 0.05 * m.getMoodleLevel(MoodleType.STRESS);
            f *= 1 + 0.05 * m.getMoodleLevel(MoodleType.TIRED);
            f *= 1 + 0.08 * m.getMoodleLevel(MoodleType.ENDURANCE);
            f *= 1 + 0.15 * m.getMoodleLevel(MoodleType.DRUNK);
            f *= 1 + 0.10 * m.getMoodleLevel(MoodleType.PAIN);
        }

        int cmax = w.getConditionMax();
        if (cmax > 0) {
            double c = Math.max(0, Math.min(1, (double) w.getCondition() / cmax));
            f *= 1 + 0.5 * (1 - c);
        }

        return (mrad * f + currentBloom()) * BallisticsConfig.spreadScale / 1000.0;
    }

    /** Adds recoil bloom for one fired round; heavier rounds kick more. */
    public static void addBloom(BallisticProfile prof) {
        double kick = Math.min(3.0, 0.4 + prof.energy(prof.muzzleVelocity) / 1500.0);
        bloomMrad = Math.min(12.0, currentBloom() + kick);
        bloomNanos = System.nanoTime();
    }

    private static double currentBloom() {
        if (bloomNanos == 0L) return 0;
        double age = (System.nanoTime() - bloomNanos) / 1e9;
        return bloomMrad * Math.exp(-age / 0.25);
    }

    private Spread() {}
}
