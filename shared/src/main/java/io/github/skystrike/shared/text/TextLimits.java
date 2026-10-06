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

    /**
     * A raw command line may be longer than a chat body (quoted names, usage-dumping greps) but
     * not unboundedly so. Enforced by the server before parsing, same as the chat cap.
     */
    public static final int MAX_COMMAND_LINE_CODE_POINTS = 256;

    /** Command budget: six executions inside this interval before the bucket needs a refill. */
    public static final int COMMAND_RATE_CAPACITY = 6;
    public static final long COMMAND_RATE_WINDOW_MILLIS = 3_000L;

    /** Server responses never ship more than this many lines, and none longer than this. */
    public static final int MAX_COMMAND_RESPONSE_LINES = 50;
    public static final int MAX_COMMAND_RESPONSE_LINE_CODE_POINTS = 240;

    private TextLimits() {
    }
}
