package io.github.skystrike.shared.config;

/**
 * Cone reach and angle, falloff shape, peripheral floor and smoke density (mechanics §3).
 *
 * <p>Filled in Phase 2. The renderer and {@code shared/vision/VisionMath} must read the same
 * values or players get shot by things the screen swore were invisible.
 */
public final class VisionConfig {

    private VisionConfig() {
    }
}
