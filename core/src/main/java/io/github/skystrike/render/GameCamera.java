package io.github.skystrike.render;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;

/**
 * The world-space camera.
 *
 * <p>Owns one concern: where the view is and how much of the arena it shows. Everything that will
 * later move the camera — following the player, the ADS pan, surveillance targets, shake — goes
 * through {@link #centreOn} and {@link #pan}, so there is never more than one thing writing the
 * camera's position.
 *
 * <p>The viewport is specified in world units of height; width follows from the window's aspect
 * ratio, so widescreen sees more to the sides rather than a squashed arena.
 */
public final class GameCamera {

    public static final float DEFAULT_VIEWPORT_HEIGHT = 1100f;
    public static final float MIN_VIEWPORT_HEIGHT = 300f;
    public static final float MAX_VIEWPORT_HEIGHT = 2400f;

    private static final float DEFAULT_ASPECT_RATIO = 16f / 9f;

    private final OrthographicCamera camera = new OrthographicCamera();
    private final float worldWidth;
    private final float worldHeight;

    private float viewportHeight = DEFAULT_VIEWPORT_HEIGHT;
    private float aspectRatio = DEFAULT_ASPECT_RATIO;
    private boolean clampedToWorld = true;

    public GameCamera(float worldWidth, float worldHeight) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        camera.setToOrtho(false, viewportHeight * aspectRatio, viewportHeight);
        centreOn(worldWidth / 2f, worldHeight / 2f);
    }

    /** Recomputes the viewport for a new window size. */
    public void resize(int windowWidth, int windowHeight) {
        if (windowWidth > 0 && windowHeight > 0) {
            aspectRatio = windowWidth / (float) windowHeight;
        }
        apply();
    }

    /** Moves the camera by a world-space offset. */
    public void pan(float dx, float dy) {
        camera.position.add(dx, dy, 0f);
        apply();
    }

    /** Places the camera centre at a world position. */
    public void centreOn(float x, float y) {
        camera.position.set(x, y, 0f);
        apply();
    }

    /** Sets how many world units tall the view is, clamped to the zoom limits. */
    public void setViewportHeight(float height) {
        viewportHeight = MathUtils.clamp(height, MIN_VIEWPORT_HEIGHT, MAX_VIEWPORT_HEIGHT);
        apply();
    }

    /** Multiplies the current view height. Values above 1 zoom out. */
    public void zoomBy(float factor) {
        setViewportHeight(viewportHeight * factor);
    }

    /** When true the view may not show anything outside the arena. */
    public void setClampedToWorld(boolean clamped) {
        this.clampedToWorld = clamped;
        apply();
    }

    public float viewportHeight() {
        return viewportHeight;
    }

    public float viewportWidth() {
        return viewportHeight * aspectRatio;
    }

    public float x() {
        return camera.position.x;
    }

    public float y() {
        return camera.position.y;
    }

    /** The view-projection matrix for world-space drawing. */
    public Matrix4 combined() {
        return camera.combined;
    }

    /** The underlying camera, for the few libGDX calls that insist on the concrete type. */
    public OrthographicCamera raw() {
        return camera;
    }

    private void apply() {
        camera.viewportHeight = viewportHeight;
        camera.viewportWidth = viewportWidth();
        if (clampedToWorld) {
            clampToWorld();
        }
        camera.update();
    }

    /**
     * Keeps the view inside the arena, and centres on the arena along any axis where the view is
     * larger than the world — scrolling past the edge of a fixed arena only ever looks like a bug.
     */
    private void clampToWorld() {
        float halfWidth = viewportWidth() / 2f;
        float halfHeight = viewportHeight / 2f;

        camera.position.x = halfWidth * 2f >= worldWidth
            ? worldWidth / 2f
            : MathUtils.clamp(camera.position.x, halfWidth, worldWidth - halfWidth);

        camera.position.y = halfHeight * 2f >= worldHeight
            ? worldHeight / 2f
            : MathUtils.clamp(camera.position.y, halfHeight, worldHeight - halfHeight);
    }
}
