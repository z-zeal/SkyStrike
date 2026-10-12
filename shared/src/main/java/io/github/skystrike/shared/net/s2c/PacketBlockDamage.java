package io.github.skystrike.shared.net.s2c;

import io.github.skystrike.shared.net.Packet;

/**
 * One block chipped but not destroyed (protocol 17): the damage the client's picture is missing.
 *
 * <p>Breaks travel as {@link PacketBlockBreak}; a chip changes nothing about the map — no rule
 * view, no live map, no re-bake — so it rides its own record rather than a second field on the
 * break, and the receiver only moves a crack overlay. {@code damage} is the authority's
 * accumulated damage for the block after the hit, not the hit's own size: the record is then
 * idempotent and order-tolerant, and the fraction the overlay draws is derived from it through the
 * shared material table, which is why neither a fraction nor a position travels.
 *
 * <p>Reliable, like the block event and for the same reason: a lost chip would leave one client's
 * picture behind the other's for the rest of the round, and hits are far rarer than snapshots.
 */
public final class PacketBlockDamage implements Packet {

    /** Server tick the hit landed on. */
    public long tick;

    /** The block's authored index — the same wire identity a break carries. */
    public int blockIndex;

    /** Damage accumulated against the block's pool, after this hit. */
    public float damage;

    public PacketBlockDamage() {
    }

    public PacketBlockDamage(long tick, int blockIndex, float damage) {
        this.tick = tick;
        this.blockIndex = blockIndex;
        this.damage = damage;
    }

    @Override
    public String toString() {
        return "PacketBlockDamage[tick=" + tick + ", blockIndex=" + blockIndex
            + ", damage=" + damage + "]";
    }
}
