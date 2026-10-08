package io.github.skystrike.audio;

/**
 * Whether the world blocks the straight line between two points (roadmap Phase 9, project
 * structure §8).
 *
 * <p>Audio occlusion matters more here than in most shooters. This game's whole premise is
 * restricted vision: a player reads the world through sound as much as through the cone of sight,
 * and a grenade that goes off on the far side of a wall has to sound like it did. The mixer has no
 * filter to apply, so a blocked source is <b>muffled</b>, not silenced — the information survives,
 * the "it went off in this room" lie does not.
 *
 * <p>The implementation is injected rather than baked in because the answer already exists twice
 * on the client — the shared {@code VisionMath} line-of-sight test that gameplay uses, and the
 * baked SDF the renderer marches — and this layer should not pick one and drag its whole
 * dependency chain in behind it. {@code GameScreen} wires the same call the stun resolution and
 * the remote-player light gate use, so what a player hears and what they can see cannot disagree.
 */
@FunctionalInterface
public interface OcclusionTest {

    /** The default for a client with nothing wired: the world never blocks. */
    OcclusionTest NONE = (x0, y0, x1, y1) -> false;

    /**
     * @return true when the line from {@code (x0, y0)} to {@code (x1, y1)} is blocked
     */
    boolean isBlocked(float x0, float y0, float x1, float y1);
}
