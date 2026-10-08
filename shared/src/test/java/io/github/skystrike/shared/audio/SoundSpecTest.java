package io.github.skystrike.shared.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The playback contract itself: clamps, the silence sentinel, and the deterministic pitch that
 * makes two clients hear the same server-seeded event the same way.
 */
class SoundSpecTest {

    private static final float EPSILON = 1e-4f;

    private static SoundSpec impact() {
        return SoundSpec.oneShot(
            "sfx/world/impact-concrete.wav", AudioBus.EFFECTS, 0.4f, 0.15f, 450f, 1100f, 0.25f, 6,
            SoundPriority.NORMAL);
    }

    @Test
    @DisplayName("the sentinel is silent and asks for no playback")
    void silenceSentinel() {
        assertTrue(SoundSpec.SILENT.isSilent());
        assertEquals(null, SoundSpec.SILENT.path());
        assertEquals(0, SoundSpec.SILENT.maxVoices());
        // A blank path is silence too: a catalogue row must not be able to ask for nothing loudly.
        assertTrue(SoundSpec.oneShot(
            "   ", AudioBus.EFFECTS, 1f, 0f, 900f, 2400f, 1f, 4, SoundPriority.NORMAL).isSilent());
    }

    @Test
    @DisplayName("hostile numbers are clamped, never carried into the backend")
    void sanitation() {
        SoundSpec spec = SoundSpec.oneShot(
            "sfx/world/fire-ignite.wav", null, 9f, 9f, -100f, Float.NaN, -3f, -1, null);

        assertEquals(1f, spec.gain(), EPSILON);
        assertTrue(spec.pitchJitter() <= 1f);
        assertEquals(0f, spec.referenceDistance(), EPSILON);
        assertEquals(0f, spec.audibleRadius(), EPSILON);
        assertEquals(0f, spec.durationSeconds(), EPSILON);
        assertEquals(0, spec.maxVoices());
        assertFalse(spec.priority() == null, "a null priority must fall back, not propagate");
        assertFalse(spec.bus() == null, "a null bus must fall back, not propagate");
        assertTrue(spec.isSilent(), "a source with no reach is silent");
    }

    @Test
    @DisplayName("a loop is never silent, and silence from a loop means a zero gain")
    void loops() {
        SoundSpec loop = SoundSpec.looping("sfx/status/tinnitus-ring.wav", AudioBus.EFFECTS, 1f, 2f);
        assertTrue(loop.looping());
        assertFalse(loop.isSilent());
        assertEquals(1, loop.maxVoices());

        SoundSpec muted = SoundSpec.looping("sfx/status/tinnitus-ring.wav", AudioBus.EFFECTS, 0f, 2f);
        assertTrue(muted.isSilent());
        // A loop has no radius to hide behind; the envelope drives it, not distance.
        assertEquals(0f, loop.audibleRadius(), EPSILON);
    }

    @Test
    @DisplayName("pitch jitter is deterministic per seed, so every client hears the same event")
    void jitterIsDeterministic() {
        SoundSpec spec = impact();

        assertEquals(spec.pitchForSeed(7), spec.pitchForSeed(7), EPSILON);
        assertEquals(spec.pitchForSeed(7), impact().pitchForSeed(7), EPSILON);
        assertNotEquals(spec.pitchForSeed(3), spec.pitchForSeed(4));
        for (int seed = -50; seed < 50; seed++) {
            float pitch = spec.pitchForSeed(seed);
            assertTrue(pitch >= 1f - spec.pitchJitter() && pitch <= 1f + spec.pitchJitter(),
                "pitch " + pitch + " escaped the jitter band for seed " + seed);
            assertTrue(pitch >= SoundSpec.MIN_PITCH && pitch <= SoundSpec.MAX_PITCH);
        }
    }

    @Test
    @DisplayName("a spec with no jitter plays its recording untouched")
    void noJitterMeansExactPitch() {
        SoundSpec exact = SoundSpec.oneShot(
            "sfx/world/flash-detonation.wav", AudioBus.EFFECTS, 0.95f, 0f, 1300f, 3600f, 0.6f, 2,
            SoundPriority.CRITICAL);
        for (int seed = -100; seed < 100; seed++) {
            assertEquals(1f, exact.pitchForSeed(seed), EPSILON);
        }
    }

    @Test
    @DisplayName("the seed hash stays in [0, 1] for every 32-bit seed, extremes included")
    void unitFromSeedRange() {
        int[] extremes = {
            Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -1, 0, 1, Integer.MAX_VALUE - 1, Integer.MAX_VALUE
        };
        for (int seed : extremes) {
            float unit = SoundSpec.unitFromSeed(seed);
            assertTrue(unit >= 0f && unit <= 1f, "seed " + seed + " produced " + unit);
        }

        Set<Integer> buckets = new HashSet<>();
        for (int seed = 0; seed < 256; seed++) {
            buckets.add(Math.round(SoundSpec.unitFromSeed(seed) * 64f));
        }
        assertTrue(buckets.size() >= 32, "the seed hash is too coarse: " + buckets.size() + " buckets");
    }
}
