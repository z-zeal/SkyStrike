package io.github.skystrike.shared.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.CombatConfig;
import io.github.skystrike.shared.config.PlayerConfig;
import io.github.skystrike.shared.model.HitZone;
import io.github.skystrike.shared.model.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The head is the top 28% of the player's <i>current</i> height, which is the whole point:
 * crouching moves the head down with the body instead of leaving it hanging in the air.
 */
class HitZoneMathTest {

    private static final float EPSILON = 1e-4f;

    private static Player standing() {
        Player p = new Player(1, "Target", 1, 500f, 200f);
        p.crouched = false;
        return p;
    }

    @Test
    @DisplayName("the head zone starts at 72% of a standing player's height")
    void headZoneBoundaryStanding() {
        Player p = standing();
        float expected = 200f + PlayerConfig.STAND_HEIGHT * 0.72f;
        assertEquals(expected, HitZoneMath.headZoneBottom(p), EPSILON);

        assertSame(HitZone.HEAD, HitZoneMath.resolve(expected, p), "the boundary itself is a head");
        assertSame(HitZone.HEAD, HitZoneMath.resolve(expected + 0.1f, p));
        assertSame(HitZone.BODY, HitZoneMath.resolve(expected - 0.1f, p));
        assertSame(HitZone.BODY, HitZoneMath.resolve(200f, p), "the feet are not a head");
    }

    @Test
    @DisplayName("crouching lowers the head zone with the body")
    void crouchingLowersTheHeadZone() {
        Player p = standing();
        float standingHead = HitZoneMath.headZoneBottom(p);

        p.crouched = true;
        float crouchedHead = HitZoneMath.headZoneBottom(p);

        assertTrue(crouchedHead < standingHead, "a crouched head is lower");
        assertEquals(200f + PlayerConfig.CROUCH_HEIGHT * 0.72f, crouchedHead, EPSILON);

        // A shot that was a headshot standing goes over a crouched player's head entirely, and a
        // shot aimed at a crouched head would have hit a standing player in the chest.
        assertSame(HitZone.BODY, HitZoneMath.resolve(crouchedHead - 0.1f, p));
        assertSame(HitZone.HEAD, HitZoneMath.resolve(crouchedHead, p));

        p.crouched = false;
        assertSame(HitZone.BODY, HitZoneMath.resolve(crouchedHead, p),
            "the crouched head height is body height when standing");
    }

    @Test
    @DisplayName("the head zone is exactly 28% of the hitbox")
    void headZoneIsTwentyEightPercent() {
        float height = PlayerConfig.STAND_HEIGHT;
        float bottom = HitZoneMath.headZoneBottom(0f, height);
        float zoneHeight = height - bottom;
        assertEquals(height * CombatConfig.HEAD_ZONE_FRACTION, zoneHeight, EPSILON);
    }

    @Test
    @DisplayName("a headshot is worth exactly double")
    void multipliers() {
        assertEquals(2.0f, HitZoneMath.multiplier(HitZone.HEAD), EPSILON);
        assertEquals(1.0f, HitZoneMath.multiplier(HitZone.BODY), EPSILON);
        assertEquals(1.0f, HitZoneMath.multiplier(null), EPSILON);

        assertEquals(96f, HitZoneMath.applyZone(48f, HitZone.HEAD), EPSILON);
        assertEquals(48f, HitZoneMath.applyZone(48f, HitZone.BODY), EPSILON);
    }

    @Test
    @DisplayName("a zero-height target cannot be headshot")
    void degenerateHeight() {
        assertSame(HitZone.BODY, HitZoneMath.resolve(100f, 0f, 0f));
    }

    @Test
    @DisplayName("the fuel tank placeholder is inert until a player actually carries one")
    void fuelTankIsPhaseSixOnly() {
        // Same impact, with and without the gadget. Phase 3 always passes false.
        float footY = 200f;
        float height = PlayerConfig.STAND_HEIGHT;
        float impactY = footY + height * 0.5f;
        float centreX = 500f;
        float rearX = centreX - PlayerConfig.WIDTH / 2f + 1f;

        assertSame(
            HitZone.BODY,
            HitZoneMath.resolveWithGadget(rearX, impactY, footY, centreX, height, true, false));
        assertSame(
            HitZone.FUEL_TANK,
            HitZoneMath.resolveWithGadget(rearX, impactY, footY, centreX, height, true, true));

        // A head hit stays a head hit even with a tank fitted.
        float headY = footY + height * 0.9f;
        assertSame(
            HitZone.HEAD,
            HitZoneMath.resolveWithGadget(rearX, headY, footY, centreX, height, true, true));
    }
}
