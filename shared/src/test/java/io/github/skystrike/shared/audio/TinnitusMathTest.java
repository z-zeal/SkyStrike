package io.github.skystrike.shared.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The stun ring's envelope. The whole point of the effect is that the ring outlives the flash, so
 * the tail is what is pinned here: an envelope that snapped to zero with the whiteout would pass a
 * listening test in a quiet room and still be the wrong effect.
 */
class TinnitusMathTest {

    private static final float EPSILON = 1e-4f;
    private static final float FRAME = 1f / 60f;

    @Test
    @DisplayName("a stronger blind earns a louder ring, capped at the ceiling")
    void ringGainFollowsBlindIntensity() {
        assertEquals(0f, TinnitusMath.ringGain(0f), EPSILON);
        assertEquals(TinnitusMath.MAX_RING_GAIN, TinnitusMath.ringGain(1f), EPSILON);
        assertEquals(TinnitusMath.MAX_RING_GAIN, TinnitusMath.ringGain(4f), EPSILON);
        assertEquals(TinnitusMath.MAX_RING_GAIN * 0.5f, TinnitusMath.ringGain(0.25f), EPSILON);
        assertEquals(0f, TinnitusMath.ringGain(-1f), EPSILON);
        assertEquals(0f, TinnitusMath.ringGain(Float.NaN), EPSILON);
    }

    @Test
    @DisplayName("the curve is steep at the bottom: a fringe stun still rings")
    void ringGainIsSteepAtTheBottom() {
        // A tenth of the whiteout rings at nearly a third of the maximum where a linear map would
        // give a tenth, which is what keeps the outer stun band from being rounded away.
        assertTrue(TinnitusMath.ringGain(0.1f) > 0.3f * TinnitusMath.MAX_RING_GAIN,
            "a tenth of the whiteout rings too softly: " + TinnitusMath.ringGain(0.1f));
        assertTrue(TinnitusMath.ringGain(0.05f) > 0.2f * TinnitusMath.MAX_RING_GAIN,
            "a twentieth of the whiteout rings too softly: " + TinnitusMath.ringGain(0.05f));
        // The square root lands exactly on half at a quarter, so the boundary is an equality.
        assertEquals(0.5f * TinnitusMath.MAX_RING_GAIN, TinnitusMath.ringGain(0.25f), EPSILON);
    }

    @Test
    @DisplayName("the attack is fast and never overshoots")
    void attack() {
        // A twentieth of a second takes the ring most of the way up.
        float level = TinnitusMath.step(0f, TinnitusMath.MAX_RING_GAIN, 0.05f);
        assertTrue(level > 0.5f * TinnitusMath.MAX_RING_GAIN, "attack too slow: " + level);
        assertTrue(level <= TinnitusMath.MAX_RING_GAIN);

        // Held at the target it converges exactly, with no ringing around it.
        for (int frame = 0; frame < 120; frame++) {
            level = TinnitusMath.step(level, TinnitusMath.MAX_RING_GAIN, FRAME);
        }
        assertEquals(TinnitusMath.MAX_RING_GAIN, level, EPSILON);
    }

    @Test
    @DisplayName("the ring survives the flash: still loud after a second, gone after the tail")
    void releaseOutlivesTheBlindness() {
        float level = TinnitusMath.MAX_RING_GAIN;

        for (int frame = 0; frame < 60; frame++) {
            level = TinnitusMath.step(level, 0f, FRAME);
        }
        assertTrue(level > 0.2f, "the ring died with the whiteout: " + level);

        for (int frame = 0; frame < 120; frame++) {
            level = TinnitusMath.step(level, 0f, FRAME);
        }
        assertTrue(level > 0f);
        assertTrue(level < 0.05f, "the ring is still loud after three seconds: " + level);

        for (int frame = 0; frame < 60 * (int) TinnitusMath.TAIL_SECONDS; frame++) {
            level = TinnitusMath.step(level, 0f, FRAME);
        }
        assertTrue(level < TinnitusMath.MAX_RING_GAIN * 0.02f,
            "the tail should be under 2% of the peak after TAIL_SECONDS: " + level);
    }

    @Test
    @DisplayName("the release decays toward the target, never through it")
    void releaseNeverUndershoots() {
        float level = TinnitusMath.step(0.8f, 0.4f, FRAME);
        assertTrue(level < 0.8f);
        assertTrue(level > 0.4f, "the release overshot the target: " + level);

        for (int frame = 0; frame < 600; frame++) {
            level = TinnitusMath.step(level, 0.4f, FRAME);
        }
        assertEquals(0.4f, level, EPSILON);
    }

    @Test
    @DisplayName("a frozen or broken frame time leaves the level alone")
    void frozenFrames() {
        assertEquals(0.5f, TinnitusMath.step(0.5f, 0f, 0f), EPSILON);
        assertEquals(0.5f, TinnitusMath.step(0.5f, 0f, -1f), EPSILON);
        assertEquals(0.5f, TinnitusMath.step(0.5f, 0f, Float.NaN), EPSILON);
        assertEquals(0f, TinnitusMath.step(Float.NaN, 0f, FRAME), EPSILON);
    }

    @Test
    @DisplayName("the ring sharpens as it gets louder, inside the range libGDX supports")
    void pitch() {
        assertEquals(TinnitusMath.BASE_RING_PITCH, TinnitusMath.ringPitch(0f), EPSILON);
        assertEquals(
            TinnitusMath.BASE_RING_PITCH + TinnitusMath.RING_PITCH_SPREAD,
            TinnitusMath.ringPitch(1f),
            EPSILON);
        assertTrue(TinnitusMath.ringPitch(0.8f) > TinnitusMath.ringPitch(0.2f));
        assertTrue(TinnitusMath.ringPitch(5f) <= SoundSpec.MAX_PITCH);
        assertEquals(TinnitusMath.BASE_RING_PITCH, TinnitusMath.ringPitch(Float.NaN), EPSILON);
    }

    @Test
    @DisplayName("the loop is worth holding a voice exactly when the level is audible")
    void audibilityFloor() {
        assertFalse(TinnitusMath.audible(0f));
        assertFalse(TinnitusMath.audible(TinnitusMath.MIN_RING_GAIN * 0.5f));
        assertFalse(TinnitusMath.audible(Float.NaN));
        assertTrue(TinnitusMath.audible(TinnitusMath.MIN_RING_GAIN));
        assertTrue(TinnitusMath.audible(TinnitusMath.MAX_RING_GAIN));
    }
}
