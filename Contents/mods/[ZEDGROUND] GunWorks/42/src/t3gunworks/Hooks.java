package t3gunworks;

import t3gunworks.aiming.ViewpointBridge;
import t3gunworks.debug.BallisticsDebug;
import t3gunworks.debug.DebugDraw;
import t3gunworks.network.ServerValidator;
import t3gunworks.projectile.BallisticsManager;
import zombie.inventory.types.HandWeapon;
import zombie.network.GameServer;
import zombie.network.PacketTypes;
import zombie.network.packets.hit.PlayerHit;

/**
 * The only methods the ZombieBuddy advice (ClientPatches, ServerPatches, ViewpointPatches) calls. Every entry point is wrapped so that no
 * exception from this mod can ever propagate into Project Zomboid's code.
 */
public final class Hooks {
    private static final BallisticsManager M = BallisticsManager.INSTANCE;

    // per-attack self-test: proves at runtime that the hooks that keep vanilla from double-hitting work
    private static boolean discardedThisAttack, missSuppressedThisAttack, inAttack;
    public static volatile boolean disabled;
    public static volatile String disabledReason = "";
    /** Set the first time ZombieBuddy's advice actually runs; proves the patches were applied. */
    public static volatile boolean patchesLive;

    // ---------------------------------------------------------------- firing (client)

    public static void attackEnter(Object owner, Object weapon) {
        inAttack = false;
        if (disabled) return;
        try {
            inAttack = M.beginAttack(owner, weapon);
            discardedThisAttack = false;
            missSuppressedThisAttack = false;
        } catch (Throwable t) {
            Log.errorOnce("attackEnter", t);
        }
    }

    public static void hitListBuilt(Object owner) {
        if (!inAttack) return;
        try {
            if (M.attackActiveFor(owner)) {
                M.discardVanillaTargets(owner);
                discardedThisAttack = true;
            }
        } catch (Throwable t) {
            Log.errorOnce("hitListBuilt", t);
        }
    }

    /**
     * CombatManager.CheckObjectHit: vanilla damages a door/window/tree directly in front of the shooter on
     * every attack. For ballistic shots the projectile decides what it hits, so this is skipped (and the
     * object-hit state cleared so nothing stale is reported).
     */
    public static boolean skipAdjacentObjectHit() {
        try {
            if (inAttack && M.attackActive()) return t3gunworks.combat.VanillaCombat.clearObjectHitState();
            return false;
        } catch (Throwable t) {
            Log.errorOnce("skipAdjacentObjectHit", t);
            return false;
        }
    }

    /** CombatManager.addTracerEffect: no vanilla tracer/impact for a ballistic shot (the projectile makes its own). */
    public static boolean skipVanillaTracer() {
        try {
            return inAttack && M.attackActive();
        } catch (Throwable t) {
            Log.errorOnce("skipVanillaTracer", t);
            return false;
        }
    }

    /**
     * INetworkPacket.send (every overload): drop the PlayerHit packet vanilla sends at the end of
     * attackCollisionCheck for a ballistic shot. With vanilla's targets discarded it only says "hit nothing";
     * the real result is sent when the round lands (BallisticsNetwork). a0 / a1 are the first two arguments
     * of whichever overload was called - the packet type is one of them. Runs for every packet the game
     * sends, so the common case returns on the first test.
     */
    public static boolean skipInstantMissPacket(Object a0, Object a1) {
        if (!inAttack) return false;
        try {
            if ((a0 == PacketTypes.PacketType.PlayerHit || a1 == PacketTypes.PacketType.PlayerHit) && M.attackActive()) {
                missSuppressedThisAttack = true;
                return true;
            }
            return false;
        } catch (Throwable t) {
            Log.errorOnce("skipInstantMissPacket", t);
            return false;
        }
    }

    public static void attackExit(Object owner) {
        if (!inAttack) return;
        inAttack = false;
        try {
            if (!discardedThisAttack) {
                // Vanilla already resolved this attack: spawning a projectile would double-hit.
                disable("the CombatManager.calculateHitInfoList hook did not run");
                M.endAttack(null); // clears the attack state without spawning
                return;
            }
            if (zombie.network.GameClient.client && !missSuppressedThisAttack) {
                disable("the INetworkPacket.send hook did not run (vanilla would also report the shot)");
                M.endAttack(null);
                return;
            }
            M.endAttack(owner);
        } catch (Throwable t) {
            Log.errorOnce("attackExit", t);
        }
    }

    private static void disable(String why) {
        if (!disabled) {
            disabled = true;
            disabledReason = why;
            Log.error("True Ballistics switched itself off for this session: " + why + ". Vanilla firearm combat is used.", null);
        }
    }

    // ---------------------------------------------------------------- per frame (client)

    public static void combatUpdate() {
        if (!patchesLive) {
            patchesLive = true;
            Log.info("ZombieBuddy patches are live: firearm shots are simulated while Viewpoint aiming is active.");
        }
        try {
            if (!GameServer.server) M.tick();
        } catch (Throwable t) {
            Log.errorOnce("combatUpdate", t);
        }
    }

    // ---------------------------------------------------------------- exact wound locations (players)

    public static int woundPart(Object bodyDamage, int part) {
        try {
            return t3gunworks.combat.TargetedWound.resolve(bodyDamage, part);
        } catch (Throwable t) {
            Log.errorOnce("woundPart", t);
            return part;
        }
    }

    // ---------------------------------------------------------------- bullet damage overflow (players)

    public static void overflowEnter(Object bodyDamage, int part, float damage, int damageType) {
        try {
            t3gunworks.combat.DamageOverflow.enter(bodyDamage, part, damage, damageType);
        } catch (Throwable t) {
            Log.errorOnce("overflowEnter", t);
        }
    }

    public static void overflowExit(Object bodyDamage) {
        try {
            t3gunworks.combat.DamageOverflow.exit(bodyDamage);
        } catch (Throwable t) {
            Log.errorOnce("overflowExit", t);
        }
    }

    // ---------------------------------------------------------------- hit packets (server, 42.22)

    public static void hitPacketEnter(Object playerHitPacket) {
        try {
            t3gunworks.network.ServerHits.packetEnter(playerHitPacket);
        } catch (Throwable t) {
            Log.errorOnce("hitPacketEnter", t);
        }
    }

    public static void hitPacketExit() {
        try {
            t3gunworks.network.ServerHits.packetExit();
        } catch (Throwable t) {
            Log.errorOnce("hitPacketExit", t);
        }
    }

    /** CombatManager.isHitRolledByClient exit: the shooter's verdict stands for every target of a projectile hit. */
    public static boolean hitRolledByClient(boolean vanilla) {
        return vanilla || t3gunworks.network.ServerHits.ballisticPacket();
    }

    /** CombatManager.getHitData entry: no second hit-chance roll for a hit a projectile already decided. */
    public static float hitChance(float vanilla) {
        return t3gunworks.network.ServerHits.ballisticPacket() ? 1000.0f : vanilla;
    }

    public static void hitRolled(Object hit, boolean kept) {
        try {
            t3gunworks.network.ServerHits.rolled(hit, kept);
        } catch (Throwable t) {
            Log.errorOnce("hitRolled", t);
        }
    }

    public static void hitProcessEnter(Object hit, Object target) {
        try {
            t3gunworks.network.ServerHits.processEnter(hit, target);
        } catch (Throwable t) {
            Log.errorOnce("hitProcessEnter", t);
        }
    }

    public static void hitProcessExit() {
        try {
            if (t3gunworks.network.ServerHits.ballisticPacket()) t3gunworks.combat.TargetedWound.end();
        } catch (Throwable t) {
            Log.errorOnce("hitProcessExit", t);
        }
    }

    // ---------------------------------------------------------------- Viewpoint

    public static void viewpointSnapshot(Object frame, Object second, Object third) {
        try {
            // Viewpoint 0.1.6: (Frame, IsoGameCharacter, Lift); 0.1.5a: (Frame, IsoCell, IsoGameCharacter)
            Object character = second instanceof zombie.characters.IsoGameCharacter ? second : third;
            ViewpointBridge.captureCamera(frame, character);
            if (character == zombie.characters.IsoPlayer.players[0]) {
                try {
                    t3gunworks.collision.CharacterHistory.noteSnapshot(System.nanoTime(), (zombie.characters.IsoGameCharacter) character);
                } catch (RuntimeException e) {
                    Log.errorOnce("recording character positions", e);
                }
                Viewpoint.captureEffects(frame);
            }
            if (BallisticsDebug.enabled() || M.activeCount() > 0 || t3gunworks.npc.NpcBallistics.INSTANCE.activeCount() > 0
                    || BallisticsDebug.hasSnapshots()) {
                Viewpoint.captureDebug(frame, M);
            }
        } catch (Throwable t) {
            Log.errorOnce("viewpointSnapshot", t);
        }
    }

    /**
     * viewpoint.gunplay.Shot.begin (Viewpoint 0.1.6 Gunplay Rework): true skips Viewpoint's instant shot
     * resolution because this trigger pull is simulated as a projectile. Side-effect free; decided with the
     * same rules as attackEnter, which may run before or after Viewpoint's own attack hook.
     */
    public static boolean skipViewpointShot(Object owner, Object weapon) {
        if (disabled) return false;
        try {
            return M.wouldSimulate(owner, weapon);
        } catch (Throwable t) {
            Log.errorOnce("skipViewpointShot", t);
            return false;
        }
    }

    /** viewpoint.gunplay.Gunplay.active exit: true = True Ballistics owns this aim, Viewpoint gunplay stands down. */
    public static boolean overrideGunplay(Object owner, Object weapon) {
        try {
            return t3gunworks.aiming.CrosshairOverride.ownsAim(owner, weapon);
        } catch (Throwable t) {
            Log.errorOnce("overrideGunplay", t);
            return false;
        }
    }

    /** viewpoint.gunplay.Gunplay.snapshot exit (main thread, per frame). */
    public static void gunplayFrame() {
        try {
            t3gunworks.aiming.CrosshairOverride.frame();
        } catch (Throwable t) {
            Log.errorOnce("gunplayFrame", t);
        }
    }

    /** viewpoint.gunplay.Gunplay.reticle exit: the aim ring shows True Ballistics' dispersion. */
    public static boolean gunplayReticle(Object reticle, boolean skip) {
        try {
            return t3gunworks.aiming.CrosshairOverride.reticle(reticle, skip);
        } catch (Throwable t) {
            Log.errorOnce("gunplayReticle", t);
            return skip;
        }
    }

    public static void viewpointDraw(Object scene) {
        try {
            t3gunworks.effects.ImpactDraw.draw(scene);
        } catch (Throwable t) {
            Log.errorOnce("impactDraw", t);
        }
        try {
            DebugDraw.draw(scene);
        } catch (Throwable t) {
            Log.errorOnce("viewpointDraw", t);
        }
    }

    // ---------------------------------------------------------------- server

    public static boolean widenWeaponDistance(Object weapon, float distance, boolean exceeded) {
        if (!exceeded) return false;
        try {
            if (!GameServer.server || !(weapon instanceof HandWeapon w) || !w.isAimedFirearm()) return true;
            return distance > ServerValidator.allowedDistance(w);
        } catch (Throwable t) {
            Log.errorOnce("widenWeaponDistance", t);
            return exceeded;
        }
    }

    public static String widenLongDistance(Object packet, String result) {
        if (result == null || !result.startsWith("distance=")) return result;
        try {
            if (!GameServer.server || !(packet instanceof PlayerHit p)) return result;
            HandWeapon w = p.getHandWeapon();
            if (w == null || !w.isAimedFirearm()) return result;
            float d = ((zombie.network.anticheats.AntiCheatHitLongDistance.IAntiCheat) packet).getDistance();
            return d <= ServerValidator.allowedDistance(w) ? null : result;
        } catch (Throwable t) {
            Log.errorOnce("widenLongDistance", t);
            return result;
        }
    }

    public static boolean validateHit(Object packet, boolean consistent) {
        if (!consistent) return false;
        try {
            return ServerValidator.validate(packet);
        } catch (Throwable t) {
            Log.errorOnce("validateHit", t);
            return true;
        }
    }

    /** Viewpoint frame field access for the debug snapshot (kept separate so it loads lazily). */
    static final class Viewpoint {
        private static java.lang.reflect.Field fScene, fCamX, fCamY, fCamZ;

        static void captureEffects(Object frame) {
            try {
                fields(frame);
                t3gunworks.effects.ImpactEffects.INSTANCE.frame(fScene.get(frame), fCamX.getFloat(frame), fCamY.getFloat(frame), fCamZ.getFloat(frame));
            } catch (ReflectiveOperationException | RuntimeException e) {
                Log.errorOnce("impact effects frame", e);
            }
        }

        private static void fields(Object frame) throws ReflectiveOperationException {
            if (fScene == null) {
                Class<?> c = frame.getClass();
                fScene = c.getField("scene");
                fCamX = c.getField("camX");
                fCamY = c.getField("camY");
                fCamZ = c.getField("camZ");
            }
        }

        static void captureDebug(Object frame, BallisticsManager m) throws ReflectiveOperationException {
            fields(frame);
            BallisticsDebug.capture(fScene.get(frame), fCamX.getFloat(frame), fCamY.getFloat(frame), fCamZ.getFloat(frame),
                m.activeProjectiles(), t3gunworks.npc.NpcBallistics.INSTANCE.tracerRounds());
        }
    }

    private Hooks() {}
}
