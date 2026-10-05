package io.github.skystrike.shared.net.s2c;

import io.github.skystrike.shared.net.Packet;
import io.github.skystrike.shared.weapons.WeaponId;

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

    /** Ordinal of the {@link WeaponId} responsible. */
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

    public WeaponId weapon() {
        return WeaponId.fromOrdinal(weaponId);
    }

    /** One line suitable for the kill feed. */
    public String feedLine() {
        if (selfInflicted) {
            return victimName + " killed themselves with the " + weapon().displayName();
        }
        String suffix = headshot ? " (headshot)" : "";
        String prefix = friendlyFire ? "[FF] " : "";
        return prefix + killerName + " → " + victimName + "  " + weapon().displayName() + suffix;
    }

    @Override
    public String toString() {
        return "PacketKillEvent[" + feedLine() + "]";
    }
}
