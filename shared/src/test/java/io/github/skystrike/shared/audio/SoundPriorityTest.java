package io.github.skystrike.shared.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.effect.EffectType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The voice-stealing rule the mixer applies when the pool is full: the ranking exists so that the
 * sound the player is being hit by always wins.
 */
class SoundPriorityTest {

    @Test
    @DisplayName("the ranking is a total order, highest first")
    void ranking() {
        assertTrue(SoundPriority.CRITICAL.outranks(SoundPriority.HIGH));
        assertTrue(SoundPriority.HIGH.outranks(SoundPriority.NORMAL));
        assertTrue(SoundPriority.NORMAL.outranks(SoundPriority.LOW));
        assertEquals(3, SoundPriority.CRITICAL.rank());
        assertEquals(0, SoundPriority.LOW.rank());
    }

    @Test
    @DisplayName("ties never steal a voice, and a null rival never blocks one")
    void tiesAndNulls() {
        assertFalse(SoundPriority.NORMAL.outranks(SoundPriority.NORMAL));
        assertFalse(SoundPriority.CRITICAL.outranks(null));
        assertTrue(SoundPriority.CRITICAL.atLeast(SoundPriority.CRITICAL));
        assertTrue(SoundPriority.CRITICAL.atLeast(null));
        assertFalse(SoundPriority.LOW.atLeast(SoundPriority.NORMAL));
    }

    @Test
    @DisplayName("a casing can never mute an explosion, and an explosion always wins a slot")
    void theDocumentedPoolingRule() {
        SoundSpec casing = EffectSoundTable.specFor(EffectType.SHELL_EJECT);
        SoundSpec frag = EffectSoundTable.specFor(EffectType.FRAG_EXPLOSION);
        assertFalse(casing.priority().outranks(frag.priority()));
        assertTrue(frag.priority().outranks(casing.priority()));

        SoundSpec impact = EffectSoundTable.specFor(EffectType.BULLET_IMPACT_CONCRETE);
        assertFalse(impact.priority().outranks(frag.priority()),
            "an impact tick must not take the slot a detonation needs");
    }
}
