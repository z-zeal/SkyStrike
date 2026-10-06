package io.github.skystrike.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import io.github.skystrike.shared.config.WorldConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.model.ThrownUtility;
import io.github.skystrike.shared.utility.DetonationMode;
import io.github.skystrike.shared.utility.ThrowablePhysics;
import io.github.skystrike.shared.utility.UtilityDefinition;
import io.github.skystrike.shared.utility.UtilityId;
import io.github.skystrike.shared.utility.UtilityRegistry;

/**
 * Predictive throwable arc rendered with the exact shared {@link ThrowablePhysics} integrator.
 *
 * <p>The preview deliberately steps a real {@link ThrownUtility} at the fixed server tick. It
 * never approximates the parabola, so thin terrain, substeps, bounces and the settle rule agree
 * with the authoritative flight path instead of teaching the player a lie.
 */
public final class TrajectoryRenderer implements Disposable {

    private static final int MAX_PREVIEW_STEPS = 180;
    private static final Color ARC = new Color(0.92f, 0.95f, 1f, 0.62f);

    private final ShapeRenderer shapes = new ShapeRenderer();
    private final ThrownUtility preview = new ThrownUtility();

    public void render(GameCamera camera, Player player, ArenaMap arena) {
        if (camera == null || player == null || arena == null || player.loadout == null) {
            return;
        }
        UtilityId id = player.loadout.activeUtilityId();
        if (id == null) {
            return;
        }
        UtilityDefinition definition = UtilityRegistry.of(id);
        if (definition.detonation() == DetonationMode.PROXIMITY) {
            return; // Placed claymores have no flight arc.
        }

        initialise(player, definition);
        shapes.setProjectionMatrix(camera.combined());
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(ARC);

        float previousX = preview.x;
        float previousY = preview.y;
        for (int step = 0; step < MAX_PREVIEW_STEPS; step++) {
            ThrowablePhysics.Contact contact = ThrowablePhysics.stepInPlace(preview, WorldConfig.TICK_SECONDS, arena);
            shapes.line(previousX, previousY, preview.x, preview.y);
            previousX = preview.x;
            previousY = preview.y;

            if ((definition.detonation() == DetonationMode.CONTACT && contact != ThrowablePhysics.Contact.NONE)
                || (definition.detonation() == DetonationMode.FUSE && preview.fuseRemaining <= 0f)
                || preview.resting) {
                break;
            }
        }
        shapes.end();
    }

    private void initialise(Player player, UtilityDefinition definition) {
        preview.id = 0;
        preview.ownerId = player.id;
        preview.teamIndex = player.teamIndex;
        preview.utilityId = definition.id().ordinal();
        preview.x = ThrowablePhysics.muzzleX(player.eyeX(), player.aimAngle);
        preview.y = ThrowablePhysics.muzzleY(player.eyeY(), player.aimAngle);
        preview.prevX = preview.x;
        preview.prevY = preview.y;
        preview.vx = ThrowablePhysics.throwVelocityX(definition.throwForce(), player.aimAngle);
        preview.vy = ThrowablePhysics.throwVelocityY(definition.throwForce(), player.aimAngle);
        preview.aimAngle = player.aimAngle;
        preview.age = 0f;
        preview.fuseRemaining = definition.fuseSeconds();
        preview.resting = false;
        preview.bounces = 0;
        preview.contactNormalX = 0f;
        preview.contactNormalY = 0f;
    }

    @Override
    public void dispose() {
        shapes.dispose();
    }
}
