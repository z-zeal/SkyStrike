package io.github.skystrike.chat;

import io.github.skystrike.command.ClientCapabilities;
import io.github.skystrike.shared.net.Packet;
import io.github.skystrike.shared.net.c2s.PacketChatRequest;
import io.github.skystrike.shared.text.ChatMessage;
import io.github.skystrike.shared.text.ChatTarget;
import io.github.skystrike.shared.text.TextSanitizer;
import io.github.skystrike.ui.text.MessageBuffer;
import io.github.skystrike.ui.text.MessageLine;
import io.github.skystrike.ui.text.MessageSeverity;
import java.util.function.Consumer;

/**
 * The client half of chat: send a line, receive the delivered ones, file them in the scrollback
 * ring (console plan §11 build-order Phase 2).
 *
 * <p><b>There is no local echo.</b> A sent line appears when the authority sends it back, because
 * the sender is always one of the recipients. Echoing locally and reconciling later is the
 * standard way to end up with a message that was visibly said and never actually delivered —
 * worse here than in most games, because a team line that silently failed is a tactical lie.
 * The round trip is one reliable hop and the dialog can show a pending state if it ever matters.
 *
 * <p>The target lives on the dialog's toggle button, not here; this class is handed one per send.
 */
public final class ChatClient {

    private final MessageBuffer buffer;
    private final ChatMuteList muteList;
    private final ClientCapabilities capabilities;
    private Consumer<Packet> sender;
    private Runnable onMessage;

    private int localPlayerId = ChatMessage.NO_AUTHOR;
    private long sentCount;
    private long receivedCount;
    private long suppressedCount;

    public ChatClient(MessageBuffer buffer, ChatMuteList muteList, ClientCapabilities capabilities) {
        if (buffer == null || muteList == null || capabilities == null) {
            throw new IllegalArgumentException("buffer, muteList and capabilities are required");
        }
        this.buffer = buffer;
        this.muteList = muteList;
        this.capabilities = capabilities;
    }

    /** Where outbound requests go. Set once by the composition root. */
    public void setSender(Consumer<Packet> sender) {
        this.sender = sender;
    }

    /** Called after each accepted inbound line, so the dialog can unfade its passive view. */
    public void setMessageListener(Runnable onMessage) {
        this.onMessage = onMessage;
    }

    /** The local player, so their own lines are never hidden by the mute list. */
    public void setLocalPlayerId(int localPlayerId) {
        this.localPlayerId = localPlayerId;
    }

    public MessageBuffer buffer() {
        return buffer;
    }

    public ChatMuteList muteList() {
        return muteList;
    }

    /**
     * Sends one chat line.
     *
     * <p>Sanitises first — not because the server trusts it, but so the player never watches a
     * line leave that the authority is about to silently rewrite. A line that sanitises to
     * nothing is not sent at all.
     *
     * @return true when a request actually went out
     */
    public boolean send(ChatTarget target, String text) {
        if (sender == null) {
            return false;
        }
        String body = TextSanitizer.sanitizeChatBody(ClientCapabilities.chatBodyFor(text));
        if (body.isEmpty()) {
            return false;
        }
        sender.accept(new PacketChatRequest(target == null ? ChatTarget.ALL : target, body));
        sentCount++;
        return true;
    }

    /**
     * Files one delivered message. Render thread only — the transport queues and this runs on
     * the drain.
     */
    public void receive(ChatMessage message) {
        if (message == null) {
            return;
        }
        receivedCount++;

        // Console-only channels cannot arrive without access, but filtering again here costs
        // nothing and keeps the "no console means no engine channels" rule true even if a
        // capability revocation and an in-flight line cross paths.
        if (!message.isVisibleTo(capabilities.consoleAccess())) {
            suppressedCount++;
            return;
        }
        if (message.authorId != localPlayerId && muteList.isMuted(message.authorId)) {
            suppressedCount++;
            return;
        }

        buffer.add(new MessageLine(
            message.timestampMillis,
            message.channel,
            message.authorId,
            message.authorName,
            message.authorTeam(),
            message.body,
            MessageSeverity.INFO));

        if (onMessage != null) {
            onMessage.run();
        }
    }

    /** Files a client-side notice through the same path, so it wraps and contrasts identically. */
    public void addSystemLine(long timestampMillis, io.github.skystrike.shared.text.ChatChannel channel,
                              String body, MessageSeverity severity) {
        buffer.add(MessageLine.system(timestampMillis, channel, body, severity));
        if (onMessage != null) {
            onMessage.run();
        }
    }

    public long sentCount() {
        return sentCount;
    }

    public long receivedCount() {
        return receivedCount;
    }

    /** Lines dropped by the local mute list or the capability filter. */
    public long suppressedCount() {
        return suppressedCount;
    }
}
