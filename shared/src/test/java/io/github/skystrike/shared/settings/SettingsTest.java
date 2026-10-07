package io.github.skystrike.shared.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SettingsTest {

    private static final float EPSILON = 1e-4f;

    @Test
    @DisplayName("invalid stored values are clamped before a video or audio backend sees them")
    void validationClampsEveryBoundedSetting() {
        Settings settings = new Settings();
        settings.width = -10;
        settings.height = Integer.MAX_VALUE;
        settings.qualityTier = 99;
        settings.masterVolume = -1f;
        settings.musicVolume = 2f;
        settings.effectsVolume = Float.NaN;
        settings.chatTarget = "console";

        settings.validate();

        assertEquals(Settings.MIN_WIDTH, settings.width);
        assertEquals(Settings.MAX_HEIGHT, settings.height);
        assertEquals(Settings.MAX_QUALITY_TIER, settings.qualityTier);
        assertEquals(0f, settings.masterVolume, EPSILON);
        assertEquals(1f, settings.musicVolume, EPSILON);
        assertEquals(0f, settings.effectsVolume, EPSILON);
        assertEquals("team", settings.chatTarget);
    }

    @Test
    @DisplayName("settings serialise to stable strings and load back without platform APIs")
    void serialisationRoundTrip() {
        Settings original = new Settings();
        original.width = 2560;
        original.height = 1440;
        original.vsync = false;
        original.fullscreen = true;
        original.qualityTier = 3;
        original.masterVolume = .75f;
        original.musicVolume = .5f;
        original.effectsVolume = .25f;
        original.chatTarget = "all";

        Map<String, String> values = original.toValues();
        Settings restored = new Settings();
        restored.loadFrom(values);

        assertEquals(2560, restored.width);
        assertEquals(1440, restored.height);
        assertFalse(restored.vsync);
        assertTrue(restored.fullscreen);
        assertEquals(3, restored.qualityTier);
        assertEquals(.75f, restored.masterVolume, EPSILON);
        assertEquals(.5f, restored.musicVolume, EPSILON);
        assertEquals(.25f, restored.effectsVolume, EPSILON);
        assertEquals("all", restored.chatTarget);
    }

    @Test
    @DisplayName("malformed persisted strings leave safe defaults in place")
    void malformedPersistenceIsIgnored() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(Settings.KEY_WIDTH, "wide");
        values.put(Settings.KEY_HEIGHT, "");
        values.put(Settings.KEY_VSYNC, "sometimes");
        values.put(Settings.KEY_FULLSCREEN, "nope");
        values.put(Settings.KEY_QUALITY_TIER, "not-a-tier");
        values.put(Settings.KEY_MASTER_VOLUME, "Infinity");
        values.put(Settings.KEY_CHAT_TARGET, "other");

        Settings settings = new Settings();
        settings.loadFrom(values);

        assertEquals(1280, settings.width);
        assertEquals(720, settings.height);
        assertTrue(settings.vsync);
        assertFalse(settings.fullscreen);
        assertEquals(2, settings.qualityTier);
        assertEquals(1f, settings.masterVolume, EPSILON);
        assertEquals("team", settings.chatTarget);
    }

    @Test
    @DisplayName("resolution and quality selectors wrap instead of creating invalid options")
    void selectorsWrap() {
        Settings settings = new Settings();
        settings.width = 2560;
        settings.height = 1440;
        settings.cycleResolution(1);
        assertEquals(1280, settings.width);
        assertEquals(720, settings.height);

        settings.qualityTier = Settings.MAX_QUALITY_TIER;
        settings.cycleQualityTier(1);
        assertEquals(0, settings.qualityTier);
        settings.cycleQualityTier(-1);
        assertEquals(Settings.MAX_QUALITY_TIER, settings.qualityTier);
    }
}
