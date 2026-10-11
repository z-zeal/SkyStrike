package io.github.skystrike.shared.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BlockRulesTest {

    @Test
    void everyBlockKindHasOneSharedRuleRow() {
        for (BlockKind kind : BlockKind.values()) {
            assertNotNull(BlockRules.forKind(kind), "missing rule row for " + kind);
        }
    }

    @Test
    void ruleRowsMatchTheMapContract() {
        assertRow(
            BlockKind.WALL,
            MovementRule.SOLID,
            true,
            true,
            true,
            BlockRenderStyle.SOLID_FILL,
            DamageRule.INDESTRUCTIBLE);
        assertRow(
            BlockKind.BREAKABLE,
            MovementRule.SOLID,
            true,
            true,
            true,
            BlockRenderStyle.BREAKABLE_FILL,
            DamageRule.DURABILITY);
        assertRow(
            BlockKind.GLASS,
            MovementRule.SOLID,
            false,
            false,
            false,
            BlockRenderStyle.GLASS_PANE,
            DamageRule.SHATTER);
        assertRow(
            BlockKind.DOOR,
            MovementRule.SOLID,
            true,
            true,
            false,
            BlockRenderStyle.DOOR_PANEL,
            DamageRule.INDESTRUCTIBLE);
        assertRow(
            BlockKind.PLATFORM,
            MovementRule.ONE_WAY_TOP,
            false,
            false,
            false,
            BlockRenderStyle.PLATFORM_SLAB,
            DamageRule.INDESTRUCTIBLE);
    }

    @Test
    void onlyBreakablesCarryADurabilityPoolAndOnlyGlassShatters() {
        for (BlockKind kind : BlockKind.values()) {
            DamageRule rule = BlockRules.damage(kind);
            switch (kind) {
                case BREAKABLE -> assertEquals(DamageRule.DURABILITY, rule, "a breakable spends a pool");
                case GLASS -> assertEquals(DamageRule.SHATTER, rule, "glass has no pool to spend");
                default -> assertEquals(
                    DamageRule.INDESTRUCTIBLE, rule, kind + " is permanent until a rule says otherwise");
            }
        }
    }

    @Test
    void blastAlwaysUsesTheSightColumn() {
        for (BlockKind kind : BlockKind.values()) {
            BlockRules.Rule rule = BlockRules.forKind(kind);
            assertEquals(rule.blocksSight(), rule.blocks(Blocking.BLAST), kind + " blast rule");
        }
    }

    @Test
    void mapBlockCarriesItsIdentityAndMirrorsOnlyItsGeometry() {
        MapBlock block = new MapBlock(
            BlockKind.BREAKABLE,
            Material.WOOD,
            new Rect(120f, 200f, 40f, 18f),
            2.5f);

        MapBlock mirror = block.mirror(1500f);

        assertEquals(BlockKind.BREAKABLE, mirror.kind());
        assertEquals(Material.WOOD, mirror.material());
        assertEquals(new Rect(2840f, 200f, 40f, 18f), mirror.rect());
        assertEquals(2.5f, mirror.durabilityMultiplier(),
            "mirroring moves the geometry; the block's durability is not geometry");
        assertTrue(block.hasAuthoredDurabilityMultiplier());
        assertFalse(new MapBlock(BlockKind.WALL, Material.STONE, new Rect(120f, 200f, 40f, 18f))
            .hasAuthoredDurabilityMultiplier(), "the three-argument form is the default multiplier");
    }

    @Test
    void mapBlockRejectsMissingIdentityOrGeometry() {
        Rect rect = new Rect(0f, 0f, 1f, 1f);
        assertThrows(NullPointerException.class, () -> new MapBlock(null, Material.STONE, rect));
        assertThrows(NullPointerException.class, () -> new MapBlock(BlockKind.WALL, null, rect));
        assertThrows(NullPointerException.class, () -> new MapBlock(BlockKind.WALL, Material.STONE, null));
        assertThrows(
            IllegalArgumentException.class,
            () -> new MapBlock(BlockKind.BREAKABLE, Material.WOOD, rect, 0f));
        assertThrows(
            IllegalArgumentException.class,
            () -> new MapBlock(BlockKind.BREAKABLE, Material.WOOD, rect, -1f));
        assertThrows(
            IllegalArgumentException.class,
            () -> new MapBlock(BlockKind.BREAKABLE, Material.WOOD, rect, Float.NaN));
    }

    private static void assertRow(
            BlockKind kind,
            MovementRule movement,
            boolean sight,
            boolean bullets,
            boolean sdf,
            BlockRenderStyle renderStyle,
            DamageRule damage) {
        BlockRules.Rule rule = BlockRules.forKind(kind);
        assertEquals(movement, rule.movement(), kind + " movement rule");
        assertEquals(sight, rule.blocks(Blocking.SIGHT), kind + " sight rule");
        assertEquals(bullets, rule.blocks(Blocking.BULLETS), kind + " bullet rule");
        assertEquals(sight, rule.blocks(Blocking.BLAST), kind + " blast rule");
        assertEquals(sdf, BlockRules.bakesIntoSdf(kind), kind + " SDF rule");
        assertEquals(renderStyle, BlockRules.renderStyle(kind), kind + " rendering rule");
        assertEquals(damage, BlockRules.damage(kind), kind + " destruction rule");
    }
}
