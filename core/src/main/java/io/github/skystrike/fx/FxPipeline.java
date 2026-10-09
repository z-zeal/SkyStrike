package io.github.skystrike.fx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.Disposable;
import io.github.skystrike.fx.gl.RenderTarget;
import io.github.skystrike.fx.gl.ShaderLibrary;
import io.github.skystrike.fx.lighting.Light;
import io.github.skystrike.fx.lighting.LightPass;
import io.github.skystrike.fx.lighting.LightPool;
import io.github.skystrike.fx.lighting.SmokeVolumes;
import io.github.skystrike.fx.lighting.VisibilitySystem;
import io.github.skystrike.fx.lighting.VisibilitySystem.ObserverState;
import io.github.skystrike.fx.particle.FxClock;
import io.github.skystrike.fx.particle.GpuParticleSystem;
import io.github.skystrike.fx.post.BlindnessPass;
import io.github.skystrike.fx.post.CompositePass;
import io.github.skystrike.fx.sdf.SdfCache;
import io.github.skystrike.fx.sdf.SdfDebugView;
import io.github.skystrike.fx.sdf.SdfTexture;
import io.github.skystrike.render.GameCamera;
import io.github.skystrike.shared.config.VisionConfig;
import io.github.skystrike.shared.effect.EffectSpawn;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.net.s2c.PacketEffectSpawn;
import io.github.skystrike.shared.vision.SmokeVolume;
import io.github.skystrike.shared.vision.VisionMath;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Orchestrates multi-pass rendering for scene geometry, visibility cones, additive player lights,
 * fog composite and debug overlays.
 */
public final class FxPipeline implements Disposable {

    private static final int PLAYER_LIGHT_CAPACITY = 64;
    private static final Color PLAYER_LIGHT_COLOUR = new Color(1f, 0.94f, 0.82f, 1f);

    private final ShaderLibrary shaders;
    private final SdfTexture sdfTexture;
    private final RenderTarget sceneFbo;
    private final VisibilitySystem visibilitySystem;
    private final LightPool lightPool;
    private final LightPass lightPass;
    private final CompositePass compositePass;
    private final SdfDebugView sdfDebugView;
    private final SmokeVolumes smokeVolumes;
    /** M7: particles and attached effect lights, sharing the light pool and the SDF field. */
    private final GpuParticleSystem particles;
    /** M7: the flashbang whiteout post pass. */
    private final BlindnessPass blindnessPass;
    /**
     * Phase 9: the one effect-event queue. Producers enqueue received batches here; the frame
     * update drains it once and hands each event to both consumers — particles and audio.
     */
    private final FxEventQueue effectQueue = new FxEventQueue();
    private final Map<Integer, Integer> remotePlayerLightHandles = new HashMap<>();

    /** Optional audio consumer; null until a screen installs one, and null again on disposal. */
    private EffectEventListener effectListener;

    private int localPlayerLightHandle = LightPool.INVALID_HANDLE;
    private int screenWidth;
    private int screenHeight;
    private boolean disposed;

    public FxPipeline(int screenWidth, int screenHeight) {
        this.screenWidth = Math.max(1, screenWidth);
        this.screenHeight = Math.max(1, screenHeight);

        this.shaders = new ShaderLibrary();
        this.sdfTexture = new SdfTexture(SdfCache.loadStandard());
        this.sceneFbo = new RenderTarget(this.screenWidth, this.screenHeight);
        this.visibilitySystem = new VisibilitySystem(this.shaders, this.screenWidth, this.screenHeight);
        this.lightPool = new LightPool(PLAYER_LIGHT_CAPACITY);
        this.lightPass = new LightPass(this.shaders, this.screenWidth, this.screenHeight);
        this.compositePass = new CompositePass(this.shaders);
        this.sdfDebugView = new SdfDebugView(this.shaders);
        this.smokeVolumes = new SmokeVolumes();
        this.particles = new GpuParticleSystem(
            new FxClock(), new FxBudget(), this.lightPool, this.sdfTexture.field(), this.shaders);
        this.blindnessPass = new BlindnessPass(this.shaders);
    }

    /** Resizes framebuffer targets to match the window dimensions. */
    public void resize(int width, int height) {
        this.screenWidth = Math.max(1, width);
        this.screenHeight = Math.max(1, height);
        sceneFbo.resize(this.screenWidth, this.screenHeight);
        visibilitySystem.resize(this.screenWidth, this.screenHeight);
        lightPass.resize(this.screenWidth, this.screenHeight);
    }

    /**
     * Begins the scene rendering pass into the full-resolution scene buffer.
     */
    public void beginScene() {
        sceneFbo.begin();
        sceneFbo.clear(0.035f, 0.045f, 0.07f, 1f);
    }

    /**
     * Ends the scene rendering pass.
     */
    public void endScene() {
        sceneFbo.end();
    }

    /**
     * Executes the half-resolution visibility pass for all observers.
     *
     * @param hardShadows {@code r_shadows} off (build plan M3 §4, F3): hard edges instead of the
     *     default SDF soft shadows.
     */
    public void renderVisibility(GameCamera camera, List<ObserverState> observers, boolean hardShadows) {
        visibilitySystem.render(camera, sdfTexture, observers, smokeVolumes, hardShadows);
    }

    /**
     * Synchronizes the bounded player-light pool with the current frame's player states.
     *
     * <p>Snapshots intentionally contain remote player state regardless of local field of view.
     * Never create a light for every snapshot entry: a hidden enemy's light would reveal the very
     * position the fog is meant to conceal. A remote source is admitted only when its centre is
     * clearly inside the local cone and has hard line of sight through the shared visibility math;
     * the light shader then applies the rendered per-pixel visibility mask as a second guard.
     */
    public void syncPlayerLights(
            Player localPlayer,
            List<Player> remotePlayers,
            ArenaMap arena,
            float visionReach,
            boolean enabled,
            float radius,
            float intensity,
            boolean castsShadow) {
        if (!enabled || localPlayer == null || !localPlayer.alive) {
            clearPlayerLights();
            return;
        }

        localPlayerLightHandle = upsertPlayerLight(
                localPlayerLightHandle,
                localPlayer.centerX(),
                localPlayer.centerY(),
                radius,
                intensity,
                castsShadow);

        Set<Integer> visibleRemoteIds = new HashSet<>();
        if (remotePlayers != null && arena != null) {
            List<SmokeVolume> smoke = smokeVolumes.all();
            for (Player remote : remotePlayers) {
                if (remote == null
                        || !remote.alive
                        || remote.id == localPlayer.id
                        || !isClearlyVisibleSource(localPlayer, remote, arena, smoke, visionReach)) {
                    continue;
                }

                Integer oldHandle = remotePlayerLightHandles.get(remote.id);
                int handle = upsertPlayerLight(
                        oldHandle == null ? LightPool.INVALID_HANDLE : oldHandle,
                        remote.centerX(),
                        remote.centerY(),
                        radius,
                        intensity,
                        castsShadow);
                if (handle != LightPool.INVALID_HANDLE) {
                    remotePlayerLightHandles.put(remote.id, handle);
                    visibleRemoteIds.add(remote.id);
                }
            }
        }

        Iterator<Map.Entry<Integer, Integer>> iterator = remotePlayerLightHandles.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Integer> entry = iterator.next();
            if (!visibleRemoteIds.contains(entry.getKey())) {
                lightPool.release(entry.getValue());
                iterator.remove();
            }
        }
    }

    /** Executes the half-resolution additive light pass after visibility has been rendered. */
    public void renderLights(GameCamera camera, Player localPlayer, boolean hardShadows) {
        lightPass.render(
                camera,
                sdfTexture,
                visibilitySystem.getVisibilityTexture(),
                lightPool,
                localPlayerLightHandle,
                localPlayer,
                hardShadows);
    }

    /**
     * Composites the scene with visibility and the already visibility-gated light buffer onto the
     * default backbuffer.
     */
    public void composite() {
        Gdx.gl.glViewport(0, 0, screenWidth, screenHeight);
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        compositePass.render(
                sceneFbo.getTexture(),
                visibilitySystem.getVisibilityTexture(),
                lightPass.texture());
    }

    // --- M7: the FX event channel and particles -------------------------------------------------

    /**
     * Enqueues a received effect batch. {@link #updateFx(float)} drains the queue on the render
     * thread and hands each event to the visuals and to audio; nothing is ever spawned from the
     * network thread.
     */
    public void enqueueEffects(PacketEffectSpawn packet) {
        if (packet == null || packet.effects == null || packet.effects.isEmpty()) {
            return;
        }
        effectQueue.enqueueAll(packet.effects);
    }

    /**
     * Installs the audio consumer of the same effect events.
     *
     * <p>This is the whole of the Phase 9 wiring: one gameplay event, one queue, one drain, and
     * both presentation layers react to the same object in the same frame. Installing a second
     * listener replaces the first — there is one audio layer, not a stack of them.
     */
    public void setEffectListener(EffectEventListener listener) {
        this.effectListener = listener;
    }

    /**
     * One frame of FX update: drains the one event queue, schedules the visuals for each event,
     * lets the audio layer react to the same events, then advances the effect clock, fires due
     * phases, animates the attached lights and steps the CPU tier. Call before the scene pass.
     */
    public void updateFx(float deltaSeconds) {
        effectQueue.drain(this::dispatchEffect);
        particles.update(deltaSeconds);
    }

    /**
     * The one place where both layers meet. Visuals first — the particle phases are scheduled
     * before the sound is asked for, so a listener can never delay or reorder the frame's effects.
     */
    private void dispatchEffect(EffectSpawn spawn) {
        if (spawn == null || spawn.type == null) {
            return;
        }
        particles.schedule(spawn);
        if (effectListener != null) {
            effectListener.onEffect(spawn);
        }
    }

    /**
     * The alpha particle batch, drawn inside the scene pass so the fog composite darkens smoke,
     * dust and debris. Includes the CPU-tier casings, which are scene content too.
     */
    public void renderAlphaParticles(GameCamera camera) {
        particles.renderAlphaParticles(camera);
    }

    /**
     * The additive particle batch, drawn after the composite so sparks, fire and flash glow
     * through darkness. Gated by the visibility texture, exactly like the light pass.
     */
    public void renderAdditiveParticles(GameCamera camera) {
        particles.renderAdditiveParticles(camera, visibilitySystem.getVisibilityTexture());
    }

    /**
     * The flashbang whiteout, drawn above the composite and the additive particles and below the
     * HUD. The intensity is the shared {@code StunMath} curve over the local player's blind state.
     */
    public void renderBlindness(float blindIntensity) {
        blindnessPass.render(blindIntensity, particles.clock().time());
    }

    /** Applies a new effects quality tier; buffers resize when the tier's caps move. */
    public void setQualityTier(FxBudget.Tier tier) {
        particles.setTier(tier);
    }

    /** The fx_debug overlay's view: live particle and light counts against the tier budget. */
    public FxStats fxStats() {
        return particles.stats();
    }

    public GpuParticleSystem particles() {
        return particles;
    }

    private int upsertPlayerLight(
            int handle,
            float x,
            float y,
            float radius,
            float intensity,
            boolean castsShadow) {
        if (lightPool.update(
                handle,
                x,
                y,
                radius,
                PLAYER_LIGHT_COLOUR,
                intensity,
                Light.DEFAULT_FALLOFF,
                castsShadow)) {
            return handle;
        }
        return lightPool.allocate(
                x,
                y,
                radius,
                PLAYER_LIGHT_COLOUR,
                intensity,
                Light.DEFAULT_FALLOFF,
                castsShadow);
    }

    private boolean isClearlyVisibleSource(
            Player observer,
            Player target,
            ArenaMap arena,
            List<SmokeVolume> smoke,
            float visionReach) {
        float visibility = VisionMath.calculateVisibility(
                observer.eyeX(),
                observer.eyeY(),
                observer.aimAngle,
                visionReach,
                target.centerX(),
                target.centerY(),
                arena,
                smoke);
        // The shared vision function retains a faint peripheral floor outside the cone. A light
        // source must clear that floor to ensure the light does not identify a hidden remote.
        return visibility > VisionConfig.PERIPHERAL_FLOOR + 0.001f;
    }

    private void clearPlayerLights() {
        lightPool.clear();
        remotePlayerLightHandles.clear();
        localPlayerLightHandle = LightPool.INVALID_HANDLE;
    }

    /**
     * Draws the SDF debug overlay.
     */
    public void renderSdfDebug(GameCamera camera) {
        sdfDebugView.render(camera, sdfTexture);
    }

    public SmokeVolumes smokeVolumes() {
        return smokeVolumes;
    }

    public SdfTexture sdfTexture() {
        return sdfTexture;
    }

    public VisibilitySystem visibilitySystem() {
        return visibilitySystem;
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        effectQueue.clear();
        effectListener = null;
        clearPlayerLights();
        particles.dispose();
        blindnessPass.dispose();
        sceneFbo.dispose();
        visibilitySystem.dispose();
        lightPass.dispose();
        compositePass.dispose();
        sdfDebugView.dispose();
        sdfTexture.dispose();
        shaders.dispose();
    }
}
