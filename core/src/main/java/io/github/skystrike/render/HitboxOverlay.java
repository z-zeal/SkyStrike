package io.github.skystrike.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import io.github.skystrike.shared.combat.HitZoneMath;
import io.github.skystrike.shared.config.CombatConfig;
import io.github.skystrike.shared.config.PlayerConfig;
import io.github.skystrike.shared.model.Player;
import java.util.List;

/**
 * {@code r_show_hitboxes} (build plan M3 §4, F9): draws the body, head and fuel-tank hit zones
 * every live player resolves damage against, using the exact same fractions as
 * {@link HitZoneMath} rather than a separately-tuned approximation.
 */
public final class HitboxOverlay implements Disposable {

    private static final Color BODY_COLOR = new Color(0.95f, 0.95f, 0.25f, 0.9f);
    private static final Color HEAD_COLOR = new Color(1.0f, 0.25f, 0.25f, 0.95f);
    private static final Color FUEL_TANK_COLOR = new Color(0.25f, 0.65f, 1.0f, 0.95f);

    private final ShapeRenderer shapes = new ShapeRenderer();

    public void render(GameCamera camera, List<Player> remotePlayers, Player localPlayer) {
        shapes.setProjectionMatrix(camera.combined());
        shapes.begin(ShapeRenderer.ShapeType.Line);
        if (remotePlayers != null) {
            for (Player p : remotePlayers) {
                if (p.alive) {
                    drawZones(p);
                }
            }
        }
        if (localPlayer != null && localPlayer.alive) {
            drawZones(localPlayer);
        }
        shapes.end();
    }

    private void drawZones(Player p) {
        float left = p.x - PlayerConfig.WIDTH / 2f;
        float height = p.currentHeight();

        shapes.setColor(BODY_COLOR);
        shapes.rect(left, p.y, PlayerConfig.WIDTH, height);

        float headBottom = HitZoneMath.headZoneBottom(p);
        shapes.setColor(HEAD_COLOR);
        shapes.rect(left, headBottom, PlayerConfig.WIDTH, (p.y + height) - headBottom);

        if (p.loadout != null && p.loadout.hasFuelTank()) {
            float strip = PlayerConfig.WIDTH * CombatConfig.FUEL_TANK_WIDTH_FRACTION;
            float rearEdge = p.isFacingRight() ? left : left + PlayerConfig.WIDTH;
            float tankLeft = p.isFacingRight() ? rearEdge - strip : rearEdge;
            float tankBottom = p.y + height * CombatConfig.FUEL_TANK_BOTTOM_FRACTION;
            float tankTop = p.y + height * CombatConfig.FUEL_TANK_TOP_FRACTION;
            shapes.setColor(FUEL_TANK_COLOR);
            shapes.rect(tankLeft, tankBottom, strip, tankTop - tankBottom);
        }
    }

    @Override
    public void dispose() {
        shapes.dispose();
    }
}
