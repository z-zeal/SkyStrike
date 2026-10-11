package io.github.skystrike.shared.weapons;

import io.github.skystrike.shared.math.Angles;

/**
 * The geometry of the drawn held weapon: frame size, grip anchor, hand offset and the muzzle
 * point (mechanics §5, effects §8.1).
 *
 * <p>The held weapon is a catalog sprite — one 512×512 product shot per weapon, anchored at its
 * grip and scaled by {@link SpriteScales}. That art <i>is</i> the barrel the player sees, so it
 * is the only honest source for where the muzzle flash, the shell casing, the round and the
 * fallback barrel line belong. Keeping the arithmetic here means the client draws the barrel and
 * the server spawns at its tip from one implementation, and the server never has to touch
 * LibGDX or the sprite catalog to do it.
 *
 * <p>This replaced a single {@code CombatConfig.MUZZLE_OFFSET}: one 22-unit offset, quoted as
 * "matching the drawn barrel", for every weapon in the game. 22 units is roughly a compact
 * pistol, so every longer gun spawned its flash and its rounds at the body's centre while the
 * sprite drew its barrel a full body length away.
 *
 * <p><b>No wire change.</b> Every value is derived from the generated {@link SpriteScales} table
 * and the constants below, so nothing new crosses the network and {@code PROTOCOL_VERSION} is
 * untouched.
 */
public final class HeldWeaponGeometry {

    /**
     * TUNE HERE — global readability multiplier applied to every held weapon, on top of the
     * per-weapon catalog scale. Guns and melee scale together, so relative sizes are preserved
     * (a zweihänder still dwarfs a pocket knife), and since the grip anchor is a fraction of the
     * frame the grip stays on the hand at any value. 1.0 is the catalog-neutral size; 1.5 draws
     * the reference carbine about 69 units long against the 50-unit player, a compact pistol
     * about 30, and the largest anti-materiel rifle about 104.
     */
    public static final float WEAPON_SIZE_MULTIPLIER = 1.5f;

    /**
     * World size of a scale-1.0 gun frame (the reference carbine, 840 mm), before
     * {@link #WEAPON_SIZE_MULTIPLIER}. Against the 50-unit player this draws the readable,
     * deliberately oversized silhouette the scale catalog is tuned for: a carbine roughly
     * body-height long, a compact pistol under half of that.
     */
    public static final float GUN_FRAME_UNITS = 46f;

    /**
     * World size of a scale-1.0 melee frame, before {@link #WEAPON_SIZE_MULTIPLIER}. Its
     * catalog reference is 900 mm, not 840 mm.
     */
    public static final float MELEE_FRAME_UNITS = 49f;

    /** Grip anchor as a fraction of frame width — guns pivot near the trigger, not the centre. */
    public static final float GUN_GRIP_ANCHOR_X = 0.34f;

    /** Melee handles sit at the left edge of the frame. */
    public static final float MELEE_GRIP_ANCHOR_X = 0.18f;

    /**
     * Hand offset from the eye along the aim, so the grip sits in front of the body rather than
     * inside it. The drawn sprite starts here; the muzzle is measured on from it.
     */
    public static final float HAND_FORWARD_OFFSET = 4f;

    /**
     * Right edge of the sprite's art, as a fraction of the frame width: the barrel tip of a gun
     * and the tip of a melee weapon.
     *
     * <p>Every shipped gun and melee sprite fits its art to the frame horizontally, from x = 28
     * to x = 483 of 512, so the tip lands on the same frame fraction for all of them — measured
     * across the whole catalog, gun and melee alike. What differs per weapon is only how large
     * that frame is drawn ({@link SpriteScales} × {@link #GUN_FRAME_UNITS} or
     * {@link #MELEE_FRAME_UNITS} × {@link #WEAPON_SIZE_MULTIPLIER}), which is why the old single
     * offset could never fit more than one weapon. Re-measure this if the art pipeline ever
     * changes how sprites are fitted to their frames.
     */
    public static final float SPRITE_MUZZLE_FRACTION = 483f / 512f;

    private HeldWeaponGeometry() {
    }

    /**
     * Drawn size of one weapon's square sprite frame, in world units, with the per-weapon scale
     * catalog and the global size multiplier applied.
     *
     * @param wireId a {@link WeaponId} ordinal or a {@link MeleeId} wire id
     */
    public static float frameSize(int wireId) {
        if (MeleeId.isMeleeWireId(wireId)) {
            return MELEE_FRAME_UNITS
                * SpriteScales.of(MeleeId.fromWireId(wireId))
                * WEAPON_SIZE_MULTIPLIER;
        }
        return GUN_FRAME_UNITS
            * SpriteScales.of(WeaponId.fromOrdinal(wireId))
            * WEAPON_SIZE_MULTIPLIER;
    }

    /** Grip anchor of one weapon, as a fraction of the frame width. */
    public static float gripAnchorX(int wireId) {
        return MeleeId.isMeleeWireId(wireId) ? MELEE_GRIP_ANCHOR_X : GUN_GRIP_ANCHOR_X;
    }

    /**
     * Distance from the player's eye to the drawn barrel tip along the aim, in world units: the
     * hand offset plus the length of the art ahead of the grip.
     *
     * <p>A 0.42-scale pistol lands on ~22 units — the value the single old constant gave every
     * weapon — a reference carbine on ~46 and the longest sniper on ~62.
     *
     * @param wireId a {@link WeaponId} ordinal or a {@link MeleeId} wire id
     */
    public static float muzzleDistance(int wireId) {
        return HAND_FORWARD_OFFSET
            + frameSize(wireId) * (SPRITE_MUZZLE_FRACTION - gripAnchorX(wireId));
    }

    /** World x of the drawn barrel tip, for a shot fired from {@code eyeX} at {@code aimAngleDegrees}. */
    public static float muzzleX(int wireId, float eyeX, float aimAngleDegrees) {
        return eyeX + muzzleDistance(wireId) * (float) Math.cos(Angles.toRadians(aimAngleDegrees));
    }

    /** World y of the drawn barrel tip, for a shot fired from {@code eyeY} at {@code aimAngleDegrees}. */
    public static float muzzleY(int wireId, float eyeY, float aimAngleDegrees) {
        return eyeY + muzzleDistance(wireId) * (float) Math.sin(Angles.toRadians(aimAngleDegrees));
    }
}
