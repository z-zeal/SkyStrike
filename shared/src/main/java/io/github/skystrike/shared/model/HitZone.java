package io.github.skystrike.shared.model;

import io.github.skystrike.shared.config.CombatConfig;

/**
 * Where a round landed on a player (mechanics §4.1).
 *
 * <p>The multiplier lives on the zone so there is one place that answers "what is a headshot
 * worth". {@link #FUEL_TANK} resolves only while the target's loadout carries a worn, intact
 * fuel tank (Phase 6); its detonation is applied by the server's gadget system, not by the
 * multiplier here.
 */
public enum HitZone {

    /** Top 28% of the player's current height. Crouching lowers it with the body. */
    HEAD(CombatConfig.HEAD_DAMAGE_MULTIPLIER, "head"),

    /** Everything below the head zone. */
    BODY(CombatConfig.BODY_DAMAGE_MULTIPLIER, "body"),

    /** Rear-mounted fuel tank gadget. Detonates instead of taking normal damage. */
    FUEL_TANK(CombatConfig.FUEL_TANK_DAMAGE_MULTIPLIER, "fuel tank");

    private final float damageMultiplier;
    private final String displayName;

    HitZone(float damageMultiplier, String displayName) {
        this.damageMultiplier = damageMultiplier;
        this.displayName = displayName;
    }

    public float damageMultiplier() {
        return damageMultiplier;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isHeadshot() {
        return this == HEAD;
    }

    public static HitZone fromOrdinal(int ordinal) {
        HitZone[] zones = values();
        if (ordinal < 0 || ordinal >= zones.length) {
            return BODY;
        }
        return zones[ordinal];
    }
}
