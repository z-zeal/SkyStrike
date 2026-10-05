package io.github.skystrike.server.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.PlayerConfig;
import io.github.skystrike.shared.config.WorldConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.model.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SpawnServiceTest {

    private final ArenaMap arena = ArenaMap.standard();
    private final SpawnService service = new SpawnService(arena);

    @Test
    @DisplayName("spawns Team A at (240, 100) facing right and Team B at (2760, 100) facing left")
    void teamSpawnsArePlacedCorrectly() {
        Player teamAPlayer = new Player(1, "A", 0, 0f, 0f);
        Player teamBPlayer = new Player(2, "B", 1, 0f, 0f);

        service.spawn(teamAPlayer);
        service.spawn(teamBPlayer);

        assertEquals(240f, teamAPlayer.x, 0f);
        assertEquals(WorldConfig.GROUND_HEIGHT, teamAPlayer.y, 0f);
        assertEquals(0f, teamAPlayer.aimAngle, 0f);
        assertEquals(PlayerConfig.MAX_HEALTH, teamAPlayer.health);
        assertEquals(PlayerConfig.MAX_FUEL, teamAPlayer.fuel);
        assertTrue(teamAPlayer.grounded);

        assertEquals(2760f, teamBPlayer.x, 0f);
        assertEquals(WorldConfig.GROUND_HEIGHT, teamBPlayer.y, 0f);
        assertEquals(180f, teamBPlayer.aimAngle, 0f);
        assertTrue(teamBPlayer.grounded);
    }

    @Test
    void teamBalancingBalancesPicks() {
        assertEquals(0, service.selectBalancedTeam(0, 0));
        assertEquals(1, service.selectBalancedTeam(1, 0));
        assertEquals(0, service.selectBalancedTeam(1, 2));
    }
}
