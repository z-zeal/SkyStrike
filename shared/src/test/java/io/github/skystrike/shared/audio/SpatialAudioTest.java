package io.github.skystrike.shared.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The two mixing curves and the placement that combines them. These are the numbers that decide
 * whether a shot across the arena is a whisper or a wall of sound, so they are pinned here rather
 * than trusted to a desktop listening pass.
 */
class SpatialAudioTest {

    private static final float EPSILON = 1e-4f;

    @Test
    @DisplayName("inverse-distance gain: full up close, half at the reference, zero at the radius")
    void attenuation() {
        assertEquals(1f, SpatialAudio.attenuate(0f, 900f, 2400f), EPSILON);
        assertEquals(0.5f, SpatialAudio.attenuate(900f, 900f, 2400f), EPSILON);
        assertEquals(1f / 3f, SpatialAudio.attenuate(1800f, 900f, 2400f), EPSILON);
        assertEquals(0f, SpatialAudio.attenuate(2400f, 900f, 2400f), EPSILON);
        assertEquals(0f, SpatialAudio.attenuate(2401f, 900f, 2400f), EPSILON);
    }

    @Test
    @DisplayName("gain never rises with distance, and the taper takes it to silence smoothly")
    void attenuationIsMonotoneAndTapered() {
        float previous = Float.MAX_VALUE;
        for (float distance = 0f; distance <= 2400f; distance += 25f) {
            float gain = SpatialAudio.attenuate(distance, 900f, 2400f);
            assertTrue(gain <= previous + EPSILON, "gain rose at " + distance);
            final float check = gain;
            assertTrue(check >= 0f && check <= 1f, "gain out of range at " + distance);
            previous = gain;
        }

        // The taper only touches the last fifth of the radius: inside it, gain is below the plain
        // inverse-distance value, and at 80% of the radius it has not started.
        float edgeStart = SpatialAudio.attenuate(2400f * 0.8f, 900f, 2400f);
        float inside = SpatialAudio.attenuate(2000f, 900f, 2400f);
        assertTrue(inside < edgeStart, "taper did not reduce the gain near the edge");
        assertEquals(900f / 2820f, edgeStart, EPSILON);
    }

    @Test
    @DisplayName("hostile distances and radii produce silence, never NaN")
    void attenuationRejectsNonsense() {
        assertEquals(0f, SpatialAudio.attenuate(Float.NaN, 900f, 2400f), EPSILON);
        assertEquals(0f, SpatialAudio.attenuate(-1f, 900f, 2400f), EPSILON);
        assertEquals(0f, SpatialAudio.attenuate(100f, 900f, 0f), EPSILON);
        assertEquals(0f, SpatialAudio.attenuate(100f, 900f, Float.NaN), EPSILON);
        assertEquals(0f, SpatialAudio.attenuate(100f, Float.NaN, 2400f), EPSILON);
        assertEquals(0f, SpatialAudio.attenuate(100f, 0f, 2400f), EPSILON);
        assertEquals(0f, SpatialAudio.attenuate(100f, -900f, 2400f), EPSILON);
    }

    @Test
    @DisplayName("panning is linear in horizontal offset and saturates at the full distance")
    void panning() {
        assertEquals(0f, SpatialAudio.pan(0f, 900f), EPSILON);
        assertEquals(0.5f, SpatialAudio.pan(450f, 900f), EPSILON);
        assertEquals(-0.5f, SpatialAudio.pan(-450f, 900f), EPSILON);
        assertEquals(1f, SpatialAudio.pan(900f, 900f), EPSILON);
        assertEquals(-1f, SpatialAudio.pan(-900f, 900f), EPSILON);
        assertEquals(1f, SpatialAudio.pan(5000f, 900f), EPSILON);
        assertEquals(0f, SpatialAudio.pan(Float.NaN, 900f), EPSILON);
    }

    @Test
    @DisplayName("a source beside the listener halves the gain and lands on one channel")
    void placement() {
        SpatialAudio.Placement placement =
            SpatialAudio.place(SpatialAudio.Listener.ORIGIN, 900f, 0f);

        assertTrue(placement.audible());
        assertEquals(0.5f, placement.gain(), EPSILON);
        assertEquals(1f, placement.pan(), EPSILON);
    }

    @Test
    @DisplayName("the mixer's float overload places the same source as the record overload")
    void placementOverloadsAgree() {
        SpatialAudio.Placement byFloats = SpatialAudio.place(100f, 200f, 1000f, 200f);
        SpatialAudio.Placement byListener =
            SpatialAudio.place(new SpatialAudio.Listener(100f, 200f), 1000f, 200f);

        assertEquals(byListener, byFloats);
        assertTrue(byFloats.audible());
        assertEquals(1f, byFloats.pan(), EPSILON);
        assertEquals(900f / 1800f, byFloats.gain(), EPSILON);
    }

    @Test
    @DisplayName("a null listener is the origin, not an exception")
    void nullListener() {
        assertEquals(
            SpatialAudio.place(SpatialAudio.Listener.ORIGIN, 300f, 400f),
            SpatialAudio.place(null, 300f, 400f));
    }

    @Test
    @DisplayName("occlusion muffles: the gain is scaled, the source is still audible")
    void occlusion() {
        SpatialAudio.Placement open = SpatialAudio.place(0f, 0f, 900f, 0f, 900f, 2400f, false);
        SpatialAudio.Placement behindWall =
            SpatialAudio.place(0f, 0f, 900f, 0f, 900f, 2400f, true);

        assertTrue(behindWall.audible(), "a blocked source must still be heard");
        assertEquals(open.gain() * SpatialAudio.OCCLUDED_GAIN, behindWall.gain(), EPSILON);
        assertEquals(open.pan(), behindWall.pan(), EPSILON);
    }

    @Test
    @DisplayName("sources past their reach, or below the floor, get no voice")
    void inaudibleSources() {
        assertFalse(SpatialAudio.place(0f, 0f, 5000f, 0f).audible());
        // Inside the radius but so far that the gain is below MIN_GAIN: not worth a voice.
        assertFalse(SpatialAudio.place(0f, 0f, 60000f, 0f, 900f, 100000f, false).audible());
        assertFalse(SpatialAudio.place(Float.NaN, 0f, 0f, 0f).audible());
        assertFalse(SpatialAudio.place(0f, 0f, Float.NaN, 0f).audible());
        assertFalse(SpatialAudio.place(Float.NaN, 0f, 0f, 0f, 900f, 2400f, false).audible());
    }
}
