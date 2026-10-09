package io.github.skystrike.shared.model;

/**
 * Team allegiances (mechanics §10).
 *
 * <p>Team A is blue, Team B is red, Neutral fights everyone and is untinted.
 */
public enum Team {
    TEAM_A(0, "Team A"),
    TEAM_B(1, "Team B"),
    NEUTRAL(2, "Neutral");

    private final int index;
    private final String displayName;

    Team(int index, String displayName) {
        this.index = index;
        this.displayName = displayName;
    }

    public int index() {
        return index;
    }

    public String displayName() {
        return displayName;
    }

    public static Team fromIndex(int index) {
        return switch (index) {
            case 0 -> TEAM_A;
            case 1 -> TEAM_B;
            default -> NEUTRAL;
        };
    }

    /**
     * Whether two team indices are the same allegiance — the one spelling of "on the same side",
     * shared by friendly fire, the kill feed's {@code [FF]} tag and the minimap's blip colours.
     *
     * <p>Neutral is outside the team system entirely (mechanics §10): a Neutral player has no
     * allies, not even another Neutral, so two Neutrals are never on the same side. That is why
     * this is not simply {@code a == b}.
     *
     * <p>The index compared against is {@link #NEUTRAL}'s own, which is the value
     * {@code CombatConfig.NEUTRAL_TEAM_INDEX} quotes; {@code TeamTest} asserts the two agree, so
     * neither can be renumbered without the other failing.
     */
    public static boolean areAllies(int teamIndexA, int teamIndexB) {
        return teamIndexA == teamIndexB && teamIndexA != NEUTRAL.index();
    }
}
