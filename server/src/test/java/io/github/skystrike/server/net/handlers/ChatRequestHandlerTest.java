package io.github.skystrike.server.net.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.esotericsoftware.kryonet.Connection;
import io.github.skystrike.server.chat.ChatService;
import io.github.skystrike.server.player.PlayerRegistry;
import io.github.skystrike.server.player.PlayerSession;
import io.github.skystrike.shared.net.Packet;
import io.github.skystrike.shared.net.c2s.PacketChatRequest;
import io.github.skystrike.shared.net.s2c.PacketChatMessage;
import io.github.skystrike.shared.text.ChatChannel;
import io.github.skystrike.shared.text.ChatTarget;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The handler's own job: use the session's identity rather than anything in the packet, and
 * deliver to exactly the ids the relay addressed.
 */
class ChatRequestHandlerTest {

    private static final class FakeConnection extends Connection {
        private final int id;

        FakeConnection(int id) {
            this.id = id;
        }

        @Override
        public int getID() {
            return id;
        }
    }

    /** The real registry, read exactly as the composition root reads it. */
    private record RegistryRoster(PlayerRegistry players) implements ChatService.Roster {

        @Override
        public Collection<Integer> playerIds() {
            List<Integer> ids = new ArrayList<>();
            for (PlayerSession session : players.all()) {
                ids.add(session.playerId());
            }
            return ids;
        }

        @Override
        public int teamIndexOf(int playerId) {
            PlayerSession session = players.byPlayerId(playerId);
            return session == null ? ChatService.NOT_JOINED : session.player().teamIndex;
        }
    }

    private record Delivery(int playerId, Packet packet) {
    }

    private final List<Delivery> delivered = new ArrayList<>();
    private PlayerRegistry registry;
    private ChatService chat;
    private ChatRequestHandler handler;
    private Connection novaConnection;

    @BeforeEach
    void setUp() {
        delivered.clear();
        registry = new PlayerRegistry();
        chat = new ChatService(new RegistryRoster(registry));
        handler = new ChatRequestHandler(
            registry, chat, (playerId, packet) -> delivered.add(new Delivery(playerId, packet)));

        novaConnection = new FakeConnection(1);
        registry.register(novaConnection, 1, "Nova", 0, 0f, 0f);
        registry.register(new FakeConnection(2), 2, "Ash", 0, 0f, 0f);
        registry.register(new FakeConnection(3), 3, "Rook", 1, 0f, 0f);
    }

    @Test
    @DisplayName("an ALL line is delivered to every joined player")
    void allLinesGoEverywhere() {
        handler.handle(novaConnection, new PacketChatRequest(ChatTarget.ALL, "hello"));

        assertEquals(3, delivered.size());
        PacketChatMessage packet = (PacketChatMessage) delivered.get(0).packet();
        assertEquals(ChatChannel.ALL, packet.message.channel);
        assertEquals("Nova", packet.message.authorName);
        assertEquals("hello", packet.message.body);
    }

    @Test
    @DisplayName("a TEAM line never reaches the other team's connection")
    void teamLinesNeverReachTheOtherTeam() {
        handler.handle(novaConnection, new PacketChatRequest(ChatTarget.TEAM, "rotating B"));

        // Sorted, because the registry is a concurrent map and delivery order is not part of
        // the contract — who receives it is.
        List<Integer> recipients = delivered.stream().map(Delivery::playerId).sorted().toList();
        assertEquals(List.of(1, 2), recipients);
    }

    @Test
    @DisplayName("the author is the session, not the packet — there is no field to forge")
    void identityComesFromTheSession() {
        handler.handle(novaConnection, new PacketChatRequest(ChatTarget.ALL, "hi"));

        PacketChatMessage packet = (PacketChatMessage) delivered.get(0).packet();
        assertEquals(1, packet.message.authorId);
        assertEquals("Nova", packet.message.authorName);
        assertEquals(0, packet.message.authorTeamIndex);
    }

    @Test
    @DisplayName("a connection that never joined is ignored entirely")
    void unjoinedConnectionsAreIgnored() {
        handler.handle(new FakeConnection(99), new PacketChatRequest(ChatTarget.ALL, "hello"));
        assertTrue(delivered.isEmpty());
    }

    @Test
    @DisplayName("a rejected line is delivered to nobody")
    void rejectedLinesAreNotDelivered() {
        handler.handle(novaConnection, new PacketChatRequest(ChatTarget.ALL, "   "));
        assertTrue(delivered.isEmpty());

        handler.handle(novaConnection, null);
        assertTrue(delivered.isEmpty());
    }

    @Test
    @DisplayName("a muted player's line reaches nobody, including themselves")
    void mutedPlayersReachNobody() {
        chat.moderation().mute(1);
        handler.handle(novaConnection, new PacketChatRequest(ChatTarget.ALL, "hello"));
        assertTrue(delivered.isEmpty());
    }
}
