package io.github.skystrike.shared.hud;

import io.github.skystrike.shared.config.CombatConfig;
import io.github.skystrike.shared.net.s2c.PacketDamageEvent;
import io.github.skystrike.shared.weapons.MeleeId;
import io.github.skystrike.shared.weapons.WeaponDefinition;
import io.github.skystrike.shared.weapons.WeaponId;
import io.github.skystrike.shared.weapons.WeaponRegistry;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Floating damage numbers as data (mechanics §10, roadmap Phase 7 HUD), so the widget only has
 * to project and draw.
 *
 * <p>{@link PacketDamageEvent} goes to the two clients it concerns, and the packet's own
 * contract names who reads what: <i>the attacker gets a hit marker and a damage number, the
 * victim gets a direction to flinch from.</i> So the model files exactly one kind of event —
 * damage the local player dealt to somebody else. Self-inflicted damage (own molotov, own
 * fuel tank) is the vignette's business, and damage taken would duplicate it a second time.
 *
 * <p>Numbers anchor at the round's impact point in <b>world units</b> and live on the wall
 * clock, exactly like {@link KillFeedModel}: a frame spike cannot freeze them mid-air and a
 * paused client cannot hoard them. The widget projects them to the screen every frame, so a
 * number stays on the wall it hit even while the camera pans.
 *
 * <p>Each entry keeps the falloff ratio mechanics §10 asks the tint to carry: applied damage
 * against the round's muzzle damage (doubled for a headshot, which the server applies after
 * falloff — "a headshot is worth exactly double" at every range). Melee, utilities and gadgets
 * have no ballistic falloff, so they report a full 1.0.
 */
public final class DamageNumberModel {

    /** Total life of one number, seconds: readable, gone before the next engagement. */
    public static final float LIFETIME_SECONDS = 0.85f;

    /** The number holds at full opacity this long before it starts to fade. */
    public static final float HOLD_SECONDS = 0.15f;

    /**
     * How far a number drifts up over its life, in world units — one reference unit on the
     * mechanics plan's scale, half a standing player, so the rise reads at every zoom.
     */
    public static final float RISE_UNITS = 25f;

    /** Entries kept at once: a shotgun burst plus a few, oldest dropped first. */
    public static final int CAPACITY = 24;

    /**
     * One floating number, anchored where the round landed.
     *
     * @param worldX          impact x in world units
     * @param worldY          impact y in world units
     * @param amount          the damage to print, rounded to a whole number
     * @param headshot        whether the round landed in the head zone
     * @param killed          whether this hit finished the target
     * @param falloffRatio    applied damage over muzzle damage, 1 for weapons without falloff
     * @param timestampMillis when the event arrived, for ageing
     */
    public record Entry(
        float worldX,
        float worldY,
        int amount,
        boolean headshot,
        boolean killed,
        float falloffRatio,
        long timestampMillis
    ) {

        /** Age in seconds at {@code nowMillis}; never negative. */
        public float ageSeconds(long nowMillis) {
            return Math.max(0f, (nowMillis - timestampMillis) / 1000f);
        }

        /**
         * 1 while held, falling to 0 across the rest of the life, 0 once expired. The short
         * hold is what makes a number readable at all: it must land before the eye finds it.
         */
        public float alpha(long nowMillis) {
            float age = ageSeconds(nowMillis);
            if (age <= HOLD_SECONDS) {
                return 1f;
            }
            if (age >= LIFETIME_SECONDS) {
                return 0f;
            }
            return 1f - (age - HOLD_SECONDS) / (LIFETIME_SECONDS - HOLD_SECONDS);
        }

        /**
         * Upward drift in world units at {@code nowMillis}: fast off the impact point, easing
         * to a stop, so the number leaves the wound quickly and then holds still to be read.
         */
        public float riseUnits(long nowMillis) {
            float t = Math.min(1f, ageSeconds(nowMillis) / LIFETIME_SECONDS);
            return RISE_UNITS * (1f - (1f - t) * (1f - t));
        }
    }

    private final Deque<Entry> entries = new ArrayDeque<>();
    private int localPlayerId = -1;

    /** The id used to decide what counts as "damage dealt" on later additions. */
    public void setLocalPlayerId(int localPlayerId) {
        this.localPlayerId = localPlayerId;
    }

    public int localPlayerId() {
        return localPlayerId;
    }

    /**
     * Files one damage event. Only damage the local player dealt to somebody else becomes a
     * number (the packet's own contract); null events, unknown owners and zero damage are
     * ignored rather than drawn.
     */
    public void add(PacketDamageEvent damage, long nowMillis) {
        if (damage == null || localPlayerId < 0) {
            return;
        }
        if (damage.attackerId != localPlayerId || damage.targetId == localPlayerId) {
            return;
        }
        if (!(damage.amount > 0f)) {
            return;
        }
        entries.addLast(new Entry(
            damage.x,
            damage.y,
            Math.round(damage.amount),
            damage.isHeadshot(),
            damage.killed,
            falloffRatio(damage),
            nowMillis));
        while (entries.size() > CAPACITY) {
            entries.removeFirst();
        }
    }

    /**
     * The share of muzzle damage a hit applied, for mechanics §10's falloff tint. Gun hits are
     * measured against the round's table damage — doubled for a headshot, because the server
     * applies falloff first and the zone multiplier second. Melee, utilities and gadgets have
     * no falloff to show, so they read as a full 1.0.
     */
    public static float falloffRatio(PacketDamageEvent damage) {
        if (damage == null) {
            return 1f;
        }
        int wireId = damage.weaponId;
        boolean gun = wireId >= 0 && wireId < MeleeId.WIRE_ID_BASE && WeaponId.isValidOrdinal(wireId);
        if (!gun) {
            return 1f;
        }
        WeaponDefinition definition = WeaponRegistry.ofOrdinal(wireId);
        float base = definition.damage()
            * (damage.isHeadshot() ? CombatConfig.HEAD_DAMAGE_MULTIPLIER : 1f);
        if (!(base > 0f)) {
            return 1f;
        }
        return Math.min(1f, Math.max(0f, damage.amount / base));
    }

    /**
     * The numbers to draw at {@code nowMillis}, oldest first so newer hits layer on top.
     * Expired entries are dropped as they are passed — reading the model is what retires it.
     */
    public List<Entry> visible(long nowMillis) {
        entries.removeIf(entry -> entry.alpha(nowMillis) <= 0f);
        return new ArrayList<>(entries);
    }

    /** Entries held right now, expired or not. Diagnostics and tests. */
    public int size() {
        return entries.size();
    }

    public void clear() {
        entries.clear();
    }
}
