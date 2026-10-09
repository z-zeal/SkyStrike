package io.github.skystrike.shared.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.CombatConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The allegiance enum, and the one spelling of "on the same side" that friendly fire, the kill
 * feed's {@code [FF]} tag and the minimap's blip colours all call.
 */
class TeamTest {

    @Test
    @DisplayName("team indices round-trip and unknown indices read as Neutral")
    void indicesRoundTrip() {
        for (Team team : Team.values()) {
            assertEquals(team, Team.fromIndex(team.index()));
        }
        assertEquals(Team.NEUTRAL, Team.fromIndex(99), "an out-of-range index is Neutral, not a crash");
    }

    @Test
    @DisplayName("the same real team is allied with itself")
    void sameTeamIsAllied() {
        assertTrue(Team.areAllies(Team.TEAM_A.index(), Team.TEAM_A.index()));
        assertTrue(Team.areAllies(Team.TEAM_B.index(), Team.TEAM_B.index()));
    }

    @Test
    @DisplayName("opposing teams are never allied")
    void opposingTeamsAreNotAllied() {
        assertFalse(Team.areAllies(Team.TEAM_A.index(), Team.TEAM_B.index()));
        assertFalse(Team.areAllies(Team.TEAM_B.index(), Team.TEAM_A.index()));
        assertFalse(Team.areAllies(Team.TEAM_A.index(), Team.NEUTRAL.index()));
        assertFalse(Team.areAllies(Team.NEUTRAL.index(), Team.TEAM_B.index()));
    }

    @Test
    @DisplayName("Neutral has no allies, not even another Neutral (mechanics §10)")
    void neutralHasNoAllies() {
        assertFalse(Team.areAllies(Team.NEUTRAL.index(), Team.NEUTRAL.index()),
            "two Neutrals fight each other, so they are not on the same side");
    }

    @Test
    @DisplayName("the neutral index the combat config quotes is the enum's own")
    void neutralIndexAgreesWithCombatConfig() {
        // DamageService and KillFeedService used to compare against CombatConfig.NEUTRAL_TEAM_INDEX
        // directly. They now call Team.areAllies, which compares against the enum; this is the
        // guard that keeps the two spellings of "Neutral is index 2" from drifting apart.
        assertEquals(Team.NEUTRAL.index(), CombatConfig.NEUTRAL_TEAM_INDEX);
    }
}
