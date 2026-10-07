package io.github.skystrike.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.utils.Disposable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The client sound owner. Sounds are loaded lazily once per packaged path and disposed once with
 * the owning game screen; no gameplay or network code can reach this class from the server.
 */
public final class AudioSystem implements Disposable {

    private final Map<String, Sound> sounds = new LinkedHashMap<>();
    private float masterVolume = 1f;
    private float effectsVolume = 1f;
    private boolean disposed;

    public AudioSystem() {
    }

    /** Updates the two existing client volume buses without loading any sound. */
    public void setVolumes(float masterVolume, float effectsVolume) {
        this.masterVolume = clamp(masterVolume);
        this.effectsVolume = clamp(effectsVolume);
    }

    /** Plays a cached sound with already-computed spatial volume and pan. */
    public long play(String path, float volume, float pitch, float pan) {
        if (disposed || path == null || Gdx.audio == null || Gdx.files == null) {
            return -1L;
        }
        Sound sound = sounds.get(path);
        if (sound == null) {
            sound = Gdx.audio.newSound(Gdx.files.internal(path));
            sounds.put(path, sound);
        }
        float gain = clamp(volume) * masterVolume * effectsVolume;
        return sound.play(gain, Math.max(0.01f, pitch), clampPan(pan));
    }

    /** Stops and disposes every cached sound exactly once. */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        for (Sound sound : sounds.values()) {
            sound.stop();
            sound.dispose();
        }
        sounds.clear();
    }

    /** Number of unique asset paths currently loaded, useful to inspect ownership without audio. */
    public int loadedSoundCount() {
        return sounds.size();
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
}
