package t3gunworks.combat;

import t3gunworks.collision.HitVolumes;

/** Coarse hit location for logs/API. The exact part (RagdollBodyPart ordinal) is what vanilla receives. */
public enum HitLocation {
    HEAD, TORSO, LIMB, NONE;

    public static HitLocation of(int ragdollPart) {
        return switch (ragdollPart) {
            case HitVolumes.HEAD -> HEAD;
            case HitVolumes.SPINE, HitVolumes.PELVIS -> TORSO;
            case -1 -> NONE;
            default -> LIMB;
        };
    }

    public static String partName(int ragdollPart) {
        return switch (ragdollPart) {
            case HitVolumes.PELVIS -> "pelvis";
            case HitVolumes.SPINE -> "torso";
            case HitVolumes.HEAD -> "head";
            case HitVolumes.L_UPPER_LEG -> "left thigh";
            case HitVolumes.L_LOWER_LEG -> "left shin";
            case HitVolumes.R_UPPER_LEG -> "right thigh";
            case HitVolumes.R_LOWER_LEG -> "right shin";
            case HitVolumes.L_UPPER_ARM -> "left upper arm";
            case HitVolumes.L_LOWER_ARM -> "left forearm";
            case HitVolumes.R_UPPER_ARM -> "right upper arm";
            case HitVolumes.R_LOWER_ARM -> "right forearm";
            default -> "none";
        };
    }
}
