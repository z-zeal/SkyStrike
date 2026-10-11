package io.github.skystrike.shared.map;

/**
 * The structural kind of a {@link MapBlock}.
 *
 * <p>A kind is identity, not a behaviour switch. Consumers must ask {@link BlockRules} for the
 * relevant rule rather than branching on these values, so collision, sight, bullets, SDF baking
 * and rendering stay on the same contract.
 *
 * <p>The rules describe an intact or closed block. Dynamic door state and destroyed breakables or
 * glass are deliberately later milestones; they will select a block's active state without
 * redefining these kind rows.
 */
public enum BlockKind {
    WALL,
    GLASS,
    DOOR,
    BREAKABLE,
    PLATFORM
}
