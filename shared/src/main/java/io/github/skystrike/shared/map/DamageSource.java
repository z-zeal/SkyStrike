package io.github.skystrike.shared.map;

/**
 * What hurt a {@link MapBlock}.
 *
 * <p>A source is not a damage amount. Each caller measures its own event — a round's damage after
 * falloff, an explosion's linear falloff at the block's nearest point, a swing's damage — and this
 * enum says which column of the shared {@link MaterialTable} decides how much of it the surface
 * accepts. That is what keeps "stone shrugs off bullets but not a frag" a table row rather than a
 * branch repeated in the bullet, blast and melee paths.
 */
public enum DamageSource {
    /** A round in flight. */
    BULLET,

    /** An explosion reaching the block's nearest point. */
    BLAST,

    /** A melee swing. */
    MELEE
}
