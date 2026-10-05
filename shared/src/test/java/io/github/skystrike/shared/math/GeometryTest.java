package io.github.skystrike.shared.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.map.Rect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GeometryTest {

    private static final Rect BOX = new Rect(10f, 10f, 20f, 20f);

    @Test
    void segmentThroughTheBoxHits() {
        assertTrue(Geometry.segmentIntersectsAabb(0f, 20f, 40f, 20f, BOX));
    }

    @Test
    void segmentPassingAboveMisses() {
        assertFalse(Geometry.segmentIntersectsAabb(0f, 35f, 40f, 35f, BOX));
    }

    @Test
    @DisplayName("a segment that stops short of the box misses")
    void segmentStoppingShortMisses() {
        assertFalse(Geometry.segmentIntersectsAabb(0f, 20f, 9f, 20f, BOX));
    }

    @Test
    void segmentStartingInsideHits() {
        assertTrue(Geometry.segmentIntersectsAabb(15f, 15f, 100f, 100f, BOX));
    }

    @Test
    void degenerateSegmentBehavesLikeAPointTest() {
        assertTrue(Geometry.segmentIntersectsAabb(15f, 15f, 15f, 15f, BOX));
        assertFalse(Geometry.segmentIntersectsAabb(5f, 5f, 5f, 5f, BOX));
    }

    @Test
    void grazingAnEdgeCounts() {
        assertTrue(Geometry.segmentIntersectsAabb(0f, 10f, 40f, 10f, BOX));
    }

    @Test
    @DisplayName("entry fraction is the distance along the segment to the near face")
    void entryFractionIsCorrect() {
        float entry = Geometry.segmentAabbEntry(0f, 20f, 40f, 20f, BOX);
        assertEquals(0.25f, entry, 1e-5f);
    }

    @Test
    void entryFractionIsNegativeWhenTheLineMisses() {
        assertEquals(-1f, Geometry.segmentAabbEntry(0f, 35f, 40f, 35f, BOX), 0f);
    }

    @Test
    void entryFractionIsZeroWhenStartingInside() {
        assertEquals(0f, Geometry.segmentAabbEntry(15f, 15f, 100f, 15f, BOX), 0f);
    }

    @Test
    void overlapIsSymmetricAndEdgeInclusive() {
        Rect touching = new Rect(30f, 10f, 5f, 5f);
        assertTrue(Geometry.aabbOverlaps(BOX, touching));
        assertTrue(Geometry.aabbOverlaps(touching, BOX));

        Rect apart = new Rect(31f, 10f, 5f, 5f);
        assertFalse(Geometry.aabbOverlaps(BOX, apart));
        assertFalse(Geometry.aabbOverlaps(apart, BOX));
    }

    @Test
    @DisplayName("a fast bullet must not tunnel through a thin 14-unit roof")
    void sweptSegmentCatchesThinGeometry() {
        Rect tunnelRoof = new Rect(1120f, 190f, 180f, 14f);
        assertTrue(Geometry.segmentIntersectsAabb(1200f, 150f, 1200f, 400f, tunnelRoof));
    }
}
