package io.github.skystrike.net;

import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.math.Lerp;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.net.c2s.PacketPlayerInput;
import io.github.skystrike.shared.physics.PlayerInput;
import io.github.skystrike.shared.physics.PlayerMotion;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Client-side prediction and server reconciliation for the local player.
 *
 * <p>Replays unacknowledged inputs on top of each authoritative snapshot using the exact same
 * {@link PlayerMotion} function executed on the server.
 */
public final class LocalPrediction {

    private record PendingInput(long sequence, PlayerInput input, float dt) {
    }

    private final Deque<PendingInput> pending = new ArrayDeque<>();
    private Player predicted;

    public LocalPrediction() {
    }

    /**
     * Steps the predicted local player state forward with a newly sampled input.
     *
     * <p>A dead player is not predicted. The server ignores their input, so replaying it
     * locally would only build a queue of motion the authoritative state never performed and
     * hand the player a rubber-band on respawn.
     */
    public Player predict(PacketPlayerInput packet, float dt, ArenaMap arena) {
        if (predicted == null || packet == null || dt <= 0f) {
            return predicted;
        }
        if (!predicted.alive) {
            pending.clear();
            return predicted;
        }
        PlayerInput input = PlayerInput.fromPacket(packet);
        pending.addLast(new PendingInput(packet.sequence, input, dt));
        PlayerMotion.stepInPlace(predicted, input, dt, arena);
        return predicted;
    }

    /**
     * Reconciles prediction against the authoritative server player state.
     */
    public void reconcile(Player authoritative, ArenaMap arena) {
        if (authoritative == null) {
            return;
        }

        if (predicted == null) {
            predicted = authoritative.copy();
            pending.clear();
            return;
        }

        // Discard inputs the server has already processed
        long ackSequence = authoritative.lastProcessedInputSequence;
        while (!pending.isEmpty() && pending.peekFirst().sequence() <= ackSequence) {
            pending.pollFirst();
        }

        // Replay all remaining unacknowledged inputs from the authoritative baseline
        Player replayed = authoritative.copy();
        for (PendingInput p : pending) {
            PlayerMotion.stepInPlace(replayed, p.input(), p.dt(), arena);
        }

        // Check divergence between predicted and replayed state
        float dx = predicted.x - replayed.x;
        float dy = predicted.y - replayed.y;
        float errorDistanceSq = dx * dx + dy * dy;

        if (errorDistanceSq > 4.0f) {
            // Diverged noticeably (e.g. wall collision or external impulse): snap to replayed
            predicted.set(replayed);
        } else {
            // Minor sub-pixel drift: smoothly blend position
            predicted.x = Lerp.smooth(predicted.x, replayed.x, 25f, 0.016f);
            predicted.y = Lerp.smooth(predicted.y, replayed.y, 25f, 0.016f);
            predicted.vx = replayed.vx;
            predicted.vy = replayed.vy;
            predicted.rotation = replayed.rotation;
            predicted.angularVelocity = replayed.angularVelocity;
            predicted.fuel = replayed.fuel;
            predicted.health = replayed.health;
            predicted.grounded = replayed.grounded;
            predicted.crouched = replayed.crouched;
            predicted.jetpacking = replayed.jetpacking;
            // Combat state is never predicted: the server alone decides it.
            predicted.alive = replayed.alive;
            predicted.weaponId = replayed.weaponId;
            predicted.spread = replayed.spread;
            predicted.gunKick = replayed.gunKick;
            predicted.kills = replayed.kills;
            predicted.deaths = replayed.deaths;
            predicted.respawnTimer = replayed.respawnTimer;
        }
    }

    public Player predicted() {
        return predicted;
    }

    public void setLocalPlayer(Player player) {
        this.predicted = player != null ? player.copy() : null;
        this.pending.clear();
    }

    public void reset() {
        this.predicted = null;
        this.pending.clear();
    }

    public int pendingCount() {
        return pending.size();
    }
}
