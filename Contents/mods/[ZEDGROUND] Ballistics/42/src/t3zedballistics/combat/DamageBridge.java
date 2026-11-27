package t3zedballistics.combat;

import t3zedballistics.Log;
import t3zedballistics.config.BallisticsConfig;
import t3zedballistics.api.ImpactEvents;
import t3zedballistics.collision.ProjectileCollision;
import t3zedballistics.projectile.BallisticProjectile;
import t3zedballistics.projectile.ShotGroup;
import zombie.CombatManager;
import zombie.Lua.LuaEventManager;
import zombie.ai.states.ZombieGetUpState;
import zombie.ai.states.ZombieOnGroundState;
import zombie.audio.parameters.ParameterBulletHitSurface;
import zombie.characters.CharacterStat;
import zombie.characters.BodyDamage.BodyPartType;
import zombie.characters.HitReactionNetworkAI;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.combat.CombatConfigKey;
import zombie.core.random.Rand;
import zombie.inventory.types.HandWeapon;
import zombie.iso.Vector2;
import zombie.network.GameClient;
import zombie.network.GameServer;
import zombie.network.PacketTypes.PacketType;
import zombie.network.fields.hit.Hit;
import zombie.network.packets.INetworkPacket;
import zombie.vehicles.BaseVehicle;

/**
 * Applies the consequences of a projectile hit through Project Zomboid's own ranged-combat code.
 *
 * This is the per-target body of CombatManager.attackCollisionCheck's ranged branch (PZ 42.22), in the
 * same order, minus the parts the projectile has replaced:
 *   - no hit-chance roll (the projectile already decided whether it hit)
 *   - no target selection (the projectile says who and which body part)
 *   - damage is scaled by the round's remaining energy (sandbox DamageFalloff)
 * Everything else - damage range, crits, hit reactions, blood, clothing defence, head/limb multipliers,
 * zombie stagger/knockdown, sounds, XP, maintenance XP, network hit packets - is vanilla.
 */
public final class DamageBridge {
    public static final int ABSORBED = 0;   // stopped by the body, no damage (PvP rules, hit cap)
    public static final int DAMAGED = 1;

    /**
     * @param bodyPartType exact BodyPartType ordinal the round hit (feet, hands, neck...), -1 if unknown;
     *                     a player's wound lands there instead of on vanilla's random part
     * @param speedRatio current speed / muzzle speed (energy falloff)
     * @param pierced    true when this round already went through another body (vanilla piercing reduction)
     */
    public static int hitCharacter(ShotGroup g, IsoGameCharacter target, int bodyPart, int bodyPartType, double speedRatio, boolean pierced) {
        return hitCharacter(g, target, bodyPart, bodyPartType, speedRatio, pierced, null, null);
    }

    /**
     * Same as above, with the projectile and collision result so the impact can be reported through
     * t3zedballistics.api.ImpactEvents (1.3.0). p / res may be null (no event data beyond the basics).
     */
    public static int hitCharacter(ShotGroup g, IsoGameCharacter target, int bodyPart, int bodyPartType, double speedRatio,
                                   boolean pierced, BallisticProjectile p, ProjectileCollision.Result res) {
        IsoPlayer owner = g.shooter;
        HandWeapon weapon = g.weapon;
        if (owner == null || weapon == null || target == null) return ABSORBED;
        if (g.test) return ABSORBED;
        CombatManager cm = CombatManager.getInstance();

        if (!target.isAnimal() && !CombatManager.checkPVP(owner, target, false)) {
            if (BallisticsConfig.logVerbose()) Log.info("hit on " + target + " blocked by PvP rules");
            return ABSORBED;
        }
        int maxHits = Math.max(1, weapon.getMaxHitCount());
        if (g.characterHits >= maxHits) return ABSORBED; // vanilla never reports more hits per shot
        g.characterHits++;

        IsoZombie zombie = target instanceof IsoZombie z ? z : null;
        boolean head = bodyPart == t3zedballistics.collision.HitVolumes.HEAD;

        // --- damage, exactly vanilla's ranged roll, then the energy falloff --------------------------
        float min = weapon.getMinDamage(), max = weapon.getMaxDamage();
        if (pierced) {
            float red = cm.getCombatConfig().get(CombatConfigKey.PIERCING_BULLET_DAMAGE_REDUCTION);
            if (red > 0) { min /= red; max /= red; }
        }
        float damage = max > min ? Rand.Next(min, max) : min;
        double energyRatio = Math.min(1.0, speedRatio * speedRatio);
        damage *= (float) ((1.0 - BallisticsConfig.damageFalloff * (1.0 - energyRatio)) * g.profile.damageMultiplier);

        // --- hit sound on the target (vanilla) ---------------------------------------------------------
        long hitSound = 0L;
        if (owner.isLocalPlayer()) {
            try {
                String snd = weapon.getZombieHitSound();
                if (snd != null) {
                    hitSound = target.getEmitter().playSoundImpl(snd, null);
                    ParameterBulletHitSurface.Material mat = head
                        ? ParameterBulletHitSurface.Material.Flesh_Hollow : ParameterBulletHitSurface.Material.Flesh;
                    target.getEmitter().setParameterValueByName(hitSound, "BulletHitSurface", mat.label);
                    if (GameClient.client) {
                        INetworkPacket.send(PacketType.PlaySound, new Object[]{snd, (byte) 0, target, mat});
                    }
                }
            } catch (RuntimeException e) {
                Log.errorOnce("playing the bullet hit sound", e);
            }
        }

        // --- zombie state, as vanilla --------------------------------------------------------------------
        if (zombie != null) {
            if (zombie.isCurrentState(ZombieOnGroundState.instance())) zombie.setReanimateTimer(zombie.getReanimateTimer() + Rand.Next(10));
            if (zombie.isCurrentState(ZombieGetUpState.instance())) zombie.setReanimateTimer(Rand.Next(60) + 30);
        }
        float rangeDel = weapon.isRangeFalloff() ? 2.0f : 1.0f;
        owner.knockbackAttackMod = 1.0f;
        owner.getAttackVars().closeKill = false;
        target.setCloseKilled(false);
        owner.addWorldSoundUnlessInvisible(8, 8, false);
        if (Rand.Next(3) != 0) {
            if (Rand.Next(7) == 0) owner.addWorldSoundUnlessInvisible(16, 16, false);
        } else {
            owner.addWorldSoundUnlessInvisible(10, 10, false);
        }
        target.setHitFromAngle(lookDot(owner, target));
        if (zombie != null) {
            zombie.setHitFromBehind(owner.isBehind(zombie));
            zombie.setPlayerAttackPosition(zombie.testDotSide(owner));
            zombie.setHitHeadWhileOnFloor(0);
            zombie.setHitLegsWhileOnFloor(false);
        }

        // --- body part: the projectile's, through vanilla's targeted-hit path ---------------------------
        int part;
        boolean targeted = false;
        // Critical hit, per projectile hit. 42.21's processHit rolled it for every ranged hit; 42.22 rolls it once
        // per attack in pressedAttack, against vanilla's own closest target - which a projectile does not use -
        // and processHit no longer does, so it is rolled here for both paths.
        owner.setCriticalHit(Rand.Next(100) < owner.calculateCritChance(target));
        if (BallisticsConfig.hitLocations && bodyPart >= 0) {
            targeted = VanillaCombat.processTargetedHit(weapon, owner, target, bodyPart);
        }
        if (targeted) part = bodyPart;
        else part = cm.processHit(weapon, owner, target); // vanilla random location
        boolean headHit = part == t3zedballistics.collision.HitVolumes.HEAD;

        float split = VanillaCombat.applyRangeHitLocationDamage(target, weapon, part, damage);
        if (Float.isNaN(split)) {
            split = damage;
            if (headHit) split *= cm.getCombatConfig().get(CombatConfigKey.HEAD_HIT_DAMAGE_SPLIT_MODIFIER);
            else if (part >= t3zedballistics.collision.HitVolumes.L_UPPER_LEG) split *= cm.getCombatConfig().get(CombatConfigKey.LEG_HIT_DAMAGE_SPLIT_MODIFIER);
        }

        boolean helmet = false;
        // 42.22: helmets fall where the hit is authoritative (CombatManager.getHitDamage: !GameClient.client).
        // In multiplayer the server does it from the hit's head value (ServerHits.rolled keeps it).
        if (!GameClient.client && !GameServer.server) {
            helmet = target.helmetFall(headHit);
        }

        float hitDamage = 0f;
        ImpactEvents.Impact impact = null;
        if (target.getSquare() != null && owner.getSquare() != null) {
            target.setCloseKilled(false);
            if (owner.isLocalPlayer() || owner.isNpc()) {
                boolean exact = BallisticsConfig.hitLocations && bodyPartType >= 0 && target instanceof IsoPlayer;
                if (exact) TargetedWound.begin(target, bodyPartType);
                impact = ImpactEvents.hasListeners() ? describe(g, target, bodyPart, bodyPartType, speedRatio, pierced, p, res, split) : null;
                if (impact != null) ImpactEvents.applying(impact);
                try {
                    hitDamage = target.Hit(weapon, owner, split, false, rangeDel);
                } finally {
                    if (exact) TargetedWound.end();
                    if (impact != null) ImpactEvents.applied();
                }
                cm.applyMeleeEnduranceLoss(owner, target, weapon, hitDamage);
                VanillaCombat.setParameterCharacterHitResult(owner, zombie, hitSound);
            }
            if (!GameClient.client && !GameServer.server && (!weapon.isRangeFalloff() || !g.xpAwarded)) {
                LuaEventManager.triggerEvent("OnWeaponHitXp", owner, weapon, target, split, 1);
                if (weapon.isRangeFalloff()) g.xpAwarded = true;
            }
            if (owner.DistToSquared(target) < 2.0f && Math.abs(owner.getZ() - target.getZ()) < 0.5f) {
                owner.addBlood(null, false, false, false);
                if (GameClient.client) owner.syncVisuals();
            }
            g.conditionHit = true;
            cm.processMaintenanceCheck(owner, weapon, target);
            if (target.isDead()) {
                owner.getStats().remove(CharacterStat.STRESS, 0.02f);
            } else if (!(target instanceof IsoPlayer)) {
                cm.splash(target, weapon, owner);
            }
            HitReactionNetworkAI.calculateHitReactionFromWeapon(owner, target, weapon);
            if (GameClient.client) {
                // 42.22 hit entry (vanilla: CombatManager.addTargetHitCharacter). hitHead > 0 is vanilla's "head
                // was hit" value; the damage is what Hit() just returned on this client.
                Hit hit = new Hit();
                Vector2 dir = target.getHitDir();
                hit.set(target.getHitForce(), dir != null ? dir.x : 0f, dir != null ? dir.y : 0f,
                    new CombatManager.HitData((byte) (headHit ? 1 : 0), false, false, pierced, true), false, hitDamage);
                // Only when the server runs this jar: mark the hit as decided by a projectile (since 42.22 the
                // server would otherwise roll hit chance and damage for players and animals itself) and add the
                // exact part in the spare bits of the hitHead byte. The server clears those bits before vanilla
                // reads the packet; a vanilla server never gets them.
                if (t3zedballistics.network.BallisticsNetwork.serverConfirmed) {
                    TargetedWound.markBallistic(hit);
                    if (BallisticsConfig.hitLocations && target instanceof IsoPlayer) {
                        TargetedWound.encode(hit, bodyPartType);
                    } else if (zombie != null && bodyPartType >= 0 && ImpactEvents.zombiePartEncodingRequested()) {
                        TargetedWound.encode(hit, bodyPartType);
                    }
                }
                g.addHit(target, hit, owner.isCriticalHit(), helmet);
            }
        }
        if (impact != null) {
            impact.damageApplied = hitDamage;
            impact.killed = target.isDead();
            ImpactEvents.fire(impact, ImpactEvents.PHASE_APPLIED);
        }
        if (BallisticsConfig.logVerbose()) {
            Log.info("hit " + target.getClass().getSimpleName() + "#" + target.getID() + " part=" + HitLocation.partName(part)
                + (targeted ? "" : " (vanilla location)") + (bodyPartType >= 0 ? " wound=" + BodyPartType.FromIndex(bodyPartType) : "") + " dmg=" + split + " applied=" + hitDamage + " energy=" + String.format("%.2f", energyRatio));
        }
        return DAMAGED;
    }

    /** Fills the reused ImpactEvents object for a player round (only called when someone listens). */
    private static ImpactEvents.Impact describe(ShotGroup g, IsoGameCharacter target, int bodyPart, int bodyPartType,
                                                double speedRatio, boolean pierced, BallisticProjectile p,
                                                ProjectileCollision.Result res, float split) {
        ImpactEvents.Impact i = ImpactEvents.begin();
        i.shooter = g.shooter;
        i.target = target;
        i.weapon = g.weapon;
        i.shotSeq = g.seq;
        i.pelletCount = g.profile != null ? g.profile.pelletCount : 1;
        i.ragdollPart = bodyPart;
        i.bodyPartType = bodyPartType;
        i.speedRatio = speedRatio;
        i.pierced = pierced;
        i.damageRolled = split;
        if (g.profile != null) {
            i.massGrams = g.profile.massGrams;
            i.category = g.profile.category;
            i.penetrationBudget = g.profile.penetration;
            i.energyJoules = g.profile.energy(g.profile.muzzleVelocity * speedRatio);
        }
        if (p != null) {
            i.pellet = p.pellet;
            i.speed = p.speed();
            i.muzzleSpeed = p.muzzleVelocity;
            double l = p.velocity.length();
            if (l > 1e-9) { i.dirX = p.velocity.x / l; i.dirY = p.velocity.y / l; i.dirU = p.velocity.u / l; }
            i.penetrates = p.penetrationLeft > 0 && speedRatio * speedRatio > t3zedballistics.projectile.BallisticsManager.PENETRATION_ENERGY_RATIO;
            i.hitX = p.position.x; i.hitY = p.position.y; i.hitU = p.position.u;
        }
        if (res != null) {
            i.hitX = res.point.x; i.hitY = res.point.y; i.hitU = res.point.u;
            if (res.hasCapsuleExit) {
                i.hasCapsuleExit = true;
                i.capsuleExitX = res.capsuleExit.x; i.capsuleExitY = res.capsuleExit.y; i.capsuleExitU = res.capsuleExit.u;
            }
        }
        return i;
    }

    /** Vehicle hit through BaseVehicle.processHit (vanilla part damage). */
    public static void hitVehicle(ShotGroup g, BaseVehicle v, double speedRatio) {
        if (g.test || g.shooter == null || g.weapon == null || v == null) return;
        HandWeapon weapon = g.weapon;
        if (g.characterHits >= Math.max(1, weapon.getMaxHitCount())) return; // vanilla counts vehicles in maxHit
        g.characterHits++;
        float min = weapon.getMinDamage(), max = weapon.getMaxDamage();
        float damage = max > min ? Rand.Next(min, max) : min;
        double energyRatio = Math.min(1.0, speedRatio * speedRatio);
        damage *= (float) ((1.0 - BallisticsConfig.damageFalloff * (1.0 - energyRatio)) * g.profile.damageMultiplier);
        if (v.processHit(g.shooter, weapon, damage)) {
            g.conditionHit = true;
            CombatManager.getInstance().processMaintenanceCheck(g.shooter, weapon, v);
            if (GameClient.client) {
                // vanilla: CombatManager.addTargetHitObject. Since 42.22 the server computes vehicle damage
                // itself; the marker makes it accept the hit without rolling vanilla's hit chance again.
                Hit hit = new Hit();
                hit.set(v.getHitForce(), v.getHitDir().x, v.getHitDir().y);
                if (t3zedballistics.network.BallisticsNetwork.serverConfirmed) TargetedWound.markBallistic(hit);
                g.addHit(v, hit, false, false);
            }
        }
    }

    /** Vanilla HitInfo.dot: how directly the shooter faces the target. */
    private static float lookDot(IsoGameCharacter owner, IsoGameCharacter target) {
        float lx = owner.getForwardDirectionX(), ly = owner.getForwardDirectionY();
        float dx = target.getX() - owner.getX(), dy = target.getY() - owner.getY();
        float l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l < 1e-4f) return 1f;
        return (lx * dx + ly * dy) / l;
    }

    private DamageBridge() {}
}
