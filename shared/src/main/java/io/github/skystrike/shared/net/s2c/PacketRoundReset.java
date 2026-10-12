package io.github.skystrike.shared.net.s2c;

import io.github.skystrike.shared.net.Packet;

/**
 * The round's destruction is gone: the authored map is back and every door is shut (protocol 18).
 *
 * <p>Plan §11.2 decided destruction resets per round — a map chewed through in one round is not the
 * map both teams agreed to fight on — and this is the event that makes the decision visible to
 * everyone but the authority. Without it a client keeps playing the chewed map: its live map, its
 * minimap, its torn-down doors and its SDF all stay as the last round left them, while the server
 * has already put the walls back.
 *
 * <p>It carries no map identity and no per-block detail on purpose. The restorable state is derived
 * from the authored map plus the current door states, both of which every client already holds, so
 * the honest instruction is "reset", not a replay — and the two of them cannot disagree, because
 * {@code MapDamageState.reset()} is the same call on both sides. {@code restoredBlocks} is the
 * authority's count, for the log line the receiving side writes and for nothing else.
 *
 * <p>Reliable: a lost reset would leave one client walking through walls that are back (or into
 * walls that are gone) for the rest of the match, which is precisely the state this record ends.
 */
public final class PacketRoundReset implements Packet {

    /** Server tick the reset happened on. */
    public long tick;

    /** How many blocks the authority restored, for the log line. Zero on an untouched map. */
    public int restoredBlocks;

    public PacketRoundReset() {
    }

    public PacketRoundReset(long tick, int restoredBlocks) {
        this.tick = tick;
        this.restoredBlocks = restoredBlocks;
    }

    @Override
    public String toString() {
        return "PacketRoundReset[tick=" + tick + ", restoredBlocks=" + restoredBlocks + "]";
    }
}
