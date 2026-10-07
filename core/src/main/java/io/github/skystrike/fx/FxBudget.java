package io.github.skystrike.fx;

import java.util.Locale;

/**
 * The effects quality budget (build plan M7 §8.2, effects plan §11): one object, resolved from the
 * client's quality settings, read by every FX subsystem. No subsystem hardcodes its own cap.
 *
 * <p>The caps are the effects plan's tier table — GPU particles, CPU collision particles and the
 * effect-light share. Degrade gracefully, never binary: the low tier still has fog, lights and
 * particles, just fewer of them.
 *
 * <p>Two sources feed the tier, in priority order: the live {@code quality} cvar (low/mid/high) and
 * the settings object's quality tier (Low/Medium/High/Ultra, mapped onto the same three tiers).
 * The client re-resolves the tier every frame, so a settings change applies without a restart.
 *
 * <p>The shared {@code LightPool} keeps its own hard capacity from M6; this budget governs how many
 * of those slots the effect system may hold at once, reserving room for player lights.
 */
public final class FxBudget {

    /** The three quality tiers the effects plan defines. */
    public enum Tier {
        LOW,
        MID,
        HIGH
    }

    // Effects plan §11: GPU particles per tier.
    public static final int GPU_PARTICLES_LOW = 500;
    public static final int GPU_PARTICLES_MID = 1500;
    public static final int GPU_PARTICLES_HIGH = 6000;

    // Effects plan §11: CPU collision particles per tier.
    public static final int CPU_PARTICLES_LOW = 60;
    public static final int CPU_PARTICLES_MID = 150;
    public static final int CPU_PARTICLES_HIGH = 400;

    /**
     * Effect lights per tier: a share of the effects plan's simultaneous-light budget, leaving the
     * rest of the shared pool's slots for M6's player lights.
     */
    public static final int EFFECT_LIGHTS_LOW = 2;
    public static final int EFFECT_LIGHTS_MID = 6;
    public static final int EFFECT_LIGHTS_HIGH = 16;

    private Tier tier = Tier.MID;

    public Tier tier() {
        return tier;
    }

    /** Applies a new tier. Safe to call every frame; a no-op when the tier did not change. */
    public void setTier(Tier tier) {
        this.tier = tier == null ? Tier.MID : tier;
    }

    /** Simultaneous GPU-tier particles across both blend batches. */
    public int gpuParticleCap() {
        return switch (tier) {
            case LOW -> GPU_PARTICLES_LOW;
            case MID -> GPU_PARTICLES_MID;
            case HIGH -> GPU_PARTICLES_HIGH;
        };
    }

    /** Simultaneous CPU-tier (colliding) particles. */
    public int cpuParticleCap() {
        return switch (tier) {
            case LOW -> CPU_PARTICLES_LOW;
            case MID -> CPU_PARTICLES_MID;
            case HIGH -> CPU_PARTICLES_HIGH;
        };
    }

    /** Effect lights the particle system may hold in the shared light pool at once. */
    public int effectLightCap() {
        return switch (tier) {
            case LOW -> EFFECT_LIGHTS_LOW;
            case MID -> EFFECT_LIGHTS_MID;
            case HIGH -> EFFECT_LIGHTS_HIGH;
        };
    }

    /**
     * Parses the {@code quality} cvar's canonical value ("low"/"mid"/"high", case-insensitive).
     *
     * @return the tier, or null when the name is not a tier (caller falls back to settings)
     */
    public static Tier parseTier(String name) {
        if (name == null) {
            return null;
        }
        return switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "low" -> Tier.LOW;
            case "mid", "medium" -> Tier.MID;
            case "high", "ultra" -> Tier.HIGH;
            default -> null;
        };
    }

    /**
     * Maps the settings object's quality tier (0 Low … 3 Ultra) onto the effects tiers. The
     * settings UI speaks four names; the effects budget speaks three, so Ultra joins High.
     */
    public static Tier fromSettingsTier(int settingsTier) {
        if (settingsTier <= 0) {
            return Tier.LOW;
        }
        if (settingsTier == 1) {
            return Tier.MID;
        }
        return Tier.HIGH;
    }
}
