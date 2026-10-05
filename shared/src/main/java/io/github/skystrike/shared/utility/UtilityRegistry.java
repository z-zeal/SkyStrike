package io.github.skystrike.shared.utility;

import io.github.skystrike.shared.config.UtilityConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The throwable table from mechanics §6, as data.
 *
 * <p>Definitions are records with primitive fields only, so this registry returns the shared
 * instance rather than a copy — there is nothing mutable for one player's grenade to leak into
 * another's. (That is the one difference from {@code WeaponRegistry}, which must copy.)
 *
 * <p>Claymore's row in the plan is entirely dashes. Its numbers are marked
 * {@link UtilityDefinition#provisional()} so a balance pass can tell them apart from the
 * specified ones at a glance, and so a test can assert that nothing else is guessed.
 */
public final class UtilityRegistry {

    private static final Map<UtilityId, UtilityDefinition> DEFINITIONS = build();

    private UtilityRegistry() {
    }

    /** The definition for {@code id}. Never null, never a copy, never mutable. */
    public static UtilityDefinition of(UtilityId id) {
        UtilityDefinition definition = DEFINITIONS.get(id);
        if (definition == null) {
            throw new IllegalArgumentException("no definition for utility: " + id);
        }
        return definition;
    }

    public static UtilityDefinition ofOrdinal(int ordinal) {
        return of(UtilityId.fromOrdinal(ordinal));
    }

    /** The definition behind a wire id, or {@code null} when it names a gun or a melee weapon. */
    public static UtilityDefinition ofWireId(int wireId) {
        UtilityId id = UtilityId.fromWireId(wireId);
        return id == null ? null : of(id);
    }

    /** Every definition, in {@link UtilityId} order. */
    public static List<UtilityDefinition> all() {
        // Built from the enum rather than from the map's values, because iteration order is
        // part of what callers expect and only the enum guarantees it.
        UtilityId[] ids = UtilityId.values();
        List<UtilityDefinition> definitions = new ArrayList<>(ids.length);
        for (UtilityId id : ids) {
            definitions.add(of(id));
        }
        return List.copyOf(definitions);
    }

    /** Display name for any wire id in the utility range, for the kill feed. */
    public static String displayNameForWireId(int wireId) {
        UtilityId id = UtilityId.fromWireId(wireId);
        return id == null ? "" : id.displayName();
    }

    private static Map<UtilityId, UtilityDefinition> build() {
        Map<UtilityId, UtilityDefinition> table = new EnumMap<>(UtilityId.class);

        // id | throw | detonation | effect | fuse | radius | damage | impulse | duration | cd | count
        put(table, UtilityId.FRAG, 800f, DetonationMode.FUSE, UtilityEffect.BLAST,
            2.5f, 350f, 100f, 950f, 0f, 1.0f, false);
        put(table, UtilityId.IMPACT, 850f, DetonationMode.CONTACT, UtilityEffect.BLAST,
            0f, 280f, 85f, 750f, 0f, 1.2f, false);
        put(table, UtilityId.SMOKE, 700f, DetonationMode.FUSE, UtilityEffect.SMOKE_CLOUD,
            1.0f, 250f, 0f, 0f, 8.0f, 0.8f, false);
        put(table, UtilityId.STUN, 720f, DetonationMode.FUSE, UtilityEffect.STUN,
            1.8f, 600f, 0f, 0f, 0f, 1.0f, false);
        put(table, UtilityId.MOLOTOV, 650f, DetonationMode.CONTACT, UtilityEffect.FIRE,
            0f, 180f, 21f, 0f, 6.0f, 1.2f, false);
        put(table, UtilityId.POISON_SMOKE, 680f, DetonationMode.FUSE, UtilityEffect.TOXIC_CLOUD,
            1.2f, 220f, 12f, 0f, 7.0f, 1.0f, false);
        put(table, UtilityId.FLASHBANG, 750f, DetonationMode.FUSE, UtilityEffect.FLASH,
            1.5f, 300f, 0f, 0f, 3.0f, 0.8f, false);

        // Provisional: the plan's claymore row is all dashes apart from "placed", "proximity"
        // and "directional cone blast". Sized between the impact grenade and the frag, because
        // it is a trap you walk into rather than something thrown at you.
        put(table, UtilityId.CLAYMORE, 0f, DetonationMode.PROXIMITY, UtilityEffect.DIRECTIONAL_BLAST,
            0f, 220f, 110f, 600f, 0f, 1.5f, 1, true);

        // Collections.unmodifiableMap rather than Map.copyOf: Map.copyOf gives an immutable map
        // with unspecified iteration order, which would silently scramble the enum ordering.
        return Collections.unmodifiableMap(table);
    }

    private static void put(
        Map<UtilityId, UtilityDefinition> table,
        UtilityId id,
        float throwForce,
        DetonationMode detonation,
        UtilityEffect effect,
        float fuseSeconds,
        float radius,
        float damage,
        float impulse,
        float durationSeconds,
        float cooldownSeconds,
        boolean provisional) {
        put(table, id, throwForce, detonation, effect, fuseSeconds, radius, damage, impulse,
            durationSeconds, cooldownSeconds, UtilityConfig.DEFAULT_CARRIED_COUNT, provisional);
    }

    private static void put(
        Map<UtilityId, UtilityDefinition> table,
        UtilityId id,
        float throwForce,
        DetonationMode detonation,
        UtilityEffect effect,
        float fuseSeconds,
        float radius,
        float damage,
        float impulse,
        float durationSeconds,
        float cooldownSeconds,
        int carriedCount,
        boolean provisional) {
        table.put(id, new UtilityDefinition(
            id, throwForce, detonation, effect, fuseSeconds, radius, damage, impulse,
            durationSeconds, cooldownSeconds, carriedCount, provisional));
    }
}
