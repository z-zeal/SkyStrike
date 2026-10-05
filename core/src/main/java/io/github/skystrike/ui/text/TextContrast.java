package io.github.skystrike.ui.text;

import com.badlogic.gdx.graphics.Color;
import java.util.List;
import java.util.Objects;

/**
 * Contrast calculations and the four mandatory dialog-preview backgrounds.
 *
 * <p>The future console debug mode renders these backgrounds; keeping the colours here makes the
 * non-visual invariant testable by tools and prevents a theme from silently dropping a required
 * case.
 */
public final class TextContrast {

    public enum PreviewBackground {
        BLACK(new Color(0f, 0f, 0f, 1f)),
        WHITE(new Color(1f, 1f, 1f, 1f)),
        GREY(new Color(0.5f, 0.5f, 0.5f, 1f)),
        NOISE(new Color(0.5f, 0.5f, 0.5f, 1f));

        private final Color representativeColor;

        PreviewBackground(Color representativeColor) {
            this.representativeColor = representativeColor;
        }

        public Color representativeColor() {
            return new Color(representativeColor);
        }
    }

    private TextContrast() {
    }

    /** The preview set required by the Phase 7 text legibility gate. */
    public static List<PreviewBackground> requiredPreviewBackgrounds() {
        return List.of(PreviewBackground.values());
    }

    /** WCAG-style contrast ratio for opaque foreground/background colours. */
    public static float contrastRatio(Color foreground, Color background) {
        Objects.requireNonNull(foreground, "foreground");
        Objects.requireNonNull(background, "background");
        float first = linearLuminance(foreground);
        float second = linearLuminance(background);
        float light = Math.max(first, second);
        float dark = Math.min(first, second);
        return (light + 0.05f) / (dark + 0.05f);
    }

    private static float linearLuminance(Color color) {
        return 0.2126f * linear(color.r) + 0.7152f * linear(color.g) + 0.0722f * linear(color.b);
    }

    private static float linear(float channel) {
        return channel <= 0.04045f
            ? channel / 12.92f
            : (float) Math.pow((channel + 0.055f) / 1.055f, 2.4d);
    }
}
