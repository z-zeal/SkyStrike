package io.github.skystrike.shared.model;

import io.github.skystrike.shared.gadget.GadgetId;
import io.github.skystrike.shared.gadget.GadgetRegistry;

/**
 * One of the two gadget slots (Q or E) as carried: which gadget it holds and the live state the
 * later authoritative systems and the Phase 7 HUD need — remaining durability, the active flag,
 * the permanent broken flag and a use cooldown (mechanics §7, structure plan
 * {@code shared/model/GadgetSlot}).
 *
 * <p>Plain mutable data with public fields, like {@link WeaponItem}: it rides inside
 * {@link PlayerLoadout} on the wire, so it keeps the serialiser-friendly shape and a no-arg
 * constructor. The rules of what the state <i>means</i> per gadget live in shared maths
 * ({@code ShieldArcMath}, {@code HitZoneMath}) and, from the next increment on, in the server's
 * gadget systems — never in client-only code.
 *
 * <p>The {@code active} flag is deliberately generic: for the shield it means <i>equipped</i>
 * (front protection, handgun only), for the drone and camera it will mean <i>deployed in the
 * world</i>. A passive fuel tank never sets it; worn-and-intact is simply "not broken".
 *
 * <p><b>Respawn semantics</b> ({@link #resetForRespawn()}): mechanics §10 says respawning
 * clears every gadget state, so a new life restores full durability, un-breaks a broken shield,
 * clears the active flag and zeroes the cooldown, while the chosen gadget itself survives —
 * exactly as a death does not steal a chosen weapon.
 */
public final class GadgetSlot {

    /** Which gadget this slot holds, as a {@link GadgetId} ordinal. NONE when empty. */
    public int gadget = GadgetId.NONE.ordinal();

    /** Remaining durability of the pool, 0 for gadgets without one (fuel tank, empty slot). */
    public float durability;

    /** True while manually engaged: shield equipped; drone or camera deployed in the world. */
    public boolean active;

    /** True once destroyed for this life: a broken shield, a downed drone. Never un-breaks mid-life. */
    public boolean broken;

    /** Seconds until the gadget key works again. 0 when ready. */
    public float cooldownRemaining;

    public GadgetSlot() {
    }

    /** A slot holding {@code id}, fresh: full durability, inactive, intact. */
    public GadgetSlot(GadgetId id) {
        equip(id);
    }

    public GadgetSlot(GadgetSlot other) {
        set(other);
    }

    public void set(GadgetSlot other) {
        this.gadget = other.gadget;
        this.durability = other.durability;
        this.active = other.active;
        this.broken = other.broken;
        this.cooldownRemaining = other.cooldownRemaining;
    }

    public GadgetSlot copy() {
        return new GadgetSlot(this);
    }

    /**
     * The gadget in this slot. Defensive for wire values: an out-of-range ordinal reads as
     * {@link GadgetId#NONE} rather than throwing.
     */
    public GadgetId gadgetId() {
        return GadgetId.fromOrdinalOrNone(gadget);
    }

    public boolean isEmpty() {
        return !gadgetId().isReal();
    }

    /** True when this slot holds exactly {@code id} (and {@code id} is a real gadget). */
    public boolean holds(GadgetId id) {
        return id != null && id.isReal() && gadgetId() == id;
    }

    /** True while the gadget exists and has not been destroyed this life. */
    public boolean isUsable() {
        return !isEmpty() && !broken;
    }

    /**
     * Puts {@code id} into the slot in its fresh state: full durability from the registry,
     * inactive, intact, no cooldown. {@code null} and NONE both empty the slot.
     */
    public void equip(GadgetId id) {
        GadgetId target = id == null ? GadgetId.NONE : id;
        this.gadget = target.ordinal();
        this.durability = GadgetRegistry.maxDurability(target);
        this.active = false;
        this.broken = false;
        this.cooldownRemaining = 0f;
    }

    /**
     * Removes {@code amount} from the durability pool. At zero the gadget breaks permanently
     * for this life and deactivates.
     *
     * @return true on the call that broke it
     */
    public boolean applyDurabilityDamage(float amount) {
        if (!isUsable() || amount <= 0f) {
            return false;
        }
        durability = Math.max(0f, durability - amount);
        if (durability <= 0f) {
            broken = true;
            active = false;
            return true;
        }
        return false;
    }

    /**
     * The shield vocabulary view of this slot's flags. Only meaningful while the slot holds
     * the shield; for anything else it still answers consistently (broken → BROKEN, active →
     * EQUIPPED, otherwise STOWED) but no caller should ask.
     */
    public ShieldState shieldState() {
        if (broken) {
            return ShieldState.BROKEN;
        }
        return active ? ShieldState.EQUIPPED : ShieldState.STOWED;
    }

    /** Counts the use cooldown down. Safe to call every tick. */
    public void tickCooldown(float dt) {
        if (dt > 0f && cooldownRemaining > 0f) {
            cooldownRemaining = Math.max(0f, cooldownRemaining - dt);
        }
    }

    /**
     * What a respawn does to a gadget slot (mechanics §10): the chosen gadget is kept, its
     * state is cleared — full durability again, not broken, not active, no cooldown.
     */
    public void resetForRespawn() {
        equip(gadgetId());
    }

    @Override
    public String toString() {
        GadgetId id = gadgetId();
        if (!id.isReal()) {
            return "GadgetSlot[empty]";
        }
        return "GadgetSlot[" + id.displayName()
            + ", durability=" + String.format("%.1f", durability)
            + (active ? ", active" : "")
            + (broken ? ", broken" : "")
            + (cooldownRemaining > 0f ? String.format(", cd=%.2fs", cooldownRemaining) : "")
            + "]";
    }
}
