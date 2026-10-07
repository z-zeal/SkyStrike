package io.github.skystrike.shared.settings;

/** Validated, platform-neutral client preferences. Rendering owns applying video fields. */
public final class Settings {
    public static final int MIN_WIDTH = 640;
    public static final int MIN_HEIGHT = 360;
    public static final int MAX_QUALITY_TIER = 3;

    public int width = 1280;
    public int height = 720;
    public boolean vsync = true;
    public boolean fullscreen;
    public int qualityTier = 2;
    public String chatTarget = "team";

    /** Clamps values loaded from Preferences so bad data cannot reach the graphics backend. */
    public void validate() {
        width = clamp(width, MIN_WIDTH, 7680);
        height = clamp(height, MIN_HEIGHT, 4320);
        qualityTier = clamp(qualityTier, 0, MAX_QUALITY_TIER);
        if (!"team".equals(chatTarget) && !"all".equals(chatTarget)) chatTarget = "team";
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
