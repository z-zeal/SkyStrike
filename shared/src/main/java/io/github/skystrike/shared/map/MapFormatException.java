package io.github.skystrike.shared.map;

/**
 * A strict text-map load failure, with the source line retained in its message when available.
 *
 * <p>A caller must reject the whole map on this exception; the parser never returns a partial
 * layout after encountering malformed source.
 */
public final class MapFormatException extends IllegalArgumentException {

    public MapFormatException(String message) {
        super(message);
    }

    public MapFormatException(int lineNumber, String message) {
        super("line " + lineNumber + ": " + message);
    }

    public MapFormatException(int lineNumber, String message, Throwable cause) {
        super("line " + lineNumber + ": " + message, cause);
    }
}
