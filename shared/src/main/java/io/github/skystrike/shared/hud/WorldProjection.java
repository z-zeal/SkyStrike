package io.github.skystrike.shared.hud;

/**
 * The HUD's one world-to-screen rule, for the widgets that belong to a place in the arena — the
 * floating damage numbers (mechanics §10).
 *
 * <p>Every other HUD widget is laid out in screen space. A damage number is the exception: it
 * is anchored where the round landed and must stay on that wall while the camera pans, so it
 * needs the camera's view. The camera is orthographic and never rotates, so the projection is
 * two axis-aligned ratios. Both spaces are y-up, like the HUD's own projection.
 *
 * <p>Kept in {@code shared} rather than beside the widget so the arithmetic has tests in CI;
 * the client only supplies the camera centre and the view size it already holds.
 *
 * @param cameraX      world x of the view centre
 * @param cameraY      world y of the view centre
 * @param viewWidth    world units visible across the screen
 * @param viewHeight   world units visible down the screen
 * @param screenWidth  screen width in pixels
 * @param screenHeight screen height in pixels
 */
public record WorldProjection(
    float cameraX,
    float cameraY,
    float viewWidth,
    float viewHeight,
    int screenWidth,
    int screenHeight
) {

    /** Screen x, in pixels from the left edge, of a world x. */
    public float toScreenX(float worldX) {
        return (worldX - (cameraX - viewWidth / 2f)) * screenWidth / viewWidth;
    }

    /** Screen y, in pixels from the bottom edge, of a world y. */
    public float toScreenY(float worldY) {
        return (worldY - (cameraY - viewHeight / 2f)) * screenHeight / viewHeight;
    }

    /** True when a world point is on screen, with {@code marginPixels} of slack. */
    public boolean onScreen(float worldX, float worldY, float marginPixels) {
        float x = toScreenX(worldX);
        float y = toScreenY(worldY);
        return x >= -marginPixels && x <= screenWidth + marginPixels
            && y >= -marginPixels && y <= screenHeight + marginPixels;
    }
}
