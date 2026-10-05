package io.github.skystrike.server.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.CombatConfig;
import io.github.skystrike.shared.config.PlayerConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.model.Player;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Death is a state, not a removal: the player keeps their id and their score and comes back
 * after the timer. These tests pin the timer and the reset.
 */
class RespawnServiceTest {

    private static final float EPSILON = 1e-3f;

    private RespawnService respawn;
    private Player player;

    @BeforeEach
    void setUp() {
        respawn = new RespawnService(new SpawnService(ArenaMap.standard()));
        player = new Player(1, "Nova", 0, 1500f, 700f);
    }

    @Test
    @DisplayName("the timer counts down and the player returns when it reaches zero")
    void respawnAfterTheDelay() {
        respawn.kill(player);
        assertFalse(player.alive);
        assertEquals(CombatConfig.RESPAWN_DELAY_SECONDS, player.respawnTimer, EPSILON);

        // Just short of the delay: still dead.
        for (int tick = 0; tick < 179; tick++) {
            respawn.update(1f / 60f, List.of(player));
        }
        assertFalse(player.alive, "still dead at " + player.respawnTimer + "s remaining");

        respawn.update(1f / 60f, List.of(player));
        assertTrue(player.alive, "the player must come back");
        assertEquals(0f, player.respawnTimer, EPSILON);
        assertEquals(PlayerConfig.MAX_HEALTH, player.health, EPSILON);
        assertEquals(PlayerConfig.MAX_FUEL, player.fuel, EPSILON);
    }

    @Test
    @DisplayName("respawning moves the player to their team spawn and clears combat state")
    void respawnResetsState() {
        player.x = 2000f;
        player.y = 900f;
        player.vx = 300f;
        player.vy = -500f;
        player.spread = 12f;
        player.gunKick = 20f;
        player.kills = 4;
        player.deaths = 7;
        respawn.kill(player);

        respawn.respawn(player);

        assertTrue(player.alive);
        assertEquals(0f, player.vx, EPSILON);
        assertEquals(0f, player.vy, EPSILON);
        assertEquals(0f, player.spread, EPSILON);
        assertEquals(0f, player.gunKick, EPSILON);
        assertTrue(player.x < 1000f, "team 0 spawns on the left, not where it died: " + player.x);

        // Score survives death.
        assertEquals(4, player.kills);
        assertEquals(7, player.deaths);
    }

    @Test
    @DisplayName("living players are left alone")
    void livingPlayersAreUntouched() {
        player.x = 2000f;
        int respawned = respawn.update(1f / 60f, List.of(player));

        assertEquals(0, respawned);
        assertEquals(2000f, player.x, EPSILON);
        assertEquals(0f, player.respawnTimer, EPSILON);
    }

    @Test
    @DisplayName("several dead players all come back, and the count is reported")
    void multiplePlayersRespawnTogether() {
        Player other = new Player(2, "Rook", 1, 1500f, 700f);
        respawn.kill(player);
        respawn.kill(other);

        assertEquals(0, respawn.update(1f, List.of(player, other)), "one second is not enough");
        assertEquals(2, respawn.update(CombatConfig.RESPAWN_DELAY_SECONDS, List.of(player, other)));
        assertTrue(player.alive);
        assertTrue(other.alive);
    }

    @Test
    @DisplayName("killing a corpse again does not restart its timer")
    void killingTwiceDoesNotExtendTheTimer() {
        respawn.kill(player);
        respawn.update(1f, List.of(player));
        float remaining = player.respawnTimer;

        respawn.kill(player);
        assertEquals(remaining, player.respawnTimer, EPSILON);
    }

    @Test
    @DisplayName("degenerate inputs are ignored")
    void degenerateInputs() {
        assertEquals(0, respawn.update(1f, null));
        assertEquals(0, respawn.update(0f, List.of(player)));
        respawn.respawn(null);
        respawn.kill(null);
    }
}
