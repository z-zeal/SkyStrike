package io.github.skystrike.shared.utility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.UtilityConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.utility.StunMath.Band;
import io.github.skystrike.shared.utility.StunMath.StunEffect;
import io.github.skystrike.shared.utility.StunMath.StunHit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Stun banding and the no-line-of-sight concussion from mechanics §6.1. */
class StunMathTest {

    private static final float RADIUS = 600f;

    private final ArenaMap map = ArenaMap.standard();

    @Test
    @DisplayName("the stun grenade's radius is the banding radius")
    void radiusMatchesTheCatalogue() {
        assertEquals(RADIUS, UtilityRegistry.of(UtilityId.STUN).radius());
    }

    @Test
    @DisplayName("bands split the radius at 30% and 70%")
    void bandBoundaries() {
        assertEquals(Band.INNER, StunMath.bandFor(0f, RADIUS));
        assertEquals(Band.INNER, StunMath.bandFor(179f, RADIUS));
        assertEquals(Band.INNER, StunMath.bandFor(180f, RADIUS), "30% is inclusive");
        assertEquals(Band.MIDDLE, StunMath.bandFor(181f, RADIUS));
        assertEquals(Band.MIDDLE, StunMath.bandFor(420f, RADIUS), "70% is inclusive");
        assertEquals(Band.OUTER, StunMath.bandFor(421f, RADIUS));
        assertEquals(Band.OUTER, StunMath.bandFor(599f, RADIUS));
        assertEquals(Band.OUT_OF_RANGE, StunMath.bandFor(600f, RADIUS));
        assertEquals(Band.OUT_OF_RANGE, StunMath.bandFor(10f, 0f));
    }

    @Test
    @DisplayName("each band's durations come straight from the plan")
    void bandDurations() {
        assertEffect(100f, 7.0f, 4.0f);
        assertEffect(300f, 4.5f, 2.5f);
        assertEffect(500f, 2.0f, 1.0f);

        StunEffect outOfRange = StunMath.effectFor(0f, 0f, RADIUS, 700f, 0f, true);
        assertEquals(StunEffect.NONE, outOfRange);
        assertFalse(outOfRange.isAnything());
    }

    @Test
    @DisplayName("no line of sight means a 0.3 s concussion and no slow, even point blank")
    void noSightIsJustAConcussion() {
        StunEffect pointBlank = StunMath.effectFor(0f, 0f, RADIUS, 10f, 0f, false);

        assertEquals(UtilityConfig.STUN_NO_SIGHT_CONCUSSION_SECONDS, pointBlank.blindSeconds(), 0.001f);
        assertEquals(0f, pointBlank.slowSeconds(), 0.001f);
        assertTrue(pointBlank.blindSeconds()
            < StunMath.effectFor(0f, 0f, RADIUS, 10f, 0f, true).blindSeconds(),
            "turning away has to be worth doing");
    }

    @Test
    @DisplayName("out of range beats everything: no sight check saves or damns you past the radius")
    void outOfRangeIsAlwaysNothing() {
        assertEquals(StunEffect.NONE, StunMath.effectFor(0f, 0f, RADIUS, 601f, 0f, false));
        assertEquals(StunEffect.NONE, StunMath.effectFor(0f, 0f, RADIUS, 601f, 0f, true));
    }

    @Test
    @DisplayName("resolve skips the dead and the distant, and reports one hit per player")
    void resolveFiltersCandidates() {
        Player close = new Player(1, "Close", 0, 600f, 100f);
        Player distant = new Player(2, "Distant", 0, 2500f, 100f);
        Player dead = new Player(3, "Dead", 0, 620f, 100f);
        dead.alive = false;

        List<StunHit> hits = StunMath.resolve(500f, 142.5f, RADIUS, List.of(close, distant, dead), null);

        assertEquals(1, hits.size(), "one entry per affected player: " + hits);
        assertEquals(1, hits.get(0).playerId());
        assertTrue(hits.get(0).hadLineOfSight());
        assertEquals(Band.INNER, StunMath.bandFor(hits.get(0).distance(), RADIUS));
    }

    @Test
    @DisplayName("a player behind the lane pillar is only concussed")
    void coverDowngradesTheStun() {
        // The lane pillar (x 1010..1034, y 100..360) stands between the two.
        Player sheltered = new Player(1, "Sheltered", 0, 1100f, 100f);

        List<StunHit> hits = StunMath.resolve(980f, 142.5f, RADIUS, List.of(sheltered), map);

        assertEquals(1, hits.size());
        assertFalse(hits.get(0).hadLineOfSight());
        assertEquals(UtilityConfig.STUN_NO_SIGHT_CONCUSSION_SECONDS,
            hits.get(0).effect().blindSeconds(), 0.001f);
        assertEquals(0f, hits.get(0).effect().slowSeconds(), 0.001f);
    }

    @Test
    @DisplayName("blindness decays from full and clears late rather than fading flatly")
    void blindnessDecaysExponentially() {
        float total = 7.0f;

        assertEquals(1f, StunMath.blindIntensity(total, total), 0.001f);
        assertEquals(0f, StunMath.blindIntensity(0f, total), 0.001f);

        float halfway = StunMath.blindIntensity(total * 0.5f, total);
        assertTrue(halfway < 0.5f, "an exponential curve is already past half at the midpoint");
        assertTrue(halfway > 0f);
        assertTrue(StunMath.blindIntensity(total * 0.25f, total) < halfway, "it must be monotonic");
    }

    @Test
    @DisplayName("stunned players move at 30% speed")
    void slowMultiplier() {
        assertEquals(0.30f, StunMath.moveSpeedMultiplier(true), 0.0001f);
        assertEquals(1f, StunMath.moveSpeedMultiplier(false), 0.0001f);
    }

    private static void assertEffect(float distance, float blind, float slow) {
        StunEffect effect = StunMath.effectFor(0f, 0f, RADIUS, distance, 0f, true);
        assertEquals(blind, effect.blindSeconds(), 0.001f, "blind at " + distance);
        assertEquals(slow, effect.slowSeconds(), 0.001f, "slow at " + distance);
    }
}
