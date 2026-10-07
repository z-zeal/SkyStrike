package io.github.skystrike.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.GL20;
import io.github.skystrike.input.InputRouter;
import io.github.skystrike.render.WeaponSprites;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.net.c2s.PacketLoadoutUpdate;
import io.github.skystrike.ui.hud.HudFrame;
import io.github.skystrike.ui.hud.HudStage;
import java.util.List;
import java.util.function.Consumer;

/**
 * A standalone full-screen home for the existing M4 picker. It owns a small preview loadout and
 * the same {@link WeaponSprites} catalogue the match renderer uses, rather than rebuilding a
 * second picker or substituting placeholder art.
 */
public final class LoadoutScreen extends de.eskalon.commons.screen.ManagedScreenAdapter {

    private final Runnable back;
    private final Consumer<PacketLoadoutUpdate> requestSink;
    private final InputRouter inputRouter = new InputRouter();
    private final InputMultiplexer input = new InputMultiplexer();
    private final WeaponSprites sprites = new WeaponSprites();
    private final HudStage hud;
    private final Player previewPlayer = new Player(0, "Preview", 0, 0f, 0f);

    private boolean pickerOpen;
    private boolean disposed;

    public LoadoutScreen(Runnable back, Consumer<PacketLoadoutUpdate> requestSink) {
        if (back == null || requestSink == null) {
            throw new IllegalArgumentException("back and requestSink are required");
        }
        this.back = back;
        this.requestSink = requestSink;
        this.hud = new HudStage(inputRouter, this::rememberRequest, sprites);
        this.hud.picker().setCloseRequest(() -> {
            pickerOpen = false;
            back.run();
        });
        input.addProcessor(inputRouter.multiplexer());
        addInputProcessor(input);
    }

    @Override
    public void show() {
        pickerOpen = true;
        hud.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(.025f, .035f, .06f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        hud.update(delta, pickerOpen, previewPlayer.loadout);
        hud.render(new HudFrame(
            previewPlayer,
            0f,
            false,
            0f,
            false,
            false,
            System.currentTimeMillis(),
            delta,
            false,
            List.of()));
    }

    private void rememberRequest(PacketLoadoutUpdate request) {
        if (request == null || request.isEmpty()) {
            return;
        }
        // The menu has no session by design. Main queues this exact server-validated packet and
        // transmits it only after a later connection has joined.
        requestSink.accept(new PacketLoadoutUpdate(
            request.primary,
            request.handgun,
            request.melee,
            request.utilityA,
            request.utilityB,
            request.gadgetQ,
            request.gadgetE));
    }

    @Override
    public void resize(int width, int height) {
        hud.resize(width, height);
    }

    @Override
    public void hide() {
        dispose();
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        inputRouter.clearFocus();
        hud.dispose();
        sprites.dispose();
    }
}
