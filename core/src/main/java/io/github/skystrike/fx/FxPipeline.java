package io.github.skystrike.fx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.Disposable;
import io.github.skystrike.fx.gl.RenderTarget;
import io.github.skystrike.fx.gl.ShaderLibrary;
import io.github.skystrike.fx.lighting.SmokeVolumes;
import io.github.skystrike.fx.lighting.VisibilitySystem;
import io.github.skystrike.fx.lighting.VisibilitySystem.ObserverState;
import io.github.skystrike.fx.post.CompositePass;
import io.github.skystrike.fx.sdf.SdfCache;
import io.github.skystrike.fx.sdf.SdfDebugView;
import io.github.skystrike.fx.sdf.SdfTexture;
import io.github.skystrike.render.GameCamera;
import java.util.List;

/**
 * Orchestrates multi-pass rendering for scene geometry, visibility cones, fog composite and debug
 * overlays.
 */
public final class FxPipeline implements Disposable {

    private final ShaderLibrary shaders;
    private final SdfTexture sdfTexture;
    private final RenderTarget sceneFbo;
    private final VisibilitySystem visibilitySystem;
    private final CompositePass compositePass;
    private final SdfDebugView sdfDebugView;
    private final SmokeVolumes smokeVolumes;

    private int screenWidth;
    private int screenHeight;

    public FxPipeline(int screenWidth, int screenHeight) {
        this.screenWidth = Math.max(1, screenWidth);
        this.screenHeight = Math.max(1, screenHeight);

        this.shaders = new ShaderLibrary();
        this.sdfTexture = new SdfTexture(SdfCache.loadStandard());
        this.sceneFbo = new RenderTarget(this.screenWidth, this.screenHeight);
        this.visibilitySystem = new VisibilitySystem(this.shaders, this.screenWidth, this.screenHeight);
        this.compositePass = new CompositePass(this.shaders);
        this.sdfDebugView = new SdfDebugView(this.shaders);
        this.smokeVolumes = new SmokeVolumes();
    }

    /** Resizes framebuffer targets to match the window dimensions. */
    public void resize(int width, int height) {
        this.screenWidth = Math.max(1, width);
        this.screenHeight = Math.max(1, height);
        sceneFbo.resize(this.screenWidth, this.screenHeight);
        visibilitySystem.resize(this.screenWidth, this.screenHeight);
    }

    /**
     * Begins the scene rendering pass into the full-resolution scene buffer.
     */
    public void beginScene() {
        sceneFbo.begin();
        sceneFbo.clear(0.035f, 0.045f, 0.07f, 1f);
    }

    /**
     * Ends the scene rendering pass.
     */
    public void endScene() {
        sceneFbo.end();
    }

    /**
     * Executes the half-resolution visibility pass for all observers.
     */
    public void renderVisibility(GameCamera camera, List<ObserverState> observers) {
        visibilitySystem.render(camera, sdfTexture, observers, smokeVolumes);
    }

    /**
     * Composites the scene with the visibility buffer onto the default backbuffer.
     */
    public void composite() {
        Gdx.gl.glViewport(0, 0, screenWidth, screenHeight);
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        compositePass.render(sceneFbo.getTexture(), visibilitySystem.getVisibilityTexture(), null);
    }

    /**
     * Draws the SDF debug overlay.
     */
    public void renderSdfDebug(GameCamera camera) {
        sdfDebugView.render(camera, sdfTexture);
    }

    public SmokeVolumes smokeVolumes() {
        return smokeVolumes;
    }

    public SdfTexture sdfTexture() {
        return sdfTexture;
    }

    public VisibilitySystem visibilitySystem() {
        return visibilitySystem;
    }

    @Override
    public void dispose() {
        sceneFbo.dispose();
        visibilitySystem.dispose();
        compositePass.dispose();
        sdfDebugView.dispose();
        sdfTexture.dispose();
        shaders.dispose();
    }
}
