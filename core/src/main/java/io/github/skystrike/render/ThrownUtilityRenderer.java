package io.github.skystrike.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import io.github.skystrike.shared.config.UtilityConfig;
import io.github.skystrike.shared.model.ThrownUtility;
import io.github.skystrike.shared.utility.UtilityId;
import java.util.List;

/** Draws authoritative live throwables as compact, colour-coded world-space markers. */
public final class ThrownUtilityRenderer implements Disposable {

    private static final Color FRAG = new Color(0.93f, 0.76f, 0.23f, 1f);
    private static final Color SMOKE = new Color(0.67f, 0.71f, 0.74f, 1f);
    private static final Color STUN = new Color(0.52f, 0.83f, 1.00f, 1f);
    private static final Color FIRE = new Color(1.00f, 0.36f, 0.10f, 1f);
    private static final Color TOXIC = new Color(0.45f, 0.90f, 0.30f, 1f);
    private static final Color FLASH = new Color(1.00f, 0.98f, 0.85f, 1f);
    private static final Color CLAYMORE = new Color(0.55f, 0.72f, 0.40f, 1f);

    private final ShapeRenderer shapes = new ShapeRenderer();

    public void render(GameCamera camera, List<ThrownUtility> utilities) {
        if (camera == null || utilities == null || utilities.isEmpty()) {
            return;
        }
        shapes.setProjectionMatrix(camera.combined());
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (ThrownUtility utility : utilities) {
            shapes.setColor(colorFor(utility.utility()));
            shapes.circle(utility.x, utility.y, UtilityConfig.THROWABLE_RADIUS + 1.5f, 10);
        }
        shapes.end();

        // Direction mark makes a placed claymore legible rather than a generic green dot.
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (ThrownUtility utility : utilities) {
            if (utility.utility() != UtilityId.CLAYMORE) {
                continue;
            }
            float radians = (float) Math.toRadians(utility.aimAngle);
            shapes.setColor(CLAYMORE);
            shapes.line(
                utility.x,
                utility.y,
                utility.x + (float) Math.cos(radians) * 12f,
                utility.y + (float) Math.sin(radians) * 12f);
        }
        shapes.end();
    }

    private static Color colorFor(UtilityId id) {
        if (id == null) {
            return FRAG;
        }
        return switch (id) {
            case FRAG, IMPACT -> FRAG;
            case SMOKE -> SMOKE;
            case STUN -> STUN;
            case MOLOTOV -> FIRE;
            case POISON_SMOKE -> TOXIC;
            case FLASHBANG -> FLASH;
            case CLAYMORE -> CLAYMORE;
        };
    }

    @Override
    public void dispose() {
        shapes.dispose();
    }
}
