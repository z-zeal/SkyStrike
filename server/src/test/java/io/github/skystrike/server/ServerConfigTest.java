package io.github.skystrike.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.skystrike.shared.config.NetConfig;
import io.github.skystrike.shared.config.WorldConfig;
import org.junit.jupiter.api.Test;

class ServerConfigTest {

    @Test
    void defaultsComeFromTheSharedContract() {
        ServerConfig config = ServerConfig.defaults();
        assertEquals(NetConfig.DEFAULT_TCP_PORT, config.tcpPort());
        assertEquals(NetConfig.DEFAULT_UDP_PORT, config.udpPort());
        assertEquals(NetConfig.MAX_PLAYERS, config.maxPlayers());
        assertEquals(WorldConfig.TICK_RATE_HZ, config.tickRateHz());
    }

    @Test
    void noArgumentsYieldsTheDefaults() {
        assertEquals(ServerConfig.defaults(), ServerConfig.fromArgs(new String[0]));
    }

    @Test
    void argumentsOverrideIndividualFields() {
        ServerConfig config = ServerConfig.fromArgs(
            new String[] {"--tcp-port", "6000", "--tick-rate", "30", "--max-players", "4"});
        assertEquals(6000, config.tcpPort());
        assertEquals(30, config.tickRateHz());
        assertEquals(4, config.maxPlayers());
        assertEquals(NetConfig.DEFAULT_UDP_PORT, config.udpPort());
    }

    @Test
    void unknownOrIncompleteArgumentsAreRejected() {
        assertThrows(
            IllegalArgumentException.class, () -> ServerConfig.fromArgs(new String[] {"--nope", "1"}));
        assertThrows(
            IllegalArgumentException.class, () -> ServerConfig.fromArgs(new String[] {"--tcp-port"}));
        assertThrows(
            IllegalArgumentException.class, () -> ServerConfig.fromArgs(new String[] {"6000"}));
    }

    @Test
    void outOfRangeValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ServerConfig(0, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new ServerConfig(1, 70000, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new ServerConfig(1, 1, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new ServerConfig(1, 1, 1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new ServerConfig(1, 1, 1, 1, -1));
    }

    @Test
    void snapshotRateDividesTheTickRate() {
        assertEquals(3, NetConfig.ticksPerSnapshot(60));
        assertEquals(1, NetConfig.ticksPerSnapshot(10));
    }
}
