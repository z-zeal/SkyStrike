package io.github.skystrike.shared.weapons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.math.Angles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The drawn-weapon geometry: one muzzle point per weapon, derived from the sprite the client
 * actually draws.
 *
 * <p>What matters here is that the value is <b>per weapon</b> and monotone with the art: the
 * single constant this replaced gave every gun a pistol-sized 22 units, which is the reported
 * bug (a sniper's flash sitting at the body's centre). These tests pin the derivation — the
 * frame size, the grip anchor and the muzzle fraction — rather than a hand-copied table, so a
 * scale-catalog change moves both sides at once.
 */
class HeldWeaponGeometryTest {

    private static final float EPSILON = 1e-3f;

    /** The old one-size-fits-all offset, kept as the thing these tests must be different from. */
    private static final float OLD_MUZZLE_OFFSET = 22f;

    @Test
    @DisplayName("the muzzle is the drawn barrel tip: hand offset + art ahead of the grip")
    void muzzleIsTheDrawnBarrelTip() {
        for (WeaponId id : WeaponId.values()) {
            float expected = HeldWeaponGeometry.HAND_FORWARD_OFFSET
                + HeldWeaponGeometry.frameSize(id.ordinal())
                    * (HeldWeaponGeometry.SPRITE_MUZZLE_FRACTION - HeldWeaponGeometry.GUN_GRIP_ANCHOR_X);
            assertEquals(expected, HeldWeaponGeometry.muzzleDistance(id.ordinal()), EPSILON, id.name());
        }
        for (MeleeId id : MeleeId.values()) {
            float expected = HeldWeaponGeometry.HAND_FORWARD_OFFSET
                + HeldWeaponGeometry.frameSize(id.wireId())
                    * (HeldWeaponGeometry.SPRITE_MUZZLE_FRACTION - HeldWeaponGeometry.MELEE_GRIP_ANCHOR_X);
            assertEquals(expected, HeldWeaponGeometry.muzzleDistance(id.wireId()), EPSILON, id.name());
        }
    }

    @Test
    @DisplayName("the reference carbine reaches the barrel drawn just under 46 units out")
    void referenceCarbineMatchesItsArt() {
        // 46-unit frame × the catalog's 1.0 scale × the 1.5 readability multiplier is a 69-unit
        // frame; the art starts 5.5% in and the grip sits at 34%, so the tip is 41.7 units ahead
        // of the hand, plus the 4-unit hand offset.
        assertEquals(45.63f, HeldWeaponGeometry.muzzleDistance(WeaponId.IRON_CARBINE.ordinal()), 0.01f);
    }

    @Test
    @DisplayName("a compact pistol lands near the old constant; long guns sit well past it")
    void longGunsReachPastTheOldConstant() {
        float compactPistol = HeldWeaponGeometry.muzzleDistance(WeaponId.COBALT_COMPACT.ordinal());
        float carbine = HeldWeaponGeometry.muzzleDistance(WeaponId.IRON_CARBINE.ordinal());
        float sniper = HeldWeaponGeometry.muzzleDistance(WeaponId.CATHEDRAL.ordinal());

        assertTrue(Math.abs(compactPistol - OLD_MUZZLE_OFFSET) < 1.5f,
            "a pocket pistol is what the old constant described: " + compactPistol);
        assertTrue(carbine > 2f * OLD_MUZZLE_OFFSET,
            "a carbine's barrel is more than twice the old offset: " + carbine);
        assertTrue(sniper > carbine, "the sniper draws the longest barrel: " + sniper);
    }

    @Test
    @DisplayName("distances never shrink with a larger catalog scale")
    void muzzleIsMonotoneInScale() {
        // Compare every weapon with a scale-1.0 frame: the drawn art is the same shape, so a
        // larger scale can only push the tip further out.
        for (WeaponId id : WeaponId.values()) {
            float scale = SpriteScales.of(id);
            float oneFrame = HeldWeaponGeometry.HAND_FORWARD_OFFSET
                + HeldWeaponGeometry.GUN_FRAME_UNITS * HeldWeaponGeometry.WEAPON_SIZE_MULTIPLIER
                    * (HeldWeaponGeometry.SPRITE_MUZZLE_FRACTION - HeldWeaponGeometry.GUN_GRIP_ANCHOR_X);
            assertEquals(scale * oneFrame + (1f - scale) * HeldWeaponGeometry.HAND_FORWARD_OFFSET,
                HeldWeaponGeometry.muzzleDistance(id.ordinal()), 0.05f, id.name());
        }
    }

    @Test
    @DisplayName("every weapon has a scale, and the fallback is the catalog-neutral 1.0")
    void everyWeaponHasAScale() {
        for (WeaponId id : WeaponId.values()) {
            assertTrue(SpriteScales.of(id) > 0f, id + " has no draw scale");
        }
        for (MeleeId id : MeleeId.values()) {
            assertTrue(SpriteScales.of(id) > 0f, id + " has no draw scale");
        }
        assertEquals(SpriteScales.FALLBACK, SpriteScales.ofWireId(-1), EPSILON);
        assertEquals(SpriteScales.FALLBACK, SpriteScales.of((WeaponId) null), EPSILON);
        assertEquals(SpriteScales.of(WeaponId.IRON_SIDEARM),
            SpriteScales.ofWireId(WeaponId.IRON_SIDEARM.ordinal()), EPSILON);
        assertEquals(SpriteScales.of(MeleeId.TRENCH_KNUCKLE),
            SpriteScales.ofWireId(MeleeId.TRENCH_KNUCKLE.wireId()), EPSILON);
    }

    @Test
    @DisplayName("the muzzle point is where the aim says it is")
    void muzzlePointFollowsTheAim() {
        int wireId = WeaponId.IRON_CARBINE.ordinal();
        float distance = HeldWeaponGeometry.muzzleDistance(wireId);

        assertEquals(100f + distance, HeldWeaponGeometry.muzzleX(wireId, 100f, 0f), EPSILON);
        assertEquals(200f, HeldWeaponGeometry.muzzleY(wireId, 200f, 0f), EPSILON);

        assertEquals(100f, HeldWeaponGeometry.muzzleX(wireId, 100f, 90f), EPSILON);
        assertEquals(200f + distance, HeldWeaponGeometry.muzzleY(wireId, 200f, 90f), EPSILON);

        // 180° is the wrap case: the muzzle goes backwards along the aim, not forwards.
        assertEquals(100f - distance, HeldWeaponGeometry.muzzleX(wireId, 100f, 180f), EPSILON);

        // The engine's angle convention is shared with the ballistics: same angle, same ray.
        float expectedX = 100f + distance * (float) Math.cos(Angles.toRadians(-45f));
        assertEquals(expectedX, HeldWeaponGeometry.muzzleX(wireId, 100f, -45f), EPSILON);
    }

    @Test
    @DisplayName("melee and out-of-range wire ids resolve without throwing")
    void wireIdsResolve() {
        assertTrue(HeldWeaponGeometry.muzzleDistance(MeleeId.DEFAULT.wireId()) > 0f);
        assertTrue(HeldWeaponGeometry.frameSize(MeleeId.DEFAULT.wireId()) > 0f);
        assertTrue(HeldWeaponGeometry.muzzleDistance(9999) > 0f, "unknown ids fall back");
        assertTrue(HeldWeaponGeometry.muzzleDistance(-3) > 0f, "negative ids fall back");
        assertEquals(HeldWeaponGeometry.MELEE_GRIP_ANCHOR_X,
            HeldWeaponGeometry.gripAnchorX(MeleeId.DEFAULT.wireId()), EPSILON);
        assertEquals(HeldWeaponGeometry.GUN_GRIP_ANCHOR_X,
            HeldWeaponGeometry.gripAnchorX(WeaponId.DEFAULT.ordinal()), EPSILON);
    }
}
