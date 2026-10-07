package io.github.skystrike.fx.particle;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;
import io.github.skystrike.fx.FxBudget;
import io.github.skystrike.fx.FxEventQueue;
import io.github.skystrike.fx.FxStats;
import io.github.skystrike.fx.gl.ShaderLibrary;
import io.github.skystrike.fx.lighting.Light;
import io.github.skystrike.fx.lighting.LightPool;
import io.github.skystrike.render.GameCamera;
import io.github.skystrike.shared.effect.EffectSpawn;
import io.github.skystrike.shared.sdf.SdfField;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * The client's particle and attached-light system (build plan M7 §8.2/§8.3).
 *
 * <p>One update per frame on the render thread: advance the {@link FxClock}, drain the
 * {@link FxEventQueue} (network arrivals only ever enqueue), schedule the drained events'
 * phases, fire the phases whose time has come, animate the attached lights, and step the CPU
 * tier. Rendering is two draws — alpha into the scene pass, additive after the composite — and
 * the pass order itself stays owned by {@code FxPipeline}.
 *
 * <p>Phases exist so a frag reads as a sequence over 0.10 s: an event never spawns anything
 * synchronously, it schedules, and the pending queue fires. That is also what keeps the network
 * path trivial — a producer only ever enqueues.
 *
 * <p>The universal occlusion rule (effects plan §9): every occlusion-tested spawn position is
 * projected out of solids along the SDF gradient and raymarched from the event's origin, so no
 * particle ever appears on the far side of a wall from its source. The attached lights are not
 * occlusion-tested here — the light pass's own SDF shadows and visibility gate are the second
 * line, and they are what lets a frag round a corner light the wall face without showing
 * particles through it.
 *
 * <p>All caps come from {@link FxBudget}: particle buffers are sized by the tier's GPU cap, the
 * casing pool by the CPU cap, and effect lights by the effect-light cap, with high-priority
 * lights (explosion flashes) evicting the oldest ordinary one rather than being dropped
 * themselves (effects plan §6.1).
 */
public final class GpuParticleSystem implements Disposable {

    private final FxClock clock;
    private final FxBudget budget;
    private final LightPool lightPool;
    private final SdfField sdf;
    private final FxEventQueue queue = new FxEventQueue();
    private final ParticleRenderer renderer;
    private final Random random = new Random();
    private final float[] spawnScratch = new float[2];
    private final float[] gradientScratch = new float[2];
    private final Color scratchColor = new Color();

    private ParticleBuffer alphaBuffer;
    private ParticleBuffer additiveBuffer;
    private final CpuParticleSystem cpuParticles;
    private final List<PendingPhase> pending = new ArrayList<>();
    private final List<EffectLightInstance> lights = new ArrayList<>();

    public GpuParticleSystem(
            FxClock clock,
            FxBudget budget,
            LightPool lightPool,
            SdfField sdf,
            ShaderLibrary shaders) {
        if (clock == null || budget == null || shaders == null) {
            throw new IllegalArgumentException("clock, budget and shader library are required");
        }
        this.clock = clock;
        this.budget = budget;
        this.lightPool = lightPool;
        this.sdf = sdf;
        this.renderer = new ParticleRenderer(shaders);
        this.alphaBuffer = new ParticleBuffer(budget.gpuParticleCap());
        this.additiveBuffer = new ParticleBuffer(budget.gpuParticleCap());
        this.cpuParticles = new CpuParticleSystem(budget.cpuParticleCap(), sdf);
    }

    public FxClock clock() {
        return clock;
    }

    public FxBudget budget() {
        return budget;
    }

    /** Enqueues one effect request. Any thread; the spawn is copied on the way in. */
    public void enqueue(EffectSpawn spawn) {
        queue.enqueue(spawn);
    }

    /** Enqueues a received batch. Any thread. */
    public void enqueueAll(List<EffectSpawn> spawns) {
        queue.enqueueAll(spawns);
    }

    /** One frame of FX: clock, queue, phases, lights, CPU tier, buffer uploads. Render thread. */
    public void update(float deltaSeconds) {
        clock.advance(deltaSeconds);
        for (EffectSpawn spawn : queue.drainToList()) {
            schedule(spawn);
        }
        fireDuePhases();
        updateLights(deltaSeconds);
        cpuParticles.update(deltaSeconds);
        alphaBuffer.uploadIfDirty();
        additiveBuffer.uploadIfDirty();
    }

    /** The alpha batch: scene-pass particles plus CPU casings, drawn into the scene target. */
    public void renderAlphaParticles(GameCamera camera) {
        renderer.renderAlpha(camera, alphaBuffer, cpuParticles, clock.time());
    }

    /** The additive batch: post-composite particles that glow through darkness. */
    public void renderAdditiveParticles(GameCamera camera, Texture visibilityTexture) {
        renderer.renderAdditive(camera, additiveBuffer, visibilityTexture, clock.time());
    }

    /** Applies a new quality tier, resizing the buffers and the casing pool when the caps move. */
    public void setTier(FxBudget.Tier tier) {
        FxBudget.Tier previous = budget.tier();
        budget.setTier(tier);
        if (budget.tier() == previous) {
            return;
        }
        if (budget.gpuParticleCap() != alphaBuffer.capacity()) {
            alphaBuffer.dispose();
            additiveBuffer.dispose();
            alphaBuffer = new ParticleBuffer(budget.gpuParticleCap());
            additiveBuffer = new ParticleBuffer(budget.gpuParticleCap());
        }
        if (budget.cpuParticleCap() != cpuParticles.capacity()) {
            cpuParticles.setCapacity(budget.cpuParticleCap());
        }
    }

    /** The fx_debug overlay's view: live counts against the tier budget. */
    public FxStats stats() {
        float now = clock.time();
        return new FxStats(
            alphaBuffer.liveCount(now),
            alphaBuffer.capacity(),
            additiveBuffer.liveCount(now),
            additiveBuffer.capacity(),
            cpuParticles.liveCount(),
            cpuParticles.capacity(),
            lights.size(),
            budget.effectLightCap(),
            pending.size());
    }

    public int pendingPhaseCount() {
        return pending.size();
    }

    @Override
    public void dispose() {
        for (EffectLightInstance light : lights) {
            lightPool.release(light.handle);
        }
        lights.clear();
        pending.clear();
        queue.clear();
        alphaBuffer.dispose();
        additiveBuffer.dispose();
        cpuParticles.dispose();
        renderer.dispose();
    }

    // --- Phasing ---------------------------------------------------------------------------------

    /** Schedules every phase of the event's recipe; nothing fires synchronously. */
    private void schedule(EffectSpawn spawn) {
        if (spawn == null || spawn.type == null) {
            return;
        }
        EmitterLibrary.EffectRecipe recipe = EmitterLibrary.recipeFor(spawn.type);
        if (recipe == null || recipe == EmitterLibrary.NONE) {
            return;
        }
        for (EmitterLibrary.TimedEmitter phase : recipe.emitters()) {
            pending.add(new PendingPhase(
                clock.time() + Math.max(0f, phase.delaySeconds()), spawn, phase.emitter(), null));
        }
        if (recipe.light() != null) {
            pending.add(new PendingPhase(clock.time(), spawn, null, recipe.light()));
        }
    }

    private void fireDuePhases() {
        float now = clock.time();
        Iterator<PendingPhase> iterator = pending.iterator();
        while (iterator.hasNext()) {
            PendingPhase phase = iterator.next();
            if (phase.fireTime > now) {
                continue;
            }
            iterator.remove();
            if (phase.emitter != null) {
                spawnParticles(phase.spawn, phase.emitter);
            } else if (phase.light != null) {
                spawnLight(phase.spawn, phase.light);
            }
        }
    }

    // --- Spawning --------------------------------------------------------------------------------

    private void spawnParticles(EffectSpawn spawn, EmitterConfig config) {
        int count = Math.round(config.count() * Math.max(0.1f, spawn.scale));
        if (count <= 0) {
            return;
        }
        // One deterministic random source per (event, emitter): every client that received the
        // same seed lays out the same burst.
        random.setSeed(spawn.seed * 7919L + config.name().hashCode());
        if (config.tier() == EmitterConfig.Tier.CPU) {
            for (int i = 0; i < count; i++) {
                cpuParticles.spawn(config, spawn.x, spawn.y, spawn.angle, spawn.scale, random);
            }
            return;
        }
        ParticleBuffer buffer =
            config.blend() == EmitterConfig.Blend.ALPHA ? alphaBuffer : additiveBuffer;
        float now = clock.time();
        float jitter = 2f * Math.max(0.1f, spawn.scale);
        for (int i = 0; i < count; i++) {
            float angleDegrees;
            if (config.spreadDegrees() >= 359.9f) {
                angleDegrees = random.nextFloat() * 360f;
            } else {
                angleDegrees = spawn.angle + (random.nextFloat() - 0.5f) * config.spreadDegrees();
            }
            float speed = lerp(config.speedMin(), config.speedMax(), random.nextFloat())
                * Math.max(0.1f, spawn.scale);
            spawnScratch[0] = spawn.x + (random.nextFloat() - 0.5f) * 2f * jitter;
            spawnScratch[1] = spawn.y + (random.nextFloat() - 0.5f) * 2f * jitter;
            if (config.occlusionTested()
                && !resolveSpawnPoint(spawn.x, spawn.y, spawnScratch)) {
                continue;
            }
            float lifetime = lerp(
                config.lifetimeMinSeconds(), config.lifetimeMaxSeconds(), random.nextFloat());
            double radians = Math.toRadians(angleDegrees);
            float scale = Math.max(0.1f, spawn.scale);
            boolean spawned = buffer.spawn(now, new ParticleBuffer.ParticleDescriptor(
                spawnScratch[0],
                spawnScratch[1],
                (float) Math.cos(radians) * speed,
                (float) Math.sin(radians) * speed,
                now,
                lifetime,
                config.startSize() * scale,
                config.endSize() * scale,
                config.startColor().r,
                config.startColor().g,
                config.startColor().b,
                config.startColor().a,
                config.endColor().r,
                config.endColor().g,
                config.endColor().b,
                config.endColor().a,
                config.gravity(),
                config.drag(),
                config.turbulence()));
            if (!spawned) {
                // The tier budget is full: drop the rest of the burst rather than grow past it.
                return;
            }
        }
    }

    /**
     * The universal occlusion rule (effects plan §9): a spawn position inside a solid is pushed
     * out along the SDF gradient so dust hugs the surface it hit, and a position with no hard
     * line of sight from the event's origin is rejected outright.
     *
     * @param originX event origin
     * @param originY event origin
     * @param xy      in/out spawn position, adjusted in place
     */
    private boolean resolveSpawnPoint(float originX, float originY, float[] xy) {
        if (sdf == null) {
            return true;
        }
        float distance = sdf.sample(xy[0], xy[1]);
        if (distance < 1f) {
            sdf.gradient(xy[0], xy[1], gradientScratch);
            float push = 1.5f - distance;
            xy[0] += gradientScratch[0] * push;
            xy[1] += gradientScratch[1] * push;
            if (sdf.sample(xy[0], xy[1]) < 0f) {
                return false;
            }
        }
        return sdf.raymarch(originX, originY, xy[0], xy[1], 16f, 24) > 0.05f;
    }

    // --- Attached lights -------------------------------------------------------------------------

    private void spawnLight(EffectSpawn spawn, EmitterLibrary.EffectLight spec) {
        if (lightPool == null || spec.durationSeconds() <= 0f) {
            return;
        }
        if (lights.size() >= budget.effectLightCap()) {
            if (!spec.highPriority()) {
                return;
            }
            // Explosion flashes are high-priority and never the ones culled (effects plan §6.1):
            // evict the oldest ordinary effect light to make room.
            EffectLightInstance victim = null;
            for (EffectLightInstance light : lights) {
                if (!light.highPriority && (victim == null || light.age > victim.age)) {
                    victim = light;
                }
            }
            if (victim == null) {
                return;
            }
            lightPool.release(victim.handle);
            lights.remove(victim);
        }
        float scale = Math.max(0.1f, spawn.scale);
        float radius = spec.radius() * scale;
        float intensity = spec.intensity() * scale;
        int handle = lightPool.allocate(
            spawn.x,
            spawn.y,
            radius,
            spec.color(),
            intensity,
            Light.DEFAULT_FALLOFF,
            true);
        if (handle == LightPool.INVALID_HANDLE) {
            return;
        }
        lights.add(new EffectLightInstance(
            handle,
            spawn.x,
            spawn.y,
            radius,
            spec,
            intensity,
            spec.endIntensity() * scale,
            spawn.seed));
    }

    private void updateLights(float deltaSeconds) {
        Iterator<EffectLightInstance> iterator = lights.iterator();
        while (iterator.hasNext()) {
            EffectLightInstance light = iterator.next();
            light.age += deltaSeconds;
            float duration = light.spec.durationSeconds();
            if (light.age >= duration) {
                lightPool.release(light.handle);
                iterator.remove();
                continue;
            }
            float lifeT = light.age / Math.max(0.0001f, duration);
            // White → orange → transparent over the duration, with the recipe's deterministic
            // flicker on top (a fire zone's flicker, an explosion's none).
            float flicker = 1f + light.spec.flickerAmplitude()
                * (float) Math.sin(light.age * 24.0 + light.flickerPhase);
            float intensity = Math.max(0f,
                lerp(light.startIntensity, light.endIntensity, lifeT) * flicker);
            scratchColor.set(
                lerp(light.spec.color().r, light.spec.endColor().r, lifeT),
                lerp(light.spec.color().g, light.spec.endColor().g, lifeT),
                lerp(light.spec.color().b, light.spec.endColor().b, lifeT),
                1f);
            lightPool.update(
                light.handle,
                light.x,
                light.y,
                light.radius,
                scratchColor,
                intensity,
                Light.DEFAULT_FALLOFF,
                true);
        }
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    /** One scheduled phase: an emitter or a light, due at {@code fireTime} on the effect clock. */
    private static final class PendingPhase {
        final float fireTime;
        final EffectSpawn spawn;
        final EmitterConfig emitter;
        final EmitterLibrary.EffectLight light;

        PendingPhase(
                float fireTime,
                EffectSpawn spawn,
                EmitterConfig emitter,
                EmitterLibrary.EffectLight light) {
            this.fireTime = fireTime;
            this.spawn = spawn;
            this.emitter = emitter;
            this.light = light;
        }
    }

    /** One live attached light: its pool handle plus the animation state the pool cannot hold. */
    private static final class EffectLightInstance {
        final int handle;
        final float x;
        final float y;
        final float radius;
        final EmitterLibrary.EffectLight spec;
        final float startIntensity;
        final float endIntensity;
        final float flickerPhase;
        float age;

        EffectLightInstance(
                int handle,
                float x,
                float y,
                float radius,
                EmitterLibrary.EffectLight spec,
                float startIntensity,
                float endIntensity,
                float flickerPhase) {
            this.handle = handle;
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.spec = spec;
            this.startIntensity = startIntensity;
            this.endIntensity = endIntensity;
            this.flickerPhase = flickerPhase * 6.2831855f;
            this.age = 0f;
        }
    }
}
