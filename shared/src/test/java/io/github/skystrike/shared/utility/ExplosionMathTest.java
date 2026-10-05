package io.github.skystrike.shared.utility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.UtilityConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.utility.ExplosionMath.Blast;
import io.github.skystrike.shared.utility.ExplosionMath.BlastHit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Blast falloff and the absolute terrain occlusion rule from mechanics §6. */
class ExplosionMathTest {

    private final ArenaMap map = ArenaMap.standard();

    /** A frag, as the registry defines it: 350 radius, 100 damage, 950 impulse. */
    private static Blast frag(float x, float y) {
        return Blast.of(UtilityRegistry.of(UtilityId.FRAG), x, y, 1);
    }

    @Test
    @DisplayName("damage falls off linearly from the centre to zero at the radius")
    void linearFalloff() {
        Blast blast = frag(500f, 300f);

        assertEquals(100f, ExplosionMath.damageAt(blast, 500f, 300f, null), 0.01f);
        assertEquals(50f, ExplosionMath.damageAt(blast, 500f + 175f, 300f, null), 0.01f);
        assertEquals(25f, ExplosionMath.damageAt(blast, 500f + 262.5f, 300f, null), 0.01f);
        assertEquals(0f, ExplosionMath.damageAt(blast, 500f + 350f, 300f, null), 0.01f);
        assertEquals(0f, ExplosionMath.damageAt(blast, 500f + 400f, 300f, null), 0.01f,
            "outside the radius is nothing at all, not a trickle");
    }

    @Test
    @DisplayName("terrain blocks blast damage entirely, however close the detonation was")
    void terrainOcclusionIsAbsolute() {
        // Both cases are 120 units from the detonation at the same height. The only difference
        // is the lane pillar (x 1010..1034) standing between the second one and the blast.
        float clear = ExplosionMath.damageAt(frag(400f, 125f), 520f, 125f, map);
        float behindPillar = ExplosionMath.damageAt(frag(980f, 125f), 1100f, 125f, map);

        assertTrue(clear > 60f, "an unobstructed 120-unit hit should sting, was " + clear);
        assertEquals(0f, behindPillar, 0.0001f, "cover has to actually work");
    }

    @Test
    @DisplayName("every live target in range is hit exactly once, and nobody else is")
    void resolveHitsEachTargetOnce() {
        Blast blast = frag(500f, 125f);
        Player near = player(1, 520f);
        Player far = player(2, 1400f);
        Player dead = player(3, 540f);
        dead.alive = false;

        List<BlastHit> hits = ExplosionMath.resolve(blast, List.of(near, far, dead), null);

        assertEquals(1, hits.size(), "one entry per affected player: " + hits);
        assertEquals(1, hits.get(0).playerId());
        assertTrue(hits.get(0).damage() > 0f);
    }

    @Test
    @DisplayName("the thrower is not spared by their own grenade")
    void ownerTakesTheirOwnBlast() {
        Player thrower = player(1, 520f);
        Blast blast = new Blast(500f, 125f, 350f, 100f, 950f, thrower.id, UtilityId.FRAG.wireId());

        List<BlastHit> hits = ExplosionMath.resolve(blast, List.of(thrower), null);

        assertEquals(1, hits.size());
        assertEquals(thrower.id, hits.get(0).playerId());
    }

    @Test
    @DisplayName("knockback points away from the detonation and keeps bite at the edge")
    void impulseDirectionAndFalloff() {
        Blast blast = frag(500f, 125f);
        Player right = player(1, 600f);
        Player left = player(2, 400f);

        List<BlastHit> hits = ExplosionMath.resolve(blast, List.of(right, left), null);
        assertEquals(2, hits.size());

        BlastHit rightHit = hits.stream().filter(h -> h.playerId() == 1).findFirst().orElseThrow();
        BlastHit leftHit = hits.stream().filter(h -> h.playerId() == 2).findFirst().orElseThrow();
        assertTrue(rightHit.impulseX() > 0f, "pushed right, away from the blast");
        assertTrue(leftHit.impulseX() < 0f, "pushed left, away from the blast");

        float edgeImpulse = ExplosionMath.impulseAt(blast, blast.radius());
        assertEquals(950f * UtilityConfig.BLAST_EDGE_IMPULSE_FRACTION, edgeImpulse, 0.01f);
        assertEquals(950f, ExplosionMath.impulseAt(blast, 0f), 0.01f);
    }

    @Test
    @DisplayName("standing exactly on a grenade gets launched upward rather than dividing by zero")
    void zeroDistanceIsSafe() {
        Player unlucky = player(1, 500f);
        Blast blast = frag(unlucky.centerX(), unlucky.centerY());

        List<BlastHit> hits = ExplosionMath.resolve(blast, List.of(unlucky), null);

        assertEquals(1, hits.size());
        assertEquals(100f, hits.get(0).damage(), 0.01f);
        assertEquals(0f, hits.get(0).impulseX(), 0.01f);
        assertTrue(hits.get(0).impulseY() > 0f);
    }

    @Test
    @DisplayName("a blast built from a definition carries that utility's wire id for the kill feed")
    void blastCarriesItsSource() {
        Blast blast = Blast.of(UtilityRegistry.of(UtilityId.IMPACT), 100f, 100f, 42);

        assertEquals(UtilityId.IMPACT.wireId(), blast.weaponWireId());
        assertEquals(42, blast.ownerId());
        assertEquals(280f, blast.radius());
        assertEquals(85f, blast.maxDamage());
    }

    @Test
    @DisplayName("a zero-radius blast is a bug, not a point explosion")
    void radiusMustBePositive() {
        assertThrows(IllegalArgumentException.class,
            () -> new Blast(0f, 0f, 0f, 50f, 0f, 1, UtilityId.FRAG.wireId()));
    }

    @Test
    @DisplayName("resolving against nothing yields nothing instead of failing")
    void emptyInputsAreHarmless() {
        assertTrue(ExplosionMath.resolve(frag(0f, 0f), List.of(), null).isEmpty());
        assertTrue(ExplosionMath.resolve(frag(0f, 0f), null, null).isEmpty());
        assertTrue(ExplosionMath.resolve(null, List.of(), null).isEmpty());
    }

    /** A live player standing on the ground at {@code x}. */
    private static Player player(int id, float x) {
        return new Player(id, "P" + id, 0, x, 100f);
    }
}
