package io.github.skystrike.shared.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.effect.EffectType;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The world half of the catalogue. Every row here is a decision the client will act on the moment
 * a grenade goes off, and the exhaustive switch is the mechanism that stops a new
 * {@code EffectType} from shipping without one.
 */
class EffectSoundTableTest {

    @Test
    @DisplayName("every effect type has a decision; only the muzzle flash is deliberately silent")
    void exhaustiveAndHonestAboutSilence() {
        int silent = 0;
        for (EffectType type : EffectType.values()) {
            SoundSpec spec = EffectSoundTable.specFor(type);
            assertNotNull(spec, "no entry for " + type);
            if (spec.isSilent()) {
                silent++;
            }
        }

        assertEquals(1, silent, "exactly one effect type is silent by design");
        assertTrue(EffectSoundTable.specFor(EffectType.MUZZLE_FLASH).isSilent(),
            "the muzzle flash must stay silent: the snapshot-driven report already plays it");
    }

    @Test
    @DisplayName("a null type is silent rather than fatal")
    void nullType() {
        assertTrue(EffectSoundTable.specFor(null).isSilent());
    }

    @Test
    @DisplayName("every audible row names a WAV on the effects bus with sane playback numbers")
    void rowsAreWellFormed() {
        for (EffectType type : EffectType.values()) {
            SoundSpec spec = EffectSoundTable.specFor(type);
            if (spec.isSilent()) {
                continue;
            }
            assertNotNull(spec.path(), "no path for " + type);
            assertTrue(spec.path().startsWith("sfx/world/"), "path outside the world pack: " + type);
            assertTrue(spec.path().endsWith(".wav"), "not a WAV: " + type);
            assertEquals(AudioBus.EFFECTS, spec.bus(), "world sounds belong on the effects bus");
            assertFalse(spec.looping(), "world effects are one-shots: " + type);
            assertTrue(spec.gain() > 0f && spec.gain() <= 1f, "gain out of range: " + type);
            assertTrue(spec.pitchJitter() >= 0f && spec.pitchJitter() < 1f,
                "pitch jitter out of range: " + type);
            assertTrue(spec.referenceDistance() > 0f, "no reference distance: " + type);
            assertTrue(spec.audibleRadius() > spec.referenceDistance(),
                "an audible radius at or inside the half-gain distance: " + type);
            assertTrue(spec.durationSeconds() > 0f, "no duration for voice bookkeeping: " + type);
            assertTrue(spec.maxVoices() >= 1, "cannot ever hold a voice: " + type);
        }
    }

    @Test
    @DisplayName("no two effect types share a recording")
    void pathsAreDistinct() {
        Map<String, EffectType> seen = new HashMap<>();
        for (EffectType type : EffectType.values()) {
            SoundSpec spec = EffectSoundTable.specFor(type);
            if (spec.isSilent()) {
                continue;
            }
            EffectType previous = seen.put(spec.path(), type);
            assertNull(previous, spec.path() + " is shared by " + previous + " and " + type);
        }
    }

    @Test
    @DisplayName("the mix is ordered: detonations outrank texture, and a casing yields first")
    void prioritiesFollowTheDesign() {
        assertTrue(EffectSoundTable.specFor(EffectType.FRAG_EXPLOSION)
            .priority().outranks(EffectSoundTable.specFor(EffectType.SHELL_EJECT).priority()));
        assertTrue(EffectSoundTable.specFor(EffectType.FLASH_DETONATION)
            .priority().outranks(EffectSoundTable.specFor(EffectType.SHELL_EJECT).priority()));
        assertEquals(
            SoundPriority.LOW, EffectSoundTable.specFor(EffectType.SHELL_EJECT).priority());
        assertEquals(SoundPriority.LOW, EffectSoundTable.specFor(EffectType.FIRE_ZONE).priority());
        // The fire patch and the casings are the two sounds a match must be able to drop.
        assertEquals(2, EffectSoundTable.specFor(EffectType.FIRE_ZONE).maxVoices());
        assertEquals(2, EffectSoundTable.specFor(EffectType.SHELL_EJECT).maxVoices());
    }
}
