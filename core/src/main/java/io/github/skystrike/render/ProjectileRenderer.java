package io.github.skystrike.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import io.github.skystrike.shared.config.CombatConfig;
import io.github.skystrike.shared.model.Projectile;
import io.github.skystrike.shared.weapons.WeaponBallistics;
import io.github.skystrike.shared.weapons.WeaponClass;
import io.github.skystrike.shared.weapons.WeaponId;
import java.util.List;

/**
 * Draws rounds in flight in {@link RenderLayers#PROJECTILES}.
 *
 * <p>A bullet is a few units across and crosses the screen in a quarter of a second, so drawing
 * it as a dot would be drawing nothing. What reads is the <b>streak</b>: a short line along the
 * round's own velocity, brightest at the nose and fading behind it. The streak length is scaled
 * by speed, so a sniper round is a long hard line and a sawed-off pellet is a stub — the weapon
 * is legible from the tracer alone.
 *
 * <p>The tracer is drawn along velocity rather than between snapshot positions on purpose:
 * velocity is current, and gravity bends the path, so a velocity-aligned streak curves with the
 * shot while a position-aligned one would lag a frame behind it.
 */
public final class ProjectileRenderer implements Disposable {

    /** Streak length as a fraction of one second of travel, before clamping. */
    private static final float TRAIL_SECONDS = 0.035f;
    private static final float TRAIL_MIN = 10f;
    private static final float TRAIL_MAX = 70f;

    private static final Color COLOR_SNIPER = new Color(0.65f, 0.90f, 1.00f, 1f);
    private static final Color COLOR_RIFLE = new Color(1.00f, 0.86f, 0.45f, 1f);
    private static final Color COLOR_SMG = new Color(1.00f, 0.74f, 0.35f, 1f);
    private static final Color COLOR_PISTOL = new Color(1.00f, 0.80f, 0.55f, 1f);
    private static final Color COLOR_SHOTGUN = new Color(1.00f, 0.62f, 0.30f, 1f);
    private static final Color COLOR_CORE = new Color(1.00f, 0.98f, 0.90f, 1f);

    private final ShapeRenderer shapes = new ShapeRenderer();
    private final Color tint = new Color();

    public ProjectileRenderer() {
    }

    /**
     * Draws every round the client currently knows about.
     *
     * <p>Culling already happened on the server: a snapshot only ever contains rounds this
     * client is allowed to see, so anything here is drawn.
     */
    public void render(GameCamera camera, List<Projectile> projectiles) {
        if (camera == null || projectiles == null || projectiles.isEmpty()) {
            return;
        }

        shapes.setProjectionMatrix(camera.combined());
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);

        // 1. Streaks, drawn additively so crossing fire brightens where it overlaps.
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (Projectile projectile : projectiles) {
            drawTrail(projectile);
        }
        shapes.end();

        // 2. Hot nose on top of the streak.
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Projectile projectile : projectiles) {
            shapes.setColor(COLOR_CORE);
            shapes.circle(projectile.x, projectile.y, CombatConfig.PROJECTILE_RADIUS + 0.6f, 6);
        }
        shapes.end();

        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    private void drawTrail(Projectile projectile) {
        float speed = projectile.speed();
        if (speed <= 1f) {
            return;
        }

        float length = clamp(speed * TRAIL_SECONDS, TRAIL_MIN, TRAIL_MAX);
        float dirX = projectile.vx / speed;
        float dirY = projectile.vy / speed;
        float tailX = projectile.x - dirX * length;
        float tailY = projectile.y - dirY * length;
        float midX = projectile.x - dirX * length * 0.45f;
        float midY = projectile.y - dirY * length * 0.45f;

        Color base = colorFor(projectile.weapon());

        // Two segments: a faint tail and a bright head. Cheaper than a gradient, reads the same.
        tint.set(base.r, base.g, base.b, 0.0f);
        shapes.setColor(tint);
        shapes.line(tailX, tailY, midX, midY);

        tint.set(base.r, base.g, base.b, 0.85f);
        shapes.setColor(tint);
        shapes.line(midX, midY, projectile.x, projectile.y);
    }

    private static Color colorFor(WeaponId weaponId) {
        if (weaponId == null) {
            return COLOR_RIFLE;
        }
        WeaponClass weaponClass = WeaponBallistics.of(weaponId).weaponClass();
        return switch (weaponClass) {
            case SNIPER -> COLOR_SNIPER;
            case SMG -> COLOR_SMG;
            case PISTOL -> COLOR_PISTOL;
            case SHOTGUN -> COLOR_SHOTGUN;
            case RIFLE -> COLOR_RIFLE;
        };
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    @Override
    public void dispose() {
        shapes.dispose();
    }
}
