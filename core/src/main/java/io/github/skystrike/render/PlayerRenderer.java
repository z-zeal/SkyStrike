package io.github.skystrike.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import io.github.skystrike.shared.config.PlayerConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.math.Angles;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.vision.SmokeVolume;
import io.github.skystrike.shared.vision.VisionMath;
import java.util.List;

/**
 * Renders players, body rotation, crouch posture, team tint, jetpack flames and aim direction in
 * {@link RenderLayers#ENTITIES}.
 *
 * <p>Enemies outside line of sight or outside vision reach are culled from rendering to ensure
 * they remain pure black / invisible behind fog and terrain.
 */
public final class PlayerRenderer implements Disposable {

    private static final Color COLOR_TEAM_A = new Color(0.20f, 0.55f, 0.90f, 1f);
    private static final Color COLOR_TEAM_B = new Color(0.90f, 0.25f, 0.20f, 1f);
    private static final Color COLOR_NEUTRAL = new Color(0.70f, 0.72f, 0.75f, 1f);
    private static final Color COLOR_GUN = new Color(0.12f, 0.12f, 0.15f, 1f);
    private static final Color COLOR_JETPACK = new Color(0.25f, 0.27f, 0.30f, 1f);
    private static final Color COLOR_FLAME = new Color(1.0f, 0.60f, 0.10f, 1f);
    private static final Color COLOR_VISOR = new Color(0.10f, 0.90f, 0.95f, 1f);
    private static final Color COLOR_HEALTH_BG = new Color(0.15f, 0.15f, 0.15f, 0.8f);
    private static final Color COLOR_HEALTH_FG = new Color(0.20f, 0.85f, 0.30f, 1f);

    private final ShapeRenderer shapes = new ShapeRenderer();

    public PlayerRenderer() {
    }

    /**
     * Renders all active players in world space with line-of-sight visibility culling.
     */
    public void render(
            GameCamera camera,
            List<Player> remotePlayers,
            Player localPlayer,
            ArenaMap map,
            List<SmokeVolume> smokeVolumes) {
        shapes.setProjectionMatrix(camera.combined());

        // 1. Draw solid shapes (bodies, jetpacks, health bars)
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        if (remotePlayers != null) {
            for (Player p : remotePlayers) {
                if (isPlayerVisibleToLocal(p, localPlayer, map, smokeVolumes)) {
                    drawPlayerFilled(p, false);
                }
            }
        }
        if (localPlayer != null) {
            drawPlayerFilled(localPlayer, true);
        }
        shapes.end();

        // 2. Draw line shapes (aim guns, outlines)
        shapes.begin(ShapeRenderer.ShapeType.Line);
        if (remotePlayers != null) {
            for (Player p : remotePlayers) {
                if (isPlayerVisibleToLocal(p, localPlayer, map, smokeVolumes)) {
                    drawPlayerLines(p);
                }
            }
        }
        if (localPlayer != null) {
            drawPlayerLines(localPlayer);
        }
        shapes.end();
    }

    private boolean isPlayerVisibleToLocal(
            Player remote, Player local, ArenaMap map, List<SmokeVolume> smokeVolumes) {
        if (local == null) {
            return true;
        }
        if (remote.id == local.id) {
            return true;
        }
        // Teammates always visible
        if (remote.teamIndex == local.teamIndex && remote.teamIndex != 2) {
            return true;
        }
        return VisionMath.canObserverSee(local, remote, map, smokeVolumes);
    }

    private void drawPlayerFilled(Player p, boolean isLocal) {
        float width = PlayerConfig.WIDTH;
        float height = p.currentHeight();
        float halfW = width / 2f;
        float halfH = height / 2f;
        float centerX = p.x;
        float centerY = p.y + halfH;
        float rotRad = Angles.toRadians(p.rotation);
        float cosR = (float) Math.cos(rotRad);
        float sinR = (float) Math.sin(rotRad);

        Color teamColor = switch (p.teamIndex) {
            case 0 -> COLOR_TEAM_A;
            case 1 -> COLOR_TEAM_B;
            default -> COLOR_NEUTRAL;
        };

        // Jetpack on back
        boolean facingRight = p.isFacingRight();
        float jetpackOffsetLocalX = facingRight ? -halfW - 3f : halfW + 3f;
        float jetpackWorldX = centerX + (jetpackOffsetLocalX * cosR);
        float jetpackWorldY = centerY + (jetpackOffsetLocalX * sinR);

        shapes.setColor(COLOR_JETPACK);
        shapes.rect(
                jetpackWorldX - 3f,
                jetpackWorldY - halfH * 0.5f,
                3f,
                halfH * 0.5f,
                6f,
                height * 0.6f,
                1f,
                1f,
                p.rotation);

        // Jetpack flame
        if (p.jetpacking && p.fuel > 0f) {
            shapes.setColor(COLOR_FLAME);
            float flameLen = 14f + MathUtils.random(0f, 6f);
            float flameBottomX = jetpackWorldX - (flameLen * sinR);
            float flameBottomY = jetpackWorldY - (flameLen * cosR);
            shapes.triangle(
                    jetpackWorldX - 3f * cosR,
                    jetpackWorldY - 3f * sinR,
                    jetpackWorldX + 3f * cosR,
                    jetpackWorldY + 3f * sinR,
                    flameBottomX,
                    flameBottomY);
        }

        // Body rectangle rotated around its center
        shapes.setColor(teamColor);
        shapes.rect(
                centerX - halfW,
                centerY - halfH,
                halfW,
                halfH,
                width,
                height,
                1f,
                1f,
                p.rotation);

        // Visor in head zone
        shapes.setColor(COLOR_VISOR);
        float visorLocalX = facingRight ? halfW * 0.3f : -halfW * 0.8f;
        float visorLocalY = halfH * 0.55f;
        float visorWorldX = centerX + (visorLocalX * cosR - visorLocalY * sinR);
        float visorWorldY = centerY + (visorLocalX * sinR + visorLocalY * cosR);
        shapes.rect(
                visorWorldX,
                visorWorldY,
                2f,
                2f,
                halfW * 0.5f,
                4f,
                1f,
                1f,
                p.rotation);

        // Health bar above head (unrotated for readability)
        float barY = p.y + height + 6f;
        float barW = 28f;
        float barH = 4f;
        shapes.setColor(COLOR_HEALTH_BG);
        shapes.rect(centerX - barW / 2f, barY, barW, barH);

        float healthRatio = MathUtils.clamp(p.health / PlayerConfig.MAX_HEALTH, 0f, 1f);
        shapes.setColor(COLOR_HEALTH_FG);
        shapes.rect(centerX - barW / 2f, barY, barW * healthRatio, barH);
    }

    private void drawPlayerLines(Player p) {
        float eyeX = p.eyeX();
        float eyeY = p.eyeY();
        float aimRad = Angles.toRadians(p.aimAngle);
        float barrelLen = 22f;

        float gunEndX = eyeX + barrelLen * (float) Math.cos(aimRad);
        float gunEndY = eyeY + barrelLen * (float) Math.sin(aimRad);

        // Gun barrel pointing at 360° aim
        shapes.setColor(COLOR_GUN);
        shapes.line(eyeX, eyeY, gunEndX, gunEndY);
    }

    @Override
    public void dispose() {
        shapes.dispose();
    }
}
