package io.github.skystrike.shared.map;

import io.github.skystrike.shared.math.Geometry;
import java.util.List;

/**
 * Stateless questions about an {@link ArenaMap}.
 *
 * <p>Pure functions only: the server asks them to resolve authority, the client asks them to
 * predict and to draw. Nothing here may hold state or depend on a frame.
 *
 * <p>Line queries take a {@link Blocking} purpose and answer it from the shared
 * {@link BlockRules} table through {@link ArenaMap#blockers(Blocking)}: sight, bullet and blast
 * consumers ask the same question with different purposes instead of each deciding which kinds
 * count. The five-argument forms are the sight convenience, kept so existing callers keep their
 * meaning.
 *
 * <p>Point/box questions ({@link #solidAt}, {@link #overlapsSolid}, {@link #surfaceBelow},
 * {@link #solidsOverlapping}) still read the bullet-blocking compatibility view. Movement is not
 * a {@link Blocking} purpose — {@link MovementRule} owns that column — and the shared sweeper
 * migrates to it with the one-way platform work rather than half a column at a time.
 */
public final class MapQueries {

    private MapQueries() {
    }

    /** True when a point is inside any bullet-blocking solid. */
    public static boolean solidAt(ArenaMap map, float x, float y) {
        for (Rect rect : map.solids()) {
            if (rect.contains(x, y)) {
                return true;
            }
        }
        return false;
    }

    /** True when a box overlaps any bullet-blocking solid. */
    public static boolean overlapsSolid(ArenaMap map, Rect box) {
        for (Rect rect : map.solids()) {
            if (Geometry.aabbOverlaps(rect, box)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when terrain blocks the straight line between two points for the given purpose.
     *
     * <p>This is the one query used for sight, explosion occlusion and bullet sweeps; the purpose
     * decides which kinds answer. Sight and blast are the same column by design (glass is
     * see-through and blast-through), bullets are their own, and a kind is never named here.
     *
     * <p>A {@code null} map is open space, so callers that legitimately hold no map (a geometry-only
     * test, a utility resolved before an arena exists) get the unoccluded answer rather than an
     * exception.
     */
    public static boolean lineBlocked(
            ArenaMap map, float x0, float y0, float x1, float y1, Blocking purpose) {
        if (map == null) {
            return false;
        }
        for (Rect rect : map.blockers(purpose)) {
            if (Geometry.segmentIntersectsAabb(x0, y0, x1, y1, rect)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when terrain blocks the straight line between two points for sight.
     *
     * <p>The compatibility form of {@code lineBlocked(map, ..., Blocking.SIGHT)}: sight call sites
     * keep their meaning while they migrate to an explicit purpose.
     */
    public static boolean lineBlocked(ArenaMap map, float x0, float y0, float x1, float y1) {
        return lineBlocked(map, x0, y0, x1, y1, Blocking.SIGHT);
    }

    /**
     * The first solid the segment hits for the given purpose, or {@code null} when the line is
     * clear. A {@code null} map is open space.
     */
    public static Rect firstBlocker(
            ArenaMap map, float x0, float y0, float x1, float y1, Blocking purpose) {
        if (map == null) {
            return null;
        }
        Rect nearest = null;
        float nearestEntry = Float.MAX_VALUE;
        for (Rect rect : map.blockers(purpose)) {
            float entry = Geometry.segmentAabbEntry(x0, y0, x1, y1, rect);
            if (entry >= 0f && entry < nearestEntry) {
                nearestEntry = entry;
                nearest = rect;
            }
        }
        return nearest;
    }

    /** The first solid the segment hits for sight, or {@code null} when the line is clear. */
    public static Rect firstBlocker(ArenaMap map, float x0, float y0, float x1, float y1) {
        return firstBlocker(map, x0, y0, x1, y1, Blocking.SIGHT);
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

    /** Every bullet-blocking solid overlapping the given box, in map order. */
    public static List<Rect> solidsOverlapping(ArenaMap map, Rect box) {
        return map.solids().stream().filter(rect -> Geometry.aabbOverlaps(rect, box)).toList();
    }
}
