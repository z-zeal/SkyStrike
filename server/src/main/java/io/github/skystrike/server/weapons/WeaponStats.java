package io.github.skystrike.server.weapons;

import io.github.skystrike.shared.config.WeaponConfig;
import io.github.skystrike.shared.weapons.FireMode;
import io.github.skystrike.shared.weapons.WeaponBallistics;
import io.github.skystrike.shared.weapons.WeaponId;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Immutable per-weapon fire parameters — the weapon table of mechanics §5.1 plus the spread and
 * recoil tuning of §4.3 and §4.4.
 *
 * <p>Authoritative, and therefore server-side. The client is told what a round <i>did</i>; it is
 * never told what a weapon <i>can</i> do, so a modified client cannot learn damage numbers it has
 * not seen. Flight behaviour is the exception: {@link WeaponBallistics} is shared because the
 * client has to draw tracers along the same arcs the server resolves hits against.
 *
 * <p>Phase 4 wraps this in {@code shared/weapons/WeaponRegistry} with ammunition and loadout
 * handling; this is the data that registry will serve, written once.
 *
 * @param id            which gun
 * @param damage        muzzle damage of one round (one pellet, for shotguns)
 * @param fireRateHz    trigger events per second — bursts per second for {@link FireMode#BURST}
 *                      and shells per second for {@link FireMode#SHOTGUN}
 * @param magazineSize  rounds per magazine (Phase 4 consumes this)
 * @param reserveAmmo   rounds carried (Phase 4 consumes this)
 * @param reloadSeconds reload time (Phase 4 consumes this)
 * @param fireMode      how a trigger pull becomes rounds
 * @param pelletCount   rounds produced per trigger event by a shell
 * @param spread        stance spread tuning
 * @param recoil        the three recoil channels
 * @param ballistics    flight and falloff, shared with the client
 */
public record WeaponStats(
    WeaponId id,
    float damage,
    float fireRateHz,
    int magazineSize,
    int reserveAmmo,
    float reloadSeconds,
    FireMode fireMode,
    int pelletCount,
    SpreadProfile spread,
    RecoilProfile recoil,
    WeaponBallistics ballistics
) {

    /**
     * Stance spread tuning (mechanics §4.3).
     *
     * @param baseDegrees        hip-fire spread while standing still
     * @param adsRatio           fraction of base spread while aiming — AWP 18%, P90 42%
     * @param movingMultiplier   1.8–2.8× while moving
     * @param kickDegrees        2.1–4.8° added per round, before the recoil multiplier
     * @param ceilingMultiplier  2.7–4× base spread
     * @param recoveryDegreesPerSecond 4–9.5 deg/s, 1.5× faster while aiming
     */
    public record SpreadProfile(
        float baseDegrees,
        float adsRatio,
        float movingMultiplier,
        float kickDegrees,
        float ceilingMultiplier,
        float recoveryDegreesPerSecond
    ) {
        /** The absolute ceiling in degrees. */
        public float ceilingDegrees() {
            return baseDegrees * ceilingMultiplier;
        }
    }

    /**
     * The three recoil channels (mechanics §4.4).
     *
     * @param adsMultiplier     0.45–0.78, lerped toward while aiming
     * @param movingMultiplier  1.4–2.0× while moving
     * @param linearImpulse     units/s added to the player's velocity, opposite the aim
     * @param angularDegreesPerSecond spin added to the body
     * @param visualKickDegrees gun-angle offset added per volley
     */
    public record RecoilProfile(
        float adsMultiplier,
        float movingMultiplier,
        float linearImpulse,
        float angularDegreesPerSecond,
        float visualKickDegrees
    ) {
    }

    /** Seconds between two trigger events. */
    public float cooldownSeconds() {
        return 1f / fireRateHz;
    }

    /** Rounds produced by one trigger event. */
    public int roundsPerTriggerEvent() {
        return switch (fireMode) {
            case BURST -> WeaponConfig.BURST_ROUNDS;
            case SHOTGUN -> pelletCount;
            default -> 1;
        };
    }

    /** Full damage of one trigger event at the muzzle, ignoring zones. */
    public float volleyDamage() {
        return damage * roundsPerTriggerEvent();
    }

    public String displayName() {
        return id.displayName();
    }

    // --- The table -----------------------------------------------------------------------------

    private static final Map<WeaponId, WeaponStats> TABLE = buildTable();

    /** Fire parameters for one weapon. Never null. */
    public static WeaponStats of(WeaponId id) {
        WeaponStats stats = TABLE.get(id);
        if (stats == null) {
            throw new IllegalArgumentException("no stats for weapon: " + id);
        }
        return stats;
    }

    /** Fire parameters by wire ordinal, falling back to the default weapon. */
    public static WeaponStats ofOrdinal(int ordinal) {
        return of(WeaponId.fromOrdinal(ordinal));
    }

    /** The whole table, for validation and tooling. */
    public static Map<WeaponId, WeaponStats> all() {
        return TABLE;
    }

    private static Map<WeaponId, WeaponStats> buildTable() {
        Map<WeaponId, WeaponStats> table = new EnumMap<>(WeaponId.class);

        put(table, WeaponId.DESERT_EAGLE, 60f, 1.5f, 7, 42, 2.0f, FireMode.SEMI, 1,
            new SpreadProfile(5.20f, 0.36f, 2.20f, 4.2f, 3.6f, 5.0f),
            new RecoilProfile(0.58f, 1.65f, 130f, 95f, 4.0f));

        put(table, WeaponId.FAMAS, 25f, 10.0f, 25, 150, 2.0f, FireMode.AUTO, 1,
            new SpreadProfile(4.10f, 0.32f, 2.00f, 2.2f, 3.2f, 8.0f),
            new RecoilProfile(0.62f, 1.50f, 55f, 45f, 2.0f));

        put(table, WeaponId.SCAR_L, 32f, 7.5f, 20, 120, 2.2f, FireMode.AUTO, 1,
            new SpreadProfile(3.80f, 0.30f, 2.10f, 2.5f, 3.2f, 7.0f),
            new RecoilProfile(0.60f, 1.55f, 70f, 55f, 2.4f));

        put(table, WeaponId.P90, 18f, 14.0f, 50, 200, 2.4f, FireMode.AUTO, 1,
            new SpreadProfile(5.50f, 0.42f, 1.80f, 2.1f, 2.8f, 9.5f),
            new RecoilProfile(0.78f, 1.40f, 38f, 32f, 1.5f));

        put(table, WeaponId.KAR98K, 90f, 0.75f, 5, 30, 3.2f, FireMode.BOLT, 1,
            new SpreadProfile(1.80f, 0.20f, 2.70f, 4.4f, 4.0f, 4.5f),
            new RecoilProfile(0.48f, 1.95f, 210f, 140f, 5.5f));

        put(table, WeaponId.AWP, 145f, 0.5f, 5, 25, 3.8f, FireMode.BOLT, 1,
            new SpreadProfile(1.20f, 0.18f, 2.80f, 4.8f, 4.0f, 4.0f),
            new RecoilProfile(0.45f, 2.00f, 240f, 150f, 6.0f));

        put(table, WeaponId.HK417, 48f, 4.0f, 10, 60, 2.6f, FireMode.SEMI, 1,
            new SpreadProfile(2.40f, 0.26f, 2.30f, 3.2f, 3.4f, 5.5f),
            new RecoilProfile(0.55f, 1.70f, 120f, 90f, 3.6f));

        put(table, WeaponId.SAWED_OFF, 100f, 1.2f, 2, 18, 2.8f, FireMode.SHOTGUN,
            WeaponConfig.SHOTGUN_PELLETS,
            new SpreadProfile(15.00f, 0.55f, 1.90f, 3.8f, 2.7f, 6.0f),
            new RecoilProfile(0.76f, 1.85f, 260f, 160f, 7.0f));

        put(table, WeaponId.BURST_RIFLE, 24f, 3.2f, 24, 144, 2.2f, FireMode.BURST, 1,
            new SpreadProfile(4.90f, 0.32f, 2.00f, 2.4f, 3.0f, 6.5f),
            new RecoilProfile(0.62f, 1.50f, 60f, 48f, 2.2f));

        put(table, WeaponId.ASSAULT_RIFLE, 30f, 6.0f, 30, 120, 2.2f, FireMode.AUTO, 1,
            new SpreadProfile(6.30f, 0.34f, 2.00f, 2.7f, 3.0f, 7.5f),
            new RecoilProfile(0.65f, 1.60f, 65f, 55f, 2.6f));

        put(table, WeaponId.SHOTGUN, 80f, 1.2f, 8, 40, 2.8f, FireMode.SHOTGUN,
            WeaponConfig.SHOTGUN_PELLETS,
            new SpreadProfile(18.50f, 0.52f, 1.90f, 3.6f, 2.7f, 6.0f),
            new RecoilProfile(0.74f, 1.80f, 220f, 140f, 6.5f));

        put(table, WeaponId.SNIPER_RIFLE, 100f, 0.8f, 5, 20, 3.0f, FireMode.SEMI, 1,
            new SpreadProfile(0.75f, 0.22f, 2.60f, 4.0f, 4.0f, 4.5f),
            new RecoilProfile(0.50f, 1.90f, 180f, 120f, 5.0f));

        put(table, WeaponId.SMG, 20f, 8.0f, 25, 150, 1.8f, FireMode.AUTO, 1,
            new SpreadProfile(7.80f, 0.40f, 1.85f, 2.3f, 2.8f, 9.0f),
            new RecoilProfile(0.72f, 1.45f, 45f, 38f, 1.8f));

        return Collections.unmodifiableMap(table);
    }

    private static void put(
            Map<WeaponId, WeaponStats> table,
            WeaponId id,
            float damage,
            float fireRateHz,
            int magazineSize,
            int reserveAmmo,
            float reloadSeconds,
            FireMode fireMode,
            int pelletCount,
            SpreadProfile spread,
            RecoilProfile recoil) {
        table.put(id, new WeaponStats(
            id,
            damage,
            fireRateHz,
            magazineSize,
            reserveAmmo,
            reloadSeconds,
            fireMode,
            pelletCount,
            spread,
            recoil,
            WeaponBallistics.of(id)));
    }
}
