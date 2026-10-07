package io.github.skystrike.ui.hud;

import com.badlogic.gdx.graphics.Color;
import io.github.skystrike.ui.console.ConsoleTheme;

/**
 * Colours and metrics for the in-match HUD (playable build plan M4 §5).
 *
 * <p>Two rules this class exists to keep.
 *
 * <p><b>One contrast authority.</b> The legibility gate — "text is legible on all four
 * {@code ui_contrast_test} backgrounds" — is already solved by the console's
 * {@link ConsoleTheme#clampedText(Color)} and the four-background card the M3
 * {@code ContrastTestOverlay} draws with those exact values. The HUD does not re-derive any of
 * it: every HUD string is drawn on {@link #panelFill()} (the console's own panel colour) in a
 * colour that has been through {@link #text(Color)}, which is the console clamp. What the test
 * card proves about the console therefore holds for the HUD, because the colours are literally
 * the same objects.
 *
 * <p><b>One scale.</b> Metrics are quoted at {@link ConsoleTheme#DESIGN_HEIGHT} and multiplied
 * by {@link ConsoleTheme#scale(int)}, the same 0.75–1.75 clamp the dialog uses, so the HUD and
 * the console grow and shrink together instead of drifting apart on a 4K display.
 */
public final class HudTheme {

    private final ConsoleTheme console = new ConsoleTheme();

    // --- Vitals ---------------------------------------------------------------------------------

    /** Health bar: green while healthy, red below {@code HudVitals.LOW_HEALTH_FRACTION}. */
    public final Color healthFill = new Color(0.36f, 0.82f, 0.42f, 0.95f);
    public final Color healthLow = new Color(0.92f, 0.29f, 0.27f, 0.95f);

    /** Fuel bar: blue while usable, amber while it is grounded-recharging, grey while stalled. */
    public final Color fuelFill = new Color(0.38f, 0.70f, 0.98f, 0.95f);
    public final Color fuelRecharging = new Color(0.98f, 0.76f, 0.33f, 0.95f);
    public final Color fuelStalled = new Color(0.55f, 0.58f, 0.64f, 0.95f);

    /** The empty part of any bar. */
    public final Color barTrack = new Color(0.08f, 0.09f, 0.12f, 0.80f);
    public final Color barEdge = new Color(0.02f, 0.03f, 0.04f, 0.90f);

    // --- Loadout bar ----------------------------------------------------------------------------

    /** Alpha matches the console panel's: the contrast clamp is only valid on that backing. */
    public final Color slotFill = new Color(0.07f, 0.08f, 0.11f, 0.82f);
    public final Color slotFillActive = new Color(0.14f, 0.18f, 0.26f, 0.90f);
    public final Color slotEdgeActive = new Color(0.45f, 0.78f, 1f, 1f);
    public final Color slotEdgeEmpty = new Color(0.26f, 0.28f, 0.33f, 0.70f);
    /** The slot a tap-swap will bounce back to (mechanics §8 quick-swap origin). */
    public final Color quickSwapMarker = new Color(0.98f, 0.82f, 0.36f, 1f);
    /** The reload sweep that fills left-to-right across the active slot. */
    public final Color reloadSweep = new Color(0.98f, 0.76f, 0.33f, 0.55f);
    public final Color magazineBar = new Color(0.82f, 0.86f, 0.94f, 0.85f);
    public final Color dryWeapon = new Color(0.92f, 0.33f, 0.31f, 1f);
    public final Color gadgetOn = new Color(0.45f, 0.92f, 0.78f, 1f);
    public final Color gadgetBroken = new Color(0.86f, 0.36f, 0.36f, 1f);

    // --- Text -----------------------------------------------------------------------------------

    public final Color textPrimary = new Color(0.96f, 0.97f, 1f, 1f);
    public final Color textDim = new Color(0.72f, 0.76f, 0.83f, 1f);
    public final Color textAccent = new Color(0.45f, 0.92f, 0.78f, 1f);
    public final Color textWarning = new Color(0.98f, 0.76f, 0.33f, 1f);

    // --- Crosshair and feedback ------------------------------------------------------------------

    public final Color crosshair = new Color(0.94f, 0.96f, 1f, 0.92f);
    public final Color crosshairAds = new Color(0.45f, 0.92f, 0.78f, 0.95f);
    public final Color hitMarker = new Color(1f, 1f, 1f, 1f);
    public final Color hitMarkerHeadshot = new Color(1f, 0.84f, 0.35f, 1f);
    public final Color hitMarkerLethal = new Color(1f, 0.38f, 0.33f, 1f);

    /** The directional damage tint. Alpha is supplied per frame by {@code DamageVignetteMath}. */
    public final Color damageTint = new Color(0.78f, 0.08f, 0.08f, 1f);

    /** Kill feed: your own kills and deaths stand out from everyone else's. */
    public final Color killFeedText = new Color(0.88f, 0.90f, 0.95f, 1f);
    public final Color killFeedLocal = new Color(0.45f, 0.92f, 0.78f, 1f);
    public final Color killFeedDeath = new Color(0.95f, 0.52f, 0.48f, 1f);

    // --- Metrics, in design pixels at ConsoleTheme.DESIGN_HEIGHT ----------------------------------

    public final float margin = 14f;
    public final float innerPadding = 8f;
    public final float lineGap = 3f;

    /** Health/fuel bars, bottom left. */
    public final float barWidth = 190f;
    public final float barHeight = 13f;
    public final float barGap = 5f;

    /** The five weapon slots plus the two gadget slots, bottom right. */
    public final float slotWidth = 66f;
    public final float slotHeight = 42f;
    public final float slotGap = 5f;
    public final float gadgetSlotWidth = 46f;

    /** Kill feed, top right. */
    public final float killFeedWidth = 330f;

    /** The loadout picker panel. */
    public final float pickerWidthFraction = 0.78f;
    public final float pickerHeightFraction = 0.72f;
    public final float pickerRowHeight = 30f;
    public final float pickerColumnWidth = 118f;

    /** The crosshair's stroke. */
    public final float crosshairThickness = 2f;

    public HudTheme() {
    }

    /** The scale every metric above is multiplied by — the dialog's, not a second one. */
    public float scale(int screenHeight) {
        return ConsoleTheme.scale(screenHeight);
    }

    /**
     * The console's luminance clamp: {@code source} guaranteed at
     * {@link ConsoleTheme#MIN_TEXT_CONTRAST} against the panel it is drawn on. Every HUD string
     * goes through this, which is why the HUD inherits the console's legibility gate rather
     * than needing its own.
     */
    public Color text(Color source) {
        return console.clampedText(source);
    }

    /** The panel colour HUD text sits on — the dialog's own, so the clamp above is valid. */
    public Color panelFill() {
        return console.panelFill();
    }

    public Color panelEdge() {
        return new Color(console.panelEdge);
    }

    /** The panel colour ignoring alpha, as the contrast reference. */
    public Color opaquePanel() {
        return console.opaquePanel();
    }

    /** The underlying console theme, for widgets that need a metric by name. */
    public ConsoleTheme console() {
        return console;
    }
}
