package io.github.skystrike.server.net;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.FrameworkMessage;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import io.github.skystrike.shared.config.NetConfig;
import io.github.skystrike.shared.net.NetworkRegistration;
import io.github.skystrike.shared.net.Packet;
import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Owns the transport: binding, the KryoNet listener, and the hand-off to the simulation.
 *
 * <p><b>Threading contract.</b> KryoNet calls the listener on its own update thread. That thread
 * does exactly one thing here — append to {@link #inbound} — and never touches game state. The
 * tick thread calls {@link #drain(Consumer)} once per tick and is the only place events are
 * interpreted. Sending is safe from either thread.
 */
public final class NetworkEndpoint {

    private final Server server;
    private final Queue<NetworkEvent> inbound = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queued = new AtomicInteger();
    private final AtomicInteger dropped = new AtomicInteger();

    private final int tcpPort;
    private final int udpPort;
    private volatile boolean started;

    public NetworkEndpoint(int tcpPort, int udpPort) {
        this.tcpPort = tcpPort;
        this.udpPort = udpPort;
        this.server = new Server(NetConfig.WRITE_BUFFER_BYTES, NetConfig.OBJECT_BUFFER_BYTES);
        NetworkRegistration.register(server.getKryo());
        server.addListener(new QueueingListener());
    }

    /** Starts the network thread and binds both ports. */
    public void start() throws IOException {
        server.start();
        server.bind(tcpPort, udpPort);
        started = true;
    }

    /** Closes every connection and stops the network thread. Idempotent. */
    public void stop() {
        started = false;
        server.stop();
        inbound.clear();
        queued.set(0);
    }

    public boolean isStarted() {
        return started;
    }

    public int tcpPort() {
        return tcpPort;
    }

    public int udpPort() {
        return udpPort;
    }

    /**
     * Hands every event queued since the last call to {@code handler}, in arrival order.
     *
     * <p>Call this from the tick thread and nowhere else.
     */
    public void drain(Consumer<NetworkEvent> handler) {
        NetworkEvent event;
        while ((event = inbound.poll()) != null) {
            queued.decrementAndGet();
            handler.accept(event);
        }
    }

    /** Reliable send to one connection. */
    public void sendReliable(Connection connection, Packet packet) {
        connection.sendTCP(packet);
    }

    /** Unreliable send to one connection, for state that the next snapshot supersedes anyway. */
    public void sendUnreliable(Connection connection, Packet packet) {
        connection.sendUDP(packet);
    }

    /** Reliable broadcast to everyone currently connected. */
    public void broadcastReliable(Packet packet) {
        server.sendToAllTCP(packet);
    }

    /** Unreliable broadcast to everyone currently connected. */
    public void broadcastUnreliable(Packet packet) {
        server.sendToAllUDP(packet);
    }

    /** Packets discarded because the tick thread fell too far behind. */
    public int droppedPackets() {
        return dropped.get();
    }

    private void enqueue(NetworkEvent event) {
        if (queued.get() >= NetConfig.INBOUND_QUEUE_CAPACITY) {
            if (inbound.poll() != null) {
                queued.decrementAndGet();
                dropped.incrementAndGet();
            }
        }
        inbound.add(event);
        queued.incrementAndGet();
    }

    /** The only code that runs on the network thread. It must stay this small. */
    private final class QueueingListener implements Listener {

        @Override
        public void connected(Connection connection) {
            enqueue(NetworkEvent.connected(connection));
        }

        @Override
        public void disconnected(Connection connection) {
            enqueue(NetworkEvent.disconnected(connection));
        }

        @Override
        public void received(Connection connection, Object object) {
            if (object instanceof FrameworkMessage) {
                return;
            }
            enqueue(NetworkEvent.received(connection, object));
        }
    }
}
