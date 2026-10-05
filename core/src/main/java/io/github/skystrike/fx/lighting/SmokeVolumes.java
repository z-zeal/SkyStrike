package io.github.skystrike.fx.lighting;

import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import io.github.skystrike.shared.config.VisionConfig;
import io.github.skystrike.shared.vision.SmokeVolume;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages active smoke volumes and uploads them to the visibility shader uniform array.
 */
public final class SmokeVolumes {

    private final List<SmokeVolume> active = new ArrayList<>();
    private final float[] uniformBuffer = new float[VisionConfig.MAX_SMOKE_VOLUMES * 4];

    public void add(SmokeVolume smoke) {
        if (smoke != null && active.size() < VisionConfig.MAX_SMOKE_VOLUMES) {
            active.add(smoke);
        }
    }

    public void remove(SmokeVolume smoke) {
        active.remove(smoke);
    }

    public void clear() {
        active.clear();
    }

    public List<SmokeVolume> all() {
        return Collections.unmodifiableList(active);
    }

    public int count() {
        return active.size();
    }

    /**
     * Uploads the smoke circle uniforms to the active visibility shader.
     */
    public void uploadUniforms(ShaderProgram shader) {
        int count = Math.min(active.size(), VisionConfig.MAX_SMOKE_VOLUMES);
        shader.setUniformi("u_smokeCount", count);

        for (int i = 0; i < count; i++) {
            SmokeVolume sv = active.get(i);
            int base = i * 4;
            uniformBuffer[base] = sv.x();
            uniformBuffer[base + 1] = sv.y();
            uniformBuffer[base + 2] = sv.radius();
            uniformBuffer[base + 3] = sv.density();
        }

        // Fill remaining with zeroes
        for (int i = count * 4; i < uniformBuffer.length; i++) {
            uniformBuffer[i] = 0f;
        }

        shader.setUniform4fv("u_smokeCircles[0]", uniformBuffer, 0, uniformBuffer.length);
    }
}
