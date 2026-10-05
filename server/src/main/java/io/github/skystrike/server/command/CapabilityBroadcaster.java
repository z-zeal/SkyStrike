package io.github.skystrike.server.command;

import io.github.skystrike.shared.command.Permission;
import io.github.skystrike.shared.net.s2c.PacketCapabilities;
import java.util.HashMap;
import java.util.Map;

/**
 * Pushes the console capability to clients on join and whenever it changes
 * (console plan §4.1, build-order Phase 3).
 *
 * <p>The client requests nothing. This is the only thing that tells it whether a console exists,
 * and it is sent again the moment a level changes so a promotion takes effect mid-match without a
 * reconnect.
 *
 * <p>Pushes are deduplicated against the last value actually sent, because the alternative — a
 * capability packet every time something asks — produces a "console access granted" system line
 * on every tick that touches permissions.
 *
 * <p><b>Threading.</b> Tick thread only.
 */
public final class CapabilityBroadcaster {

    /** How a capability packet reaches one player. Supplied by the composition root. */
    @FunctionalInterface
    public interface Sender {
        void send(int playerId, PacketCapabilities packet);
    }

    private final PermissionResolver resolver;
    private final Sender sender;
    private final Map<Integer, PacketCapabilities> lastSent = new HashMap<>();

    public CapabilityBroadcaster(PermissionResolver resolver, Sender sender) {
        if (resolver == null || sender == null) {
            throw new IllegalArgumentException("resolver and sender are required");
        }
        this.resolver = resolver;
        this.sender = sender;
    }

    /**
     * Sends the current capability unconditionally. Used on join, where the client has no prior
     * value and silence would be indistinguishable from "no console".
     */
    public PacketCapabilities pushOnJoin(int playerId, String name) {
        PacketCapabilities packet = currentFor(playerId, name);
        lastSent.put(playerId, packet);
        sender.send(playerId, packet);
        return packet;
    }

    /**
     * Sends only if the capability differs from the last value this player was told.
     *
     * @return true when a packet was sent
     */
    public boolean pushIfChanged(int playerId, String name) {
        PacketCapabilities packet = currentFor(playerId, name);
        PacketCapabilities previous = lastSent.get(playerId);
        if (packet.matches(previous)) {
            return false;
        }
        lastSent.put(playerId, packet);
        sender.send(playerId, packet);
        return true;
    }

    /**
     * Changes a live player's level and pushes the result if it changed anything.
     *
     * @return true when the client was told something new
     */
    public boolean promote(int playerId, String name, Permission level) {
        resolver.setRuntimeLevel(playerId, name, level);
        return pushIfChanged(playerId, name);
    }

    /** The capability this player would be told right now, without sending it. */
    public PacketCapabilities currentFor(int playerId, String name) {
        return PacketCapabilities.forLevel(
            resolver.resolve(playerId, name), resolver.consoleThreshold());
    }

    /** The last value actually sent to this player, or {@code null}. */
    public PacketCapabilities lastSentTo(int playerId) {
        return lastSent.get(playerId);
    }

    /** Drops session state for a departing player. */
    public void forget(int playerId) {
        lastSent.remove(playerId);
        resolver.forget(playerId);
    }

    public void clear() {
        lastSent.clear();
    }
}
