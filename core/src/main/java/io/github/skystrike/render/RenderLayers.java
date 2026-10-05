package io.github.skystrike.render;

/**
 * The draw order, written down once.
 *
 * <p>Most of these are empty in Phase 0. They exist anyway because draw order is the kind of
 * decision that is cheap now and expensive once six systems each append to the frame wherever
 * they happen to be constructed. Renderers declare the layer they belong to; the frame walks
 * {@link #values()} in order.
 *
 * <p>Two rules the ordering encodes, from the effects plan: visibility multiplies the scene and
 * light adds to it, so {@link #FOG} comes after everything in world space and before
 * {@link #POST}; and the HUD draws last of all, after the flashbang whiteout, because being
 * blinded is a gameplay state but being unable to read the chat is a UI failure.
 */
public enum RenderLayers {

    /** Sky, parallax, arena backdrop. */
    BACKGROUND,

    /** Static arena geometry. The only populated world layer in Phase 0. */
    TERRAIN,

    /** Bullet holes, scorch marks, blood. Under entities, over terrain. */
    DECALS,

    /** Players, drones, cameras, shields. */
    ENTITIES,

    /** Bullets and grenades in flight. */
    PROJECTILES,

    /** Particles, shockwaves, muzzle flashes. */
    EFFECTS,

    /** The visibility composite: cones, fog, smoke. Multiplies everything above. */
    FOG,

    /** Additive lights, applied after fog so an explosion lights a wall outside your cone. */
    LIGHTING,

    /** Bloom, distortion, blindness, vignette, grain. */
    POST,

    /** Health, ammo, minimap, kill feed, crosshair. */
    HUD,

    /** Chat and console. Last, always readable. */
    DIALOG,

    /** Hitboxes, profiler, overlays. Gated behind the debug flags. */
    DEBUG;

    /** The layers in the order a frame draws them. */
    public static RenderLayers[] drawOrder() {
        return values();
    }
}
