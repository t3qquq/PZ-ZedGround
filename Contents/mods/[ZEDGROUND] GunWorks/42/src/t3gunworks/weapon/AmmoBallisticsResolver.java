package t3gunworks.weapon;

import java.util.regex.Pattern;

/**
 * Turns an ammunition identifier (vanilla "base:bullets_9mm", or whatever a Workshop gun pack uses)
 * plus a few weapon script values into a starting ballistic profile. No list of weapon IDs: the
 * calibre is recognised from the ammo name, and when that fails the weapon's own stats decide.
 *
 * Velocities are rounded real-world figures for common loads (not taken from any game); they are
 * scaled by the VelocityScale sandbox option at spawn time.
 *
 * Pure Java on purpose (no game classes) so it is unit-tested offline.
 */
public final class AmmoBallisticsResolver {

    private record Rule(Pattern pattern, BallisticProfile profile) {}

    // category templates ------------------------------------------------------------------------------
    //                                       name                  v0    drag    g   mass pel sprd base dmg  rng life pen tracer
    static final BallisticProfile HANDGUN   = p("handgun",           360, 0.0016, 1, 8.0, 1, 0, 0, 1, 0, 0, 0);
    static final BallisticProfile REVOLVER  = p("magnum handgun",    435, 0.0015, 1, 10.2, 1, 0, 0, 1, 0, 0, 0);
    static final BallisticProfile PDW       = p("PDW",               715, 0.0014, 1, 2.0, 1, 0, 0, 1, 0, 0, 0);
    static final BallisticProfile RIMFIRE   = p("rimfire",           370, 0.0020, 1, 2.6, 1, 0, 0, 1, 0, 0, 0);
    static final BallisticProfile INTERMED  = p("intermediate rifle",900, 0.0010, 1, 4.0, 1, 0, 0, 1, 0, 0, 1);
    static final BallisticProfile SUBSONIC  = p("subsonic rifle",    300, 0.0016, 1, 16.0, 1, 0, 0, 1, 0, 0, 1);
    static final BallisticProfile FULLPOWER = p("full-power rifle",  830, 0.0007, 1, 10.0, 1, 0, 0, 1, 0, 0, 2);
    static final BallisticProfile ANTIMAT   = p("heavy rifle",       880, 0.0004, 1, 45.0, 1, 0, 0, 1, 0, 0, 3);
    static final BallisticProfile BUCKSHOT  = p("shotgun",           400, 0.0060, 1, 3.5, 9, 1.2, 0, 1, 0, 0, 0);
    static final BallisticProfile SLUG      = p("shotgun slug",      450, 0.0025, 1, 28.0, 1, 0, 0, 1, 0, 0, 1);

    private static final Rule[] RULES = {
        // shotguns first ("12g", "shells", "buck", "slug")
        r("slug", SLUG),
        r("shell|shotgun|buck|gauge|(?<!\\d)(10|12|16|20)g(?![a-z])|(?<!\\d)(12|20)ga", BUCKSHOT),
        // heavy / anti-materiel
        r("50bmg|(?<!\\d)127x(99|108)|(?<!\\d)145x114|(?<!\\d)408|(?<!\\d)338", ANTIMAT),
        // subsonic special
        r("9x39|300blk|300aac", SUBSONIC),
        // full-power rifle calibres (checked before intermediate because "762x54" contains "762")
        r("(?<!\\d)308(?!\\d)|762x51|762x54|7623x?54|(?<!\\d)3030|(?<!\\d)3006|(?<!\\d)303(?!\\d)|(?<!\\d)270(?!\\d)|300win|65creed|(?<!\\d)243(?!\\d)|8mm|792x57|7x57|(?<!\\d)3570|4570|(?<!\\d)458", FULLPOWER),
        // Tokarev before intermediate: "762x25" also contains "762"
        r("762x25", HANDGUN),
        // intermediate
        r("(?<!\\d)556|(?<!\\d)223(?!\\d)|(?<!\\d)545(?!\\d)|545x39|762x39|58x42|68spc|65grendel|(?<!\\d)366|(?<!\\d)762(?!\\d)", INTERMED),
        // rimfire before handgun so ".22" never reads as something else
        r("(?<!\\d)22(lr|wmr|mag)?(?!\\d)", RIMFIRE),
        // PDW
        r("57x28|46x30", PDW),
        // magnum handguns
        r("(?<!\\d)357(?!\\d)|(?<!\\d)44(?!\\d)|44mag|50ae|(?<!\\d)454|500sw|(?<!\\d)41mag|10mm", REVOLVER),
        // everything else that looks like a pistol calibre
        r("9mm|9x19|9x18|9x21|(?<!\\d)45(?!\\d)|acp|(?<!\\d)38(?!\\d)|40sw|(?<!\\d)380|762x25|(?<!\\d)32(?!\\d)|25acp", HANDGUN),
    };

    /**
     * Picks a template from the ammo name. Returns null when nothing matched, so the caller can fall
     * back to the weapon's stats.
     */
    public static BallisticProfile fromAmmoName(String ammoKey) {
        if (ammoKey == null) return null;
        String key = normalise(ammoKey);
        for (Rule rule : RULES) {
            if (rule.pattern.matcher(key).find()) return rule.profile;
        }
        return null;
    }

    /**
     * Full resolution from ammo name and weapon stats.
     *
     * @param projectileCount weapon ProjectileCount (vanilla shotguns use this)
     * @param maxRange        weapon MaxRange in tiles
     * @param maxDamage       weapon MaxDamage
     * @param projectileSpread weapon ProjectileSpread (vanilla shotguns, native units)
     */
    public static BallisticProfile resolve(String ammoKey, int projectileCount, double maxRange, double maxDamage,
                                           double projectileSpread) {
        BallisticProfile base = fromAmmoName(ammoKey);
        if (base == null) {
            // Unknown calibre: decide from what the weapon script says.
            if (projectileCount > 1) base = BUCKSHOT;
            else if (maxRange >= 30 && maxDamage >= 2.5) base = FULLPOWER;
            else if (maxRange >= 20) base = INTERMED;
            else base = HANDGUN;
        }
        if (projectileCount > 1 && base.pelletCount <= 1) {
            base = BUCKSHOT; // a multi-projectile weapon always fires pellets, whatever the ammo is called
        }
        if (base.pelletCount > 1) {
            int pellets = projectileCount > 1 ? projectileCount : base.pelletCount;
            // Vanilla ProjectileSpread is 0.6 (pump) .. 2.0 (sawn-off). It is read as the cone half-angle
            // in degrees: a pump gives a ~0.3 m pattern at 15 m, a sawn-off ~1 m, close to real buckshot.
            double spread = projectileSpread > 0 ? Math.max(0.3, Math.min(8.0, projectileSpread)) : base.pelletSpreadDeg;
            base = new BallisticProfile(base.category, base.muzzleVelocity, base.drag, base.gravityScale, base.massGrams,
                pellets, spread, base.baseSpreadMrad, base.damageMultiplier, base.maxRange, base.lifetime,
                base.penetration, base.tracer);
        }
        return base;
    }

    /** "base:Bullets_9mm" -> "base:bullets9mm" ("x" kept for 7.62x39 style names). */
    static String normalise(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toLowerCase(s.charAt(i));
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == ':') b.append(c);
        }
        return b.toString();
    }

    private static Rule r(String regex, BallisticProfile p) { return new Rule(Pattern.compile(regex), p); }

    private static BallisticProfile p(String n, double v, double k, double g, double m, int pel, double spr,
                                      double base, double dmg, double rng, double life, int pen) {
        return new BallisticProfile(n, v, k, g, m, pel, spr, base, dmg, rng, life, pen, false);
    }

    private AmmoBallisticsResolver() {}
}
