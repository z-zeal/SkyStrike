package io.github.skystrike.fx.gl;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.Disposable;

/**
 * Managed FrameBuffer target supporting linear filtering, clamp-to-edge wrapping and resolution
 * resizing.
 */
public final class RenderTarget implements Disposable {

    private final Pixmap.Format format;
    private final boolean hasDepth;
    private int width;
    private int height;
    private FrameBuffer fbo;

    public RenderTarget(int width, int height) {
        this(width, height, Pixmap.Format.RGBA8888, false);
    }

    public RenderTarget(int width, int height, Pixmap.Format format, boolean hasDepth) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.format = format;
        this.hasDepth = hasDepth;
        createFbo();
    }

    private void createFbo() {
        if (fbo != null) {
            fbo.dispose();
        }
        fbo = new FrameBuffer(format, width, height, hasDepth);
        Texture tex = fbo.getColorBufferTexture();
        tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        tex.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
    }

    public void resize(int newWidth, int newHeight) {
        int w = Math.max(1, newWidth);
        int h = Math.max(1, newHeight);
        if (this.width != w || this.height != h) {
            this.width = w;
            this.height = h;
            createFbo();
        }
    }

    public void begin() {
        fbo.begin();
        Gdx.gl.glViewport(0, 0, width, height);
    }

    public void end() {
        fbo.end();
    }

    public void clear(float r, float g, float b, float a) {
        Gdx.gl.glClearColor(r, g, b, a);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | (hasDepth ? GL20.GL_DEPTH_BUFFER_BIT : 0));
    }

    public Texture getTexture() {
        return fbo.getColorBufferTexture();
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    @Override
    public void dispose() {
        if (fbo != null) {
            fbo.dispose();
            fbo = null;
        }
    }
}
