package io.github.skystrike.shared.net.c2s;

import io.github.skystrike.shared.net.Packet;
import io.github.skystrike.shared.text.ChatTarget;

/**
 * Client asks the server to say something.
 *
 * <p>It is a <i>request</i>, not a message: the client contributes a target and a raw body and
 * nothing else. The author id, the author name, the team used for scoping and the timestamp are
 * all filled in by the authority from its own record, so a modified client cannot speak as
 * somebody else, cannot place itself on the other team to read their chat, and cannot forge a
 * console channel.
 *
 * <p>The body is sent raw and sanitised server-side. The client sanitises too, but only so the
 * player sees what will actually be sent — the server never trusts that it happened.
 */
public final class PacketChatRequest implements Packet {

    /** Which of the two chat destinations the dialog's toggle button is on. */
    public ChatTarget target = ChatTarget.ALL;

    /** Raw, unsanitised, possibly oversized body. The server fixes all three. */
    public String body = "";

    public PacketChatRequest() {
    }

    public PacketChatRequest(ChatTarget target, String body) {
        this.target = target == null ? ChatTarget.ALL : target;
        this.body = body == null ? "" : body;
    }

    @Override
    public String toString() {
        return "PacketChatRequest[target=" + target + ", bodyLength=" + body.length() + "]";
    }
}
