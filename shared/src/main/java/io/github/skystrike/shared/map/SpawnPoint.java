package io.github.skystrike.shared.map;

/**
 * A team spawn position in world units.
 *
 * <p>{@code teamIndex} is 0 for the left-hand team and 1 for the right-hand team; the richer
 * {@code shared/model/Team} type arrives with match flow in Phase 11.
 */
public record SpawnPoint(int teamIndex, float x, float y) {

    public SpawnPoint {
        if (teamIndex < 0) {
            throw new IllegalArgumentException("teamIndex must not be negative: " + teamIndex);
        }
    }

    /** Reflects this spawn about the vertical line {@code x = axis}, keeping the team index. */
    public SpawnPoint mirror(float axis) {
        return new SpawnPoint(teamIndex, 2f * axis - x, y);
    }
}
