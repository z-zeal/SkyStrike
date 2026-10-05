package io.github.skystrike.server.net.handlers;

import com.esotericsoftware.kryonet.Connection;
import io.github.skystrike.server.chat.ChatService;
import io.github.skystrike.server.net.PacketHandler;
import io.github.skystrike.server.player.PlayerRegistry;
import io.github.skystrike.server.player.PlayerSession;
import io.github.skystrike.shared.net.Packet;
import io.github.skystrike.shared.net.c2s.PacketChatRequest;
import io.github.skystrike.shared.net.s2c.PacketChatMessage;

/**
 * Applies {@link PacketChatRequest}: resolve who actually sent it, hand the body to the
 * authoritative relay, and deliver the result to exactly the connections the relay addressed.
 *
 * <p>The identity used is the one on the session, not the one in the packet — the request format
 * has no author field for that reason. A rejection is silent to everyone except the server log:
 * telling a spammer precisely which rule stopped them is free tuning information.
 */
public final class ChatRequestHandler implements PacketHandler<PacketChatRequest> {

    /** How one addressed packet reaches one player. Supplied by the composition root. */
    @FunctionalInterface
    public interface Dispatch {
        void send(int playerId, Packet packet);
    }

    private final PlayerRegistry players;
    private final ChatService chat;
    private final Dispatch dispatch;

    public ChatRequestHandler(PlayerRegistry players, ChatService chat, Dispatch dispatch) {
        if (players == null || chat == null || dispatch == null) {
            throw new IllegalArgumentException("players, chat and dispatch are required");
        }
        this.players = players;
        this.chat = chat;
        this.dispatch = dispatch;
    }

    @Override
    public void handle(Connection connection, PacketChatRequest packet) {
        if (packet == null) {
            return;
        }
        PlayerSession session = players.byConnection(connection);
        if (session == null) {
            return;
        }

        ChatService.Result result = chat.submit(
            session.playerId(),
            session.name(),
            packet.target,
            packet.body,
            System.currentTimeMillis());

        if (!result.accepted()) {
            System.out.printf(
                "[chat] drop player=%d reason=%s%n", session.playerId(), result.rejection());
            return;
        }

        PacketChatMessage out = new PacketChatMessage(result.message());
        for (Integer recipientId : result.recipientIds()) {
            if (recipientId != null) {
                dispatch.send(recipientId, out);
            }
        }

        System.out.printf(
            "[chat] %s <%s> %s (%d recipients)%n",
            result.message().channel,
            result.message().authorName,
            result.message().body,
            result.recipientIds().size());
    }
}
