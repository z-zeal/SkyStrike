package io.github.skystrike;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import io.github.skystrike.net.ClientSession;
import io.github.skystrike.screens.ConnectingScreen;
import io.github.skystrike.screens.GameScreen;
import io.github.skystrike.screens.MainMenuScreen;
import io.github.skystrike.screens.SettingsScreen;
import io.github.skystrike.shared.config.NetConfig;
import io.github.skystrike.shared.settings.Settings;

/** Application root and the single owner of screen transitions. */
public final class Main extends Game {
    private static final String HOST = "skystrike.host";
    private static final String TCP = "skystrike.tcpPort";
    private static final String UDP = "skystrike.udpPort";
    private static final String NAME = "skystrike.name";

    private final Settings settings = new Settings();

    @Override public void create() { showMenu(); }

    private void showMenu() {
        setScreen(new MainMenuScreen(this::menuAction,
            property(NAME, "Player"), property(HOST, NetConfig.DEFAULT_HOST),
            intProperty(TCP, NetConfig.DEFAULT_TCP_PORT), intProperty(UDP, NetConfig.DEFAULT_UDP_PORT)));
    }

    private void menuAction(String action) {
        if ("Settings".equals(action)) {
            setScreen(new SettingsScreen(settings, this::showMenu));
        } else if ("Play".equals(action)) {
            connectToGame(false);
        } else if ("Loadout".equals(action)) {
            connectToGame(true);
        } else if ("Quit".equals(action)) {
            Gdx.app.exit();
        }
    }

    private void connectToGame(boolean openLoadout) {
        String name = property(NAME, "Player");
        String host = property(HOST, NetConfig.DEFAULT_HOST);
        int tcp = intProperty(TCP, NetConfig.DEFAULT_TCP_PORT);
        int udp = intProperty(UDP, NetConfig.DEFAULT_UDP_PORT);
        ClientSession session = new ClientSession(name);
        Runnable joined = () -> {
            GameScreen game = new GameScreen(session, host, tcp, udp);
            setScreen(game);
            if (openLoadout) game.openLoadout();
        };
        Runnable cancelled = this::showMenu;
        setScreen(new ConnectingScreen(session, host, tcp, udp, joined, cancelled));
    }

    @Override public void dispose() {
        // setScreen() disposes the old screen through Game; dispose the current one exactly once.
        super.dispose();
    }

    private static String property(String key, String fallback) {
        String value = System.getProperty(key);
        return value == null || value.isBlank() ? fallback : value;
    }
    private static int intProperty(String key, int fallback) {
        try { return Integer.parseInt(property(key, Integer.toString(fallback)).trim()); }
        catch (NumberFormatException ignored) { return fallback; }
    }
}
