package io.github.skystrike.shared.gadget;

import io.github.skystrike.shared.config.GadgetConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The gadget table from mechanics §7, as data: one definition per real gadget, keyed by the
 * append-only {@link GadgetId}.
 *
 * <p>Definitions are records with primitive fields only, so this registry returns the shared
 * instance rather than a copy — the same reasoning as {@code UtilityRegistry}, and the one
 * difference from {@code WeaponRegistry}, which must copy.
 *
 * <p>{@link GadgetId#NONE} deliberately has no row. An empty slot has no durability, no
 * behaviour and no entity; asking for its definition is a programming error and throws, while
 * the wire-facing {@link #ofOrdinalOrNull(int)} degrades to {@code null} instead so a hostile
 * packet cannot crash the receiving side.
 */
public final class GadgetRegistry {

    private static final Map<GadgetId, GadgetDefinition> DEFINITIONS = build();

    private GadgetRegistry() {
    }

    /** The definition for a real gadget. Never null, never a copy, never mutable. */
    public static GadgetDefinition of(GadgetId id) {
        GadgetDefinition definition = DEFINITIONS.get(id);
        if (definition == null) {
            throw new IllegalArgumentException("no definition for gadget: " + id);
        }
        return definition;
    }

    /** As {@link #of(GadgetId)}, from an ordinal. Throws on invalid ordinals and on NONE. */
    public static GadgetDefinition ofOrdinal(int ordinal) {
        return of(GadgetId.fromOrdinal(ordinal));
    }

    /**
     * Defensive lookup for wire values: {@code null} for {@link GadgetId#NONE}, for a negative
     * ordinal and for anything out of range, a definition for everything real.
     */
    public static GadgetDefinition ofOrdinalOrNull(int ordinal) {
        GadgetId id = GadgetId.fromOrdinalOrNone(ordinal);
        return id.isReal() ? of(id) : null;
    }

    /** Every real gadget's definition, in {@link GadgetId} order. */
    public static List<GadgetDefinition> all() {
        // Built from the enum rather than from the map's values, because iteration order is
        // part of what callers expect and only the enum guarantees it.
        GadgetId[] ids = GadgetId.values();
        List<GadgetDefinition> definitions = new ArrayList<>(ids.length - 1);
        for (GadgetId id : ids) {
            if (id.isReal()) {
                definitions.add(of(id));
            }
        }
        return List.copyOf(definitions);
    }

    /** Durability a freshly equipped {@code id} starts with; 0 for NONE and for the fuel tank. */
    public static float maxDurability(GadgetId id) {
        return id != null && id.isReal() ? of(id).maxDurability() : 0f;
    }

    /** Display name for any wire id in the gadget range, for the kill feed. */
    public static String displayNameForWireId(int wireId) {
        GadgetId id = GadgetId.fromWireId(wireId);
        return id == null ? "" : id.displayName();
    }

    private static Map<GadgetId, GadgetDefinition> build() {
        Map<GadgetId, GadgetDefinition> table = new EnumMap<>(GadgetId.class);

        // id | durability | spawns entity (mechanics §7.1–§7.4; nothing here is provisional —
        // every number is quoted by the plan, and the per-gadget tuning sits in GadgetConfig).
        table.put(GadgetId.DRONE, new GadgetDefinition(
            GadgetId.DRONE, GadgetConfig.DRONE_HEALTH, true, false));
        table.put(GadgetId.SHIELD, new GadgetDefinition(
            GadgetId.SHIELD, GadgetConfig.SHIELD_DURABILITY, false, false));
        table.put(GadgetId.FUEL_TANK, new GadgetDefinition(
            GadgetId.FUEL_TANK, 0f, false, false));
        table.put(GadgetId.CAMERA, new GadgetDefinition(
            GadgetId.CAMERA, GadgetConfig.CAMERA_HEALTH, true, false));

        // Collections.unmodifiableMap rather than Map.copyOf: Map.copyOf gives an immutable map
        // with unspecified iteration order, which would silently scramble the enum ordering.
        return Collections.unmodifiableMap(table);
    }
}
