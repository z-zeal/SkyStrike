package io.github.skystrike.shared.map;

/**
 * How a map block participates in player movement.
 *
 * <p>{@link #ONE_WAY_TOP} is deliberately a distinct value rather than a special case for
 * {@link BlockKind#PLATFORM}: the shared sweeper will use it to accept only a falling body's
 * top-surface landing without learning a block kind by name.
 */
public enum MovementRule {
    /** A normal collider blocks on every applicable face. */
    SOLID,

    /** Only a falling body crossing the top face may land on this block. */
    ONE_WAY_TOP
}
