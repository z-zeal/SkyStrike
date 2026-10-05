package io.github.skystrike.fx.post;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;
import io.github.skystrike.fx.gl.FullscreenQuad;
import io.github.skystrike.fx.gl.ShaderLibrary;
import io.github.skystrike.shared.config.VisionConfig;

/**
 * Fog composite pass: {@code scene * max(visibility, ambientFloor) + light}.
 *
 * <p>Multiplies world-space scene contents by the occluded vision field, leaving non-cone and
 * wall-occluded space in pitch black or faint ambient illumination.
 */
public final class CompositePass implements Disposable {

    private final ShaderLibrary shaders;
    private final FullscreenQuad quad;
    private ShaderProgram shader;

    public CompositePass(ShaderLibrary shaders) {
        this.shaders = shaders;
        this.quad = new FullscreenQuad();
    }

    /**
     * Executes the fog composite pass into the currently bound framebuffer or backbuffer.
     */
    public void render(Texture sceneTexture, Texture visibilityTexture, Texture lightTexture) {
        render(sceneTexture, visibilityTexture, lightTexture, VisionConfig.AMBIENT_FLOOR);
    }

    /**
     * Executes the fog composite pass with a custom ambient floor.
     */
    public void render(Texture sceneTexture, Texture visibilityTexture, Texture lightTexture, float ambientFloor) {
        if (shader == null) {
            shader = shaders.load("composite", "post/fullscreen.vert", "post/composite.frag");
        }

        shader.bind();

        sceneTexture.bind(0);
        shader.setUniformi("u_sceneTexture", 0);

        visibilityTexture.bind(1);
        shader.setUniformi("u_visibilityTexture", 1);

        if (lightTexture != null) {
            lightTexture.bind(2);
            shader.setUniformi("u_lightTexture", 2);
            shader.setUniformi("u_hasLight", 1);
        } else {
            shader.setUniformi("u_hasLight", 0);
        }

        shader.setUniformf("u_ambientFloor", ambientFloor);

        quad.render(shader);

        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
    }

    @Override
    public void dispose() {
        quad.dispose();
    }
}
