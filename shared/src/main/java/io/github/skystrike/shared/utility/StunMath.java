package io.github.skystrike.shared.utility;

import io.github.skystrike.shared.config.UtilityConfig;
import io.github.skystrike.shared.config.VisionConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.math.Angles;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.vision.SmokeVolume;
import io.github.skystrike.shared.vision.VisionMath;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Stun grenade banding (mechanics §6.1).
 *
 * <p>Distance is banded rather than continuous: inside 30% of the radius it is devastating, out
 * to 70% it is punishing, beyond that it is a nuisance. Bands make the effect something a player
 * can learn to judge by eye, which a smooth curve does not.
 *
 * <p>Sight is checked to the <b>eye</b>, not the centre — unlike a blast, which arrives at the
 * whole body. The detonation must also lie inside the player's forward vision cone. A flash you
 * did not see because cover blocked it <em>or because you turned away</em> does almost nothing:
 * no sight means a 0.3 s concussion and no slow at all.
 */
public final class StunMath {

    private StunMath() {
    }

    /** How long a player is blinded and slowed. */
    public record StunEffect(float blindSeconds, float slowSeconds) {

        public static final StunEffect NONE = new StunEffect(0f, 0f);

        public boolean isAnything() {
            return blindSeconds > 0f || slowSeconds > 0f;
        }
    }

    /** A stun applied to one player; {@code hadLineOfSight} includes terrain, smoke and facing. */
    public record StunHit(int playerId, StunEffect effect, float distance, boolean hadLineOfSight) {
    }

    /** Which band a distance falls in, as a label for tests and for the debug overlay. */
    public enum Band {
        INNER,
        MIDDLE,
        OUTER,
        OUT_OF_RANGE
    }

    public static Band bandFor(float distance, float radius) {
        if (radius <= 0f || distance >= radius) {
            return Band.OUT_OF_RANGE;
        }
        float fraction = distance / radius;
        if (fraction <= UtilityConfig.STUN_INNER_BAND) {
            return Band.INNER;
        }
        if (fraction <= UtilityConfig.STUN_MIDDLE_BAND) {
            return Band.MIDDLE;
        }
        return Band.OUTER;
    }

    /**
     * The effect of a stun at {@code (x, y)} on a point, given whether that point can see it.
     *
     * @param hasLineOfSight whether the detonation is visible from the target
     */
    public static StunEffect effectFor(
        float x, float y, float radius, float targetX, float targetY, boolean hasLineOfSight) {
        Band band = bandFor(distance(x, y, targetX, targetY), radius);
        if (band == Band.OUT_OF_RANGE) {
            return StunEffect.NONE;
        }
        if (!hasLineOfSight) {
            // In range but behind cover or facing away: a concussion, nothing more.
            return new StunEffect(UtilityConfig.STUN_NO_SIGHT_CONCUSSION_SECONDS, 0f);
        }
        return switch (band) {
            case INNER -> new StunEffect(
                UtilityConfig.STUN_INNER_BLIND_SECONDS, UtilityConfig.STUN_INNER_SLOW_SECONDS);
            case MIDDLE -> new StunEffect(
                UtilityConfig.STUN_MIDDLE_BLIND_SECONDS, UtilityConfig.STUN_MIDDLE_SLOW_SECONDS);
            case OUTER -> new StunEffect(
                UtilityConfig.STUN_OUTER_BLIND_SECONDS, UtilityConfig.STUN_OUTER_SLOW_SECONDS);
            case OUT_OF_RANGE -> StunEffect.NONE;
        };
    }

    /**
     * Resolves a stun against every candidate. One entry per affected player, live players only,
     * and nothing for players the stun does not reach.
     */
    public static List<StunHit> resolve(
        float x, float y, float radius, Collection<Player> candidates, ArenaMap map) {
        return resolve(x, y, radius, candidates, map, null);
    }

    /**
     * As {@link #resolve(float, float, float, Collection, ArenaMap)}, but smoke volumes take part
     * in the target's sight check just as terrain and facing do.
     */
    public static List<StunHit> resolve(
        float x,
        float y,
        float radius,
        Collection<Player> candidates,
        ArenaMap map,
        List<SmokeVolume> smokeVolumes
    ) {
        List<StunHit> hits = new ArrayList<>();
        if (candidates == null) {
            return hits;
        }
        for (Player player : candidates) {
            if (player == null || !player.alive) {
                continue;
            }
            float distance = distance(x, y, player.eyeX(), player.eyeY());
            if (bandFor(distance, radius) == Band.OUT_OF_RANGE) {
                continue;
            }
            boolean sees = canSeeDetonation(player, x, y, map, smokeVolumes);
            StunEffect effect = effectFor(x, y, radius, player.eyeX(), player.eyeY(), sees);
            if (!effect.isAnything()) {
                continue;
            }
            hits.add(new StunHit(player.id, effect, distance, sees));
        }
        return hits;
    }

    /**
     * Whether the player actually sees a stun detonation: terrain must be clear and it must be
     * inside their forward cone. The peripheral visibility floor is intentionally not enough —
     * turning completely away is a learnt defensive response, not a cosmetic camera change.
     */
    public static boolean canSeeDetonation(Player player, float x, float y, ArenaMap map) {
        return canSeeDetonation(player, x, y, map, null);
    }

    /** As {@link #canSeeDetonation(Player, float, float, ArenaMap)}, with active smoke occlusion. */
    public static boolean canSeeDetonation(
        Player player,
        float x,
        float y,
        ArenaMap map,
        List<SmokeVolume> smokeVolumes
    ) {
        if (player == null
            || !VisionMath.hasLineOfSight(x, y, player.eyeX(), player.eyeY(), map, smokeVolumes)) {
            return false;
        }
        float angleToDetonation = Angles.toDegrees((float) Math.atan2(y - player.eyeY(), x - player.eyeX()));
        return Math.abs(Angles.shortestDelta(player.aimAngle, angleToDetonation))
            <= VisionConfig.CONE_HALF_ANGLE_DEGREES;
    }

    /**
     * Remaining blindness, decaying exponentially from full at the moment of the flash.
     *
     * <p>Exponential rather than linear because the back half of a linear fade is a long dull
     * grey; an exponential one clears late and fast, which reads as recovering rather than as
     * waiting.
     */
    public static float blindIntensity(float remainingSeconds, float totalSeconds) {
        if (remainingSeconds <= 0f || totalSeconds <= 0f) {
            return 0f;
        }
        float elapsedFraction = 1f - Math.min(1f, remainingSeconds / totalSeconds);
        return (float) Math.exp(-4.0 * elapsedFraction);
    }

    /** Movement speed multiplier for a player who is currently slowed. */
    public static float moveSpeedMultiplier(boolean slowed) {
        return slowed ? UtilityConfig.STUN_MOVE_SPEED_MULTIPLIER : 1f;
    }

    private static float distance(float x0, float y0, float x1, float y1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
