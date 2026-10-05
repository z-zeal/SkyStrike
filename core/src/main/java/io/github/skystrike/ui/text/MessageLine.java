package io.github.skystrike.ui.text;

import io.github.skystrike.shared.model.Team;
import io.github.skystrike.shared.text.ChatChannel;
import java.util.Objects;

/**
 * One structured scrollback record.
 *
 * <p>The buffer stores this data rather than a pre-coloured string so a resize can wrap it again,
 * a theme can recolour it, and console-only channels can disappear completely when the server
 * revokes console access.
 */
public record MessageLine(
    long timestampMillis,
    ChatChannel channel,
    int authorId,
    String authorName,
    Team authorTeam,
    String body,
    MessageSeverity severity
) {

    /** No player authored this system, server, console, or debug line. */
    public static final int NO_AUTHOR = -1;

    public MessageLine {
        channel = Objects.requireNonNull(channel, "channel");
        authorName = authorName == null ? "" : authorName;
        authorTeam = authorTeam == null ? Team.NEUTRAL : authorTeam;
        body = body == null ? "" : body;
        severity = severity == null ? MessageSeverity.INFO : severity;
    }

    /** A server/system line following the same display path as a player line. */
    public static MessageLine system(long timestampMillis, ChatChannel channel, String body, MessageSeverity severity) {
        if (channel == null || channel.isChatTarget()) {
            throw new IllegalArgumentException("a system line needs a non-player channel");
        }
        return new MessageLine(timestampMillis, channel, NO_AUTHOR, "", Team.NEUTRAL, body, severity);
    }

    /** Whether this message has an author name to display. */
    public boolean hasAuthor() {
        return authorId != NO_AUTHOR && !authorName.isBlank();
    }

    /** Delegates permission filtering to the canonical shared channel definition. */
    public boolean isVisibleTo(boolean consoleAccess) {
        return channel.isVisibleTo(consoleAccess);
    }
}
