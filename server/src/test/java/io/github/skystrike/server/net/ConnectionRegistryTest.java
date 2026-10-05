package io.github.skystrike.server.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.esotericsoftware.kryonet.Connection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The registry only reads {@link Connection#getID()}, so a bare Connection is enough to exercise
 * it without opening a socket.
 */
class ConnectionRegistryTest {

    /** KryoNet assigns ids on connect; here we just need two distinguishable instances. */
    private static final class FakeConnection extends Connection {
        private final int id;

        FakeConnection(int id) {
            this.id = id;
        }

        @Override
        public int getID() {
            return id;
        }
    }

    @Test
    void joiningAllocatesAscendingPlayerIds() {
        ConnectionRegistry registry = new ConnectionRegistry(4);
        ConnectionRegistry.Entry first = registry.join(new FakeConnection(1), "a");
        ConnectionRegistry.Entry second = registry.join(new FakeConnection(2), "b");

        assertNotNull(first);
        assertNotNull(second);
        assertNotEquals(first.playerId(), second.playerId());
        assertEquals(2, registry.count());
    }

    @Test
    void theSameConnectionCannotJoinTwice() {
        ConnectionRegistry registry = new ConnectionRegistry(4);
        Connection connection = new FakeConnection(1);

        assertNotNull(registry.join(connection, "a"));
        assertNull(registry.join(connection, "a"));
        assertEquals(1, registry.count());
    }

    @Test
    @DisplayName("a full server refuses the next join")
    void capacityIsEnforced() {
        ConnectionRegistry registry = new ConnectionRegistry(2);
        assertNotNull(registry.join(new FakeConnection(1), "a"));
        assertNotNull(registry.join(new FakeConnection(2), "b"));
        assertTrue(registry.isFull());
        assertNull(registry.join(new FakeConnection(3), "c"));
    }

    @Test
    void leavingFreesTheSlotAndIsIdempotent() {
        ConnectionRegistry registry = new ConnectionRegistry(1);
        Connection connection = new FakeConnection(1);
        registry.join(connection, "a");

        assertNotNull(registry.leave(connection));
        assertNull(registry.leave(connection));
        assertEquals(0, registry.count());
        assertFalse(registry.isFull());
    }

    @Test
    void lookupsWorkByConnectionAndByPlayerId() {
        ConnectionRegistry registry = new ConnectionRegistry(4);
        Connection connection = new FakeConnection(7);
        ConnectionRegistry.Entry entry = registry.join(connection, "nova");

        assertEquals(entry, registry.byConnection(connection));
        assertEquals(entry, registry.byPlayerId(entry.playerId()));
        assertNull(registry.byPlayerId(-1));
        assertTrue(registry.hasJoined(connection));
    }

    @Test
    void entriesAreReadOnly() {
        ConnectionRegistry registry = new ConnectionRegistry(4);
        registry.join(new FakeConnection(1), "a");
        assertThrows(UnsupportedOperationException.class, () -> registry.entries().clear());
    }

    @Test
    void rejectsANonPositiveCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new ConnectionRegistry(0));
    }
}
