package io.github.skystrike.server.net;

import com.esotericsoftware.kryonet.Connection;
import io.github.skystrike.shared.net.Packet;

/**
 * Handles exactly one inbound packet type.
 *
 * <p>One type per handler, no switch statements: adding a packet should mean adding a file, not
 * growing a dispatcher. Handlers run on the tick thread and may touch game state directly.
 *
 * @param <T> the packet type this handler consumes
 */
@FunctionalInterface
public interface PacketHandler<T extends Packet> {

    /**
     * @param connection the sender; may or may not correspond to a joined player
     * @param packet the decoded packet
     */
    void handle(Connection connection, T packet);
}
