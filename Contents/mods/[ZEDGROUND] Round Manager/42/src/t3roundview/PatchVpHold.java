package t3roundview;

import me.zed_0xff.zombie_buddy.Patch;

/**
 * viewpoint.ViewHold.frame(IsoGameCharacter) reads viewpoint.core.View.heldView and moves the view towards it
 * (names from javap of Viewpoint 0.1.7, bodies not read). The hold value is written right before it runs.
 * ZombieBuddy inlines this advice into ViewHold: everything it calls must be public.
 */
@Patch(className = "viewpoint.ViewHold", methodName = "frame")
public class PatchVpHold {
    @Patch.OnEnter
    public static void enter() {
        ViewLock.frame("hold");
    }
}
