package io.github.skystrike.server.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.esotericsoftware.kryonet.Connection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlayerRegistryTest {

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
    void registersAndLooksUpPlayers() {
        PlayerRegistry registry = new PlayerRegistry();
        Connection c1 = new FakeConnection(10);
        Connection c2 = new FakeConnection(20);

        PlayerSession s1 = registry.register(c1, 1, "Alice", 0, 100f, 100f);
        PlayerSession s2 = registry.register(c2, 2, "Bob", 1, 200f, 100f);

        assertEquals(2, registry.count());
        assertEquals(s1, registry.byPlayerId(1));
        assertEquals(s2, registry.byPlayerId(2));
        assertEquals(s1, registry.byConnection(c1));
        assertEquals(s2, registry.byConnection(c2));
    }

    @Test
    void teamCountingTracksActiveSessions() {
        PlayerRegistry registry = new PlayerRegistry();
        registry.register(new FakeConnection(1), 1, "Alice", 0, 100f, 100f);
        registry.register(new FakeConnection(2), 2, "Bob", 1, 200f, 100f);
        registry.register(new FakeConnection(3), 3, "Charlie", 0, 100f, 100f);

        assertEquals(2, registry.teamCount(0));
        assertEquals(1, registry.teamCount(1));
    }

    @Test
    void removingByConnectionOrIdFreesEntry() {
        PlayerRegistry registry = new PlayerRegistry();
        Connection conn = new FakeConnection(1);
        registry.register(conn, 1, "Alice", 0, 100f, 100f);

        assertNotNull(registry.remove(conn));
        assertEquals(0, registry.count());
        assertNull(registry.byPlayerId(1));
        assertNull(registry.byConnection(conn));
    }

    @Test
    @DisplayName("anyCheatActive reflects any session's noclip, godmode or infinite-ammo flag")
    void anyCheatActiveReflectsAnySessionFlag() {
        PlayerRegistry registry = new PlayerRegistry();
        PlayerSession alice = registry.register(new FakeConnection(1), 1, "Alice", 0, 100f, 100f);
        registry.register(new FakeConnection(2), 2, "Bob", 1, 200f, 100f);

        assertFalse(registry.anyCheatActive(), "no session has a toggle on yet");

        alice.setInfiniteAmmo(true);
        assertTrue(registry.anyCheatActive());

        alice.setInfiniteAmmo(false);
        assertFalse(registry.anyCheatActive());

        alice.setNoclip(true);
        assertTrue(registry.anyCheatActive());
        alice.setNoclip(false);

        alice.setGodmode(true);
        assertTrue(registry.anyCheatActive());
    }
}
