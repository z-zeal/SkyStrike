package io.github.skystrike.shared.vision;

import java.util.ArrayList;
import java.util.List;

/**
 * The occlusion shape of one smoke or poison cloud: a small deterministic cluster of
 * {@link SmokeVolume} circles that follows the rendered cloud instead of collapsing it to a
 * single disc at the detonation point.
 *
 * <p>The client's cloud is a billowing alpha puff field (core {@code EmitterLibrary} presets
 * {@code smoke.cloud} / {@code poison.cloud} plus the sustained wisps the zone emitter keeps
 * spawning): puffs spawn at the detonation point, drift outward with their initial velocity, sag
 * under gravity, wander with curl-noise turbulence, and grow from small sprites into large ones
 * over their whole lives. A single full-radius circle matches none of that — it is already opaque
 * before the first puff has left the ground, it is centred on the detonation point while the
 * cloud sags below it, and its rim sits inside the drifted fringes. This cluster is the
 * occlusion-side answer: five circles — a dense core, two sagging side lobes and a small crown
 * where the cloud billows upward — arranged as a blob that sags downward like the cloud does and
 * reaches as far as the puffs really do.
 *
 * <p><b>Where the fractions come from.</b> The birth burst's puffs drift {@code speed/drag ≈ 53}
 * units before drag saturates them and wander up to {@code turbulence ≈ 110} units inside the
 * curl field, and a puff's visible mass out to its half-alpha radius is roughly
 * {@code 0.78 × endSize ≈ 148} units. Together that puts the bright fringe about 310 units from
 * the detonation — about 1.25× the 250-unit zone radius. The lobes carry the horizontal reach to
 * 1.40× that radius so the sides are never under-covered, the crown reaches 1.12× above where the
 * cloud billows, and the core covers the dense middle. The numbers are laid out against the
 * particle presets in {@code core/fx/particle/EmitterLibrary}; if those are retuned, these
 * fractions are the ones that must follow.
 *
 * <p><b>Growth and dispersal.</b> {@link #growth} swells the cluster over the first
 * {@link #GROW_FRACTION} of the zone's life — late enough that the occlusion is not fully out
 * while the birth puffs are still expanding to full size (their lives run 2.2–5.0 s of an 8 s
 * zone, so the cloud is visibly fully spread at about 55% of its life). {@link #dispersal} then
 * thins it back to nothing over the final {@link #DISPERSE_FRACTION}, so sight opens as the
 * visible cloud disperses instead of the occlusion popping off on the expiry tick. The combined
 * {@link #envelope} is what {@code UtilityZone.smokeVolumes()} scales the cluster by.
 *
 * <p>The cluster is a pure function of the zone's position, radius and remaining life, so the
 * server (gameplay sight queries) and every client (the visibility shader) derive the identical
 * list from the same snapshot fields — the CPU and GPU never evaluate different smoke geometry.
 * The layout fractions are public because they are the contract: a test pins them, and the
 * particle presets are tuned against them.
 */
public final class SmokeCloud {

    /**
     * Circles per cloud. Five tracks the billow — dense core, two sagging side lobes, crown —
     * while still fitting {@code VisionConfig.MAX_SMOKE_VOLUMES} three clouds deep with a slot to
     * spare. See {@link SmokeField} for how the field is filled when more clouds are live than
     * fit.
     */
    public static final int CIRCLES_PER_CLOUD = 5;

    /**
     * Fraction of the zone's life over which the cloud grows from nothing to full size. The
     * birth-burst puffs grow through their whole lives (2.2–5.0 s of an 8 s smoke zone, scaled
     * per zone), so the cloud's spread is fully out when the slowest of them tops out, with the
     * bulk full around 45% of the zone's life. 0.55 splits that: the occlusion is fully grown at
     * 4.4 s, when the rendered cloud visibly is, rather than the 3.2 s a bulk-only reading gave.
     */
    public static final float GROW_FRACTION = 0.55f;

    /**
     * Fraction of the zone's life at the end over which the occlusion thins back to nothing. The
     * zone emitter stops feeding the cloud and spawns short-lived fade wisps across this same
     * window, so the picture and the sight rule disperse together instead of the occlusion
     * outliving the smoke (invisible wall) or vanishing under it (see-through cloud).
     */
    public static final float DISPERSE_FRACTION = 0.2f;

    /** Core circle: the dense middle of the cloud, centred slightly below the detonation point. */
    public static final float CORE_OFFSET_Y = -0.14f;
    public static final float CORE_RADIUS = 0.92f;

    /**
     * Two lobes either side and lower still: the sagging, drifting bulk of the cloud. These set
     * the cluster's widest reach, so the rendered cloud's horizontal fringe is always covered.
     */
    public static final float LOBE_OFFSET_X = 0.72f;
    public static final float LOBE_OFFSET_Y = -0.30f;
    public static final float LOBE_RADIUS = 0.68f;

    /**
     * A small crown above the detonation: the part of the billow that rises before gravity and
     * drag sag it back into the mass. The only circles at or above the detonation point.
     */
    public static final float CROWN_OFFSET_X = 0.26f;
    public static final float CROWN_OFFSET_Y = 0.50f;
    public static final float CROWN_RADIUS = 0.62f;

    private SmokeCloud() {
    }

    /**
     * How grown the cloud is at {@code remainingSeconds} of its {@code durationSeconds}: 0 at
     * detonation, 1 once the first {@link #GROW_FRACTION} of the life has elapsed, eased with a
     * smoothstep so the occlusion swells with the billowing rather than popping. A cloud with no
     * recorded duration is treated as fully grown. Growth never shrinks — the end-of-life
     * thinning is {@link #dispersal}'s job, kept separate so this curve stays pinned.
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
     * How much of the cloud is left at {@code remainingSeconds} of its {@code durationSeconds}:
     * 1 until the final {@link #DISPERSE_FRACTION} of the life, then a smoothstep down to 0 at
     * expiry. A cloud with no recorded duration never disperses.
     */
    public static float dispersal(float remainingSeconds, float durationSeconds) {
        if (durationSeconds <= 0f) {
            return 1f;
        }
        float tail = durationSeconds * DISPERSE_FRACTION;
        if (tail <= 0f) {
            return remainingSeconds > 0f ? 1f : 0f;
        }
        float t = Math.max(0f, Math.min(1f, remainingSeconds / tail));
        return t * t * (3f - 2f * t);
    }

    /**
     * The scale the cluster is actually drawn and queried at: growth times dispersal, so the
     * occlusion swells with the billowing and thins with the dispersion. This is the number
     * {@code UtilityZone.smokeVolumes()} passes to {@link #volumes(float, float, float, float)}.
     */
    public static float envelope(float remainingSeconds, float durationSeconds) {
        return growth(remainingSeconds, durationSeconds) * dispersal(remainingSeconds, durationSeconds);
    }

    /**
     * The occlusion circles of a cloud at {@code (x, y)} with the given zone {@code radius},
     * scaled by {@code scale} — a factor in {@code [0, 1]}; production callers pass
     * {@link #envelope(float, float)} so the cluster swells and disperses with the cloud. A cloud
     * that has not started growing yields no circles at all — there is no smoke to occlude yet —
     * and a degenerate radius yields none either. The result is immutable and never null.
     * Layout order is part of the contract: core first, then the left/right lobes, then the
     * crown pair.
     */
    public static List<SmokeVolume> volumes(float x, float y, float radius, float scale) {
        List<SmokeVolume> volumes = new ArrayList<>(CIRCLES_PER_CLOUD);
        float g = Math.max(0f, Math.min(1f, scale));
        if (g <= 0f || radius <= 0f) {
            return List.of();
        }
        float coreY = y + CORE_OFFSET_Y * radius * g;
        float lobeY = y + LOBE_OFFSET_Y * radius * g;
        float lobeX = LOBE_OFFSET_X * radius * g;
        float crownY = y + CROWN_OFFSET_Y * radius * g;
        float crownX = CROWN_OFFSET_X * radius * g;
        volumes.add(new SmokeVolume(x, coreY, CORE_RADIUS * radius * g, 1f));
        volumes.add(new SmokeVolume(x - lobeX, lobeY, LOBE_RADIUS * radius * g, 1f));
        volumes.add(new SmokeVolume(x + lobeX, lobeY, LOBE_RADIUS * radius * g, 1f));
        volumes.add(new SmokeVolume(x - crownX, crownY, CROWN_RADIUS * radius * g, 1f));
        volumes.add(new SmokeVolume(x + crownX, crownY, CROWN_RADIUS * radius * g, 1f));
        return List.copyOf(volumes);
    }
}
