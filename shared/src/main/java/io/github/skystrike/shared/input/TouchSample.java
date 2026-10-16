package io.github.skystrike.shared.input;

import io.github.skystrike.shared.math.Angles;
import io.github.skystrike.shared.net.c2s.PacketPlayerInput;

/**
 * One consumed touch sample, shaped so AN-3 can copy it onto {@link PacketPlayerInput}.
 *
 * <p>Slot, gadget, view and use values use that packet's sentinels. Birth sequences are not
 * here: the gameplay controllers already own retransmission. {@code uiAction} is HUD chrome
 * (chat, quick chat, loadout, pause) and never rides the wire. A fast tap that started and
 * ended before this sample still reports {@code fire}, {@code jump}, {@code crouch} or
 * {@code jetpack} once — the server derives press edges by comparing samples, so a tap that
 * never appears is a shot, a jump or a platform drop that never happens.
 *
 * @param moveX      horizontal intent in {@code [-1, 1]}
 * @param jump       jump level, or drone climb
 * @param crouch     crouch level, or drone descend
 * @param jetpack    jetpack level; also climbs a drone, because the drone reads jump <i>or</i> jetpack
 * @param ads        aim-down-sights level after toggle or hold
 * @param fire       trigger level, including a one-sample latch for a tap between ticks
 * @param aimAngle   retained aim in degrees, {@code (-180, 180]}
 * @param slotPress  slot 1–5, or {@link PacketPlayerInput#NO_SLOT_PRESS}
 * @param gadgetPress {@link PacketPlayerInput#GADGET_Q_PRESS}, {@link PacketPlayerInput#GADGET_E_PRESS},
 *                    or {@link PacketPlayerInput#NO_GADGET_PRESS}
 * @param viewAction {@link PacketPlayerInput#VIEW_CYCLE}, {@link PacketPlayerInput#VIEW_EXIT},
 *                   or {@link PacketPlayerInput#NO_VIEW_ACTION}
 * @param usePress   {@link PacketPlayerInput#USE_PRESS} or {@link PacketPlayerInput#NO_USE_PRESS}
 * @param uiAction   one chrome edge, or {@code null}
 */
public record TouchSample(
    float moveX,
    boolean jump,
    boolean crouch,
    boolean jetpack,
    boolean ads,
    boolean fire,
    float aimAngle,
    int slotPress,
    int gadgetPress,
    int viewAction,
    int usePress,
    TouchAction uiAction
) {

    public TouchSample {
        if (!Float.isFinite(moveX)) {
            moveX = 0f;
        }
        moveX = Math.max(-1f, Math.min(1f, moveX));
        aimAngle = Float.isFinite(aimAngle) ? Angles.wrap(aimAngle) : 0f;
    }

    /** No intent, but the retained aim is kept so a modal sample cannot snap the reticle. */
    public static TouchSample idle(float aimAngle) {
        return new TouchSample(
            0f,
            false,
            false,
            false,
            false,
            false,
            aimAngle,
            PacketPlayerInput.NO_SLOT_PRESS,
            PacketPlayerInput.NO_GADGET_PRESS,
            PacketPlayerInput.NO_VIEW_ACTION,
            PacketPlayerInput.NO_USE_PRESS,
            null);
    }
}
