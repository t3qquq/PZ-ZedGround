package t3gunworks.npc;

import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

import se.krka.kahlua.vm.KahluaTable;
import t3gunworks.BallisticsUnits;
import t3gunworks.Log;
import t3gunworks.V3;
import t3gunworks.collision.HitVolumes;
import t3gunworks.collision.ProjectileCollision;
import t3gunworks.config.BallisticsConfig;
import t3gunworks.projectile.BallisticProjectile;
import t3gunworks.projectile.ProjectilePhysics;
import t3gunworks.weapon.BallisticProfile;
import t3gunworks.weapon.WeaponBallisticsResolver;
import zombie.GameTime;
import zombie.Lua.LuaManager;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoZombie;
import zombie.core.skinnedmodel.animation.AnimationPlayer;
import zombie.core.skinnedmodel.model.Model;
import zombie.inventory.types.HandWeapon;
import zombie.iso.objects.IsoWindow;
import zombie.iso.Vector3;

/**
 * Real projectiles for scripted shooters (NPC mods). The NPC mod decides WHEN a character fires and at
 * WHAT; this class decides where the round actually goes, with the same physics (muzzle velocity, drag,
 * drop) and the same collision (walls, doors, windows, furniture, trees, vehicles, skeleton hitboxes) as
 * a player's round. Impacts are handed back to Lua, which applies damage through the NPC mod's own rules.
 *
 * Two kinds of rounds:
 *   authoritative - fired where the NPC mod runs its combat (single player, or the dedicated server);
 *                   they smash glass and report impacts
 *   visual        - replayed on multiplayer clients from the server's launch data; they only draw a tracer
 *                   and stop at what they hit, so the streak matches the real round
 *
 * Driven from Lua (Events.OnTick -> GunWorksJ.npcTick) on both sides, so it also runs on a
 * dedicated server where the game's CombatManager.update is never called.
 */
public final class NpcBallistics {
    public static final NpcBallistics INSTANCE = new NpcBallistics();

    private static final int MAX_ROUNDS = 512;
    private static final int MAX_SUBSTEPS = 64;
    private static final double TORSO_RADIUS = 0.25;     // m, target size the NPC mod's hit chance is converted for
    private static final double MIN_SIGMA = 0.0008, MAX_SIGMA = 0.08; // rad
    private static final double HARD_CAP = 150.0;        // m, no NPC round ever flies further

    private static final class Round {
        final BallisticProjectile p = new BallisticProjectile();
        int id;
        boolean authoritative;
        // for t3gunworks.api.ImpactEvents (authoritative rounds only)
        IsoGameCharacter shooter;
        HandWeapon weapon;
        BallisticProfile profile;
        int pellets;
    }

    private final ArrayList<Round> rounds = new ArrayList<>();
    private final ArrayList<BallisticProjectile> tracers = new ArrayList<>();
    private final java.util.HashMap<Integer, int[]> pelletsAlive = new java.util.HashMap<>();
    private final ProjectileCollision collision = new ProjectileCollision();
    private final ProjectileCollision.Result res = new ProjectileCollision.Result();
    private final V3 muzzle = new V3(), aim = new V3(), dir = new V3(), tmp = new V3();
    private final Vector3 bone = new Vector3();
    private int nextId = 1;
    private long lastNanos;
    public long fired, impacts;

    private NpcBallistics() {}

    // ================================================================================== launching

    /**
     * Fires one trigger pull. target may be null (then aimX/aimY/aimZ is the aim point, z in levels).
     * hitChance (0..1) is the NPC mod's own chance to hit a person at this range; it becomes an angular
     * dispersion so a 60% shooter lands about 60% of rounds on a torso there - but walls, cover and
     * bodies in the way now decide what actually gets hit. Returns the launch data (for client tracers)
     * or null if nothing was fired.
     */
    public KahluaTable fire(Object shooterObj, Object targetObj, double aimX, double aimY, double aimZ, Object weaponObj,
                            double hitChance, double maxDistance, boolean authoritative, boolean tracer) {
        if (!(shooterObj instanceof IsoGameCharacter shooter) || !(weaponObj instanceof HandWeapon w)) return null;
        if (rounds.size() >= MAX_ROUNDS) return null;
        collision.world.bind();

        BallisticProfile prof = WeaponBallisticsResolver.resolve(w);
        double v0 = prof.muzzleVelocity * BallisticsConfig.velocityScale;
        double g = BallisticsConfig.GRAVITY * BallisticsConfig.gravityScale * prof.gravityScale;
        double k = prof.drag * BallisticsConfig.dragScale;
        double range = WeaponBallisticsResolver.maxDistance(w, prof, false);
        if (maxDistance > 0) range = Math.min(range, maxDistance);
        range = Math.min(range, HARD_CAP);

        IsoGameCharacter target = targetObj instanceof IsoGameCharacter c ? c : null;
        if (target != null) {
            aim.set(target.getX(), target.getY(), BallisticsUnits.levelsToMeters(target.getZ()) + aimHeight(target));
        } else {
            aim.set(aimX, aimY, BallisticsUnits.levelsToMeters(aimZ) + 1.1);
        }
        muzzleOf(shooter, aim, muzzle);

        dir.set(aim.x - muzzle.x, aim.y - muzzle.y, aim.u - muzzle.u);
        double dist = dir.length();
        if (dist < 0.05 || !dir.finite()) return null;
        dir.normalize();
        // A shooter holds over for the range it is aiming at (its sight is "zeroed" on the target).
        ProjectilePhysics.pitchUp(dir, ProjectilePhysics.zeroAngle(v0, g, k, Math.hypot(aim.x - muzzle.x, aim.y - muzzle.y)));

        double p = Math.max(0.02, Math.min(0.995, hitChance));
        double sigma = TORSO_RADIUS / (Math.max(1.0, dist) * Math.sqrt(-2.0 * Math.log(1.0 - p)));
        sigma = Math.max(MIN_SIGMA, Math.min(MAX_SIGMA, sigma)) * BallisticsConfig.spreadScale;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        ProjectilePhysics.deflect(dir, rnd.nextGaussian() * sigma, rnd.nextGaussian() * sigma);

        int id = nextId++;
        if (nextId > 1_000_000_000) nextId = 1;
        int pellets = prof.pelletCount;
        double cone = Math.toRadians(prof.pelletSpreadDeg);
        double lifetime = prof.lifetime > 0 ? prof.lifetime : BallisticsConfig.projectileLifetime;

        KahluaTable out = LuaManager.platform.newTable();
        out.rawset("id", (double) id);
        out.rawset("ox", muzzle.x); out.rawset("oy", muzzle.y); out.rawset("ou", muzzle.u);
        out.rawset("v0", v0); out.rawset("g", g); out.rawset("k", k);
        out.rawset("range", range); out.rawset("life", lifetime);
        KahluaTable dirs = LuaManager.platform.newTable();
        out.rawset("dirs", dirs);

        int launched = 0;
        for (int i = 0; i < pellets && rounds.size() < MAX_ROUNDS; i++) {
            tmp.set(dir);
            if (pellets > 1) {
                double r = cone * Math.sqrt(rnd.nextDouble()), a = rnd.nextDouble() * Math.PI * 2;
                ProjectilePhysics.deflect(tmp, Math.cos(a) * r, Math.sin(a) * r);
            }
            Round rd = spawn(id, muzzle, tmp, v0, g, k, range, lifetime, authoritative, tracer);
            rd.p.massGrams = prof.massGrams;
            rd.p.penetrationLeft = BallisticsConfig.penetration ? prof.penetration : 0;
            rd.p.addIgnore(shooter);
            rd.p.pellet = i;
            rd.shooter = shooter;
            rd.weapon = w;
            rd.profile = prof;
            rd.pellets = pellets;
            launched++;
            KahluaTable d = LuaManager.platform.newTable();
            d.rawset("x", tmp.x); d.rawset("y", tmp.y); d.rawset("u", tmp.u);
            dirs.rawset((double) launched, d);
        }
        if (launched == 0) return null;
        if (authoritative) pelletsAlive.put(id, new int[] { launched });
        out.rawset("pellets", (double) launched);
        fired++;
        return out;
    }

    /** Client side: replays a server launch as visual-only tracer rounds (no damage, no glass). */
    public void visual(KahluaTable t) {
        if (t == null) return;
        double ox = num(t, "ox"), oy = num(t, "oy"), ou = num(t, "ou");
        double v0 = num(t, "v0"), g = num(t, "g"), k = num(t, "k");
        double range = Math.min(HARD_CAP, num(t, "range")), life = num(t, "life");
        if (!(v0 > 1) || !Double.isFinite(ox + oy + ou + g + k + range)) return;
        Object dirsObj = t.rawget("dirs");
        if (!(dirsObj instanceof KahluaTable dirs)) return;
        muzzle.set(ox, oy, ou);
        for (int i = 1; i <= 16; i++) {
            if (!(dirs.rawget((double) i) instanceof KahluaTable d)) break;
            tmp.set(num(d, "x"), num(d, "y"), num(d, "u"));
            if (!tmp.finite() || tmp.length() < 0.5) continue;
            tmp.normalize();
            spawn(0, muzzle, tmp, v0, g, k, range, life > 0 ? life : 3.0, false, true);
        }
    }

    private Round spawn(int id, V3 from, V3 d, double v0, double g, double k, double range, double lifetime,
                        boolean authoritative, boolean tracer) {
        Round rd = new Round();
        BallisticProjectile p = rd.p;
        rd.id = id;
        rd.authoritative = authoritative;
        p.position.set(from);
        p.previousPosition.set(from);
        p.origin.set(from);
        p.velocity.set(d.x * v0, d.y * v0, d.u * v0);
        p.muzzleVelocity = v0;
        p.gravity = g;
        p.drag = k;
        p.maxDistance = range;
        p.maxLifetime = lifetime;
        p.tracer = tracer;
        p.alive = true;
        rounds.add(rd);
        if (tracer) tracers.add(p);
        return rd;
    }

    /** Chest height of what the NPC aims at (m above its feet). */
    private static double aimHeight(IsoGameCharacter t) {
        try {
            if (t.isProne() || t.isOnFloor() || (t instanceof IsoZombie z && z.isCrawling())) return 0.25;
            if (t.isSneaking()) return 0.85;
        } catch (RuntimeException ignored) {
            // fall through to standing
        }
        return 1.3;
    }

    /** Muzzle: the right hand from the animated skeleton, pushed forward along the gun; else a standing estimate. */
    private void muzzleOf(IsoGameCharacter c, V3 aimPoint, V3 out) {
        double x = c.getX(), y = c.getY(), u0 = BallisticsUnits.levelsToMeters(c.getZ());
        double fx = aimPoint.x - x, fy = aimPoint.y - y;
        double fl = Math.hypot(fx, fy);
        if (fl > 1e-6) { fx /= fl; fy /= fl; } else { fx = 1; fy = 0; }
        try {
            if (c.hasAnimationPlayer()) {
                AnimationPlayer ap = c.getAnimationPlayer();
                if (ap != null && ap.isReady()) {
                    int idx = ap.getSkinningBoneIndex("Bip01_R_Hand", -1);
                    if (idx >= 0) {
                        Model.boneToWorldCoords(c, idx, bone);
                        double hu = BallisticsUnits.levelsToMeters(bone.z);
                        double dx = bone.x - x, dy = bone.y - y;
                        if (Double.isFinite(hu) && dx * dx + dy * dy < 1.0 && hu > u0 + 0.3 && hu < u0 + 1.9) {
                            out.set(bone.x + fx * 0.45, bone.y + fy * 0.45, hu + 0.05);
                            return;
                        }
                    }
                }
            }
        } catch (RuntimeException e) {
            Log.errorOnce("NPC muzzle from skeleton", e);
        }
        double h = c.isSneaking() ? 0.95 : 1.4;
        out.set(x + fx * 0.6, y + fy * 0.6, u0 + h);
    }

    // ================================================================================== simulation

    /** Advances every round by the real time since the last call. Returns the impacts of authoritative rounds. */
    public KahluaTable tick() {
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min(0.25, (now - lastNanos) / 1e9);
        lastNanos = now;
        KahluaTable out = null;
        if (rounds.isEmpty() || dt <= 0) return null;
        try {
            if (GameTime.isGamePaused()) return null;
        } catch (RuntimeException | LinkageError ignored) {
            // no game time on this side: keep simulating
        }
        collision.world.bind();
        int n = 0;
        for (int i = rounds.size() - 1; i >= 0; i--) {
            Round rd = rounds.get(i);
            try {
                out = simulate(rd, dt, out);
            } catch (RuntimeException e) {
                Log.errorOnce("NPC round update (round removed)", e);
                rd.p.alive = false;
            }
            if (!rd.p.alive) {
                rounds.remove(i);
                if (rd.p.tracer) tracers.remove(rd.p);
                if (rd.authoritative) out = finished(rd.id, out);
            }
        }
        return out;
    }

    private KahluaTable simulate(Round rd, double dt, KahluaTable out) {
        BallisticProjectile p = rd.p;
        double remaining = dt;
        int steps = 0;
        while (remaining > 1e-9 && p.alive && steps++ < MAX_SUBSTEPS) {
            double speed = Math.max(1.0, p.speed());
            double h = Math.min(remaining, BallisticsConfig.MAX_SEGMENT / speed);
            ProjectilePhysics.step(p.position, p.velocity, p.previousPosition, h, p.gravity, p.drag);
            p.age += h;
            remaining -= h;
            double seg = p.previousPosition.dist(p.position);
            boolean rangeEnd = false;
            if (p.traveled + seg >= p.maxDistance && seg > 1e-9) {
                p.position.lerp(p.previousPosition, p.position, Math.max(0, (p.maxDistance - p.traveled) / seg));
                seg = p.previousPosition.dist(p.position);
                rangeEnd = true;
            }
            // visual replays skip bodies for the first metre (they do not know the shooter's own arms)
            boolean bodies = rd.authoritative || p.traveled > 1.0;
            boolean hit = collision.sweep(p.previousPosition, p.position, p.ignore, p.ignoreCount, rd.authoritative, bodies, true, res);
            double tStop = hit ? res.t : 1.0;
            if (rd.authoritative) {
                for (int i = 0; i < res.windows.count; i++) {
                    if (res.windows.t[i] >= tStop) break;
                    smash(p, res.windows.window[i]);
                }
            }
            if (hit) {
                p.traveled += seg * tStop;
                p.position.set(res.point);
                out = impact(rd, out);
            } else {
                p.traveled += seg;
            }
            if (!p.alive) return out;
            if (rangeEnd || p.traveled >= p.maxDistance || p.age >= p.maxLifetime
                    || p.speed() < Math.min(BallisticsConfig.MIN_SPEED, 0.25 * p.muzzleVelocity)) {
                p.alive = false;
            }
        }
        return out;
    }

    /** Bullet hole and debris where an NPC round (authoritative or a client's visual replay) hit the world. */
    private void worldEffects(Round rd) {
        try {
            BallisticProjectile p = rd.p;
            t3gunworks.collision.WorldCollision.Hit h = res.world;
            double vl = Math.max(1e-9, p.velocity.length());
            tmp.set(p.velocity.x / vl, p.velocity.y / vl, p.velocity.u / vl);
            collision.world.bind();
            zombie.iso.IsoGridSquare sq = collision.world.square(h.tileX, h.tileY, h.tileZ);
            zombie.iso.IsoGridSquare far = (h.edgeDx != 0 || h.edgeDy != 0)
                ? collision.world.square(h.tileX + h.edgeDx, h.tileY + h.edgeDy, h.tileZ) : null;
            int material = t3gunworks.effects.ImpactMaterial.resolve(h.kind, sq, far, h.edgeDx, h.edgeDy);
            BallisticProfile prof = rd.profile != null ? rd.profile : (rd.weapon != null ? WeaponBallisticsResolver.resolve(rd.weapon) : null);
            double ratio = p.speed() / Math.max(1.0, p.muzzleVelocity);
            t3gunworks.effects.ImpactEffects.INSTANCE.impact(res.point, h.nx, h.ny, h.nu, tmp, h.kind, h.tileX, h.tileY, h.tileZ,
                h.edgeDx, h.edgeDy, material, prof, ratio * ratio, false);
        } catch (RuntimeException e) {
            Log.errorOnce("NPC bullet hole / impact debris", e);
        }
    }

    /** t3gunworks.api.ImpactEvents for an authoritative NPC round touching a body (before the NPC mod's damage). */
    private void report(Round rd, IsoGameCharacter c, double ratio, boolean through) {
        BallisticProjectile p = rd.p;
        t3gunworks.api.ImpactEvents.Impact i = t3gunworks.api.ImpactEvents.begin();
        i.npc = true;
        i.shooter = rd.shooter;
        i.target = c;
        i.weapon = rd.weapon;
        i.shotSeq = rd.id;
        i.pellet = p.pellet;
        i.pelletCount = rd.pellets;
        i.ragdollPart = res.bodyPart;
        i.bodyPartType = res.bodyPartType >= 0 ? res.bodyPartType : bodyPartIndex(res.bodyPart, c);
        i.hitX = res.point.x; i.hitY = res.point.y; i.hitU = res.point.u;
        double l = p.velocity.length();
        if (l > 1e-9) { i.dirX = p.velocity.x / l; i.dirY = p.velocity.y / l; i.dirU = p.velocity.u / l; }
        if (res.hasCapsuleExit) {
            i.hasCapsuleExit = true;
            i.capsuleExitX = res.capsuleExit.x; i.capsuleExitY = res.capsuleExit.y; i.capsuleExitU = res.capsuleExit.u;
        }
        i.speed = p.speed();
        i.muzzleSpeed = p.muzzleVelocity;
        i.speedRatio = ratio;
        i.massGrams = p.massGrams;
        if (rd.profile != null) {
            i.category = rd.profile.category;
            i.penetrationBudget = rd.profile.penetration;
            i.energyJoules = rd.profile.energy(rd.profile.muzzleVelocity * ratio);
        }
        i.pierced = p.ignoreCount > 1;
        i.penetrates = through;
        t3gunworks.api.ImpactEvents.fire(i, t3gunworks.api.ImpactEvents.PHASE_REPORTED);
    }

    private KahluaTable impact(Round rd, KahluaTable out) {
        BallisticProjectile p = rd.p;
        if (res.kind == ProjectileCollision.WORLD && !zombie.network.GameServer.server) worldEffects(rd);
        if (!rd.authoritative) {
            p.alive = false;
            return out;
        }
        impacts++;
        KahluaTable e = LuaManager.platform.newTable();
        e.rawset("id", (double) rd.id);
        e.rawset("x", p.position.x);
        e.rawset("y", p.position.y);
        e.rawset("z", BallisticsUnits.metersToLevels(p.position.u));
        e.rawset("dist", p.origin.dist(p.position));
        double ratio = p.speed() / Math.max(1.0, p.muzzleVelocity);
        e.rawset("energy", ratio * ratio);
        switch (res.kind) {
            case ProjectileCollision.CHARACTER -> {
                IsoGameCharacter c = res.character;
                e.rawset("kind", "character");
                e.rawset("character", c);
                e.rawset("zone", zone(res.bodyPart));
                e.rawset("part", (double) (res.bodyPartType >= 0 ? res.bodyPartType : bodyPartIndex(res.bodyPart, c)));
                boolean through = p.penetrationLeft > 0 && ratio * ratio > t3gunworks.projectile.BallisticsManager.PENETRATION_ENERGY_RATIO;
                if (t3gunworks.api.ImpactEvents.hasListeners()) report(rd, c, ratio, through);
                if (through) {
                    p.penetrationLeft--;
                    p.addIgnore(c);
                    p.velocity.set(p.velocity.x * 0.6, p.velocity.y * 0.6, p.velocity.u * 0.6);
                } else {
                    p.alive = false;
                }
            }
            case ProjectileCollision.VEHICLE -> {
                e.rawset("kind", "vehicle");
                e.rawset("vehicle", res.vehicle);
                p.alive = false;
            }
            default -> {
                e.rawset("kind", "world");
                p.alive = false;
            }
        }
        return append(out, e);
    }

    private KahluaTable finished(int id, KahluaTable out) {
        int[] left = pelletsAlive.get(id);
        if (left == null) return out;
        if (--left[0] > 0) return out;
        pelletsAlive.remove(id);
        KahluaTable e = LuaManager.platform.newTable();
        e.rawset("id", (double) id);
        e.rawset("kind", "done");
        return append(out, e);
    }

    private static KahluaTable append(KahluaTable out, KahluaTable e) {
        if (out == null) out = LuaManager.platform.newTable();
        out.rawset((double) (out.len() + 1), e);
        return out;
    }

    private void smash(BallisticProjectile p, Object w) {
        if (!(w instanceof IsoWindow win)) return;
        try {
            if (!win.isDestroyed() && !win.isSmashed()) win.smashWindow();
            p.velocity.set(p.velocity.x * BallisticsConfig.WINDOW_VELOCITY_KEEP, p.velocity.y * BallisticsConfig.WINDOW_VELOCITY_KEEP,
                p.velocity.u * BallisticsConfig.WINDOW_VELOCITY_KEEP);
        } catch (RuntimeException e) {
            Log.errorOnce("NPC round breaking a window", e);
        }
    }

    // ================================================================================== mapping

    /** The NPC mod's coarse zones: head / torso / limb. */
    static String zone(int part) {
        return switch (part) {
            case HitVolumes.HEAD -> "head";
            case HitVolumes.SPINE, HitVolumes.PELVIS, -1 -> "torso";
            default -> "limb";
        };
    }

    /** Vanilla BodyPartType index (BodyPartType.ToIndex) of the part a skeleton capsule stands for. */
    static int bodyPartIndex(int part, IsoGameCharacter c) {
        return switch (part) {
            case HitVolumes.HEAD -> 8;              // Head
            case HitVolumes.SPINE -> 6;             // Torso_Upper
            case HitVolumes.PELVIS -> 7;            // Torso_Lower
            case HitVolumes.L_UPPER_LEG -> 11;
            case HitVolumes.R_UPPER_LEG -> 12;
            case HitVolumes.L_LOWER_LEG -> 13;
            case HitVolumes.R_LOWER_LEG -> 14;
            case HitVolumes.L_UPPER_ARM -> 4;
            case HitVolumes.R_UPPER_ARM -> 5;
            case HitVolumes.L_LOWER_ARM -> 2;
            case HitVolumes.R_LOWER_ARM -> 3;
            default -> 6;
        };
    }

    private static double num(KahluaTable t, String key) {
        Object o = t.rawget(key);
        return o instanceof Number n ? n.doubleValue() : Double.NaN;
    }

    // ================================================================================== misc

    /** Rounds drawn as tracers this frame (Viewpoint 3D view). */
    public ArrayList<BallisticProjectile> tracerRounds() { return tracers; }

    public int activeCount() { return rounds.size(); }

    public void reset() {
        rounds.clear();
        tracers.clear();
        pelletsAlive.clear();
        lastNanos = 0;
    }
}
