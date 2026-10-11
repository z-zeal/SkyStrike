package io.github.skystrike.shared.map;

/**
 * How a block's kind responds to damage — the destruction column of the shared {@link BlockRules}
 * table.
 *
 * <p>It is deliberately separate from the numbers in {@link MaterialTable}: the rule says whether
 * a block has a durability pool at all, and the material says how large that pool is and which
 * sources can reach it. A wall is permanent because its row says so, not because stone happens to
 * be tough — which is what lets a scenario map author an energy wall a frag can open without any
 * consumer learning a new kind.
 */
public enum DamageRule {
    /** Permanent geometry: damage is refused outright and no per-block state is recorded. */
    INDESTRUCTIBLE,

    /**
     * Accumulates damage from the sources its material accepts and leaves the map at zero.
     *
     * <p>This is the only rule whose blocks carry a durability pool, and therefore the only one a
     * renderer can draw a crack state for.
     */
    DURABILITY,

    /**
     * One damaging hit destroys it: no pool, no partial state, nothing half-broken to collide
     * with. Glass is the shipped example, and its whole reason is that a window must not behave
     * like a wall.
     */
    SHATTER
}
