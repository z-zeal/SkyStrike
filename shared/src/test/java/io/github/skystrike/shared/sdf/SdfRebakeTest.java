package io.github.skystrike.shared.sdf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.map.ArenaMapFormat;
import io.github.skystrike.shared.map.BlockBreak;
import io.github.skystrike.shared.map.DamageSource;
import io.github.skystrike.shared.map.MapBlock;
import io.github.skystrike.shared.map.MapDamageState;
import io.github.skystrike.shared.map.Material;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The milestone-3 gate, shared side: the re-bake path is deterministic, and what a re-bake asks a
 * renderer to re-upload is the changed texel region rather than the whole field.
 *
 * <p>The maps here are authored so the fixture under test stands alone: the shipped arena's own
 * crate is on a sky platform with the rest of its range, and a region that leaked past a test
 * room's own walls would prove nothing about the crate inside it. The room below is built so the
 * destroyed crate's removal can only move the texels inside it: every texel outside the room is
 * already nearer one of the room's walls than it is the crate, so a changed region that leaked out
 * of the room would be a bug rather than a tolerance question.
 */
class SdfRebakeTest {

    private static final float TEXEL_SCALE = 2f;

    /** Floor, ceiling and two side walls around a wooden crate, all at the origin's mirror axis. */
    private static ArenaMap roomWithCrate() {
        return ArenaMapFormat.parse(String.join("\n",
            "skystrike-map 1",
            "mirror 1500",
            "centred wall stone 1300 100 400 40",
            "centred wall stone 1300 500 400 40",
            "centred wall stone 1300 140 40 360",
            "centred wall stone 1660 140 40 360",
            "centred breakable wood 1490 300 20 40",
            ""));
    }

    private static MapBlock crate(ArenaMap map) {
        for (MapBlock block : map.blocks()) {
            if (block.material() == Material.WOOD) {
                return block;
            }
        }
        throw new AssertionError("the room must contain the crate: " + map.blocks());
    }

    /** The map as it stands after one heavy bullet takes the crate out. */
    private static MapDamageState brokenRoom(ArenaMap authored) {
        MapDamageState state = MapDamageState.intact(authored);
        BlockBreak broke = state.damage(crate(authored), DamageSource.BULLET, 1_000f, 1500f, 320f);
        assertNotNull(broke, "a wooden crate must break under one heavy hit");
        assertEquals(1, state.revision());
        return state;
    }

    @Test
    @DisplayName("baking the same map twice gives byte-identical fields")
    void bakingIsDeterministic() {
        ArenaMap map = roomWithCrate();

        SdfField first = SdfBaker.bake(map, TEXEL_SCALE);
        SdfField second = SdfBaker.bake(map, TEXEL_SCALE);

        assertArrayEquals(
            first.rawData(),
            second.rawData(),
            "a deterministic bake is the whole premise of a hash-named asset");
        assertArrayEquals(first.mapHash(), second.mapHash());
        assertTrue(first.diff(second).isEmpty(), "identical fields have nothing to patch");
    }

    @Test
    @DisplayName("a re-bake after a break names the live map and re-bakes twice the same")
    void rebakeNamesTheLiveMap() {
        ArenaMap authored = roomWithCrate();
        SdfField intact = SdfBaker.bake(authored, TEXEL_SCALE);
        MapDamageState state = brokenRoom(authored);

        SdfField secondBake = SdfBaker.bake(state.liveMap(), TEXEL_SCALE);
        SdfField repeat = SdfBaker.bake(state.liveMap(), TEXEL_SCALE);

        assertTrue(secondBake.matchesMap(state.liveMap()), "the field names the map as it stands");
        assertFalse(secondBake.matchesMap(authored), "and not the authored identity");
        assertArrayEquals(
            secondBake.rawData(),
            repeat.rawData(),
            "re-baking the same live map twice must give the same field, or the SDF asset and a"
                + " runtime bake would disagree");
        assertFalse(
            Arrays.equals(intact.rawData(), secondBake.rawData()),
            "removing a block must move texels");
    }

    @Test
    @DisplayName("the changed region is the destroyed block's own room, not the whole field")
    void rebakeRegionIsALocalPatch() {
        ArenaMap authored = roomWithCrate();
        SdfField intact = SdfBaker.bake(authored, TEXEL_SCALE);
        SdfField rebaked = SdfBaker.bake(brokenRoom(authored).liveMap(), TEXEL_SCALE);

        SdfDiff region = intact.diff(rebaked);

        assertFalse(region.isEmpty(), "the crate was solid, so something must have changed");
        assertTrue(
            region.texels() < intact.rawData().length / 4,
            "the patch must be a fraction of the field, was " + region.texels() + " texels of "
                + intact.rawData().length);

        // The crate's own texels are inside the patch...
        MapBlock crate = crate(authored);
        for (int tx = (int) Math.floor(crate.rect().left() / TEXEL_SCALE);
                tx <= (int) Math.floor(crate.rect().right() / TEXEL_SCALE); tx++) {
            for (int ty = (int) Math.floor(crate.rect().bottom() / TEXEL_SCALE);
                    ty <= (int) Math.floor(crate.rect().top() / TEXEL_SCALE); ty++) {
                assertTrue(
                    tx >= region.x() && tx < region.x() + region.width()
                        && ty >= region.y() && ty < region.y() + region.height(),
                    "crate texel (" + tx + ", " + ty + ") must be inside " + region);
            }
        }

        // ...and nothing outside the room is, because the walls are nearer to every texel beyond
        // them than a crate in the middle of the room can be.
        assertTrue(region.x() * TEXEL_SCALE >= 1300f, "patch leaks past the west wall: " + region);
        assertTrue(
            (region.x() + region.width()) * TEXEL_SCALE <= 1700f,
            "patch leaks past the east wall: " + region);
        assertTrue(region.y() * TEXEL_SCALE >= 100f, "patch leaks past the floor: " + region);
        assertTrue(
            (region.y() + region.height()) * TEXEL_SCALE <= 540f,
            "patch leaks past the ceiling: " + region);
    }

    @Test
    @DisplayName("copying the changed region over the old field reproduces the re-bake exactly")
    void patchingTheChangedRegionReproducesTheField() {
        ArenaMap authored = roomWithCrate();
        SdfField intact = SdfBaker.bake(authored, TEXEL_SCALE);
        SdfField rebaked = SdfBaker.bake(brokenRoom(authored).liveMap(), TEXEL_SCALE);

        SdfDiff region = intact.diff(rebaked);
        byte[] patched = intact.rawData().clone();
        for (int row = 0; row < region.height(); row++) {
            int offset = (region.y() + row) * intact.width() + region.x();
            System.arraycopy(rebaked.rawData(), offset, patched, offset, region.width());
        }

        assertArrayEquals(
            rebaked.rawData(),
            patched,
            "the region is only a patch if applying it is the same as the re-bake");
    }

    @Test
    @DisplayName("identical fields diff to nothing; a different grid diffs to everything")
    void diffsDescribeWhatCanBePatched() {
        ArenaMap map = roomWithCrate();
        SdfField field = SdfBaker.bake(map, TEXEL_SCALE);

        SdfDiff same = field.diff(SdfBaker.bake(map, TEXEL_SCALE));
        assertTrue(same.isEmpty());
        assertEquals(0, same.texels());
        assertEquals(SdfDiff.NONE, same);

        SdfField coarser = SdfBaker.bake(map, TEXEL_SCALE * 2f);
        SdfDiff rescaled = field.diff(coarser);
        assertEquals(0, rescaled.x());
        assertEquals(0, rescaled.y());
        assertEquals(coarser.width(), rescaled.width());
        assertEquals(coarser.height(), rescaled.height());
        assertEquals(
            coarser.rawData().length,
            rescaled.texels(),
            "a field on another grid cannot be patched texel for texel, so all of it is different");
    }
}
