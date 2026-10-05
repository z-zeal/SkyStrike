package io.github.skystrike.shared.weapons;

/**
 * How a trigger pull turns into rounds (mechanics §4.5).
 *
 * <p>{@link #AUTO} is the only mode that keeps firing while the trigger is held; every other mode
 * needs a fresh press, which is why the server tracks a trigger edge per player rather than just
 * the held flag.
 */
public enum FireMode {

    /** Fires continuously while held, at the weapon's rate. */
    AUTO(false),

    /** One round per press, gated by the weapon's cooldown. */
    SEMI(true),

    /** One round per press with a long cycle: the bolt has to be worked. */
    BOLT(true),

    /** Three rounds in one instant per press, in a fixed angular pattern. */
    BURST(true),

    /** One shell of pellets per press, spread evenly across the cone. */
    SHOTGUN(true);

    private final boolean requiresTriggerRelease;

    FireMode(boolean requiresTriggerRelease) {
        this.requiresTriggerRelease = requiresTriggerRelease;
    }

    /** True when holding the trigger down is not enough — the player must release and press. */
    public boolean requiresTriggerRelease() {
        return requiresTriggerRelease;
    }

    /** True when one trigger event produces several rounds at once. */
    public boolean isVolley() {
        return this == BURST || this == SHOTGUN;
    }
}
