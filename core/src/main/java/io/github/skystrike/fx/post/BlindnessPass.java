package io.github.skystrike.fx.post;

import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;
import io.github.skystrike.fx.gl.BlendState;
import io.github.skystrike.fx.gl.FullscreenQuad;
import io.github.skystrike.fx.gl.ShaderLibrary;

/**
 * The flashbang whiteout post pass (build plan M7 §8.3, effects plan §8).
 *
 * <p>Full-screen, drawn after the composite and the additive particles and before the HUD, so the
 * flash blinds the world but never the UI. The intensity is the shared {@code StunMath} curve —
 * the server already scaled the blind by distance band and line of sight when it applied the
 * status, and the client decays the same exponential locally for a smooth recovery between
 * snapshots. A vignette deepens with the flash and animated grain keeps a full whiteout from
 * reading as a flat fill.
 *
 * <p>The overlay is premultiplied-alpha blended over whatever is already on the backbuffer, so it
 * needs no scene texture — the cost of that simplicity is no chromatic aberration, which the
 * effects plan reserves for the full secondary-effects pass.
 */
public final class BlindnessPass implements Disposable {

    /** Below this the overlay is invisible; skip the draw entirely. */
    private static final float MIN_INTENSITY = 0.002f;

    private final ShaderLibrary shaders;
    private final FullscreenQuad quad;
    private ShaderProgram shader;

    public BlindnessPass(ShaderLibrary shaders) {
        if (shaders == null) {
            throw new IllegalArgumentException("shader library is required");
        }
        this.shaders = shaders;
        this.quad = new FullscreenQuad();
    }

    /**
     * Draws the whiteout at the given intensity, in {@code [0, 1]}.
     *
     * @param intensity  the current blind intensity, from {@code StunMath.blindIntensity}
     * @param timeSeconds effect time, for the grain animation
     */
    public void render(float intensity, float timeSeconds) {
        if (intensity <= MIN_INTENSITY) {
            return;
        }
        if (shader == null) {
            shader = shaders.load("blindness", "post/fullscreen.vert", "post/blindness.frag");
        }
        shader.bind();
        shader.setUniformf("u_intensity", Math.min(1f, Math.max(0f, intensity)));
        shader.setUniformf("u_time", timeSeconds);
        BlendState.setAlphaPremultiplied();
        quad.render(shader);
        BlendState.setAlpha();
    }

    @Override
    public void dispose() {
        quad.dispose();
    }
}
