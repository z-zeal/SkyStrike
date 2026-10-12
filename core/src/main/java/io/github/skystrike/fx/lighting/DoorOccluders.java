package io.github.skystrike.fx.lighting;

import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import io.github.skystrike.shared.config.VisionConfig;
import io.github.skystrike.shared.map.Rect;

/**
 * The closed-door occluder array for the visibility and light shaders (map plan D3): axis
 * -aligned door rectangles this frame, uploaded as a flat {@code vec4} uniform array after the
 * {@link SmokeVolumes} pattern.
 *
 * <p>Doors need this separate channel because a door never enters the SDF the walls bake into:
 * the SDF is re-baked only when terrain is destroyed, a door swings every few seconds, and the
 * static-map rule (the renderer draws the panel posed from {@code MapDoorState}) is exactly
 * what keeps the bake cheap. So the two shaders test a sight/light segment against these
 * rectangles analytically, next to the SDF march, for everything the bake cannot know.
 *
 * <p>The contract with the shaders is the same one smoke keeps: the array is flat, capped at
 * {@link VisionConfig#MAX_DOOR_OCCLUDERS} (which the map format already enforces at author
 * time, so overflow is structurally impossible and unchecked here), uploaded once per pass, and
 * the tail beyond {@link #count()} is zeroed so a stale rect from a fuller earlier frame cannot
 * gate a ray that should now pass.
 */
public final class DoorOccluders {

    private final float[] uniformBuffer = new float[VisionConfig.MAX_DOOR_OCCLUDERS * 4];
    private int count;

    /** Starts the frame's set; follows {@link #add} calls and an {@link #uploadUniforms}. */
    public void clear() {
        count = 0;
    }

    /**
     * Adds one door rectangle (xy = lower-left, zw = size). The feed decides which doors are
     * occluders at all — exactly the ones whose machine state still blocks
     * ({@code MapDoorState.blocks}), so an already-open door costs no shader iterations.
     */
    public void add(Rect rect) {
        if (rect == null || count >= VisionConfig.MAX_DOOR_OCCLUDERS) {
            return;
        }
        int base = count * 4;
        uniformBuffer[base] = rect.x();
        uniformBuffer[base + 1] = rect.y();
        uniformBuffer[base + 2] = rect.width();
        uniformBuffer[base + 3] = rect.height();
        count++;
    }

    /** The number of doors this frame's upload states; everything past it reads as zeroes. */
    public int count() {
        return count;
    }

    /** Uploads the occluder uniforms to the active pass's shader, tail zeroed. */
    public void uploadUniforms(ShaderProgram shader) {
        shader.setUniformi("u_doorCount", count);
        for (int i = count * 4; i < uniformBuffer.length; i++) {
            uniformBuffer[i] = 0f;
        }
        shader.setUniform4fv("u_doorRects[0]", uniformBuffer, 0, uniformBuffer.length);
    }
}
