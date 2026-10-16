package io.github.skystrike.platform;

/** Insets in physical pixels, excluding the soft keyboard. */
public record SafeAreaInsets(int left, int right, int top, int bottom) {
    public static final SafeAreaInsets ZERO = new SafeAreaInsets(0, 0, 0, 0);

    public SafeAreaInsets {
        if (left < 0 || right < 0 || top < 0 || bottom < 0) {
            throw new IllegalArgumentException("Insets must be non-negative");
        }
    }
}
