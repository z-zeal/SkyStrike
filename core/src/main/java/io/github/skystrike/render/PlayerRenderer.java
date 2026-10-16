package io.github.skystrike.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.IntFloatMap;
import io.github.skystrike.shared.config.PlayerConfig;
import io.github.skystrike.shared.math.Angles;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.utility.UtilityId;
import io.github.skystrike.shared.weapons.HeldWeaponGeometry;
import java.util.List;

/**
 * Renders players, body rotation, crouch posture, team tint, jetpack flames and aim direction in
 * {@link RenderLayers#ENTITIES}.
 *
 * <p>Every player received in a snapshot is <i>eligible</i> to be drawn — the snapshot is
 * deliberately not vision-culled — but an enemy the fog is hiding must not appear on the screen
 * at all: the composite's ambient floor would otherwise paint 4–6% of their sprite, and the user
 * is right that "barely visible" is visible. The composition root therefore gates each remote
 * player once per frame — the same {@code ObserverSet.isLit} question the minimap's blips ask —
 * smooths the answer through the shared {@code SightingFade} envelope (fast fade-in, brief hold,
 * slower fade-out, exactly 0 when unlit), and hands this renderer the finished per-player alpha
 * as data. Teammates and the local player are never gated and always draw at 1.
 *
 * <p>Anti-flicker is part of the gate, not a nicety: a target straddling the cone edge eases
 * through the envelope instead of blinking, and the alpha is multiplied into <b>everything</b>
 * that player draws — body, jetpack, flame, visor, health bar, held utility, held weapon sprite
 * and the barrel fallback — so no outline, bar or glow leaks a body the fog is meant to hide.
 * At exactly 0 the player is skipped entirely: unlit means not one pixel.
 *
 * <p>Corpses are the one exception to being drawn: a dead player is not drawn at all. Combat
 * state reaches the renderer the same way position does — through the snapshot — so the weapon
 * is drawn at {@link Player#renderedGunAngle()} rather than the raw aim. That is the aim plus the
 * recoil kick, and it is what makes a burst look like it climbs.
 *
 * <p>The held weapon is a catalog sprite ({@link WeaponSprites}), anchored at its grip over the
 * hands, rotated with the aim, flipped vertically when aiming left so the top rail stays up, and
 * drawn into the scene pass so the fog composite shades it like everything else. A missing sprite
 * falls back to the Phase-4 barrel line rather than an invisible gun.
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
    private static final Color COLOR_UTILITY_EXPLOSIVE = new Color(0.93f, 0.76f, 0.23f, 1f);
    private static final Color COLOR_UTILITY_SMOKE = new Color(0.67f, 0.71f, 0.74f, 1f);
    private static final Color COLOR_UTILITY_STUN = new Color(0.52f, 0.83f, 1.00f, 1f);
    private static final Color COLOR_UTILITY_FIRE = new Color(1.00f, 0.36f, 0.10f, 1f);
    private static final Color COLOR_UTILITY_TOXIC = new Color(0.45f, 0.90f, 0.30f, 1f);
    private static final Color COLOR_UTILITY_FLASH = new Color(1.00f, 0.98f, 0.85f, 1f);
    private static final Color COLOR_UTILITY_CLAYMORE = new Color(0.55f, 0.72f, 0.40f, 1f);

    private final ShapeRenderer shapes = new ShapeRenderer();
    private final SpriteBatch batch = new SpriteBatch();
    private final WeaponSprites weaponSprites = new WeaponSprites();
    /** Reused per draw, so gating never allocates colours per frame. */
    private final Color scratch = new Color();

    public PlayerRenderer() {
    }

    /**
     * The held-weapon atlas, shared with the HUD's loadout picker (build plan M4 §5) so
     * browsing the catalogue does not fill a second texture cache with the same sprites. This
     * renderer stays the owner: it loads them lazily and disposes them.
     */
    public WeaponSprites weaponSprites() {
        return weaponSprites;
    }

    /**
     * Renders all active players in world space; the composite pass supplies illumination.
     *
     * @param remoteAlphas finished per-player draw alpha from the composition root's enemy gate,
     *                     keyed by {@link Player#id}; missing entries read as 1 (ungated). The
     *                     renderer reads it as data and reaches back into nothing
     */
    public void render(
            GameCamera camera,
            List<Player> remotePlayers,
            Player localPlayer,
            IntFloatMap remoteAlphas) {
        shapes.setProjectionMatrix(camera.combined());

        // 1. Draw solid shapes (bodies, jetpacks, health bars)
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        if (remotePlayers != null) {
            for (Player p : remotePlayers) {
                float alpha = alphaFor(p, remoteAlphas);
                if (p.alive && alpha > 0f) {
                    drawPlayerFilled(p, alpha);
                }
            }
        }
        if (localPlayer != null && localPlayer.alive) {
            drawPlayerFilled(localPlayer, 1f);
        }
        if (remotePlayers != null) {
            for (Player p : remotePlayers) {
                float alpha = alphaFor(p, remoteAlphas);
                if (p.alive && alpha > 0f) {
                    drawHeldUtility(p, alpha);
                }
            }
        }
        if (localPlayer != null && localPlayer.alive) {
            drawHeldUtility(localPlayer, 1f);
        }
        shapes.end();

        // 2. Draw the held weapon sprites over the bodies.
        batch.setProjectionMatrix(camera.combined());
        batch.begin();
        if (remotePlayers != null) {
            for (Player p : remotePlayers) {
                float alpha = alphaFor(p, remoteAlphas);
                if (p.alive && alpha > 0f) {
                    drawHeldWeapon(p, alpha);
                }
            }
        }
        if (localPlayer != null && localPlayer.alive) {
            drawHeldWeapon(localPlayer, 1f);
        }
        batch.end();

        // 3. Draw line shapes (barrel fallback for missing sprites).
        shapes.begin(ShapeRenderer.ShapeType.Line);
        if (remotePlayers != null) {
            for (Player p : remotePlayers) {
                float alpha = alphaFor(p, remoteAlphas);
                if (p.alive && alpha > 0f && !UtilityId.isUtilityWireId(p.weaponId)
                    && !weaponSprites.hasTexture(p.weaponId)) {
                    drawPlayerLines(p, alpha);
                }
            }
        }
        if (localPlayer != null && localPlayer.alive
            && !UtilityId.isUtilityWireId(localPlayer.weaponId)
            && !weaponSprites.hasTexture(localPlayer.weaponId)) {
            drawPlayerLines(localPlayer, 1f);
        }
        shapes.end();
    }

    /** The gate's finished alpha for this player: 1 when nothing is known (ungated). */
    private static float alphaFor(Player p, IntFloatMap remoteAlphas) {
        return p == null ? 0f : (remoteAlphas == null ? 1f : remoteAlphas.get(p.id, 1f));
    }

    /** The palette entry at the gate's alpha, in the shared scratch colour. */
    private Color tint(Color base, float alpha) {
        return scratch.set(base.r, base.g, base.b, base.a * alpha);
    }

    /**
     * The held weapon, as its catalog sprite: grip anchored just ahead of the eye, rotated to
     * the recoil-kicked aim, flipped vertically while aiming left so the sights stay on top.
     * The frame size comes from the scale catalog — the sprites themselves are all fitted to
     * their 512px frame, so without that multiplier a pocket pistol would draw rifle-sized.
     */
    private void drawHeldWeapon(Player p, float alpha) {
        if (UtilityId.isUtilityWireId(p.weaponId)) {
            return;
        }
        Texture texture = weaponSprites.texture(p.weaponId);
        if (texture == null) {
            return; // the line pass draws the fallback barrel
        }

        float angle = p.renderedGunAngle();
        float rad = Angles.toRadians(angle);
        float size = weaponSprites.frameSize(p.weaponId);
        float originX = size * weaponSprites.gripAnchorX(p.weaponId);
        float originY = size * 0.5f;
        float handX = p.eyeX() + HeldWeaponGeometry.HAND_FORWARD_OFFSET * (float) Math.cos(rad);
        float handY = p.eyeY() + HeldWeaponGeometry.HAND_FORWARD_OFFSET * (float) Math.sin(rad);

        // The sprite carries its own colours; the gate only scales them.
        batch.setColor(1f, 1f, 1f, alpha);
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

    /** Draws a colour-coded marker at the hand instead of falling back to the default gun sprite. */
    private void drawHeldUtility(Player p, float alpha) {
        UtilityId utility = UtilityId.fromWireId(p.weaponId);
        if (utility == null) {
            return;
        }
        float angle = Angles.toRadians(p.renderedGunAngle());
        float handX = p.eyeX() + HeldWeaponGeometry.HAND_FORWARD_OFFSET * (float) Math.cos(angle);
        float handY = p.eyeY() + HeldWeaponGeometry.HAND_FORWARD_OFFSET * (float) Math.sin(angle);
        shapes.setColor(tint(utilityColor(utility), alpha));
        shapes.circle(handX, handY, 3.5f, 8);
    }

    private static Color utilityColor(UtilityId utility) {
        return switch (utility) {
            case FRAG, IMPACT -> COLOR_UTILITY_EXPLOSIVE;
            case SMOKE -> COLOR_UTILITY_SMOKE;
            case STUN -> COLOR_UTILITY_STUN;
            case MOLOTOV -> COLOR_UTILITY_FIRE;
            case POISON_SMOKE -> COLOR_UTILITY_TOXIC;
            case FLASHBANG -> COLOR_UTILITY_FLASH;
            case CLAYMORE -> COLOR_UTILITY_CLAYMORE;
        };
    }

    private void drawPlayerFilled(Player p, float alpha) {
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

        shapes.setColor(tint(COLOR_JETPACK, alpha));
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
            shapes.setColor(tint(COLOR_FLAME, alpha));
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
        shapes.setColor(tint(teamColor, alpha));
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
        shapes.setColor(tint(COLOR_VISOR, alpha));
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
        shapes.setColor(tint(COLOR_HEALTH_BG, alpha));
        shapes.rect(centerX - barW / 2f, barY, barW, barH);

        float healthRatio = MathUtils.clamp(p.health / PlayerConfig.MAX_HEALTH, 0f, 1f);
        shapes.setColor(tint(COLOR_HEALTH_FG, alpha));
        shapes.rect(centerX - barW / 2f, barY, barW * healthRatio, barH);
    }

    private void drawPlayerLines(Player p, float alpha) {
        float eyeX = p.eyeX();
        float eyeY = p.eyeY();
        // Aim plus visual recoil: the barrel climbs, the crosshair does not.
        float gunRad = Angles.toRadians(p.renderedGunAngle());
        // The same per-weapon muzzle the sprite's barrel reaches and the server spawns at, so a
        // missing sprite still shows a barrel of the right length instead of a pistol-sized one.
        float barrelLen = HeldWeaponGeometry.muzzleDistance(p.weaponId);

        float gunEndX = eyeX + barrelLen * (float) Math.cos(gunRad);
        float gunEndY = eyeY + barrelLen * (float) Math.sin(gunRad);

        // Gun barrel pointing at 360° aim
        shapes.setColor(tint(COLOR_GUN, alpha));
        shapes.line(eyeX, eyeY, gunEndX, gunEndY);
    }

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        weaponSprites.dispose();
    }
}
