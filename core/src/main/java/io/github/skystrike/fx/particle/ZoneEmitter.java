package io.github.skystrike.fx.particle;

import io.github.skystrike.fx.FxBudget;
import io.github.skystrike.shared.config.VisionConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.model.UtilityZone;
import io.github.skystrike.shared.utility.FireSurface;
import io.github.skystrike.shared.utility.UtilityEffect;
import io.github.skystrike.shared.utility.UtilityRegistry;
import io.github.skystrike.shared.vision.SmokeCloud;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sustained, per-zone particle emission driven from the live snapshot zone list, once per frame.
 *
 * <p>The particle system is burst-only — a recipe fires its phases once when an event arrives —
 * but a smoke grenade lives 8 seconds and a molotov 6, and one-shot bursts cannot paint either:
 * the old smoke's single 46-puff detonation thinned visibly from 2.5 s and died before the zone
 * did, and molotov fire emitted its sparks only at ignition. This driver is the second path:
 * while a zone is alive it spawns small waves on fixed time slices, so the cloud keeps billowing
 * and the fire keeps sparking for exactly as long as the authoritative zone lives.
 *
 * <p><b>Why time slices and not per-frame spawns.</b> A slice is a fixed interval of the zone's
 * own elapsed life ({@code duration − remainingSeconds}, the authoritative clock), and the wave's
 * seed is derived from the zone id and the slice index. Every client therefore emits the same
 * waves at the same times with the same layouts regardless of its frame rate — a per-frame spawn
 * rate would be frame-rate-dependent and would never match across clients. A frame that spans
 * several slices catches up a bounded number of waves, and the tier budget drops what does not
 * fit.
 *
 * <p>Fire waves launch along the surface-aware flame direction ({@link FireSurface}), so sparks
 * climb a wall and hang from a ceiling instead of always rising. Cloud waves scale with the zone
 * radius exactly like the detonation burst does, and switch to the short-lived fade wisps across
 * the zone's final {@code SmokeCloud.DISPERSE_FRACTION} — the same window the occlusion thins in
 * — so the visible cloud disperses with the sight rule instead of outliving it.
 *
 * <p>State is a bounded map of zone id → emission slices, dropped the moment a zone leaves the
 * list (the snapshot is unculled; an absent zone is an expired one). Nothing here allocates per
 * wave beyond the particle descriptors themselves.
 */
public final class ZoneEmitter {

    /** Seconds between ember waves off one fire zone. <b>Provisional</b> — a look number. */
    private static final float FIRE_EMBER_INTERVAL_SECONDS = 0.55f;

    /** Seconds between heat-smoke waves off one fire zone. <b>Provisional</b>, with the above. */
    private static final float FIRE_HEAT_INTERVAL_SECONDS = 1.1f;

    /** Seconds between wisp waves off one cloud zone. <b>Provisional</b>. */
    private static final float CLOUD_WISP_INTERVAL_SECONDS = 0.5f;

    /**
     * Slices a frame may catch up for one zone after a hitch, so a stall cannot fire a minute of
     * queued waves in one frame.
     */
    private static final int MAX_CATCHUP_WAVES = 3;

    /** Fire waves are not scaled by zone radius: every molotov patch is {@code FIRE_ZONE_RADIUS}. */
    private static final float FIRE_SCALE = 1f;

    /** Emission density per quality tier; particle size is untouched. */
    private static float countScale(FxBudget.Tier tier) {
        return switch (tier) {
            case LOW -> 0.5f;
            case MID -> 1.0f;
            case HIGH -> 1.35f;
        };
    }

    /** Per-zone emission cursors: the last slice index fired for each wave kind. */
    private static final class State {
        int emberSlice = -1;
        int heatSlice = -1;
        int wispSlice = -1;
    }

    private final Map<Integer, State> states = new HashMap<>();
    private final Set<Integer> seen = new HashSet<>();

    /**
     * Spawns this frame's due waves for every live zone. Call once per frame from the FX update;
     * zones absent from the list have their state dropped immediately. The map is only read for
     * the fire patches' surface probes ({@link FireSurface}).
     */
    public void update(List<UtilityZone> zones, ArenaMap map, GpuParticleSystem particles) {
        if (zones == null || zones.isEmpty()) {
            states.clear();
            return;
        }
        float waveScale = countScale(particles.budget().tier());
        for (UtilityZone zone : zones) {
            if (zone == null || zone.remainingSeconds <= 0f) {
                continue;
            }
            UtilityEffect effect = zone.effect();
            if (effect != UtilityEffect.FIRE
                && effect != UtilityEffect.SMOKE_CLOUD
                && effect != UtilityEffect.TOXIC_CLOUD) {
                continue;
            }
            State state = states.computeIfAbsent(zone.id, id -> new State());
            float duration = UtilityRegistry.of(zone.utility()).durationSeconds();
            float elapsed = Math.max(0f, duration - zone.remainingSeconds);
            FireSurface.Frame surface = FireSurface.of(zone, zones, map);
            if (effect == UtilityEffect.FIRE) {
                float emberAngle = flameAngleDegrees(surface);
                fireWaves(particles, EmitterLibrary.fireEmbers(), zone,
                    elapsed, FIRE_EMBER_INTERVAL_SECONDS, state, 0, emberAngle, waveScale);
                fireWaves(particles, EmitterLibrary.fireHeatSmoke(), zone,
                    elapsed, FIRE_HEAT_INTERVAL_SECONDS, state, 1, emberAngle, waveScale);
            } else {
                boolean dispersing = isDispersing(zone, duration);
                EmitterConfig wisp = effect == UtilityEffect.SMOKE_CLOUD
                    ? (dispersing ? EmitterLibrary.smokeFadeWisp() : EmitterLibrary.smokeWisp())
                    : (dispersing ? EmitterLibrary.poisonFadeWisp() : EmitterLibrary.poisonWisp());
                cloudWaves(particles, wisp, zone, elapsed, state, waveScale);
            }
            seen.add(zone.id);
        }
        states.keySet().retainAll(seen);
        seen.clear();
    }

    /** Forgets every slice cursor: teardown, and the tests. */
    public void clear() {
        states.clear();
    }

    /** Ember/heat waves for one fire zone, catching up at most {@link #MAX_CATCHUP_WAVES}. */
    private void fireWaves(
            GpuParticleSystem particles,
            EmitterConfig config,
            UtilityZone zone,
            float elapsed,
            float intervalSeconds,
            State state,
            int kind,
            float angleDegrees,
            float waveScale) {
        int slice = (int) (elapsed / intervalSeconds);
        int last = kind == 0 ? state.emberSlice : state.heatSlice;
        int waves = 0;
        for (int s = last + 1; s <= slice && waves < MAX_CATCHUP_WAVES; s++, waves++) {
            int seed = waveSeed(zone.id, s, kind);
            float x = zone.x + (hash(zone.id, s * 3 + kind) - 0.5f) * zone.radius * 0.5f;
            float y = zone.y + (hash(zone.id, s * 3 + kind + 1) - 0.5f) * zone.radius * 0.5f;
            particles.spawnBurst(config, x, y, angleDegrees, FIRE_SCALE, waveScale, seed);
        }
        if (kind == 0) {
            state.emberSlice = Math.max(state.emberSlice, slice);
        } else {
            state.heatSlice = Math.max(state.heatSlice, slice);
        }
    }

    /** Cloud wisps for one smoke or poison zone: main billow, then the dispersal tail. */
    private void cloudWaves(
            GpuParticleSystem particles,
            EmitterConfig config,
            UtilityZone zone,
            float elapsed,
            State state,
            float waveScale) {
        int slice = (int) (elapsed / CLOUD_WISP_INTERVAL_SECONDS);
        int waves = 0;
        for (int s = state.wispSlice + 1; s <= slice && waves < MAX_CATCHUP_WAVES; s++, waves++) {
            int seed = waveSeed(zone.id, s, 2);
            float x = zone.x + (hash(zone.id, s * 3 + 5) - 0.5f) * zone.radius * 0.7f;
            float y = zone.y + (hash(zone.id, s * 3 + 6) - 0.5f) * zone.radius * 0.7f;
            float cloudScale = zone.radius / VisionConfig.DEFAULT_SMOKE_RADIUS;
            particles.spawnBurst(config, x, y, 90f, cloudScale, waveScale, seed);
        }
        state.wispSlice = Math.max(state.wispSlice, slice);
    }

    /** The surface-aware launch direction of fire waves, in the effect spawn's degrees. */
    private static float flameAngleDegrees(FireSurface.Frame surface) {
        return (float) Math.toDegrees(Math.atan2(surface.flameY(), surface.flameX()));
    }

    /** Stable per-(zone, slice, kind) layout seed — never the frame counter. */
    private static int waveSeed(int zoneId, int slice, int kind) {
        return zoneId * 31 + slice * 7 + kind * 131;
    }

    /** Deterministic per-(zone, slice) offset in {@code [0, 1)}; the same on every client. */
    private static float hash(int a, int b) {
        float value = (float) Math.sin(a * 12.9898f + b * 78.233f) * 43758.5453f;
        return value - (float) Math.floor(value);
    }

    /**
     * Whether a cloud zone is inside its dispersal tail — the same window
     * {@link SmokeCloud#DISPERSE_FRACTION} thins the occlusion in, so the wisps that keep the
     * picture alive switch to the short-lived fade variant as sight starts opening.
     */
    private static boolean isDispersing(UtilityZone zone, float duration) {
        return duration > 0f
            && zone.remainingSeconds <= duration * SmokeCloud.DISPERSE_FRACTION;
    }
}
