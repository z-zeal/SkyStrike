package io.github.skystrike.shared.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.model.HitZone;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.net.s2c.PacketDamageEvent;
import io.github.skystrike.shared.weapons.WeaponId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Which edge lights up, how hard, and for how long. */
class DamageVignetteMathTest {

    private Player victim;

    @BeforeEach
    void setUp() {
        victim = new Player(2, "Nova", 0, 100f, 100f);
    }

    private PacketDamageEvent hitAt(float x, float y, float amount) {
        return new PacketDamageEvent(9, victim.id, amount, victim.health - amount,
            HitZone.BODY, WeaponId.DEFAULT.ordinal(), x, y, 300f, false);
    }

    @Test
    @DisplayName("the tint points at the impact, in the aim-angle convention")
    void directionFollowsTheImpact() {
        float centerX = victim.centerX();
        float centerY = victim.centerY();
        assertEquals(0f, DamageVignetteMath.directionDegrees(centerX, centerY, centerX + 50f, centerY), 1e-3f);
        assertEquals(90f, DamageVignetteMath.directionDegrees(centerX, centerY, centerX, centerY + 50f), 1e-3f);
        assertEquals(180f, DamageVignetteMath.directionDegrees(centerX, centerY, centerX - 50f, centerY), 1e-3f);
        assertEquals(-90f, DamageVignetteMath.directionDegrees(centerX, centerY, centerX, centerY - 50f), 1e-3f);

        assertEquals(180f,
            DamageVignetteMath.directionDegrees(hitAt(centerX - 80f, centerY, 20f), victim), 1e-3f);
        assertEquals(0f, DamageVignetteMath.directionDegrees(null, victim), 1e-4f);
        assertEquals(0f, DamageVignetteMath.directionDegrees(hitAt(0f, 0f, 10f), null), 1e-4f);
    }

    @Test
    @DisplayName("an impact inside the victim has no direction to blame")
    void selfInflictedHasNoDirection() {
        assertTrue(DamageVignetteMath.isDirectional(hitAt(victim.centerX() + 40f, victim.centerY(), 10f), victim));
        assertFalse(DamageVignetteMath.isDirectional(hitAt(victim.centerX(), victim.centerY(), 10f), victim));
        assertFalse(DamageVignetteMath.isDirectional(null, victim));
        assertFalse(DamageVignetteMath.isDirectional(hitAt(0f, 0f, 10f), null));
    }

    @Test
    @DisplayName("intensity is the share of the bar the hit took, floored and capped")
    void intensityScalesWithDamage() {
        assertEquals(0f, DamageVignetteMath.intensity(0f), 1e-4f);
        assertEquals(0f, DamageVignetteMath.intensity(-5f), 1e-4f);
        assertEquals(DamageVignetteMath.MIN_INTENSITY, DamageVignetteMath.intensity(1f), 1e-4f);

        // 35% of 150 saturates: 52.5 damage and anything above reads 1.
        assertEquals(1f, DamageVignetteMath.intensity(52.5f), 1e-4f);
        assertEquals(1f, DamageVignetteMath.intensity(150f), 1e-4f);
        assertEquals(0.5f, DamageVignetteMath.intensity(26.25f), 1e-4f);
    }

    @Test
    @DisplayName("the flash decays to nothing within its own duration")
    void fadeCurve() {
        assertEquals(1f, DamageVignetteMath.fade(0f), 1e-4f);
        assertEquals(0.25f, DamageVignetteMath.fade(DamageVignetteMath.DURATION_SECONDS / 2f), 1e-4f);
        assertEquals(0f, DamageVignetteMath.fade(DamageVignetteMath.DURATION_SECONDS), 1e-4f);
        assertEquals(0f, DamageVignetteMath.fade(5f), 1e-4f);
        assertEquals(0f, DamageVignetteMath.fade(-1f), 1e-4f);

        assertEquals(1f, DamageVignetteMath.alpha(60f, 0f), 1e-4f);
        assertEquals(0f, DamageVignetteMath.alpha(60f, DamageVignetteMath.DURATION_SECONDS), 1e-4f);
    }

    @Test
    @DisplayName("two hits do not average into a direction nobody was shot from")
    void mergeKeepsTheStrongerBearing() {
        assertEquals(180f, DamageVignetteMath.mergeDirection(0f, 0.2f, 180f, 0.9f), 1e-4f);
        assertEquals(0f, DamageVignetteMath.mergeDirection(0f, 0.9f, 180f, 0.2f), 1e-4f);
        assertEquals(-90f, DamageVignetteMath.mergeDirection(0f, 0.5f, 270f, 0.5f), 1e-4f,
            "the incoming bearing wins a tie, wrapped into range");
    }
}
