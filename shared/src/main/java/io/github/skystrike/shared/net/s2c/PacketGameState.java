package io.github.skystrike.shared.net.s2c;

import io.github.skystrike.shared.model.CameraEntity;
import io.github.skystrike.shared.model.DroneEntity;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.model.Projectile;
import io.github.skystrike.shared.model.ThrownUtility;
import io.github.skystrike.shared.model.UtilityZone;
import io.github.skystrike.shared.net.Packet;
import java.util.ArrayList;
import java.util.List;

/**
 * The per-snapshot state broadcast.
 *
 * <p>Carries players, rounds, throwable utilities, and the Phase 6 gadget devices (drones and
 * throw cameras) in the world. Entity lists can be culled per recipient by the server; state the
 * viewer is not allowed to observe must never be exposed by a client-authored packet.
 */
public final class PacketGameState implements Packet {

    /** Simulation tick this snapshot describes. */
    public long tick;

    /** Server wall clock when the snapshot was built, for clock-offset estimation. */
    public long serverTimeMillis;

    /** How many clients are currently joined. */
    public int playerCount;

    /** Snapshot of the player states visible to the recipient. */
    public List<Player> players = new ArrayList<>();

    /** Rounds in flight visible to the recipient. */
    public List<Projectile> projectiles = new ArrayList<>();

    /** Thrown utilities in flight or settled while their fuse counts down. */
    public List<ThrownUtility> thrownUtilities = new ArrayList<>();

    /** Persistent smoke, poison and fire zones currently in the arena. */
    public List<UtilityZone> utilityZones = new ArrayList<>();

    /** Deployed surveillance drones currently in the arena (mechanics §7.1). */
    public List<DroneEntity> drones = new ArrayList<>();

    /** Thrown cameras in flight or stuck to a surface (mechanics §7.2). */
    public List<CameraEntity> cameras = new ArrayList<>();

    /**
     * True while any joined session has a server debug toggle on ({@code sv_noclip},
     * {@code sv_godmode} or {@code sv_infinite_ammo}; build plan M3 §4) — the HUD's "CHEATS" tag
     * reads this so nobody is confused about why a target will not die.
     */
    public boolean cheatsActive;

    public PacketGameState() {
    }

    public PacketGameState(long tick, long serverTimeMillis, int playerCount) {
        this.tick = tick;
        this.serverTimeMillis = serverTimeMillis;
        this.playerCount = playerCount;
    }

    public PacketGameState(long tick, long serverTimeMillis, int playerCount, List<Player> players) {
        this(tick, serverTimeMillis, playerCount, players, null);
    }

    public PacketGameState(
        long tick,
        long serverTimeMillis,
        int playerCount,
        List<Player> players,
        List<Projectile> projectiles
    ) {
        this(tick, serverTimeMillis, playerCount, players, projectiles, null, null);
    }

    public PacketGameState(
        long tick,
        long serverTimeMillis,
        int playerCount,
        List<Player> players,
        List<Projectile> projectiles,
        List<ThrownUtility> thrownUtilities
    ) {
        this(tick, serverTimeMillis, playerCount, players, projectiles, thrownUtilities, null);
    }

    public PacketGameState(
        long tick,
        long serverTimeMillis,
        int playerCount,
        List<Player> players,
        List<Projectile> projectiles,
        List<ThrownUtility> thrownUtilities,
        List<UtilityZone> utilityZones
    ) {
        this(tick, serverTimeMillis, playerCount, players, projectiles, thrownUtilities,
            utilityZones, null, null);
    }

    /** Full constructor: the entity lists ride the snapshot in the order they are declared. */
    public PacketGameState(
        long tick,
        long serverTimeMillis,
        int playerCount,
        List<Player> players,
        List<Projectile> projectiles,
        List<ThrownUtility> thrownUtilities,
        List<UtilityZone> utilityZones,
        List<DroneEntity> drones,
        List<CameraEntity> cameras
    ) {
        this.tick = tick;
        this.serverTimeMillis = serverTimeMillis;
        this.playerCount = playerCount;
        if (players != null) {
            this.players = new ArrayList<>(players);
        }
        if (projectiles != null) {
            this.projectiles = new ArrayList<>(projectiles);
        }
        if (thrownUtilities != null) {
            this.thrownUtilities = new ArrayList<>(thrownUtilities);
        }
        if (utilityZones != null) {
            this.utilityZones = new ArrayList<>(utilityZones);
        }
        if (drones != null) {
            this.drones = new ArrayList<>(drones);
        }
        if (cameras != null) {
            this.cameras = new ArrayList<>(cameras);
        }
    }

    @Override
    public String toString() {
        return "PacketGameState[tick=" + tick
            + ", serverTimeMillis=" + serverTimeMillis
            + ", playerCount=" + playerCount
            + ", players=" + players.size()
            + ", projectiles=" + projectiles.size()
            + ", thrownUtilities=" + thrownUtilities.size()
            + ", utilityZones=" + utilityZones.size()
            + ", drones=" + drones.size()
            + ", cameras=" + cameras.size()
            + ", cheatsActive=" + cheatsActive + "]";
    }
}
