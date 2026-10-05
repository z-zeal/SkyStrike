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
}
