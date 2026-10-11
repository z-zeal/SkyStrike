package io.github.skystrike.shared.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The wire order of the effect vocabulary.
 *
 * <p>An {@link EffectType} travels as its ordinal, so inserting, reordering or removing a constant
 * silently rewrites the meaning of every effect event already in flight. This test names the order
 * explicitly: an append is a deliberate edit to this list, and anything else fails here before it
 * reaches a client that decoded the old numbering.
 */
class EffectTypeTest {

    @Test
    @DisplayName("the wire order is append-only; a new effect goes on the end")
    void ordinalsAreAppendOnly() {
        assertEquals(List.of(
            "FRAG_EXPLOSION",
            "IMPACT_EXPLOSION",
            "SMOKE_BURST",
            "MOLOTOV_SPLASH",
            "FIRE_ZONE",
            "POISON_BURST",
            "FLASH_DETONATION",
            "CLAYMORE_BLAST",
            "BULLET_IMPACT_CONCRETE",
            "BULLET_IMPACT_METAL",
            "BULLET_IMPACT_WOOD",
            "MUZZLE_FLASH",
            "SHELL_EJECT",
            "GLASS_SHATTER"), names());
    }

    @Test
    @DisplayName("ordinal decoding round-trips and refuses the value past the end")
    void ordinalRoundTrip() {
        for (EffectType type : EffectType.values()) {
            assertEquals(type, EffectType.fromOrdinal(type.ordinal()), type.toString());
            assertTrue(EffectType.isValidOrdinal(type.ordinal()), type.toString());
        }
        assertFalse(EffectType.isValidOrdinal(EffectType.values().length));
        assertFalse(EffectType.isValidOrdinal(-1));
    }

    private static List<String> names() {
        List<String> names = new ArrayList<>(EffectType.values().length);
        for (EffectType type : EffectType.values()) {
            names.add(type.name());
        }
        return names;
    }
}
