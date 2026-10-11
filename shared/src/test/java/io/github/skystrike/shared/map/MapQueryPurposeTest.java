package io.github.skystrike.shared.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The rule-aware line queries: sight, bullets and blast each get the answer their own column of
 * the shared {@link BlockRules} table gives.
 *
 * <p>The shipped arena now carries a fixture range of its own, but its blocks stand next to each
 * other and a line through one of them proves less than a line through an isolated one. These
 * tests therefore author a mixed-kind map — each kind alone in open air — and ask it the questions
 * a map consumer can ask.
 */
class MapQueryPurposeTest {

    /**
     * A wall, a pane of glass, a one-way platform, an intact breakable and a closed door, each
     * isolated in open air so a line through one of them proves only that block's row.
     *
     * <p>Every block is mirrored or centred, as the builder and the format both require, so the
     * left-hand copy below is joined by its reflection on the other side of the axis.
     */
    private static final ArenaMap MIXED = parse(
        "skystrike-map 1",
        "mirror 1500",
        "spawn 0 240 180",
        "spawn 1 2760 180",
        "mirrored wall stone 100 200 20 20",
        "mirrored glass glass 400 300 20 80",
        "mirrored platform energy 700 450 40 18",
        "mirrored breakable wood 900 200 20 20",
        "centred door metal 1490 500 20 90");

    /** A probe line through the wall, which blocks every purpose. */
    private static final float WALL_Y = 210f;
    /** A probe line through the glass pane, which blocks movement only. */
    private static final float GLASS_Y = 340f;
    /** A probe line through the one-way platform, which blocks a falling body's top face only. */
    private static final float PLATFORM_Y = 459f;
    /** A probe line through the intact breakable, which is a wall until it is destroyed. */
    private static final float BREAKABLE_Y = 210f;
    /** A probe line through the closed door, which blocks every purpose. */
    private static final float DOOR_Y = 545f;

    @Test
    @DisplayName("every line query answers from the shared rule row, for every kind and purpose")
    void lineQueriesFollowTheSharedRuleTable() {
        for (BlockKind kind : BlockKind.values()) {
            // One isolated, centred block of just this kind, so the probe below can meet nothing
            // else. The material is irrelevant to blocking; only the kind's rule row is read.
            ArenaMap single = ArenaMap.builder(1500f)
                .centred(new MapBlock(kind, Material.STONE, new Rect(1490f, 400f, 20f, 90f)))
                .build();

            for (Blocking purpose : Blocking.values()) {
                boolean expected = BlockRules.blocks(kind, purpose);
                String context = kind + " / " + purpose;

                assertEquals(
                    expected,
                    MapQueries.lineBlocked(single, 1300f, 445f, 1700f, 445f, purpose),
                    context);
                assertEquals(
                    expected,
                    MapQueries.firstBlocker(single, 1300f, 445f, 1700f, 445f, purpose) != null,
                    context);
                assertEquals(
                    expected ? 1 : 0,
                    single.blockers(purpose).size(),
                    context + ": the purpose view must hold exactly the blocking blocks");
            }
        }
    }

    @Test
    @DisplayName("intact glass passes sight, bullets and blast alike")
    void glassPassesEveryLineQuery() {
        for (Blocking purpose : Blocking.values()) {
            assertFalse(
                MapQueries.lineBlocked(MIXED, 300f, GLASS_Y, 600f, GLASS_Y, purpose),
                "glass must not block " + purpose);
            assertNull(
                MapQueries.firstBlocker(MIXED, 300f, GLASS_Y, 600f, GLASS_Y, purpose),
                "glass must not be a blocker for " + purpose);
        }
        Rect pane = new Rect(400f, 300f, 20f, 80f);
        assertFalse(MIXED.blockers(Blocking.SIGHT).contains(pane));
        assertFalse(MIXED.blockers(Blocking.BULLETS).contains(pane));
        assertFalse(MIXED.blockers(Blocking.BLAST).contains(pane));
    }

    @Test
    @DisplayName("a one-way platform is in no line-query view at all")
    void platformBlocksNoLineQuery() {
        for (Blocking purpose : Blocking.values()) {
            assertFalse(
                MapQueries.lineBlocked(MIXED, 600f, PLATFORM_Y, 900f, PLATFORM_Y, purpose),
                "a platform must not block " + purpose);
            assertNull(MapQueries.firstBlocker(MIXED, 600f, PLATFORM_Y, 900f, PLATFORM_Y, purpose));
        }
        assertFalse(MIXED.blockers(Blocking.SIGHT).contains(new Rect(700f, 450f, 40f, 18f)));
        assertFalse(MIXED.blockers(Blocking.BULLETS).contains(new Rect(700f, 450f, 40f, 18f)));
        assertFalse(MIXED.blockers(Blocking.BLAST).contains(new Rect(700f, 450f, 40f, 18f)));
    }

    @Test
    @DisplayName("walls, intact breakables and a closed door block every purpose")
    void solidKindsBlockEveryPurpose() {
        for (Blocking purpose : Blocking.values()) {
            assertTrue(
                MapQueries.lineBlocked(MIXED, 0f, WALL_Y, 300f, WALL_Y, purpose),
                "the wall must block " + purpose);
            assertTrue(
                MapQueries.lineBlocked(MIXED, 800f, BREAKABLE_Y, 1100f, BREAKABLE_Y, purpose),
                "an intact breakable must block " + purpose);
            assertTrue(
                MapQueries.lineBlocked(MIXED, 1400f, DOOR_Y, 1600f, DOOR_Y, purpose),
                "a closed door must block " + purpose);

            assertNotNull(MapQueries.firstBlocker(MIXED, 1400f, DOOR_Y, 1600f, DOOR_Y, purpose));
        }
    }

    @Test
    @DisplayName("the five-argument line query is exactly the sight purpose")
    void theFiveArgumentFormIsTheSightConvenience() {
        assertTrue(MapQueries.lineBlocked(MIXED, 1400f, DOOR_Y, 1600f, DOOR_Y));
        assertTrue(MapQueries.lineBlocked(MIXED, 1400f, DOOR_Y, 1600f, DOOR_Y, Blocking.SIGHT));
        assertFalse(MapQueries.lineBlocked(MIXED, 300f, GLASS_Y, 600f, GLASS_Y));
        assertFalse(MapQueries.lineBlocked(MIXED, 300f, GLASS_Y, 600f, GLASS_Y, Blocking.SIGHT));

        assertEquals(
            MapQueries.firstBlocker(MIXED, 1400f, DOOR_Y, 1600f, DOOR_Y, Blocking.SIGHT),
            MapQueries.firstBlocker(MIXED, 1400f, DOOR_Y, 1600f, DOOR_Y));
        assertNull(MapQueries.firstBlocker(MIXED, 300f, GLASS_Y, 600f, GLASS_Y));
    }

    @Test
    @DisplayName("blast occlusion is the sight column, so a window never stops an explosion")
    void blastOcclusionFollowsSight() {
        for (BlockKind kind : BlockKind.values()) {
            assertEquals(
                BlockRules.blocks(kind, Blocking.SIGHT),
                BlockRules.blocks(kind, Blocking.BLAST),
                kind + " must treat blast as sight");
        }
        assertEquals(MIXED.blockers(Blocking.SIGHT), MIXED.blockers(Blocking.BLAST));
    }

    @Test
    @DisplayName("movement questions read the movement column, which no line query can answer")
    void movementQuestionsReadTheMovementColumn() {
        // Intact glass and a closed door stop a body though no line query notices the glass, and a
        // one-way platform never does: it is a landing from above, which a box overlap cannot say.
        assertTrue(
            MapQueries.overlapsMovementBlocker(MIXED, new Rect(390f, 330f, 40f, 40f)),
            "intact glass stops a body");
        assertTrue(
            MapQueries.overlapsMovementBlocker(MIXED, new Rect(1480f, 530f, 40f, 40f)),
            "a closed door stops a body");
        assertFalse(
            MapQueries.overlapsMovementBlocker(MIXED, new Rect(690f, 459f, 60f, 40f)),
            "a one-way platform is not a body blocker");
        assertFalse(
            MapQueries.overlapsMovementBlocker(MIXED, new Rect(200f, 900f, 40f, 40f)),
            "open air stops nothing");

        // The views hold exactly the rows the table gives them: wall, glass, breakable and door are
        // SOLID movement (7 rects), the platform is the only ONE_WAY_TOP (2 rects).
        assertEquals(7, MIXED.movementBlockers(MovementRule.SOLID).size());
        assertEquals(2, MIXED.movementBlockers(MovementRule.ONE_WAY_TOP).size());
        assertTrue(MIXED.movementBlockers(MovementRule.ONE_WAY_TOP)
            .contains(new Rect(700f, 450f, 40f, 18f)));
        assertThrows(
            UnsupportedOperationException.class,
            () -> MIXED.movementBlockers(MovementRule.SOLID).add(new Rect(0f, 0f, 1f, 1f)));
        assertThrows(NullPointerException.class, () -> MIXED.movementBlockers(null));
    }

    @Test
    @DisplayName("every movement rule presents a standable top, so surfaceBelow is not a bullet sweep")
    void surfaceBelowAnswersFromEveryMovementRule() {
        for (BlockKind kind : BlockKind.values()) {
            // One isolated centred block of just this kind, as in lineQueriesFollowTheSharedRuleTable.
            ArenaMap single = ArenaMap.builder(1500f)
                .centred(new MapBlock(kind, Material.STONE, new Rect(1490f, 400f, 20f, 90f)))
                .build();

            boolean stopsBody = BlockRules.movement(kind) == MovementRule.SOLID;
            assertEquals(
                stopsBody,
                MapQueries.overlapsMovementBlocker(single, new Rect(1480f, 430f, 40f, 40f)),
                kind + ": only a SOLID movement row stops a body");
            assertEquals(
                490f,
                MapQueries.surfaceBelow(single, 1500f, 600f),
                0f,
                kind + ": its top face supports a body");
        }

        // A platform's top supports a body standing on it, and a probe below the slab does not
        // reach up through it.
        assertEquals(468f, MapQueries.surfaceBelow(MIXED, 700f, 600f), 0f);
        assertEquals(0f, MapQueries.surfaceBelow(MIXED, 700f, 300f), 0f);
    }

    @Test
    @DisplayName("the per-purpose views are immutable and the bullet view is solids()")
    void purposeViewsAreTheRuleTablesOwnColumns() {
        List<Rect> sight = MIXED.blockers(Blocking.SIGHT);
        List<Rect> bullets = MIXED.blockers(Blocking.BULLETS);
        List<Rect> blast = MIXED.blockers(Blocking.BLAST);

        // The map above authors a mirrored wall, glass, platform and breakable (two rects each)
        // and one centred door. Wall, breakable and door block sight and bullets (5 rects, the
        // same column); glass and platform block neither sight, bullets nor blast.
        assertEquals(5, sight.size());
        assertEquals(5, bullets.size());
        assertEquals(5, blast.size());
        assertTrue(sight.contains(new Rect(100f, 200f, 20f, 20f)));
        assertTrue(sight.contains(new Rect(900f, 200f, 20f, 20f)));
        assertTrue(sight.contains(new Rect(1490f, 500f, 20f, 90f)));
        assertFalse(sight.contains(new Rect(400f, 300f, 20f, 80f)));
        assertFalse(sight.contains(new Rect(700f, 450f, 40f, 18f)));

        assertEquals(
            9, MIXED.blocks().size(),
            "two rects each for wall, glass, platform and breakable, plus the centred door");
        assertEquals(bullets, MIXED.solids(), "solids() must stay the bullet-blocking view");
        assertThrows(UnsupportedOperationException.class, () -> sight.add(new Rect(0f, 0f, 1f, 1f)));
        assertThrows(NullPointerException.class, () -> MIXED.blockers(null));
    }

    private static ArenaMap parse(String... lines) {
        return ArenaMapFormat.parse(String.join("\n", lines) + "\n");
    }
}
