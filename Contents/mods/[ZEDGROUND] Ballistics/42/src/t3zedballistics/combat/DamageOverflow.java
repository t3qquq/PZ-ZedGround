package t3zedballistics.combat;

import java.util.List;

import t3zedballistics.Log;
import t3zedballistics.config.BallisticsConfig;
import zombie.characters.BodyDamage.BodyDamage;
import zombie.characters.BodyDamage.BodyPart;
import zombie.characters.BodyDamage.BodyPartType;
import zombie.characters.IsoGameCharacter;
import zombie.network.GameServer;

/**
 * Bullet damage that a body part cannot take any more still counts.
 *
 * Vanilla (42.22.2) keeps a 0..100 health per body part and computes the overall health as
 *   100 - sum((100 - partHealth) * damageModifier(part))
 * with a part's health clamped at 0 (BodyPart.ReduceHealth). So one part can only ever take
 * 100 * damageModifier off the overall health (upper torso 35, head 60, neck 70) and every further round in
 * an emptied part does nothing but bleed. With exact hit locations (TargetedWound) a player shot in the same
 * place again and again stops losing health.
 *
 * Around BodyDamage.applyDamageFromWeapon(part, damage, type, pain), for bullet wounds (type 6) only:
 *   overflow  - the damage the part could not take (damage - partHealth before the hit), times the part's
 *               share, is taken off the overall health
 *   head share - the head counts with HeadDamageShare instead of vanilla's 0.6 (both for the damage the head
 *               took and for its overflow)
 * The extra loss is spread over the other parts that still have health, so the overall health drops by exactly
 * that amount (vanilla recomputes it from the parts). Then the damage is synced to clients again.
 *
 * Runs wherever vanilla applies the wound: the server in multiplayer (IsoPlayer.Hit skips it on clients for a
 * player attacker), the game itself in single player.
 */
public final class DamageOverflow {
    private static final int BULLET = 6;
    private static final ThreadLocal<Pending> PENDING = ThreadLocal.withInitial(Pending::new);

    private static final class Pending {
        boolean active;
        int part;
        float damage;
        float healthBefore;
        float overallBefore;
    }

    private DamageOverflow() {}

    /** applyDamageFromWeapon entry: remembers the part's health before vanilla changes it. */
    public static void enter(Object bodyDamage, int partIndex, float damage, int damageType) {
        Pending p = PENDING.get();
        p.active = false;
        if (damageType != BULLET || !(bodyDamage instanceof BodyDamage bd)) return;
        boolean changes = BallisticsConfig.overflowDamage || share(partIndex, true) > share(partIndex, false);
        if (!changes && !BallisticsConfig.logVerbose()) return;
        BodyPart part = partOf(bd, partIndex);
        if (part == null || !(damage > 0f)) return;
        p.active = true;
        p.part = partIndex;
        p.damage = damage;
        p.healthBefore = part.getHealth();
        p.overallBefore = bd.getOverallBodyHealth();
    }

    /** applyDamageFromWeapon exit: takes the extra loss off the other parts and syncs it. */
    public static void exit(Object bodyDamage) {
        Pending p = PENDING.get();
        if (!p.active) return;
        p.active = false;
        if (!(bodyDamage instanceof BodyDamage bd)) return;

        float vanillaShare = share(p.part, false);
        float realShare = share(p.part, true);
        float absorbed = Math.min(p.damage, Math.max(0f, p.healthBefore));
        float overflow = Math.max(0f, p.damage - Math.max(0f, p.healthBefore));
        float extra = absorbed * Math.max(0f, realShare - vanillaShare);
        if (BallisticsConfig.overflowDamage) extra += overflow * realShare;
        if (!(extra > 0.0001f)) {
            logWound(bd, p, vanillaShare, realShare, overflow, 0f, 0f);
            return;
        }

        float applied = spread(bd, p.part, extra);
        bd.calculateOverallHealth();
        if (GameServer.server) {
            IsoGameCharacter c = bd.getParentChar();
            if (c != null && c.getNetworkCharacterAI() != null) c.getNetworkCharacterAI().syncDamage();
        }
        logWound(bd, p, vanillaShare, realShare, overflow, extra, applied);
    }

    /**
     * Lowers the health of the other parts so that the overall health drops by amount (as far as they still
     * have health). Equal share per part, refilled from the rest while some parts run out.
     */
    static float spread(BodyDamage bd, int hitPart, float amount) {
        List<BodyPart> parts = bd.getBodyParts();
        int count = Math.min(parts.size(), BodyPartType.ToIndex(BodyPartType.MAX));
        float remaining = amount;
        for (int round = 0; round < count && remaining > 0.0001f; round++) {
            int open = 0;
            for (int i = 0; i < count; i++) if (i != hitPart && parts.get(i).getHealth() > 0f) open++;
            if (open == 0) break;
            float each = remaining / open;
            for (int i = 0; i < count; i++) {
                if (i == hitPart) continue;
                BodyPart bp = parts.get(i);
                float h = bp.getHealth();
                if (h <= 0f) continue;
                float mod = BodyPartType.getDamageModifyer(i);
                if (mod <= 0f) continue;
                float take = Math.min(each, h * mod);
                bp.SetHealth(Math.max(0f, h - take / mod));
                remaining -= take;
            }
        }
        return amount - Math.max(0f, remaining);
    }

    /** How much of a part's lost health counts against the overall health. */
    static float share(int partIndex, boolean withSettings) {
        if (withSettings && partIndex == BodyPartType.ToIndex(BodyPartType.Head)) return (float) BallisticsConfig.headDamageShare;
        return BodyPartType.getDamageModifyer(partIndex);
    }

    private static BodyPart partOf(BodyDamage bd, int partIndex) {
        List<BodyPart> parts = bd.getBodyParts();
        return partIndex >= 0 && partIndex < parts.size() ? parts.get(partIndex) : null;
    }

    private static void logWound(BodyDamage bd, Pending p, float vanillaShare, float realShare, float overflow, float extra, float applied) {
        if (!BallisticsConfig.logVerbose()) return;
        BodyPart part = partOf(bd, p.part);
        Log.info(String.format("wound: %s part=%s damage=%.1f partHealth %.1f->%.1f overflow=%.1f share=%.2f (vanilla %.2f) extra=%.1f (applied %.1f) overall %.1f->%.1f",
            bd.getParentChar(), BodyPartType.ToString(BodyPartType.FromIndex(p.part)), p.damage, p.healthBefore,
            part == null ? -1f : part.getHealth(), overflow, realShare, vanillaShare, extra, applied,
            p.overallBefore, bd.getOverallBodyHealth()));
    }
}
