package io.github.skystrike.server.gadget;

import io.github.skystrike.server.fx.EffectSink;
import io.github.skystrike.shared.config.GadgetConfig;
import io.github.skystrike.shared.effect.EffectSpawn;
import io.github.skystrike.shared.effect.EffectType;
import io.github.skystrike.shared.gadget.DroneMotion;
import io.github.skystrike.shared.gadget.GadgetId;
import io.github.skystrike.shared.gadget.GadgetPress;
import io.github.skystrike.shared.gadget.SurveillanceView;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.math.Angles;
import io.github.skystrike.shared.model.DroneEntity;
import io.github.skystrike.shared.model.GadgetSlot;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.physics.PlayerInput;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Authoritative lifecycle of every deployed surveillance drone (mechanics §7.1).
 *
 * <p>The system owns the entity list and nothing else: motion is the shared {@link DroneMotion}
 * (the client prediction runs the identical code), press transitions are the shared
 * {@link GadgetPress} state machine (the loadout tick dispatches Q/E edges here), and damage is
 * applied to the entity's health by the bullet system — the {@link #sweep(Collection)} then
 * destroys a spent drone, marks its gadget slot broken for the rest of the life and returns a
 * pilot to their own eyes.
 *
 * <p>Two calls per tick, both on the tick thread. {@link #stepOwned} runs inside the per-player
 * loop, because a piloted drone steers with that player's input; {@link #sweep} runs once after
 * combat, because that is when damage and deaths have landed. A drone whose owner died or left
 * is removed with them — respawning clears every gadget state (mechanics §10), and a disconnected
 * player's devices must not outlive their session.
 */
public final class DroneSystem {

    private final ArenaMap arena;
    private final List<DroneEntity> drones = new ArrayList<>();

    /**
     * Where destruction visuals go (build plan M7 §8.1). Null in geometry-only tests; a missing
     * sink never changes gameplay, only presentation.
     */
    private EffectSink effectSink;

    private int nextId = 1;

    public DroneSystem(ArenaMap arena) {
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
     * One Q/E press on the player's drone slot, dispatched from the loadout tick. Deploys the
     * drone above the owner, starts piloting it, or returns the view to the body — the shared
     * press machine decides, so the client's prediction and this authority cannot disagree.
     */
    public void press(Player player) {
        if (player == null || player.loadout == null || !player.alive || player.isSlowed()) {
            return;
        }
        GadgetSlot slot = player.loadout.droneSlot();
        if (slot == null) {
            return;
        }
        DroneEntity live = byOwner(player.id);
        GadgetPress.Outcome outcome = GadgetPress.resolve(
            GadgetId.DRONE,
            player.surveillance(),
            slot.isUsable(),
            live != null,
            live != null);
        GadgetPress.apply(player, slot, outcome);
        if (outcome == GadgetPress.Outcome.DRONE_DEPLOYED) {
            spawn(player);
        }
    }

    /**
     * Steps the drone owned by {@code player}, if any, with this tick's input. A piloted drone
     * flies with the input (movement keys drive the device, mechanics §9) and its cone follows the
     * aim; an unpiloted drone simply damps to a hover where it was left.
     */
    public void stepOwned(Player player, PlayerInput input, float dt) {
        if (player == null || !player.alive || dt <= 0f) {
            return;
        }
        DroneEntity drone = byOwner(player.id);
        if (drone == null) {
            return;
        }
        if (player.surveillance() == SurveillanceView.DRONE && input != null) {
            DroneMotion.stepInPlace(
                drone, input.moveX, input.jump || input.jetpack, input.crouch, dt, arena);
            drone.aimAngle = Angles.wrap(input.aimAngle);
        } else {
            DroneMotion.stepInPlace(drone, 0f, false, false, dt, arena);
        }
        mirrorDurability(player, drone);
    }

    /**
     * Removes drones whose owner died or left, and destroys the ones whose health ran out.
     * Runs once per tick after combat, on the tick thread.
     */
    public void sweep(Collection<Player> players) {
        for (int i = drones.size() - 1; i >= 0; i--) {
            DroneEntity drone = drones.get(i);
            Player owner = findById(players, drone.ownerId);
            if (owner == null || !owner.alive) {
                drones.remove(i);
                if (owner != null) {
                    releaseOwner(owner);
                }
                continue;
            }
            if (drone.isDestroyed()) {
                destroy(drone, owner);
                drones.remove(i);
            }
        }
    }

    /** Live drones in spawn order. Mutations stay inside this system. */
    public List<DroneEntity> active() {
        return Collections.unmodifiableList(drones);
    }

    /** The drone owned by {@code playerId}, or {@code null} when none is deployed. */
    public DroneEntity byOwner(int playerId) {
        for (DroneEntity drone : drones) {
            if (drone.ownerId == playerId) {
                return drone;
            }
        }
        return null;
    }

    /** Every drone owned by {@code playerId} — at most one, but the list keeps callers honest. */
    public List<DroneEntity> ownedBy(int playerId) {
        List<DroneEntity> owned = new ArrayList<>();
        for (DroneEntity drone : drones) {
            if (drone.ownerId == playerId) {
                owned.add(drone);
            }
        }
        return owned;
    }

    public int count() {
        return drones.size();
    }

    public ArenaMap arena() {
        return arena;
    }

    public void clear() {
        drones.clear();
    }

    /** Deploys a drone above the owner's head; the motion pass pushes it clear of any ceiling. */
    private void spawn(Player owner) {
        float x = owner.centerX();
        float y = owner.y + owner.currentHeight() + GadgetConfig.DRONE_SPAWN_OFFSET_Y;
        DroneEntity drone = new DroneEntity(
            nextId++, owner.id, owner.teamIndex, x, y, owner.aimAngle, GadgetConfig.DRONE_HEALTH);
        drones.add(drone);
    }

    /** A downed drone is gone for the rest of the life and a pilot loses the view with it. */
    private void destroy(DroneEntity drone, Player owner) {
        markSlotBroken(owner);
        releaseOwner(owner);
        // A small pop, not a frag: the drone is a device, not an explosive.
        emit(EffectType.IMPACT_EXPLOSION, drone.x, drone.y, 0f, 0.35f);
    }

    /** The owner's death or disconnect takes the drone with them; the slot follows at respawn. */
    private void releaseOwner(Player owner) {
        if (owner.surveillance() == SurveillanceView.DRONE) {
            owner.surveillanceView = SurveillanceView.SELF.ordinal();
        }
        if (owner.loadout != null) {
            GadgetSlot slot = owner.loadout.droneSlot();
            if (slot != null) {
                slot.active = false;
            }
        }
    }

    private void markSlotBroken(Player owner) {
        if (owner.loadout == null) {
            return;
        }
        GadgetSlot slot = owner.loadout.droneSlot();
        if (slot != null) {
            slot.broken = true;
            slot.active = false;
            slot.durability = 0f;
        }
    }

    /** The gadget slot's durability mirrors the entity's health so one HUD bar reads both. */
    private void mirrorDurability(Player owner, DroneEntity drone) {
        if (owner.loadout == null) {
            return;
        }
        GadgetSlot slot = owner.loadout.droneSlot();
        if (slot != null) {
            slot.durability = drone.health;
        }
    }

    private void emit(EffectType type, float x, float y, float angle, float scale) {
        if (effectSink != null) {
            effectSink.emit(new EffectSpawn(type, x, y, angle, scale));
        }
    }

    private static Player findById(Collection<Player> players, int id) {
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
}
