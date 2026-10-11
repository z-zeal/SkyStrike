package io.github.skystrike.shared.map;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The authoritative per-round destruction state of one map.
 *
 * <p>An {@link ArenaMap} is authored geometry and never changes. This is the other half of the
 * picture: what has been chipped and what has been removed since the round started. The server owns
 * the authority — the shared damage paths (bullets, blasts, melee) mutate it through {@link #damage}
 * — and the client holds a mirror fed by the authoritative block events through
 * {@link #applyRemoteBreak}, never by measuring damage itself. Every consumer that cares whether a
 * block is still standing reads {@link #liveMap()}.
 *
 * <p><b>The live map is derived, never authored.</b> {@link #liveMap()} is an ordinary immutable
 * {@link ArenaMap} built from the surviving blocks, so the rule views a query already reads are the
 * post-destruction ones: a destroyed breakable leaves the movement, sight, bullet and blast sets in
 * the same call that removed it, which is what the plan's "in the same tick" asks for. The views are
 * rebuilt per destruction — microseconds, against an event that happens a handful of times a round.
 *
 * <p><b>Revision, not identity.</b> {@link #revision()} counts the blocks destroyed since the round
 * started. The authored map's {@link MapHash} stays the identity both sides compare, and the
 * revision says how much of that map is still there; {@link #liveMap()}'s own hash describes the
 * surviving contents, which is what a re-baked SDF names, and differs from the authored hash the
 * moment something is destroyed.
 *
 * <p><b>Permanence costs nothing.</b> A block whose rule row is {@link DamageRule#INDESTRUCTIBLE} is
 * never recorded, never chipped and never removed: {@link #damage} refuses it before it measures
 * anything, so the walls, doors and platforms a map is mostly made of cost nothing — no damage
 * taken, {@code liveMap()} is the authored map itself and the revision stays zero until something
 * the map did author as destructible is hit.
 *
 * <p><b>Damage is per round.</b> {@link #reset()} puts the authored map back and returns the
 * revision to zero, because a map chewed through in one round is not the map both teams agreed to
 * fight on. Nothing is ever repaired mid-round.
 *
 * <p><b>Threading.</b> Mutation belongs to the authority's tick thread. Publishing the state to
 * another thread (the client's SDF re-bake worker) through a thread-safe hand-off is enough, because
 * everything it can read — the maps, the block list, the hashes — is immutable.
 */
public final class MapDamageState {

    private final ArenaMap authoredMap;
    private final Map<MapBlock, Integer> authoredIndices;
    private final float[] damage;
    private final boolean[] destroyed;
    private final List<MapBlock> destroyedBlocks = new ArrayList<>();
    /** Every break in destruction order: the server's broadcast queue and the wire history. */
    private final List<BlockBreak> breaks = new ArrayList<>();
    private int revision;
    private ArenaMap liveMap;

    private MapDamageState(ArenaMap authoredMap) {
        this.authoredMap = authoredMap;
        List<MapBlock> blocks = authoredMap.blocks();
        this.authoredIndices = new HashMap<>(blocks.size() * 2);
        for (int index = 0; index < blocks.size(); index++) {
            authoredIndices.put(blocks.get(index), index);
        }
        this.damage = new float[blocks.size()];
        this.destroyed = new boolean[blocks.size()];
        this.liveMap = authoredMap;
    }

    /** An untouched state for {@code authoredMap}: nothing chipped, nothing removed. */
    public static MapDamageState intact(ArenaMap authoredMap) {
        return new MapDamageState(Objects.requireNonNull(authoredMap, "authoredMap"));
    }

    /** The authored map, exactly as it was loaded. Its hash is the identity of the map in play. */
    public ArenaMap authoredMap() {
        return authoredMap;
    }

    /**
     * The map as it stands: authored geometry minus everything destroyed. Never null, and never the
     * authored instance once something has been removed.
     */
    public ArenaMap liveMap() {
        return liveMap;
    }

    /** How many blocks have been destroyed since the round started. */
    public int revision() {
        return revision;
    }

    /** How many blocks the authored map holds — the size of this state's per-block arrays. */
    public int blockCount() {
        return damage.length;
    }

    /** Blocks destroyed since the round started, in the order they went. Immutable copy. */
    public List<MapBlock> destroyedBlocks() {
        return List.copyOf(destroyedBlocks);
    }

    /**
     * Every break since the round started, in destruction order. Immutable copy.
     *
     * <p>This is the wire history: the authority broadcasts the tail the clients have not seen yet,
     * and the join payload replays the whole list, so a late joiner lands on the same set without
     * replaying the match. Position {@code i} in this list is the break that moved the revision
     * from {@code i} to {@code i + 1}.
     */
    public List<BlockBreak> breaks() {
        return List.copyOf(breaks);
    }

    /**
     * The authored index of {@code block}: its position in {@link #authoredMap()}'s block list.
     *
     * <p>This index is the block's wire identity. Both sides build the identical authored list from
     * the same canonical text, so an index names a block unambiguously without carrying the whole
     * triple — but only against the map hash the join payload states. A client whose map differs
     * must not apply indices at all.
     */
    public int authoredIndexOf(MapBlock block) {
        return indexOf(block);
    }

    /** True when {@code block} has been destroyed this round. */
    public boolean isDestroyed(MapBlock block) {
        return destroyed[indexOf(block)];
    }

    /** Damage {@code block} has accumulated this round. A shattered block never accumulates any. */
    public float damage(MapBlock block) {
        return damage[indexOf(block)];
    }

    /**
     * What is left of {@code block}'s durability pool, or {@link Float#POSITIVE_INFINITY} for a
     * permanent block and {@code 0} for one that is already gone.
     */
    public float remainingDurability(MapBlock block) {
        int index = indexOf(block);
        if (BlockRules.damage(block.kind()) == DamageRule.INDESTRUCTIBLE) {
            return Float.POSITIVE_INFINITY;
        }
        if (destroyed[index]) {
            return 0f;
        }
        return Math.max(0f, MaterialTable.durabilityPool(block) - damage[index]);
    }

    /**
     * How much of {@code block}'s pool has been spent, in {@code [0, 1]} — the number a crack
     * overlay draws. Zero for a permanent block and 1 for a destroyed one.
     */
    public float damageFraction(MapBlock block) {
        int index = indexOf(block);
        if (BlockRules.damage(block.kind()) == DamageRule.INDESTRUCTIBLE) {
            return 0f;
        }
        if (destroyed[index]) {
            return 1f;
        }
        float pool = MaterialTable.durabilityPool(block);
        return Math.min(1f, Math.max(0f, damage[index] / pool));
    }

    /**
     * Applies one measured hit to {@code block}.
     *
     * <p>The caller owns the measurement — a round's damage after falloff, an explosion's falloff at
     * the block's nearest point, a swing's damage — and this method owns the decision: the material's
     * retention decides whether any of it lands, the kind's {@link DamageRule} decides whether the
     * block spends a pool or shatters outright, and the per-block multiplier decides how big that
     * pool is. A {@link DamageSource} this surface refuses, an already-destroyed block and a
     * permanent block all return {@code null}: there is nothing to report and nothing to draw.
     *
     * @param x impact position, in world units, for the debris
     * @param y impact position, in world units, for the debris
     * @return the break when this hit took the block out of the map, otherwise {@code null}
     */
    public BlockBreak damage(MapBlock block, DamageSource source, float rawDamage, float x, float y) {
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(source, "source");
        DamageRule rule = BlockRules.damage(block.kind());
        if (rule == DamageRule.INDESTRUCTIBLE) {
            return null;
        }
        int index = indexOf(block);
        if (destroyed[index]) {
            return null;
        }
        float accepted = MaterialTable.acceptedDamage(block, source, rawDamage);
        if (accepted <= 0f) {
            return null;
        }
        if (rule == DamageRule.SHATTER) {
            return destroy(index, block, source, x, y, true);
        }
        damage[index] += accepted;
        if (damage[index] >= MaterialTable.durabilityPool(block)) {
            return destroy(index, block, source, x, y, false);
        }
        return null;
    }

    /**
     * Applies one authoritative break received over the wire.
     *
     * <p>The mirror's only mutation besides {@link #reset()}. Nothing is measured here — the
     * authority already decided the cause, the position and whether it shattered, and the client
     * never re-derives any of it (plan D11). The recorded {@link BlockBreak} is the packet's own
     * record, so both sides' {@link #breaks()} lists agree and their revisions stay in lock-step.
     *
     * <p>Defensive by necessity: wire data can be stale, duplicated or indexed against a map this
     * side is not playing. An out-of-range index and an already-destroyed block both return
     * {@code null} instead of throwing, and a caller that failed the join-time map-hash check must
     * not call this at all.
     *
     * @param authoredIndex the block's position in the authored list, per {@link #authoredIndexOf}
     * @param cause the authoritative cause, exactly as the packet carried it
     * @param x impact position, in world units, for the debris
     * @param y impact position, in world units, for the debris
     * @param shattered whether the block shattered outright rather than spending its pool
     * @return the recorded break, or {@code null} when there was nothing new to apply
     */
    public BlockBreak applyRemoteBreak(
            int authoredIndex, DamageSource cause, float x, float y, boolean shattered) {
        Objects.requireNonNull(cause, "cause");
        if (authoredIndex < 0 || authoredIndex >= destroyed.length) {
            return null;
        }
        if (destroyed[authoredIndex]) {
            return null;
        }
        return destroy(authoredIndex, authoredMap.blocks().get(authoredIndex),
            cause, x, y, shattered);
    }

    /**
     * Puts every block back for the next round: damage cleared, destroyed blocks restored, the
     * revision back to zero and the live map the authored one again.
     *
     * @return the blocks that had been destroyed, in the order they went
     */
    public List<MapBlock> reset() {
        List<MapBlock> restored = List.copyOf(destroyedBlocks);
        destroyedBlocks.clear();
        Arrays.fill(damage, 0f);
        Arrays.fill(destroyed, false);
        revision = 0;
        liveMap = authoredMap;
        return restored;
    }

    private BlockBreak destroy(
            int index, MapBlock block, DamageSource source, float x, float y, boolean shattered) {
        destroyed[index] = true;
        destroyedBlocks.add(block);
        BlockBreak broke = new BlockBreak(block, source, x, y, shattered);
        breaks.add(broke);
        revision++;
        liveMap = authoredMap.without(destroyedBlocks);
        return broke;
    }

    private int indexOf(MapBlock block) {
        Integer index = authoredIndices.get(Objects.requireNonNull(block, "block"));
        if (index == null) {
            throw new IllegalArgumentException("block does not belong to this map: " + block);
        }
        return index;
    }
}
