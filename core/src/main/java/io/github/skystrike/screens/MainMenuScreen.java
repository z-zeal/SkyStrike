package io.github.skystrike.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import java.util.function.Consumer;

/**
 * The front door to a match. The connection form is intentionally small and keyboard-first, but
 * every displayed field is editable by clicking it or cycling with Tab.
 */
public final class MainMenuScreen extends de.eskalon.commons.screen.ManagedScreenAdapter {

    private static final float LEFT = 80f;
    private static final float FIELD_WIDTH = 420f;
    private static final float FIELD_HEIGHT = 27f;
    private static final float ITEM_GAP = 34f;
    private static final String[] ITEMS = {"Play", "Loadout", "Settings", "Quit"};

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

    private enum Field {
        NONE,
        NAME,
        HOST,
        TCP,
        UDP
    }

    private final Consumer<Request> action;
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();
    private final InputAdapter input = new MenuInput();

    private String playerName;
    private String host;
    private String tcpText;
    private String udpText;
    private String message = "Click a field to edit. Tab moves between fields.";
    private Field editing = Field.NONE;
    private int selectedItem;
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
        this.playerName = safe(playerName, "Player");
        this.host = safe(host, "127.0.0.1");
        this.tcpText = safe(tcpText, "54555");
        this.udpText = safe(udpText, "54556");
        addInputProcessor(input);
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(.025f, .035f, .06f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        float height = Gdx.graphics.getHeight();
        float formTop = height - 118f;

        batch.begin();
        font.draw(batch, "SKYSTRIKE", LEFT, height - 55f);
        font.draw(batch, "CONNECTION", LEFT, height - 82f);
        drawField("Name", playerName, Field.NAME, formTop);
        drawField("Host", host, Field.HOST, formTop - 38f);
        drawField("TCP", tcpText, Field.TCP, formTop - 76f);
        drawField("UDP", udpText, Field.UDP, formTop - 114f);

        float itemTop = formTop - 174f;
        for (int i = 0; i < ITEMS.length; i++) {
            String prefix = i == selectedItem && editing == Field.NONE ? "> " : "  ";
            font.draw(batch, prefix + ITEMS[i], LEFT + 20f, itemTop - i * ITEM_GAP);
        }
        font.draw(batch, message, LEFT, Math.max(32f, itemTop - ITEMS.length * ITEM_GAP - 14f));
        batch.end();
    }

    private void drawField(String label, String value, Field field, float baseline) {
        String prefix = editing == field ? "> " : "  ";
        String suffix = editing == field ? "_" : "";
        font.draw(batch, prefix + label + ": [" + value + "]" + suffix, LEFT, baseline);
    }

    private void activateSelected() {
        activate(ITEMS[selectedItem]);
    }

    private void activate(String selected) {
        Connection connection = null;
        if ("Play".equals(selected) || "Loadout".equals(selected)) {
            connection = validatedConnection();
            if (connection == null) {
                return;
            }
        }
        action.accept(new Request(selected, playerName, host, tcpText, udpText, connection));
    }

    private Connection validatedConnection() {
        if (playerName.isBlank()) {
            message = "Name is required.";
            editing = Field.NAME;
            return null;
        }
        if (host.isBlank()) {
            message = "Host is required.";
            editing = Field.HOST;
            return null;
        }
        int tcp = port(tcpText, "TCP", Field.TCP);
        if (tcp < 0) {
            return null;
        }
        int udp = port(udpText, "UDP", Field.UDP);
        if (udp < 0) {
            return null;
        }
        return new Connection(playerName.trim(), host.trim(), tcp, udp);
    }

    private int port(String value, String label, Field field) {
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed >= 1 && parsed <= 65535) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // The player gets a focused, actionable message below.
        }
        message = label + " port must be between 1 and 65535.";
        editing = field;
        return -1;
    }

    private void cycleField(int direction) {
        Field[] fields = {Field.NAME, Field.HOST, Field.TCP, Field.UDP};
        int current = 0;
        for (int i = 0; i < fields.length; i++) {
            if (fields[i] == editing) {
                current = i;
                break;
            }
        }
        editing = fields[Math.floorMod(current + direction, fields.length)];
        message = "Editing " + editing.name().toLowerCase(java.util.Locale.ROOT) + ".";
    }

    private void append(char character) {
        if (character < 32 || character > 126 || editing == Field.NONE) {
            return;
        }
        if ((editing == Field.TCP || editing == Field.UDP) && !Character.isDigit(character)) {
            return;
        }
        switch (editing) {
            case NAME -> playerName += character;
            case HOST -> host += character;
            case TCP -> tcpText += character;
            case UDP -> udpText += character;
            default -> { }
        }
    }

    private void backspace() {
        switch (editing) {
            case NAME -> playerName = trimLast(playerName);
            case HOST -> host = trimLast(host);
            case TCP -> tcpText = trimLast(tcpText);
            case UDP -> udpText = trimLast(udpText);
            default -> { }
        }
    }

    private Field fieldAt(float y, float formTop) {
        if (inRow(y, formTop)) return Field.NAME;
        if (inRow(y, formTop - 38f)) return Field.HOST;
        if (inRow(y, formTop - 76f)) return Field.TCP;
        if (inRow(y, formTop - 114f)) return Field.UDP;
        return Field.NONE;
    }

    private static boolean inRow(float y, float baseline) {
        return y >= baseline - FIELD_HEIGHT && y <= baseline + 6f;
    }

    private static String trimLast(String value) {
        return value.isEmpty() ? value : value.substring(0, value.length() - 1);
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
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
        batch.dispose();
        font.dispose();
    }

    private final class MenuInput extends InputAdapter {
        @Override
        public boolean keyDown(int keycode) {
            if (editing != Field.NONE) {
                if (keycode == Input.Keys.BACKSPACE || keycode == Input.Keys.FORWARD_DEL) {
                    backspace();
                } else if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
                    editing = Field.NONE;
                    message = "Connection form ready.";
                } else if (keycode == Input.Keys.ESCAPE) {
                    editing = Field.NONE;
                    message = "Editing cancelled.";
                } else if (keycode == Input.Keys.TAB) {
                    cycleField(1);
                }
                return true;
            }
            if (keycode == Input.Keys.UP) {
                selectedItem = Math.floorMod(selectedItem - 1, ITEMS.length);
            } else if (keycode == Input.Keys.DOWN) {
                selectedItem = (selectedItem + 1) % ITEMS.length;
            } else if (keycode == Input.Keys.TAB) {
                cycleField(1);
            } else if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
                activateSelected();
            }
            return true;
        }

        @Override
        public boolean keyTyped(char character) {
            append(character);
            return editing != Field.NONE;
        }

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            float x = screenX;
            float y = Gdx.graphics.getHeight() - screenY;
            float formTop = Gdx.graphics.getHeight() - 118f;
            if (x >= LEFT - 8f && x <= LEFT + FIELD_WIDTH) {
                Field field = fieldAt(y, formTop);
                if (field != Field.NONE) {
                    editing = field;
                    message = "Editing " + field.name().toLowerCase(java.util.Locale.ROOT) + ".";
                    return true;
                }
            }
            float itemTop = formTop - 174f;
            for (int i = 0; i < ITEMS.length; i++) {
                float baseline = itemTop - i * ITEM_GAP;
                if (x >= LEFT && x <= LEFT + 180f && inRow(y, baseline)) {
                    editing = Field.NONE;
                    selectedItem = i;
                    activateSelected();
                    return true;
                }
            }
            return true;
        }
    }
}
