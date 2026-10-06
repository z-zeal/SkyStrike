package io.github.skystrike.shared.gadget;

import io.github.skystrike.shared.config.GadgetConfig;

/**
 * The cross-gadget shape of one gadget (mechanics §7): identity, activation behaviour and the
 * durability pool. Per-gadget tuning that only one gadget has — drone flight, camera launch,
 * shield arcs, fuel-tank multipliers — lives in {@link GadgetConfig}, exactly as the structure
 * plan assigns it.
 *
 * <p>A record, so like {@code UtilityDefinition} the registry hands out the shared instance:
 * there is no mutable field for one player's gadget to leak into another's. The live, mutable
 * state (remaining durability, active flag) lives in
 * {@link io.github.skystrike.shared.model.GadgetSlot}.
 *
 * @param id            which gadget this describes; never {@link GadgetId#NONE}
 * @param maxDurability hit points of the deployed device (drone, camera) or of the worn shield.
 *                      0 means the gadget has no pool at all: the fuel tank is not whittled
 *                      down — a single hit on its rear zone detonates it
 * @param spawnsEntity  true when activating it puts a destructible entity into the world that
 *                      the server must simulate and snapshot (drone, camera); false for gear
 *                      worn on the body (shield, fuel tank)
 * @param provisional   true when the mechanics plan leaves this row's numbers blank and they
 *                      were chosen here rather than specified
 */
public record GadgetDefinition(
    GadgetId id,
    float maxDurability,
    boolean spawnsEntity,
    boolean provisional
) {

    public GadgetDefinition {
        if (id == null || !id.isReal()) {
            throw new IllegalArgumentException("a definition needs a real gadget id, not " + id);
        }
        if (maxDurability < 0f) {
            throw new IllegalArgumentException("durability must not be negative: " + id);
        }
    }

    public String displayName() {
        return id.displayName();
    }

    /** Activation behaviour, delegated to the identity so there is exactly one source of it. */
    public GadgetBehavior behavior() {
        return id.behavior();
    }

    /** This gadget's encoding in the shared weapon-id space. */
    public int wireId() {
        return id.wireId();
    }

    /** True when the gadget has a durability pool that damage whittles down. */
    public boolean hasDurabilityPool() {
        return maxDurability > 0f;
    }
}
