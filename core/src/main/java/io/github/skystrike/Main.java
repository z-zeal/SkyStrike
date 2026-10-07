package io.github.skystrike;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import io.github.skystrike.input.KeyBindings;
import io.github.skystrike.net.ClientSession;
import io.github.skystrike.screens.ConnectingScreen;
import io.github.skystrike.screens.GameScreen;
import io.github.skystrike.screens.LoadoutScreen;
import io.github.skystrike.screens.MainMenuScreen;
import io.github.skystrike.screens.SettingsScreen;
import io.github.skystrike.settings.ClientPreferences;
import io.github.skystrike.shared.config.NetConfig;
import io.github.skystrike.shared.net.c2s.PacketLoadoutUpdate;
import io.github.skystrike.shared.settings.Settings;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Application root and M5 screen router. The project-provided screen manager owns active-screen
 * input registration and routes every normal transition; screens dispose themselves exactly when
 * they are hidden, except a game deliberately retained while its settings screen is open.
 */
public final class Main extends de.eskalon.commons.core.ManagedGame<
        de.eskalon.commons.screen.ManagedScreen,
        de.eskalon.commons.screen.transition.ScreenTransition> {

    private static final String HOST = "skystrike.host";
    private static final String TCP = "skystrike.tcpPort";
    private static final String UDP = "skystrike.udpPort";
    private static final String NAME = "skystrike.name";

    private final Settings settings = new Settings();
    private final ClientPreferences preferences = new ClientPreferences();
    /** One bindings instance is shared by settings, the console and every game screen. */
    private final KeyBindings bindings = new KeyBindings();

    private String menuName = property(NAME, "Player");
    private String menuHost = property(HOST, NetConfig.DEFAULT_HOST);
    private String menuTcp = property(TCP, Integer.toString(NetConfig.DEFAULT_TCP_PORT));
    private String menuUdp = property(UDP, Integer.toString(NetConfig.DEFAULT_UDP_PORT));
    private PacketLoadoutUpdate pendingLoadout = new PacketLoadoutUpdate();
    /** A joined match parked behind Settings; it must be released if the app exits there. */
    private GameScreen retainedGame;

    @Override
    public void create() {
        super.create();
        // Screens own their exact teardown on hide. This lets a GameScreen stay alive while the
        // settings screen is visible, but still makes disconnect -> menu dispose it once.
        getScreenManager().setAutoDispose(false, false);
        bindings.loadPreferences();
        preferences.loadSettings(settings);
        applySettings(settings);
        showMenu();
    }

    private void showMenu() {
        route(new MainMenuScreen(this::menuAction, menuName, menuHost, menuTcp, menuUdp));
    }

    private void menuAction(MainMenuScreen.Request request) {
        menuName = request.playerName();
        menuHost = request.host();
        menuTcp = request.tcpText();
        menuUdp = request.udpText();
        switch (request.action()) {
            case "Settings" -> showMenuSettings();
            case "Play" -> connectToGame(request.connection());
            case "Loadout" -> route(new LoadoutScreen(this::showMenu, this::rememberLoadout));
            case "Quit" -> Gdx.app.exit();
            default -> throw new IllegalArgumentException("unknown menu action: " + request.action());
        }
    }

    private void showMenuSettings() {
        route(new SettingsScreen(settings, bindings, this::applySettings, this::showMenu));
    }

    private void connectToGame(MainMenuScreen.Connection connection) {
        if (connection == null) {
            return;
        }
        Supplier<ClientSession> sessionFactory = () -> new ClientSession(connection.playerName());
        Consumer<ClientSession> joined = session -> startGame(session, connection);
        route(new ConnectingScreen(
            sessionFactory,
            connection.host(),
            connection.tcpPort(),
            connection.udpPort(),
            joined,
            this::showMenu));
    }

    private void startGame(ClientSession session, MainMenuScreen.Connection connection) {
        GameScreen game = new GameScreen(
            session,
            connection.host(),
            connection.tcpPort(),
            connection.udpPort(),
            bindings,
            this::showMenu,
            this::showGameSettings,
            settings);
        if (!pendingLoadout.isEmpty()) {
            game.requestLoadout(pendingLoadout);
            pendingLoadout = new PacketLoadoutUpdate();
        }
        route(game);
    }

    /** Opens settings without tearing a joined match down; returning re-shows the same screen. */
    private void showGameSettings(GameScreen game) {
        if (game == null) {
            return;
        }
        retainedGame = game;
        game.suspendForSettings();
        route(new SettingsScreen(settings, bindings, this::applySettings, () -> {
            GameScreen resume = retainedGame;
            retainedGame = null;
            if (resume != null) {
                route(resume);
            } else {
                showMenu();
            }
        }));
    }

    private void rememberLoadout(PacketLoadoutUpdate request) {
        if (request == null || request.isEmpty()) {
            return;
        }
        if (request.primary != PacketLoadoutUpdate.KEEP_CURRENT) pendingLoadout.primary = request.primary;
        if (request.handgun != PacketLoadoutUpdate.KEEP_CURRENT) pendingLoadout.handgun = request.handgun;
        if (request.melee != PacketLoadoutUpdate.KEEP_CURRENT) pendingLoadout.melee = request.melee;
        if (request.utilityA != PacketLoadoutUpdate.KEEP_CURRENT) pendingLoadout.utilityA = request.utilityA;
        if (request.utilityB != PacketLoadoutUpdate.KEEP_CURRENT) pendingLoadout.utilityB = request.utilityB;
        if (request.gadgetQ != PacketLoadoutUpdate.KEEP_CURRENT) pendingLoadout.gadgetQ = request.gadgetQ;
        if (request.gadgetE != PacketLoadoutUpdate.KEEP_CURRENT) pendingLoadout.gadgetE = request.gadgetE;
    }

    /** Saves validated values before applying the subset libGDX can change at runtime. */
    private void applySettings(Settings changed) {
        changed.validate();
        preferences.saveSettings(changed);
        if (Gdx.graphics == null) {
            return;
        }
        Gdx.graphics.setVSync(changed.vsync);
        if (changed.fullscreen) {
            Graphics.DisplayMode displayMode = Gdx.graphics.getDisplayMode();
            if (displayMode != null) {
                Gdx.graphics.setFullscreenMode(displayMode);
            }
        } else {
            Gdx.graphics.setWindowedMode(changed.width, changed.height);
        }
    }

    private void route(de.eskalon.commons.screen.ManagedScreen screen) {
        getScreenManager().pushScreen(screen, null);
    }

    private static String property(String key, String fallback) {
        String value = System.getProperty(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    @Override
    public void dispose() {
        // ManagedGame delegates to ScreenManager. A retained game is not active while Settings is
        // shown, so release that one explicit suspension as well.
        GameScreen suspended = retainedGame;
        retainedGame = null;
        super.dispose();
        if (suspended != null) {
            suspended.dispose();
        }
    }
}
