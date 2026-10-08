package io.github.skystrike.audio;

import io.github.skystrike.shared.audio.SoundSpec;
import io.github.skystrike.shared.audio.SpatialAudio;
import io.github.skystrike.shared.effect.EffectSpawn;

/**
 * The audio half of the effect-event channel (roadmap Phase 9).
 *
 * <p><b>One event, two layers.</b> The server raises a closed vocabulary of effect events and
 * culls them per recipient; the client drains them into one queue on the render thread. The
 * pipeline hands every drained event to the particle system <em>and</em> to this class, in the same
 * frame, from the same object: a frag is a flash, a shockwave, a fireball and a boom, and the two
 * layers cannot drift apart because there is nothing to keep in step. Gameplay never knows audio
 * exists — which is exactly the rule the roadmap sets for this phase.
 *
 * <p>It is a reader of the catalogue, not a mixer: {@link SoundCatalog} says what an event sounds
 * like, {@link AudioSystem} decides whether it gets a voice, how loud and where. This class adds
 * only what the event itself carries — its position, its size, and its server-assigned seed, which
 * turns into a pitch that every client hearing the same shot will reproduce identically.
 *
 * <p>Silent events are counted, not ignored blindly: {@code MUZZLE_FLASH} is deliberately silent
 * (the weapon-specific report already comes from authoritative snapshots), and the counter is what
 * lets the debug line show that the omission is a decision rather than a missing row.
 */
public final class EffectAudio {

    /** Gain multiplier for the smallest and largest event scales; a cloud twice as wide, not twice as loud. */
    private static final float MIN_SCALE_GAIN = 0.75f;
    private static final float MAX_SCALE_GAIN = 1.15f;
    private static final float SCALE_GAIN_RANGE = MAX_SCALE_GAIN - MIN_SCALE_GAIN;

    private final AudioSystem audio;
    private final SoundCatalog catalog;
    private final OcclusionTest occlusion;

    private int playedEvents;
    private int silentEvents;
    private int rejectedEvents;

    public EffectAudio(AudioSystem audio, SoundCatalog catalog, OcclusionTest occlusion) {
        if (audio == null || catalog == null) {
            throw new IllegalArgumentException("audio system and catalogue are required");
        }
        this.audio = audio;
        this.catalog = catalog;
        this.occlusion = occlusion == null ? OcclusionTest.NONE : occlusion;
    }

    /**
     * Reacts to one drained effect request. Render thread only, in the same frame the particles
     * for the same event are scheduled.
     */
    public void onEffect(EffectSpawn spawn) {
        if (spawn == null || spawn.type == null) {
            return;
        }
        SoundSpec spec = catalog.specFor(spawn.type);
        if (spec == null || spec.isSilent()) {
            silentEvents++;
            return;
        }
        // Cheapest test first: a source past its own reach is never played, so there is no reason
        // to spend a line-of-sight query on it.
        boolean inReach = SpatialAudio.distance(
            audio.listenerX(), audio.listenerY(), spawn.x, spawn.y) < spec.audibleRadius();
        boolean blocked = inReach
            && occlusion.isBlocked(audio.listenerX(), audio.listenerY(), spawn.x, spawn.y);
        boolean started = audio.playAt(
            spec,
            spawn.x,
            spawn.y,
            blocked,
            spec.pitchForSeed(spawn.seed),
            scaleGain(spawn.scale));
        if (started) {
            playedEvents++;
        } else {
            rejectedEvents++;
        }
    }

    public int playedEvents() {
        return playedEvents;
    }

    public int silentEvents() {
        return silentEvents;
    }

    /** Requests the mixer refused: out of reach under the current sliders, or the pool was full. */
    public int rejectedEvents() {
        return rejectedEvents;
    }

    /** The event counters, appended to the mixer's debug line. */
    public String statusLine() {
        return String.format(
            "events %d played / %d silent / %d dropped",
            playedEvents,
            silentEvents,
            rejectedEvents);
    }

    /**
     * The gain multiplier for an event's size.
     *
     * <p>{@code scale} is a size multiplier, not a volume: a smoke cloud that grows to twice the
     * standard radius should be a little louder, never twice as loud, or the biggest cloud in the
     * match would clip the mix. The curve is bounded and gentle for exactly that reason.
     */
    private static float scaleGain(float scale) {
        if (!Float.isFinite(scale) || scale <= 0f) {
            return 1f;
        }
        float normalised = Math.min(1f, Math.max(0f, (scale - 0.5f) / 1.5f));
        return MIN_SCALE_GAIN + SCALE_GAIN_RANGE * normalised;
    }
}
