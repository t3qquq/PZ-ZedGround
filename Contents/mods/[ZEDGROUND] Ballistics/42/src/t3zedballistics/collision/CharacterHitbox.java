package t3zedballistics.collision;

import t3zedballistics.BallisticsUnits;
import t3zedballistics.Log;
import t3zedballistics.V3;
import zombie.characters.IsoGameCharacter;
import zombie.core.skinnedmodel.animation.AnimationPlayer;
import zombie.core.skinnedmodel.model.Model;
import zombie.iso.Vector3;

/**
 * Builds hit capsules from the character's animated skeleton (the same bones vanilla combat reads via
 * Model.boneToWorldCoords), so a round hits the head where the head actually is in the current
 * animation frame - crouching, lunging, crawling or lying. Falls back to fixed proportions when the
 * character has no ready animation player.
 *
 * Bone names verified against the game: they are the ones vanilla code references (Bip01_Head,
 * Bip01_HeadNub, Bip01_Neck, Bip01_Spine, Bip01_Spine1, Bip01_Pelvis, Bip01_[L|R]_Thigh/Calf/Foot,
 * Bip01_[L|R]_UpperArm/Forearm/Hand).
 */
public final class CharacterHitbox {
    private static final String[] BONES = {
        "Bip01_Head", "Bip01_HeadNub", "Bip01_Neck", "Bip01_Spine", "Bip01_Spine1", "Bip01_Pelvis",
        "Bip01_L_Thigh", "Bip01_L_Calf", "Bip01_L_Foot", "Bip01_R_Thigh", "Bip01_R_Calf", "Bip01_R_Foot",
        "Bip01_L_UpperArm", "Bip01_L_Forearm", "Bip01_L_Hand", "Bip01_R_UpperArm", "Bip01_R_Forearm", "Bip01_R_Hand"
    };
    private static final int HEAD = 0, HEADNUB = 1, NECK = 2, SPINE = 3, SPINE1 = 4, PELVIS = 5,
        LTHIGH = 6, LCALF = 7, LFOOT = 8, RTHIGH = 9, RCALF = 10, RFOOT = 11,
        LUARM = 12, LFARM = 13, LHAND = 14, RUARM = 15, RFARM = 16, RHAND = 17;

    private final V3[] p = new V3[BONES.length];
    private final boolean[] ok = new boolean[BONES.length];
    private final Vector3 tmp = new Vector3();
    private final V3 extra = new V3();

    public CharacterHitbox() {
        for (int i = 0; i < p.length; i++) p[i] = new V3();
    }

    /** Fills out with the character's current hit volumes. Never throws. */
    public void build(IsoGameCharacter c, HitVolumes out) {
        double x = c.getX(), y = c.getY(), u0 = BallisticsUnits.levelsToMeters(c.getZ());
        double fx = c.getForwardDirectionX(), fy = c.getForwardDirectionY();
        double fl = Math.hypot(fx, fy);
        if (fl > 1e-6) { fx /= fl; fy /= fl; } else { fx = 1; fy = 0; }

        if (c.isAnimal()) {
            out.animal(x, y, u0, fx, fy, 1.0);
            return;
        }
        try {
            if (bones(c, x, y, u0)) {
                fromBones(out, fx, fy);
                filter(c, out);
                return;
            }
        } catch (RuntimeException e) {
            Log.errorOnce("reading skeleton for hitboxes", e);
        }
        if (c.isProne() || c.isOnFloor()) out.lying(x, y, u0, fx, fy);
        else out.standing(x, y, u0, fx, fy);
        filter(c, out);
    }

    /** t3zedballistics.api.BodyPartFilter: parts an add-on says are missing are not hittable. */
    private static void filter(IsoGameCharacter c, HitVolumes out) {
        t3zedballistics.api.BodyPartFilter f = t3zedballistics.api.BallisticsAPI.getBodyPartFilter();
        if (f == null) return;
        try {
            out.removeMissing(f.missingBodyPartTypes(c));
        } catch (RuntimeException e) {
            Log.errorOnce("body part filter " + f.getClass().getName(), e);
        }
    }

    private boolean bones(IsoGameCharacter c, double x, double y, double u0) {
        if (!c.hasAnimationPlayer()) return false;
        AnimationPlayer ap = c.getAnimationPlayer();
        if (ap == null || !ap.isReady()) return false;
        for (int i = 0; i < BONES.length; i++) {
            int idx = ap.getSkinningBoneIndex(BONES[i], -1);
            ok[i] = idx >= 0;
            if (!ok[i]) continue;
            Model.boneToWorldCoords(c, idx, tmp);
            p[i].set(tmp.x, tmp.y, BallisticsUnits.levelsToMeters(tmp.z));
            if (!p[i].finite()) ok[i] = false;
        }
        if (!ok[HEAD] || !ok[PELVIS] || !ok[NECK]) return false;
        // sanity: skeleton must be where the character is
        double dx = p[HEAD].x - x, dy = p[HEAD].y - y, du = p[HEAD].u - u0;
        return dx * dx + dy * dy < 6.25 && du > -0.5 && du < 2.6;
    }

    private void fromBones(HitVolumes v, double fx, double fy) {
        v.clear();
        // head: from the head bone to the top of the skull, plus the neck
        if (ok[HEADNUB]) v.add(p[HEAD], p[HEADNUB], 0.11, HitVolumes.HEAD, HitVolumes.T_HEAD);
        else {
            extra.set(p[HEAD].x + (p[HEAD].x - p[NECK].x), p[HEAD].y + (p[HEAD].y - p[NECK].y),
                p[HEAD].u + (p[HEAD].u - p[NECK].u));
            v.add(p[HEAD], extra, 0.11, HitVolumes.HEAD, HitVolumes.T_HEAD);
        }
        v.add(p[NECK], p[HEAD], 0.06, HitVolumes.HEAD, HitVolumes.T_NECK);
        // torso and pelvis (the lowest third of the pelvis capsule is the groin)
        V3 chest = ok[SPINE1] ? p[SPINE1] : (ok[SPINE] ? p[SPINE] : p[PELVIS]);
        v.add(chest, p[NECK], 0.16, HitVolumes.SPINE, HitVolumes.T_TORSO_UPPER);
        V3 waist = ok[SPINE] ? p[SPINE] : chest;
        v.add(p[PELVIS].x, p[PELVIS].y, p[PELVIS].u - 0.06, waist.x, waist.y, waist.u, 0.16, HitVolumes.PELVIS,
            HitVolumes.T_GROIN, HitVolumes.T_TORSO_LOWER, 0.3);
        limb(v, LTHIGH, LCALF, 0.085, HitVolumes.L_UPPER_LEG, HitVolumes.T_UPPERLEG_L);
        limb(v, LCALF, LFOOT, 0.06, HitVolumes.L_LOWER_LEG, HitVolumes.T_LOWERLEG_L);
        limb(v, RTHIGH, RCALF, 0.085, HitVolumes.R_UPPER_LEG, HitVolumes.T_UPPERLEG_R);
        limb(v, RCALF, RFOOT, 0.06, HitVolumes.R_LOWER_LEG, HitVolumes.T_LOWERLEG_R);
        limb(v, LUARM, LFARM, 0.055, HitVolumes.L_UPPER_ARM, HitVolumes.T_UPPERARM_L);
        limb(v, LFARM, LHAND, 0.045, HitVolumes.L_LOWER_ARM, HitVolumes.T_FOREARM_L);
        limb(v, RUARM, RFARM, 0.055, HitVolumes.R_UPPER_ARM, HitVolumes.T_UPPERARM_R);
        limb(v, RFARM, RHAND, 0.045, HitVolumes.R_LOWER_ARM, HitVolumes.T_FOREARM_R);
        // feet: from the ankle (foot bone) forward along the facing, down to the ground
        foot(v, LFOOT, fx, fy, HitVolumes.L_LOWER_LEG, HitVolumes.T_FOOT_L);
        foot(v, RFOOT, fx, fy, HitVolumes.R_LOWER_LEG, HitVolumes.T_FOOT_R);
        // hands: from the wrist (hand bone) on along the forearm
        hand(v, LFARM, LHAND, HitVolumes.L_LOWER_ARM, HitVolumes.T_HAND_L);
        hand(v, RFARM, RHAND, HitVolumes.R_LOWER_ARM, HitVolumes.T_HAND_R);
    }

    private void limb(HitVolumes v, int from, int to, double r, int part, int type) {
        if (ok[from] && ok[to]) v.add(p[from], p[to], r, part, type);
    }

    private void foot(HitVolumes v, int ankle, double fx, double fy, int part, int type) {
        if (!ok[ankle]) return;
        V3 a = p[ankle];
        double toeU = a.u - 0.06; // toes sit a little lower than the ankle
        v.add(a.x, a.y, a.u, a.x + fx * 0.16, a.y + fy * 0.16, toeU, 0.055, part, type, type, 0);
    }

    private void hand(HitVolumes v, int forearm, int wrist, int part, int type) {
        if (!ok[forearm] || !ok[wrist]) return;
        V3 f = p[forearm], w = p[wrist];
        double dx = w.x - f.x, dy = w.y - f.y, du = w.u - f.u;
        double l = Math.sqrt(dx * dx + dy * dy + du * du);
        if (l < 1e-4) return;
        double k = 0.1 / l;
        v.add(w.x, w.y, w.u, w.x + dx * k, w.y + dy * k, w.u + du * k, 0.045, part, type, type, 0);
    }

    /** Height of the character's top above its feet, for broad-phase rejection (m). */
    public static double reach(IsoGameCharacter c) {
        return c.isAnimal() ? 1.6 : 2.1;
    }
}
