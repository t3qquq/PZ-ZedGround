package t3gunworks.aiming;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import t3gunworks.BallisticsUnits;
import t3gunworks.Hooks;
import t3gunworks.Log;
import t3gunworks.V3;
import t3gunworks.collision.ProjectileCollision;
import t3gunworks.combat.VanillaCombat;
import t3gunworks.config.BallisticsConfig;
import t3gunworks.weapon.BallisticProfile;
import t3gunworks.weapon.WeaponBallisticsResolver;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.inventory.InventoryItem;
import zombie.inventory.types.HandWeapon;
import zombie.network.GameServer;

/**
 * True Ballistics takes over Viewpoint 0.1.6's "Gunplay Rework" for the firearms it simulates.
 *
 * Viewpoint's gunplay makes the crosshair wander inside an aim ring sized from the game's hit chance, turns
 * the dot red from its own instant hit test, and resolves the shot instantly. None of that matches a
 * simulated round, so while True Ballistics owns the aim:
 *
 *   viewpoint.gunplay.Gunplay.active(...)  -> false   (no sway, no instant shot, the dot stays on the screen
 *                                                       centre, which is exactly where TB's aim ray goes)
 *   View.crosshairOnTarget                 -> TB's own aim ray: red only when the round would meet a body
 *   the aim ring (vanilla IsoReticle, sized by viewpoint.gunplay.Reticle.set)
 *                                          -> TB's real dispersion: 2 sigma (skill, moodles, movement,
 *                                             condition, recoil bloom, sandbox spread) plus the pellet cone
 *
 * Everything is reached by reflection and looked up once; if a Viewpoint update changes it, the override
 * switches itself off and Viewpoint's own gunplay is left alone.
 */
public final class CrosshairOverride {
    private static final long FRESH = 1_000_000_000L; // a slow frame can take 250 ms; the ring must not flicker back
    /** Height of the third-person ray's start above the feet: the same eye height AimProvider uses for the shot. */
    private static final double EYE = 1.55, SNEAK_EYE = 1.15;

    private static boolean resolved, ok;
    private static Field onTarget;
    private static Object ringToggle;
    private static Method toggleGet, reticleSet;

    private static final ProjectileCollision collision = new ProjectileCollision();
    private static final ProjectileCollision.Result res = new ProjectileCollision.Result();
    private static final IsoGameCharacter[] ignore = new IsoGameCharacter[1];
    private static final V3 camera = new V3(), forward = new V3(), start = new V3(), end = new V3(), head = new V3();
    private static final double[] sight = new double[7];

    /** Main thread, once per Viewpoint frame. */
    private static volatile boolean owned, targetFlag;
    private static volatile double ringRadians;
    private static volatile long ownedNanos;
    private static boolean announced;

    private static void resolve() {
        if (resolved) return;
        resolved = true;
        try {
            ClassLoader cl = CrosshairOverride.class.getClassLoader();
            Class<?> view = cls("viewpoint.core.View", cl);
            onTarget = view.getField("crosshairOnTarget");
            Class<?> gunplay = cls("viewpoint.gunplay.Gunplay", cl);
            Field ring = gunplay.getDeclaredField("RING");
            ring.setAccessible(true);
            ringToggle = ring.get(null);
            toggleGet = ringToggle.getClass().getMethod("get");
            toggleGet.setAccessible(true);
            Class<?> reticle = cls("viewpoint.gunplay.Reticle", cl);
            reticleSet = reticle.getDeclaredMethod("set", zombie.iso.sprite.IsoReticle.class, double.class);
            reticleSet.setAccessible(true);
            ok = true;
        } catch (ClassNotFoundException e) {
            ok = false; // Viewpoint without Gunplay (0.1.5a): nothing to override
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            ok = false;
            Log.warn("Viewpoint Gunplay crosshair override unavailable (" + e + "); Viewpoint's own crosshair is used");
        }
    }

    private static Class<?> cls(String n, ClassLoader cl) throws ClassNotFoundException {
        try {
            return Class.forName(n, false, cl);
        } catch (ClassNotFoundException e) {
            ClassLoader ctx = Thread.currentThread().getContextClassLoader();
            if (ctx == null) throw e;
            return Class.forName(n, false, ctx);
        }
    }

    /**
     * Whether True Ballistics simulates this character's shots with this weapon right now (aiming frames
     * included, so unlike BallisticsManager.wouldSimulate an empty magazine does not hand the aim back).
     */
    public static boolean ownsAim(Object owner, Object weapon) {
        if (Hooks.disabled || !BallisticsConfig.enabled || GameServer.server || !VanillaCombat.objectHitControl) return false;
        if (!(owner instanceof IsoPlayer p) || p != IsoPlayer.players[0]) return false;
        if (!(weapon instanceof HandWeapon w) || !w.isAimedFirearm()) return false;
        if (p.getVehicle() != null) return false;
        return ViewpointBridge.controls(p);
    }

    /** viewpoint.gunplay.Gunplay.snapshot exit (main thread, after CrosshairAim.snapshot placed the camera). */
    public static void frame() {
        resolve();
        if (!ok) return;
        IsoPlayer p = IsoPlayer.players[0];
        InventoryItem item = p == null ? null : p.getPrimaryHandItem();
        if (p == null || !ownsAim(p, item)) {
            owned = false;
            return;
        }
        HandWeapon w = (HandWeapon) item;
        if (!announced) {
            announced = true;
            Log.info("Viewpoint Gunplay Rework overridden for simulated firearms: crosshair, aim ring and target colour come from True Ballistics.");
        }
        BallisticProfile prof = WeaponBallisticsResolver.resolve(w);
        boolean ads = false;
        if (!ViewpointBridge.thirdPerson() && AdsSightBridge.ray(sight)) {
            camera.set(sight[0], sight[1], sight[2]);
            forward.set(sight[3], sight[4], sight[5]).normalize();
            ads = camera.finite() && forward.finite();
        }
        if (!ads) {
            ViewpointBridge.direction(ViewpointBridge.yaw(), ViewpointBridge.pitch(), forward);
            if (!ViewpointBridge.camera(camera)) {
                camera.set(p.getX(), p.getY(), BallisticsUnits.levelsToMeters(p.getZ()) + 1.55);
            }
        }

        double sigma = Spread.sigmaRadians(p, w, prof) * (ads ? BallisticsConfig.adsSpreadScale : 1.0);
        double cone = prof.pelletCount > 1 ? Math.toRadians(prof.pelletSpreadDeg) : 0.0;
        ringRadians = 2.0 * sigma + cone;
        targetFlag = aimedAtBody(p, w, prof);
        owned = true;
        ownedNanos = System.nanoTime();
        try {
            onTarget.setBoolean(null, targetFlag);
        } catch (IllegalAccessException | RuntimeException e) {
            Log.errorOnce("CrosshairOverride.onTarget", e);
        }
    }

    /** The straight aim ray from the camera: true when its first hit is a living character. */
    private static boolean aimedAtBody(IsoPlayer p, HandWeapon w, BallisticProfile prof) {
        try {
            double x = p.getX(), y = p.getY(), u0 = BallisticsUnits.levelsToMeters(p.getZ());
            start.set(camera);
            if (ViewpointBridge.thirdPerson()) {
                // start level with the shooter so nothing behind the over-the-shoulder camera counts
                head.set(x - camera.x, y - camera.y, u0 + (p.isSneaking() ? SNEAK_EYE : EYE) - camera.u);
                double t0 = Math.max(0, head.dot(forward));
                start.set(camera.x + forward.x * t0, camera.y + forward.y * t0, camera.u + forward.u * t0);
            }
            double dist = WeaponBallisticsResolver.maxDistance(w, prof, t3gunworks.network.BallisticsNetwork.vanillaRangeOnly());
            ignore[0] = p;
            collision.world.bind();
            if (!SceneRay.sweep(collision, start, forward, dist, ignore, 1, res)) return false;
            return res.kind == ProjectileCollision.CHARACTER && res.character != null && !res.character.isDead();
        } catch (RuntimeException e) {
            Log.errorOnce("CrosshairOverride.aimedAtBody", e);
            return false;
        }
    }

    /**
     * viewpoint.gunplay.Gunplay.reticle exit: while True Ballistics owns the aim, the aim ring shows its
     * dispersion (or stays hidden when Viewpoint's "Show the aim ring" is off).
     *
     * @return the value Gunplay.reticle should return (true = skip the vanilla reticle)
     */
    public static boolean reticle(Object isoReticle, boolean skip) {
        if (!ok || !owned || System.nanoTime() - ownedNanos >= FRESH) return skip;
        try {
            if (!Boolean.TRUE.equals(toggleGet.invoke(ringToggle))) return true;
            reticleSet.invoke(null, isoReticle, ringRadians);
            return false;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.errorOnce("CrosshairOverride.reticle", e);
            ok = false;
            return skip;
        }
    }

    private CrosshairOverride() {}
}
