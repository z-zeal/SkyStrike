package io.github.skystrike.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.utils.Disposable;
import io.github.skystrike.shared.audio.AudioBus;
import io.github.skystrike.shared.audio.SoundPriority;
import io.github.skystrike.shared.audio.SoundSpec;
import io.github.skystrike.shared.audio.SpatialAudio;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The client's one sound owner (roadmap Phase 9, project structure §8).
 *
 * <p>Nothing else in the game is allowed to touch {@code Gdx.audio}: the server cannot reach it,
 * gameplay never sees it, and the two presentation layers that do — the world event listener and
 * the weapon-state bridge — go through {@link SoundSpec} requests, so an asset path, a bus and a
 * voice cap are decided by the catalogue rather than by whoever happens to be calling.
 *
 * <p><b>Three jobs.</b>
 *
 * <ul>
 *   <li><b>Buses.</b> Master, music and effects are the sliders the settings dialog already
 *       exposes; every request is mixed through {@link AudioBus#gain} in one place, and a slider
 *       move also re-applies to any live loop.</li>
 *   <li><b>Pooling.</b> A phone will not mix five players' automatic fire, a grenade and every
 *       casing on screen. The pool bounds per-asset voices and per-bus voices, and when a cap is
 *       reached the incoming request plays only if it outranks the weakest voice holding a slot
 *       ({@link SoundPriority}) — it then takes that slot. Loops live in their own small pool, so
 *       an impact tick can never steal the stun ring's voice.</li>
 *   <li><b>Spatialisation.</b> Gain and pan come from {@link SpatialAudio}; the caller supplies
 *       only the listener's position (once per frame), the source's, and whether terrain blocks
 *       the line between them.</li>
 * </ul>
 *
 * <p><b>Failure policy.</b> A missing or unreadable asset is logged once and then silent forever —
 * never an exception on the render thread, never a retry every frame. A request that is
 * out of range, inaudible at the current slider settings, or rejected by the pool is simply not
 * played; and if {@code Gdx.audio} is absent (headless, a unit test, a stripped backend) every
 * call is a safe no-op.
 *
 * <p><b>Voice lifetime.</b> libGDX's {@code Sound} reports no duration, so a voice is booked
 * against the spec's asset length and retired on {@link #update(float)}. The approximation is
 * deliberate: it only ever costs a slot for a fraction of a second.
 */
public final class AudioSystem implements Disposable {

    /** Returned by {@link #loop(SoundSpec)} when no looping voice could be started. */
    public static final long NO_LOOP = -1L;

    /** Highest number of simultaneous loops; four is already generous for a status effect. */
    private static final int MAX_LOOPS = 4;

    /** Extra life booked per one-shot voice, covering backend latency and rounding. */
    private static final float VOICE_SLACK_SECONDS = 0.15f;

    /** Below this mixed gain a request is not worth a voice. */
    private static final float MIN_MIX_GAIN = 0.001f;

    private static final String LOG_TAG = "SkyStrike";

    /** One playing one-shot: enough to expire it, count it against a cap, and steal its slot. */
    private static final class Voice {

        private final Sound sound;
        private final long id;
        private final String path;
        private final AudioBus bus;
        private final SoundPriority priority;
        private final float expiresAt;

        private Voice(
                Sound sound,
                long id,
                String path,
                AudioBus bus,
                SoundPriority priority,
                float expiresAt) {
            this.sound = sound;
            this.id = id;
            this.path = path;
            this.bus = bus;
            this.priority = priority;
            this.expiresAt = expiresAt;
        }
    }

    /** One live loop: the voice, its catalogue entry, and the envelope the caller is driving. */
    private static final class Loop {

        private final Sound sound;
        private final long id;
        private final SoundSpec spec;
        private float level;
        private float pitch = 1f;

        private Loop(Sound sound, long id, SoundSpec spec) {
            this.sound = sound;
            this.id = id;
            this.spec = spec;
        }
    }

    private final Map<String, Sound> sounds = new HashMap<>();
    private final Set<String> unavailable = new HashSet<>();
    private final List<Voice> voices = new ArrayList<>();
    private final Map<Long, Loop> loops = new HashMap<>();

    private float masterVolume = 1f;
    private float musicVolume = 1f;
    private float effectsVolume = 1f;
    private float listenerX;
    private float listenerY;
    private float clockSeconds;
    private long nextLoopHandle = 1L;

    private int startedVoices;
    private int stolenVoices;
    private int droppedRequests;
    private int missingAssets;
    private boolean disposed;

    /** One mixer per client. Building a second one doubles the cache and the pool for no benefit. */
    public AudioSystem() {
    }

    // --- buses ---------------------------------------------------------------------------------

    /** Applies the three client sliders and re-derives every live loop's volume from them. */
    public void setVolumes(float masterVolume, float musicVolume, float effectsVolume) {
        this.masterVolume = clamp(masterVolume);
        this.musicVolume = clamp(musicVolume);
        this.effectsVolume = clamp(effectsVolume);
        for (Loop loop : loops.values()) {
            applyLoop(loop);
        }
    }

    /** Moves the local player's ears. Spatial placement is measured from here. */
    public void updateListener(float x, float y) {
        if (Float.isFinite(x) && Float.isFinite(y)) {
            listenerX = x;
            listenerY = y;
        }
    }

    public float listenerX() {
        return listenerX;
    }

    public float listenerY() {
        return listenerY;
    }

    /**
     * The clock step: retires finished one-shots so their slots come back.
     *
     * <p>Call once per frame. If it is never called the pool fills and stops admitting sounds,
     * which is the failure this method exists to prevent.
     */
    public void update(float deltaSeconds) {
        if (!Float.isFinite(deltaSeconds) || deltaSeconds < 0f) {
            return;
        }
        clockSeconds += deltaSeconds;
        for (int index = voices.size() - 1; index >= 0; index--) {
            if (voices.get(index).expiresAt <= clockSeconds) {
                voices.remove(index);
            }
        }
    }

    // --- one-shots -----------------------------------------------------------------------------

    /** Plays a head-locked sound: interface feedback, and the local player's own weapon handling. */
    public boolean play(SoundSpec spec) {
        if (spec == null || spec.isSilent() || !admit(spec)) {
            return false;
        }
        Sound sound = soundFor(spec.path());
        if (sound == null) {
            droppedRequests++;
            return false;
        }
        float gain = spec.gain() * busGain(spec.bus());
        return start(sound, spec, gain, 1f, 0f);
    }

    /**
     * Plays a world sound for the listener's current position.
     *
     * @param occluded  true when terrain blocks the straight line from the listener to the source
     * @param pitch     pitch multiplier; the deterministic per-event jitter arrives this way
     *                  ({@link SoundSpec#pitchForSeed(int)})
     * @param gainScale extra gain multiplier for events whose size varies — a smoke cloud scaled
     *                  to its zone radius is louder than a small one. {@code 1} for everything
     *                  whose size is fixed.
     */
    public boolean playAt(
            SoundSpec spec, float x, float y, boolean occluded, float pitch, float gainScale) {
        if (spec == null || spec.isSilent()) {
            return false;
        }
        SpatialAudio.Placement placement = SpatialAudio.place(
            listenerX,
            listenerY,
            x,
            y,
            spec.referenceDistance(),
            spec.audibleRadius(),
            occluded);
        if (!placement.audible()) {
            // Not a drop: the source is simply out of reach, and counting it as dropped would make
            // the debug line look like the pool is starving when nothing is wrong.
            return false;
        }
        if (!admit(spec)) {
            return false;
        }
        Sound sound = soundFor(spec.path());
        if (sound == null) {
            droppedRequests++;
            return false;
        }
        float scale = Float.isFinite(gainScale) ? Math.max(0f, gainScale) : 1f;
        float gain = spec.gain() * placement.gain() * scale * busGain(spec.bus());
        return start(sound, spec, gain, pitch, placement.pan());
    }

    // --- loops ---------------------------------------------------------------------------------

    /**
     * Starts a looping sound and returns a handle for it, or {@link #NO_LOOP}.
     *
     * <p>The handle is the caller's to keep: the tinnitus effect drives the stun ring's level and
     * pitch through it every frame, and stops it when the ring fades. A loop never takes a
     * one-shot's voice and is never stolen.
     */
    public long loop(SoundSpec spec) {
        if (disposed || spec == null || spec.isSilent() || !spec.looping() || loops.size() >= MAX_LOOPS) {
            return NO_LOOP;
        }
        Sound sound = soundFor(spec.path());
        if (sound == null) {
            return NO_LOOP;
        }
        long id = sound.loop(clamp(spec.gain() * busGain(spec.bus())), 1f, 0f);
        if (id < 0L) {
            return NO_LOOP;
        }
        long handle = nextLoopHandle++;
        loops.put(handle, new Loop(sound, id, spec));
        return handle;
    }

    /** Sets a loop's level and pitch; the bus and master gains are re-applied on top. */
    public void setLoop(long handle, float level, float pitch) {
        Loop loop = loops.get(handle);
        if (loop == null) {
            return;
        }
        loop.level = clamp(level);
        loop.pitch = clampPitch(pitch);
        applyLoop(loop);
    }

    /** Stops a loop and forgets its handle. Unknown handles are ignored. */
    public void stopLoop(long handle) {
        Loop loop = loops.remove(handle);
        if (loop != null) {
            loop.sound.stop(loop.id);
        }
    }

    // --- loading -------------------------------------------------------------------------------

    /**
     * Loads an asset without playing it, so a sound the player has not earned yet costs nothing
     * when it first fires. Used for the stun ring at match start.
     */
    public boolean prepare(SoundSpec spec) {
        if (disposed || spec == null || spec.isSilent()) {
            return false;
        }
        return soundFor(spec.path()) != null;
    }

    // --- diagnostics ---------------------------------------------------------------------------

    /** Live one-shot voices, the number the pool caps. */
    public int activeVoices() {
        return voices.size();
    }

    public int loopCount() {
        return loops.size();
    }

    /** Unique assets currently loaded; useful to prove ownership without an audio device. */
    public int loadedSoundCount() {
        return sounds.size();
    }

    /** Assets that failed to load and were disabled for the session. */
    public int missingAssetCount() {
        return missingAssets;
    }

    public int startedVoices() {
        return startedVoices;
    }

    public int stolenVoices() {
        return stolenVoices;
    }

    public int droppedRequests() {
        return droppedRequests;
    }

    /** A one-line summary for the debug overlay. */
    public String statusLine() {
        return String.format(
            "sfx: voices %d  loops %d  assets %d  missing %d  started %d  stolen %d  dropped %d",
            voices.size(),
            loops.size(),
            sounds.size(),
            missingAssets,
            startedVoices,
            stolenVoices,
            droppedRequests);
    }

    // --- pooling -------------------------------------------------------------------------------

    /**
     * Whether the pool can take this request. The asset cap is checked first, then the bus cap;
     * either one may be satisfied by taking the weakest voice already holding a slot there.
     */
    private boolean admit(SoundSpec spec) {
        if (!admitOnPath(spec) || !admitOnBus(spec)) {
            droppedRequests++;
            return false;
        }
        return true;
    }

    private boolean admitOnPath(SoundSpec spec) {
        if (countVoices(spec.path(), null) < spec.maxVoices()) {
            return true;
        }
        return stealWeakest(spec.path(), null, spec.priority());
    }

    private boolean admitOnBus(SoundSpec spec) {
        if (countVoices(null, spec.bus()) < busCapacity(spec.bus())) {
            return true;
        }
        return stealWeakest(null, spec.bus(), spec.priority());
    }

    /** Stops the weakest voice matching the filter, when the incoming priority outranks it. */
    private boolean stealWeakest(String path, AudioBus bus, SoundPriority incoming) {
        int weakestIndex = -1;
        SoundPriority weakest = null;
        for (int index = 0; index < voices.size(); index++) {
            Voice voice = voices.get(index);
            if (path != null && !path.equals(voice.path)) {
                continue;
            }
            if (bus != null && voice.bus != bus) {
                continue;
            }
            if (weakest == null || voice.priority.rank() < weakest.rank()) {
                weakest = voice.priority;
                weakestIndex = index;
            }
        }
        if (weakestIndex < 0 || !incoming.outranks(weakest)) {
            return false;
        }
        Voice stolen = voices.remove(weakestIndex);
        stolen.sound.stop(stolen.id);
        stolenVoices++;
        return true;
    }

    private int countVoices(String path, AudioBus bus) {
        int count = 0;
        for (Voice voice : voices) {
            if (path != null && !path.equals(voice.path)) {
                continue;
            }
            if (bus != null && voice.bus != bus) {
                continue;
            }
            count++;
        }
        return count;
    }

    /** How many one-shot voices a bus may hold at once. Loops are outside this pool. */
    private static int busCapacity(AudioBus bus) {
        return switch (bus) {
            case EFFECTS -> 24;
            case MUSIC -> 2;
            case UI -> 4;
        };
    }

    // --- playback ------------------------------------------------------------------------------

    private boolean start(Sound sound, SoundSpec spec, float gain, float pitch, float pan) {
        float mixed = clamp(gain);
        if (mixed <= MIN_MIX_GAIN) {
            return false;
        }
        float playablePitch = clampPitch(pitch);
        long id = sound.play(mixed, playablePitch, clampPan(pan));
        if (id < 0L) {
            return false;
        }
        startedVoices++;
        float life = spec.durationSeconds() / Math.max(SoundSpec.MIN_PITCH, playablePitch)
            + VOICE_SLACK_SECONDS;
        voices.add(new Voice(
            sound, id, spec.path(), spec.bus(), spec.priority(), clockSeconds + life));
        return true;
    }

    private void applyLoop(Loop loop) {
        float gain = clamp(loop.spec.gain() * loop.level * busGain(loop.spec.bus()));
        loop.sound.setVolume(loop.id, gain);
        loop.sound.setPitch(loop.id, clampPitch(loop.pitch));
    }

    private float busGain(AudioBus bus) {
        return bus.gain(masterVolume, musicVolume, effectsVolume);
    }

    /**
     * The loader. One {@code Sound} per packaged path, loaded once, never reloaded after a failure.
     */
    private Sound soundFor(String path) {
        if (path == null || unavailable.contains(path)) {
            return null;
        }
        Sound cached = sounds.get(path);
        if (cached != null) {
            return cached;
        }
        if (Gdx.audio == null || Gdx.files == null) {
            return null;
        }
        try {
            Sound loaded = Gdx.audio.newSound(Gdx.files.internal(path));
            sounds.put(path, loaded);
            return loaded;
        } catch (RuntimeException missing) {
            unavailable.add(path);
            missingAssets++;
            if (Gdx.app != null) {
                Gdx.app.error(LOG_TAG, "sound asset unavailable, disabling it: " + path, missing);
            }
            return null;
        }
    }

    /** Stops everything and disposes every cached asset exactly once. */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        for (Loop loop : loops.values()) {
            loop.sound.stop(loop.id);
        }
        loops.clear();
        for (Voice voice : voices) {
            voice.sound.stop(voice.id);
        }
        voices.clear();
        for (Sound sound : sounds.values()) {
            sound.stop();
            sound.dispose();
        }
        sounds.clear();
        unavailable.clear();
    }

    private static float clamp(float value) {
        if (!Float.isFinite(value)) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, value));
    }

    private static float clampPan(float value) {
        if (!Float.isFinite(value)) {
            return 0f;
        }
        return Math.max(-1f, Math.min(1f, value));
    }

    /** libGDX only supports a pitch multiplier between 0.5 and 2.0 and does not clamp for you. */
    private static float clampPitch(float value) {
        if (!Float.isFinite(value)) {
            return 1f;
        }
        return Math.max(SoundSpec.MIN_PITCH, Math.min(SoundSpec.MAX_PITCH, value));
    }
}
