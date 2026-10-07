package io.github.skystrike.fx.particle;

import com.badlogic.gdx.graphics.Color;
import io.github.skystrike.shared.effect.EffectType;
import io.github.skystrike.shared.utility.UtilityId;
import io.github.skystrike.shared.utility.UtilityRegistry;
import java.util.List;

/**
 * The effect catalogue as data (build plan M7 §8.3, effects plan §9): every {@link EffectType}
 * maps to a recipe of timed emitter phases plus at most one attached light, so a new effect is a
 * table row, not a code path.
 *
 * <p>Phasing exists so a frag reads as a sequence — flash, ring, fireball, debris, smoke — at
 * 0.00/0.03/0.05/0.08/0.10 s. Phases are driven from the particle system's pending queue: an
 * event schedules its phases and nothing happens synchronously, so the network path only ever
 * enqueues.
 *
 * <p>Every spawn position of an occlusion-tested burst is tested against the SDF: no particle may
 * appear on the far side of a wall from its source (effects plan §9 universal rule). The attached
 * lights are not occlusion-tested here — the light pass applies its own SDF soft shadows and
 * visibility gate, which is what lets a frag round a corner light the wall face without showing
 * particles through it.
 *
 * <p>Numbers are the effects plan's starting points, tuned for readability at the arena's scale;
 * they are data and expected to be rebalanced by eye.
 */
public final class EmitterLibrary {

    /** One phase of a recipe: an emitter that fires this many seconds after the event. */
    public record TimedEmitter(float delaySeconds, EmitterConfig emitter) {
    }

    /**
     * An attached light, animated over its duration: colour and intensity both lerp from their
     * start to their end value, with a deterministic flicker on top (a fire zone's flicker, an
     * explosion's none). The particle system owns the per-frame updates and releases the light's
     * pool handle when the duration elapses.
     */
    public record EffectLight(
        float radius,
        Color color,
        Color endColor,
        float intensity,
        float endIntensity,
        float durationSeconds,
        float flickerAmplitude,
        boolean highPriority) {
    }

    /** Everything an effect type does when its event arrives: timed phases plus one light. */
    public record EffectRecipe(List<TimedEmitter> emitters, EffectLight light) {
    }

    /** The recipe for a type with no presentation; callers skip it. */
    public static final EffectRecipe NONE = new EffectRecipe(List.of(), null);

    private static final Color WHITE = new Color(1f, 1f, 1f, 1f);
    private static final Color FLASH_WHITE = new Color(1f, 0.98f, 0.9f, 1f);
    private static final Color FLASH_YELLOW = new Color(1f, 0.92f, 0.55f, 1f);
    private static final Color ORANGE = new Color(1f, 0.55f, 0.12f, 1f);
    private static final Color EMBER = new Color(1f, 0.8f, 0.35f, 1f);
    private static final Color HOT = new Color(1f, 0.7f, 0.25f, 1f);
    private static final Color DEBRIS = new Color(0.45f, 0.4f, 0.36f, 0.95f);
    private static final Color DEBRIS_END = new Color(0.3f, 0.27f, 0.24f, 0f);
    private static final Color SMOKE = new Color(0.16f, 0.16f, 0.18f, 0.85f);
    private static final Color SMOKE_END = new Color(0.1f, 0.1f, 0.12f, 0f);
    private static final Color CLOUD = new Color(0.62f, 0.65f, 0.68f, 0.55f);
    private static final Color CLOUD_END = new Color(0.5f, 0.53f, 0.56f, 0f);
    private static final Color POISON = new Color(0.35f, 0.6f, 0.3f, 0.5f);
    private static final Color POISON_END = new Color(0.25f, 0.45f, 0.22f, 0f);
    private static final Color GLASS = new Color(0.85f, 0.95f, 1f, 1f);
    private static final Color GLASS_END = new Color(0.4f, 0.6f, 0.8f, 0f);
    private static final Color FLAME = new Color(1f, 0.75f, 0.3f, 0.95f);
    private static final Color FLAME_END = new Color(0.95f, 0.3f, 0.05f, 0f);
    private static final Color CONCRETE_DUST = new Color(0.75f, 0.72f, 0.68f, 0.8f);
    private static final Color CONCRETE_DUST_END = new Color(0.6f, 0.58f, 0.55f, 0f);
    private static final Color CHIP = new Color(0.55f, 0.52f, 0.48f, 0.95f);
    private static final Color CHIP_END = new Color(0.4f, 0.38f, 0.35f, 0f);
    private static final Color SPARK = new Color(1f, 0.95f, 0.6f, 1f);
    private static final Color SPARK_END = new Color(1f, 0.6f, 0.2f, 0f);
    private static final Color DROPLET = new Color(1f, 0.85f, 0.4f, 1f);
    private static final Color SPLINTER = new Color(0.45f, 0.3f, 0.16f, 0.95f);
    private static final Color SPLINTER_END = new Color(0.3f, 0.2f, 0.1f, 0f);
    private static final Color WOOD_DUST = new Color(0.4f, 0.32f, 0.22f, 0.75f);
    private static final Color WOOD_DUST_END = new Color(0.3f, 0.24f, 0.16f, 0f);
    private static final Color BRASS = new Color(0.9f, 0.7f, 0.3f, 1f);
    private static final Color BRASS_END = new Color(0.9f, 0.7f, 0.3f, 0f);

    private EmitterLibrary() {
    }

    /** The recipe for an effect type; {@link #NONE} for null or unknown types. */
    public static EffectRecipe recipeFor(EffectType type) {
        if (type == null) {
            return NONE;
        }
        return switch (type) {
            case FRAG_EXPLOSION -> fragExplosion();
            case IMPACT_EXPLOSION -> impactExplosion();
            case SMOKE_BURST -> smokeBurst();
            case MOLOTOV_SPLASH -> molotovSplash();
            case FIRE_ZONE -> fireZone();
            case POISON_BURST -> poisonBurst();
            case FLASH_DETONATION -> flashDetonation();
            case CLAYMORE_BLAST -> claymoreBlast();
            case BULLET_IMPACT_CONCRETE -> bulletImpactConcrete();
            case BULLET_IMPACT_METAL -> bulletImpactMetal();
            case BULLET_IMPACT_WOOD -> bulletImpactWood();
            case MUZZLE_FLASH -> muzzleFlash();
            case SHELL_EJECT -> shellEject();
        };
    }

    // --- Explosions -----------------------------------------------------------------------------

    /**
     * The frag schedule (effects plan §9): white-yellow flash plus a high-priority light at 0.00 s,
     * inner ring at 0.03, fireball puffs at 0.05, debris and embers at 0.08, long-lived smoke at
     * 0.10. Every burst is occlusion-tested: a frag behind a wall lights the wall face but spawns
     * no particles through it.
     */
    private static EffectRecipe fragExplosion() {
        return new EffectRecipe(List.of(
            phase(0.00f, EmitterConfig.builder("frag.flash")
                .count(28)
                .lifetime(0.10f, 0.22f)
                .size(70f, 8f)
                .color(FLASH_YELLOW, ORANGE)
                .speed(350f, 750f)
                .drag(2.5f)
                .turbulence(60f)
                .occlusionTested(true)
                .build()),
            phase(0.03f, EmitterConfig.builder("frag.ring")
                .count(18)
                .lifetime(0.12f, 0.20f)
                .size(46f, 6f)
                .color(HOT, ORANGE)
                .speed(220f, 420f)
                .drag(2f)
                .turbulence(30f)
                .occlusionTested(true)
                .build()),
            phase(0.05f, EmitterConfig.builder("frag.fireball")
                .count(14)
                .lifetime(0.35f, 0.60f)
                .size(30f, 58f)
                .color(ORANGE, new Color(0.9f, 0.25f, 0.05f, 0f))
                .speed(90f, 200f)
                .gravity(-80f)
                .drag(1.2f)
                .turbulence(90f)
                .occlusionTested(true)
                .build()),
            phase(0.08f, EmitterConfig.builder("frag.debris")
                .count(10)
                .lifetime(0.5f, 0.9f)
                .size(9f, 6f)
                .color(DEBRIS, DEBRIS_END)
                .speed(220f, 480f)
                .gravity(-650f)
                .drag(0.4f)
                .turbulence(20f)
                .blend(EmitterConfig.Blend.ALPHA)
                .occlusionTested(true)
                .build()),
            phase(0.08f, EmitterConfig.builder("frag.embers")
                .count(22)
                .lifetime(0.4f, 1.1f)
                .size(5f, 1f)
                .color(EMBER, SPARK_END)
                .speed(150f, 400f)
                .gravity(-350f)
                .drag(0.8f)
                .turbulence(50f)
                .occlusionTested(true)
                .build()),
            phase(0.10f, EmitterConfig.builder("frag.smoke")
                .count(12)
                .lifetime(1.2f, 2.2f)
                .size(34f, 95f)
                .color(SMOKE, SMOKE_END)
                .speed(25f, 70f)
                .gravity(-20f)
                .drag(1f)
                .turbulence(110f)
                .blend(EmitterConfig.Blend.ALPHA)
                .occlusionTested(true)
                .build())),
            new EffectLight(
                330f,
                FLASH_WHITE,
                new Color(1f, 0.45f, 0.1f, 1f),
                0.95f,
                0f,
                0.35f,
                0.15f,
                true));
    }

    /** The impact grenade: the frag schedule, smaller and faster. */
    private static EffectRecipe impactExplosion() {
        return new EffectRecipe(List.of(
            phase(0.00f, EmitterConfig.builder("impact.flash")
                .count(18)
                .lifetime(0.08f, 0.18f)
                .size(50f, 6f)
                .color(FLASH_YELLOW, ORANGE)
                .speed(280f, 600f)
                .drag(2.5f)
                .turbulence(50f)
                .occlusionTested(true)
                .build()),
            phase(0.03f, EmitterConfig.builder("impact.ring")
                .count(12)
                .lifetime(0.10f, 0.18f)
                .size(34f, 5f)
                .color(HOT, ORANGE)
                .speed(180f, 340f)
                .drag(2f)
                .turbulence(30f)
                .occlusionTested(true)
                .build()),
            phase(0.05f, EmitterConfig.builder("impact.fireball")
                .count(9)
                .lifetime(0.30f, 0.50f)
                .size(22f, 44f)
                .color(ORANGE, new Color(0.9f, 0.25f, 0.05f, 0f))
                .speed(70f, 160f)
                .gravity(-80f)
                .drag(1.2f)
                .turbulence(80f)
                .occlusionTested(true)
                .build()),
            phase(0.08f, EmitterConfig.builder("impact.debris")
                .count(7)
                .lifetime(0.4f, 0.8f)
                .size(7f, 5f)
                .color(DEBRIS, DEBRIS_END)
                .speed(180f, 400f)
                .gravity(-650f)
                .drag(0.4f)
                .turbulence(20f)
                .blend(EmitterConfig.Blend.ALPHA)
                .occlusionTested(true)
                .build()),
            phase(0.08f, EmitterConfig.builder("impact.embers")
                .count(14)
                .lifetime(0.35f, 1.0f)
                .size(4f, 1f)
                .color(EMBER, SPARK_END)
                .speed(120f, 320f)
                .gravity(-350f)
                .drag(0.8f)
                .turbulence(50f)
                .occlusionTested(true)
                .build()),
            phase(0.10f, EmitterConfig.builder("impact.smoke")
                .count(8)
                .lifetime(1.0f, 1.8f)
                .size(26f, 70f)
                .color(SMOKE, SMOKE_END)
                .speed(20f, 55f)
                .gravity(-20f)
                .drag(1f)
                .turbulence(100f)
                .blend(EmitterConfig.Blend.ALPHA)
                .occlusionTested(true)
                .build())),
            new EffectLight(
                240f,
                FLASH_WHITE,
                new Color(1f, 0.45f, 0.1f, 1f),
                0.7f,
                0f,
                0.28f,
                0.15f,
                true));
    }

    // --- Clouds ---------------------------------------------------------------------------------

    /**
     * The smoke grenade: a billowing alpha cloud whose puffs grow into the zone's own radius, so
     * the visual and the vision-blocking shader circle are the same thing (they already share
     * {@code SmokeVolume}). Long-lived, heavily turbulent, occlusion-tested — particles spawn
     * only where unoccluded.
     */
    private static EffectRecipe smokeBurst() {
        return new EffectRecipe(List.of(
            phase(0.00f, EmitterConfig.builder("smoke.cloud")
                .count(46)
                .lifetime(2.5f, 7.5f)
                .size(26f, 320f)
                .color(CLOUD, CLOUD_END)
                .speed(20f, 55f)
                .gravity(-10f)
                .drag(0.9f)
                .turbulence(130f)
                .blend(EmitterConfig.Blend.ALPHA)
                .occlusionTested(true)
                .build())),
            null);
    }

    /** The poison cloud: smaller, greener, same growth rule. */
    private static EffectRecipe poisonBurst() {
        return new EffectRecipe(List.of(
            phase(0.00f, EmitterConfig.builder("poison.cloud")
                .count(40)
                .lifetime(2.5f, 6.5f)
                .size(24f, 280f)
                .color(POISON, POISON_END)
                .speed(20f, 50f)
                .gravity(-10f)
                .drag(0.9f)
                .turbulence(120f)
                .blend(EmitterConfig.Blend.ALPHA)
                .occlusionTested(true)
                .build())),
            null);
    }

    // --- Molotov --------------------------------------------------------------------------------

    /**
     * The molotov's impact: glass sparks in every direction plus a fire splash along the surface
     * tangent the event's angle carries, and a short flickering light.
     */
    private static EffectRecipe molotovSplash() {
        return new EffectRecipe(List.of(
            phase(0.00f, EmitterConfig.builder("molotov.glass")
                .count(16)
                .lifetime(0.15f, 0.35f)
                .size(7f, 1f)
                .color(GLASS, GLASS_END)
                .speed(120f, 320f)
                .gravity(-500f)
                .drag(3f)
                .turbulence(40f)
                .occlusionTested(true)
                .build()),
            phase(0.00f, EmitterConfig.builder("molotov.splash")
                .count(20)
                .lifetime(0.25f, 0.50f)
                .size(10f, 26f)
                .color(FLAME, FLAME_END)
                .speed(60f, 160f)
                .spread(100f)
                .gravity(-150f)
                .drag(1.5f)
                .turbulence(60f)
                .occlusionTested(true)
                .build())),
            new EffectLight(
                110f,
                new Color(1f, 0.55f, 0.15f, 1f),
                new Color(1f, 0.3f, 0.05f, 1f),
                0.6f,
                0f,
                0.5f,
                0.35f,
                false));
    }

    /**
     * One patch of molotov fire: small rising flames plus a flickering attached light that lives
     * for the zone's own duration, so the light and the damage zone expire together.
     */
    private static EffectRecipe fireZone() {
        float zoneDuration = UtilityRegistry.of(UtilityId.MOLOTOV).durationSeconds();
        return new EffectRecipe(List.of(
            phase(0.00f, EmitterConfig.builder("fire.flames")
                .count(8)
                .lifetime(0.7f, 1.5f)
                .size(10f, 30f)
                .color(FLAME, FLAME_END)
                .speed(40f, 110f)
                .spread(50f)
                .gravity(-60f)
                .drag(1f)
                .turbulence(70f)
                .occlusionTested(true)
                .build())),
            new EffectLight(
                55f,
                new Color(1f, 0.5f, 0.12f, 1f),
                new Color(1f, 0.35f, 0.05f, 1f),
                0.55f,
                0.15f,
                zoneDuration,
                0.3f,
                false));
    }

    // --- Flash / stun ---------------------------------------------------------------------------

    /**
     * The flashbang (and stun grenade) burst: a white additive flash, residual wisps, and a
     * brief high-priority light. The blindness itself is server state on the player — the whiteout
     * post pass reads it — so this recipe is only the visible burst at the detonation point.
     */
    private static EffectRecipe flashDetonation() {
        return new EffectRecipe(List.of(
            phase(0.00f, EmitterConfig.builder("flash.burst")
                .count(30)
                .lifetime(0.08f, 0.18f)
                .size(60f, 4f)
                .color(WHITE, new Color(1f, 0.95f, 0.7f, 0f))
                .speed(300f, 650f)
                .drag(3f)
                .turbulence(20f)
                .occlusionTested(true)
                .build()),
            phase(0.00f, EmitterConfig.builder("flash.wisps")
                .count(8)
                .lifetime(0.8f, 1.6f)
                .size(18f, 55f)
                .color(new Color(0.85f, 0.85f, 0.8f, 0.6f), new Color(0.7f, 0.7f, 0.65f, 0f))
                .speed(30f, 70f)
                .gravity(-30f)
                .drag(1.2f)
                .turbulence(90f)
                .blend(EmitterConfig.Blend.ALPHA)
                .occlusionTested(true)
                .build())),
            new EffectLight(
                300f,
                WHITE,
                new Color(1f, 0.9f, 0.6f, 1f),
                1f,
                0f,
                0.12f,
                0.1f,
                true));
    }

    // --- Claymore --------------------------------------------------------------------------------

    /** The claymore's directional blast: everything the frag does, shaped into the placed cone. */
    private static EffectRecipe claymoreBlast() {
        return new EffectRecipe(List.of(
            phase(0.00f, EmitterConfig.builder("claymore.flash")
                .count(20)
                .lifetime(0.10f, 0.20f)
                .size(55f, 6f)
                .color(FLASH_YELLOW, ORANGE)
                .speed(300f, 600f)
                .spread(100f)
                .drag(2.5f)
                .turbulence(40f)
                .occlusionTested(true)
                .build()),
            phase(0.05f, EmitterConfig.builder("claymore.debris")
                .count(14)
                .lifetime(0.5f, 1.0f)
                .size(9f, 6f)
                .color(DEBRIS, DEBRIS_END)
                .speed(250f, 550f)
                .spread(100f)
                .gravity(-650f)
                .drag(0.4f)
                .turbulence(20f)
                .blend(EmitterConfig.Blend.ALPHA)
                .occlusionTested(true)
                .build()),
            phase(0.08f, EmitterConfig.builder("claymore.smoke")
                .count(8)
                .lifetime(1.0f, 2.0f)
                .size(30f, 80f)
                .color(SMOKE, SMOKE_END)
                .speed(30f, 70f)
                .spread(100f)
                .gravity(-20f)
                .drag(1f)
                .turbulence(100f)
                .blend(EmitterConfig.Blend.ALPHA)
                .occlusionTested(true)
                .build())),
            new EffectLight(
                200f,
                new Color(1f, 0.85f, 0.5f, 1f),
                new Color(1f, 0.4f, 0.08f, 1f),
                0.8f,
                0f,
                0.25f,
                0.2f,
                true));
    }

    // --- Bullet impacts --------------------------------------------------------------------------

    /** Concrete: a dust cloud plus small chips, both hugging the surface they hit. */
    private static EffectRecipe bulletImpactConcrete() {
        return new EffectRecipe(List.of(
            phase(0.00f, EmitterConfig.builder("impact.concrete.dust")
                .count(7)
                .lifetime(0.25f, 0.45f)
                .size(6f, 18f)
                .color(CONCRETE_DUST, CONCRETE_DUST_END)
                .speed(25f, 80f)
                .gravity(-250f)
                .drag(1.5f)
                .turbulence(40f)
                .blend(EmitterConfig.Blend.ALPHA)
                .occlusionTested(true)
                .build()),
            phase(0.00f, EmitterConfig.builder("impact.concrete.chips")
                .count(3)
                .lifetime(0.3f, 0.55f)
                .size(4f, 3f)
                .color(CHIP, CHIP_END)
                .speed(60f, 150f)
                .gravity(-700f)
                .drag(0.3f)
                .turbulence(10f)
                .blend(EmitterConfig.Blend.ALPHA)
                .occlusionTested(true)
                .build())),
            null);
    }

    /** Metal: bright sparks deflected forward plus molten droplets. No surface material yet. */
    private static EffectRecipe bulletImpactMetal() {
        return new EffectRecipe(List.of(
            phase(0.00f, EmitterConfig.builder("impact.metal.sparks")
                .count(10)
                .lifetime(0.1f, 0.3f)
                .size(5f, 1f)
                .color(SPARK, SPARK_END)
                .speed(150f, 400f)
                .spread(120f)
                .gravity(-400f)
                .drag(2f)
                .turbulence(30f)
                .occlusionTested(true)
                .build()),
            phase(0.00f, EmitterConfig.builder("impact.metal.droplets")
                .count(4)
                .lifetime(0.2f, 0.4f)
                .size(4f, 1f)
                .color(DROPLET, SPARK_END)
                .speed(80f, 200f)
                .spread(120f)
                .gravity(-600f)
                .drag(1f)
                .turbulence(20f)
                .occlusionTested(true)
                .build())),
            null);
    }

    /** Wood: brown splinters plus floating dust. No surface material yet. */
    private static EffectRecipe bulletImpactWood() {
        return new EffectRecipe(List.of(
            phase(0.00f, EmitterConfig.builder("impact.wood.splinters")
                .count(5)
                .lifetime(0.3f, 0.6f)
                .size(5f, 3f)
                .color(SPLINTER, SPLINTER_END)
                .speed(70f, 180f)
                .gravity(-700f)
                .drag(0.4f)
                .turbulence(15f)
                .blend(EmitterConfig.Blend.ALPHA)
                .occlusionTested(true)
                .build()),
            phase(0.00f, EmitterConfig.builder("impact.wood.dust")
                .count(6)
                .lifetime(0.25f, 0.5f)
                .size(5f, 16f)
                .color(WOOD_DUST, WOOD_DUST_END)
                .speed(25f, 70f)
                .gravity(-250f)
                .drag(1.5f)
                .turbulence(40f)
                .blend(EmitterConfig.Blend.ALPHA)
                .occlusionTested(true)
                .build())),
            null);
    }

    // --- Muzzle ----------------------------------------------------------------------------------

    /**
     * The muzzle flash: one bright core disc, a short gas burst along the barrel, and an attached
     * light that lives about one frame. The casing is a separate event on the CPU tier.
     */
    private static EffectRecipe muzzleFlash() {
        return new EffectRecipe(List.of(
            phase(0.00f, EmitterConfig.builder("muzzle.core")
                .count(1)
                .lifetime(0.05f, 0.07f)
                .size(26f, 4f)
                .color(new Color(1f, 0.95f, 0.75f, 1f), new Color(1f, 0.7f, 0.3f, 0f))
                .speed(0f, 10f)
                .build()),
            phase(0.00f, EmitterConfig.builder("muzzle.gas")
                .count(7)
                .lifetime(0.08f, 0.18f)
                .size(8f, 22f)
                .color(new Color(1f, 0.75f, 0.4f, 0.9f), new Color(0.9f, 0.4f, 0.1f, 0f))
                .speed(90f, 220f)
                .spread(50f)
                .drag(2.5f)
                .turbulence(50f)
                .build())),
            new EffectLight(
                70f,
                new Color(1f, 0.9f, 0.6f, 1f),
                new Color(1f, 0.6f, 0.2f, 1f),
                0.9f,
                0f,
                0.06f,
                0f,
                false));
    }

    /**
     * The shell casing: the tier-2 proof (effects plan §7.2). One CPU particle per event, ejected
     * to the right of the barrel with an upward bias, bouncing against the SDF and settling. CPU
     * emitters eject perpendicular to the event angle; the SDF collision is the recipe's job, not
     * the spawner's.
     */
    private static EffectRecipe shellEject() {
        return new EffectRecipe(List.of(
            phase(0.00f, EmitterConfig.builder("shell.casing")
                .count(1)
                .lifetime(0.5f, 0.9f)
                .size(4.5f, 3.5f)
                .color(BRASS, BRASS_END)
                .speed(140f, 240f)
                .spread(40f)
                .gravity(-900f)
                .tier(EmitterConfig.Tier.CPU)
                .restitution(0.45f)
                .build())),
            null);
    }

    private static TimedEmitter phase(float delaySeconds, EmitterConfig emitter) {
        return new TimedEmitter(delaySeconds, emitter);
    }
}
