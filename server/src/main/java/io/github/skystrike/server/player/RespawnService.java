package io.github.skystrike.server.player;

import io.github.skystrike.shared.config.CombatConfig;
import io.github.skystrike.shared.gadget.SurveillanceView;
import io.github.skystrike.shared.model.Player;
import java.util.Collection;
import java.util.function.Consumer;

/**
 * Counts dead players back in.
 *
 * <p>Death is a state, not a removal: the player keeps their id, their score and their place in
 * the snapshot, and simply stops being a valid target until the timer runs out. Placement and
 * state reset belong to {@link SpawnService}, so respawning is "wait, then spawn" and there is
 * only ever one piece of code that knows where a player starts.
 */
public final class RespawnService {

    /**
     * Subtracting 1/60 from 3.0 sixty times a second does not land on exactly zero — float
     * rounding leaves a few hundred-millionths behind, which would hold a player dead for one
     * extra tick. The timer is considered spent once it is within this much of zero.
     */
    private static final float TIMER_EPSILON = 1e-4f;

    private final SpawnService spawnService;
    private final float respawnDelaySeconds;

    public RespawnService(SpawnService spawnService) {
        this(spawnService, CombatConfig.RESPAWN_DELAY_SECONDS);
    }

    public RespawnService(SpawnService spawnService, float respawnDelaySeconds) {
        this.spawnService = spawnService;
        this.respawnDelaySeconds = Math.max(0f, respawnDelaySeconds);
    }

    /** Ticks every dead player's timer and respawns the ones that reach zero. */
    public int update(float dt, Collection<Player> players) {
        return update(dt, players, null);
    }

    /**
     * As {@link #update(float, Collection)}, with {@code onRespawn} fired for each player the
     * moment they come back — the hook the loadout reset hangs off, so a respawned player gets
     * their requested composition before the next tick runs.
     */
    public int update(float dt, Collection<Player> players, Consumer<Player> onRespawn) {
        if (players == null || dt <= 0f) {
            return 0;
        }
        int respawned = 0;
        for (Player player : players) {
            if (player == null || player.alive) {
                continue;
            }
            player.respawnTimer -= dt;
            if (player.respawnTimer <= TIMER_EPSILON) {
                respawn(player);
                if (onRespawn != null) {
                    onRespawn.accept(player);
                }
                respawned++;
            }
        }
        return respawned;
    }

    /**
     * Immediately returns a player to their spawn with full health and fuel — and the loadout
     * reset the mechanics plan demands (§10): full magazines, no reload in flight, hands on the
     * first real weapon. The surveillance view is per-life state too: a respawn lands on the
     * player's own eyes, never mid-pilot. Composition changes requested mid-life are applied by
     * the {@code onRespawn} hook right after this.
     */
    public void respawn(Player player) {
        if (player == null) {
            return;
        }
        player.respawnTimer = 0f;
        player.alive = true;
        player.spread = 0f;
        player.gunKick = 0f;
        player.surveillanceView = SurveillanceView.SELF.ordinal();
        player.clearStatusEffects();
        if (player.loadout != null) {
            player.loadout.resetForRespawn();
        }
        spawnService.spawn(player);
    }

    /** Marks a player dead and starts their timer. Damage normally does this itself. */
    public void kill(Player player) {
        if (player == null || !player.alive) {
            return;
        }
        player.alive = false;
        player.health = 0f;
        player.respawnTimer = respawnDelaySeconds;
    }

    public float respawnDelaySeconds() {
        return respawnDelaySeconds;
    }
}
