package t3zedmanager;

/**
 * ZombieBuddy entry point of the Manager view lock (runs once when ZombieBuddy loads the jar at server join).
 * ZombieBuddy also applies the @Patch classes of this package (PatchVpHold, PatchVpRender).
 * The lock only works when Viewpoint is installed; without it the patched classes never load and nothing happens
 * (ZedManagerView.lua then sees no heartbeat and turns the player away).
 */
public class Main {
    public static final String VERSION = "0.1.0";

    public static void main(String[] args) {
        ViewLock.log("loaded v" + VERSION + " (ZombieBuddy, holds Viewpoint in first/third person)");
    }
}
