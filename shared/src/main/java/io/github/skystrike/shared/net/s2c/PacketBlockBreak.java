package io.github.skystrike.shared.net.s2c;

import io.github.skystrike.shared.map.DamageSource;
import io.github.skystrike.shared.net.Packet;

/**
 * One block leaving the map, authoritatively.
 *
 * <p>Sent reliably on the tick the block is destroyed, and replayed in the join payload for a
 * client that arrives after it happened — the same record both ways, so the live path and the
 * late-join path apply identical state. The client feeds it to its mirror of the destruction
 * state, which is what removes the block from its rendering, its prediction and its next SDF
 * re-bake; it never measures damage itself.
 *
 * <p>Reliable by design, unlike the effect batches: a lost batch costs a missing flash, but a
 * lost break would leave a wall standing on one client forever. Destruction is rare — a handful
 * of blocks a round — so the reliable channel costs nothing here.
 */
public final class PacketBlockBreak implements Packet {

    /**
     * Simulation tick the break was reported on, for diagnostics: the destruction tick on the
     * live path, the join tick for history replayed in the join payload.
     */
    public long tick;

    /**
     * The block's authored index: its position in the authored block list both sides build from
     * the same canonical text. Only meaningful against the map hash the join payload states.
     */
    public int blockIndex;

    /** What destroyed it, exactly as the authority recorded it. */
    public DamageSource cause;

    /** Impact position in world units, where the debris belongs. */
    public float x;

    /** Impact position in world units, where the debris belongs. */
    public float y;

    /** True when the block shattered outright rather than spending a durability pool. */
    public boolean shattered;

    /** The destruction revision after this break — position in the round's break history. */
    public int revision;

    public PacketBlockBreak() {
    }

    public PacketBlockBreak(
        long tick, int blockIndex, DamageSource cause, float x, float y,
        boolean shattered, int revision
    ) {
        this.tick = tick;
        this.blockIndex = blockIndex;
        this.cause = cause;
        this.x = x;
        this.y = y;
        this.shattered = shattered;
        this.revision = revision;
    }

    @Override
    public String toString() {
        return "PacketBlockBreak[tick=" + tick
            + ", blockIndex=" + blockIndex
            + ", cause=" + cause
            + ", x=" + x
            + ", y=" + y
            + ", shattered=" + shattered
            + ", revision=" + revision + "]";
    }
}
