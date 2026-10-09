package io.github.skystrike.server.utility;

import io.github.skystrike.server.combat.DamageService;
import io.github.skystrike.server.fx.EffectSink;
import io.github.skystrike.shared.config.CombatConfig;
import io.github.skystrike.shared.config.UtilityConfig;
import io.github.skystrike.shared.config.VisionConfig;
import io.github.skystrike.shared.effect.EffectSpawn;
import io.github.skystrike.shared.effect.EffectType;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.map.MapQueries;
import io.github.skystrike.shared.math.Angles;
import io.github.skystrike.shared.math.Lerp;
import io.github.skystrike.shared.model.HitZone;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.model.ThrownUtility;
import io.github.skystrike.shared.model.UtilityZone;
import io.github.skystrike.shared.utility.DirectionalBlastMath;
import io.github.skystrike.shared.utility.ExplosionMath;
import io.github.skystrike.shared.utility.StunMath;
import io.github.skystrike.shared.utility.ThrowablePhysics;
import io.github.skystrike.shared.utility.UtilityDefinition;
import io.github.skystrike.shared.utility.UtilityEffect;
import io.github.skystrike.shared.utility.UtilityId;
import io.github.skystrike.shared.utility.UtilityRegistry;
import io.github.skystrike.shared.vision.SmokeVolume;
import io.github.skystrike.shared.vision.VisionMath;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Authoritative throw, flight, detonation and lingering-zone lifecycle for Phase 5 utilities.
 *
 * <p>The only state this class owns is utility state. It delegates the common rules rather than
 * duplicating them: {@link ThrowablePhysics} is the same substepped integrator the client
 * trajectory preview uses, {@link ExplosionMath} performs terrain-occluded blast resolution,
 * {@link StunMath} owns the special sight-dependent bands, and {@link DamageService} is still the
 * only death bookkeeping path. A utility cannot bypass friendly-fire, kill-feed or respawn rules
 * by accident.
 *
 * <p>Persistent smoke zones are exposed both as {@link UtilityZone}s (for snapshots) and as the
 * matching {@link SmokeVolume} list (for gameplay sight queries). The client builds its shader
 * circle array from the snapshot zones through the same shared {@code SmokeCloud} cluster, so the
 * server has no second smoke geometry to drift from.
 */
public final class UtilitySystem {

    /**
     * Direction fire-zone effect events carry: straight up. Flames rise regardless of which
     * surface the molotov splashed on, so the client recipe reads the angle as "up", not as a
     * surface tangent (M7 §8.1 per-type angle contract).
     */
    private static final float FIRE_ZONE_ANGLE_DEGREES = 90f;

    private final ArenaMap arena;
    private final List<ThrownUtility> active = new ArrayList<>();
    private final List<UtilityZone> zones = new ArrayList<>();
    private final List<SmokeVolume> smokeVolumes = new ArrayList<>();

    /**
     * Where detonation visuals go (build plan M7 §8.1). Null in geometry-only tests; a missing
     * sink never changes gameplay, only presentation.
     */
    private EffectSink effectSink;

    private int nextThrownId = 1;
    private int nextZoneId = 1;
    private float dotClock;

    public UtilitySystem(ArenaMap arena) {
        if (arena == null) {
            throw new IllegalArgumentException("arena is required");
        }
        this.arena = arena;
    }

    /** Installs the effect sink. Null detaches; safe to call more than once. */
    public void setEffectSink(EffectSink effectSink) {
        this.effectSink = effectSink;
    }

    /**
     * Attempts to put an equipped utility into the world.
     *
     * <p>Returns false without consuming inventory when the utility cap is full or the release
     * point is inside terrain. The caller is deliberately responsible for consuming carried
     * count only after this succeeds.
     */
    public boolean throwUtility(Player owner, UtilityId utilityId) {
        if (owner == null || utilityId == null || !owner.alive
            || active.size() >= UtilityConfig.MAX_ACTIVE_THROWABLES) {
            return false;
        }
        UtilityDefinition definition = UtilityRegistry.of(utilityId);
        if (definition.detonation().isPlaced()) {
            return place(owner, definition);
        }

        float x = ThrowablePhysics.muzzleX(owner.eyeX(), owner.aimAngle);
        float y = ThrowablePhysics.muzzleY(owner.eyeY(), owner.aimAngle);
        if (!insideArena(x, y)
            || ThrowablePhysics.solidAt(arena, x, y) != null
            || !VisionMath.hasLineOfSight(owner.eyeX(), owner.eyeY(), x, y, arena)) {
            return false;
        }

        ThrownUtility thrown = new ThrownUtility(
            nextThrownId++,
            owner.id,
            owner.teamIndex,
            utilityId.ordinal(),
            x,
            y,
            ThrowablePhysics.throwVelocityX(definition.throwForce(), owner.aimAngle),
            ThrowablePhysics.throwVelocityY(definition.throwForce(), owner.aimAngle),
            definition.fuseSeconds());
        thrown.aimAngle = owner.aimAngle;
        active.add(thrown);
        return true;
    }

    /** Steps all live throwables and lingering zones once. Tick-thread only. */
    public void step(float dt, Collection<Player> players, DamageService damage) {
        if (dt <= 0f) {
            return;
        }

        for (int i = active.size() - 1; i >= 0; i--) {
            ThrownUtility thrown = active.get(i);
            UtilityDefinition definition = safeDefinition(thrown);
            if (definition == null) {
                active.remove(i);
                continue;
            }

            ThrowablePhysics.Contact contact = ThrowablePhysics.stepInPlace(thrown, dt, arena);
            boolean detonate = switch (definition.detonation()) {
                case FUSE -> thrown.fuseRemaining <= 0f;
                case CONTACT -> contact != ThrowablePhysics.Contact.NONE;
                case PROXIMITY -> isClaymoreTriggered(thrown, players);
            };
            if (detonate) {
                active.remove(i);
                detonate(thrown, definition, players, damage);
            } else if (thrown.age >= UtilityConfig.MAX_LIFETIME_SECONDS) {
                // The safety lifetime cap is disposal, not a surprise 20-second delayed blast.
                active.remove(i);
            }
        }

        // Resolve the shared DOT clock before removing zones that expire on this exact tick: a
        // six-second molotov with a 0.5-second clock deals its twelfth and final advertised tick.
        if (damage != null) {
            applyDamageOverTime(dt, players, damage);
        }
        updateZones(dt);
        rebuildSmokeVolumes();
    }

    /** Live thrown/placed devices in spawn order. Mutations stay inside this system. */
    public List<ThrownUtility> active() {
        return Collections.unmodifiableList(active);
    }

    /** Persistent smoke, poison and fire state in creation order. */
    public List<UtilityZone> zones() {
        return Collections.unmodifiableList(zones);
    }

    /** Exact smoke query volumes generated from the currently active snapshot zones. */
    public List<SmokeVolume> smokeVolumes() {
        return Collections.unmodifiableList(smokeVolumes);
    }

    public int count() {
        return active.size();
    }

    public int zoneCount() {
        return zones.size();
    }

    public ArenaMap arena() {
        return arena;
    }

    public void clear() {
        active.clear();
        zones.clear();
        smokeVolumes.clear();
        dotClock = 0f;
    }

    /**
     * Package-visible for deterministic unit tests of effect resolution without spending a fuse.
     * Production code reaches this only through {@link #step(float, Collection, DamageService)}.
     */
    void detonate(ThrownUtility thrown, UtilityDefinition definition, Collection<Player> players, DamageService damage) {
        switch (definition.effect()) {
            case BLAST -> {
                applyBlast(ExplosionMath.Blast.of(definition, thrown.x, thrown.y, thrown.ownerId), players, damage);
                // M7 §8.1: the detonation is silent and invisible without this event.
                emit(definition.id() == UtilityId.IMPACT
                        ? EffectType.IMPACT_EXPLOSION
                        : EffectType.FRAG_EXPLOSION,
                    thrown.x, thrown.y, 0f, 1f);
            }
            case DIRECTIONAL_BLAST -> {
                applyDirectionalBlast(
                    DirectionalBlastMath.Blast.of(definition, thrown.x, thrown.y, thrown.aimAngle, thrown.ownerId),
                    players,
                    damage);
                emit(EffectType.CLAYMORE_BLAST, thrown.x, thrown.y, thrown.aimAngle, 1f);
            }
            case STUN -> {
                applyStun(thrown, definition, players);
                // The blind itself is server state on the player; the event is the white burst.
                emit(EffectType.FLASH_DETONATION, thrown.x, thrown.y, 0f, 1f);
            }
            case FLASH -> {
                applyFlash(thrown, definition, players);
                emit(EffectType.FLASH_DETONATION, thrown.x, thrown.y, 0f, 1f);
            }
            case SMOKE_CLOUD, TOXIC_CLOUD -> {
                addZone(new UtilityZone(
                    nextZoneId++,
                    thrown.ownerId,
                    thrown.teamIndex,
                    thrown.utilityId,
                    thrown.x,
                    thrown.y,
                    definition.radius(),
                    definition.damage(),
                    definition.durationSeconds()));
                // The cloud grows into the zone's own radius, so the visual and the shader
                // circle stay the same thing (M7 §8.3).
                emit(definition.effect() == UtilityEffect.SMOKE_CLOUD
                        ? EffectType.SMOKE_BURST
                        : EffectType.POISON_BURST,
                    thrown.x, thrown.y, 0f, cloudScale(definition));
            }
            case FIRE -> {
                // The splash is the impact itself; the zones (and their per-zone flame events)
                // follow as the fire spreads along the surface.
                emit(EffectType.MOLOTOV_SPLASH, thrown.x, thrown.y, tangentAngleDegrees(thrown), 1f);
                addMolotovZones(thrown, definition);
            }
        }
    }

    /** One effect request to the sink, if any is installed. Never throws, never blocks. */
    private void emit(EffectType type, float x, float y, float angle, float scale) {
        if (effectSink != null) {
            effectSink.emit(new EffectSpawn(type, x, y, angle, scale));
        }
    }

    /**
     * The cloud scale for smoke and poison: the zone radius in units of the shared default, so
     * the client's recipe grows its puffs to exactly the radius the snapshot carries.
     */
    private static float cloudScale(UtilityDefinition definition) {
        return definition.radius() / VisionConfig.DEFAULT_SMOKE_RADIUS;
    }

    /**
     * The surface tangent at impact, as a direction in degrees: the molotov's fire splash runs
     * along it, so the spread reads correctly on floors, ramps and vertical walls alike.
     */
    private static float tangentAngleDegrees(ThrownUtility thrown) {
        float normalX = thrown.hasContactNormal() ? thrown.contactNormalX : 0f;
        float normalY = thrown.hasContactNormal() ? thrown.contactNormalY : 1f;
        return Angles.toDegrees((float) Math.atan2(normalX, -normalY));
    }

    private boolean place(Player owner, UtilityDefinition definition) {
        // A placed device sits on the highest surface directly in front of the user's feet. It
        // cannot hover at eye height and does not borrow grenade flight physics.
        float direction = (float) Math.cos(Math.toRadians(owner.aimAngle));
        float x = Lerp.clamp(
            owner.centerX() + direction * UtilityConfig.THROW_OFFSET,
            UtilityConfig.THROWABLE_RADIUS,
            arena.width() - UtilityConfig.THROWABLE_RADIUS);
        float support = MapQueries.surfaceBelow(arena, x, owner.y + owner.currentHeight());
        float y = support + UtilityConfig.THROWABLE_RADIUS + UtilityConfig.CONTACT_SKIN;
        if (ThrowablePhysics.solidAt(arena, x, y) != null) {
            return false;
        }

        ThrownUtility placed = new ThrownUtility(
            nextThrownId++, owner.id, owner.teamIndex, definition.id().ordinal(), x, y, 0f, 0f, 0f);
        placed.aimAngle = owner.aimAngle;
        placed.resting = true;
        placed.contactNormalY = 1f;
        active.add(placed);
        return true;
    }

    private boolean isClaymoreTriggered(ThrownUtility claymore, Collection<Player> players) {
        if (claymore.age < UtilityConfig.CLAYMORE_ARMING_SECONDS || players == null) {
            return false;
        }
        for (Player candidate : players) {
            if (candidate == null || !candidate.alive || candidate.id == claymore.ownerId) {
                continue;
            }
            // A trap triggers on a hostile entry. Friendly fire still applies to everyone caught
            // in the eventual blast; teammates merely do not remotely detonate their own trap.
            if (candidate.teamIndex == claymore.teamIndex
                && candidate.teamIndex != CombatConfig.NEUTRAL_TEAM_INDEX) {
                continue;
            }
            float dx = candidate.centerX() - claymore.x;
            float dy = candidate.centerY() - claymore.y;
            if (dx * dx + dy * dy <= UtilityConfig.CLAYMORE_TRIGGER_RADIUS * UtilityConfig.CLAYMORE_TRIGGER_RADIUS) {
                return true;
            }
        }
        return false;
    }

    private void applyBlast(ExplosionMath.Blast blast, Collection<Player> players, DamageService damage) {
        if (damage == null) {
            return;
        }
        Player attacker = playerById(players, blast.ownerId());
        for (ExplosionMath.BlastHit hit : ExplosionMath.resolve(blast, players, arena)) {
            Player target = playerById(players, hit.playerId());
            if (!DamageService.canDamage(attacker, target)) {
                continue;
            }
            DamageService.DamageResult result = damage.apply(
                attacker,
                blast.ownerId(),
                target,
                hit.damage(),
                HitZone.BODY,
                blast.weaponWireId(),
                blast.x(),
                blast.y(),
                hit.distance());
            if (result != null && target.alive) {
                target.vx += hit.impulseX();
                target.vy += hit.impulseY();
            }
        }
    }

    private void applyDirectionalBlast(
        DirectionalBlastMath.Blast blast,
        Collection<Player> players,
        DamageService damage
    ) {
        if (damage == null) {
            return;
        }
        Player attacker = playerById(players, blast.ownerId());
        for (DirectionalBlastMath.BlastHit hit : DirectionalBlastMath.resolve(blast, players, arena)) {
            Player target = playerById(players, hit.playerId());
            if (!DamageService.canDamage(attacker, target)) {
                continue;
            }
            DamageService.DamageResult result = damage.apply(
                attacker,
                blast.ownerId(),
                target,
                hit.damage(),
                HitZone.BODY,
                blast.weaponWireId(),
                blast.x(),
                blast.y(),
                hit.distance());
            if (result != null && target.alive) {
                target.vx += hit.impulseX();
                target.vy += hit.impulseY();
            }
        }
    }

    private void applyStun(ThrownUtility thrown, UtilityDefinition definition, Collection<Player> players) {
        for (StunMath.StunHit hit : StunMath.resolve(
            thrown.x, thrown.y, definition.radius(), players, arena, smokeVolumes)) {
            Player target = playerById(players, hit.playerId());
            if (target != null) {
                target.applyStatus(hit.effect().blindSeconds(), hit.effect().slowSeconds());
            }
        }
    }

    private void applyFlash(ThrownUtility thrown, UtilityDefinition definition, Collection<Player> players) {
        if (players == null) {
            return;
        }
        float radiusSq = definition.radius() * definition.radius();
        for (Player player : players) {
            if (player == null || !player.alive) {
                continue;
            }
            float dx = player.eyeX() - thrown.x;
            float dy = player.eyeY() - thrown.y;
            if (dx * dx + dy * dy <= radiusSq
                && VisionMath.hasLineOfSight(thrown.x, thrown.y, player.eyeX(), player.eyeY(), arena, smokeVolumes)) {
                player.applyStatus(definition.durationSeconds(), 0f);
            }
        }
    }

    private void addMolotovZones(ThrownUtility thrown, UtilityDefinition definition) {
        if (addZone(new UtilityZone(
            nextZoneId++,
            thrown.ownerId,
            thrown.teamIndex,
            thrown.utilityId,
            thrown.x,
            thrown.y,
            UtilityConfig.FIRE_ZONE_RADIUS,
            definition.damage(),
            definition.durationSeconds()))) {
            // M7 §8.3: one flickering attached light per fire zone, carried by the event.
            emit(EffectType.FIRE_ZONE, thrown.x, thrown.y, FIRE_ZONE_ANGLE_DEGREES, 1f);
        }

        // A contact normal's perpendicular is the surface tangent. The shared physics records
        // the normal at impact, so the exact same fire spread works on floors, ceilings and walls.
        float normalX = thrown.hasContactNormal() ? thrown.contactNormalX : 0f;
        float normalY = thrown.hasContactNormal() ? thrown.contactNormalY : 1f;
        float tangentX = -normalY;
        float tangentY = normalX;
        for (int side : new int[] {-1, 1}) {
            float previousX = thrown.x;
            float previousY = thrown.y;
            for (int index = 1; index <= UtilityConfig.FIRE_SPREAD_ZONES_PER_SIDE; index++) {
                float distance = index * UtilityConfig.FIRE_SPREAD_OFFSET
                    + deterministicJitter(thrown.id, side * index);
                float zoneX = thrown.x + tangentX * side * distance;
                float zoneY = thrown.y + tangentY * side * distance;
                // A surface line cannot jump through a wall or solid platform. Stop this side at
                // its last clear patch rather than placing fire on the far side of the obstruction.
                if (MapQueries.lineBlocked(arena, previousX, previousY, zoneX, zoneY)) {
                    break;
                }
                if (!addZone(new UtilityZone(
                    nextZoneId++,
                    thrown.ownerId,
                    thrown.teamIndex,
                    thrown.utilityId,
                    zoneX,
                    zoneY,
                    UtilityConfig.FIRE_ZONE_RADIUS,
                    UtilityConfig.FIRE_SPREAD_DAMAGE,
                    definition.durationSeconds()))) {
                    break;
                }
                emit(EffectType.FIRE_ZONE, zoneX, zoneY, FIRE_ZONE_ANGLE_DEGREES, 1f);
                previousX = zoneX;
                previousY = zoneY;
            }
        }
    }

    /** @return true when the zone was actually added, false at the active-zone cap */
    private boolean addZone(UtilityZone zone) {
        if (zone != null && zones.size() < UtilityConfig.MAX_ACTIVE_ZONES) {
            zones.add(zone);
            return true;
        }
        return false;
    }

    private void updateZones(float dt) {
        for (int i = zones.size() - 1; i >= 0; i--) {
            UtilityZone zone = zones.get(i);
            zone.remainingSeconds -= dt;
            if (zone.remainingSeconds <= 0f) {
                zones.remove(i);
            }
        }
    }

    /**
     * Applies the DOT clock to each player at most once per interval. Overlapping molotov spread
     * patches choose the strongest patch instead of multiplying seven almost-coincident circles
     * into an instant kill; outward casts extend the denial area, they do not stack damage.
     */
    private void applyDamageOverTime(float dt, Collection<Player> players, DamageService damage) {
        if (players == null || zones.isEmpty()) {
            return;
        }
        dotClock += dt;
        while (dotClock >= UtilityConfig.DOT_TICK_SECONDS) {
            dotClock -= UtilityConfig.DOT_TICK_SECONDS;
            for (Player target : players) {
                if (target == null || !target.alive) {
                    continue;
                }
                UtilityZone strongest = strongestDamageZoneAt(target.centerX(), target.centerY());
                if (strongest == null) {
                    continue;
                }
                Player attacker = playerById(players, strongest.ownerId);
                if (DamageService.canDamage(attacker, target)) {
                    damage.apply(
                        attacker,
                        strongest.ownerId,
                        target,
                        strongest.damage,
                        HitZone.BODY,
                        strongest.utility().wireId(),
                        strongest.x,
                        strongest.y,
                        0f);
                }
            }
        }
    }

    private UtilityZone strongestDamageZoneAt(float x, float y) {
        UtilityZone strongest = null;
        for (UtilityZone zone : zones) {
            if (zone.damage <= 0f) {
                continue;
            }
            float dx = x - zone.x;
            float dy = y - zone.y;
            if (dx * dx + dy * dy > zone.radius * zone.radius) {
                continue;
            }
            if (strongest == null || zone.damage > strongest.damage
                || (zone.damage == strongest.damage && zone.id < strongest.id)) {
                strongest = zone;
            }
        }
        return strongest;
    }

    private void rebuildSmokeVolumes() {
        smokeVolumes.clear();
        for (UtilityZone zone : zones) {
            if (!zone.blocksVision()) {
                continue;
            }
            // Each cloud contributes its whole SmokeCloud cluster, so the occlusion follows the
            // rendered cloud's shape. The visibility shader has a finite uniform array; the same
            // cap, filled in the same creation order the client derives from the snapshot, means
            // visual and gameplay sight cannot disagree under a smoke-spam attempt.
            for (SmokeVolume volume : zone.smokeVolumes()) {
                if (smokeVolumes.size() >= VisionConfig.MAX_SMOKE_VOLUMES) {
                    return;
                }
                smokeVolumes.add(volume);
            }
        }
    }

    private static UtilityDefinition safeDefinition(ThrownUtility thrown) {
        if (thrown == null || !UtilityId.isValidOrdinal(thrown.utilityId)) {
            return null;
        }
        return UtilityRegistry.ofOrdinal(thrown.utilityId);
    }

    private boolean insideArena(float x, float y) {
        return x >= UtilityConfig.THROWABLE_RADIUS
            && x <= arena.width() - UtilityConfig.THROWABLE_RADIUS
            && y >= UtilityConfig.THROWABLE_RADIUS
            && y <= arena.height() - UtilityConfig.THROWABLE_RADIUS;
    }

    private static Player playerById(Collection<Player> players, int id) {
        if (players == null) {
            return null;
        }
        for (Player player : players) {
            if (player != null && player.id == id) {
                return player;
            }
        }
        return null;
    }

    private static float deterministicJitter(int thrownId, int signedIndex) {
        int value = thrownId * 1103515245 + signedIndex * 12345;
        float unit = ((value >>> 8) & 0xffff) / 65535f;
        return (unit * 2f - 1f) * UtilityConfig.FIRE_SPREAD_JITTER;
    }
}
