package io.github.skystrike.ui.hud;

/**
 * Where the OS pointer is this frame, in the HUD's own screen pixels: origin bottom-left, the
 * projection {@link HudStage} draws with.
 *
 * <p>A frame with no {@code PointerPosition} has no pointer to follow, and the crosshair anchors
 * to the centre of the screen instead, where it has always been. That is the touch case — a phone
 * or tablet has no mouse — and the menu case, where the arena is not drawn and the picker is the
 * only cursor user.
 *
 * <p>The value is deliberately the raw window pixel, not a world position: the crosshair is a HUD
 * widget drawn in screen space, and the aim already travels mouse pixel → unprojected world point
 * → angle. Drawing the reticle at that same pixel is what makes the crosshair the cursor rather
 * than a second opinion about where the cursor is.
 *
 * @param x window pixel, origin left
 * @param y window pixel, origin bottom (the HUD's projection flips {@code Gdx.input.getY()})
 */
public record PointerPosition(float x, float y) {
}
