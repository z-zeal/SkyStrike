package io.github.skystrike.shared.model;

import io.github.skystrike.shared.weapons.WeaponDefinition;
import io.github.skystrike.shared.weapons.WeaponId;
import io.github.skystrike.shared.weapons.WeaponRegistry;

/**
 * One gun as carried: which weapon it is, how many rounds are in the magazine, and how many are
 * in reserve. Magazine and reserve are tracked separately (mechanics §8), and the per-weapon
 * sizes come from the {@link WeaponRegistry} table rather than being stored a second time.
 *
 * <p>This is plain data plus ammo arithmetic — no timers, no spread. The live firing state of the
 * weapon in someone's hands is a separate concern (server-side {@code GunInstance}).
 */
public final class WeaponItem {

    /** Sentinel for the slot holding nothing: {@code weapon} is {@link #EMPTY_WEAPON}. */
    public static final int EMPTY_WEAPON = -1;

    /** Ordinal of the {@link WeaponId}, or {@link #EMPTY_WEAPON} when the slot is empty. */
    public int weapon = EMPTY_WEAPON;

    /** Rounds currently in the magazine (shells, for shotguns). */
    public int magazine;

    /** Rounds carried beyond the magazine. */
    public int reserve;

    public WeaponItem() {
    }

    /** The weapon with full magazine and full reserve, straight from the registry table. */
    public WeaponItem(WeaponId id) {
        this();
        fill(id);
    }

    public WeaponItem(WeaponItem other) {
        set(other);
    }

    public void set(WeaponItem other) {
        this.weapon = other.weapon;
        this.magazine = other.magazine;
        this.reserve = other.reserve;
    }

    public WeaponItem copy() {
        return new WeaponItem(this);
    }

    public boolean hasWeapon() {
        return weapon != EMPTY_WEAPON;
    }

    /** The weapon, or {@code null} when the slot is empty. */
    public WeaponId weaponId() {
        return hasWeapon() ? WeaponId.fromOrdinal(weapon) : null;
    }

    /** The registry definition of the held weapon, or {@code null} when empty. */
    public WeaponDefinition definition() {
        return hasWeapon() ? WeaponRegistry.ofOrdinal(weapon) : null;
    }

    /** Replaces the held weapon and fills magazine and reserve to the table values. */
    public void fill(WeaponId id) {
        WeaponDefinition definition = WeaponRegistry.of(id);
        this.weapon = id.ordinal();
        this.magazine = definition.magazineSize();
        this.reserve = definition.reserveAmmo();
    }

    /** Refills to the table values without changing the held weapon. */
    public void refill() {
        WeaponId id = weaponId();
        if (id != null) {
            fill(id);
        }
    }

    public int magazineSize() {
        WeaponDefinition definition = definition();
        return definition == null ? 0 : definition.magazineSize();
    }

    public boolean hasRounds() {
        return magazine > 0;
    }

    /**
     * Spends up to {@code rounds} from the magazine, clamped at what is there.
     *
     * @return the rounds actually spent
     */
    public int consume(int rounds) {
        int spent = Math.max(0, Math.min(rounds, magazine));
        magazine -= spent;
        return spent;
    }

    /** True when a reload would change anything: not full, and reserve holds something. */
    public boolean needsReload() {
        return hasWeapon() && magazine < magazineSize() && reserve > 0;
    }

    /**
     * Moves up to {@code rounds} from reserve into the magazine, clamped on both ends.
     *
     * @return the rounds actually moved
     */
    public int transferFromReserve(int rounds) {
        int moved = Math.max(0, Math.min(Math.min(rounds, magazineSize() - magazine), reserve));
        magazine += moved;
        reserve -= moved;
        return moved;
    }

    @Override
    public String toString() {
        return "WeaponItem[" + (hasWeapon() ? weaponId().displayName() : "empty")
            + ", mag=" + magazine + "/" + magazineSize()
            + ", reserve=" + reserve + "]";
    }
}
