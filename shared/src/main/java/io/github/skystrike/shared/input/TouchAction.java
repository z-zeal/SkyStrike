package io.github.skystrike.shared.input;

/**
 * One discrete touch target (Android plan §3.1).
 *
 * <p>Declaration order is the hit-test order when two button rects contain the same point.
 * The layout promises they do not overlap; the order is the tie-break if a later change breaks
 * that promise. Sticks are not actions — they are zones, and they lose to every button.
 */
public enum TouchAction {

    FIRE,
    JUMP,
    JETPACK,
    CROUCH,
    ADS,
    USE,
    EXIT_VIEW,
    VIEW_CYCLE,
    SLOT_1,
    SLOT_2,
    SLOT_3,
    SLOT_4,
    SLOT_5,
    GADGET_Q,
    GADGET_E,
    CHAT,
    QUICK_CHAT,
    LOADOUT,
    PAUSE;

    /** True while the finger must stay down for the action to stay asserted. */
    public boolean held() {
        return this == FIRE || this == JUMP || this == JETPACK || this == CROUCH || this == ADS;
    }

    /** HUD chrome, not a field of {@code PacketPlayerInput}. */
    public boolean ui() {
        return this == CHAT || this == QUICK_CHAT || this == LOADOUT || this == PAUSE;
    }

    /** Loadout slot 1–5, or {@code -1} when this action is not a slot. */
    public int slotNumber() {
        return switch (this) {
            case SLOT_1 -> 1;
            case SLOT_2 -> 2;
            case SLOT_3 -> 3;
            case SLOT_4 -> 4;
            case SLOT_5 -> 5;
            default -> -1;
        };
    }
}
