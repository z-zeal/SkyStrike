package io.github.skystrike.shared.map;

import java.util.Objects;

/**
 * One measured hit that did <i>not</i> take a block out: the block, and the damage it has
 * accumulated once that hit landed.
 *
 * <p>This is the crack record of plan §8's per-hit sync. A {@link BlockBreak} is a block leaving
 * the map and carries the debris; a chip is a block staying, with only its picture changing — the
 * number {@link MapDamageState#damageFraction} turns into the crack overlay. The value is the
 * authority's running total rather than the hit's own damage, which is what makes the record
 * idempotent, order-tolerant (an older record can never un-chip a block) and measurement-free on
 * the receiving side: the mirror applies the number, it never adds one.
 */
public record BlockChip(MapBlock block, float damage) {

    public BlockChip {
        Objects.requireNonNull(block, "block");
        if (!(damage > 0f)) {
            // Also refuses NaN: a chip is a hit that landed, and a hit that landed is positive.
            throw new IllegalArgumentException("a chip carries positive damage: " + damage);
        }
    }
}
