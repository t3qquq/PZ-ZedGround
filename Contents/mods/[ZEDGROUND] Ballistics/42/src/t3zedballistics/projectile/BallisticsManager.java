package t3zedballistics.projectile;

import t3zedballistics.collision.CharacterHistory;

import java.util.ArrayList;

import org.joml.Vector3f;

import t3zedballistics.BallisticsUnits;
import t3zedballistics.Log;
import t3zedballistics.V3;
import t3zedballistics.aiming.AimProvider;
import t3zedballistics.aiming.AimSolution;
import t3zedballistics.aiming.MuzzleResolver;
import t3zedballistics.aiming.Spread;
import t3zedballistics.aiming.ViewpointBridge;
import t3zedballistics.collision.ProjectileCollision;
import t3zedballistics.collision.WorldCollision;
import t3zedballistics.combat.DamageBridge;
import t3zedballistics.combat.HitLocation;
import t3zedballistics.config.BallisticsConfig;
import t3zedballistics.debug.BallisticsDebug;
import t3zedballistics.network.BallisticsNetwork;
import t3zedballistics.weapon.BallisticProfile;
import t3zedballistics.weapon.WeaponBallisticsResolver;
import zombie.CombatManager;
import zombie.GameTime;
import zombie.SoundManager;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.inventory.types.HandWeapon;
import zombie.iso.IsoGridSquare;
import zombie.iso.objects.IsoBulletTracerEffects;
import zombie.iso.objects.IsoWindow;
import zombie.network.GameClient;
import zombie.network.GameServer;
import zombie.network.fields.hit.TracerInfo;

/**
 * Owns every live round. Responsibilities: decide whether an attack is ballistic, spawn the rounds when
 * the gun fires, integrate and sweep them every frame, hand impacts to DamageBridge, and pass finished
 * shots to the network layer. Main thread only (all entry points are called from game-thread hooks).
 */
public final class BallisticsManager {
    public static final BallisticsManager INSTANCE = new BallisticsManager();

    private static final int MAX_SUBSTEPS = 32;
    /** A round with penetration left passes through a body while it keeps more than this share of its energy. */
    public static final double PENETRATION_ENERGY_RATIO = 0.35;
    private static final int MAX_ACTIVE = 4096;

    private final ProjectilePool pool = new ProjectilePool();
    private final ArrayList<BallisticProjectile> active = new ArrayList<>(256);
    private final ProjectileCollision collision = new ProjectileCollision();
    private final V3 impactDir = new V3();
    /** Metres after a fly-on object hit (t3zedballistics.api.ObjectHits) in which the next touch is ignored. */
    static final double OBJECT_FLY_ON_SKIP = 0.05;
    private final ProjectileCollision.Result res = new ProjectileCollision.Result();
    private final AimProvider aim = new AimProvider();
    private final AimSolution sol = new AimSolution();
    private final ShotRandom rnd = new ShotRandom();
    public final BallisticsNetwork net = new BallisticsNetwork();
    private final V3 dir = new V3(), tmp = new V3();
    private final double[] cone = new double[2];
    private final Vector3f hitLoc = new Vector3f();

    private long shotSeq;
    private long lastTickNanos;
    private long lastConfigNanos;

    // ---- current attack (between attackCollisionCheck enter and exit) ----
    private IsoPlayer attackOwner;
    private HandWeapon attackWeapon;
    private boolean attackActive;

    // ---- statistics ----
    public long shotsFired, roundsSpawned, impactsWorld, impactsCharacter, impactsVehicle, expired;
    public double lastUpdateMs, maxUpdateMs, lastCollisionMs;
    private double collisionNanosAcc;

    private BallisticsManager() {}

    // =================================================================================== attack hooks

    /** attackCollisionCheck entry. Returns true if this attack is taken over by the projectile system. */
    public boolean beginAttack(Object ownerObj, Object weaponObj) {
        attackActive = false;
        attackOwner = null;
        attackWeapon = null;
        if (!wouldSimulate(ownerObj, weaponObj)) return false;
        attackOwner = (IsoPlayer) ownerObj;
        attackWeapon = (HandWeapon) weaponObj;
        attackActive = true;
        return true;
    }

    /** Whether a trigger pull by owner with weapon is simulated as projectiles (no side effects). */
    public boolean wouldSimulate(Object ownerObj, Object weaponObj) {
        try {
            if (!BallisticsConfig.enabled || GameServer.server || !t3zedballistics.combat.VanillaCombat.objectHitControl) return false;
            if (!(ownerObj instanceof IsoPlayer p) || !(weaponObj instanceof HandWeapon w)) return false;
            if (!p.isLocalPlayer() || !w.isAimedFirearm()) return false;
            if (p.isRangedWeaponEmpty() || p.isDoShove() || p.isPerformingShoveAnimation()) return false;
            if (p.getVehicle() != null) return false;
            boolean viewpoint = ViewpointBridge.controls(p);
            return viewpoint || BallisticsConfig.useInIsoView;
        } catch (RuntimeException e) {
            Log.errorOnce("deciding whether an attack is ballistic (vanilla firing used)", e);
            return false;
        }
    }

    public boolean attackActiveFor(Object owner) { return attackActive && owner == attackOwner; }

    public boolean attackActive() { return attackActive; }

    /** calculateHitInfoList exit: vanilla's instantly selected targets are discarded. */
    public void discardVanillaTargets(Object owner) {
        if (!attackActiveFor(owner)) return;
        try {
            ((IsoGameCharacter) owner).clearHitInfo();
        } catch (RuntimeException e) {
            Log.errorOnce("clearing vanilla targets", e);
        }
    }

    /** attackCollisionCheck exit (also on exceptions). Spawns the rounds for this trigger pull. */
    public void endAttack(Object owner) {
        if (!attackActiveFor(owner)) {
            attackActive = false;
            attackOwner = null;
            attackWeapon = null;
            return;
        }
        IsoPlayer p = attackOwner;
        HandWeapon w = attackWeapon;
        attackActive = false;
        attackOwner = null;
        attackWeapon = null;
        try {
            spawnShot(p, w, false);
        } catch (RuntimeException e) {
            Log.errorOnce("spawning a projectile", e);
        }
    }

    // =================================================================================== spawning

    /** Fires one trigger pull from p with w. test == true makes inert stress-test rounds. */
    public ShotGroup spawnShot(IsoPlayer p, HandWeapon w, boolean test) {
        BallisticProfile prof = WeaponBallisticsResolver.resolve(w);
        double maxDist = WeaponBallisticsResolver.maxDistance(w, prof, BallisticsNetwork.vanillaRangeOnly());
        double v0 = prof.muzzleVelocity * BallisticsConfig.velocityScale;
        double g = BallisticsConfig.GRAVITY * BallisticsConfig.gravityScale * prof.gravityScale;
        double k = prof.drag * BallisticsConfig.dragScale;
        ShotGroup grp = pool.obtainGroup();
        grp.seq = ++shotSeq;
        grp.shooter = p;
        grp.weapon = w;
        grp.profile = prof;
        grp.firedNanos = System.nanoTime();
        grp.test = test;
        grp.seenNanos = test ? 0 : CharacterHistory.seenFor(p, grp.firedNanos);
        // The group is always queued: even a shot that fails below must still be reported (as a miss),
        // or the server would never account for the round that vanilla already fired.
        net.enqueue(grp);
        shotsFired++;
        try {
            launch(p, w, prof, grp, maxDist, v0, g, k);
        } catch (RuntimeException e) {
            Log.errorOnce("launching a projectile (shot reported as a miss)", e);
        }
        return grp;
    }

    private void launch(IsoPlayer p, HandWeapon w, BallisticProfile prof, ShotGroup grp, double maxDist,
                        double v0, double g, double k) {
        boolean test = grp.test;
        collision.world.bind();
        boolean solved;
        collision.rewindTo = grp.seenNanos; // aim at the bodies as they were on screen
        try {
            solved = aim.solve(p, w, prof, maxDist, v0, g, k, collision, sol);
        } finally {
            collision.rewindTo = 0;
        }
        if (!solved) {
            Log.warnOnce("aim-invalid", "could not build an aim solution; shot reported as a miss");
            return;
        }

        int every = BallisticsConfig.tracerEvery;
        grp.tracer = prof.tracer || (every > 0 && grp.seq % every == 0);

        rnd.seed(p.getOnlineID() & 0xffffL, grp.seq);
        // shooter dispersion: one deviation per trigger pull, shared by every pellet
        dir.set(sol.direction);
        ProjectilePhysics.deflect(dir, rnd.gaussian() * sol.sigma, rnd.gaussian() * sol.sigma);

        int pellets = prof.pelletCount;
        double coneRad = Math.toRadians(prof.pelletSpreadDeg);
        double lifetime = prof.lifetime > 0 ? prof.lifetime : BallisticsConfig.projectileLifetime;
        for (int i = 0; i < pellets && active.size() < MAX_ACTIVE; i++) {
            BallisticProjectile pr = pool.obtain();
            tmp.set(dir);
            if (pellets > 1) {
                rnd.cone(coneRad, cone);
                ProjectilePhysics.deflect(tmp, cone[0], cone[1]);
            }
            pr.position.set(sol.muzzle);
            pr.previousPosition.set(sol.muzzle);
            pr.origin.set(sol.muzzle);
            pr.velocity.set(tmp.x * v0, tmp.y * v0, tmp.u * v0);
            pr.muzzleVelocity = v0;
            pr.gravity = g;
            pr.drag = k;
            pr.massGrams = prof.massGrams;
            pr.maxLifetime = lifetime;
            pr.maxDistance = maxDist;
            pr.penetrationLeft = BallisticsConfig.penetration ? prof.penetration : 0;
            pr.tracer = grp.tracer;
            pr.pellet = i;
            pr.shotSeq = grp.seq;
            pr.addIgnore(p);
            pr.group = grp;
            grp.pelletsAlive++;
            roundsSpawned++;
            if (sol.muzzleBlocked) {
                // Near-wall rule: the barrel is behind an obstacle, the round hits it right away.
                pr.position.set(sol.blockPoint);
                worldImpact(pr, sol.blockPoint, WorldCollision.HIT_WALL,
                    (int) Math.floor(sol.blockPoint.x), (int) Math.floor(sol.blockPoint.y), BallisticsUnits.levelOf(sol.blockPoint.u), null);
                finish(pr);
                pool.free(pr);
            } else {
                if (BallisticsDebug.enabled()) pr.recordTrail();
                active.add(pr);
            }
        }
        if (!test) Spread.addBloom(prof);

        if (BallisticsDebug.enabled()) {
            BallisticsDebug.line(sol.camera, sol.aimPoint, 0.2f, 0.9f, 1f, 0.8f);          // camera ray
            BallisticsDebug.point(sol.muzzle, 0.08, 1f, 1f, 1f);                           // muzzle
            if (sol.barrel.length() > 0.5) {
                tmp.set(sol.muzzle.x + sol.barrel.x * 0.6, sol.muzzle.y + sol.barrel.y * 0.6, sol.muzzle.u + sol.barrel.u * 0.6);
                BallisticsDebug.line(sol.muzzle, tmp, 0.2f, 1f, 0.2f, 1f);                  // barrel forward
            }
            if (sol.muzzleBlocked) BallisticsDebug.point(sol.blockPoint, 0.25, 1f, 0.5f, 0f);
        }
        if (BallisticsConfig.logVerbose()) {
            Log.info(String.format("Shot #%d weapon=%s %s v0=%.0fm/s g=%.2f drag=%.4f pellets=%d sigma=%.2fmrad zero=%.2fmrad "
                    + "origin=%s(%s) aim=%s%s dir=%s range=%.0fm%s",
                grp.seq, w.getFullType(), prof.category, v0, g, k, pellets, sol.sigma * 1000, sol.zeroAngle * 1000,
                sol.muzzle, MuzzleResolver.sourceName(sol.muzzleSource), sol.aimPoint, sol.aimHit ? "" : " (max range)",
                dir, maxDist, sol.viewpoint ? (sol.thirdPerson ? " TPS" : (sol.adsSight ? " ADS(zero " + Math.round(sol.zeroDistance) + "m)" : " FPS")) : " iso"));
        }
    }

    // =================================================================================== simulation

    /** Per-frame update, from the CombatManager.update advice (main thread). */
    public void tick() {
        long now = System.nanoTime();
        if (now - lastConfigNanos > 1_000_000_000L) {
            lastConfigNanos = now;
            BallisticsConfig.refreshSandbox();
            t3zedballistics.api.BallisticsAPI.reapplyZero();
        }
        if (lastTickNanos == 0L || GameTime.isGamePaused()) {
            lastTickNanos = now;
            return;
        }
        double dt = Math.min(0.25, (now - lastTickNanos) / 1e9);
        lastTickNanos = now;

        collision.world.bind();
        recordHistory(now);
        collisionNanosAcc = 0;
        for (int i = active.size() - 1; i >= 0; i--) {
            BallisticProjectile p = active.get(i);
            try {
                simulate(p, dt);
            } catch (RuntimeException e) {
                Log.errorOnce("projectile update (round removed)", e);
                finish(p); // counts the pellet down and marks it dead
            }
            if (!p.alive) {
                int last = active.size() - 1;
                active.set(i, active.get(last));
                active.remove(last);
                pool.free(p);
            }
        }
        net.flush(pool);
        BallisticsDebug.tick(isViewpointFrame());
        if (BallisticsConfig.debugHitboxes) drawHitboxes();

        double ms = (System.nanoTime() - now) / 1e6;
        lastUpdateMs = ms;
        lastCollisionMs = collisionNanosAcc / 1e6;
        if (ms > maxUpdateMs) maxUpdateMs = ms;
    }

    /**
     * Lag compensation: the time at which this round meets the bodies - when the shot was seen plus the time
     * the round would really have needed to get here (its age times the velocity scale). 0 = the present.
     */
    private static long rewindTime(BallisticProjectile p, long now) {
        long seen = p.group == null ? 0 : p.group.seenNanos;
        if (seen <= 0) return 0;
        double real = p.age * Math.min(1.0, BallisticsConfig.velocityScale);
        long at = seen + (long) (real * 1e9);
        return at < now ? at : 0;
    }

    private void recordHistory(long now) {
        if (!GameServer.server) CharacterHistory.wanted(now); // forgets the history once Viewpoint stops drawing
    }

    private final t3zedballistics.collision.CharacterHitbox debugHitbox = new t3zedballistics.collision.CharacterHitbox();
    private final t3zedballistics.collision.HitVolumes debugVolumes = new t3zedballistics.collision.HitVolumes();
    private final V3 debugA = new V3(), debugB = new V3();

    /**
     * ZGB_debug("hitboxes", true): the hit capsules of characters within 30 m, redrawn every tick (magenta,
     * head red), so they can be compared with the drawn models. Each capsule is its axis plus four lines
     * along its surface.
     */
    private void drawHitboxes() {
        IsoPlayer me = IsoPlayer.players[0];
        if (me == null || zombie.iso.IsoWorld.instance == null || zombie.iso.IsoWorld.instance.getCell() == null) return;
        int drawn = 0;
        try {
            for (zombie.iso.IsoMovingObject o : zombie.iso.IsoWorld.instance.getCell().getObjectList()) {
                if (!(o instanceof IsoGameCharacter c) || c == me || c.isDead()) continue;
                double dx = c.getX() - me.getX(), dy = c.getY() - me.getY();
                if (dx * dx + dy * dy > 900) continue;
                debugHitbox.build(c, debugVolumes);
                for (int i = 0; i < debugVolumes.count; i++) {
                    V3 a = debugVolumes.a[i], b = debugVolumes.b[i];
                    double r = debugVolumes.r[i];
                    boolean head = debugVolumes.part[i] == t3zedballistics.collision.HitVolumes.HEAD;
                    float red = 1f, green = 0.2f, blue = head ? 0.2f : 1f; // magenta body, red head
                    BallisticsDebug.line(a.x, a.y, a.u, b.x, b.y, b.u, red, green, blue, 1f, 0.01);
                    // two directions across the axis
                    double ax = b.x - a.x, ay = b.y - a.y, au = b.u - a.u;
                    double l = Math.sqrt(ax * ax + ay * ay + au * au);
                    if (l < 1e-6) { ax = 0; ay = 0; au = 1; } else { ax /= l; ay /= l; au /= l; }
                    // p = axis x up (or x when vertical), q = axis x p
                    double px = ay * 1 - au * 0, py = au * 0 - ax * 1, pu = 0;
                    double pl = Math.sqrt(px * px + py * py + pu * pu);
                    if (pl < 1e-6) { px = 1; py = 0; pu = 0; pl = 1; }
                    px /= pl; py /= pl; pu /= pl;
                    double qx = ay * pu - au * py, qy = au * px - ax * pu, qu = ax * py - ay * px;
                    for (int k = 0; k < 4; k++) {
                        double sx = (k == 0 ? px : k == 1 ? -px : k == 2 ? qx : -qx) * r;
                        double sy = (k == 0 ? py : k == 1 ? -py : k == 2 ? qy : -qy) * r;
                        double su = (k == 0 ? pu : k == 1 ? -pu : k == 2 ? qu : -qu) * r;
                        BallisticsDebug.line(a.x + sx, a.y + sy, a.u + su, b.x + sx, b.y + sy, b.u + su,
                            red, green, blue, 0.6f, 0.01);
                    }
                }
                if (++drawn >= 24) break;
            }
        } catch (RuntimeException e) {
            Log.errorOnce("drawing hitboxes", e);
        }
    }

    private static boolean isViewpointFrame() {
        IsoPlayer p = IsoPlayer.players[0];
        return p != null && ViewpointBridge.controls(p);
    }

    private void simulate(BallisticProjectile p, double dt) {
        double remaining = dt;
        int steps = 0;
        double objectSkip = 0; // after a round flew on through another mod's object: ignore touches right at the start
        while (remaining > 1e-9 && p.alive && steps++ < MAX_SUBSTEPS) {
            double speed = Math.max(1.0, p.speed());
            double h = Math.min(remaining, BallisticsConfig.MAX_SEGMENT / speed);
            ProjectilePhysics.step(p.position, p.velocity, p.previousPosition, h, p.gravity, p.drag);
            p.age += h;
            remaining -= h;

            double seg = p.previousPosition.dist(p.position);
            boolean rangeEnd = false;
            if (p.traveled + seg >= p.maxDistance && seg > 1e-9) {
                double f = (p.maxDistance - p.traveled) / seg;
                p.position.lerp(p.previousPosition, p.position, Math.max(0, f));
                seg = p.previousPosition.dist(p.position);
                rangeEnd = true;
            }

            long c0 = System.nanoTime();
            collision.rewindTo = rewindTime(p, c0);
            boolean hit;
            try {
                hit = collision.sweep(p.previousPosition, p.position, p.ignore, p.ignoreCount, !p.group.test, true, true, res);
            } finally {
                collision.rewindTo = 0;
            }
            collisionNanosAcc += System.nanoTime() - c0;
            double tStop = hit ? res.t : 1.0;

            // Other mods' objects in the way (t3zedballistics.api.ObjectHits): physics props and the like
            if (!p.group.test && t3zedballistics.api.ObjectHits.active()) {
                double tObj = t3zedballistics.api.ObjectHits.sweep(p.previousPosition.x, p.previousPosition.y, p.previousPosition.u,
                    p.position.x, p.position.y, p.position.u);
                double skip = objectSkip;
                objectSkip = 0;
                // A furniture hit is only the tile's box (inset 0.15 m, up to 0.9/1.9 m); when a registered object
                // is touched inside that same tile, the object is what the round really meets.
                boolean insideBox = hit && res.kind == ProjectileCollision.WORLD && res.world.kind == WorldCollision.HIT_FURNITURE
                    && tObj >= 0 && sameTile(p.previousPosition, p.position, tObj, res.world.tileX, res.world.tileY);
                if (tObj >= 0 && (tObj < tStop || insideBox) && tObj * seg >= skip) {
                    for (int i = 0; i < res.windows.count; i++) {
                        if (res.windows.t[i] >= tObj) break;
                        breakWindow(p, res.windows.window[i]);
                    }
                    p.traveled += seg * tObj;
                    p.position.lerp(p.previousPosition, p.position, tObj);
                    if (objectHit(p)) return;
                    // flew on: the next piece starts on the object's surface, so the same object is not hit again
                    objectSkip = OBJECT_FLY_ON_SKIP;
                    continue;
                }
            }

            for (int i = 0; i < res.windows.count; i++) {
                if (res.windows.t[i] >= tStop) break;
                breakWindow(p, res.windows.window[i]);
            }

            if (hit) {
                p.traveled += seg * tStop;
                p.position.set(res.point);
                if (BallisticsDebug.enabled()) p.recordTrail();
                impact(p);
            } else {
                p.traveled += seg;
                if (BallisticsDebug.enabled() && steps % 2 == 0) p.recordTrail();
            }
            if (!p.alive) return;

            if (rangeEnd || p.traveled >= p.maxDistance || p.age >= p.maxLifetime || p.speed() < Math.min(BallisticsConfig.MIN_SPEED, 0.25 * p.muzzleVelocity)) {
                expire(p);
                return;
            }
        }
    }

    private void impact(BallisticProjectile p) {
        switch (res.kind) {
            case ProjectileCollision.CHARACTER -> {
                impactsCharacter++;
                IsoGameCharacter c = res.character;
                double ratio = p.speed() / Math.max(1.0, p.muzzleVelocity);
                boolean pierced = p.ignoreCount > 1;
                int r = DamageBridge.hitCharacter(p.group, c, res.bodyPart, res.bodyPartType, ratio, pierced, p, res);
                tracer(p, null);
                debugImpact(p, 1f, 0.15f, 0.15f);
                if (BallisticsConfig.logVerbose() || BallisticsConfig.debugImpacts) {
                    Log.info(String.format("Shot #%d pellet %d hit %s#%d %s at %.1fm after %.3fs, %.0fm/s %.0fJ%s",
                        p.shotSeq, p.pellet, c.getClass().getSimpleName(), c.getID(), HitLocation.of(res.bodyPart),
                        p.origin.dist(res.point), p.age, p.speed(), p.energy(), r == DamageBridge.ABSORBED ? " (no damage)" : ""));
                }
                if (r == DamageBridge.DAMAGED && p.penetrationLeft > 0 && ratio * ratio > PENETRATION_ENERGY_RATIO) {
                    // Through the body: keep flying with reduced speed, never hit this body again.
                    p.penetrationLeft--;
                    p.addIgnore(c);
                    p.velocity.set(p.velocity.x * 0.6, p.velocity.y * 0.6, p.velocity.u * 0.6);
                    return;
                }
                finish(p);
            }
            case ProjectileCollision.VEHICLE -> {
                impactsVehicle++;
                DamageBridge.hitVehicle(p.group, res.vehicle, p.speed() / Math.max(1.0, p.muzzleVelocity));
                tracer(p, null);
                debugImpact(p, 1f, 0.5f, 0.1f);
                finish(p);
            }
            default -> {
                worldImpact(p, res.point, res.world.kind, res.world.tileX, res.world.tileY, res.world.tileZ, res.world);
                finish(p);
            }
        }
    }

    private void worldImpact(BallisticProjectile p, V3 point, int kind, int tx, int ty, int tz, WorldCollision.Hit hit) {
        impactsWorld++;
        IsoGridSquare sq = collision.world.square(tx, ty, tz);
        tracer(p, sq);
        debugImpact(p, 1f, 0.15f, 0.15f);
        if (BallisticsConfig.logVerbose() || BallisticsConfig.debugImpacts) {
            Log.info(String.format("Shot #%d pellet %d hit world (%s) at %s, %.1fm after %.3fs",
                p.shotSeq, p.pellet, worldKind(kind), point, p.origin.dist(point), p.age));
        }
        ShotGroup g = p.group;
        if (g.test || g.shooter == null || sq == null || GameServer.server) return;

        // surface normal and wall edge (a blocked barrel has no trace: face the round, wall on the main axis)
        double vl = Math.max(1e-9, p.velocity.length());
        impactDir.set(p.velocity.x / vl, p.velocity.y / vl, p.velocity.u / vl);
        double nx, ny, nu;
        int edx, edy;
        if (hit != null && (hit.nx != 0 || hit.ny != 0 || hit.nu != 0)) {
            nx = hit.nx; ny = hit.ny; nu = hit.nu; edx = hit.edgeDx; edy = hit.edgeDy;
        } else if (Math.abs(impactDir.x) >= Math.abs(impactDir.y)) {
            edx = impactDir.x >= 0 ? 1 : -1; edy = 0; nx = -edx; ny = 0; nu = 0;
        } else {
            edx = 0; edy = impactDir.y >= 0 ? 1 : -1; nx = 0; ny = -edy; nu = 0;
        }
        IsoGridSquare far = (edx != 0 || edy != 0) ? collision.world.square(tx + edx, ty + edy, tz) : null;
        int material = t3zedballistics.effects.ImpactMaterial.resolve(kind, sq, far, edx, edy);
        double ratio = p.speed() / Math.max(1.0, p.muzzleVelocity);
        try {
            t3zedballistics.effects.ImpactEffects.INSTANCE.impact(point, nx, ny, nu, impactDir, kind, tx, ty, tz, edx, edy, material,
                g.profile, ratio * ratio, GameClient.client);
        } catch (RuntimeException e) {
            Log.errorOnce("bullet hole / impact debris", e);
        }
        try {
            // Same impact feedback as vanilla's tracer effect: shoot placed items, else the impact sound.
            // In multiplayer vanilla's placed-item shooting sends its own hit packet immediately (outside
            // the ordered shot queue), so it is single player only. (42.22: third argument = local shooter.)
            boolean handled = false;
            if (!GameClient.client) {
                IsoBulletTracerEffects.Effect effect = IsoBulletTracerEffects.getInstance().addEffect(g.shooter,
                    (float) p.origin.dist(point), (float) point.x, (float) point.y, (float) BallisticsUnits.metersToLevels(point.u), null);
                hitLoc.set((float) point.x, (float) point.y, (float) BallisticsUnits.metersToLevels(point.u));
                handled = effect != null && CombatManager.getInstance().hitIsoGridSquare(sq, hitLoc, true);
            }
            if (!handled) {
                // the sound of the material actually hit (vanilla uses the square's merged property)
                zombie.iso.enums.MaterialType mt = null;
                if (material > 0) {
                    try { mt = zombie.iso.enums.MaterialType.valueOf(t3zedballistics.effects.ImpactMaterial.NAMES[material]); }
                    catch (IllegalArgumentException ignored) { mt = null; }
                }
                if (mt != null) SoundManager.instance.playImpactSound(sq, g.weapon.getAmmoType(), mt);
                else SoundManager.instance.playImpactSound(sq, g.weapon.getAmmoType());
            }
        } catch (RuntimeException e) {
            Log.errorOnce("world impact effect", e);
        }
    }

    private void breakWindow(BallisticProjectile p, Object w) {
        if (!(w instanceof IsoWindow win) || p.group.test) return;
        try {
            if (!win.isDestroyed()) {
                win.addBrokenGlass(p.group.shooter);
                win.smashWindow();
            }
            p.velocity.set(p.velocity.x * BallisticsConfig.WINDOW_VELOCITY_KEEP, p.velocity.y * BallisticsConfig.WINDOW_VELOCITY_KEEP,
                p.velocity.u * BallisticsConfig.WINDOW_VELOCITY_KEEP);
            if (BallisticsDebug.enabled()) BallisticsDebug.point(p.position, 0.3, 0.5f, 0.8f, 1f);
        } catch (RuntimeException e) {
            Log.errorOnce("breaking a window", e);
        }
    }

    /** Multiplayer tracer for other clients (vanilla TracerInfo inside the hit packet). */
    private void tracer(BallisticProjectile p, IsoGridSquare sq) {
        if (!GameClient.client || p.group.test) return;
        TracerInfo t = new TracerInfo();
        t.set((float) p.origin.dist(p.position), (float) p.position.x, (float) p.position.y,
            (float) BallisticsUnits.metersToLevels(p.position.u), sq);
        p.group.addTracer(t);
    }

    private void expire(BallisticProjectile p) {
        expired++;
        tracer(p, null);
        if (BallisticsConfig.logVerbose()) {
            Log.info(String.format("Shot #%d pellet %d spent at %s after %.1fm / %.3fs (%.0fm/s)",
                p.shotSeq, p.pellet, p.position, p.traveled, p.age, p.speed()));
        }
        finish(p);
    }

    private static boolean sameTile(V3 a, V3 b, double t, int tx, int ty) {
        double x = a.x + (b.x - a.x) * t, y = a.y + (b.y - a.y) * t;
        return (int) Math.floor(x) == tx && (int) Math.floor(y) == ty;
    }

    /** The round reached another mod's object (t3zedballistics.api.ObjectHits). True when it stopped there. */
    private boolean objectHit(BallisticProjectile p) {
        impactsWorld++;
        double vl = Math.max(1e-9, p.velocity.length());
        boolean stop = t3zedballistics.api.ObjectHits.hit(p.position.x, p.position.y, p.position.u, p.velocity.x / vl, p.velocity.y / vl,
            p.velocity.u / vl, vl, p.massGrams, p.group.shooter);
        if (BallisticsDebug.enabled()) p.recordTrail();
        debugImpact(p, 0.3f, 1f, 0.3f);
        if (BallisticsConfig.logVerbose() || BallisticsConfig.debugImpacts) {
            Log.info(String.format("Shot #%d pellet %d hit an object at %s, %.1fm after %.3fs, %.0fm/s%s",
                p.shotSeq, p.pellet, p.position, p.origin.dist(p.position), p.age, vl, stop ? "" : " (flies on)"));
        }
        if (!stop) return false;
        IsoGridSquare sq = collision.world.square((int) Math.floor(p.position.x), (int) Math.floor(p.position.y),
            (int) Math.floor(BallisticsUnits.metersToLevels(p.position.u) + 1e-6));
        tracer(p, sq);
        int tx = (int) Math.floor(p.position.x), ty = (int) Math.floor(p.position.y);
        int tz = (int) Math.floor(BallisticsUnits.metersToLevels(p.position.u) + 1e-6);
        int material = sq != null ? t3zedballistics.effects.ImpactMaterial.resolve(WorldCollision.HIT_FURNITURE, sq, null, 0, 0) : 0;
        if (!GameServer.server && !p.group.test) {
            try {
                // furniture a mod models precisely (claimed tile, it does not move): a hole facing the round + debris;
                // a loose prop that may fly off: debris only
                impactDir.set(p.velocity.x / vl, p.velocity.y / vl, p.velocity.u / vl);
                double ratio = vl / Math.max(1.0, p.muzzleVelocity);
                if (t3zedballistics.api.ObjectHits.covers(tx, ty, tz)) {
                    t3zedballistics.effects.ImpactEffects.INSTANCE.impact(p.position, -impactDir.x, -impactDir.y, -impactDir.u, impactDir,
                        WorldCollision.HIT_FURNITURE, tx, ty, tz, 0, 0, material, p.group.profile, ratio * ratio, GameClient.client);
                } else {
                    t3zedballistics.effects.ImpactEffects.INSTANCE.debrisOnly(p.position, impactDir, tx, ty, tz, material, p.group.profile, ratio * ratio);
                }
            } catch (RuntimeException e) {
                Log.errorOnce("object impact effects", e);
            }
        }
        try {
            if (sq != null && p.group.weapon != null && !GameServer.server) {
                zombie.iso.enums.MaterialType mt = null;
                if (material > 0) {
                    try { mt = zombie.iso.enums.MaterialType.valueOf(t3zedballistics.effects.ImpactMaterial.NAMES[material]); }
                    catch (IllegalArgumentException ignored) { mt = null; }
                }
                if (mt != null) SoundManager.instance.playImpactSound(sq, p.group.weapon.getAmmoType(), mt);
                else SoundManager.instance.playImpactSound(sq, p.group.weapon.getAmmoType());
            }
        } catch (RuntimeException e) {
            Log.errorOnce("object impact sound", e);
        }
        finish(p);
        return true;
    }

    private void finish(BallisticProjectile p) {
        if (!p.alive) return; // already finished (protects the pellet counter)
        if (BallisticsDebug.enabled()) drawTrail(p);
        p.alive = false;
        if (p.group != null) p.group.pelletsAlive--;
    }

    private void drawTrail(BallisticProjectile p) {
        float[] t = p.trail;
        double px = p.origin.x, py = p.origin.y, pu = p.origin.u;
        for (int i = 0; i < p.trailCount; i++) {
            BallisticsDebug.line(px, py, pu, t[i * 3], t[i * 3 + 1], t[i * 3 + 2], 1f, 0.9f, 0.1f, 0.9f, BallisticsConfig.debugLifetime);
            px = t[i * 3]; py = t[i * 3 + 1]; pu = t[i * 3 + 2];
        }
        BallisticsDebug.line(px, py, pu, p.position.x, p.position.y, p.position.u, 1f, 0.9f, 0.1f, 0.9f, BallisticsConfig.debugLifetime);
    }

    private void debugImpact(BallisticProjectile p, float r, float g, float b) {
        if (!BallisticsConfig.debugImpacts) return;
        BallisticsDebug.point(p.position, 0.15, r, g, b);
        BallisticsDebug.line(p.previousPosition, p.position, 1f, 0.55f, 0f, 1f); // collision segment
    }

    private static String worldKind(int k) {
        return switch (k) {
            case WorldCollision.HIT_WALL -> "wall/door";
            case WorldCollision.HIT_LOW -> "low wall/fence";
            case WorldCollision.HIT_FLOOR -> "floor/ground";
            case WorldCollision.HIT_CEILING -> "ceiling";
            case WorldCollision.HIT_TREE -> "tree";
            case WorldCollision.HIT_FURNITURE -> "furniture";
            case WorldCollision.HIT_VOID -> "edge of the world";
            default -> "?";
        };
    }

    // =================================================================================== misc

    public ArrayList<BallisticProjectile> activeProjectiles() { return active; }

    public int activeCount() { return active.size(); }

    public double collisionStats() { return collision.charactersTested; }

    /** Removes everything (game reload). */
    public void reset() {
        for (BallisticProjectile p : active) pool.free(p);
        active.clear();
        net.clear(pool);
        BallisticsDebug.clear();
        CharacterHistory.clear();
        attackActive = false;
        lastTickNanos = 0;
    }

    /** Inert test rounds for ZGB_stress(). Returns how many were spawned. */
    public int stress(IsoPlayer p, HandWeapon w, int n) {
        int before = active.size();
        for (int i = 0; i < n && active.size() < MAX_ACTIVE; i++) {
            ShotGroup g = spawnShot(p, w, true);
            if (g == null) break;
            // fan the test rounds out so they exercise different paths
            for (int j = active.size() - g.pelletsAlive; j < active.size(); j++) {
                BallisticProjectile pr = active.get(j);
                double yaw = (rnd.uniform() - 0.5) * Math.toRadians(70);
                double cs = Math.cos(yaw), sn = Math.sin(yaw);
                double vx = pr.velocity.x * cs - pr.velocity.y * sn, vy = pr.velocity.x * sn + pr.velocity.y * cs;
                pr.velocity.set(vx, vy, pr.velocity.u + (rnd.uniform() - 0.5) * 20);
            }
        }
        return active.size() - before;
    }

    public String stats() {
        return String.format("active=%d shots=%d rounds=%d impacts: world=%d characters=%d vehicles=%d expired=%d | update %.3fms "
                + "(collision %.3fms, max %.3fms) | net pending=%d sent=%d packets=%d (%.1f/s) | hitbox tests=%d",
            active.size(), shotsFired, roundsSpawned, impactsWorld, impactsCharacter, impactsVehicle, expired,
            lastUpdateMs, lastCollisionMs, maxUpdateMs, net.pendingCount(), net.shotsSent, net.packetsSent, net.packetsPerSecond,
            collision.charactersTested);
    }

    public void resetStats() {
        maxUpdateMs = 0;
    }
}
