package io.github.skystrike.shared.vision;

/**
 * A 2D spherical smoke volume that locally attenuates visibility and blocks line of sight.
 *
 * <p>Shared between CPU gameplay queries ({@link VisionMath}) and GPU visibility shaders
 * ({@code visibility_cone.frag}).
 */
public record SmokeVolume(float x, float y, float radius, float density) {

    public SmokeVolume {
        if (radius < 0f) {
            throw new IllegalArgumentException("radius cannot be negative: " + radius);
        }
        if (density < 0f || density > 1f) {
            throw new IllegalArgumentException("density must be in [0, 1]: " + density);
        }
    }

    /** Returns true if the point (px, py) is strictly inside this smoke volume. */
    public boolean contains(float px, float py) {
        float dx = px - x;
        float dy = py - y;
        return (dx * dx + dy * dy) <= (radius * radius);
    }

    /**
     * Computes the smoke density at a specific point, falling off linearly from center to edge.
     */
    public float densityAt(float px, float py) {
        float dx = px - x;
        float dy = py - y;
        float distSq = dx * dx + dy * dy;
        float radSq = radius * radius;
        if (distSq >= radSq) {
            return 0f;
        }
        float dist = (float) Math.sqrt(distSq);
        return density * (1f - dist / radius);
    }

    /**
     * Computes the optical attenuation for a line of sight segment between (x0, y0) and (x1, y1).
     * Returns a value in [0, density] based on the closest approach of the segment to the smoke center.
     */
    public float attenuationAlongSegment(float x0, float y0, float x1, float y1) {
        float segDx = x1 - x0;
        float segDy = y1 - y0;
        float segLenSq = segDx * segDx + segDy * segDy;
        if (segLenSq < 0.0001f) {
            return densityAt(x0, y0);
        }

        // Project center onto segment: t = dot(center - p0, p1 - p0) / |p1 - p0|^2
        float t = ((x - x0) * segDx + (y - y0) * segDy) / segLenSq;
        float clampedT = Math.max(0f, Math.min(1f, t));

        float closestX = x0 + clampedT * segDx;
        float closestY = y0 + clampedT * segDy;

        return densityAt(closestX, closestY);
    }
}
