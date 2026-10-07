package io.github.skystrike.net;

import io.github.skystrike.shared.config.NetConfig;
import io.github.skystrike.shared.net.Packet;
import io.github.skystrike.shared.net.c2s.PacketCommandRequest;
import io.github.skystrike.shared.net.c2s.PacketJoinRequest;
import io.github.skystrike.shared.net.c2s.PacketLeaveRequest;
import io.github.skystrike.shared.net.c2s.PacketPing;
import io.github.skystrike.shared.net.s2c.PacketCapabilities;
import io.github.skystrike.shared.net.s2c.PacketChatMessage;
import io.github.skystrike.shared.net.s2c.PacketCommandResponse;
import io.github.skystrike.shared.net.s2c.PacketDamageEvent;
import io.github.skystrike.shared.net.s2c.PacketGameState;
import io.github.skystrike.shared.net.s2c.PacketJoinAccept;
import io.github.skystrike.shared.net.s2c.PacketJoinReject;
import io.github.skystrike.shared.net.s2c.PacketKillEvent;
import io.github.skystrike.shared.net.s2c.PacketPong;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

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

    /** How many kill feed entries the client keeps. The HUD shows fewer; Phase 7 owns that. */
    private static final int KILL_FEED_CAPACITY = 8;

    /** How long a hit marker stays lit, in seconds. */
    private static final float HIT_MARKER_SECONDS = 0.35f;

    private final NetworkClient client = new NetworkClient();
    private final String playerName;

    private ConnectionState state = ConnectionState.OFFLINE;
    private String statusDetail = "";
    /** Non-null only when the server refused the join request, not on a transport failure. */
    private String joinRejectReason;
    /** A NetworkClient was started and must be closed before this session can be forgotten. */
    private boolean transportStarted;
    private int playerId = -1;
    private String acceptedName = "";
    private int serverTickRateHz;
    private long serverTick;
    private int serverPlayerCount;
    private long snapshotsReceived;
    private int latencyMillis = -1;
    private PacketGameState latestSnapshot;
    private Consumer<PacketGameState> snapshotListener;
    private Consumer<PacketDamageEvent> damageListener;
    private Consumer<PacketKillEvent> killListener;
    private Consumer<io.github.skystrike.shared.text.ChatMessage> chatListener;
    private Consumer<PacketCapabilities> capabilityListener;
    private Consumer<PacketCommandResponse> commandResponseListener;
    private Runnable sessionResetListener;

    private final Deque<PacketKillEvent> killFeed = new ArrayDeque<>();
    private PacketDamageEvent lastDamageDealt;
    private PacketDamageEvent lastDamageTaken;
    private float hitMarkerTimer;
    private boolean hitMarkerHeadshot;
    private boolean hitMarkerLethal;

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
        joinRejectReason = null;
        transportStarted = true;
        client.connect(host, tcpPort, udpPort);
    }

    /** Sends a packet reliably over TCP. */
    public void sendReliable(Packet packet) {
        client.sendReliable(packet);
    }

    /** Sends a packet unreliably over UDP. */
    public void sendUnreliable(Packet packet) {
        client.sendUnreliable(packet);
    }

    public void setSnapshotListener(Consumer<PacketGameState> listener) {
        this.snapshotListener = listener;
    }

    /** Called on the render thread for every damage event this client is party to. */
    public void setDamageListener(Consumer<PacketDamageEvent> listener) {
        this.damageListener = listener;
    }

    /** Called on the render thread for every kill in the match. */
    public void setKillListener(Consumer<PacketKillEvent> listener) {
        this.killListener = listener;
    }

    /**
     * Called on the render thread for every chat line addressed to this client.
     *
     * <p>Team scoping already happened server-side: if it arrived, it was meant for us.
     */
    public void setChatListener(Consumer<io.github.skystrike.shared.text.ChatMessage> listener) {
        this.chatListener = listener;
    }

    /** Called on the render thread whenever the server pushes a capability change. */
    public void setCapabilityListener(Consumer<PacketCapabilities> listener) {
        this.capabilityListener = listener;
    }

    /** Called on the render thread for every command response the server sends back. */
    public void setCommandResponseListener(Consumer<PacketCommandResponse> listener) {
        this.commandResponseListener = listener;
    }

    /** Called whenever a connection attempt or joined session is torn down. */
    public void setSessionResetListener(Runnable listener) {
        this.sessionResetListener = listener;
    }

    /**
     * Sends one command line to the server (playable build plan M1 §2.6).
     *
     * <p>The payload is the raw line only — no capabilities, no claimed identity. The server
     * re-parses and re-authorises everything against its own session state.
     */
    public void sendCommand(String rawLine) {
        if (state != ConnectionState.JOINED || rawLine == null || rawLine.isBlank()) {
            return;
        }
        client.sendReliable(new PacketCommandRequest(rawLine));
    }

    public PacketGameState latestSnapshot() {
        return latestSnapshot;
    }

    /** Drains the inbound queue and keeps the latency probe ticking. Render thread only. */
    public void update(float delta) {
        client.drain(this::apply);

        if (hitMarkerTimer > 0f) {
            hitMarkerTimer = Math.max(0f, hitMarkerTimer - delta);
        }

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
                // JoinReject is followed by a server close. Preserve the authoritative reason
                // rather than replacing it with the transport's less useful "connection closed".
                if (joinRejectReason != null) {
                    state = ConnectionState.FAILED;
                    statusDetail = joinRejectReason;
                } else {
                    state = ConnectionState.OFFLINE;
                    statusDetail = "connection closed";
                }
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
            joinRejectReason = reject.reason == null || reject.reason.isBlank()
                ? "server rejected the join request" : reject.reason;
            statusDetail = joinRejectReason;
            resetSessionState();
        } else if (payload instanceof PacketGameState snapshot) {
            serverTick = snapshot.tick;
            serverPlayerCount = snapshot.playerCount;
            snapshotsReceived++;
            latestSnapshot = snapshot;
            if (snapshotListener != null) {
                snapshotListener.accept(snapshot);
            }
        } else if (payload instanceof PacketDamageEvent damage) {
            applyDamageEvent(damage);
        } else if (payload instanceof PacketKillEvent kill) {
            killFeed.addLast(kill);
            while (killFeed.size() > KILL_FEED_CAPACITY) {
                killFeed.removeFirst();
            }
            if (killListener != null) {
                killListener.accept(kill);
            }
        } else if (payload instanceof PacketChatMessage chat) {
            if (chatListener != null && chat.message != null) {
                chatListener.accept(chat.message);
            }
        } else if (payload instanceof PacketCapabilities caps) {
            if (capabilityListener != null) {
                capabilityListener.accept(caps);
            }
        } else if (payload instanceof PacketCommandResponse response) {
            if (commandResponseListener != null) {
                commandResponseListener.accept(response);
            }
        } else if (payload instanceof PacketPong pong) {
            serverTick = pong.serverTick;
            latencyMillis = (int) (System.currentTimeMillis() - pong.clientTimeMillis);
        }
    }

    /**
     * Records a damage event.
     *
     * <p>The server sends each event to both parties, so the same packet can be "I hit someone"
     * and "someone hit me" — and with self-damage on it can be both at once. The hit marker only
     * lights for damage this client actually dealt to somebody else.
     */
    private void applyDamageEvent(PacketDamageEvent damage) {
        if (damage.attackerId == playerId) {
            lastDamageDealt = damage;
            if (damage.targetId != playerId) {
                hitMarkerTimer = HIT_MARKER_SECONDS;
                hitMarkerHeadshot = damage.isHeadshot();
                hitMarkerLethal = damage.killed;
            }
        }
        if (damage.targetId == playerId) {
            lastDamageTaken = damage;
        }
        if (damageListener != null) {
            damageListener.accept(damage);
        }
    }

    /** Most recent kills first. */
    public List<PacketKillEvent> killFeed() {
        List<PacketKillEvent> entries = new ArrayList<>(killFeed);
        Collections.reverse(entries);
        return entries;
    }

    public PacketDamageEvent lastDamageDealt() {
        return lastDamageDealt;
    }

    public PacketDamageEvent lastDamageTaken() {
        return lastDamageTaken;
    }

    /** True while the hit marker should be drawn. */
    public boolean hitMarkerActive() {
        return hitMarkerTimer > 0f;
    }

    /** Hit marker fade, 1 at the moment of the hit down to 0. */
    public float hitMarkerAlpha() {
        return hitMarkerTimer / HIT_MARKER_SECONDS;
    }

    public boolean hitMarkerHeadshot() {
        return hitMarkerActive() && hitMarkerHeadshot;
    }

    public boolean hitMarkerLethal() {
        return hitMarkerActive() && hitMarkerLethal;
    }

    private void resetSessionState() {
        playerId = -1;
        acceptedName = "";
        serverPlayerCount = 0;
        latencyMillis = -1;
        latestSnapshot = null;
        sincePing = 0f;
        killFeed.clear();
        lastDamageDealt = null;
        lastDamageTaken = null;
        hitMarkerTimer = 0f;
        hitMarkerHeadshot = false;
        hitMarkerLethal = false;
        if (sessionResetListener != null) {
            sessionResetListener.run();
        }
    }

    /**
     * Tells the server we are leaving, then tears the transport down. The owning screen may
     * reach this method again during disposal; only the first call closes the transport and
     * resets listeners, so one transition has one teardown.
     */
    public void disconnect() {
        if (!transportStarted) {
            return;
        }
        if (state == ConnectionState.JOINED || state == ConnectionState.JOINING) {
            client.sendReliable(new PacketLeaveRequest());
        }
        transportStarted = false;
        client.close();
        state = ConnectionState.OFFLINE;
        statusDetail = "";
        joinRejectReason = null;
        resetSessionState();
    }

    /** The server's JoinReject text, retained across its immediate close until retry/cancel. */
    public String joinRejectReason() {
        return joinRejectReason;
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
