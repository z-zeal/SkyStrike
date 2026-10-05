package io.github.skystrike.shared.weapons;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * The melee table: id → definition, for the four melee weapons of mechanics §5.2.
 *
 * <p>Like {@link WeaponRegistry}, lookups <b>return copies</b>: definitions are shared, static
 * truth; live state (swing cooldowns) belongs to whoever is swinging.
 */
public final class MeleeRegistry {

    private MeleeRegistry() {
    }

    /** Definition of one melee weapon, as a fresh copy. Never null. */
    public static MeleeDefinition of(MeleeId id) {
        MeleeDefinition definition = TABLE.get(id);
        if (definition == null) {
            throw new IllegalArgumentException("no definition for melee weapon: " + id);
        }
        return definition.copy();
    }

    /** Definition by ordinal, falling back to the default melee weapon. */
    public static MeleeDefinition ofOrdinal(int ordinal) {
        return of(MeleeId.fromOrdinal(ordinal));
    }

    /** The whole table, for validation and tooling. Values are copies. */
    public static Map<MeleeId, MeleeDefinition> all() {
        Map<MeleeId, MeleeDefinition> out = new EnumMap<>(MeleeId.class);
        for (MeleeId id : MeleeId.values()) {
            out.put(id, of(id));
        }
        return Collections.unmodifiableMap(out);
    }

    private static final Map<MeleeId, MeleeDefinition> TABLE = buildTable();

    private static Map<MeleeId, MeleeDefinition> buildTable() {
        Map<MeleeId, MeleeDefinition> table = new EnumMap<>(MeleeId.class);
        //                                                                 dmg   /s    range knockback
        table.put(MeleeId.COMBAT_KNIFE, new MeleeDefinition(MeleeId.COMBAT_KNIFE, 50f, 2.0f,   60f, 200f));
        table.put(MeleeId.SHOVEL,       new MeleeDefinition(MeleeId.SHOVEL,       65f, 1.667f, 70f, 250f));
        table.put(MeleeId.BASEBALL_BAT, new MeleeDefinition(MeleeId.BASEBALL_BAT, 65f, 1.5f,   80f, 350f));
        table.put(MeleeId.KATANA,       new MeleeDefinition(MeleeId.KATANA,       75f, 2.5f,   90f, 150f));
        return Collections.unmodifiableMap(table);
    }
}
