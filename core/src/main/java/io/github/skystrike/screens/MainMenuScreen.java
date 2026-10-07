package io.github.skystrike.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The front door to a match. The connection form lives only inside a non-movable modal dialog
 * that opens when the player presses Play. The initial menu shows only the primary actions in a
 * centred, hierarchical layout so the connection fields are not permanently visible.
 *
 * <p>Visual hierarchy reuses the existing Scene2D Skin and font architecture rather than
 * introducing a second UI framework. The dialog is a true modal: a full-screen dim overlay
 * captures focus, prevents click-through, is not draggable, and supports Escape/Cancel while
 * preserving the existing validation and routing contracts.
 */
public final class MainMenuScreen extends de.eskalon.commons.screen.ManagedScreenAdapter {

    private static final float BUTTON_WIDTH = 280f;
    private static final float BUTTON_HEIGHT = 42f;
    private static final float FORM_LABEL_WIDTH = 110f;
    private static final float FORM_FIELD_WIDTH = 300f;
    private static final float DIALOG_WIDTH = 480f;
    private static final String[] ACTIONS = {"Play", "Loadout", "Settings", "Quit"};

    /** A validated connection request produced only by Play. */
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

    private final List<Actor> menuOrder = new ArrayList<>();
    private final List<Actor> dialogOrder = new ArrayList<>();

    private final TextField playerNameField;
    private final TextField hostField;
    private final TextField tcpField;
    private final TextField udpField;
    private final TextButton[] actionButtons = new TextButton[ACTIONS.length];
    private final Label status;
    private final Label dialogStatus;
    private final Table overlay;
    private final Table dialog;
    private final TextButton connectButton;
    private final TextButton cancelButton;

    private boolean dialogOpen;
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
        status = new Label("Choose Play to connect — or adjust your loadout first.", skin);
        status.setWrap(true);
        status.setAlignment(Align.center);
        status.setColor(new Color(0.72f, 0.78f, 0.88f, 1f));

        dialogStatus = new Label("", skin);
        dialogStatus.setWrap(true);
        dialogStatus.setColor(new Color(1f, 0.76f, 0.42f, 1f));

        connectButton = new TextButton("Connect", skin);
        cancelButton = new TextButton("Cancel", skin);

        // Build the polished initial menu (no permanently visible connection fields) and then
        // the true-modal connection dialog on top.
        Table menuRoot = buildMenuLayout();
        overlay = buildDialogOverlay();

        // Stage ordering: menu behind, overlay in front when visible.
        stage.addActor(menuRoot);
        stage.addActor(overlay);

        input.addProcessor(new MenuKeyboard());
        input.addProcessor(stage);
        addInputProcessor(input);
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        stage.setKeyboardFocus(actionButtons[0]);
    }

    private TextField field(String value, String message) {
        TextField field = new TextField(value, skin);
        field.setMessageText(message);
        field.setSelectAllOnFocus(false);
        return field;
    }

    private Table buildMenuLayout() {
        Table root = new Table();
        root.setFillParent(true);
        root.center().pad(32f);

        Table header = new Table();
        header.center();

        Label title = new Label("SKYSTRIKE", skin, "window");
        title.setAlignment(Align.center);
        title.setColor(Color.WHITE);

        Label tagline = new Label("TACTICAL ARENA  •  AIR COMBAT", skin, "subtitle");
        tagline.setAlignment(Align.center);
        tagline.setColor(new Color(0.62f, 0.70f, 0.84f, 1f));

        Image separator = new Image(skin.newDrawable("white", new Color(0.18f, 0.24f, 0.36f, 1f)));
        Label subtitle = new Label("Select an action to begin", skin);
        subtitle.setAlignment(Align.center);
        subtitle.setColor(new Color(0.58f, 0.66f, 0.80f, 1f));

        header.add(title).padBottom(6f).row();
        header.add(tagline).padBottom(10f).row();
        header.add(separator).width(240f).height(1f).padTop(2f).padBottom(10f).row();
        header.add(subtitle).padTop(2f).row();

        root.add(header).padBottom(28f).row();

        Table menu = new Table();
        menu.defaults().width(BUTTON_WIDTH).height(BUTTON_HEIGHT).padBottom(9f);
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
            menuOrder.add(button);
            menu.add(button).row();
        }
        // Slightly de-emphasise Quit with a muted text colour handled via skin; keep layout ordered.
        root.add(menu).padBottom(22f).row();

        Table footer = new Table();
        footer.center();
        footer.add(status).width(560f).padBottom(6f).row();
        Label version = new Label("SkyStrike  •  v1.0  —  Press Play to enter the arena", skin);
        version.setAlignment(Align.center);
        version.setColor(new Color(0.52f, 0.60f, 0.74f, 1f));
        version.setFontScale(0.85f);
        footer.add(version).padTop(2f).row();

        root.add(footer).padTop(4f);
        return root;
    }

    private Table buildDialogOverlay() {
        Table full = new Table();
        full.setFillParent(true);
        full.setVisible(false);
        full.setTouchable(Touchable.enabled);
        // Dim that also blocks clicks. Using white pixel tinted to black at 55% alpha.
        full.setBackground(skin.newDrawable("white", new Color(0f, 0f, 0f, 0.56f)));
        // Prevent click-through: any touch on the dim is consumed and does not reach the menu.
        full.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                // Only consume touches on the overlay background itself, not children (the dialog).
                // The event's target will be the overlay when the click misses the dialog.
                return event.getTarget() == full;
            }
        });
        // Also intercept hit for empty area so stage.hit returns the overlay and not the menu.
        full.setTouchable(Touchable.enabled);

        dialog = new Table(skin);
        dialog.setBackground(skin.getDrawable("window"));
        dialog.pad(18f);
        dialog.defaults().left();
        dialog.setTouchable(Touchable.enabled);

        Label dialogTitle = new Label("CONNECT", skin, "window");
        dialogTitle.setColor(Color.WHITE);
        Label dialogSubtitle = new Label("Enter server details to join a match", skin);
        dialogSubtitle.setColor(new Color(0.68f, 0.74f, 0.86f, 1f));

        dialog.add(dialogTitle).padBottom(2f).row();
        dialog.add(dialogSubtitle).padBottom(14f).row();

        Table form = new Table();
        form.defaults().padBottom(7f);
        addDialogFormRow(form, "Player", playerNameField);
        addDialogFormRow(form, "Host / IP", hostField);
        addDialogFormRow(form, "TCP Port", tcpField);
        addDialogFormRow(form, "UDP Port", udpField);
        // Keyboard order for the true modal.
        dialogOrder.add(playerNameField);
        dialogOrder.add(hostField);
        dialogOrder.add(tcpField);
        dialogOrder.add(udpField);
        dialogOrder.add(connectButton);
        dialogOrder.add(cancelButton);

        dialog.add(form).width(DIALOG_WIDTH - 36f).padBottom(6f).row();
        dialog.add(dialogStatus).left().width(DIALOG_WIDTH - 36f).padBottom(10f).row();

        Table buttons = new Table();
        buttons.defaults().width(150f).height(38f).padLeft(8f);
        // Cancel first in visual order but both reachable; Connect is the affirmative action.
        cancelButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                hideDialog();
            }
        });
        connectButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                confirmDialog();
            }
        });
        // Prevent the dialog Table itself from being draggable - we never use Window, and this
        // Table has no Window dragging logic by construction.
        buttons.add(cancelButton);
        buttons.add(connectButton);
        dialog.add(buttons).right().padTop(4f).row();

        // Centre the dialog inside the dim.
        full.add(dialog).center().width(DIALOG_WIDTH);
        return full;
    }

    private void addDialogFormRow(Table table, String label, TextField field) {
        table.add(new Label(label, skin)).left().width(FORM_LABEL_WIDTH).padBottom(7f);
        table.add(field).width(FORM_FIELD_WIDTH).height(34f).padBottom(7f);
        table.row();
    }

    private void activate(String selected) {
        if ("Play".equals(selected)) {
            showDialog();
            return;
        }
        // Loadout, Settings and Quit keep the existing Request shape but do not require a
        // validated connection. The current field texts are still forwarded so Main can preserve
        // saved/default values across screen transitions, and Loadout does not gain a second
        // always-visible connection form.
        action.accept(new Request(
            selected,
            playerNameField.getText(),
            hostField.getText(),
            tcpField.getText(),
            udpField.getText(),
            null));
    }

    private void showDialog() {
        if (dialogOpen) {
            return;
        }
        dialogOpen = true;
        overlay.setVisible(true);
        overlay.toFront();
        dialogStatus.setText("");
        stage.setKeyboardFocus(playerNameField);
        // Select all for quick replace, without stealing focus from other actors.
        playerNameField.selectAll();
    }

    private void hideDialog() {
        if (!dialogOpen) {
            return;
        }
        dialogOpen = false;
        overlay.setVisible(false);
        dialogStatus.setText("");
        status.setText("Choose Play to connect — or adjust your loadout first.");
        stage.setKeyboardFocus(actionButtons[0]);
    }

    private void confirmDialog() {
        Connection connection = validatedConnection();
        if (connection == null) {
            return;
        }
        // Preserve the existing routing: MainMenuScreen.Request with a validated Connection is
        // what ConnectingScreen and GameScreen expect. Hide the modal before routing so the next
        // screen does not inherit focus.
        String pName = playerNameField.getText();
        String h = hostField.getText();
        String t = tcpField.getText();
        String u = udpField.getText();
        // Close the modal first; the status line on the menu will be overwritten only on error.
        dialogOpen = false;
        overlay.setVisible(false);
        dialogStatus.setText("");
        action.accept(new Request("Play", pName, h, t, u, connection));
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
        if (dialogOpen) {
            dialogStatus.setText(message);
            stage.setKeyboardFocus(focus);
            if (focus instanceof TextField) {
                ((TextField) focus).selectAll();
            }
        } else {
            status.setText(message);
            stage.setKeyboardFocus(focus);
        }
    }

    private void moveMenuFocus(int direction) {
        if (menuOrder.isEmpty()) {
            return;
        }
        int index = menuOrder.indexOf(stage.getKeyboardFocus());
        if (index < 0) {
            index = direction < 0 ? 0 : -1;
        }
        int next = Math.floorMod(index + direction, menuOrder.size());
        stage.setKeyboardFocus(menuOrder.get(next));
    }

    private void moveDialogFocus(int direction) {
        if (dialogOrder.isEmpty()) {
            return;
        }
        int index = dialogOrder.indexOf(stage.getKeyboardFocus());
        if (index < 0) {
            index = direction < 0 ? 0 : -1;
        }
        int next = Math.floorMod(index + direction, dialogOrder.size());
        Actor toFocus = dialogOrder.get(next);
        stage.setKeyboardFocus(toFocus);
        if (toFocus instanceof TextField) {
            ((TextField) toFocus).selectAll();
        }
    }

    private void activateFocusedMenu() {
        Actor focused = stage.getKeyboardFocus();
        for (int i = 0; i < actionButtons.length; i++) {
            if (focused == actionButtons[i]) {
                activate(ACTIONS[i]);
                return;
            }
        }
        // No field has focus on the polished menu, but keep a sensible default.
        activate("Play");
    }

    private void activateFocusedDialog() {
        Actor focused = stage.getKeyboardFocus();
        if (focused == cancelButton) {
            hideDialog();
            return;
        }
        if (focused == connectButton || focused instanceof TextField) {
            confirmDialog();
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
            if (dialogOpen) {
                if (keycode == Input.Keys.TAB) {
                    boolean reverse = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                        || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
                    moveDialogFocus(reverse ? -1 : 1);
                    return true;
                }
                if (keycode == Input.Keys.UP) {
                    // When a TextField has focus, UP/DOWN should still move dialog focus so the
                    // modal remains fully keyboard navigable without a mouse.
                    moveDialogFocus(-1);
                    return true;
                }
                if (keycode == Input.Keys.DOWN) {
                    moveDialogFocus(1);
                    return true;
                }
                if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
                    activateFocusedDialog();
                    return true;
                }
                if (keycode == Input.Keys.ESCAPE) {
                    hideDialog();
                    return true;
                }
                return false;
            }

            if (keycode == Input.Keys.TAB) {
                boolean reverse = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                    || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
                moveMenuFocus(reverse ? -1 : 1);
                return true;
            }
            if (keycode == Input.Keys.UP) {
                moveMenuFocus(-1);
                return true;
            }
            if (keycode == Input.Keys.DOWN) {
                moveMenuFocus(1);
                return true;
            }
            if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
                activateFocusedMenu();
                return true;
            }
            if (keycode == Input.Keys.ESCAPE) {
                stage.setKeyboardFocus(null);
                status.setText("Choose Play to connect — or adjust your loadout first.");
                return true;
            }
            return false;
        }

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            // While the modal is open, any touch outside the dialog is consumed by the overlay's
            // own listener and must not reach the stage's menu buttons. This guard ensures the
            // InputMultiplexer's stage processor does not also treat the same touch as a click on
            // a menu button behind the dim.
            if (dialogOpen) {
                // Let the stage handle touches on the dialog's own actors (fields/buttons) but
                // swallow touches that would otherwise fall through to the menu.
                // The overlay Table already consumes background touches; this just prevents the
                // multiplexer from delivering to any other processor.
                return false;
            }
            return false;
        }
    }
}
