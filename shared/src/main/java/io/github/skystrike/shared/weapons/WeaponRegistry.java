package io.github.skystrike.shared.weapons;

import io.github.skystrike.shared.config.WeaponConfig;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * The gun table: id → definition, for every one of the thirteen guns of mechanics §5.1.
 *
 * <p>Lookups <b>return copies</b>. A shared mutable definition handed to every player would be a
 * cross-player state leak — one player editing their weapon would edit everyone's. The
 * definitions are immutable records today, so the copy is cheap insurance, but the contract is
 * the point: callers may do what they like with what they get.
 *
 * <p>This registry is the single source of truth for per-weapon parameters. Phase 3 kept the
 * table on the server ({@code server/weapons/WeaponStats}); Phase 4 supersedes that copy because
 * the client's predicted loadout and HUD need the ammunition and ADS numbers verbatim, and two
 * copies of a balance table are a divergence waiting to happen. Live state — spread, cooldown,
 * ammunition — stays server-authoritative; only static definitions live here.
 */
public final class WeaponRegistry {

    private WeaponRegistry() {
    }

    /** Fire parameters for one weapon, as a fresh copy. Never null. */
    public static WeaponDefinition of(WeaponId id) {
        WeaponDefinition definition = TABLE.get(id);
        if (definition == null) {
            throw new IllegalArgumentException("no definition for weapon: " + id);
        }
        return definition.copy();
    }

    /** Fire parameters by wire ordinal, falling back to the default weapon. */
    public static WeaponDefinition ofOrdinal(int ordinal) {
        return of(WeaponId.fromOrdinal(ordinal));
    }

    /** The whole table, for validation and tooling. Values are copies. */
    public static Map<WeaponId, WeaponDefinition> all() {
        Map<WeaponId, WeaponDefinition> out = new EnumMap<>(WeaponId.class);
        for (WeaponId id : WeaponId.values()) {
            out.put(id, of(id));
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * Display name for any {@code weaponId} int on the wire: values below
     * {@link MeleeId#WIRE_ID_BASE} are gun ordinals, values at or above it are melee wire ids.
     */
    public static String displayNameForWireId(int wireId) {
        if (MeleeId.isMeleeWireId(wireId)) {
            return MeleeId.fromWireId(wireId).displayName();
        }
        return WeaponId.fromOrdinal(wireId).displayName();
    }

    private static final Map<WeaponId, WeaponDefinition> TABLE = buildTable();

    private static Map<WeaponId, WeaponDefinition> buildTable() {
        Map<WeaponId, WeaponDefinition> table = new EnumMap<>(WeaponId.class);

        put(table, WeaponId.DESERT_EAGLE, 60f, 1.5f, 7, 42, 2.0f, FireMode.SEMI, 1,
            new WeaponDefinition.SpreadProfile(5.20f, 0.36f, 2.20f, 4.2f, 3.6f, 5.0f),
            new WeaponDefinition.RecoilProfile(0.58f, 1.65f, 130f, 95f, 4.0f));

        put(table, WeaponId.FAMAS, 25f, 10.0f, 25, 150, 2.0f, FireMode.AUTO, 1,
            new WeaponDefinition.SpreadProfile(4.10f, 0.32f, 2.00f, 2.2f, 3.2f, 8.0f),
            new WeaponDefinition.RecoilProfile(0.62f, 1.50f, 55f, 45f, 2.0f));

        put(table, WeaponId.SCAR_L, 32f, 7.5f, 20, 120, 2.2f, FireMode.AUTO, 1,
            new WeaponDefinition.SpreadProfile(3.80f, 0.30f, 2.10f, 2.5f, 3.2f, 7.0f),
            new WeaponDefinition.RecoilProfile(0.60f, 1.55f, 70f, 55f, 2.4f));

        put(table, WeaponId.P90, 18f, 14.0f, 50, 200, 2.4f, FireMode.AUTO, 1,
            new WeaponDefinition.SpreadProfile(5.50f, 0.42f, 1.80f, 2.1f, 2.8f, 9.5f),
            new WeaponDefinition.RecoilProfile(0.78f, 1.40f, 38f, 32f, 1.5f));

        put(table, WeaponId.KAR98K, 90f, 0.75f, 5, 30, 3.2f, FireMode.BOLT, 1,
            new WeaponDefinition.SpreadProfile(1.80f, 0.20f, 2.70f, 4.4f, 4.0f, 4.5f),
            new WeaponDefinition.RecoilProfile(0.48f, 1.95f, 210f, 140f, 5.5f));

        put(table, WeaponId.AWP, 145f, 0.5f, 5, 25, 3.8f, FireMode.BOLT, 1,
            new WeaponDefinition.SpreadProfile(1.20f, 0.18f, 2.80f, 4.8f, 4.0f, 4.0f),
            new WeaponDefinition.RecoilProfile(0.45f, 2.00f, 240f, 150f, 6.0f));

        put(table, WeaponId.HK417, 48f, 4.0f, 10, 60, 2.6f, FireMode.SEMI, 1,
            new WeaponDefinition.SpreadProfile(2.40f, 0.26f, 2.30f, 3.2f, 3.4f, 5.5f),
            new WeaponDefinition.RecoilProfile(0.55f, 1.70f, 120f, 90f, 3.6f));

        put(table, WeaponId.SAWED_OFF, 100f, 1.2f, 2, 18, 2.8f, FireMode.SHOTGUN,
            WeaponConfig.SHOTGUN_PELLETS,
            new WeaponDefinition.SpreadProfile(15.00f, 0.55f, 1.90f, 3.8f, 2.7f, 6.0f),
            new WeaponDefinition.RecoilProfile(0.76f, 1.85f, 260f, 160f, 7.0f));

        put(table, WeaponId.BURST_RIFLE, 24f, 3.2f, 24, 144, 2.2f, FireMode.BURST, 1,
            new WeaponDefinition.SpreadProfile(4.90f, 0.32f, 2.00f, 2.4f, 3.0f, 6.5f),
            new WeaponDefinition.RecoilProfile(0.62f, 1.50f, 60f, 48f, 2.2f));

        put(table, WeaponId.ASSAULT_RIFLE, 30f, 6.0f, 30, 120, 2.2f, FireMode.AUTO, 1,
            new WeaponDefinition.SpreadProfile(6.30f, 0.34f, 2.00f, 2.7f, 3.0f, 7.5f),
            new WeaponDefinition.RecoilProfile(0.65f, 1.60f, 65f, 55f, 2.6f));

        put(table, WeaponId.SHOTGUN, 80f, 1.2f, 8, 40, 2.8f, FireMode.SHOTGUN,
            WeaponConfig.SHOTGUN_PELLETS,
            new WeaponDefinition.SpreadProfile(18.50f, 0.52f, 1.90f, 3.6f, 2.7f, 6.0f),
            new WeaponDefinition.RecoilProfile(0.74f, 1.80f, 220f, 140f, 6.5f));

        put(table, WeaponId.SNIPER_RIFLE, 100f, 0.8f, 5, 20, 3.0f, FireMode.SEMI, 1,
            new WeaponDefinition.SpreadProfile(0.75f, 0.22f, 2.60f, 4.0f, 4.0f, 4.5f),
            new WeaponDefinition.RecoilProfile(0.50f, 1.90f, 180f, 120f, 5.0f));

        put(table, WeaponId.SMG, 20f, 8.0f, 25, 150, 1.8f, FireMode.AUTO, 1,
            new WeaponDefinition.SpreadProfile(7.80f, 0.40f, 1.85f, 2.3f, 2.8f, 9.0f),
            new WeaponDefinition.RecoilProfile(0.72f, 1.45f, 45f, 38f, 1.8f));

        return Collections.unmodifiableMap(table);
    }

    private static void put(
            Map<WeaponId, WeaponDefinition> table,
            WeaponId id,
            float damage,
            float fireRateHz,
            int magazineSize,
            int reserveAmmo,
            float reloadSeconds,
            FireMode fireMode,
            int pelletCount,
            WeaponDefinition.SpreadProfile spread,
            WeaponDefinition.RecoilProfile recoil) {
        table.put(id, new WeaponDefinition(
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
