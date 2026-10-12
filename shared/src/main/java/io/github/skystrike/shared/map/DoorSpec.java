package io.github.skystrike.shared.map;

import java.util.Objects;

/**
 * The authored description of one door: its id, who may open it, and (for a team door) which team
 * that is.
 *
 * <p>{@link #NONE} is the spec every block that is not a door carries, so "is this a door" is a
 * question about authored data rather than a second nullable reference inside {@link MapBlock}.
 * {@link MapBlock}'s constructor keeps the two honest: a {@link BlockKind#DOOR} block must carry a
 * real spec and every other kind must carry {@link #NONE}.
 *
 * <p><b>The id is the door's wire identity</b> (plan §8). Blocks travel by authored index because
 * both sides build the identical list from the same text; a door also has to be *named* while its
 * state changes, and an index reads badly in a map file and in a log line. So a door is authored
 * with an explicit id, the format refuses a duplicate, and the door-state packet carries that id.
 *
 * <p><b>A mirrored door is authored twice.</b> A door is a single object with a single id, and the
 * two sides of a mirrored pair are two doors, so {@link #reflection()} hands the reflected block
 * the next id: an authored {@code mirrored door ... id=7} becomes the doors 7 and 8. That is not a
 * hidden convention — {@code ArenaMapFormat} documents it, and the builder fails a map whose ids
 * collide, which is exactly what a hand-written file gets wrong.
 */
public record DoorSpec(int id, DoorAccess access, int teamIndex) {

    /** The spec of a block that is not a door. Its id 0 is not a valid authored door id. */
    public static final DoorSpec NONE = new DoorSpec(0, DoorAccess.ALL, 0);

    public DoorSpec {
        if (id < 0) {
            throw new IllegalArgumentException("door id must not be negative: " + id);
        }
        Objects.requireNonNull(access, "access");
        if (teamIndex < 0) {
            throw new IllegalArgumentException("door team index must not be negative: " + teamIndex);
        }
        if (access == DoorAccess.ALL && teamIndex != 0) {
            throw new IllegalArgumentException(
                "an ALL door has no team, but a team index was authored: " + teamIndex);
        }
    }

    /** A door anyone may open. */
    public static DoorSpec all(int id) {
        return new DoorSpec(id, DoorAccess.ALL, 0);
    }

    /** A door only {@code teamIndex} may open. */
    public static DoorSpec team(int id, int teamIndex) {
        return new DoorSpec(id, DoorAccess.TEAM, teamIndex);
    }

    /** True when this spec describes a real door rather than {@link #NONE}. */
    public boolean isDoor() {
        return id > 0;
    }

    /**
     * True when the player on {@code teamIndex} may open this door.
     *
     * <p>The comparison is a plain index equality rather than {@code Team.areAllies}, because a
     * door's authored team is a spawn side (0 or 1) and Neutral (2) can therefore never match one:
     * the neutral rule that makes two Neutrals strangers is already expressed by the values that
     * can reach this method. Keeping it an index also keeps {@code shared/model} out of
     * {@code shared/map}, which {@code shared/model} already imports for {@link Rect}.
     */
    public boolean allows(int teamIndex) {
        return access == DoorAccess.ALL || teamIndex == this.teamIndex;
    }

    /**
     * The spec of this door's reflection about a map's mirror axis.
     *
     * <p>A reflected door is a second door, not the same one seen from the other side, so it takes
     * the next id ({@link #NONE} stays {@link #NONE}: a block that is not a door has no pair).
     */
    public DoorSpec reflection() {
        return isDoor() ? new DoorSpec(id + 1, access, teamIndex) : NONE;
    }
}
