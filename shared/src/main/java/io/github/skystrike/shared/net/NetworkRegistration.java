package io.github.skystrike.shared.net;

import com.esotericsoftware.kryo.Kryo;
import io.github.skystrike.shared.net.c2s.PacketJoinRequest;
import io.github.skystrike.shared.net.c2s.PacketLeaveRequest;
import io.github.skystrike.shared.net.c2s.PacketPing;
import io.github.skystrike.shared.net.s2c.PacketGameState;
import io.github.skystrike.shared.net.s2c.PacketJoinAccept;
import io.github.skystrike.shared.net.s2c.PacketJoinReject;
import io.github.skystrike.shared.net.s2c.PacketPong;
import java.util.List;

/**
 * The single registration both sides call.
 *
 * <p>Kryo identifies classes on the wire by the order they were registered. If the client and the
 * server ever register a different set, or the same set in a different order, every packet still
 * deserialises — into the wrong type, silently. So there is exactly one list, here, and neither
 * side is allowed to register a packet of its own.
 *
 * <p><b>Rules for changing this list:</b> only append, never insert or reorder, and bump
 * {@code NetConfig.PROTOCOL_VERSION} whenever it changes.
 */
public final class NetworkRegistration {

    private static final List<Class<?>> TYPES = List.of(
        // Marker, registered first so the field type of any future polymorphic field resolves.
        Packet.class,

        // Client to server.
        PacketJoinRequest.class,
        PacketPing.class,
        PacketLeaveRequest.class,

        // Server to client.
        PacketJoinAccept.class,
        PacketJoinReject.class,
        PacketPong.class,
        PacketGameState.class);

    private NetworkRegistration() {
    }

    /**
     * Registers every packet type on {@code kryo}, in the canonical order.
     *
     * <p>Call this on the Kryo instance owned by the transport endpoint, after the transport has
     * registered its own framework classes, and on both sides.
     */
    public static void register(Kryo kryo) {
        for (Class<?> type : TYPES) {
            kryo.register(type);
        }
    }

    /** The canonical list, in registration order. Exposed so tests can assert on it. */
    public static List<Class<?>> registeredTypes() {
        return TYPES;
    }
}
