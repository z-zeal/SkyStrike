package io.github.skystrike.server.world;

import io.github.skystrike.shared.config.DoorConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.map.MapBlock;
import io.github.skystrike.shared.map.MapDoorState;
import io.github.skystrike.shared.map.MapQueries;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.model.ThrownUtility;
import java.util.List;

/**
 * The server authority for doors (map plan §4, D3): which press does what, and when time moves
 * a door, against the true occupancy of the world.
 *
 * <p>The rules the authority enforces are the shared ones: {@link MapQueries#doorAt} picks the
 * door a press means, {@link MapDoorState#requestUse} decides it, and {@link
 * MapDoorState#advance} ages it — the same machine a client predicts with, so the two cannot
 * disagree about what a press means, only about whether it has arrived. What only the server can
 * contribute is the occupancy those rules ask about. It is assembled per call from everything
 * physical that is not terrain:
 * <ul>
 *   <li><b>Every live player body</b> counts by its hitbox, the presser's own included — plan
 *       §4's "cannot open it while standing in the swing" is exactly that: a press from inside
 *       the swing region is refused, and a shutting door can never close through someone.</li>
 *   <li><b>Every thrown utility</b> counts by its position point — a live projectile physically
 *       occupies where it is this tick; plopped gadgets are handled by their own system before
 *       they ever rest inside a doorway.</li>
 * </ul>
 *
 * <p>One policy deliberately lives here rather than in the shared machine: a press is consumed
 * <b>every tick, refused or not</b>. The refusal answer is a notice to the presser, not a banked
 * retry — a door must never remember an intent the world has changed its answer to, so the
 * latch in {@code PlayerSession} is one-shot and this system never asks it to wait.
 */
public final class DoorSystem {

    /**
     * What one press resolved to, with the door it resolved against, so the caller can answer a
     * refusal about <i>that</i> door (warning text, plus the authoritative record that corrects
     * whatever the presser predicted).
     */
    public record PressResult(MapBlock door, MapDoorState.Outcome outcome) {
    }

    private final ArenaMap authored;
    private final MapDoorState doors;

    /**
     * @param authored the authored map, where doors live; doors never move to the live map, so
     *                 resolution never reads the damage mirror
     * @param doors the shared machine behind this server's {@code MapDamageState}: both presses
     *              and the tick clock write into it, and the wire diff reads it back
     */
    public DoorSystem(ArenaMap authored, MapDoorState doors) {
        this.authored = authored;
        this.doors = doors;
    }

    /** The shared door machine this system writes into, exposed for the wire diff and tests. */
    public MapDoorState doors() {
        return doors;
    }

    /**
     * Resolves one use press from {@code player} against the authored map and the true
     * occupancy.
     *
     * @param playerStates the players alive in the world this tick, presser included — plan §4's
     *                     no-open-while-standing-in-the-swing rule is exactly the presser's own
     *                     body counting too
     * @param thrownUtilities every utility currently in flight, from {@code UtilitySystem.active()}
     * @return the door and its outcome, or null when the press never reached a decision — the
     *         player cannot act (dead, stunned or piloting, consumed like any dead-tick edge) or
     *         no door is in reach. Only an outcome is worth a notice: a refusal of a legitimate
     *         use tells the presser something a silent miss does not need to.
     */
    public PressResult usePressed(Player player, List<Player> playerStates,
        List<ThrownUtility> thrownUtilities) {
        if (player == null || !player.alive || player.isSlowed() || player.isSurveillanceLocked()) {
            return null;
        }
        MapBlock door = MapQueries.doorAt(
            authored, player.centerX(), player.centerY(), DoorConfig.USE_REACH);
        if (door == null) {
            return null;
        }
        return new PressResult(
            door, doors.requestUse(door, player.teamIndex, occupancy(playerStates, thrownUtilities)));
    }

    /**
     * Ages every door one tick against the true occupancy. The clock half of the machine: the
     * swing ends, an open door shuts once its hold elapses and the doorway is clear, and a
     * swing that would finish on someone swings back instead.
     *
     * @return the doors that changed this tick, in authored order (the machine's own report —
     *         the wire diff reads the states directly, this is for logging and tests)
     */
    public List<MapBlock> advance(long nowTick, List<Player> playerStates,
        List<ThrownUtility> thrownUtilities) {
        return doors.advance(nowTick, occupancy(playerStates, thrownUtilities));
    }

    /**
     * The occupancy half of the door rules, from the authoritative player list plus whatever is
     * currently thrown. Assembled per call because the world moves every tick: bodies are
     * hitboxes (plan §4 counts anything the swing would pass through), thrown utilities are
     * points, and dead players occupy nothing.
     */
    private MapDoorState.Occupancy occupancy(List<Player> playerStates,
        List<ThrownUtility> thrownUtilities) {
        return swingRegion -> {
            for (Player candidate : playerStates) {
                if (candidate.alive && swingRegion.overlaps(candidate.hitbox())) {
                    return true;
                }
            }
            for (ThrownUtility utility : thrownUtilities) {
                if (swingRegion.contains(utility.x, utility.y)) {
                    return true;
                }
            }
            return false;
        };
    }
}
