package io.github.skystrike.shared.map;

/**
 * Renderer-facing shape treatment declared by a {@link BlockRules} row.
 *
 * <p>The values deliberately contain no LibGDX types or colours. The core renderer owns palette
 * selection while the shared table remains the one source of which treatment a kind requires.
 */
public enum BlockRenderStyle {
    SOLID_FILL,
    GLASS_PANE,
    DOOR_PANEL,
    BREAKABLE_FILL,
    PLATFORM_SLAB
}
