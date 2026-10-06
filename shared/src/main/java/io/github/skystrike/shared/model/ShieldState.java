package io.github.skystrike.shared.model;

/**
 * The three states of the shield gadget (mechanics §7.3, structure plan
 * {@code shared/model/ShieldState}).
 *
 * <p>This is a derived view, not stored state: {@link GadgetSlot#shieldState()} computes it
 * from the slot's {@code active} and {@code broken} flags, so there is no second field that
 * could disagree with them. It exists so the shield-arc maths and later the HUD can speak in
 * the plan's own vocabulary.
 */
public enum ShieldState {

    /** Worn on the back: the rear arc is protected, every weapon is usable. */
    STOWED,

    /** Held in front: the frontal arc is protected, but only the handgun may be used. */
    EQUIPPED,

    /** Durability reached zero. Permanent for the rest of the life; absorbs nothing. */
    BROKEN;

    /** True while the shield still absorbs anything at all. */
    public boolean absorbs() {
        return this != BROKEN;
    }

    /** True while the shield restricts the wearer to their handgun (mechanics §7.3). */
    public boolean handgunOnly() {
        return this == EQUIPPED;
    }
}
