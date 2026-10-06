package io.github.skystrike.ui.console;

import io.github.skystrike.settings.ClientPreferences;
import io.github.skystrike.shared.text.ChatTarget;
import io.github.skystrike.ui.text.MessageFormatter;
import com.badlogic.gdx.graphics.Color;

/**
 * The ALL↔TEAM toggle at the left of the input strip (console plan §3.3).
 *
 * <p>It owns one of the system-design's two pieces of state: {@code chatTarget}. Clicking or
 * tapping its rect flips it; so does Tab while the dialog is open with the field not completing
 * (the accelerator lives here so the keystroke and the click do the same thing, not two nearby
 * things). The value persists immediately through {@link ClientPreferences} — the ALL/TEAM a
 * player leaves with is the ALL/TEAM they come back to, even across a restart.
 */
public final class ChatTargetButton {

    private final ClientPreferences preferences;
    private ChatTarget target;

    /** Hit rect in screen pixels (bottom-left origin), set by the dialog layout each frame. */
    private float x;
    private float y;
    private float width;
    private float height;

    public ChatTargetButton(ClientPreferences preferences) {
        if (preferences == null) {
            throw new IllegalArgumentException("preferences are required");
        }
        this.preferences = preferences;
        this.target = preferences.chatTarget();
    }

    /** Where the dialog draws it; also the tap hit test. */
    public void layout(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /** A click or tap; true when it landed on the button and toggled. */
    public boolean touchDown(float screenX, float screenY) {
        if (screenX < x || screenX > x + width || screenY < y || screenY > y + height) {
            return false;
        }
        toggle();
        return true;
    }

    /** The Tab accelerator: true when consumed. */
    public boolean onTabPressed() {
        toggle();
        return true;
    }

    public void toggle() {
        target = target.toggle();
        preferences.saveChatTarget(target);
    }

    public ChatTarget target() {
        return target;
    }

    public String label() {
        return target.name();
    }

    /** Team-tinted label colour, contrast-clamped for the button fill. */
    public Color labelColor() {
        return switch (target) {
            case ALL -> MessageFormatter.withMinimumLuminance(
                new Color(0.75f, 0.86f, 1f, 1f), 0.55f);
            case TEAM -> MessageFormatter.withMinimumLuminance(
                new Color(0.55f, 0.95f, 0.68f, 1f), 0.55f);
        };
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }

    /** Metrics helper for layout: the button sticks to the theme's design width. */
    public static float designWidth(ConsoleTheme theme) {
        return theme.buttonWidth;
    }
}
