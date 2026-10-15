package io.github.skystrike.shared.utility;

import io.github.skystrike.shared.config.UtilityConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.map.Blocking;
import io.github.skystrike.shared.map.MapQueries;
import io.github.skystrike.shared.model.UtilityZone;
import java.util.List;

/**
 * Which way fire licks off the surface a molotov patch is burning on: the contact normal (the
 * open side), the surface tangent the server spread the fire along, and the flame direction that
 * falls out of both.
 *
 * <p>One molotov lays its patches in a line along the contact normal's perpendicular — the same
 * tangent on floors, ceilings and walls (see {@code UtilitySystem.addMolotovZones}). That line is
 * recoverable from the snapshot alone: sibling zones of the same cast share an owner, a utility,
 * a remaining lifetime and a spacing window, and the axis through the nearest sibling is the
 * server's tangent. The normal's sign is then resolved with a short
 * {@link MapQueries#lineBlocked} probe along each candidate — the side a solid answers for is
 * inside the surface, so the normal points the other way. A lone patch (its spread was stopped
 * immediately) probes the four axis directions instead and takes the negated sum of the blocked
 * ones: floor, ceiling and wall all resolve without a second rule.
 *
 * <p>The flame direction that comes out is deliberately not always world-up: on a floor the fire
 * rises with buoyancy, on a wall it climbs the wall leaning slightly off it, and on a ceiling it
 * clings and hangs into the open air below. Both consumers of this — the persistent flame
 * renderer and the sustained ember/heat-smoke emitter — ask this class, so the tongues and the
 * sparks agree about which surface the fire is on.
 *
 * <p>Lives in {@code shared} even though only the client draws fire: it is pure geometry over the
 * zone list and the map, evaluated by two subsystems that must never disagree, and the client
 * module has no test source set.
 */
public final class FireSurface {

    /**
     * The surface frame of one fire patch.
     *
     * @param normalX unit contact normal, pointing into the open air (away from the solid)
     * @param normalY unit contact normal, y component
     * @param axisX   unit tangent along the server's spread line, or 0 when the patch has no
     *                sibling to derive it from
     * @param axisY   tangent, y component
     * @param flameX  unit direction flames lick, from the normal per the class rules
     * @param flameY  flame direction, y component
     */
    public record Frame(
        float normalX,
        float normalY,
        float axisX,
        float axisY,
        float flameX,
        float flameY) {
    }

    /** How far the normal probes reach from the patch centre, in world units. */
    private static final float PROBE_DISTANCE = UtilityConfig.FIRE_ZONE_RADIUS * 0.6f;

    /**
     * How far apart two patches of one cast may sit and still be siblings: the server's spread
     * spacing plus generous slack for the deterministic jitter, well under the distance to any
     * neighbouring molotov's line.
     */
    private static final float SIBLING_WINDOW = UtilityConfig.FIRE_SPREAD_OFFSET * 1.75f;

    /** Above this {@code |normalY|} the surface is a floor or a ceiling rather than a wall. */
    private static final float FLAT_NORMAL_Y = 0.55f;

    /** How far a wall-climbing flame leans off the surface, against its upward climb. */
    private static final float WALL_LEAN = 0.35f;

    /** The up-only frame: no map, no siblings, fire behaves as it always did. */
    private static final Frame FLAT = new Frame(0f, 1f, 0f, 0f, 0f, 1f);

    private FireSurface() {
    }

    /**
     * The frame of {@code zone} against the live {@code map}, using the other zones of its cast
     * to recover the spread line. Never null: an unresolvable patch falls back to open-ground
     * fire that rises in world space. A {@code null} map is open space, so a caller that holds no
     * map gets the same fallback rather than an exception.
     */
    public static Frame of(UtilityZone zone, List<UtilityZone> zones, ArenaMap map) {
        if (zone == null) {
            return FLAT;
        }
        UtilityZone sibling = nearestSibling(zone, zones);
        float normalX;
        float normalY;
        float axisX = 0f;
        float axisY = 0f;
        if (sibling != null) {
            float dx = zone.x - sibling.x;
            float dy = zone.y - sibling.y;
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            if (length < 0.0001f) {
                return FLAT;
            }
            axisX = dx / length;
            axisY = dy / length;
            // The tangent's perpendicular spans the two normal candidates.
            float candidateX = -axisY;
            float candidateY = axisX;
            boolean plusBlocked = blocked(zone, candidateX, candidateY, map);
            boolean minusBlocked = blocked(zone, -candidateX, -candidateY, map);
            if (!plusBlocked && minusBlocked) {
                normalX = candidateX;
                normalY = candidateY;
            } else if (plusBlocked && !minusBlocked) {
                normalX = -candidateX;
                normalY = -candidateY;
            } else {
                // Edge or freestanding: prefer the candidate that points up, like unprobed fire.
                if (candidateY < 0f) {
                    candidateX = -candidateX;
                    candidateY = -candidateY;
                }
                normalX = candidateX;
                normalY = candidateY;
            }
        } else {
            float blockedX = 0f;
            float blockedY = 0f;
            if (blocked(zone, 1f, 0f, map)) {
                blockedX += 1f;
            }
            if (blocked(zone, -1f, 0f, map)) {
                blockedX -= 1f;
            }
            if (blocked(zone, 0f, 1f, map)) {
                blockedY += 1f;
            }
            if (blocked(zone, 0f, -1f, map)) {
                blockedY -= 1f;
            }
            if (blockedX == 0f && blockedY == 0f) {
                return FLAT;
            }
            float length = (float) Math.sqrt(blockedX * blockedX + blockedY * blockedY);
            normalX = -blockedX / length;
            normalY = -blockedY / length;
        }
        return new Frame(normalX, normalY, axisX, axisY,
            flameDirectionX(normalX, normalY), flameDirectionY(normalX, normalY));
    }

    /**
     * The flame direction of a frame's normal: buoyancy on anything floor-like, clinging and
     * hanging along anything ceiling-like, and a slight outward lean on the climb up a wall.
     * Always unit length, so the renderer can place tongues off it directly.
     */
    private static float flameDirectionX(float normalX, float normalY) {
        if (normalY >= FLAT_NORMAL_Y) {
            return 0f;
        }
        if (normalY <= -FLAT_NORMAL_Y) {
            return normalX;
        }
        return wallFlameX(normalX);
    }

    private static float flameDirectionY(float normalX, float normalY) {
        if (normalY >= FLAT_NORMAL_Y) {
            return 1f;
        }
        if (normalY <= -FLAT_NORMAL_Y) {
            return normalY;
        }
        return wallFlameY(normalX);
    }

    private static float wallFlameX(float normalX) {
        return normalX * WALL_LEAN / wallFlameLength(normalX);
    }

    private static float wallFlameY(float normalX) {
        return 1f / wallFlameLength(normalX);
    }

    private static float wallFlameLength(float normalX) {
        float x = normalX * WALL_LEAN;
        return (float) Math.sqrt(x * x + 1f);
    }

    /**
     * Whether a short probe from the patch centre along {@code (dx, dy)} meets a solid. The probe
     * runs on {@link Blocking#BLAST} — the same column the server's spread rule stopped on — so
     * a window that passed the bottle passes the probe too.
     */
    private static boolean blocked(UtilityZone zone, float dx, float dy, ArenaMap map) {
        return MapQueries.lineBlocked(
            map,
            zone.x,
            zone.y,
            zone.x + dx * PROBE_DISTANCE,
            zone.y + dy * PROBE_DISTANCE,
            Blocking.BLAST);
    }

    /**
     * The nearest other patch of the same cast, or {@code null}: same owner, same utility, a
     * remaining lifetime that has aged together, and inside the spread-spacing window. Two
     * molotovs landing near each other are different casts and never merge into one surface
     * line.
     */
    private static UtilityZone nearestSibling(UtilityZone zone, List<UtilityZone> zones) {
        if (zones == null) {
            return null;
        }
        UtilityZone nearest = null;
        float nearestDistanceSq = SIBLING_WINDOW * SIBLING_WINDOW;
        for (UtilityZone other : zones) {
            if (other == null || other == zone
                || other.id == zone.id
                || other.ownerId != zone.ownerId
                || other.utilityId != zone.utilityId) {
                continue;
            }
            if (Math.abs(other.remainingSeconds - zone.remainingSeconds) > 0.05f) {
                continue;
            }
            float dx = other.x - zone.x;
            float dy = other.y - zone.y;
            float distanceSq = dx * dx + dy * dy;
            if (distanceSq > 0.01f && distanceSq < nearestDistanceSq) {
                nearestDistanceSq = distanceSq;
                nearest = other;
            }
        }
        return nearest;
    }
}
