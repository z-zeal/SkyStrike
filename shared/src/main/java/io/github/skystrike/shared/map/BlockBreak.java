package io.github.skystrike.shared.map;

import io.github.skystrike.shared.effect.EffectType;
import java.util.Objects;

/**
 * One block leaving the map.
 *
 * <p>Raised by {@link MapDamageState} when a hit takes a block out, and it is the whole
 * description of the event: <b>which</b> block (so collision, sight, bullets, the picture and the
 * re-bake all agree on the rectangle that is gone), <b>where</b> the hit landed (where the debris
 * and the shards belong), <b>what</b> caused it, and whether it shattered outright or spent the
 * last of its durability.
 *
 * <p>The effect itself is not named here. A caller asks {@link MaterialTable} for the material's
 * impact effect and scales it, so a new surface does not have to be added to an event type as well
 * as to a table row.
 */
public record BlockBreak(MapBlock block, DamageSource cause, float x, float y, boolean shattered) {

    public BlockBreak {
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(cause, "cause");
    }

    /**
     * What a break should look and sound like: the block's material impact.
     *
     * <p>Derived rather than stored, so the debris of a break and the mark of a chip on the same
     * surface cannot disagree, and the recording follows from the type through the existing sound
     * catalogue. A pane's row names {@code GLASS_SHATTER}, which is exactly the shard burst the
     * append added for it.
     */
    public EffectType effect() {
        return MaterialTable.profileFor(block.material()).impactEffect();
    }
}
