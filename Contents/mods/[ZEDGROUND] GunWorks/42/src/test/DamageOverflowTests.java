import java.lang.reflect.Field;
import java.util.ArrayList;

import t3gunworks.combat.DamageOverflow;
import t3gunworks.config.BallisticsConfig;
import zombie.characters.BodyDamage.BodyDamage;
import zombie.characters.BodyDamage.BodyPart;
import zombie.characters.BodyDamage.BodyPartType;

/**
 * combat.DamageOverflow around a vanilla applyDamageFromWeapon, without a world: a bare BodyDamage with 17
 * full-health parts, the vanilla part damage done by hand between enter and exit (BodyPart.ReduceHealth clamps
 * at 0, as in the game), overall health from BodyDamage.calculateOverallHealth.
 * Run: java -cp build/classes:test-classes:projectzomboid.jar DamageOverflowTests
 */
public class DamageOverflowTests {
    static int passed, failed;
    static final int BULLET = 6;
    static final int HEAD = BodyPartType.ToIndex(BodyPartType.Head);
    static final int CHEST = BodyPartType.ToIndex(BodyPartType.Torso_Upper);
    static final int NECK = BodyPartType.ToIndex(BodyPartType.Neck);

    static void check(boolean ok, String what) { if (ok) passed++; else { failed++; System.out.println("FAIL " + what); } }
    static boolean near(float a, float b) { return Math.abs(a - b) < 0.05f; }

    static Object alloc(Class<?> c) throws Exception {
        Field u = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        u.setAccessible(true);
        return ((sun.misc.Unsafe) u.get(null)).allocateInstance(c);
    }

    static BodyDamage body() throws Exception {
        BodyDamage bd = (BodyDamage) alloc(BodyDamage.class);
        ArrayList<BodyPart> parts = new ArrayList<>();
        for (int i = 0; i < BodyPartType.ToIndex(BodyPartType.MAX); i++) {
            BodyPart p = (BodyPart) alloc(BodyPart.class);
            p.SetHealth(100f);
            parts.add(p);
        }
        Field f = BodyDamage.class.getDeclaredField("bodyParts");
        f.setAccessible(true);
        f.set(bd, parts);
        bd.calculateOverallHealth();
        return bd;
    }

    /** One wound the way the game applies it: our entry advice, vanilla AddDamage, our exit advice. */
    static float shot(BodyDamage bd, int part, float damage, int type) {
        DamageOverflow.enter(bd, part, damage, type);
        bd.getBodyParts().get(part).ReduceHealth(damage);
        DamageOverflow.exit(bd);
        bd.calculateOverallHealth();
        return bd.getOverallBodyHealth();
    }

    public static void main(String[] args) throws Exception {
        BallisticsConfig.debugLog = false;
        BallisticsConfig.verbose = false;

        // vanilla behaviour (overflow off, head share = vanilla): the chest stops at -35
        BallisticsConfig.overflowDamage = false;
        BallisticsConfig.headDamageShare = 0.6;
        BodyDamage bd = body();
        check(near(shot(bd, CHEST, 60, BULLET), 79f), "vanilla chest shot 1 -> 79");
        check(near(shot(bd, CHEST, 60, BULLET), 65f), "vanilla chest shot 2 -> 65 (part empty)");
        check(near(shot(bd, CHEST, 60, BULLET), 65f), "vanilla chest shot 3 does nothing");

        // overflow on: every chest round counts
        BallisticsConfig.overflowDamage = true;
        bd = body();
        check(near(shot(bd, CHEST, 60, BULLET), 79f), "overflow chest shot 1 -> 79 (no overflow yet)");
        check(near(shot(bd, CHEST, 60, BULLET), 58f), "overflow chest shot 2 -> 58");
        check(near(shot(bd, CHEST, 60, BULLET), 37f), "overflow chest shot 3 -> 37");
        check(near(shot(bd, CHEST, 60, BULLET), 16f), "overflow chest shot 4 -> 16");
        check(shot(bd, CHEST, 60, BULLET) <= 0.05f, "overflow chest shot 5 kills");
        check(bd.getBodyParts().get(CHEST).getHealth() == 0f, "chest part itself stays at 0");

        // head share 1.0: a full head wound counts 100
        BallisticsConfig.headDamageShare = 1.0;
        bd = body();
        check(near(shot(bd, HEAD, 50, BULLET), 50f), "head 50 with share 1.0 -> 50 (vanilla 70)");
        bd = body();
        check(shot(bd, HEAD, 120, BULLET) <= 0.05f, "head 120 with share 1.0 kills");
        BallisticsConfig.headDamageShare = 0.6;
        BallisticsConfig.overflowDamage = false;
        bd = body();
        check(near(shot(bd, HEAD, 120, BULLET), 40f), "head 120 vanilla -> 40");

        // other parts keep vanilla shares; the neck is not changed by the head setting
        BallisticsConfig.overflowDamage = true;
        BallisticsConfig.headDamageShare = 1.0;
        bd = body();
        check(near(shot(bd, NECK, 50, BULLET), 65f), "neck 50 -> 65 (share 0.7 kept)");

        // only bullet wounds: a blade cut (type 2) in an emptied part changes nothing
        bd = body();
        shot(bd, CHEST, 120, 2);
        float before = bd.getOverallBodyHealth();
        check(near(shot(bd, CHEST, 60, 2), before), "non-bullet damage has no overflow");

        // nothing left to take: the extra stops when every other part is empty, no exception
        bd = body();
        for (BodyPart p : bd.getBodyParts()) p.SetHealth(0f);
        bd.getBodyParts().get(CHEST).SetHealth(10f);
        shot(bd, CHEST, 500, BULLET);
        check(bd.getOverallBodyHealth() <= 0.05f, "all parts empty -> 0, no exception");

        // exit without enter (another thread's wound, a vanilla call outside a shot) does nothing
        bd = body();
        DamageOverflow.exit(bd);
        check(near(bd.getOverallBodyHealth(), 100f), "exit without enter is a no-op");

        // the hit part is never lowered by the spread
        bd = body();
        bd.getBodyParts().get(CHEST).SetHealth(30f);
        shot(bd, CHEST, 100, BULLET);
        check(bd.getBodyParts().get(CHEST).getHealth() == 0f, "hit part 0 after overflow");
        float sum = 0;
        for (int i = 0; i < bd.getBodyParts().size(); i++) if (i != CHEST) sum += (100f - bd.getBodyParts().get(i).getHealth()) * BodyPartType.getDamageModifyer(i);
        check(near(sum, 70f * 0.35f), "the overflow (70 x 0.35) lands on the other parts exactly");

        System.out.println("passed " + passed + ", failed " + failed);
        if (failed > 0) System.exit(1);
    }
}
