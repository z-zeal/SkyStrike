package io.github.skystrike.shared.map;

/**
 * The shared question a map consumer asks of a block kind.
 *
 * <p>Blast occlusion intentionally follows sight: glass passes both sight and blast effects.
 * SDF baking and rendering are separate columns in {@link BlockRules}, because neither is a
 * line-blocking query.
 */
public enum Blocking {
    SIGHT,
    BULLETS,
    BLAST
}
