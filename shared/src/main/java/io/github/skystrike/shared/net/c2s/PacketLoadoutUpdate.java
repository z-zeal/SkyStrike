package io.github.skystrike.shared.net.c2s;

import io.github.skystrike.shared.net.Packet;

/**
 * Client-to-server request to change the loadout composition: which gun sits in slot 1, which
 * sidearm in slot 2, which melee weapon in slot 3.
 *
 * <p>The server validates the choice (a real gun for slot 1, a pistol for slot 2, a real melee
 * weapon for slot 3) and applies it at the next respawn, which is the one moment a loadout is
 * rebuilt anyway (mechanics §10). Each field is independent: {@link #KEEP_CURRENT} leaves that
 * slot as it is.
 */
public final class PacketLoadoutUpdate implements Packet {

    /** Field value meaning "do not change this slot". */
    public static final int KEEP_CURRENT = -1;

    /** New primary, as a {@link io.github.skystrike.shared.weapons.WeaponId} ordinal. */
    public int primary = KEEP_CURRENT;

    /** New sidearm, as a {@link io.github.skystrike.shared.weapons.WeaponId} ordinal. */
    public int handgun = KEEP_CURRENT;

    /** New melee weapon, as a {@link io.github.skystrike.shared.weapons.MeleeId} ordinal. */
    public int melee = KEEP_CURRENT;

    public PacketLoadoutUpdate() {
    }

    public PacketLoadoutUpdate(int primary, int handgun, int melee) {
        this.primary = primary;
        this.handgun = handgun;
        this.melee = melee;
    }

    /** True when every field asks to keep the current choice. */
    public boolean isEmpty() {
        return primary == KEEP_CURRENT && handgun == KEEP_CURRENT && melee == KEEP_CURRENT;
    }

    @Override
    public String toString() {
        return "PacketLoadoutUpdate[primary=" + primary
            + ", handgun=" + handgun
            + ", melee=" + melee + "]";
    }
}
