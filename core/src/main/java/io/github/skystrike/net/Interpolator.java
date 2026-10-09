package io.github.skystrike.net;

import io.github.skystrike.shared.math.Angles;
import io.github.skystrike.shared.math.Lerp;
import io.github.skystrike.shared.model.CameraEntity;
import io.github.skystrike.shared.model.DroneEntity;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.model.Projectile;
import io.github.skystrike.shared.model.ThrownUtility;
import io.github.skystrike.shared.model.UtilityZone;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Interpolates remote entities smoothly between buffered snapshots.
 *
 * <p>Players and rounds in flight are interpolated the same way and with the same delay, so a
 * tracer and the player it came from stay in step. A round that only exists in the newer
 * snapshot is drawn at its newer position rather than popped into being at the older one.
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
            // Combat state is authoritative and discrete: take the newer snapshot, except the
            // visual gun kick, which is a continuous angle and reads badly if it steps.
            result.alive = p1.alive;
            result.weaponId = p1.weaponId;
            result.loadout.set(p1.loadout);
            result.spread = Lerp.mix(p0.spread, p1.spread, alpha);
            result.gunKick = Lerp.mix(p0.gunKick, p1.gunKick, alpha);
            result.kills = p1.kills;
            result.deaths = p1.deaths;
            result.respawnTimer = p1.respawnTimer;
            result.blindRemaining = Lerp.mix(p0.blindRemaining, p1.blindRemaining, alpha);
            result.blindDuration = p1.blindDuration;
            result.slowRemaining = Lerp.mix(p0.slowRemaining, p1.slowRemaining, alpha);
            interpolated.add(result);
        }

        return interpolated;
    }

    /**
     * Returns interpolated rounds in flight at the current render timestamp.
     *
     * <p>Rounds the server has retired simply stop appearing; the renderer notices the gap and
     * plays the impact.
     */
    public List<Projectile> interpolateProjectiles() {
        List<StateBuffer.Snapshot> snaps = buffer.snapshots();
        if (snaps.isEmpty()) {
            return Collections.emptyList();
        }

        if (snaps.size() == 1) {
            return copyAll(snaps.get(0).projectiles());
        }

        long renderTime = System.currentTimeMillis() - delayMillis;
        StateBuffer.Snapshot from = null;
        StateBuffer.Snapshot to = null;

        for (int i = 0; i < snaps.size() - 1; i++) {
            if (snaps.get(i).timestampMillis() <= renderTime
                && snaps.get(i + 1).timestampMillis() >= renderTime) {
                from = snaps.get(i);
                to = snaps.get(i + 1);
                break;
            }
        }

        if (from == null || to == null) {
            return copyAll(snaps.get(snaps.size() - 1).projectiles());
        }

        long span = to.timestampMillis() - from.timestampMillis();
        float alpha = span > 0 ? (float) (renderTime - from.timestampMillis()) / (float) span : 1f;
        alpha = Lerp.clamp(alpha, 0f, 1f);

        List<Projectile> interpolated = new ArrayList<>();
        Map<Integer, Projectile> fromRounds = from.projectiles();
        for (Map.Entry<Integer, Projectile> entry : to.projectiles().entrySet()) {
            Projectile newer = entry.getValue();
            Projectile older = fromRounds.get(entry.getKey());
            if (older == null) {
                // Fired after the older snapshot: show it where it actually is.
                interpolated.add(newer.copy());
                continue;
            }
            Projectile result = newer.copy();
            result.x = Lerp.mix(older.x, newer.x, alpha);
            result.y = Lerp.mix(older.y, newer.y, alpha);
            result.vx = Lerp.mix(older.vx, newer.vx, alpha);
            result.vy = Lerp.mix(older.vy, newer.vy, alpha);
            interpolated.add(result);
        }
        return interpolated;
    }

    /**
     * Returns smoothly interpolated utilities in flight. A newly observed utility is drawn at its
     * newer position rather than delayed until the next pair of snapshots, matching bullets.
     */
    public List<ThrownUtility> interpolateThrownUtilities() {
        List<StateBuffer.Snapshot> snaps = buffer.snapshots();
        if (snaps.isEmpty()) {
            return Collections.emptyList();
        }
        if (snaps.size() == 1) {
            return copyUtilities(snaps.get(0).thrownUtilities());
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
            return copyUtilities(snaps.get(snaps.size() - 1).thrownUtilities());
        }

        long span = to.timestampMillis() - from.timestampMillis();
        float alpha = span > 0 ? (float) (renderTime - from.timestampMillis()) / (float) span : 1f;
        alpha = Lerp.clamp(alpha, 0f, 1f);
        List<ThrownUtility> result = new ArrayList<>();
        for (Map.Entry<Integer, ThrownUtility> entry : to.thrownUtilities().entrySet()) {
            ThrownUtility newer = entry.getValue();
            ThrownUtility older = from.thrownUtilities().get(entry.getKey());
            if (older == null) {
                result.add(newer.copy());
                continue;
            }
            ThrownUtility interpolated = newer.copy();
            interpolated.x = Lerp.mix(older.x, newer.x, alpha);
            interpolated.y = Lerp.mix(older.y, newer.y, alpha);
            interpolated.vx = Lerp.mix(older.vx, newer.vx, alpha);
            interpolated.vy = Lerp.mix(older.vy, newer.vy, alpha);
            result.add(interpolated);
        }
        return result;
    }

    /**
     * Returns smoothly interpolated drones, the same treatment as utilities in flight: a deployed
     * drone moves at up to 200 u/s, which steps visibly at the 20 Hz snapshot rate. The owner's
     * own drone is predicted locally by the surveillance controller instead; this list is for
     * everyone else's devices (and the fallback before the first prediction exists).
     */
    public List<DroneEntity> interpolateDrones() {
        List<StateBuffer.Snapshot> snaps = buffer.snapshots();
        if (snaps.isEmpty()) {
            return Collections.emptyList();
        }
        if (snaps.size() == 1) {
            return copyDrones(snaps.get(0).drones());
        }

        long renderTime = System.currentTimeMillis() - delayMillis;
        StateBuffer.Snapshot from = null;
        StateBuffer.Snapshot to = null;
        for (int i = 0; i < snaps.size() - 1; i++) {
            if (snaps.get(i).timestampMillis() <= renderTime
                && snaps.get(i + 1).timestampMillis() >= renderTime) {
                from = snaps.get(i);
                to = snaps.get(i + 1);
                break;
            }
        }
        if (from == null || to == null) {
            return copyDrones(snaps.get(snaps.size() - 1).drones());
        }

        long span = to.timestampMillis() - from.timestampMillis();
        float alpha = span > 0 ? (float) (renderTime - from.timestampMillis()) / (float) span : 1f;
        alpha = Lerp.clamp(alpha, 0f, 1f);
        List<DroneEntity> result = new ArrayList<>();
        for (Map.Entry<Integer, DroneEntity> entry : to.drones().entrySet()) {
            DroneEntity newer = entry.getValue();
            DroneEntity older = from.drones().get(entry.getKey());
            if (older == null) {
                result.add(newer.copy());
                continue;
            }
            DroneEntity interpolated = newer.copy();
            interpolated.x = Lerp.mix(older.x, newer.x, alpha);
            interpolated.y = Lerp.mix(older.y, newer.y, alpha);
            interpolated.vx = Lerp.mix(older.vx, newer.vx, alpha);
            interpolated.vy = Lerp.mix(older.vy, newer.vy, alpha);
            interpolated.aimAngle = Angles.wrap(
                older.aimAngle + Angles.shortestDelta(older.aimAngle, newer.aimAngle) * alpha);
            result.add(interpolated);
        }
        return result;
    }

    /**
     * Returns smoothly interpolated cameras. A stuck camera never moves, so this matters only for
     * the flight arc — and for the stuck ones the newest snapshot is exact anyway.
     */
    public List<CameraEntity> interpolateCameras() {
        List<StateBuffer.Snapshot> snaps = buffer.snapshots();
        if (snaps.isEmpty()) {
            return Collections.emptyList();
        }
        if (snaps.size() == 1) {
            return copyCameras(snaps.get(0).cameras());
        }

        long renderTime = System.currentTimeMillis() - delayMillis;
        StateBuffer.Snapshot from = null;
        StateBuffer.Snapshot to = null;
        for (int i = 0; i < snaps.size() - 1; i++) {
            if (snaps.get(i).timestampMillis() <= renderTime
                && snaps.get(i + 1).timestampMillis() >= renderTime) {
                from = snaps.get(i);
                to = snaps.get(i + 1);
                break;
            }
        }
        if (from == null || to == null) {
            return copyCameras(snaps.get(snaps.size() - 1).cameras());
        }

        long span = to.timestampMillis() - from.timestampMillis();
        float alpha = span > 0 ? (float) (renderTime - from.timestampMillis()) / (float) span : 1f;
        alpha = Lerp.clamp(alpha, 0f, 1f);
        List<CameraEntity> result = new ArrayList<>();
        for (Map.Entry<Integer, CameraEntity> entry : to.cameras().entrySet()) {
            CameraEntity newer = entry.getValue();
            CameraEntity older = from.cameras().get(entry.getKey());
            if (older == null) {
                result.add(newer.copy());
                continue;
            }
            CameraEntity interpolated = newer.copy();
            interpolated.x = Lerp.mix(older.x, newer.x, alpha);
            interpolated.y = Lerp.mix(older.y, newer.y, alpha);
            interpolated.vx = Lerp.mix(older.vx, newer.vx, alpha);
            interpolated.vy = Lerp.mix(older.vy, newer.vy, alpha);
            interpolated.aimAngle = Angles.wrap(
                older.aimAngle + Angles.shortestDelta(older.aimAngle, newer.aimAngle) * alpha);
            result.add(interpolated);
        }
        return result;
    }

    /**
     * Persistent zones do not move, so the newest snapshot is authoritative. Copies keep callers
     * from mutating the historical network buffer while preparing shader smoke uniforms.
     */
    public List<UtilityZone> latestUtilityZones() {
        List<StateBuffer.Snapshot> snaps = buffer.snapshots();
        if (snaps.isEmpty()) {
            return Collections.emptyList();
        }
        List<UtilityZone> zones = new ArrayList<>();
        for (UtilityZone zone : snaps.get(snaps.size() - 1).utilityZones().values()) {
            zones.add(zone.copy());
        }
        return zones;
    }

    private static List<Projectile> copyAll(Map<Integer, Projectile> rounds) {
        List<Projectile> list = new ArrayList<>(rounds.size());
        for (Projectile projectile : rounds.values()) {
            list.add(projectile.copy());
        }
        return list;
    }

    private static List<ThrownUtility> copyUtilities(Map<Integer, ThrownUtility> utilities) {
        List<ThrownUtility> list = new ArrayList<>(utilities.size());
        for (ThrownUtility utility : utilities.values()) {
            list.add(utility.copy());
        }
        return list;
    }

    private static List<DroneEntity> copyDrones(Map<Integer, DroneEntity> drones) {
        List<DroneEntity> list = new ArrayList<>(drones.size());
        for (DroneEntity drone : drones.values()) {
            list.add(drone.copy());
        }
        return list;
    }

    private static List<CameraEntity> copyCameras(Map<Integer, CameraEntity> cameras) {
        List<CameraEntity> list = new ArrayList<>(cameras.size());
        for (CameraEntity camera : cameras.values()) {
            list.add(camera.copy());
        }
        return list;
    }
}
