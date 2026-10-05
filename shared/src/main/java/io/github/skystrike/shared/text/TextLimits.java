package io.github.skystrike.shared.text;

/**
 * Bounded text-system values shared by the client and authoritative chat service.
 *
 * <p>Keeping the externally visible limits in {@code shared} prevents a client from promising
 * room for text that the server will later truncate differently.
 */
public final class TextLimits {

    /** Long enough for a useful callout, short enough that one line cannot flood the panel. */
    public static final int MAX_CHAT_BODY_CODE_POINTS = 120;

    /** Structured scrollback records retained by the client, oldest-first eviction. */
    public static final int MESSAGE_BUFFER_CAPACITY = 512;

    /** Maximum persisted history entries once command/chat history is introduced. */
    public static final int INPUT_HISTORY_CAPACITY = 128;

    /** Chat burst: three messages within this interval are allowed before refilling begins. */
    public static final int CHAT_RATE_CAPACITY = 3;
    public static final long CHAT_RATE_WINDOW_MILLIS = 5_000L;

    /** Repeating an identical message inside this window is dropped by the server. */
    public static final long DUPLICATE_MESSAGE_WINDOW_MILLIS = 3_000L;

    private TextLimits() {
    }
}
