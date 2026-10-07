package io.github.skystrike.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.skystrike.net.ClientSession;
import io.github.skystrike.net.ConnectionState;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Owns one pre-game connection attempt at a time. Retrying always creates a fresh session, so a
 * rejected or interrupted transport can never leak state into the next attempt.
 */
public final class ConnectingScreen extends de.eskalon.commons.screen.ManagedScreenAdapter {

    private final Supplier<ClientSession> sessionFactory;
    private final String host;
    private final int tcp;
    private final int udp;
    private final Consumer<ClientSession> joined;
    private final Runnable back;
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();
    private final InputAdapter input = new ConnectingInput();

    private ClientSession session;
    private boolean transferred;
    private boolean disposed;

    public ConnectingScreen(
            Supplier<ClientSession> sessionFactory,
            String host,
            int tcp,
            int udp,
            Consumer<ClientSession> joined,
            Runnable back) {
        if (sessionFactory == null || host == null || joined == null || back == null) {
            throw new IllegalArgumentException("connection collaborators are required");
        }
        this.sessionFactory = sessionFactory;
        this.host = host;
        this.tcp = tcp;
        this.udp = udp;
        this.joined = joined;
        this.back = back;
        addInputProcessor(input);
    }

    @Override
    public void show() {
        if (session == null) {
            startAttempt();
        }
    }

    private void startAttempt() {
        if (session != null) {
            session.disconnect();
        }
        session = sessionFactory.get();
        if (session == null) {
            throw new IllegalStateException("session factory returned null");
        }
        transferred = false;
        session.connect(host, tcp, udp);
    }

    private void cancel() {
        if (session != null) {
            session.disconnect();
        }
        back.run();
    }

    @Override
    public void render(float delta) {
        session.update(delta);
        if (session.state() == ConnectionState.JOINED) {
            transferred = true;
            joined.accept(session);
            return;
        }

        Gdx.gl.glClearColor(.025f, .035f, .06f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.begin();
        font.draw(batch, "CONNECTING", 80, 420);
        String rejected = session.joinRejectReason();
        if (rejected != null && !rejected.isBlank()) {
            font.draw(batch, "Join rejected: " + rejected, 80, 360);
            font.draw(batch, "R or click Retry. Escape cancels.", 80, 300);
        } else {
            font.draw(batch, session.statusLine(), 80, 360);
            font.draw(batch, "Escape: cancel   R or click: retry", 80, 300);
        }
        batch.end();
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
        if (!transferred && session != null) {
            session.disconnect();
        }
        batch.dispose();
        font.dispose();
    }

    private final class ConnectingInput extends InputAdapter {
        @Override
        public boolean keyDown(int keycode) {
            if (keycode == Input.Keys.ESCAPE) {
                cancel();
            } else if (keycode == Input.Keys.R) {
                startAttempt();
            }
            return true;
        }

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            float y = Gdx.graphics.getHeight() - screenY;
            if (y >= 270f && y <= 325f) {
                startAttempt();
            } else if (y >= 215f && y < 270f) {
                cancel();
            } else {
                startAttempt();
            }
            return true;
        }
    }
}
