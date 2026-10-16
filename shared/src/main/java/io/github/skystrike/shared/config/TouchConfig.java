package io.github.skystrike.shared.config;

/**
 * Touch-control and touch-HUD constants (Android plan §3–§4, decisions AN1–AN4).
 *
 * <p>Pure numbers. The state machine lives in {@code shared/input/TouchControlModel} and the
 * geometry in {@code shared/hud/TouchHudLayout}, so a test can pin a deadzone or a 48 dp floor
 * without a screen. Stick fractions are of the stick radius, not of the screen. Layout lengths
 * are in density-independent pixels; callers multiply by {@code density} to reach physical
 * pixels, the same unit {@code SafeAreaInsets} already uses.
 *
 * <p>Scheme C (a relative touchpad) is not a constant here. Decision AN2 ships Scheme A and
 * Scheme B only. The optional Jump double-tap / slide-up jetpack gesture from §3.2 has no
 * timing in the plan, so it is not a constant either — jetpack is the dedicated button.
 */
public final class TouchConfig {

    /** LibGDX pointer ids the model tracks. Ids outside this range are ignored. */
    public static final int MIN_POINTER = 0;
    public static final int MAX_POINTER = 9;

    // --- Sticks (plan §3.2, §3.3) ---------------------------------------------------------------

    /** Radial deadzone of the movement stick. Inside it, {@code moveX} is 0. */
    public static final float MOVE_DEADZONE = 0.12f;

    /**
     * Radial deadzone of the aim stick. Inside it the last aim angle is kept; releasing the
     * stick never snaps aim to 0° or to a button.
     */
    public static final float AIM_DEADZONE = 0.10f;

    /**
     * Scheme A outer-ring auto-fire, as a fraction of the aim-stick radius. On by default for
     * guns; throwables and melee stay on the dedicated Fire button so a hard push cannot throw.
     */
    public static final float FIRE_RING_THRESHOLD = 0.85f;

    /** Scheme B: automatic guns fire while the aim stick is deflected at least this far. */
    public static final float SCHEME_B_AUTO_FIRE_THRESHOLD = 0.35f;

    /**
     * Scheme B cancel radius for single-shot guns, melee and throwables. Releasing outside it
     * fires once; releasing on or inside it does not.
     */
    public static final float SCHEME_B_CANCEL_RADIUS = 0.20f;

    /**
     * Drone flight-stick vertical gate. Above {@code +threshold} the sample jumps (climb);
     * below {@code -threshold} it crouches (descend). On foot this axis is ignored — a diagonal
     * thumb must not drop through a one-way platform (decision AN1).
     */
    public static final float DRONE_VERTICAL_THRESHOLD = 0.20f;

    // --- Touch reticle (plan §3.4) --------------------------------------------------------------

    /** World units from the aim anchor to the hip-fire touch reticle. */
    public static final float RETICLE_DISTANCE_HIP = 220f;

    /** World units from the aim anchor to the fully-aimed touch reticle. */
    public static final float RETICLE_DISTANCE_ADS = 340f;

    // --- Layout floors and classification (plan §4.2–§4.4) --------------------------------------

    /** Shortest-width dp at which a touch layout is a tablet. Matches the Android classifier. */
    public static final float TABLET_SHORTEST_WIDTH_DP = 600f;

    /** Minimum interactive hit box, both axes. */
    public static final float MIN_TOUCH_TARGET_DP = 48f;

    /** Minimum gap between interactive hit boxes. */
    public static final float MIN_TOUCH_GAP_DP = 6f;

    /** Padding inside the safe rect so a zero-inset phone does not draw controls on the bezel. */
    public static final float HUD_PAD_DP = 8f;

    /** Phone movement claim region: left edge to this fraction of screen width. */
    public static final float MOVE_ZONE_WIDTH_FRACTION = 0.30f;

    /** Phone movement claim region: bottom to this fraction of screen height. */
    public static final float MOVE_ZONE_HEIGHT_FRACTION = 0.55f;

    /** Aim claim region starts at this fraction of screen width (the right half). */
    public static final float AIM_ZONE_LEFT_FRACTION = 0.50f;

    /** Preferred stick radius on both form factors. The layout may shrink it, never below the floor. */
    public static final float STICK_RADIUS_DP = 64f;

    /** Smallest stick radius whose diameter still meets the 48 dp floor. */
    public static final float STICK_RADIUS_MIN_DP = MIN_TOUCH_TARGET_DP / 2f;

    public static final float PHONE_SLOT_WIDTH_DP = 52f;
    public static final float PHONE_SLOT_HEIGHT_DP = 48f;
    public static final float TABLET_SLOT_WIDTH_DP = 64f;
    public static final float TABLET_SLOT_HEIGHT_DP = 52f;

    public static final float FIRE_BUTTON_DP = 64f;
    public static final float JUMP_BUTTON_DP = 52f;
    public static final float JETPACK_BUTTON_DP = 52f;

    public static final float PHONE_MINIMAP_WIDTH_DP = 160f;
    public static final float PHONE_MINIMAP_HEIGHT_DP = 106f;
    public static final float TABLET_MINIMAP_WIDTH_DP = 210f;
    public static final float TABLET_MINIMAP_HEIGHT_DP = 140f;

    public static final float PHONE_VITALS_WIDTH_DP = 180f;
    public static final float PHONE_VITALS_HEIGHT_DP = 36f;
    public static final float TABLET_VITALS_WIDTH_DP = 220f;
    public static final float TABLET_VITALS_HEIGHT_DP = 44f;

    /** Tablet vitals sit this far above the bottom safe edge, clear of the left thumb. */
    public static final float TABLET_VITALS_BOTTOM_DP = 250f;

    /** Tablet stick centres, measured inward from the safe corner. */
    public static final float TABLET_STICK_CENTER_X_DP = 130f;
    public static final float TABLET_STICK_CENTER_Y_DP = 125f;

    public static final int PHONE_PASSIVE_CHAT_LINES = 3;
    public static final int TABLET_PASSIVE_CHAT_LINES = 6;

    /**
     * Provisional passive-chat line height. The console theme owns the real line height; this is
     * only the reservation {@code TouchHudLayout} keeps clear of the thumb sticks.
     */
    public static final float PASSIVE_CHAT_LINE_DP = 18f;

    /**
     * Provisional kill-feed reservation so the top utility buttons are not drawn under it.
     * AN-4 may replace the width with the widget's measured width.
     */
    public static final float PHONE_KILL_FEED_WIDTH_DP = 120f;
    public static final float TABLET_KILL_FEED_WIDTH_DP = 160f;

    // --- Persisted control preferences (provisional ranges; the plan names the fields, not the bounds)

    public static final float BUTTON_SCALE_MIN = 0.75f;
    public static final float BUTTON_SCALE_MAX = 1.50f;
    public static final float BUTTON_SCALE_DEFAULT = 1f;

    /** Provisional. High enough that a stick ring stays visible over bright terrain. */
    public static final float BUTTON_OPACITY_MIN = 0.35f;
    public static final float BUTTON_OPACITY_MAX = 1f;
    public static final float BUTTON_OPACITY_DEFAULT = 0.72f;

    private TouchConfig() {
    }

    /** Density that is safe to divide by. Non-finite or non-positive values read as 1. */
    public static float saneDensity(float density) {
        return Float.isFinite(density) && density > 0f ? density : 1f;
    }

    /** Clamps a button scale into the persisted range. Non-finite values fall to the minimum. */
    public static float clampButtonScale(float scale) {
        if (!Float.isFinite(scale)) {
            return BUTTON_SCALE_MIN;
        }
        return Math.max(BUTTON_SCALE_MIN, Math.min(BUTTON_SCALE_MAX, scale));
    }

    /** Clamps a button opacity into the persisted range. Non-finite values fall to the minimum. */
    public static float clampButtonOpacity(float opacity) {
        if (!Float.isFinite(opacity)) {
            return BUTTON_OPACITY_MIN;
        }
        return Math.max(BUTTON_OPACITY_MIN, Math.min(BUTTON_OPACITY_MAX, opacity));
    }
}
