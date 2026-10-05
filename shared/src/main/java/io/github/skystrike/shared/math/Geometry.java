package io.github.skystrike.shared.math;

import io.github.skystrike.shared.map.Rect;

/**
 * Segment and axis-aligned-box intersection — the only copy in the project.
 *
 * <p>Collision, line of sight and explosion occlusion all resolve down to these two queries, so
 * both the client and the server must call exactly this code. A second implementation anywhere is
 * a divergence waiting to happen.
 */
public final class Geometry {

    private Geometry() {
    }

    /**
     * Slab test: does the segment {@code (x0,y0)-(x1,y1)} touch the rectangle?
     *
     * <p>Touching an edge counts as a hit. A zero-length segment degenerates to a point-in-rect
     * test.
     */
    public static boolean segmentIntersectsAabb(float x0, float y0, float x1, float y1, Rect rect) {
        float dx = x1 - x0;
        float dy = y1 - y0;

        float enter = 0f;
        float exit = 1f;

        float[] direction = {-dx, dx, -dy, dy};
        float[] distance = {
            x0 - rect.left(),
            rect.right() - x0,
            y0 - rect.bottom(),
            rect.top() - y0,
        };

        for (int axis = 0; axis < 4; axis++) {
            if (direction[axis] == 0f) {
                // Parallel to this slab: either already inside it, or never will be.
                if (distance[axis] < 0f) {
                    return false;
                }
                continue;
            }
            float t = distance[axis] / direction[axis];
            if (direction[axis] < 0f) {
                enter = Math.max(enter, t);
            } else {
                exit = Math.min(exit, t);
            }
            if (enter > exit) {
                return false;
            }
        }
        return true;
    }

    /**
     * Fraction along the segment at which it first touches the rectangle, or {@code -1} when it
     * never does. Useful for swept collision and for the nearest sight blocker.
     */
    public static float segmentAabbEntry(float x0, float y0, float x1, float y1, Rect rect) {
        float dx = x1 - x0;
        float dy = y1 - y0;

        float enter = 0f;
        float exit = 1f;

        float[] direction = {-dx, dx, -dy, dy};
        float[] distance = {
            x0 - rect.left(),
            rect.right() - x0,
            y0 - rect.bottom(),
            rect.top() - y0,
        };

        for (int axis = 0; axis < 4; axis++) {
            if (direction[axis] == 0f) {
                if (distance[axis] < 0f) {
                    return -1f;
                }
                continue;
            }
            float t = distance[axis] / direction[axis];
            if (direction[axis] < 0f) {
                enter = Math.max(enter, t);
            } else {
                exit = Math.min(exit, t);
            }
            if (enter > exit) {
                return -1f;
            }
        }
        return enter;
    }

    /** True when two axis-aligned boxes share any area or edge. */
    public static boolean aabbOverlaps(Rect a, Rect b) {
        return a.left() <= b.right()
            && b.left() <= a.right()
            && a.bottom() <= b.top()
            && b.bottom() <= a.top();
    }
}
