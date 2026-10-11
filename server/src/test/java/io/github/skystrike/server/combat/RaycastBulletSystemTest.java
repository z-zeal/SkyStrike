package io.github.skystrike.server.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.server.combat.RaycastBulletSystem.Hit;
import io.github.skystrike.server.combat.RaycastBulletSystem.HitType;
import io.github.skystrike.shared.config.WorldConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.map.BlockKind;
import io.github.skystrike.shared.map.BlockRules;
import io.github.skystrike.shared.map.Blocking;
import io.github.skystrike.shared.map.MapBlock;
import io.github.skystrike.shared.map.Material;
import io.github.skystrike.shared.map.Rect;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.weapons.WeaponId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The rule-aware terrain column of the bullet sweep.
 *
 * <p>The shipped arena is wall-only, so every wall answers a bullet the same way and nothing
 * about the shared rule table can be proved against it. These tests therefore author mixed-kind
 * maps: a round passes through intact glass and a one-way platform without the raycaster knowing
 * what either is, and stops on a wall, an intact breakable or a closed door — read from the
 * shared {@link Blocking#BULLETS} column rather than from a list of kinds.
 */
class RaycastBulletSystemTest {

    private static final float AXIS = WorldConfig.MIRROR_AXIS_X;
    private static final float PROBE_Y = 445f;
    private static final Rect CENTRED_RECT = new Rect(1490f, 400f, 20f, 90f);

    /** One isolated, centred block of a single kind, with nothing else on the probe line. */
    private static ArenaMap singleBlockMap(BlockKind kind) {
        return ArenaMap.builder(AXIS)
            .centred(new MapBlock(kind, Material.STONE, CENTRED_RECT))
            .build();
    }

    @Test
    @DisplayName("a round passes through the kinds the bullet column leaves out")
    void roundsPassThroughNonBlockingKinds() {
        for (BlockKind kind : List.of(BlockKind.GLASS, BlockKind.PLATFORM)) {
            ArenaMap map = singleBlockMap(kind);
            RaycastBulletSystem raycaster = new RaycastBulletSystem(map);

            assertNull(
                raycaster.resolve(1300f, PROBE_Y, 1700f, PROBE_Y, List.of(), 1, true),
                kind + " must not stop a round");
            assertFalse(
                raycaster.blockedByTerrain(1300f, PROBE_Y, 1700f, PROBE_Y, Blocking.BULLETS),
                kind + " must not block the muzzle guard");
        }
    }

    @Test
    @DisplayName("a round stops on the kinds the bullet column includes")
    void roundsStopOnBlockingKinds() {
        for (BlockKind kind : List.of(BlockKind.WALL, BlockKind.BREAKABLE, BlockKind.DOOR)) {
            RaycastBulletSystem raycaster = new RaycastBulletSystem(singleBlockMap(kind));

            Hit hit = raycaster.resolve(1300f, PROBE_Y, 1700f, PROBE_Y, List.of(), 1, true);

            assertNotNull(hit, kind + " must stop a round");
            assertEquals(HitType.TERRAIN, hit.type(), kind.toString());
            assertEquals(CENTRED_RECT, hit.terrain(), kind.toString());
            assertTrue(
                raycaster.blockedByTerrain(1300f, PROBE_Y, 1700f, PROBE_Y, Blocking.BULLETS),
                kind + " must block the muzzle guard");
        }
    }

    @Test
    @DisplayName("every purpose the raycaster is asked answers from the shared rule row")
    void blockedByTerrainFollowsTheSharedRuleTable() {
        for (BlockKind kind : BlockKind.values()) {
            RaycastBulletSystem raycaster = new RaycastBulletSystem(singleBlockMap(kind));
            for (Blocking purpose : Blocking.values()) {
                assertEquals(
                    BlockRules.blocks(kind, purpose),
                    raycaster.blockedByTerrain(1300f, PROBE_Y, 1700f, PROBE_Y, purpose),
                    kind + " / " + purpose);
            }
        }
    }

    @Test
    @DisplayName("a round passes the window and still stops on the wall behind it")
    void aWallBehindAGlassPaneStillProtectsTheBodyBehindIt() {
        // Glass at 1450..1470 and 1530..1550; a wall at 1700..1720 and 1280..1300. The probe runs
        // right, so it meets the glass pane first and the wall second.
        ArenaMap map = ArenaMap.builder(AXIS)
            .mirrored(new MapBlock(BlockKind.GLASS, Material.GLASS, new Rect(1450f, 400f, 20f, 90f)))
            .mirrored(new MapBlock(BlockKind.WALL, Material.STONE, new Rect(1700f, 400f, 20f, 90f)))
            .build();
        Rect wall = new Rect(1700f, 400f, 20f, 90f);

        // A body standing between the window and the wall is hit: the pane did not stop the round.
        Player between = new Player(2, "Between", 1, 1600f, 420f);
        Hit throughGlass = new RaycastBulletSystem(map)
            .resolve(1490f, PROBE_Y, 1900f, PROBE_Y, List.of(between), 1, true);
        assertNotNull(throughGlass, "the pane must not stop the round");
        assertEquals(HitType.PLAYER, throughGlass.type());
        assertNotNull(throughGlass.player());
        assertEquals(between.id, throughGlass.player().id);

        // A body behind the wall is not: the wall is the first thing the round meets.
        Player behind = new Player(3, "Behind", 1, 1800f, 420f);
        Hit stoppedByWall = new RaycastBulletSystem(map)
            .resolve(1490f, PROBE_Y, 1900f, PROBE_Y, List.of(behind), 1, true);
        assertNotNull(stoppedByWall);
        assertEquals(HitType.TERRAIN, stoppedByWall.type());
        assertEquals(wall, stoppedByWall.terrain());
    }

    @Test
    @DisplayName("the muzzle guard asks the bullet column, so glass never absorbs a shot")
    void theMuzzleGuardUsesTheBulletColumn() {
        // The eye-to-muzzle segment of a long-barrelled weapon crosses the centred block.
        Player shooter = new Player(1, "Shooter", 0, 1470f, 420f);
        shooter.aimAngle = 0f;

        BulletSystem throughGlass = new BulletSystem(singleBlockMap(BlockKind.GLASS));
        assertNotNull(
            throughGlass.spawn(shooter, WeaponId.IRON_CARBINE, 0f),
            "an intact window must not absorb the shot");
        assertEquals(1, throughGlass.count(), "the round is in flight");

        BulletSystem intoWall = new BulletSystem(singleBlockMap(BlockKind.WALL));
        assertNull(
            intoWall.spawn(shooter, WeaponId.IRON_CARBINE, 0f),
            "a wall at the muzzle still absorbs the shot");
        assertEquals(0, intoWall.count());
    }

    @Test
    @DisplayName("no arena means no terrain occlusion")
    void aMissingArenaBlocksNothing() {
        RaycastBulletSystem raycaster = new RaycastBulletSystem(null);

        assertFalse(raycaster.blockedByTerrain(0f, 0f, 100f, 100f, Blocking.BULLETS));
        assertNull(raycaster.resolve(0f, 0f, 100f, 100f, List.of(), 1, true));
    }
}
