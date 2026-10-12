package io.github.skystrike.gameplay;

import io.github.skystrike.input.InputRouter;
import io.github.skystrike.input.KeyBindings;
import io.github.skystrike.shared.config.DoorConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.map.MapBlock;
import io.github.skystrike.shared.map.MapDamageState;
import io.github.skystrike.shared.map.MapDoorState;
import io.github.skystrike.shared.map.MapQueries;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.net.c2s.PacketPlayerInput;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The use key's client half (map plan §4, D3): poll the key, resolve which door the player
 * means, predict the answer locally, and carry the press until the server acknowledges it.
 *
 * <p>One press does three things here, modelled exactly on {@link LoadoutController}'s press
 * lifecycle:
 * <ol>
 *   <li><b>Resolve.</b> The door comes from the shared {@link MapQueries#doorAt} rule — the
 *       nearest authored door within {@link DoorConfig#USE_REACH} of the player's centre — so
 *       the client and the server pick the same door from the same world; only the position
 *       they consult differs (predicted vs authoritative).</li>
 *   <li><b>Predict.</b> The shared state machine answers the press immediately
 *       ({@link MapDoorState#requestUse}), knowing only that the presser's own body might stand
 *       in the way. A swing the authority agrees with then starts on the tick the key went down
 *       instead of a round trip later.</li>
 *   <li><b>Retransmit.</b> The edge rides every packet with its birth sequence until the
 *       server acknowledges it, so UDP loss cannot eat a door use and duplication cannot apply
 *       one twice.</li>
 * </ol>
 *
 * <p>A press is carried even when the prediction refuses it, and a predicted refusal is never
 * shown: the authority is the only voice a refusal is allowed to have (the machine roots this
 * in the resolution rule itself — the client cannot know who else stands in the swing). If the
 * server refuses, it says so with its own warning and, where the door record channel applies,
 * an authoritative correction of whatever was predicted.
 *
 * <p>Nothing here is replayed into a snapshotted player, because door state does not live on
 * the player: it lives on the shared damage state, and reconciliation with the authority is the
 * door record channel's whole job. What this controller owns is only the edge's safe delivery.
 */
public final class DoorUseController {

    /**
     * Presses waiting for their acknowledgements, capped like the loadout channels: beyond this
     * the oldest are dropped and the authoritative door record repairs the view. Four cover any
     * human burst inside one round trip.
     */
    private static final int MAX_OUTSTANDING_PRESSES = 4;

    /**
     * One press carried on the wire. A birth sequence of {@code -1} means "not yet sent": it is
     * assigned by {@link #stampPacket} from the first packet that carries the press, exactly like
     * the loadout presses.
     */
    private record PendingPress(long birthSequence) {
    }

    private final KeyBindings bindings;
    private final InputRouter router;
    private final ArenaMap arena;
    private final MapDoorState doorState;

    private final Deque<PendingPress> outstanding = new ArrayDeque<>();

    /**
     * @param arena the authored map the shared resolution rule reads; doors live here, never in
     *              the live map, so an open door remains resolvable
     * @param doorState the shared door machine the press is predicted against
     */
    public DoorUseController(KeyBindings bindings, InputRouter router, ArenaMap arena,
        MapDamageState damageState) {
        this.bindings = bindings;
        this.router = router;
        this.arena = arena;
        this.doorState = damageState.doors();
    }

    /**
     * Polls one frame of use-key input against the predicted local player. Call once per frame,
     * before the input packet is sampled, so a press this frame rides this frame's packet.
     */
    public void update(Player localPlayer) {
        if (!router.isGameplayActive()) {
            return;
        }
        if (!bindings.isUseJustPressed()) {
            return;
        }
        if (localPlayer == null || !localPlayer.alive || localPlayer.isSlowed()
            || localPlayer.isSurveillanceLocked()) {
            // A piloting view owns the whole keyboard; a stunned or dead player simply cannot act.
            return;
        }
        MapBlock door = MapQueries.doorAt(
            arena, localPlayer.centerX(), localPlayer.centerY(), DoorConfig.USE_REACH);
        if (door == null) {
            return;
        }
        // The prediction knows only the presser's own body; the authority answers with the true
        // occupancy and its record is the correction of whatever was predicted here.
        doorState.requestUse(door, localPlayer.teamIndex,
            swingRegion -> swingRegion.overlaps(localPlayer.hitbox()));
        if (outstanding.size() >= MAX_OUTSTANDING_PRESSES) {
            outstanding.pollFirst();
        }
        outstanding.addLast(new PendingPress(-1L));
    }

    /**
     * Puts the oldest unacknowledged press on the packet about to be sent — every packet, until
     * the server acknowledges it, so UDP loss cannot eat a use. Same contract as
     * {@link LoadoutController#stampPacket}.
     */
    public void stampPacket(PacketPlayerInput packet) {
        packet.usePress = PacketPlayerInput.NO_USE_PRESS;
        packet.usePressSeq = -1L;
        PendingPress oldest = outstanding.peekFirst();
        if (oldest != null) {
            long birth = oldest.birthSequence();
            if (birth < 0L) {
                birth = packet.sequence;
                outstanding.pollFirst();
                outstanding.addFirst(new PendingPress(birth));
            }
            packet.usePress = PacketPlayerInput.USE_PRESS;
            packet.usePressSeq = birth;
        }
    }

    /**
     * Retires the presses the server has processed. Deaths and stuns clear the queue instead of
     * draining it: the authority consumed and rejected those edges, like the loadout channels.
     */
    public void onAuthoritativePlayer(Player authoritative) {
        if (authoritative == null) {
            return;
        }
        long acked = authoritative.lastProcessedInputSequence;
        while (!outstanding.isEmpty()) {
            PendingPress oldest = outstanding.peekFirst();
            if (oldest.birthSequence() < 0L || oldest.birthSequence() > acked) {
                break;
            }
            outstanding.pollFirst();
        }
        if (!authoritative.alive || authoritative.isSlowed()) {
            outstanding.clear();
        }
    }
}
