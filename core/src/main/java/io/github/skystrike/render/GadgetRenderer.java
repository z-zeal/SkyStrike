package io.github.skystrike.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import io.github.skystrike.shared.config.CombatConfig;
import io.github.skystrike.shared.config.GadgetConfig;
import io.github.skystrike.shared.config.PlayerConfig;
import io.github.skystrike.shared.model.CameraEntity;
import io.github.skystrike.shared.model.DroneEntity;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.model.ShieldState;
import java.util.List;

/**
 * Renders the Phase 6 gadgets in the entity layer: deployed drones, thrown cameras, the shield's
 * front/rear arc on players, and the rear fuel tank on its wearer.
 *
 * <p>Everything here is presentation — the entities it draws come from the snapshot (interpolated
 * for everyone else's devices, locally predicted for the piloted drone), and the two player-worn
 * gadgets are drawn from the loadout the renderer already receives. The shield arc and the tank
 * are drawn for <b>every</b> player who carries them, not just the local one: the roadmap's "done
 * when" is that the shield's front/back choice is legible and a flanker can see the tank to
 * shoot it, and both of those are things you read off an enemy.
 *
 * <p>The tank's rectangle reuses the exact fractions {@code HitZoneMath} resolves the rear zone
 * with, so what the renderer shows and what a bullet can hit are the same strip. The arc's angle
 * comes from {@code GadgetConfig} the same way {@code ShieldArcMath} absorbs with it.
 */
public final class GadgetRenderer implements Disposable {

    private static final Color COLOR_TEAM_A = new Color(0.20f, 0.55f, 0.90f, 1f);
    private static final Color COLOR_TEAM_B = new Color(0.90f, 0.25f, 0.20f, 1f);
    private static final Color COLOR_NEUTRAL = new Color(0.70f, 0.72f, 0.75f, 1f);
    private static final Color COLOR_DRONE_HULL = new Color(0.16f, 0.17f, 0.20f, 1f);
    private static final Color COLOR_DRONE_CORE = new Color(0.45f, 0.92f, 0.78f, 1f);
    private static final Color COLOR_CAMERA_BODY = new Color(0.12f, 0.13f, 0.16f, 1f);
    private static final Color COLOR_CAMERA_LENS = new Color(0.85f, 0.92f, 1.00f, 1f);
    private static final Color COLOR_SHIELD = new Color(0.35f, 0.75f, 0.95f, 0.65f);
    private static final Color COLOR_TANK = new Color(0.85f, 0.55f, 0.15f, 1f);
    private static final Color COLOR_HEALTH_BG = new Color(0.15f, 0.15f, 0.15f, 0.8f);
    private static final Color COLOR_HEALTH_FG = new Color(0.20f, 0.85f, 0.30f, 1f);

    /** How far the aim line reaches from a device's centre, in world units. */
    private static final float AIM_LINE_LENGTH = 16f;
    /** The shield arc's radius: just outside the body it protects. */
    private static final float SHIELD_ARC_RADIUS = PlayerConfig.WIDTH / 2f + 10f;

    private final ShapeRenderer shapes = new ShapeRenderer();

    public GadgetRenderer() {
    }

    /**
     * Draws every gadget in the world plus the two player-worn ones.
     *
     * @param remotePlayers every other player in the match
     * @param localPlayer   the local player, or null before spawn
     * @param drones        interpolated drones (the piloted one is the local prediction)
     * @param cameras       interpolated cameras
     */
    public void render(
            GameCamera camera,
            List<Player> remotePlayers,
            Player localPlayer,
            List<DroneEntity> drones,
            List<CameraEntity> cameras) {
        if (camera == null) {
            return;
        }
        shapes.setProjectionMatrix(camera.combined());

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        if (remotePlayers != null) {
            for (Player player : remotePlayers) {
                if (player != null && player.alive) {
                    drawFuelTank(player);
                }
            }
        }
        if (localPlayer != null && localPlayer.alive) {
            drawFuelTank(localPlayer);
        }
        if (drones != null) {
            for (DroneEntity drone : drones) {
                if (drone != null) {
                    drawDrone(drone);
                }
            }
        }
        if (cameras != null) {
            for (CameraEntity cameraEntity : cameras) {
                if (cameraEntity != null) {
                    drawCamera(cameraEntity);
                }
            }
        }
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        if (remotePlayers != null) {
            for (Player player : remotePlayers) {
                if (player != null && player.alive) {
                    drawShieldArc(player);
                }
            }
        }
        if (localPlayer != null && localPlayer.alive) {
            drawShieldArc(localPlayer);
        }
        if (drones != null) {
            for (DroneEntity drone : drones) {
                if (drone != null) {
                    drawDroneAim(drone);
                }
            }
        }
        if (cameras != null) {
            for (CameraEntity cameraEntity : cameras) {
                if (cameraEntity != null) {
                    drawCameraDetails(cameraEntity);
                }
            }
        }
        shapes.end();
    }

    // --- Drones -----------------------------------------------------------------------------------

    private void drawDrone(DroneEntity drone) {
        Color team = teamColor(drone.teamIndex);
        float size = GadgetConfig.DRONE_RADIUS * 2f;

        // Hull: a diamond, so a drone reads as a device rather than a body.
        shapes.setColor(COLOR_DRONE_HULL);
        shapes.circle(drone.x, drone.y, GadgetConfig.DRONE_RADIUS, 4);
        shapes.setColor(team);
        shapes.circle(drone.x, drone.y, GadgetConfig.DRONE_RADIUS - 2f, 4);

        // Core light, in the surveillance accent: the drone is a camera.
        shapes.setColor(COLOR_DRONE_CORE);
        shapes.circle(drone.x, drone.y, 2f, 6);

        healthBar(drone.x, drone.y + GadgetConfig.DRONE_RADIUS + 6f,
            drone.health / GadgetConfig.DRONE_HEALTH, size);
    }

    private void drawDroneAim(DroneEntity drone) {
        Color team = teamColor(drone.teamIndex);
        shapes.setColor(team.r, team.g, team.b, 0.55f);
        float radians = (float) Math.toRadians(drone.aimAngle);
        shapes.line(
            drone.x,
            drone.y,
            drone.x + AIM_LINE_LENGTH * (float) Math.cos(radians),
            drone.y + AIM_LINE_LENGTH * (float) Math.sin(radians));
    }

    // --- Cameras ----------------------------------------------------------------------------------

    private void drawCamera(CameraEntity camera) {
        Color team = teamColor(camera.teamIndex);
        float size = GadgetConfig.CAMERA_RADIUS * 2f;

        shapes.setColor(COLOR_CAMERA_BODY);
        shapes.rect(camera.x - size / 2f, camera.y - size / 2f, size, size);
        shapes.setColor(team);
        shapes.rect(camera.x - size / 2f + 1f, camera.y - size / 2f + 1f, size - 2f, size - 2f);

        healthBar(camera.x, camera.y + GadgetConfig.CAMERA_RADIUS + 6f,
            camera.health / GadgetConfig.CAMERA_HEALTH, size);
    }

    private void drawCameraDetails(CameraEntity camera) {
        if (camera.stuck) {
            // The lens: where the camera looks, which is the aim while it is being viewed.
            shapes.setColor(COLOR_CAMERA_LENS);
            float radians = (float) Math.toRadians(camera.aimAngle);
            shapes.line(
                camera.x,
                camera.y,
                camera.x + AIM_LINE_LENGTH * (float) Math.cos(radians),
                camera.y + AIM_LINE_LENGTH * (float) Math.sin(radians));
        } else {
            // In flight: a streak along the arc, so the throw reads as motion.
            shapes.setColor(COLOR_CAMERA_LENS);
            shapes.line(camera.prevX, camera.prevY, camera.x, camera.y);
        }
    }

    // --- Player-worn gadgets ------------------------------------------------------------------------

    /**
     * The shield's arc: front when equipped, rear when stowed, nothing when broken or absent.
     * Centred on the aim, exactly where {@code ShieldArcMath} tests absorption, so what the
     * player sees is what protects them.
     */
    private void drawShieldArc(Player player) {
        if (player.loadout == null) {
            return;
        }
        ShieldState state = player.loadout.shieldState();
        if (state == null || state == ShieldState.BROKEN) {
            return;
        }
        boolean equipped = state == ShieldState.EQUIPPED;
        float arcDegrees = equipped
            ? GadgetConfig.SHIELD_FRONT_ARC_DEGREES
            : GadgetConfig.SHIELD_REAR_ARC_DEGREES;
        float centre = equipped ? player.aimAngle : player.aimAngle + 180f;
        shapes.setColor(COLOR_SHIELD);
        shapes.arc(
            player.centerX(),
            player.centerY(),
            SHIELD_ARC_RADIUS,
            centre - arcDegrees / 2f,
            arcDegrees);
    }

    /**
     * The rear fuel tank: the exact strip {@code HitZoneMath} resolves the tank zone with — the
     * side the aim points away from, between 30% and 72% of the body's height. Drawn only while
     * the tank is worn and intact; a detonated tank is gone.
     */
    private void drawFuelTank(Player player) {
        if (player.loadout == null || !player.loadout.hasFuelTank()) {
            return;
        }
        float height = player.currentHeight();
        float tankWidth = PlayerConfig.WIDTH * CombatConfig.FUEL_TANK_WIDTH_FRACTION;
        float bottom = player.y + height * CombatConfig.FUEL_TANK_BOTTOM_FRACTION;
        float top = player.y + height * CombatConfig.FUEL_TANK_TOP_FRACTION;
        float rearEdge = player.isFacingRight()
            ? player.x - PlayerConfig.WIDTH / 2f
            : player.x + PlayerConfig.WIDTH / 2f;
        float x = player.isFacingRight() ? rearEdge : rearEdge - tankWidth;
        shapes.setColor(COLOR_TANK);
        shapes.rect(x, bottom, tankWidth, top - bottom);
    }

    // --- Shared bits --------------------------------------------------------------------------------

    private void healthBar(float centerX, float y, float fraction, float width) {
        float barWidth = Math.min(width + 12f, 28f);
        float barHeight = 3f;
        shapes.setColor(COLOR_HEALTH_BG);
        shapes.rect(centerX - barWidth / 2f, y, barWidth, barHeight);
        float filled = barWidth * Math.min(1f, Math.max(0f, fraction));
        if (filled > 0f) {
            shapes.setColor(COLOR_HEALTH_FG);
            shapes.rect(centerX - barWidth / 2f, y, filled, barHeight);
        }
    }

    private static Color teamColor(int teamIndex) {
        return switch (teamIndex) {
            case 0 -> COLOR_TEAM_A;
            case 1 -> COLOR_TEAM_B;
            default -> COLOR_NEUTRAL;
        };
    }

    @Override
    public void dispose() {
        shapes.dispose();
    }
}
