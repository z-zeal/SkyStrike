package io.github.skystrike.shared.map;

import io.github.skystrike.shared.math.Geometry;
import java.util.List;

/**
 * Stateless questions about an {@link ArenaMap}.
 *
 * <p>Pure functions only: the server asks them to resolve authority, the client asks them to
 * predict and to draw. Nothing here may hold state or depend on a frame.
 */
public final class MapQueries {

    private MapQueries() {
    }

    /** True when a point is inside any solid. */
    public static boolean solidAt(ArenaMap map, float x, float y) {
        for (Rect rect : map.solids()) {
            if (rect.contains(x, y)) {
                return true;
            }
        }
        return false;
    }

    /** True when a box overlaps any solid. */
    public static boolean overlapsSolid(ArenaMap map, Rect box) {
        for (Rect rect : map.solids()) {
            if (Geometry.aabbOverlaps(rect, box)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when terrain blocks the straight line between two points. This is the one query used
     * for sight, explosion occlusion and bullet sweeps.
     */
    public static boolean lineBlocked(ArenaMap map, float x0, float y0, float x1, float y1) {
        for (Rect rect : map.solids()) {
            if (Geometry.segmentIntersectsAabb(x0, y0, x1, y1, rect)) {
                return true;
            }
        }
        return false;
    }

    /** The first solid the segment hits, or {@code null} when the line is clear. */
    public static Rect firstBlocker(ArenaMap map, float x0, float y0, float x1, float y1) {
        Rect nearest = null;
        float nearestEntry = Float.MAX_VALUE;
        for (Rect rect : map.solids()) {
            float entry = Geometry.segmentAabbEntry(x0, y0, x1, y1, rect);
            if (entry >= 0f && entry < nearestEntry) {
                nearestEntry = entry;
                nearest = rect;
            }
        }
        return nearest;
    }

    /**
     * Height of the highest surface at or below {@code y} directly under {@code x}, or the arena
     * floor when nothing is beneath. Used for spawn placement and ground snapping.
     */
    public static float surfaceBelow(ArenaMap map, float x, float y) {
        float best = 0f;
        for (Rect rect : map.solids()) {
            if (x < rect.left() || x > rect.right()) {
                continue;
            }
            float top = rect.top();
            if (top <= y && top > best) {
                best = top;
            }
        }
        return best;
    }

    /** Every solid overlapping the given box, in map order. */
    public static List<Rect> solidsOverlapping(ArenaMap map, Rect box) {
        return map.solids().stream().filter(rect -> Geometry.aabbOverlaps(rect, box)).toList();
    }
}
