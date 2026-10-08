package io.github.skystrike.audio;

import com.badlogic.gdx.utils.Disposable;
import io.github.skystrike.shared.audio.SoundSpec;
import io.github.skystrike.shared.audio.TinnitusMath;

/**
 * The stun ring: the loop a blinded player hears, and the tail that outlives the flash
 * (mechanics §6.1 "plus an audio ringing effect", roadmap Phase 9).
 *
 * <p>It is the one sound in the game that is <b>not</b> driven by the effect-event channel, and
 * deliberately so. A flashbang's bang is a world event everyone nearby hears; the ringing is a
 * <em>status effect on this player</em> — it lasts as long as their blindness lasts, it is inside
 * their head, and nobody else can hear it. So it is driven per frame from the same authoritative
 * blind state the whiteout pass reads ({@code ClientSession.blindIntensity()}, itself
 * {@code StunMath.blindIntensity}), which keeps the eye and the ear on one clock: the whiteout and
 * the ring decay together and neither can outlive the other.
 *
 * <p>The envelope is in {@link TinnitusMath}. This class owns the two things that need an audio
 * device: one looping voice, and writing its level and pitch every frame. The voice is started
 * lazily the first frame the ring is audible, stopped when it is not, and it lives in the mixer's
 * separate loop pool so no impact tick can steal it.
 */
public final class TinnitusEffect implements Disposable {

    private final AudioSystem audio;
    private final SoundCatalog catalog;

    private long handle = AudioSystem.NO_LOOP;
    private float level;
    private boolean unavailable;

    public TinnitusEffect(AudioSystem audio, SoundCatalog catalog) {
        if (audio == null || catalog == null) {
            throw new IllegalArgumentException("audio system and catalogue are required");
        }
        this.audio = audio;
        this.catalog = catalog;
    }

    /**
     * One frame of the ring.
     *
     * @param deltaSeconds    frame time
     * @param blindIntensity  the local player's current blind intensity, the same value the
     *                        whiteout pass renders
     */
    public void update(float deltaSeconds, float blindIntensity) {
        level = TinnitusMath.step(level, TinnitusMath.ringGain(blindIntensity), deltaSeconds);
        if (!TinnitusMath.audible(level)) {
            stop();
            return;
        }
        if (handle == AudioSystem.NO_LOOP) {
            if (unavailable) {
                // The asset failed to load once; retrying every frame would only log at the frame
                // rate. The ring is simply absent for this session.
                level = 0f;
                return;
            }
            SoundSpec spec = catalog.tinnitusSpec();
            handle = audio.loop(spec);
            if (handle == AudioSystem.NO_LOOP) {
                unavailable = true;
                level = 0f;
                return;
            }
        }
        audio.setLoop(handle, level, TinnitusMath.ringPitch(level));
    }

    /** The current ring level, {@code 0} when silent. Exposed for the debug overlay. */
    public float level() {
        return level;
    }

    /** True while the loop is playing. */
    public boolean ringing() {
        return handle != AudioSystem.NO_LOOP;
    }

    /** Stops the loop and clears the envelope; called when a match ends or the session resets. */
    public void stop() {
        if (handle != AudioSystem.NO_LOOP) {
            audio.stopLoop(handle);
            handle = AudioSystem.NO_LOOP;
        }
        level = 0f;
    }

    @Override
    public void dispose() {
        stop();
    }
}
