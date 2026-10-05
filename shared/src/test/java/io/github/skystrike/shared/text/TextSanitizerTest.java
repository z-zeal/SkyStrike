package io.github.skystrike.shared.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TextSanitizerTest {

    @Test
    @DisplayName("chat sanitisation strips controls, zero-width characters and markup delimiters")
    void removesInvisibleControlsAndMarkup() {
        String raw = "  [RED]Push\tmid\nnow[]\u200B\u202E  ";

        assertEquals("REDPush mid now", TextSanitizer.sanitizeChatBody(raw));
    }

    @Test
    @DisplayName("sanitisation collapses whitespace and drops a body that becomes empty")
    void collapsesWhitespaceAndDetectsBlank() {
        assertEquals("hold this lane", TextSanitizer.sanitizeChatBody(" hold\u00A0\u00A0this   lane "));
        assertEquals("", TextSanitizer.sanitizeChatBody("\t\n\u200B\u202E"));
        assertTrue(TextSanitizer.isBlank("\t\n\u200B"));
        assertFalse(TextSanitizer.isBlank("ready"));
    }

    @Test
    @DisplayName("the visible code-point limit never splits a supplementary character")
    void truncatesByCodePoint() {
        String raw = "a".repeat(119) + "\uD83D\uDE80z";

        String sanitized = TextSanitizer.sanitizeChatBody(raw);

        assertEquals(120, sanitized.codePointCount(0, sanitized.length()));
        assertTrue(sanitized.endsWith("\uD83D\uDE80"));
    }

    @Test
    @DisplayName("a caller can use the shared normaliser with a tighter bounded field")
    void supportsCustomBounds() {
        assertEquals("alpha", TextSanitizer.sanitize(" alpha beta", 5));
        assertEquals("", TextSanitizer.sanitize("visible", 0));
    }
}
