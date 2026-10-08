package io.github.skystrike.shared.audio;

/**
 * One playable sound: its packaged asset, its mix bus, and the playback policy the client obeys
 * (roadmap Phase 9, project structure §8).
 *
 * <p>A spec is the whole contract between the catalogue and the mixer. The catalogue decides
 * <em>what</em> an event sounds like; the mixer
 * ({@code io.github.skystrike.audio.AudioSystem}) applies the numbers without knowing anything
 * about explosions, weapons or status effects.
 *
 * <p><b>Why this lives in {@code shared}.</b> It is plain data with no libGDX types, and the
 * module that does touch {@code Gdx.audio} has no test source set. Keeping specs here lets the
 * catalogue be tested like the rest of the project's presentation maths —
 * {@code EffectSoundTableTest} checks that every effect type has a decision, that no two entries
 * collide on a path, and that the numbers are sane. The alternative was a table validated only by
 * a running desktop build.
 *
 * <p><b>Voice lifetime.</b> {@code durationSeconds} is the asset's playback length and nothing
 * else. libGDX's {@code Sound} exposes no duration, so the mixer books a voice against this
 * number to know when the pool has a slot back; it is bookkeeping, never a gameplay timer.
 *
 * @param path             packaged asset path, relative to {@code assets/}; {@code null} only on
 *                         {@link #SILENT}
 * @param bus              which volume slider this sound obeys
 * @param gain             pre-spatial gain in {@code [0, 1]}, before bus, master and distance
 * @param pitchJitter      fractional random pitch spread, {@code 0} for a fixed pitch; the value
 *                         is derived from the event's server-assigned seed, so two clients hear
 *                         the same shot at the same pitch (see {@link #pitchForSeed(int)})
 * @param referenceDistance distance at which attenuation is half of full gain, in world units
 * @param audibleRadius    distance past which the source is silent, in world units
 * @param durationSeconds  the asset's length in seconds, for voice bookkeeping only
 * @param maxVoices        simultaneous voices this asset may hold across the whole mixer
 * @param priority         what this sound may steal from, and what may steal from it
 * @param looping          true for the looping assets (the stun ring); a loop is never a one-shot
 */
public record SoundSpec(
        String path,
        AudioBus bus,
        float gain,
        float pitchJitter,
        float referenceDistance,
        float audibleRadius,
        float durationSeconds,
        int maxVoices,
        SoundPriority priority,
        boolean looping) {

    /**
     * The deliberate absence of a sound.
     *
     * <p>A catalogue entry returns this rather than {@code null} so "this event makes no sound" is
     * a decision the table states, not a value a caller has to remember to check for. One entry
     * currently uses it: {@code MUZZLE_FLASH}, whose weapon-specific report the client already
     * plays from authoritative snapshots (the effect event carries no weapon identity, and playing
     * both would double every shot).
     */
    public static final SoundSpec SILENT =
        new SoundSpec(null, AudioBus.EFFECTS, 0f, 0f, 0f, 0f, 0f, 0, SoundPriority.LOW, false);

    /** libGDX documents the pitch multiplier's usable range as 0.5 to 2.0. */
    public static final float MIN_PITCH = 0.5f;
    public static final float MAX_PITCH = 2.0f;

    /** Bounds a hostile or hand-typed value into something the mixer can use. */
    public SoundSpec {
        if (bus == null) {
            bus = AudioBus.EFFECTS;
        }
        if (priority == null) {
            priority = SoundPriority.NORMAL;
        }
        if (path != null && path.isBlank()) {
            path = null;
        }
        gain = clamp(gain, 0f, 1f);
        pitchJitter = clamp(pitchJitter, 0f, MAX_PITCH - 1f);
        referenceDistance = Math.max(0f, finiteOrZero(referenceDistance));
        audibleRadius = Math.max(0f, finiteOrZero(audibleRadius));
        durationSeconds = Math.max(0f, finiteOrZero(durationSeconds));
        maxVoices = Math.max(0, maxVoices);
    }

    /**
     * A one-shot world sound on the effects bus.
     *
     * <p>The row order is load-bearing: the catalogue table is read by
     * {@code tools/scratch/static_audio_check.py}, which parses exactly this argument order out of
     * the Java source and compares it against the shipped WAV files. Keep new rows in this shape.
     *
     * @param path              packaged asset path
     * @param bus               mix bus, normally {@link AudioBus#EFFECTS}
     * @param gain              base gain in {@code [0, 1]}
     * @param pitchJitter       fractional pitch spread; {@code 0} for a fixed, mechanical sound
     * @param referenceDistance half-gain distance in world units
     * @param audibleRadius     silence distance in world units
     * @param durationSeconds   the asset's length, for voice bookkeeping
     * @param maxVoices         simultaneous voices across the mixer
     * @param priority          pool priority
     */
    public static SoundSpec oneShot(
            String path,
            AudioBus bus,
            float gain,
            float pitchJitter,
            float referenceDistance,
            float audibleRadius,
            float durationSeconds,
            int maxVoices,
            SoundPriority priority) {
        return new SoundSpec(
            path,
            bus,
            gain,
            pitchJitter,
            referenceDistance,
            audibleRadius,
            durationSeconds,
            maxVoices,
            priority,
            false);
    }

    /**
     * A looping, non-spatial sound.
     *
     * <p>Loops are head-locked by construction: the stun ring is inside the player's skull, not in
     * the arena, so there is nothing to attenuate and nothing to pan. The mixer keeps loop voices
     * in a pool of their own, exactly so an impact tick can never steal a status effect's voice.
     *
     * @param path            packaged asset path
     * @param bus             mix bus
     * @param gain            base gain in {@code [0, 1]}, usually {@code 1} with the envelope
     *                        supplying the actual level per frame
     * @param durationSeconds the loop's period in seconds, for bookkeeping
     */
    public static SoundSpec looping(String path, AudioBus bus, float gain, float durationSeconds) {
        return new SoundSpec(
            path, bus, gain, 0f, 0f, 0f, durationSeconds, 1, SoundPriority.CRITICAL, true);
    }

    /** True when this entry asks for no playback at all. */
    public boolean isSilent() {
        if (path == null || gain <= 0f) {
            return true;
        }
        return !looping && audibleRadius <= 0f;
    }

    /**
     * The pitch to play one instance at, derived deterministically from an event seed.
     *
     * <p>Same reasoning as the particle layout: the server assigns the seed, so every client that
     * hears the same impact hears it at the same pitch. A spec with no jitter always returns
     * {@code 1}, keeping its asset untouched.
     */
    public float pitchForSeed(int seed) {
        if (pitchJitter <= 0f) {
            return 1f;
        }
        float unit = unitFromSeed(seed);
        return clamp(1f + pitchJitter * (2f * unit - 1f), MIN_PITCH, MAX_PITCH);
    }

    /** A deterministic {@code [0, 1]} value from a seed, stable across platforms and runs. */
    public static float unitFromSeed(int seed) {
        int hash = seed * 0x9E3779B1;
        hash ^= hash >>> 16;
        hash *= 0x85EBCA6B;
        hash ^= hash >>> 13;
        return (hash & 0xFFFF) / 65535f;
    }

    private static float clamp(float value, float min, float max) {
        if (!Float.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static float finiteOrZero(float value) {
        return Float.isFinite(value) ? value : 0f;
    }
}
