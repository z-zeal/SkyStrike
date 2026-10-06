package io.github.skystrike.shared.net.c2s;

import io.github.skystrike.shared.net.Packet;

/**
 * Asks the server to run a command line (playable build plan M1 §2.3).
 *
 * <p>It carries the <b>raw line</b> and nothing else — no parsed arguments, no claimed
 * permission, no caller id. The server re-parses from scratch and re-authorises from its own
 * resolved level at execution time, so the most a forged client can buy with a hand-built
 * packet is a well-formatted refusal.
 */
public final class PacketCommandRequest implements Packet {

    /** The command text without its leading slash, exactly as typed. */
    public String line = "";

    public PacketCommandRequest() {
    }

    public PacketCommandRequest(String line) {
        this.line = line == null ? "" : line;
    }

    @Override
    public String toString() {
        return "PacketCommandRequest[lineLength=" + line.length() + "]";
    }
}
