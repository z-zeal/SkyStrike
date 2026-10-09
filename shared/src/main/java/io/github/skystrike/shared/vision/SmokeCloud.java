package io.github.skystrike.shared.vision;

import java.util.ArrayList;
import java.util.List;

/**
 * The occlusion shape of one smoke or poison cloud: a small deterministic cluster of
 * {@link SmokeVolume} circles that follows the rendered cloud instead of collapsing it to a
 * single disc at the detonation point.
 *
 * <p>The client's cloud is a one-shot particle burst (core {@code EmitterLibrary} presets
 * {@code smoke.cloud} / {@code poison.cloud}): puffs spawn at the detonation point, drift outward
 * with their initial velocity, sag under gravity, wander with curl-noise turbulence, and grow
 * from small sprites into large ones. A single full-radius circle matches none of that — it is
 * already opaque before the first puff has left the ground, it is centred on the detonation
 * point while the cloud sags below it, and its rim sits inside the drifted fringes. This cluster
 * is the occlusion-side answer: one core circle plus two lobes, arranged as a blob that sags
 * downward like the cloud does, and scaled by a growth factor so the occlusion appears and
 * swells exactly when the rendered cloud does.
 *
 * <p>The cluster is a pure function of the zone's position, radius and remaining life, so the
 * server (gameplay sight queries) and every client (the visibility shader) derive the identical
 * list from the same snapshot fields — the CPU and GPU never evaluate different smoke geometry.
 * The layout fractions are public because they are the contract: a test pins them, and the
 * particle presets are tuned against them.
 */
public final class SmokeCloud {

    /** Circles per cloud. Three keeps two overlapping clouds inside the shader's uniform cap. */
    public static final int CIRCLES_PER_CLOUD = 3;

    /**
     * Fraction of the zone's life over which the cloud grows from nothing to full size. The
     * particle bursts reach their spread within a few seconds of detonation (drag saturates the
     * initial velocity in about a second, turbulence is a bounded offset), so the occlusion
     * swells over the same window rather than sitting at full radius from the first frame.
     */
    public static final float GROW_FRACTION = 0.4f;

    /** Core circle: centred slightly below the detonation point, where the cloud's mass is. */
    public static final float CORE_OFFSET_Y = -0.12f;
    public static final float CORE_RADIUS = 0.68f;

    /** Two lobes either side, lower still: the sagging, drifting bulk of the cloud. */
    public static final float LOBE_OFFSET_X = 0.52f;
    public static final float LOBE_OFFSET_Y = -0.30f;
    public static final float LOBE_RADIUS = 0.50f;

    private SmokeCloud() {
    }

    /**
     * How grown the cloud is at {@code remainingSeconds} of its {@code durationSeconds}: 0 at
     * detonation, 1 once the first {@link #GROW_FRACTION} of the life has elapsed, eased with a
     * smoothstep so the occlusion swells with the billowing rather than popping. A cloud with no
     * recorded duration is treated as fully grown.
     */
    public static float growth(float remainingSeconds, float durationSeconds) {
        if (durationSeconds <= 0f) {
            return 1f;
        }
        float elapsed = durationSeconds - remainingSeconds;
        float t = elapsed / (durationSeconds * GROW_FRACTION);
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    /**
     * The occlusion circles of a cloud at {@code (x, y)} with the given zone {@code radius},
     * scaled by {@code growth} (see {@link #growth(float, float)}). A cloud that has not started
     * growing yields no circles at all — there is no smoke to occlude yet — and a degenerate
     * radius yields none either. The result is immutable and never null.
     */
    public static List<SmokeVolume> volumes(float x, float y, float radius, float growth) {
        List<SmokeVolume> volumes = new ArrayList<>(CIRCLES_PER_CLOUD);
        float g = Math.max(0f, Math.min(1f, growth));
        if (g <= 0f || radius <= 0f) {
            return List.of();
        }
        float coreY = y + CORE_OFFSET_Y * radius * g;
        float lobeY = y + LOBE_OFFSET_Y * radius * g;
        float lobeX = LOBE_OFFSET_X * radius * g;
        volumes.add(new SmokeVolume(x, coreY, CORE_RADIUS * radius * g, 1f));
        volumes.add(new SmokeVolume(x - lobeX, lobeY, LOBE_RADIUS * radius * g, 1f));
        volumes.add(new SmokeVolume(x + lobeX, lobeY, LOBE_RADIUS * radius * g, 1f));
        return List.copyOf(volumes);
    }
}
