package io.github.skystrike.audio;

import io.github.skystrike.shared.audio.AudioBus;
import io.github.skystrike.shared.audio.EffectSoundTable;
import io.github.skystrike.shared.audio.SoundPriority;
import io.github.skystrike.shared.audio.SoundSpec;
import io.github.skystrike.shared.effect.EffectType;
import io.github.skystrike.shared.weapons.FireMode;
import io.github.skystrike.shared.weapons.WeaponBallistics;
import io.github.skystrike.shared.weapons.WeaponClass;
import io.github.skystrike.shared.weapons.WeaponDefinition;
import io.github.skystrike.shared.weapons.WeaponId;
import io.github.skystrike.shared.weapons.WeaponRegistry;

/**
 * The client's sound catalogue: the one place that answers "what does this sound like?" for the
 * three sources of sound in a match (roadmap Phase 9, project structure §8).
 *
 * <p>There is deliberately no second taxonomy anywhere. Weapon fire and handling vary by the
 * shared {@link WeaponId} and {@link WeaponClass} registries; world events vary by the shared
 * {@link EffectType} the server already broadcasts; the stun ring is the local player's own status.
 * Nothing here invents a parallel list of weapons or effects to keep in step.
 *
 * <p><b>Two decision homes, on purpose.</b> The world half lives in
 * {@link EffectSoundTable} in {@code shared}, next to the event enum it maps and inside the module
 * that has unit tests; this class holds the weapon half, which needs the weapon registry and the
 * packaged gun recordings. Both halves produce the same {@link SoundSpec}, so the mixer never
 * knows which it was handed.
 */
public final class SoundCatalog {

    private static final String FIRE_ROOT = "sfx/guns/fire/";
    private static final String SHARED_ROOT = "sfx/guns/shared/";

    /** The stun ring (mechanics §6.1): a two-second seamless loop, head-locked. */
    private static final SoundSpec TINNITUS = SoundSpec.looping(
        "sfx/status/tinnitus-ring.wav", AudioBus.EFFECTS, 1f, 2f);

    public SoundCatalog() {
    }

    // --- world effects -------------------------------------------------------------------------

    /** What one effect event sounds like; never {@code null}, and silent where documented. */
    public SoundSpec specFor(EffectType type) {
        return EffectSoundTable.specFor(type);
    }

    /** The looping stun ring, driven per frame by {@link TinnitusEffect}. */
    public SoundSpec tinnitusSpec() {
        return TINNITUS;
    }

    // --- weapons -------------------------------------------------------------------------------

    /** Returns the packaged path, or {@code null} when an event does not apply to this weapon. */
    public String pathFor(WeaponId weaponId, GunSoundEvent event) {
        if (weaponId == null || event == null) {
            return null;
        }
        return switch (event) {
            case FIRE -> FIRE_ROOT + classStem(WeaponBallistics.of(weaponId).weaponClass()) + ".wav";
            case RELOAD -> SHARED_ROOT + "reload.wav";
            case EMPTY -> SHARED_ROOT + "empty.wav";
            case EQUIP -> SHARED_ROOT + "equip.wav";
            case ADS_IN -> SHARED_ROOT + "ads-in.wav";
            case ADS_OUT -> SHARED_ROOT + "ads-out.wav";
            case CYCLE -> hasCycle(weaponId) ? SHARED_ROOT + "cycle.wav" : null;
        };
    }

    /**
     * The full playback policy for one weapon event: the asset, its bus, and how far it carries.
     *
     * <p>Distances are gameplay information, not decoration. Fire is heard across the arena
     * because being shot at from off-screen must never be a surprise; a reload or a weapon swap is
     * heard a fraction of that, so the handling sounds reward listening without giving away
     * position from the far side of the map. Reload and reloading-adjacent sounds sit at
     * {@link SoundPriority#LOW} for the same reason casing tinkles do: they are texture.
     *
     * <p>Durations are the packaged recordings' lengths, used only for voice bookkeeping.
     */
    public SoundSpec specFor(WeaponId weaponId, GunSoundEvent event) {
        String path = pathFor(weaponId, event);
        if (path == null) {
            return SoundSpec.SILENT;
        }
        return switch (event) {
            case FIRE -> SoundSpec.oneShot(
                path, AudioBus.EFFECTS, 0.78f, 0.04f, 900f, 2400f, 1.23f, 8, SoundPriority.HIGH);
            case RELOAD -> SoundSpec.oneShot(
                path, AudioBus.EFFECTS, 0.55f, 0.03f, 700f, 1100f, 1.25f, 3, SoundPriority.NORMAL);
            case EMPTY -> SoundSpec.oneShot(
                path, AudioBus.EFFECTS, 0.45f, 0.05f, 500f, 800f, 0.29f, 2, SoundPriority.NORMAL);
            case EQUIP -> SoundSpec.oneShot(
                path, AudioBus.EFFECTS, 0.40f, 0.04f, 500f, 800f, 0.67f, 2, SoundPriority.LOW);
            case ADS_IN, ADS_OUT -> SoundSpec.oneShot(
                path, AudioBus.EFFECTS, 0.32f, 0.03f, 450f, 700f, 0.85f, 2, SoundPriority.LOW);
            case CYCLE -> SoundSpec.oneShot(
                path, AudioBus.EFFECTS, 0.50f, 0.05f, 700f, 1100f, 1.55f, 2, SoundPriority.NORMAL);
        };
    }

    /** True for bolt, pump and break-action guns whose existing assets had a cycle event. */
    public boolean hasCycle(WeaponId weaponId) {
        if (weaponId == null) {
            return false;
        }
        WeaponDefinition definition = WeaponRegistry.of(weaponId);
        FireMode mode = definition.fireMode();
        return mode == FireMode.BOLT || mode == FireMode.PUMP || mode == FireMode.BREAK;
    }

    private static String classStem(WeaponClass weaponClass) {
        return switch (weaponClass) {
            case ASSAULT_RIFLE -> "assault-rifle";
            case BATTLE_RIFLE -> "battle-rifle";
            case DMR -> "dmr";
            case LMG -> "lmg";
            case PDW -> "pdw";
            case PISTOL -> "pistol";
            case REVOLVER -> "revolver";
            case SHOTGUN -> "shotgun";
            case SMG -> "smg";
            case SNIPER -> "sniper";
        };
    }
}
