package io.github.skystrike.shared.config;

/**
 * Door interaction and swing numbers (map plan §4).
 *
 * <p>Times are counted in <b>ticks</b>, and each side supplies its own tick: the server advances
 * the authoritative state machine with {@code SimulationClock.tick()}, and the client advances a
 * per-frame accumulator so the swing it draws lasts the same wall-clock time as the swing the
 * authority simulates. Nothing here is ever measured in wall-clock seconds on the wire, which is
 * what keeps the two clocks out of the protocol (plan §8 carries a tick, not a timestamp).
 *
 * <p>Every value in this class is <b>provisional</b>: the plan fixes the behaviour and the shape
 * of the door state machine but names no numbers, so these are the first numbers and a playtest
 * may move them. They are deliberately few.
 */
public final class DoorConfig {

    /**
     * How near a player's centre must be to a door's rectangle to use it, in world units.
     *
     * <p>Measured to the rectangle's nearest point, like a melee arc measures a block, so a tall
     * door is reachable from its whole height rather than from its middle. A player is 50 units
     * wide, so 72 units reaches slightly further than the body itself — enough that "walk up to
     * the door and press the key" reads the way a player expects.
     */
    public static final float USE_REACH = 72f;

    /** Ticks a panel takes to swing from shut to open (or back). 24 ticks = 0.4 s at 60 Hz. */
    public static final int SWING_TICKS = 24;

    /**
     * Ticks an open door waits before it swings shut again. 180 ticks = 3 s at 60 Hz.
     *
     * <p>Doors are manual to open and automatic to close: a match whose doors stayed open once
     * used would slowly dissolve into a map with no doors at all, and the plan's field list gives
     * a door a {@code holdSeconds} for exactly this. The hold is one configured value rather than
     * an authored per-door one — nothing in the acceptance checklist needs two doors with
     * different holds, and an attribute whose only consumer is a constant is a format change
     * before it is a feature.
     */
    public static final int HOLD_TICKS = 180;

    /**
     * How far beyond its own rectangle a panel's swing reaches, in world units.
     *
     * <p>The swing region is a door's rectangle grown by this much, and a body or a thrown object
     * inside it stops the swing. A door is thin (the shipped format's example is 24 units wide),
     * so without a margin a body could stand a hair outside the panel's box and be hit by it. The
     * number is small on purpose: it exists to cover the body's own width against the drawn panel,
     * not to reserve a room around the door.
     */
    public static final float SWING_CLEARANCE = 4f;

    private DoorConfig() {
    }
}
