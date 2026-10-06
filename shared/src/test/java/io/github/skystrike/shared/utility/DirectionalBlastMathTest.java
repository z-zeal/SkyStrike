package io.github.skystrike.shared.utility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.UtilityConfig;
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

    private static Player player(int id, float x, float y) {
        return new Player(id, "P" + id, 1, x, y);
    }
}
