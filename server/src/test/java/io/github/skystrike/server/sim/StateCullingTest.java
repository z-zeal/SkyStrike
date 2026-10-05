package io.github.skystrike.server.sim;

import static org.junit.jupiter.api.Assertions.*;

import io.github.skystrike.server.player.PlayerRegistry;
import io.github.skystrike.server.player.PlayerSession;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.vision.VisionMath;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StateCullingTest {

    private ArenaMap arena;

    @BeforeEach
    void setUp() {
        arena = ArenaMap.standard();
    }

    @Test
    void testVisibleEnemyIsNotCulled() {
        Player teamAPlayer = new Player(1, "PlayerA", 0, 300f, 600f);
        teamAPlayer.aimAngle = 0f;

        Player teamBPlayer = new Player(2, "PlayerB", 1, 600f, 600f);

        assertTrue(VisionMath.canObserverSee(teamAPlayer, teamBPlayer, arena));
    }

    @Test
    void testOccludedEnemyIsCulled() {
        // Player inside centre room behind wall (x=1200, y=400)
        Player insidePlayer = new Player(1, "Inside", 1, 1200f, 400f);

        // Player outside room (x=1050, y=400)
        Player outsidePlayer = new Player(2, "Outside", 0, 1050f, 400f);
        outsidePlayer.aimAngle = 0f;

        assertFalse(VisionMath.canObserverSee(outsidePlayer, insidePlayer, arena));
    }

    @Test
    void testTeammatesShareVision() {
        // Teammate 1 is behind a wall and cannot see enemy
        Player teamMate1 = new Player(1, "Mate1", 0, 1050f, 400f);
        teamMate1.aimAngle = 0f;

        // Enemy is inside the room
        Player enemy = new Player(2, "Enemy", 1, 1200f, 400f);
        assertFalse(VisionMath.canObserverSee(teamMate1, enemy, arena));

        // Teammate 2 is also inside the room and CAN see enemy
        Player teamMate2 = new Player(3, "Mate2", 0, 1300f, 400f);
        teamMate2.aimAngle = 180f;
        assertTrue(VisionMath.canObserverSee(teamMate2, enemy, arena));

        // Combined team vision reveals enemy to the team
        assertTrue(VisionMath.canTeamSee(List.of(teamMate1, teamMate2), enemy, arena, null));
    }
}
