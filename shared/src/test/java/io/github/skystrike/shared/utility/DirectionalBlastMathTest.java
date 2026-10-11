package io.github.skystrike.shared.utility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.UtilityConfig;
import io.github.skystrike.shared.config.WorldConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.map.BlockKind;
import io.github.skystrike.shared.map.MapBlock;
import io.github.skystrike.shared.map.Material;
import io.github.skystrike.shared.map.Rect;
import io.github.skystrike.shared.model.Player;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pins the claymore's forward-only, terrain-occluded resolution. */
class DirectionalBlastMathTest {

    @Test
    @DisplayName("a directional blast reaches only targets in its forward cone, once each")
    void resolvesOnlyForwardTargets() {
        DirectionalBlastMath.Blast blast = new DirectionalBlastMath.Blast(
            500f, 500f, 220f, 110f, 600f, 0f,
            UtilityConfig.CLAYMORE_CONE_HALF_ANGLE_DEGREES, 1, UtilityId.CLAYMORE.wireId());
        Player forward = player(2, 600f, 500f);
        Player diagonal = player(3, 600f, 560f); // centre is clearly inside the 45° cone
        Player behind = player(4, 400f, 500f);
        Player distant = player(5, 800f, 500f);

        List<DirectionalBlastMath.BlastHit> hits =
            DirectionalBlastMath.resolve(blast, List.of(forward, diagonal, behind, distant), null);

        assertEquals(2, hits.size());
        assertTrue(hits.stream().anyMatch(hit -> hit.playerId() == forward.id));
        assertTrue(hits.stream().anyMatch(hit -> hit.playerId() == diagonal.id));
        assertTrue(hits.stream().noneMatch(hit -> hit.playerId() == behind.id));
        assertTrue(hits.stream().noneMatch(hit -> hit.playerId() == distant.id));
    }

    @Test
    @DisplayName("a target directly on the charge gets a safe upward impulse")
    void zeroDistanceIsSafe() {
        DirectionalBlastMath.Blast blast = new DirectionalBlastMath.Blast(
            500f, 500f, 220f, 110f, 600f, 0f, 45f, 1, UtilityId.CLAYMORE.wireId());
        Player target = player(2, 500f, 475f); // player centre is y + 25

        DirectionalBlastMath.BlastHit hit =
            DirectionalBlastMath.resolve(blast, List.of(target), null).get(0);

        assertEquals(0f, hit.impulseX(), 0.0001f);
        assertTrue(hit.impulseY() > 0f);
    }

    @Test
    @DisplayName("the charge's cone decides which blocks a directional blast reaches")
    void coneDecidesWhichBlocksAreReached() {
        ArenaMap map = ArenaMap.builder(WorldConfig.MIRROR_AXIS_X)
            .mirrored(new MapBlock(BlockKind.BREAKABLE, Material.WOOD, new Rect(1400f, 300f, 40f, 40f)))
            .build();
        MapBlock inFront = blockAt(map, 1400f);
        MapBlock behind = blockAt(map, 1560f);
        // Facing west from between the two crates: both are inside the 300-unit radius, and only
        // the cone separates them.
        DirectionalBlastMath.Blast charge = new DirectionalBlastMath.Blast(
            1450f, 320f, 300f, 100f, 0f, 180f, 45f, 1, UtilityId.CLAYMORE.wireId());

        assertEquals(
            ExplosionMath.falloff(100f, 10f, 300f),
            DirectionalBlastMath.damageAtBlock(charge, inFront, map),
            0.01f,
            "the crate the charge faces takes the hit on its near edge");
        assertEquals(
            0f,
            DirectionalBlastMath.damageAtBlock(charge, behind, map),
            0.0001f,
            "the crate behind the charge is outside the cone however close it stands");
    }

    private static MapBlock blockAt(ArenaMap map, float left) {
        for (MapBlock block : map.blocks()) {
            if (block.rect().x() == left) {
                return block;
            }
        }
        throw new AssertionError("no block with left edge " + left + " in " + map.blocks());
    }

    private static Player player(int id, float x, float y) {
        return new Player(id, "P" + id, 1, x, y);
    }
}
