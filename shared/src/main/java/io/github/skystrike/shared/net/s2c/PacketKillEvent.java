package io.github.skystrike.shared.net.s2c;

import io.github.skystrike.shared.gadget.GadgetRegistry;
import io.github.skystrike.shared.net.Packet;
import io.github.skystrike.shared.utility.UtilityRegistry;
import io.github.skystrike.shared.weapons.WeaponId;
import io.github.skystrike.shared.weapons.WeaponRegistry;

/**
 * A death, broadcast to everyone.
 *
 * <p>Carries the names as well as the ids: a client that never saw the killer (they were outside
 * its vision cone the whole time, which is normal in this game) still has to be able to print
 * the line.
 */
public final class PacketKillEvent implements Packet {

    public int killerId;
    public String killerName = "";
    public int victimId;
    public String victimName = "";

    /**
     * The weapon responsible, as a wire id: a {@link WeaponId} ordinal for a gun,
     * 1000 + ordinal for melee, 2000 + ordinal for a utility, or 3000 + ordinal for a gadget.
     */
    public int weaponId;

    public boolean headshot;
    public boolean selfInflicted;
    public boolean friendlyFire;

    public PacketKillEvent() {
    }

    public PacketKillEvent(
        int killerId,
        String killerName,
        int victimId,
        String victimName,
        int weaponId,
        boolean headshot,
        boolean selfInflicted,
        boolean friendlyFire
    ) {
        this.killerId = killerId;
        this.killerName = killerName == null ? "" : killerName;
        this.victimId = victimId;
        this.victimName = victimName == null ? "" : victimName;
        this.weaponId = weaponId;
        this.headshot = headshot;
        this.selfInflicted = selfInflicted;
        this.friendlyFire = friendlyFire;
    }

    /**
     * The gun responsible. Only meaningful for gun wire ids — for melee or a utility use
     * {@link #weaponDisplayName()} instead.
     */
    public WeaponId weapon() {
        return WeaponId.fromOrdinal(weaponId);
    }

    /** Display name of the responsible gun, melee weapon or utility. */
    public String weaponDisplayName() {
        String gadget = GadgetRegistry.displayNameForWireId(weaponId);
        if (!gadget.isEmpty()) {
            return gadget;
        }
        String utility = UtilityRegistry.displayNameForWireId(weaponId);
        return utility.isEmpty() ? WeaponRegistry.displayNameForWireId(weaponId) : utility;
    }

    /** One line suitable for the kill feed. */
    public String feedLine() {
        if (selfInflicted) {
            return victimName + " killed themselves with the " + weaponDisplayName();
        }
        String suffix = headshot ? " (headshot)" : "";
        String prefix = friendlyFire ? "[FF] " : "";
        return prefix + killerName + " → " + victimName + "  " + weaponDisplayName() + suffix;
    }

    @Override
    public String toString() {
        return "PacketKillEvent[" + feedLine() + "]";
    }
}
