package io.github.skystrike.shared.input;

/**
 * What a virtual stick is doing this frame, for the HUD ring and for tests.
 *
 * <p>{@code outputX} and {@code outputY} are the post-deadzone deflection in {@code [-1, 1]},
 * already divided by the radius (plan §3.2: {@code dx / maxRadius}, not rescaled past the
 * deadzone). The knob position is the visual finger, including inside the deadzone, so the ring
 * tracks the thumb before the axis engages. An idle stick sits on its layout anchor with a null
 * pointer.
 *
 * @param active   a finger currently owns this stick
 * @param pointer  the owning pointer, or {@link #NO_POINTER}
 * @param originX  deflection origin (the touch point when floating, the anchor when fixed)
 * @param originY  deflection origin
 * @param knobX    visual knob, clamped to the radius
 * @param knobY    visual knob, clamped to the radius
 * @param outputX  horizontal axis after the deadzone gate
 * @param outputY  vertical axis after the deadzone gate
 * @param radial   clamped deflection, 0 at the origin and 1 at the rim
 * @param radius   radius in pixels
 */
public record VirtualStickState(
    boolean active,
    int pointer,
    float originX,
    float originY,
    float knobX,
    float knobY,
    float outputX,
    float outputY,
    float radial,
    float radius
) {

    public static final int NO_POINTER = -1;

    public static VirtualStickState idle(float anchorX, float anchorY, float radius) {
        float safeRadius = Float.isFinite(radius) && radius > 0f ? radius : 0f;
        float x = Float.isFinite(anchorX) ? anchorX : 0f;
        float y = Float.isFinite(anchorY) ? anchorY : 0f;
        return new VirtualStickState(false, NO_POINTER, x, y, x, y, 0f, 0f, 0f, safeRadius);
    }
}
