package t3gunworks.aiming;

import t3gunworks.BallisticsUnits;
import t3gunworks.Log;
import t3gunworks.V3;
import zombie.CombatManager;
import zombie.characters.IsoGameCharacter;
import zombie.core.skinnedmodel.model.ModelInstance;
import zombie.iso.Vector3;
import zombie.scripting.objects.ModelAttachment;
import zombie.scripting.objects.ModelAttachmentId;

/**
 * Where the round leaves the gun. Priority:
 *
 *  1. the weapon model's "muzzle" attachment (ModelInstance.getAttachmentWorldPosition on the
 *     character's primaryHandModel - the model Viewpoint renders, already posed by Viewpoint's arm
 *     pitch and by the ADS add-on). This is the same attachment vanilla B42's own BallisticsController
 *     uses, and it is present on vanilla, VFE and most Workshop guns.
 *  2. the right-hand prop bone (Bip01_Prop1) pushed forward by a typical barrel length
 *  3. a point in front of the chest at shoulder height (documented emergency fallback)
 *
 * The result is validated against the character position: a muzzle more than 1.6 m away, or outside
 * 0.2..2.3 m above the feet, is rejected and the next source is tried.
 */
public final class MuzzleResolver {
    public static final int SOURCE_ATTACHMENT = 1, SOURCE_HAND_BONE = 2, SOURCE_FALLBACK = 3;

    private final Vector3 pos = new Vector3(), dir = new Vector3();
    public int lastSource;

    /** Writes the muzzle into out (metric) and the barrel direction into barrel (unit or zero). */
    public void resolve(IsoGameCharacter c, double yaw, V3 out, V3 barrel) {
        barrel.set(0, 0, 0);
        double x = c.getX(), y = c.getY(), u0 = BallisticsUnits.levelsToMeters(c.getZ());
        try {
            ModelInstance mi = c.primaryHandModel;
            if (mi != null && mi.modelScript != null && mi.animPlayer != null) {
                ModelAttachment att = mi.modelScript.getAttachmentById(ModelAttachmentId.MUZZLE);
                if (att != null) {
                    mi.getAttachmentWorldPosition(att, pos, dir);
                    out.set(pos.x, pos.y, BallisticsUnits.levelsToMeters(pos.z));
                    if (plausible(out, x, y, u0)) {
                        barrel.set(dir.x, dir.y, BallisticsUnits.levelsToMeters(dir.z)).normalize();
                        lastSource = SOURCE_ATTACHMENT;
                        return;
                    }
                }
            }
        } catch (RuntimeException e) {
            Log.errorOnce("reading the muzzle attachment", e);
        }
        double fx = Math.cos(yaw), fy = Math.sin(yaw);
        try {
            CombatManager.getBoneWorldPos(c, "Bip01_Prop1", pos);
            out.set(pos.x + fx * 0.45, pos.y + fy * 0.45, BallisticsUnits.levelsToMeters(pos.z));
            // getBoneWorldPos returns the character position itself when the bone is missing
            boolean isCharacterOrigin = Math.abs(pos.x - x) < 1e-4 && Math.abs(pos.y - y) < 1e-4;
            if (!isCharacterOrigin && plausible(out, x, y, u0)) {
                lastSource = SOURCE_HAND_BONE;
                return;
            }
        } catch (RuntimeException e) {
            Log.errorOnce("reading the hand bone", e);
        }
        double rx = -fy, ry = fx; // right-hand side
        double h = c.isSneaking() ? 1.0 : 1.38;
        out.set(x + fx * 0.55 + rx * 0.12, y + fy * 0.55 + ry * 0.12, u0 + h);
        lastSource = SOURCE_FALLBACK;
        Log.warnOnce("muzzle-fallback", "no muzzle attachment or hand bone for the held weapon; using the chest fallback (shots still leave in front of the body)");
    }

    private static boolean plausible(V3 m, double x, double y, double u0) {
        if (!m.finite()) return false;
        double dx = m.x - x, dy = m.y - y, du = m.u - u0;
        return dx * dx + dy * dy < 1.6 * 1.6 && du > 0.2 && du < 2.3;
    }

    public static String sourceName(int s) {
        return switch (s) {
            case SOURCE_ATTACHMENT -> "muzzle attachment";
            case SOURCE_HAND_BONE -> "hand bone";
            case SOURCE_FALLBACK -> "chest fallback";
            default -> "?";
        };
    }
}
