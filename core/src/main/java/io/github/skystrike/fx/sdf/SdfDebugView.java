package io.github.skystrike.fx.sdf;

import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;
import io.github.skystrike.fx.gl.FullscreenQuad;
import io.github.skystrike.fx.gl.ShaderLibrary;
import io.github.skystrike.render.GameCamera;

/**
 * Debug overlay for visualising the Signed Distance Field and surface isolines.
 */
public final class SdfDebugView implements Disposable {

    private final ShaderLibrary shaders;
    private final FullscreenQuad quad;
    private ShaderProgram shader;

    public SdfDebugView(ShaderLibrary shaders) {
        this.shaders = shaders;
        this.quad = new FullscreenQuad();
    }

    public void render(GameCamera camera, SdfTexture sdfTexture) {
        if (shader == null) {
            shader = shaders.load("sdf_view", "light/visibility.vert", "debug/sdf_view.frag");
        }

        shader.bind();
        shader.setUniformf("u_camPos", camera.x(), camera.y());
        shader.setUniformf("u_viewportSize", camera.viewportWidth(), camera.viewportHeight());
        shader.setUniformf("u_worldSize", sdfTexture.worldWidth(), sdfTexture.worldHeight());

        sdfTexture.bind(0);
        shader.setUniformi("u_sdfTexture", 0);

        quad.render(shader);
    }

    @Override
    public void dispose() {
        quad.dispose();
    }
}
