package t3zedballistics.api;

import zombie.characters.IsoGameCharacter;

/**
 * Lets an add-on remove body parts from a character's hit volumes (added in 1.3.0), for example a
 * dismemberment mod whose zombies have lost a forearm: rounds must then pass where the forearm was.
 * Register with {@link BallisticsAPI#setBodyPartFilter(BodyPartFilter)}.
 *
 * Called on the game thread every time True Ballistics builds the hit capsules of a character near a
 * round's path, so it must be fast and allocation-free.
 */
public interface BodyPartFilter {
    /**
     * Bit mask over zombie.characters.BodyDamage.BodyPartType ordinals (bit n = ordinal n, 0..16) of
     * parts that no longer exist on c. 0 = everything is there.
     */
    long missingBodyPartTypes(IsoGameCharacter c);
}
