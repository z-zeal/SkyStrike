package io.github.skystrike.shared.text;

/**
 * The two user-selectable destinations in the shared chat/console dialog.
 *
 * <p>This deliberately does not contain a console value. A submitted line is a command only
 * when the server-granted capability is present and the line begins with one slash; the target
 * remains only ALL or TEAM.
 */
public enum ChatTarget {
    ALL(ChatChannel.ALL),
    TEAM(ChatChannel.TEAM);

    private final ChatChannel channel;

    ChatTarget(ChatChannel channel) {
        this.channel = channel;
    }

    /** The corresponding player chat delivery channel. */
    public ChatChannel channel() {
        return channel;
    }

    /** The target selected by the dialog's ALL/TEAM toggle. */
    public ChatTarget toggle() {
        return this == ALL ? TEAM : ALL;
    }

    /** Defensive conversion for a wire value; only player chat targets are accepted. */
    public static ChatTarget fromChannel(ChatChannel channel) {
        return channel == ChatChannel.TEAM ? TEAM : ALL;
    }
}
