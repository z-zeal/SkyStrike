package io.github.skystrike.shared.map;

/**
 * The runtime state of one door (plan D3: {@code CLOSED}/{@code OPENING}/{@code OPEN}/{@code
 * SHUTTING} with the tick it entered that state).
 *
 * <p><b>Collision, sight and bullets read this, never the animation.</b> {@link #blocks()} is the
 * whole contract a consumer needs, and it is a property of the state rather than of the pose: a
 * door nobody can walk through is one whose panel is not out of the way yet. So {@link #OPEN} does
 * not block and the other three do — a swinging panel is across the doorway for part of its swing,
 * which is exactly the part a body or a round would meet it in, and the pose is only ever read by
 * the renderer, from {@link MapDoorState#swingFraction(MapBlock)}.
 *
 * <p><b>Only a fully open door passes.</b> The alternative rule — a door that stops blocking the
 * moment it starts to shut — reads better for a body that happens to be in the doorway, but it
 * makes a doorway crossable while a panel is coming down into it, and the door then has to catch
 * whoever is standing there. This way the collision answer never depends on how far a swing has
 * got: a door starts blocking again the instant it starts shutting, so nothing can step into the
 * gap, and {@link MapDoorState#advance} refuses to finish a swing that something occupies anyway —
 * a door can end up open when it is blocked, never shut around a body.
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

    /** Swinging shut: blocking again, on its way back to {@link #CLOSED}. */
    SHUTTING(true);

    private final boolean blocks;

    DoorState(boolean blocks) {
        this.blocks = blocks;
    }

    /**
     * True while this door is in the way.
     *
     * <p>True for everything but {@link #OPEN}: a half-open panel is a panel you would walk into,
     * a closing one is a panel about to be in the way, and the state machine keeps a body out of
     * the region it is closing through ({@link MapDoorState#advance}).
     */
    public boolean blocks() {
        return blocks;
    }

    /**
     * Decodes a wire ordinal defensively (protocol 15): an unknown value — from a mismatched
     * build that is only possible through the version gate — fails shut to {@link #CLOSED}, the
     * blocking state, rather than walking a body through a panel or swinging a door it cannot
     * name.
     */
    public static DoorState fromOrdinal(int ordinal) {
        DoorState[] states = values();
        if (ordinal < 0 || ordinal >= states.length) {
            return CLOSED;
        }
        return states[ordinal];
    }
}
