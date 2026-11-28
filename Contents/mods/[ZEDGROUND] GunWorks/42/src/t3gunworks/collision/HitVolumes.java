package t3gunworks.collision;

import t3gunworks.V3;

/**
 * A character's hit volumes as capsules in metric space, each tagged with a body part. Body part
 * numbers are the ordinals of zombie.core.physics.RagdollBodyPart, the enum vanilla's own targeted
 * ballistics hit path (CombatManager.processTargetedHit) takes, so a hit maps 1:1 onto vanilla.
 */
public final class HitVolumes {
    public static final int PELVIS = 0, SPINE = 1, HEAD = 2,
        L_UPPER_LEG = 3, L_LOWER_LEG = 4, R_UPPER_LEG = 5, R_LOWER_LEG = 6,
        L_UPPER_ARM = 7, L_LOWER_ARM = 8, R_UPPER_ARM = 9, R_LOWER_ARM = 10;
    public static final int MAX = 16;

    /**
     * Exact vanilla body part (zombie.characters.BodyDamage.BodyPartType ordinal) for player wounds.
     * RagdollBodyPart has no hands, feet, neck or groin, so each capsule also carries the BodyPartType it
     * stands for - two of them when it spans a joint (calf + foot, pelvis + groin), switching at a
     * fraction along the capsule.
     */
    public static final int T_HAND_L = 0, T_HAND_R = 1, T_FOREARM_L = 2, T_FOREARM_R = 3, T_UPPERARM_L = 4,
        T_UPPERARM_R = 5, T_TORSO_UPPER = 6, T_TORSO_LOWER = 7, T_HEAD = 8, T_NECK = 9, T_GROIN = 10,
        T_UPPERLEG_L = 11, T_UPPERLEG_R = 12, T_LOWERLEG_L = 13, T_LOWERLEG_R = 14, T_FOOT_L = 15, T_FOOT_R = 16;

    public final V3[] a = new V3[MAX], b = new V3[MAX];
    public final double[] r = new double[MAX];
    public final int[] part = new int[MAX];
    /** BodyPartType near end a / near end b, and where along a->b (0..1) the second one starts. */
    public final int[] typeA = new int[MAX], typeB = new int[MAX];
    public final double[] split = new double[MAX];
    public int count;

    private final double[] tmp = new double[2];
    private final V3 far = new V3();
    /** Index of the capsule the last successful test() entered, -1 if none. */
    public int lastIndex = -1;

    public HitVolumes() {
        for (int i = 0; i < MAX; i++) { a[i] = new V3(); b[i] = new V3(); }
    }

    public void clear() { count = 0; }

    /** Moves every volume by (dx, dy, du) metres (lag compensation, see CharacterHistory). */
    public void translate(double dx, double dy, double du) {
        if (dx == 0 && dy == 0 && du == 0) return;
        for (int i = 0; i < count; i++) {
            a[i].set(a[i].x + dx, a[i].y + dy, a[i].u + du);
            b[i].set(b[i].x + dx, b[i].y + dy, b[i].u + du);
        }
    }

    public void add(double ax, double ay, double au, double bx, double by, double bu, double radius, int bodyPart) {
        int t = defaultType(bodyPart);
        add(ax, ay, au, bx, by, bu, radius, bodyPart, t, t, 0);
    }

    public void add(double ax, double ay, double au, double bx, double by, double bu, double radius, int bodyPart,
                    int typeNearA, int typeNearB, double splitAt) {
        if (count >= MAX) return;
        a[count].set(ax, ay, au);
        b[count].set(bx, by, bu);
        r[count] = radius;
        part[count] = bodyPart;
        typeA[count] = typeNearA;
        typeB[count] = typeNearB;
        split[count] = splitAt;
        count++;
    }

    public void add(V3 p, V3 q, double radius, int bodyPart) { add(p.x, p.y, p.u, q.x, q.y, q.u, radius, bodyPart); }

    public void add(V3 p, V3 q, double radius, int bodyPart, int type) {
        add(p.x, p.y, p.u, q.x, q.y, q.u, radius, bodyPart, type, type, 0);
    }

    /**
     * Drops every capsule whose BodyPartType(s) are all in the missing mask (bit n = BodyPartType
     * ordinal n). Keeps capsule order. Used for t3gunworks.api.BodyPartFilter.
     */
    public void removeMissing(long missing) {
        if (missing == 0) return;
        int w = 0;
        for (int i = 0; i < count; i++) {
            boolean goneA = typeA[i] >= 0 && (missing & (1L << typeA[i])) != 0;
            boolean goneB = typeB[i] >= 0 && (missing & (1L << typeB[i])) != 0;
            if (goneA && goneB) continue;
            if (w != i) {
                a[w].set(a[i]); b[w].set(b[i]); r[w] = r[i]; part[w] = part[i];
                typeA[w] = goneA ? typeB[i] : typeA[i];
                typeB[w] = goneB ? typeA[i] : typeB[i];
                split[w] = split[i];
            } else if (goneA || goneB) {
                // one end of a split capsule is gone: the rest of it reports the remaining part
                if (goneA) typeA[w] = typeB[i]; else typeB[w] = typeA[i];
            }
            w++;
        }
        count = w;
    }

    /** BodyPartType a RagdollBodyPart stands for when nothing finer is known. */
    public static int defaultType(int ragdollPart) {
        return switch (ragdollPart) {
            case HEAD -> T_HEAD;
            case SPINE -> T_TORSO_UPPER;
            case PELVIS -> T_TORSO_LOWER;
            case L_UPPER_LEG -> T_UPPERLEG_L;
            case R_UPPER_LEG -> T_UPPERLEG_R;
            case L_LOWER_LEG -> T_LOWERLEG_L;
            case R_LOWER_LEG -> T_LOWERLEG_R;
            case L_UPPER_ARM -> T_UPPERARM_L;
            case R_UPPER_ARM -> T_UPPERARM_R;
            case L_LOWER_ARM -> T_FOREARM_L;
            case R_LOWER_ARM -> T_FOREARM_R;
            default -> T_TORSO_UPPER;
        };
    }

    /**
     * Earliest entry fraction of segment s0->s1 into any volume, or -1. The RagdollBodyPart is written to
     * partOut[0] and, when partOut has room, the exact BodyPartType to partOut[1].
     */
    public double test(V3 s0, V3 s1, int[] partOut) {
        double best = -1;
        int bestI = -1;
        for (int i = 0; i < count; i++) {
            double t = SegmentMath.capsuleEntry(s0, s1, a[i], b[i], r[i], tmp);
            if (t >= 0 && (best < 0 || t < best)) {
                best = t;
                bestI = i;
                partOut[0] = part[i];
            }
        }
        lastIndex = bestI;
        if (bestI >= 0 && partOut.length > 1) partOut[1] = typeAt(bestI, s0, s1, best);
        return best;
    }

    /**
     * Where a straight line entering capsule i at p along unit direction (dx,dy,du) leaves it again,
     * written to out. Returns false if the line does not cross the capsule (grazing hit).
     */
    public boolean exitPoint(int i, V3 p, double dx, double dy, double du, V3 out) {
        if (i < 0 || i >= count) return false;
        double reach = a[i].dist(b[i]) + 2.0 * r[i] + 0.05;
        far.set(p.x + dx * reach, p.y + dy * reach, p.u + du * reach);
        double t = SegmentMath.capsuleEntry(far, p, a[i], b[i], r[i], tmp);
        if (t < 0) return false;
        out.lerp(far, p, t);
        return out.dist(p) > 1e-3;
    }

    /** BodyPartType where segment s0->s1 enters capsule i at fraction t. */
    private int typeAt(int i, V3 s0, V3 s1, double t) {
        if (typeA[i] == typeB[i]) return typeA[i];
        double px = s0.x + (s1.x - s0.x) * t, py = s0.y + (s1.y - s0.y) * t, pu = s0.u + (s1.u - s0.u) * t;
        double ax = b[i].x - a[i].x, ay = b[i].y - a[i].y, au = b[i].u - a[i].u;
        double len2 = ax * ax + ay * ay + au * au;
        double s = len2 < 1e-12 ? 0 : ((px - a[i].x) * ax + (py - a[i].y) * ay + (pu - a[i].u) * au) / len2;
        return s >= split[i] ? typeB[i] : typeA[i];
    }

    /**
     * Upright fallback used when no animated skeleton is available (character not animated this frame).
     * Proportions of a 1.8 m adult; (x,y) is the character position, u0 the height of its feet.
     */
    public void standing(double x, double y, double u0, double fx, double fy) {
        clear();
        double rx = -fy * 0.12, ry = fx * 0.12; // to the character's side
        add(x, y, u0 + 1.55, x, y, u0 + 1.72, 0.12, HEAD);
        add(x, y, u0 + 1.05, x, y, u0 + 1.42, 0.19, SPINE);
        add(x, y, u0 + 0.88, x, y, u0 + 1.02, 0.18, PELVIS, T_GROIN, T_TORSO_LOWER, 0.35);
        add(x + rx, y + ry, u0 + 0.48, x + rx, y + ry, u0 + 0.86, 0.09, L_UPPER_LEG);
        add(x + rx, y + ry, u0 + 0.05, x + rx, y + ry, u0 + 0.47, 0.07, L_LOWER_LEG, T_FOOT_L, T_LOWERLEG_L, 0.2);
        add(x - rx, y - ry, u0 + 0.48, x - rx, y - ry, u0 + 0.86, 0.09, R_UPPER_LEG);
        add(x - rx, y - ry, u0 + 0.05, x - rx, y - ry, u0 + 0.47, 0.07, R_LOWER_LEG, T_FOOT_R, T_LOWERLEG_R, 0.2);
    }

    /** Lying fallback: a body along the facing direction, head first. */
    public void lying(double x, double y, double u0, double fx, double fy) {
        clear();
        add(x + fx * 0.65, y + fy * 0.65, u0 + 0.15, x + fx * 0.85, y + fy * 0.85, u0 + 0.15, 0.12, HEAD);
        add(x + fx * 0.05, y + fy * 0.05, u0 + 0.15, x + fx * 0.55, y + fy * 0.55, u0 + 0.15, 0.18, SPINE);
        add(x - fx * 0.15, y - fy * 0.15, u0 + 0.15, x + fx * 0.05, y + fy * 0.05, u0 + 0.15, 0.17, PELVIS);
        add(x - fx * 0.95, y - fy * 0.95, u0 + 0.10, x - fx * 0.15, y - fy * 0.15, u0 + 0.10, 0.12, L_UPPER_LEG);
    }

    /** Four-legged animal fallback: horizontal body plus a head in front. */
    public void animal(double x, double y, double u0, double fx, double fy, double size) {
        clear();
        double h = 0.55 * size, len = 0.55 * size;
        add(x - fx * len, y - fy * len, u0 + h, x + fx * len, y + fy * len, u0 + h, 0.25 * size, SPINE);
        add(x + fx * (len + 0.15), y + fy * (len + 0.15), u0 + h + 0.15 * size,
            x + fx * (len + 0.3), y + fy * (len + 0.3), u0 + h + 0.2 * size, 0.13 * size, HEAD);
    }
}
