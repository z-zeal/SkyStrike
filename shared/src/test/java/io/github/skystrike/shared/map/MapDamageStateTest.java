package io.github.skystrike.shared.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.WorldConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The destruction rules of plan §7: damage accumulates only from the sources the material accepts,
 * and at zero the block leaves the collision, sight, bullet and blast sets in the same call.
 *
 * <p>Most maps here are authored rather than taken from the shipped arena, because a property like
 * "a chip stops a round" needs one isolated breakable; the shipped arena's own fixture range is used
 * where the point is that the shipped map really does carry one — the crate chips and breaks, the
 * pane shatters, and every permanent block refuses damage outright.
 */
class MapDamageStateTest {

    private static final float AXIS = WorldConfig.MIRROR_AXIS_X;
    private static final Rect CRATE_RECT = new Rect(700f, 450f, 40f, 20f);
    private static final Rect PANE_RECT = new Rect(1490f, 300f, 20f, 80f);
    private static final Rect WALL_RECT = new Rect(100f, 200f, 20f, 20f);

    private static final MapBlock CRATE = new MapBlock(BlockKind.BREAKABLE, Material.WOOD, CRATE_RECT);
    private static final MapBlock PANE = new MapBlock(BlockKind.GLASS, Material.GLASS, PANE_RECT);
    private static final MapBlock WALL = new MapBlock(BlockKind.WALL, Material.STONE, WALL_RECT);

    @Test
    @DisplayName("damage lands only through the material's own sources")
    void damageFollowsTheMaterialTable() {
        MapDamageState state = intactState();
        MapBlock stoneCrate = new MapBlock(BlockKind.BREAKABLE, Material.STONE, CRATE_RECT);
        MapDamageState stoneState =
            MapDamageState.intact(ArenaMap.builder(AXIS).centred(stoneCrate).build());

        assertNull(state.damage(CRATE, DamageSource.BULLET, 0f, 700f, 450f), "no damage is no hit");
        assertNull(state.damage(CRATE, DamageSource.BULLET, -10f, 700f, 450f));

        assertNull(
            stoneState.damage(stoneCrate, DamageSource.BULLET, 200f, 700f, 450f),
            "stone takes no bullet damage, however hard the round");
        assertNull(stoneState.damage(stoneCrate, DamageSource.MELEE, 200f, 700f, 450f));
        assertNull(
            stoneState.damage(stoneCrate, DamageSource.BLAST, 200f, 700f, 450f),
            "a non-lethal blast only chips it");
        assertEquals(70f, stoneState.damage(stoneCrate), 1e-4f, "0.35 of the blast landed");
        assertNotNull(
            stoneState.damage(stoneCrate, DamageSource.BLAST, 1_000f, 700f, 450f),
            "an explosion is the one source stone accepts, and it can finish the job");

        // A permanent block records nothing at all, whatever hits it. Authored order is the crate
        // and its reflection, then the pane, then the wall and its reflection.
        MapBlock wall = state.authoredMap().blocks().get(3);
        assertNull(state.damage(wall, DamageSource.BLAST, 400f, 100f, 200f));
        assertEquals(0, state.revision());
        assertSame(state.authoredMap(), state.liveMap(), "nothing happened, so nothing was rebuilt");
    }

    @Test
    @DisplayName("a breakable accumulates damage and leaves the map at zero")
    void breakableLeavesAtZero() {
        MapDamageState state = MapDamageState.intact(ArenaMap.builder(AXIS)
            .mirrored(new MapBlock(BlockKind.BREAKABLE, Material.WOOD, CRATE_RECT))
            .build());
        MapBlock left = state.authoredMap().blocks().get(0);

        assertNull(state.damage(left, DamageSource.BULLET, 50f, 700f, 450f));
        assertEquals(50f, state.damage(left), 1e-4f);
        assertEquals(70f, state.remainingDurability(left), 1e-4f);
        assertEquals(0, state.revision());

        BlockBreak broke = state.damage(left, DamageSource.BULLET, 70f, 700f, 450f);

        assertNotNull(broke, "the hit that spends the pool destroys the block");
        assertEquals(left, broke.block());
        assertEquals(DamageSource.BULLET, broke.cause());
        assertEquals(700f, broke.x());
        assertEquals(450f, broke.y());
        assertFalse(broke.shattered(), "a crate is chipped down, not shattered");
        assertEquals(1, state.revision());
        assertEquals(0f, state.remainingDurability(left), 1e-4f);
        assertEquals(1f, state.damageFraction(left), 1e-4f);
        assertTrue(state.isDestroyed(left));
        assertEquals(List.of(left), state.destroyedBlocks());

        // It is gone from the live map, and from every rule view a query reads, in this call.
        ArenaMap live = state.liveMap();
        assertFalse(live.blocks().contains(left));
        for (Blocking purpose : Blocking.values()) {
            assertFalse(live.blockers(purpose).contains(CRATE_RECT), purpose.toString());
            assertFalse(
                MapQueries.lineBlocked(live, 600f, 460f, 800f, 460f, purpose), purpose.toString());
        }
        assertFalse(live.movementBlockers(MovementRule.SOLID).contains(CRATE_RECT));
        assertFalse(MapQueries.overlapsMovementBlocker(live, CRATE_RECT));
        assertEquals(
            CRATE_RECT,
            MapQueries.firstBlocker(state.authoredMap(), 600f, 460f, 800f, 460f, Blocking.BULLETS),
            "the authored map the other side agreed to is untouched");

        // The other side of the mirror is still standing: destruction is per block, not per pair.
        assertTrue(live.blocks().contains(left.mirror(AXIS)));
        assertNull(state.damage(left, DamageSource.BULLET, 500f, 700f, 450f), "already gone");
        assertEquals(1, state.revision());
    }

    @Test
    @DisplayName("glass shatters on any hit and never keeps a partial state")
    void glassShattersOnceAndCompletely() {
        MapDamageState state = MapDamageState.intact(
            ArenaMap.builder(AXIS).centred(PANE).build());
        MapBlock pane = state.authoredMap().blocks().get(0);

        BlockBreak broke = state.damage(pane, DamageSource.BULLET, 1f, 1500f, 340f);

        assertNotNull(broke, "any hit shatters a pane");
        assertTrue(broke.shattered());
        assertEquals(0f, state.damage(pane), "no durability pool is spent, so none is recorded");
        assertEquals(1f, state.damageFraction(pane));
        assertEquals(0f, state.remainingDurability(pane));
        assertNull(state.damage(pane, DamageSource.BLAST, 500f, 1500f, 340f));
        assertEquals(1, state.revision());
        assertTrue(state.liveMap().blocks().isEmpty());

        // A pane is in no line view to begin with; what leaves the world is the movement block.
        assertTrue(MapQueries.overlapsMovementBlocker(state.authoredMap(), PANE_RECT));
        assertFalse(MapQueries.overlapsMovementBlocker(state.liveMap(), PANE_RECT));
    }

    @Test
    @DisplayName("the live map is a derived map whose hash names what is left")
    void liveMapIsDerivedAndDeterministic() {
        MapDamageState state = intactState();
        ArenaMap authored = state.authoredMap();

        state.damage(CRATE, DamageSource.BULLET, 500f, 700f, 450f);
        ArenaMap live = state.liveMap();

        assertFalse(
            Arrays.equals(authored.mapHash(), live.mapHash()),
            "the surviving contents are a different map from the authored one");
        assertTrue(
            Arrays.equals(authored.without(List.of(CRATE)).mapHash(), live.mapHash()),
            "the derived map is built the same way every time, so a re-bake is reproducible");
        assertEquals(authored.spawns(), live.spawns());
        assertEquals(authored.mirrorAxisX(), live.mirrorAxisX());
    }

    @Test
    @DisplayName("a reset restores the authored map, the revision and every view")
    void resetRestoresTheRound() {
        MapDamageState state = intactState();
        state.damage(CRATE, DamageSource.BLAST, 500f, 700f, 450f);
        state.damage(PANE, DamageSource.BULLET, 40f, 1500f, 340f);
        assertEquals(2, state.revision());

        List<MapBlock> restored = state.reset();

        assertEquals(2, restored.size());
        assertEquals(0, state.revision());
        assertEquals(0f, state.damage(CRATE));
        assertSame(state.authoredMap(), state.liveMap(), "the authored instance comes back");
        assertTrue(state.liveMap().blocks().contains(CRATE));
        assertTrue(state.liveMap().blocks().contains(PANE));
        assertTrue(MapQueries.overlapsMovementBlocker(state.liveMap(), PANE_RECT));
        assertTrue(state.destroyedBlocks().isEmpty());
    }

    @Test
    @DisplayName("the shipped arena's permanent blocks cost nothing and its fixtures are live")
    void theShippedArenaCostsNothingButItsFixtures() {
        MapDamageState state = MapDamageState.intact(ArenaMap.standard());
        List<MapBlock> destructible = new ArrayList<>();

        for (MapBlock block : state.authoredMap().blocks()) {
            // A wall, a door and a one-way platform are permanent by their own rule row: this state
            // has nothing to record for them however hard they are hit.
            if (BlockRules.damage(block.kind()) == DamageRule.INDESTRUCTIBLE) {
                assertNull(state.damage(block, DamageSource.BLAST, 1_000f,
                    block.rect().centerX(), block.rect().centerY()), block.toString());
                continue;
            }
            destructible.add(block);
        }
        assertEquals(0, state.revision(), "nothing the arena authored as permanent can be hurt");
        assertSame(state.authoredMap(), state.liveMap());
        assertEquals(4, destructible.size(),
            "the arena authors one crate and one pane per side: " + destructible);

        // And they are real. The shipped map is no longer wall-only, so the milestone can be
        // exercised in a running game instead of only in authored test rooms: the crate spends its
        // pool and leaves the live map, its pane shatters on any accepted hit.
        MapBlock crate = destructible.stream()
            .filter(block -> block.kind() == BlockKind.BREAKABLE)
            .findFirst()
            .orElseThrow();
        MapBlock pane = destructible.stream()
            .filter(block -> block.kind() == BlockKind.GLASS)
            .findFirst()
            .orElseThrow();

        assertTrue(MaterialTable.takesDamage(crate, DamageSource.BULLET));
        assertNotNull(state.damage(crate, DamageSource.BLAST, 1_000f,
            crate.rect().centerX(), crate.rect().centerY()));
        assertNotNull(state.damage(pane, DamageSource.BULLET, 1f,
            pane.rect().centerX(), pane.rect().centerY()));
        assertEquals(2, state.revision());
        assertFalse(state.liveMap().blocks().contains(crate));
        assertFalse(state.liveMap().blocks().contains(pane));
    }

    @Test
    @DisplayName("a block from another map is refused loudly")
    void foreignBlocksAreRejected() {
        MapDamageState state = intactState();
        MapBlock foreign =
            new MapBlock(BlockKind.BREAKABLE, Material.WOOD, new Rect(1f, 1f, 10f, 10f));

        assertThrows(
            IllegalArgumentException.class,
            () -> state.damage(foreign, DamageSource.BULLET, 10f, 1f, 1f));
        assertThrows(IllegalArgumentException.class, () -> state.isDestroyed(foreign));
        assertThrows(IllegalArgumentException.class, () -> state.remainingDurability(foreign));
    }

    private static MapDamageState intactState() {
        return MapDamageState.intact(ArenaMap.builder(AXIS)
            .mirrored(CRATE)
            .centred(PANE)
            .mirrored(WALL)
            .build());
    }
}
