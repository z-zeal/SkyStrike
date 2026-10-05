package io.github.skystrike.fx.gl;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;

/**
 * Utility for managing OpenGL blending modes, including MAX blending for multi-observer visibility union.
 */
public final class BlendState {

    /** GL_MAX blend equation token (0x8008 in OpenGL ES 2.0 extension / OpenGL 2.0+ / ES 3.0). */
    public static final int GL_MAX = 0x8008;

    private BlendState() {
    }

    /** Enables standard alpha blending (SRC_ALPHA, ONE_MINUS_SRC_ALPHA). */
    public static void setAlpha() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendEquation(GL20.GL_FUNC_ADD);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    /** Enables additive blending (SRC_ALPHA, ONE or ONE, ONE). */
    public static void setAdditive() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendEquation(GL20.GL_FUNC_ADD);
        Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE);
    }

    /**
     * Enables MAX blending for unioning multiple observer vision cones without double-brightening.
     */
    public static void setMax() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        try {
            Gdx.gl.glBlendEquation(GL_MAX);
        } catch (Throwable ignored) {
            // Fallback to additive if GL_MAX is unsupported on specific legacy driver
            Gdx.gl.glBlendEquation(GL20.GL_FUNC_ADD);
        }
        Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE);
    }

    /** Disables blending. */
    public static void disable() {
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }
}
