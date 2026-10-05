package io.github.skystrike.ui.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Width-aware word wrapping with no renderer dependency.
 *
 * <p>Callers provide their active font's measurement function, so the same wrapping rules work
 * before and after a DPI-triggered font regeneration. Oversized words are split at code-point
 * boundaries rather than silently overflowing the dialog panel.
 */
public final class TextWrapper {

    private TextWrapper() {
    }

    @FunctionalInterface
    public interface WidthMeasurer {
        float width(String text);
    }

    /** Wraps {@code text} into visual lines no wider than {@code maxWidth} where possible. */
    public static List<String> wrap(String text, float maxWidth, WidthMeasurer measurer) {
        if (maxWidth <= 0f || Float.isNaN(maxWidth)) {
            throw new IllegalArgumentException("maxWidth must be positive");
        }
        Objects.requireNonNull(measurer, "measurer");

        String safeText = text == null ? "" : text;
        String[] paragraphs = safeText.split("\\r?\\n", -1);
        List<String> wrapped = new ArrayList<>();
        for (String paragraph : paragraphs) {
            wrapParagraph(paragraph, maxWidth, measurer, wrapped);
        }
        return List.copyOf(wrapped);
    }

    private static void wrapParagraph(
        String paragraph,
        float maxWidth,
        WidthMeasurer measurer,
        List<String> output
    ) {
        String trimmed = paragraph.trim();
        if (trimmed.isEmpty()) {
            output.add("");
            return;
        }

        StringBuilder current = new StringBuilder();
        for (String word : trimmed.split("\\s+")) {
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (fits(candidate, maxWidth, measurer)) {
                current.setLength(0);
                current.append(candidate);
                continue;
            }

            if (current.length() > 0) {
                output.add(current.toString());
                current.setLength(0);
            }
            appendLongWord(word, maxWidth, measurer, output, current);
        }
        if (current.length() > 0) {
            output.add(current.toString());
        }
    }

    private static void appendLongWord(
        String word,
        float maxWidth,
        WidthMeasurer measurer,
        List<String> output,
        StringBuilder current
    ) {
        if (fits(word, maxWidth, measurer)) {
            current.append(word);
            return;
        }

        StringBuilder segment = new StringBuilder();
        for (int offset = 0; offset < word.length();) {
            int codePoint = word.codePointAt(offset);
            String glyph = new String(Character.toChars(codePoint));
            String candidate = segment + glyph;
            if (segment.length() > 0 && !fits(candidate, maxWidth, measurer)) {
                output.add(segment.toString());
                segment.setLength(0);
            }
            segment.append(glyph);
            offset += Character.charCount(codePoint);
        }
        current.append(segment);
    }

    private static boolean fits(String value, float maxWidth, WidthMeasurer measurer) {
        float measured = measurer.width(value);
        return !Float.isNaN(measured) && measured <= maxWidth;
    }
}
