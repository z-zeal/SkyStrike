package io.github.skystrike.shared.settings;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Validated, platform-neutral client preferences.
 *
 * <p>This is deliberately free of libGDX APIs. Platform code loads and saves its string map,
 * while this class owns the safe defaults, clamping and representation shared by every frontend.
 * Rendering owns applying video fields after they have been validated.
 */
public final class Settings {
    public static final int MIN_WIDTH = 640;
    public static final int MIN_HEIGHT = 360;
    public static final int MAX_WIDTH = 7680;
    public static final int MAX_HEIGHT = 4320;
    public static final int MAX_QUALITY_TIER = 3;

    public static final String KEY_WIDTH = "video.width";
    public static final String KEY_HEIGHT = "video.height";
    public static final String KEY_VSYNC = "video.vsync";
    public static final String KEY_FULLSCREEN = "video.fullscreen";
    public static final String KEY_QUALITY_TIER = "video.qualityTier";
    public static final String KEY_MASTER_VOLUME = "audio.masterVolume";
    public static final String KEY_MUSIC_VOLUME = "audio.musicVolume";
    public static final String KEY_EFFECTS_VOLUME = "audio.effectsVolume";
    public static final String KEY_CHAT_TARGET = "chatTarget";

    /** Windowed resolutions offered by the settings screen. Custom valid values still round-trip. */
    public static final List<Resolution> RESOLUTIONS = List.of(
        new Resolution(1280, 720),
        new Resolution(1600, 900),
        new Resolution(1920, 1080),
        new Resolution(2560, 1440));

    public int width = 1280;
    public int height = 720;
    public boolean vsync = true;
    public boolean fullscreen;
    public int qualityTier = 2;
    /** Audio wiring arrives later; these values are retained now so the UI never lies. */
    public float masterVolume = 1f;
    public float musicVolume = 1f;
    public float effectsVolume = 1f;
    public String chatTarget = "team";

    /** A display size offered by the video menu. */
    public record Resolution(int width, int height) {
        public Resolution {
            if (width < MIN_WIDTH || height < MIN_HEIGHT) {
                throw new IllegalArgumentException("resolution is below the supported minimum");
            }
        }

        public String label() {
            return width + "x" + height;
        }
    }

    /** Clamps values loaded from storage so bad data cannot reach the graphics backend. */
    public void validate() {
        width = clamp(width, MIN_WIDTH, MAX_WIDTH);
        height = clamp(height, MIN_HEIGHT, MAX_HEIGHT);
        qualityTier = clamp(qualityTier, 0, MAX_QUALITY_TIER);
        masterVolume = clamp(masterVolume, 0f, 1f);
        musicVolume = clamp(musicVolume, 0f, 1f);
        effectsVolume = clamp(effectsVolume, 0f, 1f);
        chatTarget = normaliseChatTarget(chatTarget);
    }

    /** Chooses the next or previous standard windowed resolution, wrapping at either end. */
    public void cycleResolution(int direction) {
        if (direction == 0) {
            return;
        }
        int current = 0;
        for (int i = 0; i < RESOLUTIONS.size(); i++) {
            Resolution candidate = RESOLUTIONS.get(i);
            if (candidate.width == width && candidate.height == height) {
                current = i;
                break;
            }
        }
        int step = direction > 0 ? 1 : -1;
        int next = Math.floorMod(current + step, RESOLUTIONS.size());
        Resolution resolution = RESOLUTIONS.get(next);
        width = resolution.width;
        height = resolution.height;
    }

    /** Cycles the bounded quality tier rather than allowing a UI control to leave its range. */
    public void cycleQualityTier(int direction) {
        if (direction == 0) {
            return;
        }
        qualityTier = Math.floorMod(qualityTier + (direction > 0 ? 1 : -1), MAX_QUALITY_TIER + 1);
    }

    /** Adjusts an audio stub without leaking an invalid gain into a future audio backend. */
    public float adjustVolume(float current, float amount) {
        return clamp(current + amount, 0f, 1f);
    }

    /** Stable string representation suitable for a platform Preferences implementation. */
    public Map<String, String> toValues() {
        validate();
        Map<String, String> values = new LinkedHashMap<>();
        values.put(KEY_WIDTH, Integer.toString(width));
        values.put(KEY_HEIGHT, Integer.toString(height));
        values.put(KEY_VSYNC, Boolean.toString(vsync));
        values.put(KEY_FULLSCREEN, Boolean.toString(fullscreen));
        values.put(KEY_QUALITY_TIER, Integer.toString(qualityTier));
        values.put(KEY_MASTER_VOLUME, Float.toString(masterVolume));
        values.put(KEY_MUSIC_VOLUME, Float.toString(musicVolume));
        values.put(KEY_EFFECTS_VOLUME, Float.toString(effectsVolume));
        values.put(KEY_CHAT_TARGET, chatTarget);
        return values;
    }

    /**
     * Merges values from storage. Missing or malformed values retain the current safe value;
     * validation is always performed before returning.
     */
    public void loadFrom(Map<String, String> values) {
        if (values == null) {
            validate();
            return;
        }
        width = parseInt(values.get(KEY_WIDTH), width);
        height = parseInt(values.get(KEY_HEIGHT), height);
        vsync = parseBoolean(values.get(KEY_VSYNC), vsync);
        fullscreen = parseBoolean(values.get(KEY_FULLSCREEN), fullscreen);
        qualityTier = parseInt(values.get(KEY_QUALITY_TIER), qualityTier);
        masterVolume = parseFloat(values.get(KEY_MASTER_VOLUME), masterVolume);
        musicVolume = parseFloat(values.get(KEY_MUSIC_VOLUME), musicVolume);
        effectsVolume = parseFloat(values.get(KEY_EFFECTS_VOLUME), effectsVolume);
        String storedChatTarget = values.get(KEY_CHAT_TARGET);
        if (storedChatTarget != null) {
            chatTarget = storedChatTarget;
        }
        validate();
    }

    public static String qualityTierLabel(int tier) {
        return switch (clamp(tier, 0, MAX_QUALITY_TIER)) {
            case 0 -> "Low";
            case 1 -> "Medium";
            case 2 -> "High";
            default -> "Ultra";
        };
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static float parseFloat(String value, float fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            float parsed = Float.parseFloat(value.trim());
            return Float.isFinite(parsed) ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        String normalised = value.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalised)) {
            return true;
        }
        if ("false".equals(normalised)) {
            return false;
        }
        return fallback;
    }

    private static String normaliseChatTarget(String target) {
        if (target == null) {
            return "team";
        }
        String normalised = target.trim().toLowerCase(Locale.ROOT);
        return "all".equals(normalised) || "team".equals(normalised) ? normalised : "team";
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        if (!Float.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
