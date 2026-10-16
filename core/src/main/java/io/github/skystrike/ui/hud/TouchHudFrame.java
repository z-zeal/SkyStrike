package io.github.skystrike.ui.hud;

import io.github.skystrike.shared.hud.TouchHudLayout;
import io.github.skystrike.shared.input.TouchAction;
import io.github.skystrike.shared.input.VirtualStickState;
import java.util.Set;

/**
 * What the touch overlay draws for one frame (Android plan AN-4): the layout it is placed from,
 * the two sticks' live state, and the actions to light. Built by {@code GameScreen} from the
 * touch controls, and absent on desktop unless the {@code cl_touch_hud} preview is on.
 *
 * @param layout      the touch layout every touch widget is placed from
 * @param move        the movement stick, idle at its anchor or following the thumb
 * @param aim         the aim stick, idle at its anchor or following the thumb
 * @param highlighted the actions to light: a held button, or ADS while the toggle is engaged
 */
public record TouchHudFrame(
    TouchHudLayout layout,
    VirtualStickState move,
    VirtualStickState aim,
    Set<TouchAction> highlighted
) {

    public TouchHudFrame {
        highlighted = highlighted == null ? Set.of() : Set.copyOf(highlighted);
    }
}
