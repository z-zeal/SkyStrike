package io.github.skystrike.ui.hud;

import io.github.skystrike.shared.hud.MinimapModel;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.model.PlayerLoadout;
import java.util.List;

/**
 * Everything the HUD is allowed to know about one frame (playable build plan M4 §5).
 *
 * <p>The HUD reads the <b>predicted</b> local player, never the last snapshot: the gate for this
 * milestone is that "ammo, reload, quick-swap and utility counts never disagree with the
 * predicted {@link PlayerLoadout}", and the cheapest way to guarantee that is to give the
 * widgets no other loadout to read. {@code GameScreen} builds one of these per frame from
 * {@code LocalPrediction.predicted()} and hands it down; no widget reaches back into the
 * session, the snapshot buffer or the registries for state.
 *
 * @param player            the predicted local player, or {@code null} before the first spawn
 * @param adsAlpha          0 hip-fire, 1 fully aimed — the same value the renderer eases
 * @param hitMarkerActive   whether a hit was confirmed recently
 * @param hitMarkerAlpha    1 at the moment of the hit, decaying to 0
 * @param hitMarkerHeadshot whether that hit was a headshot
 * @param hitMarkerLethal   whether that hit killed
 * @param nowMillis         wall clock, for ageing the kill feed
 * @param deltaSeconds      frame time, for the vignette's decay
 * @param debugPanelVisible {@code cl_debug_overlay} (F1)
 * @param debugLines        what the debug panel prints when it is visible
 * @param minimap           this frame's map — markers, terrain and the camera's view rectangle,
 *                          already decided by {@link MinimapModel} — or {@code null} when
 *                          {@code cl_minimap} is off, which draws nothing and hands the corner
 *                          back to the debug panel
 */
public record HudFrame(
    Player player,
    float adsAlpha,
    boolean hitMarkerActive,
    float hitMarkerAlpha,
    boolean hitMarkerHeadshot,
    boolean hitMarkerLethal,
    long nowMillis,
    float deltaSeconds,
    boolean debugPanelVisible,
    List<String> debugLines,
    MinimapModel.View minimap
) {

    public HudFrame {
        debugLines = debugLines == null ? List.of() : List.copyOf(debugLines);
    }

    /** The predicted loadout, or {@code null} when there is no player yet. */
    public PlayerLoadout loadout() {
        return player == null ? null : player.loadout;
    }

    /** True while the local player is dead and waiting on a respawn. */
    public boolean dead() {
        return player != null && !player.alive;
    }
}
