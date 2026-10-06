package io.github.skystrike.shared.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The M1 safety property: while the master switch is off, every read is the safe default no
 * matter what was stored — a stray toggle is inert at the read site, not merely refused at the
 * parse site.
 */
class DebugStateTest {

    @Test
    @DisplayName("with the master off, every stored intent reads back as the safe default")
    void masterOffMeansEveryReadIsDefault() {
        DebugState state = new DebugState(false);
        state.setOverlay(true)
            .setHitboxes(true)
            .setSdfView(true)
            .setFreecam(true)
            .setShadows(true)
            .setPlayerLight(true)
            .setPlayerLightShadows(true)
            .setInfiniteAmmo(true)
            .setNoclip(true)
            .setGodmode(true)
            .setFxDebug(true)
            .setContrastTest(true)
            .setTimescale(4f);

        assertFalse(state.masterEnabled());
        assertFalse(state.overlay());
        assertFalse(state.hitboxes());
        assertFalse(state.sdfView());
        assertFalse(state.freecam());
        assertFalse(state.shadows());
        assertFalse(state.playerLight());
        assertFalse(state.playerLightShadows());
        assertFalse(state.infiniteAmmo());
        assertFalse(state.noclip());
        assertFalse(state.godmode());
        assertFalse(state.fxDebug());
        assertFalse(state.contrastTest());
        assertEquals(DebugState.DEFAULT_TIMESCALE, state.timescale());
    }

    @Test
    @DisplayName("with the master on, stored values read back as stored")
    void masterOnMeansReadsFollowStorage() {
        DebugState state = new DebugState(true);
        state.setNoclip(true).setGodmode(true).setContrastTest(true).setTimescale(2.5f);

        assertTrue(state.noclip());
        assertTrue(state.godmode());
        assertTrue(state.contrastTest());
        assertEquals(2.5f, state.timescale(), 1e-6f);
        assertFalse(state.freecam(), "untouched features still read false");
        assertFalse(state.overlay());
    }

    @Test
    @DisplayName("a non-positive or non-finite timescale falls back to the default, even with the master on")
    void timescaleRejectsNonsense() {
        DebugState state = new DebugState(true);
        state.setTimescale(0f);
        assertEquals(DebugState.DEFAULT_TIMESCALE, state.timescale());
        state.setTimescale(Float.NaN);
        assertEquals(DebugState.DEFAULT_TIMESCALE, state.timescale());
        state.setTimescale(Float.POSITIVE_INFINITY);
        assertEquals(DebugState.DEFAULT_TIMESCALE, state.timescale());
    }
}
