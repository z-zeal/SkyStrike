package io.github.skystrike.fx.lighting;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;
import io.github.skystrike.fx.gl.BlendState;
import io.github.skystrike.fx.gl.RenderTarget;
import io.github.skystrike.fx.gl.ShaderLibrary;
import io.github.skystrike.fx.sdf.SdfTexture;
import io.github.skystrike.render.GameCamera;
import io.github.skystrike.shared.config.VisionConfig;

/**
 * Half-resolution additive player-light pass. Each active light draws one radius-sized quad into
 * an RGB target; the fragment shader applies radial falloff, SDF occlusion and the local vision
 * gate before its contribution can reach the fog composite.
 */
public final class LightPass implements Disposable {

    private static final float VISIBILITY_GATE_FEATHER = 0.035f;

    private static final short[] QUAD_INDICES = {0, 1, 2, 2, 3, 0};

    private final ShaderLibrary shaders;
    private final RenderTarget lightFbo;
    private final Mesh quad;
    private final float[] vertices = new float[16];

    private ShaderProgram shader;

    public LightPass(ShaderLibrary shaders, int screenWidth, int screenHeight) {
        if (shaders == null) {
            throw new IllegalArgumentException("shader library is required");
        }
        this.shaders = shaders;
        this.lightFbo = new RenderTarget(half(screenWidth), half(screenHeight));
        this.quad = new Mesh(
                false,
                4,
                6,
                new VertexAttribute(VertexAttributes.Usage.Position, 2, ShaderProgram.POSITION_ATTRIBUTE),
                new VertexAttribute(
                        VertexAttributes.Usage.TextureCoordinates,
                        2,
                        ShaderProgram.TEXCOORD_ATTRIBUTE + "0"));
        this.quad.setIndices(QUAD_INDICES);
    }

    /** Resizes the half-resolution light target with the rest of the FX render resources. */
    public void resize(int screenWidth, int screenHeight) {
        lightFbo.resize(half(screenWidth), half(screenHeight));
    }

    /**
     * Renders the active lights. The visibility texture is sampled in the same screen-space
     * coordinates as the visibility target, and values at/below its peripheral floor are rejected
     * so player lights cannot brighten pixels outside the local vision cone or behind a wall.
     *
     * @param hardShadows true when {@code r_shadows} is off; this changes SDF penumbrae to hard
     *     edges but does not disable SDF occlusion
     */
    public void render(
            GameCamera camera,
            SdfTexture sdfTexture,
            Texture visibilityTexture,
            LightPool lights,
            boolean hardShadows) {
        lightFbo.begin();
        try {
            lightFbo.clear(0f, 0f, 0f, 0f);
            if (lights == null || lights.size() == 0) {
                return;
            }

            if (camera == null || sdfTexture == null || visibilityTexture == null) {
                throw new IllegalArgumentException("camera, SDF and visibility texture are required");
            }
            if (shader == null) {
                shader = shaders.load("light_radial", "light/visibility.vert", "light/light_radial.frag");
            }

            shader.bind();
            shader.setUniformf("u_camPos", camera.x(), camera.y());
            shader.setUniformf("u_viewportSize", camera.viewportWidth(), camera.viewportHeight());
            shader.setUniformf("u_worldSize", sdfTexture.worldWidth(), sdfTexture.worldHeight());
            shader.setUniformf(
                    "u_shadowK",
                    hardShadows ? VisionConfig.SHADOW_HARD_K : VisionConfig.SHADOW_SOFTNESS_K);
            shader.setUniformi("u_maxMarchSteps", VisionConfig.MARCH_STEPS_DESKTOP);
            shader.setUniformf("u_visibilityFloor", VisionConfig.PERIPHERAL_FLOOR);
            shader.setUniformf("u_visibilityFeather", VISIBILITY_GATE_FEATHER);

            sdfTexture.bind(0);
            shader.setUniformi("u_sdfTexture", 0);
            visibilityTexture.bind(1);
            shader.setUniformi("u_visibilityTexture", 1);

            float halfViewWidth = camera.viewportWidth() * 0.5f;
            float halfViewHeight = camera.viewportHeight() * 0.5f;
            float viewLeft = camera.x() - halfViewWidth;
            float viewRight = camera.x() + halfViewWidth;
            float viewBottom = camera.y() - halfViewHeight;
            float viewTop = camera.y() + halfViewHeight;

            BlendState.setAdditive();
            for (int slot = 0; slot < lights.capacity(); slot++) {
                Light light = lights.lightAtSlot(slot);
                if (light == null || !intersectsView(light, viewLeft, viewRight, viewBottom, viewTop)) {
                    continue;
                }
                shader.setUniformf("u_lightPos", light.x(), light.y());
                shader.setUniformf("u_lightRadius", light.radius());
                shader.setUniformf("u_lightColor", light.red(), light.green(), light.blue());
                shader.setUniformf("u_lightIntensity", light.intensity());
                shader.setUniformf("u_falloff", light.falloff());
                shader.setUniformi("u_castsShadow", light.castsShadow() ? 1 : 0);

                setQuad(camera, light);
                quad.setVertices(vertices);
                quad.render(shader, GL20.GL_TRIANGLES);
            }
        } finally {
            // Keep the next render pass on the existing alpha-blend contract, including the
            // empty-light path and exceptions during lazy shader compilation.
            BlendState.setAlpha();
            lightFbo.end();
        }
    }

    public Texture texture() {
        return lightFbo.getTexture();
    }

    @Override
    public void dispose() {
        quad.dispose();
        lightFbo.dispose();
    }

    private void setQuad(GameCamera camera, Light light) {
        float halfViewWidth = camera.viewportWidth() * 0.5f;
        float halfViewHeight = camera.viewportHeight() * 0.5f;
        float centreX = (light.x() - camera.x()) / halfViewWidth;
        float centreY = (light.y() - camera.y()) / halfViewHeight;
        float radiusX = light.radius() / halfViewWidth;
        float radiusY = light.radius() / halfViewHeight;

        float left = centreX - radiusX;
        float right = centreX + radiusX;
        float bottom = centreY - radiusY;
        float top = centreY + radiusY;

        // The shared vertex shader turns these NDC corners into world positions. Texture
        // coordinates are viewport UVs (not light-local UVs), so the visibility lookup aligns
        // with the half-resolution visibility target.
        putVertex(0, left, bottom);
        putVertex(1, right, bottom);
        putVertex(2, right, top);
        putVertex(3, left, top);
    }

    private void putVertex(int index, float ndcX, float ndcY) {
        int offset = index * 4;
        vertices[offset] = ndcX;
        vertices[offset + 1] = ndcY;
        vertices[offset + 2] = (ndcX + 1f) * 0.5f;
        vertices[offset + 3] = (ndcY + 1f) * 0.5f;
    }

    private static boolean intersectsView(
            Light light, float left, float right, float bottom, float top) {
        float radius = light.radius();
        return light.x() + radius >= left
                && light.x() - radius <= right
                && light.y() + radius >= bottom
                && light.y() - radius <= top;
    }

    private static int half(int dimension) {
        return Math.max(1, Math.max(1, dimension) / 2);
    }
}
