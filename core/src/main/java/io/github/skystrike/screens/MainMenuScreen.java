package io.github.skystrike.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The front door to a match. The connection form uses Scene2D fields and buttons so mouse,
 * keyboard and assistive focus all follow the same controls rather than a second hit-test path.
 */
public final class MainMenuScreen extends de.eskalon.commons.screen.ManagedScreenAdapter {

    private static final float FORM_WIDTH = 420f;
    private static final float BUTTON_WIDTH = 240f;
    private static final String[] ACTIONS = {"Play", "Loadout", "Settings", "Quit"};

    /** A validated connection request produced only by Play or Loadout. */
    public record Connection(String playerName, String host, int tcpPort, int udpPort) {
    }

    /** Menu action plus the current raw field text, so opening Settings cannot lose edits. */
    public record Request(
        String action,
        String playerName,
        String host,
        String tcpText,
        String udpText,
        Connection connection
    ) {
    }

    private final Consumer<Request> action;
    private final Skin skin;
    private final Stage stage;
    private final InputMultiplexer input = new InputMultiplexer();
    private final List<Actor> keyboardOrder = new ArrayList<>();

    private final TextField playerNameField;
    private final TextField hostField;
    private final TextField tcpField;
    private final TextField udpField;
    private final TextButton[] actionButtons = new TextButton[ACTIONS.length];
    private final Label status;

    private boolean disposed;

    public MainMenuScreen(
            Consumer<Request> action,
            String playerName,
            String host,
            String tcpText,
            String udpText) {
        if (action == null) {
            throw new IllegalArgumentException("action is required");
        }
        this.action = action;
        this.skin = new Skin(Gdx.files.internal("ui/uiskin.json"));
        this.stage = new Stage(new ScreenViewport());

        playerNameField = field(safe(playerName, "Player"), "Player name");
        hostField = field(safe(host, "127.0.0.1"), "Host or IP address");
        tcpField = field(safe(tcpText, "54555"), "TCP port");
        udpField = field(safe(udpText, "54556"), "UDP port");
        status = new Label("Edit the connection details, then choose Play.", skin);
        status.setWrap(true);

        buildLayout();
        input.addProcessor(new MenuKeyboard());
        input.addProcessor(stage);
        addInputProcessor(input);
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        stage.setKeyboardFocus(playerNameField);
    }

    private TextField field(String value, String message) {
        TextField field = new TextField(value, skin);
        field.setMessageText(message);
        field.setSelectAllOnFocus(false);
        keyboardOrder.add(field);
        return field;
    }

    private void buildLayout() {
        Table root = new Table();
        root.setFillParent(true);
        root.top().left().pad(42f);
        stage.addActor(root);

        Label title = new Label("SKYSTRIKE", skin);
        root.add(title).left();
        root.row();
        root.add(new Label("CONNECTION", skin)).left().padTop(8f).padBottom(14f);
        root.row();

        Table form = new Table();
        addFormRow(form, "Name", playerNameField);
        addFormRow(form, "Host", hostField);
        addFormRow(form, "TCP", tcpField);
        addFormRow(form, "UDP", udpField);
        root.add(form).left();
        root.row();

        Table menu = new Table();
        menu.defaults().width(BUTTON_WIDTH).height(38f).padBottom(7f);
        for (int i = 0; i < ACTIONS.length; i++) {
            String actionName = ACTIONS[i];
            TextButton button = new TextButton(actionName, skin);
            button.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    activate(actionName);
                }
            });
            actionButtons[i] = button;
            keyboardOrder.add(button);
            menu.add(button).left();
            menu.row();
        }
        root.add(menu).left().padTop(20f);
        root.row();
        root.add(status).left().width(620f).padTop(8f);
    }

    private void addFormRow(Table table, String label, TextField field) {
        table.add(new Label(label, skin)).left().width(92f).padBottom(7f);
        table.add(field).width(FORM_WIDTH).height(34f).padBottom(7f);
        table.row();
    }

    private void activate(String selected) {
        Connection connection = null;
        if ("Play".equals(selected) || "Loadout".equals(selected)) {
            connection = validatedConnection();
            if (connection == null) {
                return;
            }
        }
        action.accept(new Request(
            selected,
            playerNameField.getText(),
            hostField.getText(),
            tcpField.getText(),
            udpField.getText(),
            connection));
    }

    private Connection validatedConnection() {
        String playerName = playerNameField.getText();
        if (playerName == null || playerName.isBlank()) {
            showError("Name is required.", playerNameField);
            return null;
        }
        String host = hostField.getText();
        if (host == null || host.isBlank()) {
            showError("Host is required.", hostField);
            return null;
        }
        int tcp = port(tcpField, "TCP");
        if (tcp < 0) {
            return null;
        }
        int udp = port(udpField, "UDP");
        if (udp < 0) {
            return null;
        }
        return new Connection(playerName.trim(), host.trim(), tcp, udp);
    }

    private int port(TextField field, String label) {
        try {
            int parsed = Integer.parseInt(field.getText().trim());
            if (parsed >= 1 && parsed <= 65535) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // The status label and field focus give the player an actionable correction.
        }
        showError(label + " port must be between 1 and 65535.", field);
        return -1;
    }

    private void showError(String message, Actor focus) {
        status.setText(message);
        stage.setKeyboardFocus(focus);
    }

    private void moveFocus(int direction) {
        if (keyboardOrder.isEmpty()) {
            return;
        }
        int index = keyboardOrder.indexOf(stage.getKeyboardFocus());
        if (index < 0) {
            index = direction < 0 ? 0 : -1;
        }
        int next = Math.floorMod(index + direction, keyboardOrder.size());
        stage.setKeyboardFocus(keyboardOrder.get(next));
    }

    private void activateFocused() {
        Actor focused = stage.getKeyboardFocus();
        for (int i = 0; i < actionButtons.length; i++) {
            if (focused == actionButtons[i]) {
                activate(ACTIONS[i]);
                return;
            }
        }
        if (focused instanceof TextField) {
            activate("Play");
        }
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(.025f, .035f, .06f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
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
        stage.dispose();
        skin.dispose();
    }

    /** Keeps the previous keyboard-first flow while delegating actual editing to Scene2D fields. */
    private final class MenuKeyboard extends InputAdapter {
        @Override
        public boolean keyDown(int keycode) {
            if (keycode == Input.Keys.TAB) {
                boolean reverse = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                    || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
                moveFocus(reverse ? -1 : 1);
                return true;
            }
            if (keycode == Input.Keys.UP && !(stage.getKeyboardFocus() instanceof TextField)) {
                moveFocus(-1);
                return true;
            }
            if (keycode == Input.Keys.DOWN && !(stage.getKeyboardFocus() instanceof TextField)) {
                moveFocus(1);
                return true;
            }
            if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
                activateFocused();
                return true;
            }
            if (keycode == Input.Keys.ESCAPE) {
                stage.setKeyboardFocus(null);
                status.setText("Connection form ready.");
                return true;
            }
            return false;
        }
    }
}
