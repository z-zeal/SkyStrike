package io.github.skystrike.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import io.github.skystrike.shared.config.CombatConfig;
import io.github.skystrike.shared.config.PlayerConfig;
import io.github.skystrike.shared.math.Angles;
import io.github.skystrike.shared.model.Player;
import java.util.List;

/**
 * Renders players, body rotation, crouch posture, team tint, jetpack flames and aim direction in
 * {@link RenderLayers#ENTITIES}.
 *
 * <p>Every player received in a snapshot is drawn into the scene. The lighting composite then
 * shades the complete player continuously, just like terrain, instead of abruptly adding or
 * removing the player when a visibility threshold is crossed.
 *
 * <p>Corpses are the one exception: a dead player is not drawn at all. Combat state reaches the
 * renderer the same way position does — through the snapshot — so the weapon is drawn at
 * {@link Player#renderedGunAngle()} rather than the raw aim. That is the aim plus the recoil
 * kick, and it is what makes a burst look like it climbs.
 *
 * <p>The held weapon is a catalog sprite ({@link WeaponSprites}), anchored at its grip over the
 * hands, rotated with the aim, flipped vertically when aiming left so the top rail stays up,
 * and drawn into the scene pass so the fog composite shades it like everything else. A missing
 * sprite falls back to the Phase-4 barrel line rather than an invisible gun.
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

    /** Hand offset from the eye along the aim, so the grip sits in front of the body. */
    private static final float HAND_FORWARD_OFFSET = 4f;

    private final ShapeRenderer shapes = new ShapeRenderer();
    private final SpriteBatch batch = new SpriteBatch();
    private final WeaponSprites weaponSprites = new WeaponSprites();

    public PlayerRenderer() {
    }

    /** Renders all active players in world space; the composite pass supplies illumination. */
    public void render(GameCamera camera, List<Player> remotePlayers, Player localPlayer) {
        shapes.setProjectionMatrix(camera.combined());

        // 1. Draw solid shapes (bodies, jetpacks, health bars)
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        if (remotePlayers != null) {
            for (Player p : remotePlayers) {
                if (p.alive) {
                    drawPlayerFilled(p);
                }
            }
        }
        if (localPlayer != null && localPlayer.alive) {
            drawPlayerFilled(localPlayer);
        }
        shapes.end();

        // 2. Draw the held weapon sprites over the bodies.
        batch.setProjectionMatrix(camera.combined());
        batch.begin();
        if (remotePlayers != null) {
            for (Player p : remotePlayers) {
                if (p.alive) {
                    drawHeldWeapon(p);
                }
            }
        }
        if (localPlayer != null && localPlayer.alive) {
            drawHeldWeapon(localPlayer);
        }
        batch.end();

        // 3. Draw line shapes (barrel fallback for missing sprites).
        shapes.begin(ShapeRenderer.ShapeType.Line);
        if (remotePlayers != null) {
            for (Player p : remotePlayers) {
                if (p.alive && !weaponSprites.hasTexture(p.weaponId)) {
                    drawPlayerLines(p);
                }
            }
        }
        if (localPlayer != null && localPlayer.alive && !weaponSprites.hasTexture(localPlayer.weaponId)) {
            drawPlayerLines(localPlayer);
        }
        shapes.end();
    }

    /**
     * The held weapon, as its catalog sprite: grip anchored just ahead of the eye, rotated to
     * the recoil-kicked aim, flipped vertically while aiming left so the sights stay on top.
     * The frame size comes from the scale catalog — the sprites themselves are all fitted to
     * their 512px frame, so without that multiplier a pocket pistol would draw rifle-sized.
     */
    private void drawHeldWeapon(Player p) {
        Texture texture = weaponSprites.texture(p.weaponId);
        if (texture == null) {
            return; // the line pass draws the fallback barrel
        }

        float angle = p.renderedGunAngle();
        float rad = Angles.toRadians(angle);
        float size = weaponSprites.frameSize(p.weaponId);
        float originX = size * weaponSprites.gripAnchorX(p.weaponId);
        float originY = size * 0.5f;
        float handX = p.eyeX() + HAND_FORWARD_OFFSET * (float) Math.cos(rad);
        float handY = p.eyeY() + HAND_FORWARD_OFFSET * (float) Math.sin(rad);

        batch.draw(
                texture,
                handX - originX,
                handY - originY,
                originX,
                originY,
                size,
                size,
                1f,
                1f,
                angle,
                0,
                0,
                texture.getWidth(),
                texture.getHeight(),
                false,
                !p.isFacingRight());
    }

    private void drawPlayerFilled(Player p) {
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
        // Aim plus visual recoil: the barrel climbs, the crosshair does not.
        float gunRad = Angles.toRadians(p.renderedGunAngle());
        float barrelLen = CombatConfig.MUZZLE_OFFSET;

        float gunEndX = eyeX + barrelLen * (float) Math.cos(gunRad);
        float gunEndY = eyeY + barrelLen * (float) Math.sin(gunRad);

        // Gun barrel pointing at 360° aim
        shapes.setColor(COLOR_GUN);
        shapes.line(eyeX, eyeY, gunEndX, gunEndY);
    }

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        weaponSprites.dispose();
    }
}
