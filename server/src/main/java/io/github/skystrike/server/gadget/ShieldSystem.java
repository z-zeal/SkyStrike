package io.github.skystrike.server.gadget;

import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.model.PlayerLoadout;
import io.github.skystrike.shared.net.c2s.PacketPlayerInput;
import io.github.skystrike.shared.model.WeaponItem;
import io.github.skystrike.shared.weapons.WeaponRegistry;

/**
 * Authoritative shield actions and weapon restriction.
 *
 * <p>Damage interception itself lives in {@code DamageService}, the single entry point shared by
 * bullets, melee and area damage. This system owns the state transition caused by Q/E and the
 * server-side handgun rule. An equipped shield forces slot 2 when a handgun exists; if a loadout
 * has no handgun, its current slot remains visible but all weapon actions are blocked. The same
 * force-to-handgun rule is exposed by {@link PlayerLoadout} so client prediction cannot show a
 * primary or melee weapon while authority has already locked it.
 *
 * <p>Q/E edges are consumed by {@code LoadoutSystem} before the alive/stun checks. A dead or
 * stunned player therefore loses the edge rather than banking a toggle for later.
 */
public final class ShieldSystem {

    /** Applies one Q/E press; returns true when a non-passive gadget toggled. */
    public boolean toggle(Player player, int gadgetPress) {
        if (player == null || player.loadout == null || !player.alive || player.isSlowed()) {
            return false;
        }
        int index = switch (gadgetPress) {
            case PacketPlayerInput.GADGET_Q_PRESS -> 0;
            case PacketPlayerInput.GADGET_E_PRESS -> 1;
            default -> -1;
        };
        return index >= 0 && player.loadout.toggleGadget(index);
    }

    /**
     * Enforces the handgun-only restriction and reports whether a usable shield is equipped.
     * Call after applying slot/gadget edges and before weapon action.
     */
    public boolean enforceHandgunOnly(Player player) {
        if (player == null || player.loadout == null) {
            return false;
        }
        player.loadout.enforceShieldHandgunLock();
        return player.loadout.shieldEquipped();
    }

    /** True when shield equipment leaves no valid sidearm action. */
    public boolean blocksWeaponAction(Player player) {
        if (player == null || player.loadout == null || !player.loadout.shieldEquipped()) {
            return false;
        }
        if (player.loadout.activeSlot != PlayerLoadout.SLOT_HANDGUN
            || !player.loadout.isFilled(PlayerLoadout.SLOT_HANDGUN)) {
            return true;
        }
        WeaponItem handgun = player.loadout.activeItem();
        return handgun == null
            || handgun.weaponId() == null
            || !WeaponRegistry.of(handgun.weaponId()).ballistics().weaponClass().isSidearm();
    }
}
