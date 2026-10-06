package io.github.skystrike.shared.gadget;

/**
 * The three activation behaviours a gadget can have (mechanics §7).
 *
 * <p>The behaviour decides what the Q/E key press means for the gadget in that slot, and what
 * the server must simulate even when the key is never touched:
 *
 * <ul>
 *   <li>{@link #PASSIVE} — always on while equipped and intact; the key does nothing. The fuel
 *       tank's capacity and thrust multipliers apply from spawn to death.</li>
 *   <li>{@link #MANUAL} — does nothing until pressed. The drone deploys on the first press and
 *       hands over the point of view on the second; the throw camera is thrown, then viewed.</li>
 *   <li>{@link #HYBRID} — a passive effect plus a manual toggle. The shield always protects one
 *       side; the press chooses which (equipped front / stowed rear).</li>
 * </ul>
 */
public enum GadgetBehavior {

    /** Always on while equipped; the gadget key is inert. */
    PASSIVE,

    /** Inert until the gadget key is pressed. */
    MANUAL,

    /** A passive effect that the gadget key reconfigures. */
    HYBRID;

    /** True when a Q/E press is meaningful for this behaviour. */
    public boolean respondsToPress() {
        return this != PASSIVE;
    }

    /** True when the gadget has an effect even if its key is never pressed. */
    public boolean hasPassiveEffect() {
        return this != MANUAL;
    }
}
