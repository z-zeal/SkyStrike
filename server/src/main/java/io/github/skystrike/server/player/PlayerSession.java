package io.github.skystrike.server.player;

import com.esotericsoftware.kryonet.Connection;
import io.github.skystrike.server.weapons.GunInstance;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.net.c2s.PacketPlayerInput;
import io.github.skystrike.shared.physics.PlayerInput;
import io.github.skystrike.shared.weapons.WeaponId;

/**
 * Server-side session binding a network {@link Connection} to its authoritative {@link Player}.
 *
 * <p>Also holds the live gun state and the <b>trigger edge</b>. The edge matters: input arrives
 * unreliably at roughly the frame rate while the simulation consumes one input per tick, so a
 * semi-automatic tap that starts and ends between two ticks would otherwise be swallowed. Every
 * arriving packet that raises {@code fire} latches a pending press, and the tick consumes it.
 */
public final class PlayerSession {

    private final Connection connection;
    private final int playerId;
    private final String name;
    private final Player player;
    private final GunInstance gun;

    private volatile PlayerInput latestInput = new PlayerInput();
    private volatile long lastInputTimeMillis;
    private volatile boolean triggerHeld;
    private volatile boolean firePressedPending;

    public PlayerSession(Connection connection, int playerId, String name, int teamIndex, float spawnX, float spawnY) {
        this.connection = connection;
        this.playerId = playerId;
        this.name = name;
        this.player = new Player(playerId, name, teamIndex, spawnX, spawnY);
        this.gun = new GunInstance(WeaponId.DEFAULT);
        this.player.weaponId = gun.weaponId().ordinal();
        this.lastInputTimeMillis = System.currentTimeMillis();
    }

    public Connection connection() {
        return connection;
    }

    public int playerId() {
        return playerId;
    }

    public String name() {
        return name;
    }

    public Player player() {
        return player;
    }

    /** Live spread and recoil state for the weapon this player is holding. */
    public GunInstance gun() {
        return gun;
    }

    public PlayerInput latestInput() {
        return latestInput;
    }

    public void setInput(PacketPlayerInput packet) {
        if (packet == null || packet.sequence < latestInput.sequence) {
            return;
        }
        if (packet.fire && !triggerHeld) {
            firePressedPending = true;
        }
        triggerHeld = packet.fire;
        this.latestInput = PlayerInput.fromPacket(packet);
        this.lastInputTimeMillis = System.currentTimeMillis();
    }

    /** True while the trigger is down. Drives automatic weapons. */
    public boolean triggerHeld() {
        return triggerHeld;
    }

    /**
     * Returns whether the trigger went down since the last tick, and clears the latch.
     *
     * <p>Tick thread only — one consumer, exactly once per press.
     */
    public boolean consumeFirePressed() {
        if (!firePressedPending) {
            return false;
        }
        firePressedPending = false;
        return true;
    }

    /** Drops a pending press, used when the player dies holding the trigger. */
    public void clearTrigger() {
        firePressedPending = false;
        triggerHeld = false;
    }

    public long lastInputTimeMillis() {
        return lastInputTimeMillis;
    }
}
