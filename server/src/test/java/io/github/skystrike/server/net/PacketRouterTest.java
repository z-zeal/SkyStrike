package io.github.skystrike.server.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.net.c2s.PacketJoinRequest;
import io.github.skystrike.shared.net.c2s.PacketPing;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pure routing behaviour. No sockets involved — the router never touches the transport. */
class PacketRouterTest {

    @Test
    void routesAPacketToItsHandler() {
        PacketRouter router = new PacketRouter();
        List<PacketPing> seen = new ArrayList<>();
        router.register(PacketPing.class, (connection, packet) -> seen.add(packet));

        PacketPing ping = new PacketPing(42L);
        assertTrue(router.route(null, ping));

        assertEquals(1, seen.size());
        assertEquals(42L, seen.get(0).clientTimeMillis);
        assertEquals(1L, router.routedCount());
    }

    @Test
    @DisplayName("a packet with no handler is counted and dropped, not thrown")
    void unregisteredPacketsAreDropped() {
        PacketRouter router = new PacketRouter();
        assertFalse(router.route(null, new PacketJoinRequest(1, "a")));
        assertEquals(1L, router.unhandledCount());
        assertEquals(0L, router.routedCount());
    }

    @Test
    void nonPacketPayloadsAreDropped() {
        PacketRouter router = new PacketRouter();
        assertFalse(router.route(null, "not a packet"));
        assertFalse(router.route(null, null));
        assertEquals(2L, router.unhandledCount());
    }

    @Test
    void registeringTwiceForOneTypeIsRejected() {
        PacketRouter router = new PacketRouter();
        router.register(PacketPing.class, (connection, packet) -> { });
        assertThrows(
            IllegalStateException.class,
            () -> router.register(PacketPing.class, (connection, packet) -> { }));
    }

    @Test
    void nullRegistrationsAreRejected() {
        PacketRouter router = new PacketRouter();
        assertThrows(
            IllegalArgumentException.class, () -> router.register(PacketPing.class, null));
        PacketHandler<PacketPing> handler = (connection, packet) -> { };
        assertThrows(IllegalArgumentException.class, () -> router.register(null, handler));
    }

    @Test
    void reportsWhatItHandles() {
        PacketRouter router = new PacketRouter();
        router.register(PacketPing.class, (connection, packet) -> { });
        assertTrue(router.handles(PacketPing.class));
        assertFalse(router.handles(PacketJoinRequest.class));
        assertEquals(1, router.handlerCount());
    }
}
