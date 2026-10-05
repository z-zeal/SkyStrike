package io.github.skystrike.shared.weapons;

/**
 * How a trigger pull turns into rounds (mechanics §4.5).
 *
 * <p>{@link #AUTO} is the only mode that keeps firing while the trigger is held; every other mode
 * needs a fresh press, which is why the server tracks a trigger edge per player rather than just
 * the held flag.
 *
 * <p>Pellet shells are <b>not</b> a mode: whether one trigger event launches a cloud is the
 * weapon's {@link WeaponDefinition#pelletCount()}, so the catalog's pump, break-action, semi
 * and even automatic shotguns all keep their own trigger behaviour.
 */
public enum FireMode {

    /** Fires continuously while held, at the weapon's rate. */
    AUTO(false),

    /** One round per press, gated by the weapon's cooldown. */
    SEMI(true),

    /** One round per press with a long cycle: the bolt has to be worked. */
    BOLT(true),

    /** One shell per press with a long cycle: the slide has to be pumped. */
    PUMP(true),

    /** One shell per press from a hinged pair of barrels; the reload is the whole magazine. */
    BREAK(true),

    /** Three rounds in one instant per press, in a fixed angular pattern. */
    BURST(true);

    private final boolean requiresTriggerRelease;

    FireMode(boolean requiresTriggerRelease) {
        this.requiresTriggerRelease = requiresTriggerRelease;
    }

    /** True when holding the trigger down is not enough — the player must release and press. */
    public boolean requiresTriggerRelease() {
        return requiresTriggerRelease;
    }
}
