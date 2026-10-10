package io.github.skystrike.shared.audio;

/**
 * How much a sound deserves a voice when the pool is full (roadmap Phase 9).
 *
 * <p>The pool is bounded, and a match can ask for far more sounds than a phone will mix at once:
 * five players auto-firing into a wall generate more impact ticks per second than any backend
 * wants. When the cap is reached something has to give, and the ranking of who gives first is a
 * design decision, not an implementation detail — so it is written down here and attached to each
 * catalogue entry.
 *
 * <p>The default rule the client applies: a request that arrives at a full pool plays only when it
 * outranks the weakest voice already holding a slot, and it takes that voice's place. A casing
 * tinkle never steals from an explosion; an explosion always steals from a casing. The mixer has
 * one narrow opt-in for rapid fresh attacks: a new marked attack may roll over an older marked
 * peer at the same rank, without changing this global priority ordering.
 */
public enum SoundPriority {

    /**
     * Never the sound that gets dropped: the player is being hit by this.
     *
     * <p>Frag and impact detonations and the flashbang all sit here. A missing explosion sound in
     * a shooter reads as a bug, because the player has just been killed by something they did not
     * hear.
     */
    CRITICAL(3),

    /** Fight-changing information the player is expected to react to: other players' fire. */
    HIGH(2),

    /** The ordinary world: impacts, smoke, poison, weapon handling. Dropped under real spam. */
    NORMAL(1),

    /**
     * Flavour that must yield first: ejected casings, the ignition pop of a molotov's spread
     * zones, and anything else duplicated many times per event.
     */
    LOW(0);

    private final int rank;

    SoundPriority(int rank) {
        this.rank = rank;
    }

    /** Higher wins. Only meaningful for comparison, never for arithmetic. */
    public int rank() {
        return rank;
    }

    /** True when this priority may take a voice from {@code other}. Ties never steal. */
    public boolean outranks(SoundPriority other) {
        return other != null && rank > other.rank();
    }

    /** True when this priority is at least {@code other}'s; ties are allowed to coexist. */
    public boolean atLeast(SoundPriority other) {
        return other == null || rank >= other.rank();
    }
}
