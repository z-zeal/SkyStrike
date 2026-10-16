package io.github.skystrike.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import io.github.skystrike.shared.gadget.SurveillanceView;
import io.github.skystrike.shared.hud.TouchHudLayout;
import io.github.skystrike.shared.input.TouchAction;
import io.github.skystrike.shared.input.TouchControlModel;
import io.github.skystrike.shared.input.TouchPreferences;
import io.github.skystrike.shared.input.TouchSample;
import io.github.skystrike.shared.model.PlayerLoadout;
import java.util.EnumSet;
import java.util.Set;

/**
 * The LibGDX half of the touch controls (Android plan §3, AN-3). Raw touch events are converted
 * to the HUD's y-up pixels and handed to {@link TouchControlModel}, which owns pointer
 * arbitration; the sampler reads one {@link TouchSample} per frame from {@link #sample}.
 *
 * <p>It is an input processor, so it sits in the same {@link com.badlogic.gdx.InputMultiplexer}
 * as the rest of the game and only claims events while {@link #setEnabled enabled}. Desktop
 * with the touch preview off never enables it, so a mouse click reaches the game exactly as
 * before. Modal focus is applied per sample by {@link #sample}, not per event, so a finger that
 * is down when a dialog opens is dropped on the next frame.
 */
public final class TouchControls extends InputAdapter {

    private final TouchControlModel model = new TouchControlModel();
    private boolean enabled;
    private TouchHudLayout layout;

    public boolean enabled() {
        return enabled;
    }

    /** Turning touch off drops every pointer, so a finger lifted while disabled cannot stick. */
    public void setEnabled(boolean enabled) {
        if (this.enabled && !enabled) {
            model.reset();
        }
        this.enabled = enabled;
    }

    /**
     * Adopts a new touch layout and its preferences. The model keeps active pointers across this,
     * so rebuilding an equal layout every frame does not cancel a thumb that is already down.
     */
    public void applyLayout(TouchHudLayout layout, TouchPreferences preferences) {
        this.layout = layout;
        model.setPreferences(preferences);
        model.setZones(layout == null ? null : layout.zones());
    }

    /** The layout last applied, or {@code null} before the first frame. */
    public TouchHudLayout layout() {
        return layout;
    }

    /** The model, for the HUD to read stick state from. The packet never reads it directly. */
    public TouchControlModel model() {
        return model;
    }

    /**
     * Consumes this frame's touch edges and levels. Call exactly once per frame while enabled,
     * before the input sampler. {@code modal} is true while a dialog holds focus; then the sample
     * is idle apart from the retained aim, and nothing is latched across it.
     */
    public TouchSample sample(boolean modal, PlayerLoadout loadout, SurveillanceView view) {
        model.setModalFocus(modal);
        model.setCombatFrom(loadout);
        model.setSurveillanceView(view);
        return model.sample();
    }

    /** Every action the HUD should light right now: held buttons, and ADS while toggled on. */
    public Set<TouchAction> highlighted() {
        Set<TouchAction> lit = EnumSet.noneOf(TouchAction.class);
        for (TouchAction action : TouchAction.values()) {
            if (model.isPressed(action)) {
                lit.add(action);
            }
        }
        return lit;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (!enabled || button != Input.Buttons.LEFT) {
            return false;
        }
        return model.touchDown(pointer, screenX, hudY(screenY));
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        return enabled && model.touchDragged(pointer, screenX, hudY(screenY));
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        return enabled && model.touchUp(pointer, screenX, hudY(screenY));
    }

    @Override
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        return enabled && model.touchCancelled(pointer, screenX, hudY(screenY));
    }

    /** libGDX reports y-down; the HUD and the model are y-up. */
    private static float hudY(int screenY) {
        return Gdx.graphics.getHeight() - screenY;
    }
}
