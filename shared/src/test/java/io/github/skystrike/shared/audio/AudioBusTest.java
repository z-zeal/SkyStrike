package io.github.skystrike.shared.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The slider arithmetic. It is three multiplications, but they are the difference between "turn the
 * effects down" and "silence the game", so the routing is pinned rather than reviewed.
 */
class AudioBusTest {

    private static final float EPSILON = 1e-4f;

    @Test
    @DisplayName("each bus obeys its own slider, with the master applied once")
    void routing() {
        assertEquals(0.5f * 0.4f, AudioBus.EFFECTS.gain(0.5f, 1f, 0.4f), EPSILON);
        assertEquals(0.5f * 0.2f, AudioBus.MUSIC.gain(0.5f, 0.2f, 1f), EPSILON);
        assertEquals(0.5f, AudioBus.UI.gain(0.5f, 1f, 1f), EPSILON);
    }

    @Test
    @DisplayName("the effects slider never touches the interface: muting the world is not muting menus")
    void effectsSliderDoesNotSilenceUi() {
        assertEquals(AudioBus.UI.gain(1f, 1f, 0f), AudioBus.UI.gain(1f, 1f, 1f), EPSILON);
        assertEquals(0f, AudioBus.EFFECTS.gain(1f, 1f, 0f), EPSILON);
        assertEquals(0f, AudioBus.MUSIC.gain(1f, 0f, 1f), EPSILON);
    }

    @Test
    @DisplayName("sliders are clamped, and a broken value silences rather than poisoning the gain")
    void clamping() {
        assertEquals(1f, AudioBus.EFFECTS.gain(2f, 2f, 2f), EPSILON);
        assertEquals(0f, AudioBus.EFFECTS.gain(-1f, 1f, 1f), EPSILON);
        assertEquals(0f, AudioBus.EFFECTS.gain(1f, 1f, Float.NaN), EPSILON);
        assertEquals(0f, AudioBus.UI.gain(Float.NaN, 1f, 1f), EPSILON);
        assertEquals(1f, AudioBus.UI.gain(1f, Float.POSITIVE_INFINITY, Float.NaN), EPSILON);
    }
}
