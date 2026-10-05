package io.github.skystrike.server.player;

import io.github.skystrike.shared.config.PlayerConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.map.MapQueries;
import io.github.skystrike.shared.map.SpawnPoint;
import io.github.skystrike.shared.model.Player;
import java.util.List;

/**
 * Places players at their team's designated spawn points and resets dynamic state.
 */
public final class SpawnService {

    private final ArenaMap arena;

    public SpawnService(ArenaMap arena) {
        this.arena = arena;
    }

    /**
     * Spawns or respawns {@code player} at their team's spawn location.
     */
    public void spawn(Player player) {
        List<SpawnPoint> spawns = arena.spawns();
        int spawnIdx = Math.abs(player.teamIndex) % Math.max(1, spawns.size());
        SpawnPoint spawn = spawns.get(spawnIdx);

        float surfaceY = MapQueries.surfaceBelow(arena, spawn.x(), spawn.y());
        player.x = spawn.x();
        player.y = surfaceY;
        player.vx = 0f;
        player.vy = 0f;
        player.rotation = 0f;
        player.angularVelocity = 0f;
        player.health = PlayerConfig.MAX_HEALTH;
        player.fuel = PlayerConfig.MAX_FUEL;
        player.grounded = true;
        player.crouched = false;
        player.jetpacking = false;
        player.coyoteTimer = PlayerConfig.COYOTE_TIME;
        // Face toward arena center on spawn
        player.aimAngle = spawnIdx == 0 ? 0f : 180f;
    }

    /** Selects the team with fewer active players to maintain balance. */
    public int selectBalancedTeam(int team0Count, int team1Count) {
        return team0Count <= team1Count ? 0 : 1;
    }
}
