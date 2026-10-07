package io.github.skystrike.fx.particle;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;
import io.github.skystrike.fx.gl.BlendState;
import io.github.skystrike.fx.gl.ShaderLibrary;
import io.github.skystrike.render.GameCamera;
import io.github.skystrike.shared.config.VisionConfig;

/**
 * The one renderer both particle tiers share (build plan M7 §8.2, effects plan §12.2
 * {@code particle/ParticleRenderer}): two shader programs, three draw calls, batched by blend
 * mode first.
 *
 * <p>The pass order is owned by {@code FxPipeline}, not here; this class only owns the two draws
 * and their blend states:
 * <ul>
 *   <li><b>Alpha batch</b> — the alpha GPU buffer plus the CPU casings, drawn into the scene
 *       target with premultiplied alpha, so the fog composite darkens smoke and dust.</li>
 *   <li><b>Additive batch</b> — the additive GPU buffer, drawn onto the composited frame with
 *       additive blending, so sparks, fire and flash glow through darkness. This batch is gated
 *       by the visibility texture exactly like M6's light pass: an emissive particle may glow in
 *       the dark, but it may never identify a detonation the observer cannot see.</li>
 * </ul>
 *
 * <p>Both tiers share one fragment shader; the CPU tier only differs in its vertex shader, which
 * takes pre-transformed quads instead of integrating motion.
 */
public final class ParticleRenderer implements Disposable {

    /** Matches M6's LightPass gate feather, so particles and lights agree on the cone edge. */
    private static final float VISIBILITY_GATE_FEATHER = 0.035f;

    /** World units per noise cell for the turbulence term. */
    private static final float NOISE_SCALE = 0.02f;

    private final ShaderLibrary shaders;
    private ShaderProgram gpuShader;
    private ShaderProgram cpuShader;

    public ParticleRenderer(ShaderLibrary shaders) {
        if (shaders == null) {
            throw new IllegalArgumentException("shader library is required");
        }
        this.shaders = shaders;
    }

    /**
     * The alpha batch: GPU alpha particles and CPU casings, into the currently bound scene
     * target. Premultiplied alpha blending; the fog composite darkens what lands here.
     */
    public void renderAlpha(
            GameCamera camera,
            ParticleBuffer alphaBuffer,
            CpuParticleSystem cpuParticles,
            float timeSeconds) {
        boolean hasGpu = alphaBuffer != null && alphaBuffer.liveCount(timeSeconds) > 0;
        boolean hasCpu = cpuParticles != null && cpuParticles.liveCount() > 0;
        if (!hasGpu && !hasCpu) {
            return;
        }
        if (hasGpu) {
            ShaderProgram shader = gpuShader();
            shader.bind();
            setGpuUniforms(shader, camera, timeSeconds, false);
            BlendState.setAlphaPremultiplied();
            alphaBuffer.renderWith(shader);
        }
        if (hasCpu) {
            ShaderProgram shader = cpuShader();
            shader.bind();
            shader.setUniformf("u_camPos", camera.x(), camera.y());
            shader.setUniformf("u_viewportSize", camera.viewportWidth(), camera.viewportHeight());
            // Same pass, same blend mode: casings are scene content and the fog darkens them.
            BlendState.setAlphaPremultiplied();
            cpuParticles.renderWith(shader);
        }
        // Restore the alpha-blend contract every other renderer in the frame relies on.
        BlendState.setAlpha();
    }

    /**
     * The additive batch: additive GPU particles onto the composited frame, gated by the
     * visibility texture so nothing glows at an observer who cannot see the source.
     */
    public void renderAdditive(
            GameCamera camera,
            ParticleBuffer additiveBuffer,
            Texture visibilityTexture,
            float timeSeconds) {
        if (additiveBuffer == null || additiveBuffer.liveCount(timeSeconds) <= 0) {
            return;
        }
        ShaderProgram shader = gpuShader();
        shader.bind();
        setGpuUniforms(shader, camera, timeSeconds, true);
        if (visibilityTexture != null) {
            visibilityTexture.bind(1);
            shader.setUniformi("u_visibilityTexture", 1);
            shader.setUniformf("u_visibilityFloor", VisionConfig.PERIPHERAL_FLOOR);
            shader.setUniformf("u_visibilityFeather", VISIBILITY_GATE_FEATHER);
            shader.setUniformi("u_gateVisibility", 1);
        } else {
            shader.setUniformi("u_gateVisibility", 0);
        }
        BlendState.setAdditive();
        additiveBuffer.renderWith(shader);
        BlendState.setAlpha();
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
    }

    private void setGpuUniforms(ShaderProgram shader, GameCamera camera, float timeSeconds, boolean gate) {
        shader.setUniformf("u_time", timeSeconds);
        shader.setUniformf("u_camPos", camera.x(), camera.y());
        shader.setUniformf("u_viewportSize", camera.viewportWidth(), camera.viewportHeight());
        shader.setUniformf("u_noiseScale", NOISE_SCALE);
        shader.setUniformi("u_gateVisibility", gate ? 1 : 0);
    }

    private ShaderProgram gpuShader() {
        if (gpuShader == null) {
            gpuShader = shaders.load("particle", "particle/particle.vert", "particle/particle.frag");
        }
        return gpuShader;
    }

    private ShaderProgram cpuShader() {
        if (cpuShader == null) {
            cpuShader = shaders.load("particle_cpu", "particle/particle_cpu.vert", "particle/particle.frag");
        }
        return cpuShader;
    }

    @Override
    public void dispose() {
        // Shader programs are owned by the ShaderLibrary, which disposes them itself.
    }
}
