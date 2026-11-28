package t3gunworks.projectile;

import java.util.ArrayDeque;

/**
 * Pools for rounds and shot groups so continuous automatic fire allocates nothing after warm-up.
 * Main thread only.
 */
public final class ProjectilePool {
    private static final int MAX_POOLED = 2048;
    private final ArrayDeque<BallisticProjectile> projectiles = new ArrayDeque<>();
    private final ArrayDeque<ShotGroup> groups = new ArrayDeque<>();

    public BallisticProjectile obtain() {
        BallisticProjectile p = projectiles.pollLast();
        if (p == null) p = new BallisticProjectile();
        p.reset();
        p.alive = true;
        return p;
    }

    public void free(BallisticProjectile p) {
        p.reset();
        if (projectiles.size() < MAX_POOLED) projectiles.addLast(p);
    }

    public ShotGroup obtainGroup() {
        ShotGroup g = groups.pollLast();
        if (g == null) g = new ShotGroup();
        g.reset();
        return g;
    }

    public void freeGroup(ShotGroup g) {
        g.reset();
        if (groups.size() < 256) groups.addLast(g);
    }
}
