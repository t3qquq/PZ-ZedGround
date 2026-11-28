package me.zed_0xff.zombie_buddy;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

/**
 * Applies this mod's @Patch advice with ZombieBuddy's own PatchEngine outside the game, then loads the
 * patched classes (run with -Xverify:all so the rewritten bytecode is verified) and exercises the 42.22
 * hooks that can run without a world:
 *
 *   INetworkPacket.send        skipped for PacketType.PlayerHit during a ballistic attack, never otherwise
 *   isHitRolledByClient        true for any target while the server processes a projectile packet
 *   PlayerHitPacket            marker / body-part bits read and cleared, head value kept through the roll
 *
 *   java -Djdk.attach.allowAttachSelf=true -Xverify:all [-Dpreload=true] -cp build/zbh:ZombieBuddy.jar:GunWorks.jar:projectzomboid.jar:Viewpoint.jar \
 *        me.zed_0xff.zombie_buddy.OfflinePatchTest zombie.CombatManager zombie.network.fields.hit.Hit ...
 *
 * Lives in ZombieBuddy's package to reach Loader.g_instrumentation.
 */
public class OfflinePatchTest {
    static int passed, failed;

    public static void main(String[] args) throws Exception {
        Instrumentation inst = net.bytebuddy.agent.ByteBuddyAgent.install();
        Loader.g_instrumentation = inst;
        // static initialisers of the packet classes reach ServerOptions, which needs the game's random generator
        Class<?> rand = Class.forName("zombie.core.random.RandStandard");
        rand.getMethod("init").invoke(rand.getField("INSTANCE").get(null));
        // -Dpreload=true: load the targets first, so the patches go in by retransformation - that is how it
        // happens in the game, where these classes are in use long before a mod's jar is loaded.
        if (Boolean.getBoolean("preload")) {
            for (String name : args) Class.forName(name, false, OfflinePatchTest.class.getClassLoader());
            System.out.println("preloaded " + args.length + " classes before patching");
        }
        PatchEngine.applyPatches("t3gunworks", OfflinePatchTest.class.getClassLoader());
        for (String name : args) {
            try {
                Class.forName(name, true, OfflinePatchTest.class.getClassLoader());
                check(true, "loaded and verified " + name);
            } catch (VerifyError | ClassFormatError e) {
                check(false, "patched bytecode of " + name + " is invalid: " + e);
            } catch (ExceptionInInitializerError | NoClassDefFoundError e) {
                // verification happens before static initialisation; the initialiser needing the game is fine
                Throwable root = e;
                while (root.getCause() != null) root = root.getCause();
                check(true, "verified " + name + " (static init needs the game: " + root + (root.getStackTrace().length > 0 ? " at " + root.getStackTrace()[0] : "") + ")");
            }
        }
        behaviour();
        System.out.println("passed " + passed + ", failed " + failed);
        if (failed > 0) System.exit(1);
    }

    static void behaviour() throws Exception {
        ClassLoader cl = OfflinePatchTest.class.getClassLoader();
        Class<?> hooks = Class.forName("t3gunworks.Hooks");
        Class<?> manager = Class.forName("t3gunworks.projectile.BallisticsManager");
        Class<?> serverHits = Class.forName("t3gunworks.network.ServerHits");
        Class<?> packetType = Class.forName("zombie.network.PacketTypes$PacketType");
        Class<?> iNetworkPacket = Class.forName("zombie.network.packets.INetworkPacket");
        Class<?> iConnection = Class.forName("zombie.network.IConnection");
        Object playerHit = packetType.getField("PlayerHit").get(null);
        Object other = packetType.getField("PlaySound").get(null);

        boolean[] reached = new boolean[1];
        Object conn = Proxy.newProxyInstance(cl, new Class<?>[] {iConnection}, (p, m, a) -> {
            if (m.getName().equals("getPacket")) { reached[0] = true; throw new IllegalStateException("stop here"); }
            Class<?> r = m.getReturnType();
            if (r == boolean.class) return false;
            if (r == int.class) return 0;
            if (r == long.class) return 0L;
            if (r == float.class) return 0f;
            if (r == double.class) return 0d;
            if (r == short.class) return (short) 0;
            if (r == byte.class) return (byte) 0;
            return null;
        });
        java.lang.reflect.Method send = iNetworkPacket.getMethod("send", iConnection, packetType, Object[].class);

        Field inAttack = hooks.getDeclaredField("inAttack");
        inAttack.setAccessible(true);
        Field active = manager.getDeclaredField("attackActive");
        active.setAccessible(true);
        Object m = manager.getField("INSTANCE").get(null);

        reached[0] = false;
        send.invoke(null, conn, playerHit, new Object[0]);
        check(reached[0], "PlayerHit packet is sent outside a ballistic attack");

        inAttack.setBoolean(null, true);
        active.setBoolean(m, true);
        reached[0] = false;
        send.invoke(null, conn, playerHit, new Object[0]);
        check(!reached[0], "vanilla's instant PlayerHit packet is dropped during a ballistic attack");
        reached[0] = false;
        send.invoke(null, conn, other, new Object[0]);
        check(reached[0], "other packets still go out during a ballistic attack");
        Field suppressed = hooks.getDeclaredField("missSuppressedThisAttack");
        suppressed.setAccessible(true);
        check(suppressed.getBoolean(null), "the attack self-test saw the dropped packet");
        inAttack.setBoolean(null, false);
        active.setBoolean(m, false);
        reached[0] = false;
        send.invoke(null, conn, playerHit, new Object[0]);
        check(reached[0], "PlayerHit packet is sent again after the attack");

        try {
            Class<?> cm = Class.forName("zombie.CombatManager");
            java.lang.reflect.Method rolled = cm.getMethod("isHitRolledByClient", Class.forName("zombie.iso.IsoMovingObject"));
            Field flag = serverHits.getDeclaredField("packetBallistic");
            flag.setAccessible(true);
            check(!(Boolean) rolled.invoke(null, new Object[] {null}), "vanilla: a non-zombie hit is rolled by the server");
            flag.setBoolean(null, true);
            check((Boolean) rolled.invoke(null, new Object[] {null}), "projectile packet: the shooter's verdict stands for any target");
            flag.setBoolean(null, false);
            check(!(Boolean) rolled.invoke(null, new Object[] {null}), "back to vanilla after the packet");
        } catch (ExceptionInInitializerError | NoClassDefFoundError e) {
            System.out.println("skip  isHitRolledByClient behaviour (CombatManager needs the game: " + e + ")");
        }
        // In its own class so that this one names no game class directly: the JVM would load those while
        // verifying this class, before the patches are installed.
        try {
            Class.forName("me.zed_0xff.zombie_buddy.ServerPacketCheck").getMethod("run").invoke(null);
        } catch (java.lang.reflect.InvocationTargetException e) {
            check(false, "server packet handling could not run: " + e.getCause());
        }
    }

    public static void check(boolean ok, String what) {
        if (ok) passed++; else failed++;
        System.out.println((ok ? "ok    " : "FAIL  ") + what);
    }
}
