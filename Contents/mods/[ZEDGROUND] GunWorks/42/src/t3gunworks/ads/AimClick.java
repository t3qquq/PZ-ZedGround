package t3gunworks.ads;

import zombie.characters.CharacterInputKeyBinding;
import zombie.characters.CharacterInputMode;
import zombie.characters.component.CharacterInputComponent;
import zombie.ui.UIManager;

public final class AimClick {
    static final long TAP_MS = 250L;
    static final int RIGHT = 1;
    static volatile boolean enabled;
    static volatile boolean allowed;
    static volatile String blockedWhy = "";
    static volatile boolean locked;
    private static volatile long pressAt;
    private static volatile boolean pressInWorld;
    private static volatile boolean pressDown;
    private static boolean failed;
    private static boolean hookLogged;
    private static boolean eventsLogged;
    private static boolean holdLogged;

    private AimClick() {
    }

    public static void button(int var0, boolean var1) {
        try {
            if (var0 != 1 || failed) {
                return;
            }

            if (!eventsLogged) {
                eventsLogged = true;
                Log.info("aim: mouse button events hooked (taps are timed from the events, not the per-frame poll)");
            }

            if (!enabled) {
                return;
            }

            long var2 = System.currentTimeMillis();
            if (var1) {
                pressDown = true;
                pressAt = var2;
                holdLogged = false;
                pressInWorld = inWorld();
                return;
            }

            if (!pressDown) {
                return;
            }

            pressDown = false;
            long var4 = var2 - pressAt;
            if (var4 > 250L) {
                return;
            }

            if (!pressInWorld) {
                Log.info("aim: click (" + var4 + " ms) over an inventory window / free cursor -> ignored");
                return;
            }

            if (locked) {
                locked = false;
                Log.info("aim: click detected (" + var4 + " ms) -> sights lowered");
            } else if (allowed) {
                locked = true;
                Log.info("aim: click detected (" + var4 + " ms) -> sights up and locked");
            } else {
                Log.info("aim: click detected (" + var4 + " ms) -> ignored (" + blockedWhy + ")");
            }
        } catch (Throwable var6) {
            fail(var6);
        }
    }

    private static boolean inWorld() {
        return UIManager.isMouseOverInventory() ? false : VP.mouseCaptured();
    }

    public static boolean forceAim(Object var0, boolean var1) {
        try {
            if (!hookLogged) {
                hookLogged = true;
                Log.info("aim: click-aim hook active on CharacterInputComponent.isForceAim (the aim key itself is not touched)");
            }

            if (!failed && enabled) {
                if (var0 instanceof CharacterInputComponent var2 && var2.getInputMode() == CharacterInputMode.KEYBOARD) {
                    if (!allowed && locked) {
                        locked = false;
                        Log.info("aim: sights lock released (" + blockedWhy + ")");
                    }

                    if (pressDown && !holdLogged && !locked && System.currentTimeMillis() - pressAt > 250L && CharacterInputKeyBinding.Aim.isKeyDownInWorld()) {
                        holdLogged = true;
                        Log.info("aim: hold detected -> vanilla hip aim");
                    }

                    return var1 || locked;
                } else {
                    return var1;
                }
            } else {
                if (locked) {
                    locked = false;
                }

                return var1;
            }
        } catch (Throwable var3) {
            fail(var3);
            return var1;
        }
    }

    private static void fail(Throwable var0) {
        if (!failed) {
            failed = true;
            Log.error("click aiming (switched off; holding right mouse still aims)", var0);
        }

        enabled = false;
        locked = false;
    }

    static void clear() {
        locked = false;
    }
}
