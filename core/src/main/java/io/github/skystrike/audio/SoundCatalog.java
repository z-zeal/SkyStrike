package io.github.skystrike.audio;

import io.github.skystrike.shared.weapons.FireMode;
import io.github.skystrike.shared.weapons.WeaponBallistics;
import io.github.skystrike.shared.weapons.WeaponDefinition;
import io.github.skystrike.shared.weapons.WeaponId;
import io.github.skystrike.shared.weapons.WeaponClass;
import io.github.skystrike.shared.weapons.WeaponRegistry;

/**
 * Maps the existing weapon registry to the packaged gun sound assets.
 *
 * <p>There is intentionally no second weapon taxonomy here. Fire sounds vary by the shared
 * {@link io.github.skystrike.shared.weapons.WeaponBallistics#weaponClass()} category; handling
 * sounds are shared because the source library does not provide one reload or dry-fire recording
 * for every game weapon. The paths mirror {@code assets/sfx/guns-sfx.json}.
 */
public final class SoundCatalog {

    private static final String FIRE_ROOT = "sfx/guns/fire/";
    private static final String SHARED_ROOT = "sfx/guns/shared/";

    public SoundCatalog() {
    }

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
