package io.github.skystrike.shared.map;

import io.github.skystrike.shared.audio.EffectSoundTable;
import io.github.skystrike.shared.audio.SoundSpec;
import io.github.skystrike.shared.effect.EffectType;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * The one shared table of surface-material properties (plan D7).
 *
 * <p>Every material declares the same seven facts about itself: how much damage one of its blocks
 * survives, how much of each {@link DamageSource}'s damage it accepts, the fill and outline
 * colours a renderer tints it with, and the one effect a hit on it raises — which also decides how
 * that hit sounds, through {@link EffectSoundTable}. Nothing here is a kind check: a
 * {@code breakable stone} and a {@code breakable wood} differ because their materials do, and a
 * kind added later inherits the whole behaviour by naming a material.
 *
 * <p><b>Retention is where immunity lives.</b> A source whose retention is {@code 0} cannot hurt
 * the material at all — that is "stone takes no bullet damage" and "an energy block only answers
 * explosions" as data, not as a comparison against a source's name. A retention also does not have
 * to be a percentage of a real number: it multiplies whatever raw damage the calling system
 * measured, so the bullet, blast and melee paths stay free to have their own falloff.
 *
 * <p><b>Colours are packed RGBA8888</b> ({@code 0xRRGGBBAA}, red in the most significant byte) —
 * the layout LibGDX's {@code Color(int)} constructor reads, chosen so the core renderer can build
 * its palette colour from the shared row in one call while shared still contains no LibGDX type.
 *
 * <p><b>The pool is material x block.</b> {@link #durabilityPool(MapBlock)} multiplies the
 * material's durability by the block's authored multiplier, which is how one cracked crate and one
 * reinforced crate can share a material and still take a different number of hits.
 */
public final class MaterialTable {

    private static final Map<Material, Profile> PROFILES = buildProfiles();

    private MaterialTable() {
    }

    /**
     * One material row.
     *
     * @param durability hit points of a single block of this material, before the block's per-block
     *                   multiplier; only a {@link DamageRule#DURABILITY} block ever spends it
     * @param bulletRetention fraction of a round's damage the surface accepts, {@code 0} = immune
     * @param blastRetention fraction of an explosion's damage at the contact point, {@code 0} = immune
     * @param meleeRetention fraction of a swing's damage, {@code 0} = immune
     * @param tintRgba packed {@code 0xRRGGBBAA} fill colour, alpha included for translucent panes
     * @param lineRgba packed {@code 0xRRGGBBAA} outline colour
     * @param impactEffect the visual a hit on this surface raises; its recording follows from
     *                     {@link EffectSoundTable}, so an impact's look and its sound are one
     *                     decision rather than two tables that can disagree
     */
    public record Profile(
            float durability,
            float bulletRetention,
            float blastRetention,
            float meleeRetention,
            int tintRgba,
            int lineRgba,
            EffectType impactEffect) {

        public Profile {
            if (!Float.isFinite(durability) || durability <= 0f) {
                throw new IllegalArgumentException(
                    "material durability must be finite and positive: " + durability);
            }
            requireRetention(bulletRetention, "bulletRetention");
            requireRetention(blastRetention, "blastRetention");
            requireRetention(meleeRetention, "meleeRetention");
            Objects.requireNonNull(impactEffect, "impactEffect");
        }

        /** How much of {@code rawDamage} reaches a block of this material from {@code source}. */
        public float acceptedDamage(DamageSource source, float rawDamage) {
            Objects.requireNonNull(source, "source");
            if (!(rawDamage > 0f)) {
                return 0f;
            }
            return rawDamage * retention(source);
        }

        /** The fraction of {@code source}'s damage this material accepts; never negative. */
        public float retention(DamageSource source) {
            Objects.requireNonNull(source, "source");
            return switch (source) {
                case BULLET -> bulletRetention;
                case BLAST -> blastRetention;
                case MELEE -> meleeRetention;
            };
        }

        private static void requireRetention(float value, String name) {
            if (!Float.isFinite(value) || value < 0f) {
                throw new IllegalArgumentException(name + " must be finite and not negative: " + value);
            }
        }
    }

    /** The immutable row for {@code material}. Never null. */
    public static Profile profileFor(Material material) {
        Objects.requireNonNull(material, "material");
        Profile profile = PROFILES.get(material);
        if (profile == null) {
            throw new IllegalArgumentException("no material profile for: " + material);
        }
        return profile;
    }

    /**
     * True when {@code block} can be hurt by {@code source} at all: its kind must carry a
     * destructible {@link DamageRule} and its material must accept a non-zero fraction of that
     * source's damage.
     *
     * <p>This is the one question a damage-dealing system asks before it bothers measuring its own
     * falloff, and the answer comes entirely from the two shared tables.
     */
    public static boolean takesDamage(MapBlock block, DamageSource source) {
        Objects.requireNonNull(block, "block");
        if (BlockRules.damage(block.kind()) == DamageRule.INDESTRUCTIBLE) {
            return false;
        }
        return profileFor(block.material()).retention(source) > 0f;
    }

    /**
     * The damage pool of one block: its material's durability scaled by the block's authored
     * multiplier.
     *
     * <p>{@link Float#POSITIVE_INFINITY} for a block whose kind is permanent. Callers that only
     * want to know whether damage lands ask {@link #takesDamage} first; this answers "how much
     * would it take", which is what a durability readout or a crack overlay needs.
     */
    public static float durabilityPool(MapBlock block) {
        Objects.requireNonNull(block, "block");
        if (BlockRules.damage(block.kind()) == DamageRule.INDESTRUCTIBLE) {
            return Float.POSITIVE_INFINITY;
        }
        return profileFor(block.material()).durability() * block.durabilityMultiplier();
    }

    /**
     * How much damage {@code rawDamage} from {@code source} deals to {@code block}, after the
     * material's retention.
     *
     * <p>Zero means the surface ignored the hit. The per-block multiplier is deliberately absent:
     * it scales the pool, so a reinforced crate survives more hits rather than taking less damage
     * per hit — the two are not the same thing once a hit is chipped away by falloff.
     */
    public static float acceptedDamage(MapBlock block, DamageSource source, float rawDamage) {
        Objects.requireNonNull(block, "block");
        return profileFor(block.material()).acceptedDamage(source, rawDamage);
    }

    /**
     * The recording that plays for {@code material}'s impact.
     *
     * <p>Deliberately derived rather than duplicated: the material names its impact effect and the
     * phase-9 catalogue already decides what that effect sounds like, so a material can never
     * point at a sound the audio check has not verified. Never null; silence is
     * {@link SoundSpec#SILENT}.
     */
    public static SoundSpec impactSound(Material material) {
        return EffectSoundTable.specFor(profileFor(material).impactEffect());
    }

    private static Map<Material, Profile> buildProfiles() {
        Map<Material, Profile> profiles = new EnumMap<>(Material.class);

        // Stone: the shipped arena's material. Tough, and it does not care about bullets at all —
        // a stone block wall is permanent even where a breakable stone block is authored, because
        // the kind's rule row, not the material, decides whether there is a pool to spend.
        put(profiles, Material.STONE, new Profile(
            420f, 0f, 0.35f, 0f,
            0x384559ff, 0x617594ff, EffectType.BULLET_IMPACT_CONCRETE));

        // Metal: tougher than the wood it usually frames, chipped slowly by rounds, loud impacts.
        // "Reflects nothing" is a decision, not an omission: a ricochet system would be a second
        // ballistics model, and this material simply does not have one.
        put(profiles, Material.METAL, new Profile(
            320f, 0.35f, 0.8f, 0.5f,
            0x666b75ff, 0xa8b3c2ff, EffectType.BULLET_IMPACT_METAL));

        // Wood: the material bullets are meant to open. Every source reaches it at full strength.
        put(profiles, Material.WOOD, new Profile(
            120f, 1f, 1f, 1f,
            0x614f38ff, 0x997a54ff, EffectType.BULLET_IMPACT_WOOD));

        // Glass: brittle on purpose. Its durability is small even though a GLASS-kind block never
        // spends it (the row shatters instead), so a breakable pane that someone authors with a
        // large multiplier still reads as glass rather than as a window-shaped wall.
        put(profiles, Material.GLASS, new Profile(
            24f, 1f, 1f, 1f,
            0x66adcc4d, 0xa8e6faff, EffectType.GLASS_SHATTER));

        // Energy: scenario geometry. Nothing about it responds to a bullet or a bat; explosives
        // are the whole interaction. Its impact effect borrows the metal sparks until an energy
        // effect exists — the honest placeholder, named here rather than hidden in a switch.
        put(profiles, Material.ENERGY, new Profile(
            260f, 0f, 1f, 0f,
            0x59a6b3ff, 0x99e6f2ff, EffectType.BULLET_IMPACT_METAL));

        if (profiles.size() != Material.values().length) {
            throw new IllegalStateException("every Material must have exactly one profile");
        }
        return Collections.unmodifiableMap(profiles);
    }

    private static void put(Map<Material, Profile> profiles, Material material, Profile profile) {
        Profile previous = profiles.put(material, profile);
        if (previous != null) {
            throw new IllegalStateException("duplicate material profile for: " + material);
        }
    }
}
