package io.github.skystrike.shared.net.s2c;

import io.github.skystrike.shared.gadget.GadgetRegistry;
import io.github.skystrike.shared.model.HitZone;
import io.github.skystrike.shared.net.Packet;
import io.github.skystrike.shared.utility.UtilityRegistry;
import io.github.skystrike.shared.weapons.WeaponId;
import io.github.skystrike.shared.weapons.WeaponRegistry;

/**
 * One resolved damage instance.
 *
 * <p>Sent to the two clients that can act on it — the attacker gets a hit marker and a damage
 * number, the victim gets a direction to flinch from. Everyone else learns about the fight from
 * the kill feed, not from a stream of other people's damage.
 */
public final class PacketDamageEvent implements Packet {

    public int attackerId;
    public int targetId;

    /** Damage actually applied, after falloff and the zone multiplier. */
    public float amount;

    /** Target health after the hit, clamped at zero. */
    public float remainingHealth;

    public HitZone zone = HitZone.BODY;

    /**
     * The weapon responsible, as a wire id: a {@link WeaponId} ordinal for a gun,
     * 1000 + ordinal for melee, 2000 + ordinal for a utility, or 3000 + ordinal for a gadget.
     */
    public int weaponId;

    /** Where the round landed, for the damage number and the impact effect. */
    public float x;
    public float y;

    /** Path length the round had travelled, so the client can show falloff. */
    public float distanceTravelled;

    public boolean killed;

    public PacketDamageEvent() {
    }

    public PacketDamageEvent(
        int attackerId,
        int targetId,
        float amount,
        float remainingHealth,
        HitZone zone,
        int weaponId,
        float x,
        float y,
        float distanceTravelled,
        boolean killed
    ) {
        this.attackerId = attackerId;
        this.targetId = targetId;
        this.amount = amount;
        this.remainingHealth = remainingHealth;
        this.zone = zone == null ? HitZone.BODY : zone;
        this.weaponId = weaponId;
        this.x = x;
        this.y = y;
        this.distanceTravelled = distanceTravelled;
        this.killed = killed;
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

    public boolean isSelfInflicted() {
        return attackerId == targetId;
    }

    /** True when the round landed in the head zone and the damage was doubled. */
    public boolean isHeadshot() {
        return zone == HitZone.HEAD;
    }

    @Override
    public String toString() {
        return "PacketDamageEvent[attacker=" + attackerId
            + ", target=" + targetId
            + ", amount=" + String.format("%.1f", amount)
            + ", zone=" + zone
            + ", weapon=" + weaponDisplayName()
            + ", killed=" + killed + "]";
    }
}
