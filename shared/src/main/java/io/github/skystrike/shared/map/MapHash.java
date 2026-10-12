package io.github.skystrike.shared.map;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Stable SHA-256 identity for the semantic contents of an {@link ArenaMap}.
 *
 * <p>The hash deliberately sorts blocks and spawns before encoding them. Reordering otherwise
 * identical text-map lines must not force an SDF re-bake or make two clients reject the same
 * layout. Kind, material, exact float bits, mirror axis and spawns all participate, so changing
 * any authored map data produces a new identity — a per-block durability multiplier included, but
 * only when the map actually authored one, and a door's id, access and team included only for a
 * door block (see the block loop below).
 */
public final class MapHash {

    /** SHA-256 output length, in bytes. */
    public static final int BYTE_LENGTH = 32;

    private static final Comparator<SpawnPoint> SPAWN_ORDER = Comparator
        .comparingInt(SpawnPoint::teamIndex)
        .thenComparingInt(spawn -> Float.floatToIntBits(spawn.x()))
        .thenComparingInt(spawn -> Float.floatToIntBits(spawn.y()));

    private static final Comparator<MapBlock> BLOCK_ORDER = Comparator
        .comparingInt((MapBlock block) -> block.kind().ordinal())
        .thenComparingInt(block -> block.material().ordinal())
        .thenComparingInt(block -> Float.floatToIntBits(block.rect().x()))
        .thenComparingInt(block -> Float.floatToIntBits(block.rect().y()))
        .thenComparingInt(block -> Float.floatToIntBits(block.rect().width()))
        .thenComparingInt(block -> Float.floatToIntBits(block.rect().height()))
        // Doors are the one kind whose identity is not their geometry, so two door blocks may
        // share a rectangle while being different doors. Ordering by id keeps the hash a function
        // of the contents rather than of the line order they were authored in.
        .thenComparingInt(block -> block.door().id());

    private MapHash() {
    }

    /** Returns the stable semantic SHA-256 identity for {@code map}. */
    public static byte[] of(ArenaMap map) {
        Objects.requireNonNull(map, "map");

        List<SpawnPoint> spawns = new ArrayList<>(map.spawns());
        spawns.sort(SPAWN_ORDER);
        List<MapBlock> blocks = new ArrayList<>(map.blocks());
        blocks.sort(BLOCK_ORDER);

        StringBuilder canonical = new StringBuilder();
        canonical.append("skystrike-map-hash 1\n");
        canonical.append("mirror ");
        appendFloatBits(canonical, map.mirrorAxisX());
        canonical.append('\n');
        canonical.append("spawns ").append(spawns.size()).append('\n');
        for (SpawnPoint spawn : spawns) {
            canonical.append(spawn.teamIndex()).append(' ');
            appendFloatBits(canonical, spawn.x());
            canonical.append(' ');
            appendFloatBits(canonical, spawn.y());
            canonical.append('\n');
        }
        canonical.append("blocks ").append(blocks.size()).append('\n');
        for (MapBlock block : blocks) {
            Rect rect = block.rect();
            canonical.append(block.kind().ordinal()).append(' ')
                .append(block.material().ordinal()).append(' ');
            appendFloatBits(canonical, rect.x());
            canonical.append(' ');
            appendFloatBits(canonical, rect.y());
            canonical.append(' ');
            appendFloatBits(canonical, rect.width());
            canonical.append(' ');
            appendFloatBits(canonical, rect.height());
            // The per-block durability multiplier is encoded only when the map authored one, so
            // the shipped arena — every block of it at the default — hashes exactly as it did
            // before the field existed and keeps the baked SDF asset that names it valid. A
            // default multiplier is not authored content; the canonical writer omits it too, so
            // two spellings of the same map still cannot hash differently.
            if (block.hasAuthoredDurabilityMultiplier()) {
                canonical.append(' ');
                appendFloatBits(canonical, block.durabilityMultiplier());
            }
            // A door's id, access and team are authored content exactly as its rectangle is: two
            // maps whose doors differ must not share an identity. Encoded only for a real door, so
            // every block of a map without doors hashes precisely as it did before doors existed —
            // the shipped arena included, whose baked SDF asset names its hash.
            if (block.door().isDoor()) {
                canonical.append(" door ").append(block.door().id())
                    .append(' ').append(block.door().access().ordinal())
                    .append(' ').append(block.door().teamIndex());
            }
            canonical.append('\n');
        }

        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            if (hash.length != BYTE_LENGTH) {
                throw new IllegalStateException("SHA-256 returned " + hash.length + " bytes");
            }
            return hash;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("the Java runtime does not provide SHA-256", exception);
        }
    }

    /** Renders a hash as lower-case hexadecimal for a load error or diagnostic. */
    public static String hex(byte[] hash) {
        Objects.requireNonNull(hash, "hash");
        if (hash.length != BYTE_LENGTH) {
            throw new IllegalArgumentException("map hash must be " + BYTE_LENGTH + " bytes, was " + hash.length);
        }
        StringBuilder hex = new StringBuilder(BYTE_LENGTH * 2);
        for (byte value : hash) {
            hex.append(Character.forDigit((value >>> 4) & 0x0f, 16));
            hex.append(Character.forDigit(value & 0x0f, 16));
        }
        return hex.toString();
    }

    private static void appendFloatBits(StringBuilder target, float value) {
        target.append(Integer.toUnsignedString(Float.floatToIntBits(value)));
    }
}
