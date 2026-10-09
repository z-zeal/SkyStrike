package io.github.skystrike.net;

import io.github.skystrike.shared.model.CameraEntity;
import io.github.skystrike.shared.model.DroneEntity;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.model.Projectile;
import io.github.skystrike.shared.model.ThrownUtility;
import io.github.skystrike.shared.model.UtilityZone;
import io.github.skystrike.shared.net.s2c.PacketGameState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Historical ring buffer of authoritative game state snapshots for remote entity interpolation.
 *
 * <p>Snapshots carry rounds in flight as well as players. Tracers arrive at the snapshot rate
 * (20 Hz) but are drawn at the frame rate, so they have to be interpolated like anything else —
 * a bullet that teleports 100 units between frames reads as a flicker, not a shot.
 */
public final class StateBuffer {

    public static final class Snapshot {
        private final long tick;
        private final long timestampMillis;
        private final Map<Integer, Player> players;
        private final Map<Integer, Projectile> projectiles;
        private final Map<Integer, ThrownUtility> thrownUtilities;
        private final Map<Integer, UtilityZone> utilityZones;
        private final Map<Integer, DroneEntity> drones;
        private final Map<Integer, CameraEntity> cameras;

        public Snapshot(long tick, long timestampMillis, List<Player> playerList) {
            this(tick, timestampMillis, playerList, null, null, null);
        }

        public Snapshot(
                long tick,
                long timestampMillis,
                List<Player> playerList,
                List<Projectile> projectileList) {
            this(tick, timestampMillis, playerList, projectileList, null, null);
        }

        public Snapshot(
                long tick,
                long timestampMillis,
                List<Player> playerList,
                List<Projectile> projectileList,
                List<ThrownUtility> thrownUtilityList,
                List<UtilityZone> utilityZoneList) {
            this(tick, timestampMillis, playerList, projectileList, thrownUtilityList,
                utilityZoneList, null, null);
        }

        /** Full snapshot: players, rounds, utilities, zones and the gadget devices. */
        public Snapshot(
                long tick,
                long timestampMillis,
                List<Player> playerList,
                List<Projectile> projectileList,
                List<ThrownUtility> thrownUtilityList,
                List<UtilityZone> utilityZoneList,
                List<DroneEntity> droneList,
                List<CameraEntity> cameraList) {
            this.tick = tick;
            this.timestampMillis = timestampMillis;

            Map<Integer, Player> playerMap = new HashMap<>();
            if (playerList != null) {
                for (Player p : playerList) {
                    playerMap.put(p.id, p.copy());
                }
            }
            this.players = Collections.unmodifiableMap(playerMap);

            Map<Integer, Projectile> projectileMap = new HashMap<>();
            if (projectileList != null) {
                for (Projectile p : projectileList) {
                    projectileMap.put(p.id, p.copy());
                }
            }
            this.projectiles = Collections.unmodifiableMap(projectileMap);

            Map<Integer, ThrownUtility> thrownMap = new HashMap<>();
            if (thrownUtilityList != null) {
                for (ThrownUtility utility : thrownUtilityList) {
                    thrownMap.put(utility.id, utility.copy());
                }
            }
            this.thrownUtilities = Collections.unmodifiableMap(thrownMap);

            Map<Integer, UtilityZone> zoneMap = new HashMap<>();
            if (utilityZoneList != null) {
                for (UtilityZone zone : utilityZoneList) {
                    zoneMap.put(zone.id, zone.copy());
                }
            }
            this.utilityZones = Collections.unmodifiableMap(zoneMap);

            Map<Integer, DroneEntity> droneMap = new HashMap<>();
            if (droneList != null) {
                for (DroneEntity drone : droneList) {
                    droneMap.put(drone.id, drone.copy());
                }
            }
            this.drones = Collections.unmodifiableMap(droneMap);

            Map<Integer, CameraEntity> cameraMap = new HashMap<>();
            if (cameraList != null) {
                for (CameraEntity camera : cameraList) {
                    cameraMap.put(camera.id, camera.copy());
                }
            }
            this.cameras = Collections.unmodifiableMap(cameraMap);
        }

        public long tick() {
            return tick;
        }

        public long timestampMillis() {
            return timestampMillis;
        }

        public Map<Integer, Player> players() {
            return players;
        }

        public Map<Integer, Projectile> projectiles() {
            return projectiles;
        }

        public Map<Integer, ThrownUtility> thrownUtilities() {
            return thrownUtilities;
        }

        public Map<Integer, UtilityZone> utilityZones() {
            return utilityZones;
        }

        public Map<Integer, DroneEntity> drones() {
            return drones;
        }

        public Map<Integer, CameraEntity> cameras() {
            return cameras;
        }
    }

    private static final int MAX_SNAPSHOTS = 64;
    private final List<Snapshot> snapshots = new ArrayList<>();

    public StateBuffer() {
    }

    /**
     * Appends a newly arrived server state snapshot.
     */
    public synchronized void addSnapshot(PacketGameState state) {
        if (state == null) {
            return;
        }
        long time = state.serverTimeMillis > 0 ? state.serverTimeMillis : System.currentTimeMillis();
        snapshots.add(new Snapshot(
            state.tick, time, state.players, state.projectiles, state.thrownUtilities,
            state.utilityZones, state.drones, state.cameras));
        while (snapshots.size() > MAX_SNAPSHOTS) {
            snapshots.remove(0);
        }
    }

    public synchronized List<Snapshot> snapshots() {
        return new ArrayList<>(snapshots);
    }

    public synchronized void clear() {
        snapshots.clear();
    }

    public synchronized int size() {
        return snapshots.size();
    }
}
