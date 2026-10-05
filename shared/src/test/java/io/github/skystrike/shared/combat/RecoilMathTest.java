package io.github.skystrike.shared.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.WeaponConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Recoil has three channels and one multiplier. These tests pin the multiplier's composition
 * and the sign conventions the channels depend on — in particular that firing downward pushes
 * you up, which is the trick the movement system is built to reward.
 */
class RecoilMathTest {

    private static final float EPSILON = 1e-4f;

    @Test
    @DisplayName("the ADS blend eases rather than snapping")
    void adsBlendIsContinuous() {
        float blend = RecoilMath.blendAds(0f, true, 1f / 60f);
        assertTrue(blend > 0f && blend < 1f, "one frame of aiming is a partial blend: " + blend);

        for (int i = 0; i < 120; i++) {
            blend = RecoilMath.blendAds(blend, true, 1f / 60f);
        }
        assertEquals(1f, blend, 0.01f, "holding ADS converges to 1");

        for (int i = 0; i < 120; i++) {
            blend = RecoilMath.blendAds(blend, false, 1f / 60f);
        }
        assertEquals(0f, blend, 0.01f, "releasing ADS converges back to 0");
    }

    @Test
    @DisplayName("the multiplier composes ADS, movement, airborne and burst")
    void multiplierComposition() {
        float ads = 0.5f;
        float moving = 1.8f;

        assertEquals(1f, RecoilMath.recoilMultiplier(0f, ads, false, moving, false, false), EPSILON);
        assertEquals(ads, RecoilMath.recoilMultiplier(1f, ads, false, moving, false, false), EPSILON);
        assertEquals(0.75f, RecoilMath.recoilMultiplier(0.5f, ads, false, moving, false, false), EPSILON,
            "a half blend is halfway between hip and ADS");

        assertEquals(moving,
            RecoilMath.recoilMultiplier(0f, ads, true, moving, false, false), EPSILON);
        assertEquals(WeaponConfig.AIRBORNE_RECOIL_MULTIPLIER,
            RecoilMath.recoilMultiplier(0f, ads, false, moving, true, false), EPSILON);
        assertEquals(WeaponConfig.BURST_RECOIL_MULTIPLIER,
            RecoilMath.recoilMultiplier(0f, ads, false, moving, false, true), EPSILON);

        float everything = RecoilMath.recoilMultiplier(1f, ads, true, moving, true, true);
        assertEquals(
            ads * moving * WeaponConfig.AIRBORNE_RECOIL_MULTIPLIER * WeaponConfig.BURST_RECOIL_MULTIPLIER,
            everything,
            EPSILON);

        // Out-of-range blends are clamped, not extrapolated.
        assertEquals(ads, RecoilMath.recoilMultiplier(5f, ads, false, moving, false, false), EPSILON);
        assertEquals(1f, RecoilMath.recoilMultiplier(-5f, ads, false, moving, false, false), EPSILON);
    }

    @Test
    @DisplayName("the linear push is opposite the aim, so firing down lifts you")
    void linearPushOpposesAim() {
        float impulse = 100f;

        // Firing right pushes left.
        assertEquals(-impulse, RecoilMath.linearPushX(0f, impulse), 0.01f);
        assertEquals(0f, RecoilMath.linearPushY(0f, impulse), 0.01f);

        // Firing straight down pushes straight up: free height while airborne.
        assertEquals(impulse, RecoilMath.linearPushY(-90f, impulse), 0.01f);
        assertEquals(0f, RecoilMath.linearPushX(-90f, impulse), 0.01f);

        // Firing straight up pushes down.
        assertEquals(-impulse, RecoilMath.linearPushY(90f, impulse), 0.01f);

        // Firing left pushes right.
        assertEquals(impulse, RecoilMath.linearPushX(180f, impulse), 0.01f);
    }

    @Test
    @DisplayName("body torque follows the horizontal component of the aim")
    void angularKickFollowsAim() {
        float impulse = 40f;
        assertEquals(impulse, RecoilMath.angularKick(0f, impulse), 0.01f);
        assertEquals(-impulse, RecoilMath.angularKick(180f, impulse), 0.01f);
        assertEquals(0f, RecoilMath.angularKick(90f, impulse), 0.01f,
            "firing straight up applies no spin");
        assertEquals(0f, RecoilMath.angularKick(-90f, impulse), 0.01f);
    }

    @Test
    @DisplayName("the visual kick is capped at 35 degrees and decays at 120 deg/s")
    void visualKickCapAndDecay() {
        float kick = 0f;
        for (int i = 0; i < 100; i++) {
            kick = RecoilMath.addVisualKick(kick, 5f);
        }
        assertEquals(WeaponConfig.VISUAL_KICK_MAX_DEGREES, kick, EPSILON);

        float negative = 0f;
        for (int i = 0; i < 100; i++) {
            negative = RecoilMath.addVisualKick(negative, -5f);
        }
        assertEquals(-WeaponConfig.VISUAL_KICK_MAX_DEGREES, negative, EPSILON);

        // 120 deg/s: one second of decay removes 120 degrees, so 35 is long gone.
        assertEquals(0f, RecoilMath.decayVisualKick(35f, 1f), EPSILON);
        assertEquals(33f, RecoilMath.decayVisualKick(35f, 1f / 60f), EPSILON);
        assertEquals(-33f, RecoilMath.decayVisualKick(-35f, 1f / 60f), EPSILON,
            "decay pulls toward zero from either side");
        assertEquals(0f, RecoilMath.decayVisualKick(0f, 1f), EPSILON);

        assertEquals(35f / 120f, RecoilMath.visualKickDecaySeconds(35f), EPSILON);
    }
}
