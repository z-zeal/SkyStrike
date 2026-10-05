package io.github.skystrike.shared.model;

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
 * empty — a player is never defenceless. Utility slots exist in the numbering but hold nothing
 * yet (Phase 5); they are never "filled", so selection and cycling skip them.
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

    /** The starting loadout: rifle, sidearm, knife — nobody spawns defenceless. */
    public PlayerLoadout() {
        this(WeaponId.DEFAULT, WeaponId.DEFAULT_SIDEARM, MeleeId.DEFAULT);
    }

    public PlayerLoadout(WeaponId primaryId, WeaponId handgunId, MeleeId meleeId) {
        this.primary = primaryId == null ? null : new WeaponItem(primaryId);
        this.handgun = handgunId == null ? null : new WeaponItem(handgunId);
        this.melee = (meleeId == null ? MeleeId.DEFAULT : meleeId).ordinal();
        this.activeSlot = firstFilledSlot();
    }

    public PlayerLoadout(PlayerLoadout other) {
        set(other);
    }

    public void set(PlayerLoadout other) {
        this.primary = other.primary == null ? null : other.primary.copy();
        this.handgun = other.handgun == null ? null : other.handgun.copy();
        this.melee = other.melee;
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

    /** A slot is filled when it holds something usable. Utility slots are empty until Phase 5. */
    public boolean isFilled(int slot) {
        return switch (slot) {
            case SLOT_PRIMARY -> primary != null && primary.hasWeapon();
            case SLOT_HANDGUN -> handgun != null && handgun.hasWeapon();
            case SLOT_MELEE -> true; // never empty, by rule
            default -> false; // utility slots: Phase 5
        };
    }

    public MeleeId meleeId() {
        return MeleeId.fromOrdinal(melee);
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
     * What is in the hands, as a wire id: a {@link WeaponId} ordinal for a gun, a
     * {@link MeleeId} wire id for melee — which is also the fallback for the not-yet-existent
     * utility slots, because a player is never defenceless.
     */
    public int heldWeaponWireId() {
        WeaponItem item = activeItem();
        return item != null ? item.weapon : meleeId().wireId();
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
        if (primaryId != null && (primary == null || primary.weapon != primaryId.ordinal())) {
            primary = new WeaponItem(primaryId);
        }
        if (handgunId != null && (handgun == null || handgun.weapon != handgunId.ordinal())) {
            handgun = new WeaponItem(handgunId);
        }
        if (meleeId != null && melee != meleeId.ordinal()) {
            melee = meleeId.ordinal();
        }
        cancelReload();
        if (!isFilled(activeSlot)) {
            activeSlot = firstFilledSlot();
            quickSwapOrigin = NO_QUICK_SWAP;
        }
    }

    /**
     * What a respawn does to a loadout (mechanics §10): both guns back to full, no reload in
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
        cancelReload();
        activeSlot = firstFilledSlot();
        quickSwapOrigin = NO_QUICK_SWAP;
    }

    @Override
    public String toString() {
        return "PlayerLoadout[slot=" + activeSlot
            + ", primary=" + (primary == null ? "empty" : primary)
            + ", handgun=" + (handgun == null ? "empty" : handgun)
            + ", melee=" + meleeId().displayName()
            + (reloading ? String.format(", reloading %.2fs", reloadTimer) : "")
            + (quickSwapOrigin != NO_QUICK_SWAP ? ", swapFrom=" + quickSwapOrigin : "")
            + "]";
    }
}
