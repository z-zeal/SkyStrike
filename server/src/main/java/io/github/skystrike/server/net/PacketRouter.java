package io.github.skystrike.server.net;

import com.esotericsoftware.kryonet.Connection;
import io.github.skystrike.shared.net.Packet;
import java.util.HashMap;
import java.util.Map;

/**
 * Dispatches a decoded packet to the one handler registered for its type.
 *
 * <p>Anything unrecognised is counted and dropped rather than thrown: a malformed or stale client
 * must not be able to take the tick thread down.
 */
public final class PacketRouter {

    private final Map<Class<?>, PacketHandler<?>> handlers = new HashMap<>();
    private long routed;
    private long unhandled;

    /** Registers the handler for {@code type}. Registering twice for one type is a bug. */
    public <T extends Packet> void register(Class<T> type, PacketHandler<T> handler) {
        if (type == null || handler == null) {
            throw new IllegalArgumentException("type and handler must not be null");
        }
        PacketHandler<?> previous = handlers.put(type, handler);
        if (previous != null) {
            throw new IllegalStateException("duplicate handler registered for " + type.getName());
        }
    }

    public boolean handles(Class<?> type) {
        return handlers.containsKey(type);
    }

    public int handlerCount() {
        return handlers.size();
    }

    public long routedCount() {
        return routed;
    }

    public long unhandledCount() {
        return unhandled;
    }

    /**
     * Routes one payload.
     *
     * @return true if a handler consumed it
     */
    @SuppressWarnings("unchecked")
    public boolean route(Connection connection, Object payload) {
        if (!(payload instanceof Packet packet)) {
            unhandled++;
            return false;
        }
        PacketHandler<Packet> handler = (PacketHandler<Packet>) handlers.get(packet.getClass());
        if (handler == null) {
            unhandled++;
            return false;
        }
        handler.handle(connection, packet);
        routed++;
        return true;
    }
}
