package io.github.skystrike.fx.lighting;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;
import io.github.skystrike.fx.gl.BlendState;
import io.github.skystrike.fx.gl.FullscreenQuad;
import io.github.skystrike.fx.gl.RenderTarget;
import io.github.skystrike.fx.gl.ShaderLibrary;
import io.github.skystrike.fx.sdf.SdfTexture;
import io.github.skystrike.render.GameCamera;
import io.github.skystrike.shared.config.VisionConfig;
import io.github.skystrike.shared.math.Angles;
import io.github.skystrike.shared.vision.Observer;
import java.util.List;

/**
 * Manages the half-resolution visibility pass and vision cone rendering.
 *
 * <p>Draws observer vision cones with SDF soft shadowing, angular feathering and continuous
 * quadratic falloff, using MAX blending to cleanly union overlapping sightlines without
 * over-brightening.
 */
public final class VisibilitySystem implements Disposable {

    /**
     * Observer state for rendering a vision cone: one cone per observer, max-blended together.
     *
     * <p>Every field is a copy of the shared {@link io.github.skystrike.shared.vision.Observer}
     * this is built from, which is deliberate. The set of eyes a viewer has — their own body
     * unless the view has moved into a device, plus every device they own — is decided once in
     * {@code shared}, and the minimap's blip gate asks that same set what it can see. If this
     * record assembled its own observers instead, the cones drawn here and the markers drawn on
     * the HUD would be two implementations of one rule, and they would eventually disagree.
     *
     * @param brightness cone brightness multiplier, 1 for a player's own eyes and the configured
     *                   dimmer value for a gadget device's cone (mechanics §7.1: a drone's cone
     *                   is "narrower and dimmer than a player's")
     */
    public record ObserverState(
            float eyeX,
            float eyeY,
            float aimAngleDeg,
            float reach,
            float coneHalfAngleDeg,
            float featherAngleDeg,
            float brightness) {

        /** The GPU's view of one shared observer. No numbers are chosen here. */
        public static ObserverState of(Observer observer) {
            return new ObserverState(
                    observer.eyeX(),
                    observer.eyeY(),
                    observer.aimAngleDeg(),
                    observer.reach(),
                    observer.coneHalfAngleDeg(),
                    observer.featherAngleDeg(),
                    observer.brightness());
        }
    }

    private final ShaderLibrary shaders;
    private final FullscreenQuad quad;
    private final RenderTarget visibilityFbo;
    private ShaderProgram shader;

    public VisibilitySystem(ShaderLibrary shaders, int screenWidth, int screenHeight) {
        this.shaders = shaders;
        this.quad = new FullscreenQuad();
        // Half-resolution single/RGBA target
        this.visibilityFbo = new RenderTarget(Math.max(1, screenWidth / 2), Math.max(1, screenHeight / 2));
    }

    public void resize(int screenWidth, int screenHeight) {
        visibilityFbo.resize(Math.max(1, screenWidth / 2), Math.max(1, screenHeight / 2));
    }

    /**
     * Executes the visibility pass for all active observers.
     *
     * @param hardShadows {@code r_shadows} off (build plan M3 §4, F3): collapses the SDF
     *     penumbra to a hard edge instead of the normal soft falloff.
     */
    public void render(
            GameCamera camera,
            SdfTexture sdfTexture,
            List<ObserverState> observers,
            SmokeVolumes smokeVolumes,
            boolean hardShadows) {
        if (shader == null) {
            shader = shaders.load("visibility_cone", "light/visibility.vert", "light/visibility_cone.frag");
        }

        visibilityFbo.begin();
        visibilityFbo.clear(0f, 0f, 0f, 1f);

        if (observers != null && !observers.isEmpty()) {
            BlendState.setMax();

            shader.bind();
            shader.setUniformf("u_camPos", camera.x(), camera.y());
            shader.setUniformf("u_viewportSize", camera.viewportWidth(), camera.viewportHeight());
            shader.setUniformf("u_worldSize", sdfTexture.worldWidth(), sdfTexture.worldHeight());
            shader.setUniformf("u_shadowK",
                hardShadows ? VisionConfig.SHADOW_HARD_K : VisionConfig.SHADOW_SOFTNESS_K);
            shader.setUniformi("u_maxMarchSteps", VisionConfig.MARCH_STEPS_DESKTOP);
            shader.setUniformf("u_peripheralFloor", VisionConfig.PERIPHERAL_FLOOR);

            sdfTexture.bind(0);
            shader.setUniformi("u_sdfTexture", 0);

            if (smokeVolumes != null) {
                smokeVolumes.uploadUniforms(shader);
            } else {
                shader.setUniformi("u_smokeCount", 0);
            }

            for (ObserverState observer : observers) {
                shader.setUniformf("u_observerPos", observer.eyeX(), observer.eyeY());
                shader.setUniformf("u_aimAngle", Angles.toRadians(observer.aimAngleDeg()));
                shader.setUniformf("u_reach", observer.reach());
                shader.setUniformf("u_coneHalfAngle", Angles.toRadians(observer.coneHalfAngleDeg()));
                shader.setUniformf("u_featherAngle", Angles.toRadians(observer.featherAngleDeg()));
                shader.setUniformf("u_brightness", observer.brightness());

                quad.render(shader);
            }

            BlendState.setAlpha();
        }

        visibilityFbo.end();
    }

    public Texture getVisibilityTexture() {
        return visibilityFbo.getTexture();
    }

    @Override
    public void dispose() {
        quad.dispose();
        visibilityFbo.dispose();
    }
}
