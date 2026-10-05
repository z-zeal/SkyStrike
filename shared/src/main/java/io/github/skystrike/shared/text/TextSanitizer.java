package io.github.skystrike.shared.text;

/**
 * Server-safe normalisation for player names, chat bodies and future string command arguments.
 *
 * <p>The sanitizer works in Unicode code points, not UTF-16 chars, so truncation cannot split a
 * supplementary character. It removes control characters, zero-width/format characters,
 * bidirectional controls and square-bracket markup delimiters before collapsing whitespace and
 * applying the protocol-visible body limit.
 */
public final class TextSanitizer {

    private TextSanitizer() {
    }

    /** Sanitises and truncates a player-authored chat body to the shared wire limit. */
    public static String sanitizeChatBody(String raw) {
        return sanitize(raw, TextLimits.MAX_CHAT_BODY_CODE_POINTS);
    }

    /** Sanitises {@code raw} and retains at most {@code maxCodePoints} visible code points. */
    public static String sanitize(String raw, int maxCodePoints) {
        if (raw == null || raw.isEmpty() || maxCodePoints <= 0) {
            return "";
        }

        StringBuilder cleaned = new StringBuilder(Math.min(raw.length(), maxCodePoints));
        boolean pendingSpace = false;
        int retained = 0;

        for (int offset = 0; offset < raw.length();) {
            int codePoint = raw.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (isDiscarded(codePoint)) {
                continue;
            }
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                if (cleaned.length() > 0) {
                    pendingSpace = true;
                }
                continue;
            }
            int requiredCodePoints = pendingSpace ? 2 : 1;
            if (retained + requiredCodePoints > maxCodePoints) {
                break;
            }
            if (pendingSpace) {
                cleaned.append(' ');
                retained++;
                pendingSpace = false;
            }
            cleaned.appendCodePoint(codePoint);
            retained++;
        }
        return cleaned.toString();
    }

    /** True when the line has no displayable content after sanitisation. */
    public static boolean isBlank(String raw) {
        return sanitizeChatBody(raw).isEmpty();
    }

    private static boolean isDiscarded(int codePoint) {
        if (Character.isISOControl(codePoint) || Character.getType(codePoint) == Character.FORMAT) {
            return true;
        }
        // Square brackets are libGDX markup delimiters. Removing the delimiters prevents an
        // untrusted line from changing glyph colours, sizes, or visibility if markup is enabled.
        if (codePoint == '[' || codePoint == ']') {
            return true;
        }
        return codePoint == 0x200E // left-to-right mark
            || codePoint == 0x200F // right-to-left mark
            || (codePoint >= 0x202A && codePoint <= 0x202E) // embedding/override controls
            || (codePoint >= 0x2066 && codePoint <= 0x2069); // isolate controls
    }
}
