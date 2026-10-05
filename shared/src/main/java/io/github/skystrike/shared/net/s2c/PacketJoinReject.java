package io.github.skystrike.shared.net.s2c;

import io.github.skystrike.shared.net.Packet;

/**
 * Refused join. The server closes the connection straight after sending this, so the reason is
 * carried as plain text the client can show verbatim.
 */
public final class PacketJoinReject implements Packet {

    public static final String REASON_PROTOCOL_MISMATCH = "protocol version mismatch";
    public static final String REASON_SERVER_FULL = "server is full";
    public static final String REASON_INVALID_NAME = "invalid player name";
    public static final String REASON_ALREADY_JOINED = "already joined";

    public String reason;

    public PacketJoinReject() {
    }

    public PacketJoinReject(String reason) {
        this.reason = reason;
    }

    @Override
    public String toString() {
        return "PacketJoinReject[reason=" + reason + "]";
    }
}
