package io.github.skystrike.shared.map;

/**
 * The runtime state of one door (plan D3: {@code CLOSED}/{@code OPENING}/{@code OPEN}/{@code
 * SHUTTING} with the tick it entered that state).
 *
 * <p><b>Collision, sight and bullets read this, never the animation.</b> {@link #blocks()} is the
 * whole contract a consumer needs, and it is a property of the state rather than of the pose: a
 * door nobody can walk through is one whose panel is not out of the way yet. That is why
 * {@code OPENING} still blocks — the panel is mid-swing across the doorway — while {@code OPEN}
 * does not, and why a shutting door is passable until it is actually shut: the swing is drawn from
 * {@link MapDoorState#swingFraction(MapBlock)}, which is presentation only.
 *
 * <p>{@link BlockKind#DOOR}'s {@link BlockRules} row keeps saying that a door blocks movement,
 * sight and bullets. This enum says which doors are currently answering that row.
 */
public enum DoorState {

    /** Shut and blocking, the authored state of every door at the start of a round. */
    CLOSED(true),

    /** Swinging open: still blocking, because the panel is still in the doorway. */
    OPENING(true),

    /** Fully open and out of the way: no movement, sight or bullet blocking at all. */
    OPEN(false),

    /** Swinging shut: already passable, and it will not finish on top of a body (see below). */
    SHUTTING(true);

    private final boolean blocks;

    DoorState(boolean blocks) {
        this.blocks = blocks;
    }

    /**
     * True while this door is in the way.
     *
     * <p>A door is passable only when it is fully {@link #OPEN}: a half-open panel is a panel you
     * would walk into. A door that is {@link #SHUTTING} is passable, because the alternative — a
     * closing door that traps a body inside itself — is worse than a doorway that can be crossed
     * while the panel comes down; the state machine never lets a door finish closing on a body
     * ({@link MapDoorState#advance}).
     */
    public boolean blocks() {
        return blocks;
    }
}
