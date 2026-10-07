package io.github.skystrike.shared.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.model.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The reticle is a function of the two numbers the server already owns, and nothing else. */
class CrosshairMathTest {

    @Test
    @DisplayName("a still, unkicked weapon draws the resting gap")
    void restingGap() {
        assertEquals(CrosshairMath.BASE_GAP_PIXELS, CrosshairMath.gapPixels(0f, 0f, 0f), 1e-4f);
        assertEquals(CrosshairMath.BASE_GAP_PIXELS, CrosshairMath.gapPixels(null, 0f), 1e-4f);
    }

    @Test
    @DisplayName("spread and kick each widen the gap, by their own weights")
    void spreadAndKickWiden() {
        assertEquals(5f + 2f * 4.5f, CrosshairMath.gapPixels(2f, 0f, 0f), 1e-4f);
        assertEquals(5f + 3f * 2.0f, CrosshairMath.gapPixels(0f, 3f, 0f), 1e-4f);
        assertEquals(5f + 2f * 4.5f + 3f * 2.0f, CrosshairMath.gapPixels(2f, 3f, 0f), 1e-4f);
    }

    @Test
    @DisplayName("a downward kick widens the reticle exactly as an upward one does")
    void kickSignIsIgnored() {
        assertEquals(CrosshairMath.gapPixels(1f, 4f, 0f), CrosshairMath.gapPixels(1f, -4f, 0f), 1e-4f);
    }

    @Test
    @DisplayName("the gap is capped, so a bloomed shotgun stays on screen")
    void gapIsCapped() {
        assertEquals(CrosshairMath.MAX_GAP_PIXELS, CrosshairMath.gapPixels(90f, 30f, 0f), 1e-4f);
    }

    @Test
    @DisplayName("aiming tightens the whole reticle and flips its form once")
    void adsTightensAndFlips() {
        float hip = CrosshairMath.gapPixels(4f, 0f, 0f);
        float aimed = CrosshairMath.gapPixels(4f, 0f, 1f);
        assertEquals(hip * CrosshairMath.ADS_GAP_MULTIPLIER, aimed, 1e-4f);
        assertTrue(aimed < hip);

        float halfway = CrosshairMath.gapPixels(4f, 0f, 0.5f);
        assertTrue(halfway < hip && halfway > aimed, "the transition is continuous");

        assertFalse(CrosshairMath.adsForm(0f));
        assertFalse(CrosshairMath.adsForm(0.5f));
        assertTrue(CrosshairMath.adsForm(0.6f));
        assertTrue(CrosshairMath.adsForm(1f));
        assertTrue(CrosshairMath.adsForm(4f), "out-of-range alphas clamp rather than misbehave");
    }

    @Test
    @DisplayName("arms grow with the gap but never shrink below the resting length")
    void armLength() {
        assertEquals(CrosshairMath.BASE_ARM_PIXELS,
            CrosshairMath.armPixels(CrosshairMath.BASE_GAP_PIXELS), 1e-4f);
        assertEquals(CrosshairMath.BASE_ARM_PIXELS, CrosshairMath.armPixels(0f), 1e-4f);
        assertTrue(CrosshairMath.armPixels(40f) > CrosshairMath.BASE_ARM_PIXELS);
    }

    @Test
    @DisplayName("the player overload reads the live spread and kick off the player")
    void readsThePlayer() {
        Player player = new Player(3, "Nova", 0, 0f, 0f);
        player.spread = 2.5f;
        player.gunKick = -1.5f;
        assertEquals(CrosshairMath.gapPixels(2.5f, -1.5f, 0.25f),
            CrosshairMath.gapPixels(player, 0.25f), 1e-4f);
    }

    @Test
    @DisplayName("the hit marker expands as it fades")
    void hitMarkerExpands() {
        assertEquals(CrosshairMath.HIT_MARKER_GAP_PIXELS,
            CrosshairMath.hitMarkerGapPixels(1f), 1e-4f);
        assertEquals(CrosshairMath.HIT_MARKER_GAP_PIXELS + CrosshairMath.HIT_MARKER_EXPANSION_PIXELS,
            CrosshairMath.hitMarkerGapPixels(0f), 1e-4f);
    }

    @Test
    @DisplayName("nonsense inputs produce a drawable reticle, never NaN")
    void nonsenseInputs() {
        assertEquals(CrosshairMath.BASE_GAP_PIXELS,
            CrosshairMath.gapPixels(Float.NaN, Float.NaN, Float.NaN), 1e-4f);
        assertEquals(CrosshairMath.BASE_GAP_PIXELS, CrosshairMath.gapPixels(-10f, 0f, 0f), 1e-4f);
    }
}
