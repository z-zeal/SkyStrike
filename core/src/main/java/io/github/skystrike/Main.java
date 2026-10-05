package io.github.skystrike;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Screen;
import io.github.skystrike.screens.GameScreen;
import io.github.skystrike.shared.config.NetConfig;

/**
 * Application root.
 *
 * <p>Phase 0 goes straight to the match screen. The loading and menu screens, and the platform
 * service injection they need, arrive in Phases 10 and 11.
 */
public final class Main extends Game {

    private static final String HOST_PROPERTY = "skystrike.host";
    private static final String TCP_PORT_PROPERTY = "skystrike.tcpPort";
    private static final String UDP_PORT_PROPERTY = "skystrike.udpPort";
    private static final String NAME_PROPERTY = "skystrike.name";

    @Override
    public void create() {
        setScreen(new GameScreen(
            property(NAME_PROPERTY, "Player"),
            property(HOST_PROPERTY, NetConfig.DEFAULT_HOST),
            intProperty(TCP_PORT_PROPERTY, NetConfig.DEFAULT_TCP_PORT),
            intProperty(UDP_PORT_PROPERTY, NetConfig.DEFAULT_UDP_PORT)));
    }

    /**
     * {@code Game.dispose()} only hides the active screen, so the screen's own resources are
     * released here explicitly.
     */
    @Override
    public void dispose() {
        Screen current = getScreen();
        super.dispose();
        if (current != null) {
            current.dispose();
        }
    }

    private static String property(String key, String fallback) {
        String value = System.getProperty(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int intProperty(String key, int fallback) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException notANumber) {
            return fallback;
        }
    }
}
