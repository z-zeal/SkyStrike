package io.github.skystrike.net;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.FrameworkMessage;
import com.esotericsoftware.kryonet.Listener;
import io.github.skystrike.shared.config.NetConfig;
import io.github.skystrike.shared.net.NetworkRegistration;
import io.github.skystrike.shared.net.Packet;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * The client half of the transport. Nothing above this class ever sees a network thread.
 *
 * <p><b>The rule, set now because it is unfixable later:</b> the transport's callbacks run on the
 * network thread and do exactly one thing — append a {@link ClientEvent} to a concurrent queue.
 * They never touch game state, the camera, the asset manager or any libGDX object. The render
 * thread calls {@link #drain(Consumer)} once per frame and that is the only place events are
 * interpreted.
 *
 * <p>Connecting blocks until both the TCP and UDP handshakes complete, so it runs on its own
 * short-lived thread and reports back through the same queue.
 */
public final class NetworkClient {

    private final Client client;
    private final Queue<ClientEvent> inbound = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queued = new AtomicInteger();
    private final AtomicInteger dropped = new AtomicInteger();

    private volatile boolean started;
    private volatile Thread connectThread;

    public NetworkClient() {
        this.client = new Client(NetConfig.WRITE_BUFFER_BYTES, NetConfig.OBJECT_BUFFER_BYTES);
        NetworkRegistration.register(client.getKryo());
        client.addListener(new QueueingListener());
    }

    /**
     * Opens a connection in the background. Returns immediately; the outcome arrives as a
     * {@link ClientEvent.Type#CONNECTED} or {@link ClientEvent.Type#FAILED} event.
     */
    public void connect(String host, int tcpPort, int udpPort) {
        if (connectThread != null && connectThread.isAlive()) {
            return;
        }
        if (!started) {
            client.start();
            started = true;
        }
        Thread thread = new Thread(() -> attemptConnect(host, tcpPort, udpPort), "skystrike-connect");
        thread.setDaemon(true);
        connectThread = thread;
        thread.start();
    }

    private void attemptConnect(String host, int tcpPort, int udpPort) {
        try {
            client.connect(NetConfig.CONNECT_TIMEOUT_MS, InetAddress.getByName(host), tcpPort, udpPort);
        } catch (IOException failure) {
            String reason = failure.getMessage() == null
                ? failure.getClass().getSimpleName()
                : failure.getMessage();
            enqueue(ClientEvent.failed(reason));
        }
    }

    public boolean isConnected() {
        return client.isConnected();
    }

    /** Round-trip time in milliseconds as last measured by the transport, or 0 if unknown. */
    public int returnTripTimeMillis() {
        return client.getReturnTripTime();
    }

    /** Reliable send. Silently does nothing while disconnected. */
    public void sendReliable(Packet packet) {
        if (client.isConnected()) {
            client.sendTCP(packet);
        }
    }

    /** Unreliable send, for input and other data the next packet supersedes. */
    public void sendUnreliable(Packet packet) {
        if (client.isConnected()) {
            client.sendUDP(packet);
        }
    }

    /**
     * Hands every event queued since the last call to {@code handler}, in arrival order.
     *
     * <p>Call this from the render thread and nowhere else.
     */
    public void drain(Consumer<ClientEvent> handler) {
        ClientEvent event;
        while ((event = inbound.poll()) != null) {
            queued.decrementAndGet();
            handler.accept(event);
        }
    }

    /** Events discarded because the render thread fell too far behind. */
    public int droppedEvents() {
        return dropped.get();
    }

    /** Closes the connection and stops the network thread. Safe to call more than once. */
    public void close() {
        Thread thread = connectThread;
        if (thread != null) {
            thread.interrupt();
            connectThread = null;
        }
        client.close();
        if (started) {
            client.stop();
            started = false;
        }
        inbound.clear();
        queued.set(0);
    }

    private void enqueue(ClientEvent event) {
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
            enqueue(ClientEvent.connected());
        }

        @Override
        public void disconnected(Connection connection) {
            enqueue(ClientEvent.disconnected());
        }

        @Override
        public void received(Connection connection, Object object) {
            if (object instanceof FrameworkMessage) {
                return;
            }
            enqueue(ClientEvent.received(object));
        }
    }
}
