package t3gunworks.combat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import t3gunworks.Log;
import zombie.CombatManager;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoZombie;
import zombie.combat.ShotDirection;
import zombie.core.physics.RagdollBodyPart;
import zombie.inventory.types.HandWeapon;

/**
 * Reflection access to the private CombatManager methods that make up vanilla's per-target ranged hit
 * (PZ 42.21). Looked up once at start; each is optional, and when one is missing the hit falls back to
 * the public vanilla path (CombatManager.processHit) instead of failing.
 *
 *   processTargetedHit(HandWeapon, IsoGameCharacter wielder, IsoGameCharacter target, RagdollBodyPart, ShotDirection)
 *       - vanilla's "the ballistics camera says this body part was hit" path: hit reaction, blood, ragdoll data
 *   calculateShotDirection(IsoGameCharacter wielder, IsoGameCharacter target) -> ShotDirection
 *   applyRangeHitLocationDamage(IsoGameCharacter target, HandWeapon, int bodyPart, float damage) -> float
 *       - head multiplier (CombatConfig HEAD_HIT_DAMAGE_SPLIT_MODIFIER), limb multiplier, clothing defence
 *   setUsePhysicHitReaction(HandWeapon, IsoGameCharacter)
 *   setParameterCharacterHitResult(IsoGameCharacter, IsoZombie, long) - hit sound parameter (cosmetic)
 */
public final class VanillaCombat {
    private static Method processTargetedHit, calculateShotDirection, applyRangeHitLocationDamage,
        setUsePhysicHitReaction, setParameterCharacterHitResult;
    public static boolean targetedHits, locationDamage;
    private static java.lang.reflect.Field objHit, treeHit, hitOnlyTree;
    public static boolean objectHitControl;

    public static void init() {
        Class<?> cm = CombatManager.class;
        processTargetedHit = find(cm, "processTargetedHit", HandWeapon.class, IsoGameCharacter.class,
            IsoGameCharacter.class, RagdollBodyPart.class, ShotDirection.class);
        calculateShotDirection = find(cm, "calculateShotDirection", IsoGameCharacter.class, IsoGameCharacter.class);
        applyRangeHitLocationDamage = find(cm, "applyRangeHitLocationDamage", IsoGameCharacter.class,
            HandWeapon.class, int.class, float.class);
        setUsePhysicHitReaction = find(cm, "setUsePhysicHitReaction", HandWeapon.class, IsoGameCharacter.class);
        setParameterCharacterHitResult = find(cm, "setParameterCharacterHitResult", IsoGameCharacter.class,
            IsoZombie.class, long.class);
        try {
            objHit = cm.getDeclaredField("objHit");
            treeHit = cm.getDeclaredField("treeHit");
            hitOnlyTree = cm.getDeclaredField("hitOnlyTree");
            objHit.setAccessible(true);
            treeHit.setAccessible(true);
            hitOnlyTree.setAccessible(true);
            objectHitControl = true;
        } catch (NoSuchFieldException | SecurityException e) {
            objectHitControl = false;
            Log.error("CombatManager.objHit/treeHit/hitOnlyTree not found; ballistics cannot safely replace firearm hits", e);
        }
        targetedHits = processTargetedHit != null && calculateShotDirection != null;
        locationDamage = applyRangeHitLocationDamage != null;
        Log.info("Vanilla hit pipeline: targeted body parts " + (targetedHits ? "available" : "UNAVAILABLE (random vanilla body part)")
            + ", hit-location damage " + (locationDamage ? "available" : "UNAVAILABLE (fallback multipliers)"));
    }

    private static Method find(Class<?> c, String name, Class<?>... args) {
        try {
            Method m = c.getDeclaredMethod(name, args);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException | SecurityException e) {
            Log.warn("CombatManager." + name + " not found (" + e + ")");
            return null;
        }
    }

    /**
     * Clears vanilla's "adjacent object hit" state (doors/windows/trees right in front of the shooter that
     * vanilla damages on every attack). Used when CheckObjectHit is skipped for a ballistic shot: the
     * projectile itself decides what it hits, and a stale objHit would make vanilla report an extra hit.
     */
    public static boolean clearObjectHitState() {
        if (!objectHitControl) return false;
        try {
            CombatManager cm = CombatManager.getInstance();
            objHit.set(cm, null);
            treeHit.set(cm, null);
            hitOnlyTree.setBoolean(cm, false);
            return true;
        } catch (IllegalAccessException | RuntimeException e) {
            Log.errorOnce("clearing CombatManager.objHit", e);
            return false;
        }
    }

    public static boolean processTargetedHit(HandWeapon w, IsoGameCharacter wielder, IsoGameCharacter target, int part) {
        if (!targetedHits) return false;
        try {
            if (setUsePhysicHitReaction != null) setUsePhysicHitReaction.invoke(CombatManager.getInstance(), w, target);
            Object dir = calculateShotDirection.invoke(CombatManager.getInstance(), wielder, target);
            processTargetedHit.invoke(CombatManager.getInstance(), w, wielder, target, RagdollBodyPart.values()[part], dir);
            return true;
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
            Log.errorOnce("CombatManager.processTargetedHit", e instanceof InvocationTargetException ite ? ite.getCause() : e);
            return false;
        }
    }

    /** Returns the damage after hit location and clothing, or NaN if the vanilla method is unavailable. */
    public static float applyRangeHitLocationDamage(IsoGameCharacter target, HandWeapon w, int part, float damage) {
        if (!locationDamage) return Float.NaN;
        try {
            return (Float) applyRangeHitLocationDamage.invoke(CombatManager.getInstance(), target, w, part, damage);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
            Log.errorOnce("CombatManager.applyRangeHitLocationDamage", e instanceof InvocationTargetException ite ? ite.getCause() : e);
            return Float.NaN;
        }
    }

    public static void setParameterCharacterHitResult(IsoGameCharacter owner, IsoZombie z, long sound) {
        if (setParameterCharacterHitResult == null || z == null) return;
        try {
            setParameterCharacterHitResult.invoke(CombatManager.getInstance(), owner, z, sound);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
            Log.errorOnce("CombatManager.setParameterCharacterHitResult", e instanceof InvocationTargetException ite ? ite.getCause() : e);
        }
    }

    private VanillaCombat() {}
}
