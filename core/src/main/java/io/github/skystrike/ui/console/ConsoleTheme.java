package io.github.skystrike.ui.console;

import com.badlogic.gdx.graphics.Color;
import io.github.skystrike.ui.text.MessageFormatter;

/**
 * Colours and metrics for the chat/console dialog (console plan §5).
 *
 * <p>One place, no per-edge inline literals: every colour the dialog draws, the panel metrics it
 * lays out to, and the contrast clamp that keeps text readable over the semi-transparent panel
 * all live here, so the debug preview with its four mandatory backgrounds exercises exactly the
 * same values the game shows.
 */
public final class ConsoleTheme {

    /** Semi-transparent panel: readable yet the fight behind it stays visible. */
    public final Color panelFill = new Color(0.055f, 0.065f, 0.085f, 0.82f);
    public final Color panelEdge = new Color(0.20f, 0.24f, 0.30f, 0.95f);

    /** The input field strip and its caret. */
    public final Color inputFill = new Color(0.02f, 0.025f, 0.035f, 0.92f);
    public final Color inputEdgeActive = new Color(0.40f, 0.62f, 0.96f, 1f);
    public final Color inputEdgeIdle = new Color(0.26f, 0.30f, 0.36f, 1f);
    public final Color caret = new Color(0.93f, 0.94f, 0.97f, 1f);
    public final Color inputTextNormal = new Color(0.96f, 0.97f, 1f, 1f);
    /** Teal tint on the command restyle (console plan §4.1: typing {@code /} restyles the bar). */
    public final Color inputTextCommand = new Color(0.45f, 0.92f, 0.78f, 1f);

    /** The ALL/TEAM target button beside the input. */
    public final Color buttonFill = new Color(0.10f, 0.12f, 0.16f, 0.95f);
    public final Color buttonEdge = new Color(0.30f, 0.34f, 0.40f, 1f);

    /** The completion popup and the hint line. */
    public final Color popupFill = new Color(0.04f, 0.05f, 0.07f, 0.96f);
    public final Color popupSelected = new Color(0.22f, 0.30f, 0.42f, 1f);
    public final Color hintText = new Color(0.70f, 0.74f, 0.80f, 1f);

    /** Minimum text-vs-panel contrast; weaker text is lifted toward white to reach it. */
    public static final float MIN_TEXT_CONTRAST = 4.5f;

    // Metrics, in screen pixels at scale = screen height / DESIGN_HEIGHT.
    public static final float DESIGN_HEIGHT = 720f;

    public final float margin = 12f;
    public final float scrollbarWidth = 6f;
    public final float panelWidthFraction = 0.62f;
    public final float minPanelWidth = 420f;
    public final float maxPanelWidth = 860f;
    /** Fraction of the screen height the panel takes when open. */
    public final float openHeightFraction = 0.42f;
    public final float inputHeight = 30f;
    public final float buttonWidth = 64f;
    public final float innerPadding = 10f;
    public final float lineGap = 3f;
    /** Passive (closed) view: how many recent lines peek above the input strip. */
    public final int passiveLines = 6;
    /** Fade with age in the passive view, seconds. */
    public final float passiveFadeDelay = 6f;
    public final float passiveFadeDuration = 4f;
    /** Open/close transition, seconds. */
    public final float openTransitionSeconds = 0.12f;
    public final int completionMaxRows = 6;
    public final float scrollPixelsPerNotch = 30f;

    /**
     * Scales a design metric to the current screen height. 100% at 720 px tall, clamped so the
     * dialog stays usable on very small and very large displays.
     */
    public static float scale(int screenHeight) {
        return Math.max(0.75f, Math.min(1.75f, screenHeight / DESIGN_HEIGHT));
    }

    /**
     * The contrast clamp: {@code source} guaranteed at {@link #MIN_TEXT_CONTRAST} or better
     * against the panel, achieved by lifting luminance toward white rather than recolouring.
     */
    public Color clampedText(Color source) {
        return MessageFormatter.withMinimumLuminance(source, minimumTextLuminance());
    }

    /** The luminance text must reach to satisfy {@link #MIN_TEXT_CONTRAST} on the panel fill. */
    public float minimumTextLuminance() {
        float panelLuminance = MessageFormatter.luminance(opaquePanel());
        return Math.min(1f, MIN_TEXT_CONTRAST * (panelLuminance + 0.05f) - 0.05f);
    }

    /** The panel colour ignoring alpha, as the contrast reference. */
    public Color opaquePanel() {
        return new Color(panelFill.r, panelFill.g, panelFill.b, 1f);
    }

    public Color panelFill() {
        return new Color(panelFill);
    }

    public Color inputTextFor(boolean command) {
        return new Color(command ? inputTextCommand : inputTextNormal);
    }
}
