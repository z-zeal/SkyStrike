package io.github.skystrike.shared.weapons;

import io.github.skystrike.shared.config.CombatConfig;
import java.util.EnumMap;
import java.util.Map;

/**
 * Per-weapon flight behaviour: muzzle speed, drop, drag and damage falloff (mechanics §4.2).
 *
 * <p>Shared on purpose. The server resolves hits with these numbers, and the client draws
 * tracers and (from Phase 7) a predicted point of impact with them; a second copy of the table
 * would quietly put the two pictures out of step.
 *
 * <p>Tuned and checked in {@code tools/scratch/phase3_ballistics.py}.
 *
 * @param weaponClass    family, which fixes the relative drop weight
 * @param muzzleSpeed    units per second at the muzzle, 900 (sawed-off) to 1950 (AWP)
 * @param dragPerTick    speed retained per 60 Hz tick, 0.980 (sawed-off) to 0.998 (AWP)
 * @param gravityRampSeconds time over which drop ramps in, so rounds fly flat up close
 * @param maxRange       distance at which damage reaches its floor, from the weapon table
 * @param minDamageRatio damage retained at maximum range, 0.35 (sawed-off) to 0.85 (AWP)
 */
public record WeaponBallistics(
    WeaponClass weaponClass,
    float muzzleSpeed,
    float dragPerTick,
    float gravityRampSeconds,
    float maxRange,
    float minDamageRatio
) {

    public WeaponBallistics {
        if (muzzleSpeed <= 0f) {
            throw new IllegalArgumentException("muzzle speed must be positive: " + muzzleSpeed);
        }
        if (dragPerTick <= 0f || dragPerTick > 1f) {
            throw new IllegalArgumentException("drag must be in (0, 1]: " + dragPerTick);
        }
        if (gravityRampSeconds <= 0f) {
            throw new IllegalArgumentException("gravity ramp must be positive: " + gravityRampSeconds);
        }
        if (maxRange <= 0f) {
            throw new IllegalArgumentException("max range must be positive: " + maxRange);
        }
        if (minDamageRatio <= 0f || minDamageRatio > 1f) {
            throw new IllegalArgumentException("damage floor must be in (0, 1]: " + minDamageRatio);
        }
    }

    /** Downward acceleration weight for this weapon's family (sniper 1.0 → shotgun 5.0). */
    public float gravityWeight() {
        return weaponClass.gravityWeight();
    }

    /** Fully ramped-in drop acceleration in units/s², always negative. */
    public float terminalGravity() {
        return -CombatConfig.BULLET_GRAVITY_BASE * gravityWeight();
    }

    private static final Map<WeaponId, WeaponBallistics> TABLE = buildTable();

    /** The flight profile of one weapon. Never null. */
    public static WeaponBallistics of(WeaponId id) {
        WeaponBallistics ballistics = TABLE.get(id);
        if (ballistics == null) {
            throw new IllegalArgumentException("no ballistics for weapon: " + id);
        }
        return ballistics;
    }

    /** The whole table, for validation and tooling. */
    public static Map<WeaponId, WeaponBallistics> all() {
        return TABLE;
    }

    private static Map<WeaponId, WeaponBallistics> buildTable() {
        Map<WeaponId, WeaponBallistics> table = new EnumMap<>(WeaponId.class);
        //                                  class              speed   drag   ramp  range  floor
        table.put(WeaponId.DESERT_EAGLE,
            new WeaponBallistics(WeaponClass.PISTOL,  1250f, 0.990f, 1.00f,  620f, 0.55f));
        table.put(WeaponId.FAMAS,
            new WeaponBallistics(WeaponClass.RIFLE,   1350f, 0.992f, 1.20f,  740f, 0.70f));
        table.put(WeaponId.SCAR_L,
            new WeaponBallistics(WeaponClass.RIFLE,   1400f, 0.993f, 1.20f,  780f, 0.70f));
        table.put(WeaponId.P90,
            new WeaponBallistics(WeaponClass.SMG,     1150f, 0.987f, 0.80f,  420f, 0.42f));
        table.put(WeaponId.KAR98K,
            new WeaponBallistics(WeaponClass.SNIPER,  1800f, 0.997f, 1.80f, 1200f, 0.75f));
        table.put(WeaponId.AWP,
            new WeaponBallistics(WeaponClass.SNIPER,  1950f, 0.998f, 1.80f, 1500f, 0.85f));
        table.put(WeaponId.HK417,
            new WeaponBallistics(WeaponClass.RIFLE,   1500f, 0.995f, 1.40f,  950f, 0.72f));
        table.put(WeaponId.SAWED_OFF,
            new WeaponBallistics(WeaponClass.SHOTGUN,  900f, 0.980f, 0.50f,  180f, 0.35f));
        table.put(WeaponId.BURST_RIFLE,
            new WeaponBallistics(WeaponClass.RIFLE,   1320f, 0.992f, 1.10f,  720f, 0.70f));
        table.put(WeaponId.ASSAULT_RIFLE,
            new WeaponBallistics(WeaponClass.RIFLE,   1300f, 0.991f, 1.10f,  750f, 0.70f));
        table.put(WeaponId.SHOTGUN,
            new WeaponBallistics(WeaponClass.SHOTGUN,  950f, 0.982f, 0.55f,  260f, 0.40f));
        table.put(WeaponId.SNIPER_RIFLE,
            new WeaponBallistics(WeaponClass.SNIPER,  1700f, 0.996f, 1.70f, 1500f, 0.78f));
        table.put(WeaponId.SMG,
            new WeaponBallistics(WeaponClass.SMG,     1100f, 0.986f, 0.80f,  450f, 0.45f));
        return java.util.Collections.unmodifiableMap(table);
    }
}
