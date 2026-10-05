package io.github.skystrike.shared.config;

/**
 * Transport tuning shared by both sides of the connection.
 *
 * <p>Every value here is part of the client/server contract: a divergence between the two sides is
 * a silent protocol bug, so neither side is allowed to hard-code its own copy.
 */
public final class NetConfig {

    /**
     * Bumped whenever the packet set or its registration order changes.
     *
     * <p>3 — Phase 3 combat: {@code Projectile}, {@code HitZone}, {@code PacketDamageEvent} and
     * {@code PacketKillEvent} registered; rounds in flight added to the state snapshot and a
     * weapon selection field added to the input packet.
     *
     * <p>4 — Phase 4 loadout: {@code PacketLoadoutUpdate}, {@code WeaponItem} and
     * {@code PlayerLoadout} registered; the loadout now rides inside {@code Player}, and the
     * input packet's weapon-ordinal field became a loadout slot press.
     */
    public static final int PROTOCOL_VERSION = 4;

    /** Reliable channel: handshake, chat, anything that must not be dropped. */
    public static final int DEFAULT_TCP_PORT = 54555;

    /** Unreliable channel: per-tick state snapshots. */
    public static final int DEFAULT_UDP_PORT = 54777;

    /** Host the desktop client dials by default. */
    public static final String DEFAULT_HOST = "127.0.0.1";

    /** How long the client waits for the TCP and UDP handshakes to complete. */
    public static final int CONNECT_TIMEOUT_MS = 5_000;

    /** Per-connection outbound buffer. Must exceed the largest single packet. */
    public static final int WRITE_BUFFER_BYTES = 64 * 1024;

    /** Scratch buffer for one serialised object graph. */
    public static final int OBJECT_BUFFER_BYTES = 16 * 1024;

    /** Hard cap on simultaneously joined players. */
    public static final int MAX_PLAYERS = 16;

    /** Inclusive bounds for a player name, enforced server-side. */
    public static final int MIN_NAME_LENGTH = 1;
    public static final int MAX_NAME_LENGTH = 24;

    /** How often the server pushes a state snapshot, independent of the simulation rate. */
    public static final int SNAPSHOT_RATE_HZ = 20;

    /**
     * Upper bound on packets buffered between the network thread and the consuming thread before
     * the oldest are dropped. Prevents a stalled render thread from exhausting the heap.
     */
    public static final int INBOUND_QUEUE_CAPACITY = 1024;

    private NetConfig() {
    }

    /** Number of simulation ticks between two outbound snapshots. */
    public static int ticksPerSnapshot(int tickRateHz) {
        return Math.max(1, tickRateHz / SNAPSHOT_RATE_HZ);
    }

    /** True when {@code name} is acceptable as a player name. */
    public static boolean isValidPlayerName(String name) {
        if (name == null) {
            return false;
        }
        String trimmed = name.trim();
        return trimmed.length() >= MIN_NAME_LENGTH && trimmed.length() <= MAX_NAME_LENGTH;
    }
}
