package io.github.skystrike.shared.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The enemy-body sighting fade: the anti-flicker envelope and its bounded, wall-clock-aged
 * tracker. The body must ease in and out at the cone edge and reach exactly zero — never blink,
 * and never linger as a wallhack.
 */
class SightingFadeTest {

    private static final float FRAME = 1f / 60f;
    private static final long T0 = 1_000_000L;

    private static long at(float seconds) {
        return T0 + (long) (seconds * 1000f);
    }

    @Test
    @DisplayName("a lit sighting fades in fast from invisible")
    void fadesInFast() {
        SightingFade tracker = new SightingFade();
        assertEquals(0f, tracker.alphaFor(7, true, at(0f)), 0.0001f,
            "the first lit frame starts the fade from exactly zero");
        float alpha = 0f;
        float seconds = 0f;
        while (seconds < SightingFade.FADE_IN_SECONDS + FRAME) {
            seconds += FRAME;
            alpha = tracker.alphaFor(7, true, at(seconds));
        }
        assertEquals(1f, alpha, 0.02f, "fully lit after one fade-in window");
    }

    @Test
    @DisplayName("a lost sighting holds, then fades to exactly zero and is forgotten")
    void holdThenFadeToZero() {
        SightingFade tracker = new SightingFade();
        float alpha = 0f;
        for (float seconds = 0f; seconds <= 0.2f; seconds += FRAME) {
            alpha = tracker.alphaFor(3, true, at(seconds));
        }
        assertEquals(1f, alpha, 0.02f);

        // Within the hold the alpha freezes at full: a dip at the cone edge never darkens.
        float held = tracker.alphaFor(3, false, at(0.2f + SightingFade.HOLD_SECONDS * 0.5f));
        assertEquals(1f, held, 0.0001f, "the hold is the hysteresis");

        // After the hold it falls, and the sighting expires at exactly zero alpha.
        float seconds = 0.2f + SightingFade.HOLD_SECONDS;
        while (seconds < 0.2f + SightingFade.LIFETIME_SECONDS + FRAME) {
            seconds += FRAME;
            alpha = tracker.alphaFor(3, false, at(seconds));
            assertTrue(alpha >= 0f && alpha <= 1f);
        }
        assertEquals(0f, alpha, 0.0001f, "unlit reaches exactly zero — the body is not drawn");
        assertEquals(0f, tracker.alphaFor(3, false, at(seconds + 1f)), 0.0001f);
        assertEquals(0, tracker.trackedCount(), "an expired sighting is forgotten");
    }

    @Test
    @DisplayName("re-acquiring mid-fade continues up from the current alpha, never blinking")
    void reacquisitionDoesNotBlink() {
        SightingFade tracker = new SightingFade();
        for (float seconds = 0f; seconds <= 0.2f; seconds += FRAME) {
            tracker.alphaFor(5, true, at(seconds));
        }
        // Lose the sighting past the hold so the fade-out has begun, then re-acquire.
        float lostAt = 0.2f + SightingFade.HOLD_SECONDS + 0.15f;
        float faded = tracker.alphaFor(5, false, at(lostAt));
        assertTrue(faded < 1f && faded > 0f, "mid-fade alpha when the sighting returns");
        float back = tracker.alphaFor(5, true, at(lostAt + FRAME));
        assertTrue(back >= faded - 0.0001f,
            "a returning sighting rises from where it was; it never dips to zero first");
    }

    @Test
    @DisplayName("untracked targets read as invisible without polluting the map")
    void untrackedIsInvisible() {
        SightingFade tracker = new SightingFade();
        assertEquals(0f, tracker.alphaFor(9, false, at(0f)), 0.0001f);
        assertEquals(0, tracker.trackedCount());
    }

    @Test
    @DisplayName("retain drops targets that are gone; clear and the respawn edge wipe everything")
    void retentionAndTeardown() {
        SightingFade tracker = new SightingFade();
        tracker.alphaFor(1, true, at(0f));
        tracker.alphaFor(2, true, at(0f));
        tracker.retain(List.of(1));
        assertEquals(1, tracker.trackedCount(), "an absent target is a dead or departed one");

        tracker.forget(1);
        assertEquals(0, tracker.trackedCount());

        tracker.alphaFor(4, true, at(1f));
        tracker.onViewerAlive(false);
        tracker.onViewerAlive(true);
        assertEquals(0, tracker.trackedCount(), "a new life starts with no sightings");

        tracker.alphaFor(4, true, at(2f));
        tracker.clear();
        assertEquals(0, tracker.trackedCount());
    }

    @Test
    @DisplayName("a frame spike ages on the wall clock without emptying or hoarding the map")
    void wallClockAges() {
        SightingFade tracker = new SightingFade();
        tracker.alphaFor(6, true, at(0f));
        // One call two seconds later: the sighting has long expired, so it is simply gone.
        assertEquals(0f, tracker.alphaFor(6, false, at(2f)), 0.0001f);
        assertEquals(0, tracker.trackedCount());
    }
}
