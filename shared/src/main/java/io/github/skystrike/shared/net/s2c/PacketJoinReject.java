package io.github.skystrike.shared.net.s2c;

import io.github.skystrike.shared.net.Packet;

/**
 * Refused join. The server closes the connection straight after sending this, so the reason is
 * carried as plain text the client can show verbatim.
 *
 * <p>Since protocol 16 the packet can also state <i>which</i> map the server is running: a map
 * mismatch is the one refusal whose answer is not in the reason text alone — "map hash mismatch"
 * without the hash the server expected leaves the player with nothing to compare against — and
 * the field is left blank by every other refusal, exactly as the reason text is the only thing
 * they can say.
 */
public final class PacketJoinReject implements Packet {

    public static final String REASON_PROTOCOL_MISMATCH = "protocol version mismatch";
    public static final String REASON_SERVER_FULL = "server is full";
    public static final String REASON_INVALID_NAME = "invalid player name";
    public static final String REASON_ALREADY_JOINED = "already joined";

    /** The client's stated map is not the map this server is running. */
    public static final String REASON_MAP_MISMATCH = "map hash mismatch";

    public String reason;

    /**
     * The authored map's hash in hex, stated on a {@link #REASON_MAP_MISMATCH} refusal so the
     * client can print both sides of the difference. Empty for every other reason.
     */
    public String serverMapHashHex = "";

    public PacketJoinReject() {
    }

    public PacketJoinReject(String reason) {
        this.reason = reason;
    }

    public PacketJoinReject(String reason, String serverMapHashHex) {
        this.reason = reason;
        this.serverMapHashHex = serverMapHashHex;
    }

    @Override
    public String toString() {
        return "PacketJoinReject[reason=" + reason
            + ", serverMapHashHex=" + serverMapHashHex + "]";
    }
}
