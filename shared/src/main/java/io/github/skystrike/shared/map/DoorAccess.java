package io.github.skystrike.shared.map;

/**
 * Who may open a door (map plan §4: {@code access} is a {@code MapBlock}'s authored door rule).
 *
 * <p>Two values, because the plan names two: {@link #ALL} for a door anyone may use, and
 * {@link #TEAM} for a door that answers to one spawn side's team index (see
 * {@link DoorSpec#allows(int)} for the resolution itself).
 */
public enum DoorAccess {
    /** Anyone who can reach the door may open it. */
    ALL,

    /** Only a player on the door's own team may open it. */
    TEAM
}
