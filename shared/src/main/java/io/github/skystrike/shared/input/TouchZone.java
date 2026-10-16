package io.github.skystrike.shared.input;

import io.github.skystrike.shared.map.Rect;

/**
 * One claim region in HUD y-up pixels (Android plan §3.1).
 *
 * <p>A button zone is its hit rect. A stick zone is the area that may claim the pointer; the
 * anchor and radius are the deflection circle, which is not always the whole zone. The movement
 * zone is the left (or, when mirrored, right) thumb half. The aim zone is the other half. Both
 * lose to any button they contain.
 *
 * @param kind    what a claim becomes
 * @param action  the button, or {@code null} for a stick
 * @param bounds  claim rect, y-up, origin at the bottom-left of the screen
 * @param anchorX stick centre, or the button centre
 * @param anchorY stick centre, or the button centre
 * @param radius  stick radius in pixels; {@code 0} for a button
 */
public record TouchZone(
    Kind kind,
    TouchAction action,
    Rect bounds,
    float anchorX,
    float anchorY,
    float radius
) {

    public enum Kind {
        BUTTON,
        MOVE,
        AIM
    }

    public TouchZone {
        if (kind == null) {
            throw new IllegalArgumentException("zone kind is required");
        }
        if (bounds == null) {
            throw new IllegalArgumentException("zone bounds are required");
        }
        if (kind == Kind.BUTTON && action == null) {
            throw new IllegalArgumentException("a button zone needs an action");
        }
        if (kind != Kind.BUTTON && action != null) {
            throw new IllegalArgumentException("a stick zone has no action");
        }
        if (!Float.isFinite(radius) || radius < 0f) {
            throw new IllegalArgumentException("stick radius must be finite and non-negative");
        }
        if (!Float.isFinite(anchorX) || !Float.isFinite(anchorY)) {
            throw new IllegalArgumentException("anchor must be finite");
        }
    }

    public static TouchZone button(TouchAction action, Rect bounds) {
        return new TouchZone(Kind.BUTTON, action, bounds, bounds.centerX(), bounds.centerY(), 0f);
    }

    public static TouchZone stick(Kind kind, Rect bounds, float anchorX, float anchorY, float radius) {
        return new TouchZone(kind, null, bounds, anchorX, anchorY, radius);
    }

    public boolean contains(float x, float y) {
        return Float.isFinite(x) && Float.isFinite(y) && bounds.contains(x, y);
    }
}
