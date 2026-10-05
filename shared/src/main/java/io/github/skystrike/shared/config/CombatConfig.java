package io.github.skystrike.shared.config;

/**
 * Hit-zone ratios, damage multipliers, distance falloff and friendly-fire rules (mechanics §4).
 */
public final class CombatConfig {

    public static final float MAX_HEALTH = 150f;
    public static final float HEAD_ZONE_FRACTION = 0.28f;
    public static final float HEAD_DAMAGE_MULTIPLIER = 2.0f;
    public static final float BODY_DAMAGE_MULTIPLIER = 1.0f;

    private CombatConfig() {
    }
}
