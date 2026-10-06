package io.github.skystrike.shared.model;

import io.github.skystrike.shared.gadget.GadgetId;
import io.github.skystrike.shared.utility.UtilityId;
import io.github.skystrike.shared.utility.UtilityRegistry;
import io.github.skystrike.shared.weapons.MeleeId;
import io.github.skystrike.shared.weapons.WeaponDefinition;
import io.github.skystrike.shared.weapons.WeaponId;

/**
 * The five main slots of a player's loadout and the state of what is in their hands
 * (mechanics §8). This is the authoritative state the server owns; it rides inside
 * {@link Player} so every snapshot mirrors it to clients, whose predictions run the exact same
 * rules from this one class.
 *
 * <p>Slot layout: 1 primary gun, 2 handgun, 3 melee, 4 and 5 utilities. Slot 3 can never be
 * empty — a player is never defenceless. A utility slot remains part of the composition after its
 * carried count reaches zero, but counts as empty for selection and cycling until the next
 * respawn refills it.
 *
 * <p>Alongside the five main slots sit the two gadget slots, Q and E (mechanics §7, §8). They
 * are deliberately <b>outside</b> the 1–5 selection system: a gadget is never "in the hands",
 * never cycled by the wheel, and works regardless of which main slot is active. Both default to
 * empty — the mechanics plan names no default gadget, and an undocumented free fuel tank or
 * shield would be a silent balance decision.
 *
 * <p>The switching rules live here, once, so client prediction and the server cannot diverge:
 * selecting is idempotent ({@link #selectSlot}), a key <i>press</i> carries the tap-swap rule
 * ({@link #tapSlot} — pressing 1 or 2 while that slot is already active drops to melee, remembers
 * the origin, and pressing again returns), and the wheel cycles filled slots only
 * ({@link #cycle}). Changing slots always cancels an in-progress reload and — server-side, via
 * the gun state the server owns — resets the weapon's accumulated spread and recoil (§8).
 *
 * <p>Reload timing is float-dust safe: ninety subtractions of 1/60 from 1.5 do not land on
 * exactly zero, so the timer is spent once it is within {@link #RELOAD_TIMER_EPSILON} of zero,
 * the same treatment {@code RespawnService} gives the respawn timer.
 */
public final class PlayerLoadout {

    public static final int SLOT_COUNT = 5;
    public static final int SLOT_PRIMARY = 1;
    public static final int SLOT_HANDGUN = 2;
    public static final int SLOT_MELEE = 3;
    public static final int SLOT_UTILITY_A = 4;
    public static final int SLOT_UTILITY_B = 5;

    /** Value of {@link #quickSwapOrigin} when no swap is being remembered. */
    public static final int NO_QUICK_SWAP = 0;

    /**
     * Reload timer completion slack. 90 × (1/60) does not reach 1.5 exactly in float, so the
     * timer counts as spent once it is within this much of zero; stricter than
     * {@code RespawnService}'s 1e-4 would leave a reload hanging for one extra tick.
     */
    public static final float RELOAD_TIMER_EPSILON = 1e-4f;

    /** Slot 1: the primary gun. May be {@code null} — the slot then counts as empty. */
    public WeaponItem primary;

    /** Slot 2: the sidearm. May be {@code null}. */
    public WeaponItem handgun;

    /** Slot 3: the melee weapon, as a {@link MeleeId} ordinal. Never empty. */
    public int melee = MeleeId.DEFAULT.ordinal();

    /** Slot 4 utility, as a {@link UtilityId} ordinal. Its type survives depletion. */
    public int utilityA = UtilityId.DEFAULT_PRIMARY.ordinal();

    /** Number of slot 4 utilities left in this life. Zero makes the slot unselectable. */
    public int utilityACount;

    /** Slot 5 utility, as a {@link UtilityId} ordinal. Its type survives depletion. */
    public int utilityB = UtilityId.DEFAULT_SECONDARY.ordinal();

    /** Number of slot 5 utilities left in this life. Zero makes the slot unselectable. */
    public int utilityBCount;

    /** The Q gadget slot. Never {@code null}; empty by default. */
    public GadgetSlot gadgetQ = new GadgetSlot();

    /** The E gadget slot. Never {@code null}; empty by default. */
    public GadgetSlot gadgetE = new GadgetSlot();

    /** The currently active slot, 1–5. Only ever a filled slot. */
    public int activeSlot = SLOT_PRIMARY;

    /**
     * The slot a tap-swap to melee came from, or {@link #NO_QUICK_SWAP}. Informational state for
     * HUD and debugging; the rules of {@link #tapSlot} maintain it on both sides identically.
     */
    public int quickSwapOrigin = NO_QUICK_SWAP;

    /** True while the active gun is reloading. */
    public boolean reloading;

    /** Seconds left on the current reload. */
    public float reloadTimer;

    /** The starting loadout: rifle, sidearm, knife, frag and smoke. */
    public PlayerLoadout() {
        this(WeaponId.DEFAULT, WeaponId.DEFAULT_SIDEARM, MeleeId.DEFAULT,
            UtilityId.DEFAULT_PRIMARY, UtilityId.DEFAULT_SECONDARY);
    }

    /** Builds the three weapon slots and supplies the default pair of utilities. */
    public PlayerLoadout(WeaponId primaryId, WeaponId handgunId, MeleeId meleeId) {
        this(primaryId, handgunId, meleeId, UtilityId.DEFAULT_PRIMARY, UtilityId.DEFAULT_SECONDARY);
    }

    public PlayerLoadout(
        WeaponId primaryId,
        WeaponId handgunId,
        MeleeId meleeId,
        UtilityId utilityAId,
        UtilityId utilityBId
    ) {
        this.primary = primaryId == null ? null : new WeaponItem(primaryId);
        this.handgun = handgunId == null ? null : new WeaponItem(handgunId);
        this.melee = (meleeId == null ? MeleeId.DEFAULT : meleeId).ordinal();
        this.utilityA = utilityAId == null ? -1 : utilityAId.ordinal();
        this.utilityB = utilityBId == null ? -1 : utilityBId.ordinal();
        refillUtilities();
        this.activeSlot = firstFilledSlot();
    }

    public PlayerLoadout(PlayerLoadout other) {
        set(other);
    }

    public void set(PlayerLoadout other) {
        this.primary = other.primary == null ? null : other.primary.copy();
        this.handgun = other.handgun == null ? null : other.handgun.copy();
        this.melee = other.melee;
        this.utilityA = other.utilityA;
        this.utilityACount = other.utilityACount;
        this.utilityB = other.utilityB;
        this.utilityBCount = other.utilityBCount;
        this.gadgetQ = other.gadgetQ == null ? new GadgetSlot() : other.gadgetQ.copy();
        this.gadgetE = other.gadgetE == null ? new GadgetSlot() : other.gadgetE.copy();
        this.activeSlot = other.activeSlot;
        this.quickSwapOrigin = other.quickSwapOrigin;
        this.reloading = other.reloading;
        this.reloadTimer = other.reloadTimer;
    }

    public PlayerLoadout copy() {
        return new PlayerLoadout(this);
    }

    // --- Slots ---------------------------------------------------------------------------------

    public static boolean isValidSlot(int slot) {
        return slot >= 1 && slot <= SLOT_COUNT;
    }

    /** A slot is filled when it holds something usable right now. */
    public boolean isFilled(int slot) {
        return switch (slot) {
            case SLOT_PRIMARY -> primary != null && primary.hasWeapon();
            case SLOT_HANDGUN -> handgun != null && handgun.hasWeapon();
            case SLOT_MELEE -> true; // never empty, by rule
            case SLOT_UTILITY_A -> UtilityId.isValidOrdinal(utilityA) && utilityACount > 0;
            case SLOT_UTILITY_B -> UtilityId.isValidOrdinal(utilityB) && utilityBCount > 0;
            default -> false;
        };
    }

    public MeleeId meleeId() {
        return MeleeId.fromOrdinal(melee);
    }

    /** Utility assigned to slot 4 or 5, or {@code null} for another or invalid slot. */
    public UtilityId utilityIdForSlot(int slot) {
        int ordinal = switch (slot) {
            case SLOT_UTILITY_A -> utilityA;
            case SLOT_UTILITY_B -> utilityB;
            default -> -1;
        };
        return UtilityId.isValidOrdinal(ordinal) ? UtilityId.fromOrdinal(ordinal) : null;
    }

    /** Carried count for slot 4 or 5. Other slots report zero. */
    public int utilityCountForSlot(int slot) {
        return switch (slot) {
            case SLOT_UTILITY_A -> Math.max(0, utilityACount);
            case SLOT_UTILITY_B -> Math.max(0, utilityBCount);
            default -> 0;
        };
    }

    // --- Gadget slots (mechanics §7, §8) --------------------------------------------------------

    /**
     * The Q or E slot by index: 0 = Q, 1 = E. Gadget slots sit outside the 1–5 numbering on
     * purpose — they are never the active slot.
     */
    public GadgetSlot gadgetSlot(int index) {
        return switch (index) {
            case 0 -> gadgetQ;
            case 1 -> gadgetE;
            default -> throw new IllegalArgumentException("gadget slot index must be 0 or 1: " + index);
        };
    }

    /** True while either slot holds a usable (equipped, not yet destroyed) {@code id}. */
    public boolean hasUsableGadget(GadgetId id) {
        return (gadgetQ.holds(id) && gadgetQ.isUsable())
            || (gadgetE.holds(id) && gadgetE.isUsable());
    }

    /**
     * True while a fuel tank is worn and intact — the condition under which
     * {@code HitZoneMath} resolves the rear tank zone. A detonated (broken) tank is gone for
     * the rest of the life and exposes no zone.
     */
    public boolean hasFuelTank() {
        return hasUsableGadget(GadgetId.FUEL_TANK);
    }

    /** The slot holding the shield, or {@code null} when no shield is carried. */
    public GadgetSlot shieldSlot() {
        if (gadgetQ.holds(GadgetId.SHIELD)) {
            return gadgetQ;
        }
        return gadgetE.holds(GadgetId.SHIELD) ? gadgetE : null;
    }

    /** The carried shield's state, or {@code null} when no shield is carried. */
    public ShieldState shieldState() {
        GadgetSlot slot = shieldSlot();
        return slot == null ? null : slot.shieldState();
    }

    /**
     * Applies a gadget composition change. {@code null} keeps a slot's current choice;
     * {@link GadgetId#NONE} explicitly empties it. A slot re-assigned the gadget it already
     * holds keeps its live state (as an unchanged gun keeps its ammo); an actual change equips
     * the new gadget fresh.
     *
     * <p><b>Duplicates are rejected per slot</b>: a change that would leave both slots holding
     * the same real gadget is dropped, Q applying before E. Provisional rule — the plan never
     * says whether two shields may be carried, and letting passive multipliers stack silently
     * is the worse default.
     */
    public void setGadgets(GadgetId gadgetQId, GadgetId gadgetEId) {
        if (gadgetQId != null && gadgetQId != gadgetQ.gadgetId()
                && !(gadgetQId.isReal() && gadgetQId == gadgetE.gadgetId())) {
            gadgetQ.equip(gadgetQId);
        }
        if (gadgetEId != null && gadgetEId != gadgetE.gadgetId()
                && !(gadgetEId.isReal() && gadgetEId == gadgetQ.gadgetId())) {
            gadgetE.equip(gadgetEId);
        }
    }

    /** The utility in hand, or {@code null} while a gun or melee weapon is active. */
    public UtilityId activeUtilityId() {
        return isFilled(activeSlot) ? utilityIdForSlot(activeSlot) : null;
    }

    public boolean utilityActive() {
        return activeUtilityId() != null;
    }

    /**
     * Consumes one item from the active utility slot. When that was the last one, the slot becomes
     * empty and the hands advance to the next filled slot so an empty utility can never stay live.
     */
    public boolean consumeActiveUtility() {
        if (!utilityActive()) {
            return false;
        }
        if (activeSlot == SLOT_UTILITY_A) {
            utilityACount--;
        } else {
            utilityBCount--;
        }
        if (!isFilled(activeSlot)) {
            cycle(1);
        }
        return true;
    }

    /** The gun item in hand, or {@code null} when the active slot is not a filled gun slot. */
    public WeaponItem activeItem() {
        return switch (activeSlot) {
            case SLOT_PRIMARY -> primary != null && primary.hasWeapon() ? primary : null;
            case SLOT_HANDGUN -> handgun != null && handgun.hasWeapon() ? handgun : null;
            default -> null;
        };
    }

    public boolean meleeActive() {
        return activeSlot == SLOT_MELEE;
    }

    /** The gun in hand, or {@code null} when melee (or an empty/util slot) is active. */
    public WeaponId heldGunId() {
        WeaponItem item = activeItem();
        return item == null ? null : item.weaponId();
    }

    /**
     * What is in the hands, as a wire id: gun ordinal, {@link MeleeId} wire id, or
     * {@link UtilityId} wire id. Melee is the defensive fallback for invalid state.
     */
    public int heldWeaponWireId() {
        WeaponItem item = activeItem();
        if (item != null) {
            return item.weapon;
        }
        UtilityId utility = activeUtilityId();
        return utility == null ? meleeId().wireId() : utility.wireId();
    }

    // --- Selection rules (mechanics §8) ---------------------------------------------------------

    /**
     * Idempotent slot request: select {@code slot} when it is filled and not already active.
     * Cancels any in-progress reload on a real change. Returns whether the active slot changed.
     *
     * <p>This is the request the server applies to repeated client input, so it carries no
     * press semantics — re-receiving it is always safe.
     */
    public boolean selectSlot(int slot) {
        if (!isValidSlot(slot) || !isFilled(slot) || slot == activeSlot) {
            return false;
        }
        activeSlot = slot;
        quickSwapOrigin = NO_QUICK_SWAP;
        cancelReload();
        return true;
    }

    /**
     * Key-press semantics: as {@link #selectSlot}, except that pressing 1 or 2 <i>while that
     * slot is already active</i> quick-swaps to melee and remembers the origin; pressing a slot
     * key again afterwards selects that slot back, completing the round trip.
     */
    public boolean tapSlot(int slot) {
        if (!isValidSlot(slot) || !isFilled(slot)) {
            return false;
        }
        if (slot == activeSlot) {
            if (slot == SLOT_PRIMARY || slot == SLOT_HANDGUN) {
                activeSlot = SLOT_MELEE;
                quickSwapOrigin = slot;
                cancelReload();
                return true;
            }
            return false; // tapping melee (or an already-active slot) does nothing more
        }
        activeSlot = slot;
        quickSwapOrigin = NO_QUICK_SWAP;
        cancelReload();
        return true;
    }

    /**
     * Wheel semantics: cycle through the filled slots, skipping the empty ones, in either
     * direction. With a single filled slot it is the only destination. Returns the slot that is
     * now active.
     */
    public int cycle(int direction) {
        int step = direction < 0 ? -1 : 1;
        for (int i = 1; i <= SLOT_COUNT; i++) {
            int slot = wrapSlot(activeSlot + step * i);
            if (isFilled(slot)) {
                if (slot != activeSlot) {
                    activeSlot = slot;
                    quickSwapOrigin = NO_QUICK_SWAP;
                    cancelReload();
                }
                return activeSlot;
            }
        }
        return activeSlot;
    }

    private static int wrapSlot(int slot) {
        return ((slot - 1) % SLOT_COUNT + SLOT_COUNT) % SLOT_COUNT + 1;
    }

    private int firstFilledSlot() {
        for (int slot = 1; slot <= SLOT_COUNT; slot++) {
            if (isFilled(slot)) {
                return slot;
            }
        }
        return SLOT_MELEE; // can never happen: slot 3 is always filled
    }

    // --- Ammunition and reload (shared timing, server-authoritative) ----------------------------

    public void cancelReload() {
        reloading = false;
        reloadTimer = 0f;
    }

    /** True when the active gun could start a reload right now. */
    public boolean canReload() {
        WeaponItem item = activeItem();
        return item != null && !reloading && item.needsReload();
    }

    /** Starts reloading the active gun using its per-weapon reload time. No-op when not needed. */
    public boolean startReload() {
        if (!canReload()) {
            return false;
        }
        WeaponItem item = activeItem();
        WeaponDefinition definition = item.definition();
        reloading = true;
        reloadTimer = definition.reloadSeconds();
        return true;
    }

    /**
     * Advances the reload timer; on completion the magazine is topped up from the reserve.
     *
     * @return true on the tick the reload completes
     */
    public boolean updateReload(float dt) {
        if (!reloading) {
            return false;
        }
        reloadTimer -= dt;
        if (reloadTimer > RELOAD_TIMER_EPSILON) {
            return false;
        }
        completeReload();
        return true;
    }

    /** Total seconds the current reload takes (0 when not reloading). */
    public float reloadDuration() {
        if (!reloading) {
            return 0f;
        }
        WeaponItem item = activeItem();
        WeaponDefinition definition = item == null ? null : item.definition();
        return definition == null ? 0f : definition.reloadSeconds();
    }

    private void completeReload() {
        WeaponItem item = activeItem();
        if (item != null) {
            item.transferFromReserve(Integer.MAX_VALUE);
        }
        reloading = false;
        reloadTimer = 0f;
    }

    // --- Composition and respawn -----------------------------------------------------------------

    /**
     * Applies a new composition, refilling any weapon that changed to a full magazine and
     * reserve. {@code null} components keep what is already there. The active slot moves only
     * if the weapon it held is gone.
     */
    public void setComposition(WeaponId primaryId, WeaponId handgunId, MeleeId meleeId) {
        setComposition(primaryId, handgunId, meleeId, null, null);
    }

    /**
     * Seven-component composition update: the five main slots plus the two gadget slots. Null
     * components keep their current choice; gadget duplicates are rejected per
     * {@link #setGadgets}.
     */
    public void setComposition(
        WeaponId primaryId,
        WeaponId handgunId,
        MeleeId meleeId,
        UtilityId utilityAId,
        UtilityId utilityBId,
        GadgetId gadgetQId,
        GadgetId gadgetEId
    ) {
        setGadgets(gadgetQId, gadgetEId);
        setComposition(primaryId, handgunId, meleeId, utilityAId, utilityBId);
    }

    /**
     * Five-slot composition update. Null components keep their current choice; changing a utility
     * supplies its full carried count immediately, matching a newly-created gun's full magazine.
     */
    public void setComposition(
        WeaponId primaryId,
        WeaponId handgunId,
        MeleeId meleeId,
        UtilityId utilityAId,
        UtilityId utilityBId
    ) {
        if (primaryId != null && (primary == null || primary.weapon != primaryId.ordinal())) {
            primary = new WeaponItem(primaryId);
        }
        if (handgunId != null && (handgun == null || handgun.weapon != handgunId.ordinal())) {
            handgun = new WeaponItem(handgunId);
        }
        if (meleeId != null && melee != meleeId.ordinal()) {
            melee = meleeId.ordinal();
        }
        if (utilityAId != null && utilityA != utilityAId.ordinal()) {
            utilityA = utilityAId.ordinal();
            utilityACount = UtilityRegistry.of(utilityAId).carriedCount();
        }
        if (utilityBId != null && utilityB != utilityBId.ordinal()) {
            utilityB = utilityBId.ordinal();
            utilityBCount = UtilityRegistry.of(utilityBId).carriedCount();
        }
        cancelReload();
        if (!isFilled(activeSlot)) {
            activeSlot = firstFilledSlot();
            quickSwapOrigin = NO_QUICK_SWAP;
        }
    }

    /**
     * What a respawn does to a loadout (mechanics §10): both guns back to full, utilities
     * restocked, gadget state cleared (full durability, not broken, not active), no reload in
     * flight, hands back on the first real weapon. The composition itself is kept — a death
     * does not steal a chosen weapon.
     */
    public void resetForRespawn() {
        if (primary != null) {
            primary.refill();
        }
        if (handgun != null) {
            handgun.refill();
        }
        refillUtilities();
        gadgetQ.resetForRespawn();
        gadgetE.resetForRespawn();
        cancelReload();
        activeSlot = firstFilledSlot();
        quickSwapOrigin = NO_QUICK_SWAP;
    }

    private void refillUtilities() {
        UtilityId a = utilityIdForSlot(SLOT_UTILITY_A);
        UtilityId b = utilityIdForSlot(SLOT_UTILITY_B);
        utilityACount = a == null ? 0 : UtilityRegistry.of(a).carriedCount();
        utilityBCount = b == null ? 0 : UtilityRegistry.of(b).carriedCount();
    }

    @Override
    public String toString() {
        return "PlayerLoadout[slot=" + activeSlot
            + ", primary=" + (primary == null ? "empty" : primary)
            + ", handgun=" + (handgun == null ? "empty" : handgun)
            + ", melee=" + meleeId().displayName()
            + ", utilityA=" + utilityIdForSlot(SLOT_UTILITY_A) + " x" + utilityACount
            + ", utilityB=" + utilityIdForSlot(SLOT_UTILITY_B) + " x" + utilityBCount
            + ", gadgetQ=" + gadgetQ
            + ", gadgetE=" + gadgetE
            + (reloading ? String.format(", reloading %.2fs", reloadTimer) : "")
            + (quickSwapOrigin != NO_QUICK_SWAP ? ", swapFrom=" + quickSwapOrigin : "")
            + "]";
    }
}
