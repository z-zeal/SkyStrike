package io.github.skystrike.fx.gl;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;

/**
 * Reusable full-screen NDC quad mesh for post-processing and fullscreen FBO passes.
 */
public final class FullscreenQuad implements Disposable {

    private final Mesh mesh;

    public FullscreenQuad() {
        float[] vertices = new float[] {
            // x, y, u, v
            -1.0f, -1.0f, 0.0f, 0.0f,
             1.0f, -1.0f, 1.0f, 0.0f,
             1.0f,  1.0f, 1.0f, 1.0f,
            -1.0f,  1.0f, 0.0f, 1.0f
        };
        short[] indices = new short[] { 0, 1, 2, 2, 3, 0 };

        mesh = new Mesh(
                true,
                4,
                6,
                new VertexAttribute(VertexAttributes.Usage.Position, 2, ShaderProgram.POSITION_ATTRIBUTE),
                new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, ShaderProgram.TEXCOORD_ATTRIBUTE + "0"));
        mesh.setVertices(vertices);
        mesh.setIndices(indices);
    }

    /**
     * Draws the fullscreen quad with the specified active shader program.
     */
    public void render(ShaderProgram shader) {
        mesh.render(shader, GL20.GL_TRIANGLES);
    }

    @Override
    public void dispose() {
        mesh.dispose();
    }
}
