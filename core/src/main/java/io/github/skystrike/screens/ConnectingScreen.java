package io.github.skystrike.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.skystrike.net.ClientSession;
import io.github.skystrike.net.ConnectionState;

/** Owns the pre-game connection lifetime and never reuses a stale ClientSession. */
public final class ConnectingScreen extends ScreenAdapter {
    private final ClientSession session;
    private final String host; private final int tcp; private final int udp;
    private final Runnable joined; private final Runnable back;
    private final SpriteBatch batch = new SpriteBatch(); private final BitmapFont font = new BitmapFont();
    public ConnectingScreen(ClientSession session, String host, int tcp, int udp, Runnable joined, Runnable back) {
        this.session=session; this.host=host; this.tcp=tcp; this.udp=udp; this.joined=joined; this.back=back;
    }
    @Override public void show() { session.connect(host, tcp, udp); Gdx.input.setInputProcessor(new com.badlogic.gdx.InputAdapter() {
        @Override public boolean keyDown(int key) { if (key == Input.Keys.ESCAPE) { session.disconnect(); back.run(); return true; } return true; }
    }); }
    @Override public void render(float delta) {
        session.update(delta);
        if (session.state() == ConnectionState.JOINED) { joined.run(); return; }
        Gdx.gl.glClearColor(.025f,.035f,.06f,1); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.begin(); font.draw(batch, "CONNECTING", 80, 420); font.draw(batch, session.statusLine(), 80, 360); font.draw(batch, "Escape: cancel", 80, 300); batch.end();
    }
    @Override public void dispose() { batch.dispose(); font.dispose(); }
}
