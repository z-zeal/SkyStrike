package io.github.skystrike.shared.net.c2s;

import io.github.skystrike.shared.net.Packet;

/**
 * First packet a client sends after the transport handshake completes.
 *
 * <p>The client states the map it is about to play on (protocol 16). The server refuses a join
 * whose stated hash is not the map it is running, which is the only place the two sides can be
 * caught before the match starts: the map's geometry decides what stops a body and a round, so a
 * client on a different map plays a different game, and the destruction records the accept
 * carries are authored indices that only name blocks against the map both sides parsed. The
 * client's own check of the accept still exists, but it is now the second line of one gate
 * rather than the only one.
 *
 * <p>An empty {@code mapHashHex} is not a wildcard: a join that states no map is a join whose
 * map cannot be verified, and every protocol-16 client states one.
 */
public final class PacketJoinRequest implements Packet {

    /** Must equal {@code NetConfig.PROTOCOL_VERSION} or the join is rejected. */
    public int protocolVersion;

    /** Requested display name. The server validates and may trim it. */
    public String playerName;

    /**
     * The hash of the map this client parsed, in the same lower-case hex form the join accept
     * states and {@code ArenaMap.mapHashHex()} renders. Empty only for a client that states no
     * map, which the server refuses.
     */
    public String mapHashHex = "";

    public PacketJoinRequest() {
    }

    public PacketJoinRequest(int protocolVersion, String playerName) {
        this.protocolVersion = protocolVersion;
        this.playerName = playerName;
    }

    public PacketJoinRequest(int protocolVersion, String playerName, String mapHashHex) {
        this.protocolVersion = protocolVersion;
        this.playerName = playerName;
        this.mapHashHex = mapHashHex;
    }

    @Override
    public String toString() {
        return "PacketJoinRequest[protocolVersion=" + protocolVersion
            + ", playerName=" + playerName + ", mapHashHex=" + mapHashHex + "]";
    }
}
