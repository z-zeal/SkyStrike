package io.github.skystrike.shared.audio;

import io.github.skystrike.shared.effect.EffectType;

/**
 * The world half of the phase-9 sound catalogue: one decision per {@link EffectType} the server
 * can raise (roadmap Phase 9, effects plan §9).
 *
 * <p>This table is why audio needed no new gameplay hook. The server already emits a closed
 * vocabulary of effect events, and {@code FxPipeline} hands each drained visual spawn to the
 * particle system and generic effects-audio layer. Weapon reports use a separate authoritative
 * cue copied into {@code PacketGameState}: audible range and terrain attenuation must not depend
 * on whether a recipient's vision allowed the muzzle flash through. Nothing in gameplay knows a
 * sound exists, and there is no second list of world effects to keep in step with this catalogue.
 *
 * <p><b>Every effect type is listed.</b> The switch is exhaustive on purpose: adding an
 * {@code EffectType} stops the build until someone decides what it sounds like, either an entry or
 * an explicit {@link SoundSpec#SILENT}. That is the same rule the wire enum already follows for
 * ordinals.
 *
 * <p><b>Two entries are worth explaining.</b>
 *
 * <ul>
 *   <li>{@code MUZZLE_FLASH} stays silent in this generic effect table. A successful shot's
 *       attributed muzzle event is copied into {@code PacketGameState.gunfireEvents}, where
 *       {@code GunAudio} resolves its weapon-specific report and applies audible range/occlusion
 *       independently of visual culling. Playing a second sound here would double every shot.</li>
 *   <li>{@code FIRE_ZONE} is emitted once per fire patch, and one molotov lays down a patch at the
 *       impact point plus a spread across the surface tangent. All of those arrive in the same
 *       tick, so this row is quiet, {@link SoundPriority#LOW} and capped at two voices: the pool
 *       drops the rest, and the ignition reads as one fire starting rather than five clicks.</li>
 * </ul>
 *
 * <p><b>Row shape.</b> Rows are written in the argument order of {@link SoundSpec#oneShot} and the
 * paths and durations are parsed out of this file by
 * {@code tools/scratch/static_audio_check.py}, which checks them against the shipped WAV files.
 * Keep new rows in the same shape and the check keeps working.
 */
public final class EffectSoundTable {

    /** A frag grenade's phased detonation. The loudest thing in the game, and the longest tail. */
    private static final SoundSpec EXPLOSION_FRAG = SoundSpec.oneShot(
        "sfx/world/explosion-frag.wav", AudioBus.EFFECTS, 0.90f, 0.05f, 1100f, 3200f, 1.35f, 6,
        SoundPriority.CRITICAL);

    /** An impact grenade: the same shape, smaller and snappier, and it still never gets culled. */
    private static final SoundSpec EXPLOSION_IMPACT = SoundSpec.oneShot(
        "sfx/world/explosion-impact.wav", AudioBus.EFFECTS, 0.75f, 0.06f, 950f, 2500f, 0.85f, 6,
        SoundPriority.CRITICAL);

    /** A smoke grenade's cloud: a long hiss, placed where the cloud actually starts. */
    private static final SoundSpec SMOKE_DEPLOY = SoundSpec.oneShot(
        "sfx/world/smoke-burst.wav", AudioBus.EFFECTS, 0.45f, 0.08f, 700f, 1500f, 1.10f, 2,
        SoundPriority.NORMAL);

    /** A molotov's glass break at the point of contact, before the fire spreads. */
    private static final SoundSpec MOLOTOV_SPLASH = SoundSpec.oneShot(
        "sfx/world/molotov-splash.wav", AudioBus.EFFECTS, 0.60f, 0.07f, 800f, 1800f, 0.75f, 3,
        SoundPriority.HIGH);

    /** One patch of fire catching. Quiet, low priority, and pooled hard — see the class javadoc. */
    private static final SoundSpec FIRE_IGNITE = SoundSpec.oneShot(
        "sfx/world/fire-ignite.wav", AudioBus.EFFECTS, 0.26f, 0.10f, 500f, 900f, 0.60f, 2,
        SoundPriority.LOW);

    /** A poison cloud: the smoke hiss at a lower, wetter pitch. */
    private static final SoundSpec POISON_DEPLOY = SoundSpec.oneShot(
        "sfx/world/poison-burst.wav", AudioBus.EFFECTS, 0.45f, 0.08f, 700f, 1500f, 1.10f, 2,
        SoundPriority.NORMAL);

    /**
     * A flashbang or stun detonation: the crack the whole area hears.
     *
     * <p>The ring that follows is not part of this entry. It is the local player's own status
     * effect, driven per frame by {@code TinnitusEffect}, because only the player who was actually
     * blinded should hear it.
     */
    private static final SoundSpec FLASH_DETONATION = SoundSpec.oneShot(
        "sfx/world/flash-detonation.wav", AudioBus.EFFECTS, 0.95f, 0.03f, 1300f, 3600f, 0.60f, 2,
        SoundPriority.CRITICAL);

    /** A claymore's directional blast: a crack plus the pellets, longer than a grenade's boom. */
    private static final SoundSpec CLAYMORE_BLAST = SoundSpec.oneShot(
        "sfx/world/claymore-blast.wav", AudioBus.EFFECTS, 0.80f, 0.05f, 1000f, 2600f, 0.90f, 4,
        SoundPriority.HIGH);

    /** A round hitting the arena's concrete: a flat thud with a dust tail. */
    private static final SoundSpec IMPACT_CONCRETE = SoundSpec.oneShot(
        "sfx/world/impact-concrete.wav", AudioBus.EFFECTS, 0.40f, 0.14f, 450f, 1100f, 0.25f, 6,
        SoundPriority.NORMAL);

    /** A round hitting metal: a bright ping. The metal/wood entries wait on a surface-material pass. */
    private static final SoundSpec IMPACT_METAL = SoundSpec.oneShot(
        "sfx/world/impact-metal.wav", AudioBus.EFFECTS, 0.42f, 0.16f, 500f, 1300f, 0.30f, 6,
        SoundPriority.NORMAL);

    /** A round hitting wood: a dry knock. */
    private static final SoundSpec IMPACT_WOOD = SoundSpec.oneShot(
        "sfx/world/impact-wood.wav", AudioBus.EFFECTS, 0.40f, 0.14f, 450f, 1100f, 0.25f, 6,
        SoundPriority.NORMAL);

    /**
     * A casing hitting the ground. The quietest entry in the table and the first to be dropped:
     * a full squad firing ejects dozens of these per second, so two voices and low priority keep
     * the texture of the fight without eating the pool the fight itself needs.
     */
    private static final SoundSpec SHELL_EJECT = SoundSpec.oneShot(
        "sfx/world/shell-eject.wav", AudioBus.EFFECTS, 0.22f, 0.18f, 350f, 700f, 0.30f, 2,
        SoundPriority.LOW);

    private EffectSoundTable() {
    }

    /**
     * What one effect event sounds like. Never {@code null}: silence is
     * {@link SoundSpec#SILENT}, and a null type is silent too, because a malformed event must not
     * be able to throw on the render thread.
     */
    public static SoundSpec specFor(EffectType type) {
        if (type == null) {
            return SoundSpec.SILENT;
        }
        return switch (type) {
            case FRAG_EXPLOSION -> EXPLOSION_FRAG;
            case IMPACT_EXPLOSION -> EXPLOSION_IMPACT;
            case SMOKE_BURST -> SMOKE_DEPLOY;
            case MOLOTOV_SPLASH -> MOLOTOV_SPLASH;
            case FIRE_ZONE -> FIRE_IGNITE;
            case POISON_BURST -> POISON_DEPLOY;
            case FLASH_DETONATION -> FLASH_DETONATION;
            case CLAYMORE_BLAST -> CLAYMORE_BLAST;
            case BULLET_IMPACT_CONCRETE -> IMPACT_CONCRETE;
            case BULLET_IMPACT_METAL -> IMPACT_METAL;
            case BULLET_IMPACT_WOOD -> IMPACT_WOOD;
            case MUZZLE_FLASH -> SoundSpec.SILENT;
            case SHELL_EJECT -> SHELL_EJECT;
        };
    }
}
