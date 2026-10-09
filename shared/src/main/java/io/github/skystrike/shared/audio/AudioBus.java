package io.github.skystrike.shared.audio;

/**
 * The client mix buses (roadmap Phase 9, project structure §8).
 *
 * <p>Three settings already exist — master, music and effects — and they are the only volumes a
 * player can change. A bus names which of those a sound obeys, and resolves the product here
 * rather than in the audio backend: one formula, one place to argue about what "effects volume"
 * means, and no chance of one call path applying the master gain twice.
 *
 * <p>Every constant is clamped to {@code [0, 1]} on the way through, so a slider cannot push an
 * unclamped gain into the backend (libGDX documents that it does not clamp for you).
 *
 * <p>Platform-neutral by construction: this type lives in {@code shared} with the rest of the
 * pure presentation maths so it can be unit-tested. The class that actually talks to
 * {@code Gdx.audio} is {@code io.github.skystrike.audio.AudioSystem} in {@code core}.
 */
public enum AudioBus {

    /**
     * World and weapon sound: explosions, impacts, muzzle reports, gutters, the stun ring.
     *
     * <p>Everything the game throws at the player that is <em>not</em> interface feedback goes
     * here, including the tinnitus ring — a player who mutes effects has asked for silence, and
     * a status effect is not an exception to that.
     */
    EFFECTS,

    /** Music. Nothing plays on this bus yet; the settings slider already exists for it. */
    MUSIC,

    /**
     * Interface feedback: chat and console blips, menu confirmations.
     *
     * <p>Deliberately <b>not</b> scaled by the effects slider. A player who turns the game's
     * effects down is asking for a quieter world, not for a menu they cannot hear; the master
     * slider is what silences the interface.
     */
    UI;

    /**
     * The gain this bus contributes for the given slider values: master times the bus's own
     * volume, with the master applied exactly once.
     *
     * @param masterVolume  the player's master slider, clamped to {@code [0, 1]}
     * @param musicVolume   the player's music slider, clamped to {@code [0, 1]}
     * @param effectsVolume the player's effects slider, clamped to {@code [0, 1]}
     * @return a gain in {@code [0, 1]}; {@code 0} for a non-finite input, never {@code NaN}
     */
    public float gain(float masterVolume, float musicVolume, float effectsVolume) {
        float busVolume = switch (this) {
            case EFFECTS -> clamp(masterVolume) * clamp(effectsVolume);
            case MUSIC -> clamp(masterVolume) * clamp(musicVolume);
            case UI -> clamp(masterVolume);
        };
        return clamp(busVolume);
    }

    private static float clamp(float value) {
        if (!Float.isFinite(value)) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, value));
    }
}
