package io.github.skystrike.shared.net.c2s;

import io.github.skystrike.shared.net.Packet;

/** First packet a client sends after the transport handshake completes. */
public final class PacketJoinRequest implements Packet {

    /** Must equal {@code NetConfig.PROTOCOL_VERSION} or the join is rejected. */
    public int protocolVersion;

    /** Requested display name. The server validates and may trim it. */
    public String playerName;

    public PacketJoinRequest() {
    }

    public PacketJoinRequest(int protocolVersion, String playerName) {
        this.protocolVersion = protocolVersion;
        this.playerName = playerName;
    }

    @Override
    public String toString() {
        return "PacketJoinRequest[protocolVersion=" + protocolVersion + ", playerName=" + playerName + "]";
    }
}
