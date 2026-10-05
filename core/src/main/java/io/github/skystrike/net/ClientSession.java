package io.github.skystrike.net;

import io.github.skystrike.shared.config.NetConfig;
import io.github.skystrike.shared.net.c2s.PacketJoinRequest;
import io.github.skystrike.shared.net.c2s.PacketLeaveRequest;
import io.github.skystrike.shared.net.c2s.PacketPing;
import io.github.skystrike.shared.net.s2c.PacketGameState;
import io.github.skystrike.shared.net.s2c.PacketJoinAccept;
import io.github.skystrike.shared.net.s2c.PacketJoinReject;
import io.github.skystrike.shared.net.s2c.PacketPong;

/**
 * The connect-and-join state machine, driven from the render thread.
 *
 * <p>Owns a {@link NetworkClient} and turns the queued transport events into the handful of
 * values the rest of the client cares about: are we connected, who are we, what tick is the
 * server on. Screens read those; they never see packets.
 *
 * <p>Keeping this out of the screen is what lets {@code GameScreen} stay a composition root
 * instead of slowly becoming the client.
 */
public final class ClientSession {

    private static final float PING_INTERVAL_SECONDS = 1f;

    private final NetworkClient client = new NetworkClient();
    private final String playerName;

    private ConnectionState state = ConnectionState.OFFLINE;
    private String statusDetail = "";
    private int playerId = -1;
    private String acceptedName = "";
    private int serverTickRateHz;
    private long serverTick;
    private int serverPlayerCount;
    private long snapshotsReceived;
    private int latencyMillis = -1;

    private float sincePing;

    public ClientSession(String playerName) {
        this.playerName = playerName;
    }

    /** Starts dialling. Does nothing if already connecting or connected. */
    public void connect(String host, int tcpPort, int udpPort) {
        if (state == ConnectionState.CONNECTING
            || state == ConnectionState.JOINING
            || state == ConnectionState.JOINED) {
            return;
        }
        state = ConnectionState.CONNECTING;
        statusDetail = host + ":" + tcpPort;
        client.connect(host, tcpPort, udpPort);
    }

    /** Drains the inbound queue and keeps the latency probe ticking. Render thread only. */
    public void update(float delta) {
        client.drain(this::apply);

        if (state == ConnectionState.JOINED) {
            sincePing += delta;
            if (sincePing >= PING_INTERVAL_SECONDS) {
                sincePing = 0f;
                client.sendReliable(new PacketPing(System.currentTimeMillis()));
            }
        }
    }

    private void apply(ClientEvent event) {
        switch (event.type()) {
            case CONNECTED -> {
                state = ConnectionState.JOINING;
                statusDetail = "";
                client.sendReliable(new PacketJoinRequest(NetConfig.PROTOCOL_VERSION, playerName));
            }
            case DISCONNECTED -> {
                state = ConnectionState.OFFLINE;
                statusDetail = "connection closed";
                resetSessionState();
            }
            case FAILED -> {
                state = ConnectionState.FAILED;
                statusDetail = String.valueOf(event.payload());
                resetSessionState();
            }
            case RECEIVED -> applyPacket(event.payload());
            default -> throw new IllegalStateException("unhandled event type: " + event.type());
        }
    }

    private void applyPacket(Object payload) {
        if (payload instanceof PacketJoinAccept accept) {
            state = ConnectionState.JOINED;
            statusDetail = "";
            playerId = accept.playerId;
            acceptedName = accept.playerName;
            serverTickRateHz = accept.tickRateHz;
            serverTick = accept.serverTick;
        } else if (payload instanceof PacketJoinReject reject) {
            state = ConnectionState.FAILED;
            statusDetail = reject.reason;
            resetSessionState();
        } else if (payload instanceof PacketGameState snapshot) {
            serverTick = snapshot.tick;
            serverPlayerCount = snapshot.playerCount;
            snapshotsReceived++;
        } else if (payload instanceof PacketPong pong) {
            serverTick = pong.serverTick;
            latencyMillis = (int) (System.currentTimeMillis() - pong.clientTimeMillis);
        }
        // Anything else is a packet this build does not understand yet; ignoring it is correct.
    }

    private void resetSessionState() {
        playerId = -1;
        acceptedName = "";
        serverPlayerCount = 0;
        latencyMillis = -1;
        sincePing = 0f;
    }

    /** Tells the server we are leaving, then tears the transport down. */
    public void disconnect() {
        if (state == ConnectionState.JOINED || state == ConnectionState.JOINING) {
            client.sendReliable(new PacketLeaveRequest());
        }
        client.close();
        state = ConnectionState.OFFLINE;
        statusDetail = "";
        resetSessionState();
    }

    public ConnectionState state() {
        return state;
    }

    public int playerId() {
        return playerId;
    }

    public String acceptedName() {
        return acceptedName;
    }

    public long serverTick() {
        return serverTick;
    }

    public int serverTickRateHz() {
        return serverTickRateHz;
    }

    public int serverPlayerCount() {
        return serverPlayerCount;
    }

    public long snapshotsReceived() {
        return snapshotsReceived;
    }

    public int latencyMillis() {
        return latencyMillis;
    }

    /** One line suitable for the Phase 0 on-screen readout. */
    public String statusLine() {
        StringBuilder line = new StringBuilder(state.label());
        if (!statusDetail.isEmpty()) {
            line.append(" (").append(statusDetail).append(')');
        }
        if (state == ConnectionState.JOINED) {
            line.append("  id=").append(playerId)
                .append("  tick=").append(serverTick)
                .append("  players=").append(serverPlayerCount);
            if (latencyMillis >= 0) {
                line.append("  rtt=").append(latencyMillis).append("ms");
            }
        }
        return line.toString();
    }
}
