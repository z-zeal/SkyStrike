package io.github.skystrike.net;

import io.github.skystrike.shared.math.Angles;
import io.github.skystrike.shared.math.Lerp;
import io.github.skystrike.shared.model.Player;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Interpolates remote players smoothly between buffered snapshots.
 */
public final class Interpolator {

    public static final long DEFAULT_INTERPOLATION_DELAY_MS = 100L;

    private final StateBuffer buffer;
    private final long delayMillis;

    public Interpolator(StateBuffer buffer) {
        this(buffer, DEFAULT_INTERPOLATION_DELAY_MS);
    }

    public Interpolator(StateBuffer buffer, long delayMillis) {
        this.buffer = buffer;
        this.delayMillis = delayMillis;
    }

    /**
     * Returns interpolated remote players at the current render timestamp.
     */
    public List<Player> interpolateRemotePlayers(int localPlayerId) {
        List<StateBuffer.Snapshot> snaps = buffer.snapshots();
        if (snaps.isEmpty()) {
            return Collections.emptyList();
        }

        if (snaps.size() == 1) {
            List<Player> list = new ArrayList<>();
            for (Player p : snaps.get(0).players().values()) {
                if (p.id != localPlayerId) {
                    list.add(p.copy());
                }
            }
            return list;
        }

        long renderTime = System.currentTimeMillis() - delayMillis;
        StateBuffer.Snapshot from = null;
        StateBuffer.Snapshot to = null;

        for (int i = 0; i < snaps.size() - 1; i++) {
            if (snaps.get(i).timestampMillis() <= renderTime && snaps.get(i + 1).timestampMillis() >= renderTime) {
                from = snaps.get(i);
                to = snaps.get(i + 1);
                break;
            }
        }

        if (from == null || to == null) {
            // If renderTime is newer than latest snapshot, take the latest
            StateBuffer.Snapshot latest = snaps.get(snaps.size() - 1);
            List<Player> list = new ArrayList<>();
            for (Player p : latest.players().values()) {
                if (p.id != localPlayerId) {
                    list.add(p.copy());
                }
            }
            return list;
        }

        long span = to.timestampMillis() - from.timestampMillis();
        float alpha = span > 0 ? (float) (renderTime - from.timestampMillis()) / (float) span : 1f;
        alpha = Lerp.clamp(alpha, 0f, 1f);

        List<Player> interpolated = new ArrayList<>();
        Map<Integer, Player> toPlayers = to.players();
        for (Map.Entry<Integer, Player> entry : from.players().entrySet()) {
            int pid = entry.getKey();
            if (pid == localPlayerId) {
                continue;
            }
            Player p0 = entry.getValue();
            Player p1 = toPlayers.get(pid);
            if (p1 == null) {
                interpolated.add(p0.copy());
                continue;
            }

            Player result = new Player(p1.id, p1.name, p1.teamIndex, 0f, 0f);
            result.x = Lerp.mix(p0.x, p1.x, alpha);
            result.y = Lerp.mix(p0.y, p1.y, alpha);
            result.vx = Lerp.mix(p0.vx, p1.vx, alpha);
            result.vy = Lerp.mix(p0.vy, p1.vy, alpha);
            result.rotation = Angles.wrap(p0.rotation + Angles.shortestDelta(p0.rotation, p1.rotation) * alpha);
            result.aimAngle = Angles.wrap(p0.aimAngle + Angles.shortestDelta(p0.aimAngle, p1.aimAngle) * alpha);
            result.angularVelocity = Lerp.mix(p0.angularVelocity, p1.angularVelocity, alpha);
            result.fuel = Lerp.mix(p0.fuel, p1.fuel, alpha);
            result.health = p1.health;
            result.crouched = alpha >= 0.5f ? p1.crouched : p0.crouched;
            result.grounded = p1.grounded;
            result.jetpacking = p1.jetpacking;
            result.ads = p1.ads;
            interpolated.add(result);
        }

        return interpolated;
    }
}
