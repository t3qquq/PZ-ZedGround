package t3zedballistics.network;

import java.util.ArrayDeque;
import java.util.ArrayList;

import t3zedballistics.Log;
import t3zedballistics.config.BallisticsConfig;
import t3zedballistics.projectile.ProjectilePool;
import t3zedballistics.projectile.ShotGroup;
import zombie.CombatManager;
import zombie.characters.IsoGameCharacter;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoUtils;
import zombie.network.GameClient;
import zombie.network.PacketTypes;
import zombie.network.fields.hit.Hit;
import zombie.network.packets.INetworkPacket;

/**
 * Client side of multiplayer.
 *
 * Project Zomboid's firearm networking is shooter-authoritative with server validation: the shooting
 * client resolves hits and sends PlayerHit* packets that the server validates (anti-cheat: distance,
 * ammo, fire rate, damage cap) and applies. True Ballistics keeps that pipeline - it sends the very same
 * packet vanilla's attackCollisionCheck sends (PacketType.PlayerHit, 42.22) - but only once the round
 * has actually landed:
 *
 *   - the instant "missed" packet vanilla would send at trigger time is suppressed (ClientPatches.SendPacket);
 *   - when every pellet of a shot has resolved, one PlayerHit packet is sent with one entry per target
 *     (or one miss entry);
 *   - shots are flushed strictly in firing order, one packet per trigger pull, so the server's per-shot
 *     accounting (one round per packet through OnWeaponSwingHitPoint) behaves exactly as for vanilla shots.
 *     (42.21 tagged packets with NetworkPlayerAI's shot ID; 42.22 removed shot IDs.)
 *
 * No per-frame projectile traffic exists: positions never go over the network. Other clients see the
 * shot (animation replay, muzzle flash, tracer to the real impact point) when its packet arrives.
 */
public final class BallisticsNetwork {
    private final ArrayDeque<ShotGroup> pending = new ArrayDeque<>();
    private long lastSendNanos, lastSentFireNanos;
    private final ArrayList<CombatManager.TargetHitList> lists = new ArrayList<>(ShotGroup.MAX_TARGETS);

    /** True once the server answered the handshake and runs the True Ballistics server component. */
    public static volatile boolean serverConfirmed;
    public static volatile String serverVersion = "";

    // statistics
    public long packetsSent, shotsSent;
    private long statWindowNanos, statWindowPackets;
    public double packetsPerSecond;

    /** True when hits must stay inside vanilla's anti-cheat range because the server is not confirmed. */
    public static boolean vanillaRangeOnly() {
        return GameClient.client && !serverConfirmed;
    }

    public void enqueue(ShotGroup g) { pending.addLast(g); }

    public int pendingCount() { return pending.size(); }

    /** Pending shots that will produce network packets (stress-test rounds excluded). */
    public int pendingRealShots() {
        int n = 0;
        for (ShotGroup g : pending) if (!g.test) n++;
        return n;
    }

    /** Sends every finished shot at the head of the queue (firing order). Main thread. */
    public void flush(ProjectilePool pool) {
        long now = System.nanoTime();
        while (!pending.isEmpty()) {
            ShotGroup g = pending.peekFirst();
            // A group is only sent (and freed) once every pellet has finished: rounds always end by
            // lifetime/distance in simulated time, so nothing can stay pending forever.
            if (!g.complete()) break;
            // Keep the original spacing between shots: the server's fire-rate check (AttackRateChecker)
            // counts packets on arrival, so outcomes must not arrive faster than the rounds were fired.
            if (lastSentFireNanos != 0L && GameClient.client && !g.test) {
                long spacing = (long) ((g.firedNanos - lastSentFireNanos) * 0.9);
                if (spacing > 0 && now - lastSendNanos < Math.min(spacing, 1_000_000_000L)) break;
            }
            pending.pollFirst();
            if (!g.test) {
                lastSendNanos = now;
                lastSentFireNanos = g.firedNanos;
            }
            try {
                finish(g);
            } catch (RuntimeException e) {
                Log.errorOnce("finishing a shot", e);
            }
            pool.freeGroup(g);
        }
        if (now - statWindowNanos > 1_000_000_000L) {
            packetsPerSecond = statWindowPackets * 1e9 / Math.max(1, now - statWindowNanos);
            statWindowPackets = 0;
            statWindowNanos = now;
        }
    }

    /** Drops everything (world unloaded / player gone). */
    public void clear(ProjectilePool pool) {
        while (!pending.isEmpty()) pool.freeGroup(pending.pollFirst());
    }

    private void finish(ShotGroup g) {
        if (g.test || g.shooter == null || g.weapon == null) return;
        // vanilla: condition loss once per attack that hit something
        if (g.conditionHit && !g.weapon.isMelee()) {
            g.weapon.checkSyncItemFields(g.weapon.damageCheck(0, 1.0F, false, true, g.shooter));
        }
        if (!GameClient.client || !g.shooter.isLocal()) return;

        // 42.22: one PlayerHit packet per attack, with one entry per target (CombatManager.TargetHitList) -
        // the same call vanilla's attackCollisionCheck ends with. Each entry copies the shooter's attack
        // state when it is built, including the critical-hit flag the server uses for the whole attack
        // (it takes it from the first entry), so that flag is set to the shot's own result for the send.
        lists.clear();
        boolean crit = false;
        if (g.targetCount == 0) {
            // vanilla miss: one empty hit without a target
            Hit h = new Hit();
            h.set(0.0F, 0.0F, 0.0F);
            ArrayList<Hit> miss = new ArrayList<>(1);
            miss.add(h);
            lists.add(new CombatManager.TargetHitList(null, miss));
        } else {
            double best = Double.MAX_VALUE;
            for (int i = 0; i < g.targetCount; i++) {
                lists.add(new CombatManager.TargetHitList(g.targets[i], new ArrayList<>(g.hits[i])));
                // Diagnostic: the server drops an entry whose zombie it no longer has ("PlayerHitZombie/PlayerHitPacket
                // is not consistent"), seen in the 11-27 run for targets this shot had already killed.
                if (g.targets[i] instanceof IsoGameCharacter dead && dead.isDead()) {
                    Log.info("net shot #" + g.seq + ": target " + dead.getClass().getSimpleName() + "#" + dead.getOnlineID()
                        + " is already dead when the packet is sent, " + String.format("%.0fms", (System.nanoTime() - g.firedNanos) / 1e6)
                        + " after firing (pending shots " + pending.size() + ")");
                }
                // the server judges the attack by its closest character target (PlayerHitPacket.processServer)
                if (g.targets[i] instanceof IsoGameCharacter c) {
                    double d = IsoUtils.DistanceTo(c.getX(), c.getY(), g.shooter.getX(), g.shooter.getY());
                    if (d < best) {
                        best = d;
                        crit = g.crit[i];
                    }
                }
            }
        }
        boolean wasCrit = g.shooter.isCriticalHit();
        // A "hit nothing" entry (PlayerHitSquarePacket) carries the shooter's attack target square, and the
        // server drops the entry when that square is missing ("PlayerHitSquare/PlayerHitPacket is not
        // consistent"). Vanilla sets it from the mouse pick when the attack starts, which Viewpoint aiming
        // never provides, so it is the shooter's own square for the send (always loaded on the server, and
        // distance 0 for the long-distance anti-cheat). Restored afterwards.
        IsoGridSquare wasTarget = g.shooter.getAttackTargetSquare();
        boolean setTarget = g.targetCount == 0;
        try {
            g.shooter.setCriticalHit(crit);
            if (setTarget && g.shooter.getCurrentSquare() != null) g.shooter.setAttackTargetSquare(g.shooter.getCurrentSquare());
            INetworkPacket.send(PacketTypes.PacketType.PlayerHit, g.shooter, g.weapon, lists, g.tracers);
            count(1);
            shotsSent++;
            if (BallisticsConfig.logNetwork()) {
                Log.info("net shot #" + g.seq + " targets=" + g.targetCount + " tracers=" + g.tracers.size()
                    + " delay=" + String.format("%.0fms", (System.nanoTime() - g.firedNanos) / 1e6));
            }
        } finally {
            g.shooter.setCriticalHit(wasCrit);
            if (setTarget) g.shooter.setAttackTargetSquare(wasTarget);
            lists.clear();
        }
    }

    private void count(int n) {
        packetsSent += n;
        statWindowPackets += n;
    }
}
