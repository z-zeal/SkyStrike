package io.github.skystrike.shared.map;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * The single shared behaviour table for every {@link BlockKind}.
 *
 * <p>Every consumer asks this table for its column instead of comparing a kind by name. That is
 * what keeps movement, line of sight, bullets, the static SDF and rendering from quietly
 * disagreeing when another kind is added.
 *
 * <p>Rows describe intact blocks and a closed door. Later dynamic state will decide whether that
 * row is active; it must not duplicate the row's per-consumer semantics.
 */
public final class BlockRules {

    private static final Map<BlockKind, Rule> RULES = buildRules();

    private BlockRules() {
    }

    /** Returns the immutable rule row for {@code kind}. Never null. */
    public static Rule forKind(BlockKind kind) {
        Objects.requireNonNull(kind, "kind");
        Rule rule = RULES.get(kind);
        if (rule == null) {
            throw new IllegalArgumentException("no block rule for kind: " + kind);
        }
        return rule;
    }

    /** Convenience for a query consumer that only needs one blocking column. */
    public static boolean blocks(BlockKind kind, Blocking purpose) {
        return forKind(kind).blocks(purpose);
    }

    /** Convenience for SDF bakers, which have their own non-line-query column. */
    public static boolean bakesIntoSdf(BlockKind kind) {
        return forKind(kind).bakesIntoSdf();
    }

    /** Convenience for renderers, which choose their treatment from the shared row. */
    public static BlockRenderStyle renderStyle(BlockKind kind) {
        return forKind(kind).renderStyle();
    }

    private static Map<BlockKind, Rule> buildRules() {
        Map<BlockKind, Rule> rules = new EnumMap<>(BlockKind.class);

        // kind       movement      sight  bullets SDF    rendering
        put(rules, BlockKind.WALL, MovementRule.SOLID, true, true, true, BlockRenderStyle.SOLID_FILL);
        put(rules, BlockKind.BREAKABLE, MovementRule.SOLID, true, true, true,
            BlockRenderStyle.BREAKABLE_FILL);
        put(rules, BlockKind.GLASS, MovementRule.SOLID, false, false, false,
            BlockRenderStyle.GLASS_PANE);
        put(rules, BlockKind.DOOR, MovementRule.SOLID, true, true, false,
            BlockRenderStyle.DOOR_PANEL);
        put(rules, BlockKind.PLATFORM, MovementRule.ONE_WAY_TOP, false, false, false,
            BlockRenderStyle.PLATFORM_SLAB);

        if (rules.size() != BlockKind.values().length) {
            throw new IllegalStateException("every BlockKind must have exactly one rule row");
        }
        return Collections.unmodifiableMap(rules);
    }

    private static void put(
            Map<BlockKind, Rule> rules,
            BlockKind kind,
            MovementRule movement,
            boolean blocksSight,
            boolean blocksBullets,
            boolean bakesIntoSdf,
            BlockRenderStyle renderStyle) {
        Rule previous = rules.put(kind, new Rule(
            movement, blocksSight, blocksBullets, bakesIntoSdf, renderStyle));
        if (previous != null) {
            throw new IllegalStateException("duplicate block rule for kind: " + kind);
        }
    }

    /** One immutable row of the table. */
    public record Rule(
            MovementRule movement,
            boolean blocksSight,
            boolean blocksBullets,
            boolean bakesIntoSdf,
            BlockRenderStyle renderStyle) {

        public Rule {
            Objects.requireNonNull(movement, "movement");
            Objects.requireNonNull(renderStyle, "renderStyle");
        }

        /**
         * Answers a shared blocking query. Blast occlusion is deliberately the same as sight
         * occlusion, so a window never blocks an explosion that can be seen through.
         */
        public boolean blocks(Blocking purpose) {
            Objects.requireNonNull(purpose, "purpose");
            return switch (purpose) {
                case SIGHT, BLAST -> blocksSight;
                case BULLETS -> blocksBullets;
            };
        }
    }
}
