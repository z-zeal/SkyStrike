package io.github.skystrike.server.net;

import com.esotericsoftware.kryonet.Connection;

/**
 * One thing that happened on the transport, captured on the network thread for replay on the tick
 * thread.
 *
 * <p>Connects and disconnects are queued alongside received packets rather than handled inline,
 * because otherwise a client could appear or vanish halfway through a tick and the simulation
 * would observe two different player sets within one step.
 */
public record NetworkEvent(Type type, Connection connection, Object payload) {

    public enum Type {
        CONNECTED,
        DISCONNECTED,
        RECEIVED
    }

    public static NetworkEvent connected(Connection connection) {
        return new NetworkEvent(Type.CONNECTED, connection, null);
    }

    public static NetworkEvent disconnected(Connection connection) {
        return new NetworkEvent(Type.DISCONNECTED, connection, null);
    }

    public static NetworkEvent received(Connection connection, Object payload) {
        return new NetworkEvent(Type.RECEIVED, connection, payload);
    }
}
