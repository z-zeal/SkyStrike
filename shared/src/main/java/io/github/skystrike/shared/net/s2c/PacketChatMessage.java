package io.github.skystrike.shared.net.s2c;

import io.github.skystrike.shared.net.Packet;
import io.github.skystrike.shared.text.ChatMessage;

/**
 * One accepted line, delivered to one recipient.
 *
 * <p>Team scoping is a <b>send-side</b> decision: a team line is addressed only to the sockets
 * belonging to that team, so there is nothing for a modified client to filter out locally. The
 * packet carries no "intended audience" field for exactly that reason — if you received it, it
 * was meant for you.
 */
public final class PacketChatMessage implements Packet {

    public ChatMessage message = new ChatMessage();

    public PacketChatMessage() {
    }

    public PacketChatMessage(ChatMessage message) {
        this.message = message == null ? new ChatMessage() : message;
    }

    @Override
    public String toString() {
        return "PacketChatMessage[" + message + "]";
    }
}
