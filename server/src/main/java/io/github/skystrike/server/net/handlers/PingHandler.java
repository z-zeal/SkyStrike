package io.github.skystrike.server.net.handlers;

import com.esotericsoftware.kryonet.Connection;
import io.github.skystrike.server.net.NetworkEndpoint;
import io.github.skystrike.server.net.PacketHandler;
import io.github.skystrike.server.sim.SimulationClock;
import io.github.skystrike.shared.net.c2s.PacketPing;
import io.github.skystrike.shared.net.s2c.PacketPong;

/**
 * Echoes a ping back with the current tick.
 *
 * <p>The client's stamp is copied through untouched; the server never interprets it, so the two
 * clocks do not have to agree for latency to be measurable.
 */
public final class PingHandler implements PacketHandler<PacketPing> {

    private final NetworkEndpoint endpoint;
    private final SimulationClock clock;

    public PingHandler(NetworkEndpoint endpoint, SimulationClock clock) {
        this.endpoint = endpoint;
        this.clock = clock;
    }

    @Override
    public void handle(Connection connection, PacketPing packet) {
        endpoint.sendReliable(connection, new PacketPong(packet.clientTimeMillis, clock.tick()));
    }
}
