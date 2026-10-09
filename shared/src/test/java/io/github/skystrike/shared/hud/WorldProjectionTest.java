package io.github.skystrike.shared.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorldProjectionTest {

    // A 1000 x 500 world-unit view centred on (1500, 1000), drawn into a 1920 x 1080 window.
    private final WorldProjection projection = new WorldProjection(1500f, 1000f, 1000f, 500f, 1920, 1080);

    @Test
    @DisplayName("the view centre lands on the middle of the screen")
    void centreMaps() {
        assertEquals(960f, projection.toScreenX(1500f), 1e-3f);
        assertEquals(540f, projection.toScreenY(1000f), 1e-3f);
    }

    @Test
    @DisplayName("the view edges land on the screen edges, y-up")
    void edgesMap() {
        assertEquals(0f, projection.toScreenX(1000f), 1e-3f);
        assertEquals(1920f, projection.toScreenX(2000f), 1e-3f);
        assertEquals(0f, projection.toScreenY(750f), 1e-3f);
        assertEquals(1080f, projection.toScreenY(1250f), 1e-3f);
    }

    @Test
    @DisplayName("a higher world y is a higher screen y (no flip)")
    void yGrowsUpward() {
        assertTrue(projection.toScreenY(1100f) > projection.toScreenY(1000f));
    }

    @Test
    @DisplayName("on-screen test honours the margin and rejects points outside the view")
    void onScreen() {
        assertTrue(projection.onScreen(1500f, 1000f, 0f));
        assertFalse(projection.onScreen(2500f, 1000f, 0f));
        assertFalse(projection.onScreen(2010f, 1000f, 0f));
        assertTrue(projection.onScreen(2010f, 1000f, 200f), "a generous margin keeps the edge");
    }
}
