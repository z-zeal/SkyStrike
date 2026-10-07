package io.github.skystrike.ui.hud;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import io.github.skystrike.input.InputRouter;
import io.github.skystrike.render.WeaponSprites;
import io.github.skystrike.shared.hud.LoadoutPickerModel;
import io.github.skystrike.shared.model.PlayerLoadout;
import io.github.skystrike.shared.net.c2s.PacketLoadoutUpdate;
import io.github.skystrike.shared.weapons.MeleeId;
import io.github.skystrike.ui.console.ConsoleFocus;
import java.util.List;
import java.util.function.Consumer;

/**
 * The loadout picker: the second half of the loadout HUD, opened by {@code ui_loadout}
 * (default {@code L}) — playable build plan M4 §5.
 *
 * <p>A column per slot, a row per catalogue entry, drawn from the same
 * {@code WeaponRegistry}/{@code MeleeRegistry}/{@code UtilityRegistry} tables the server
 * validates against. {@link LoadoutPickerModel} holds every decision that is not a pixel — what
 * a slot may offer, where the cursor is, what has changed and what the packet therefore says —
 * in {@code shared}, under test. This class draws that model, feeds it keystrokes and sends the
 * {@link PacketLoadoutUpdate} it produces.
 *
 * <p>Two honesty rules.
 *
 * <p><b>It says when it takes effect.</b> {@code LoadoutSystem} rebuilds a composition at the
 * next respawn, never mid-life, so {@link LoadoutPickerModel#APPLIES_AT_RESPAWN} is printed
 * across the footer. A picker that silently changes nothing until you die reads as broken.
 *
 * <p><b>It cannot offer what the server would drop.</b> The option lists are
 * {@code LoadoutOptions}, which is also what {@code LoadoutUpdateHandler} validates with — one
 * table, both ends.
 *
 * <p>While open it holds input focus through {@link ConsoleFocus}, which is the same gate the
 * console uses: {@code InputSampler} zeroes movement intent and {@code LoadoutController}
 * ignores slot keys for as long as anything holds focus, so browsing guns cannot walk you off
 * a ledge.
 */
public final class LoadoutPicker extends InputAdapter {

    private static final String CONTROLS =
        "W/S or arrows choose  A/D or Tab change slot  Enter apply  Esc close";

    private final HudTheme theme;
    private final LoadoutPickerModel model = new LoadoutPickerModel();
    private final WeaponSprites sprites;
    private final ConsoleFocus focus;
    private final Consumer<PacketLoadoutUpdate> sender;

    /** Asks the owner to clear {@code ui_loadout}; the cvar stays the single source of truth. */
    private Runnable closeRequest = () -> {
    };

    private boolean open;
    private String statusLine = "";

    private int screenWidth = 1;
    private int screenHeight = 1;

    private float panelX;
    private float panelY;
    private float panelWidth;
    private float panelHeight;
    private float rowHeight;
    private float columnWidth;
    private float scale;
    private int visibleRows = 1;

    private final GlyphLayout measurer = new GlyphLayout();

    public LoadoutPicker(
            HudTheme theme,
            WeaponSprites sprites,
            InputRouter router,
            Consumer<PacketLoadoutUpdate> sender) {
        if (theme == null || sprites == null || router == null || sender == null) {
            throw new IllegalArgumentException("all collaborators are required");
        }
        this.theme = theme;
        this.sprites = sprites;
        this.focus = new ConsoleFocus(router);
        this.sender = sender;
    }

    /** What to run when the picker wants to be closed — flipping {@code ui_loadout} to false. */
    public void setCloseRequest(Runnable closeRequest) {
        if (closeRequest != null) {
            this.closeRequest = closeRequest;
        }
    }

    public boolean isOpen() {
        return open;
    }

    /**
     * Opens or closes the picker, taking and releasing input focus with it. Opening re-reads
     * the live loadout, so the grid always starts on what is actually carried rather than on
     * the last thing that was browsed.
     */
    public void setOpen(boolean shouldOpen, PlayerLoadout live) {
        if (shouldOpen == open) {
            return;
        }
        open = shouldOpen;
        if (open) {
            model.syncFrom(live);
            statusLine = "";
            focus.take(this);
        } else {
            focus.release();
        }
    }

    /** Closes the picker and asks the owner to clear the cvar that opened it. */
    public void requestClose() {
        if (!open) {
            return;
        }
        setOpen(false, null);
        closeRequest.run();
    }

    /** Re-reads the live loadout, e.g. after the server confirmed a respawn rebuilt it. */
    public void syncFrom(PlayerLoadout live) {
        if (live != null) {
            model.syncFrom(live);
        }
    }

    // --- Input ----------------------------------------------------------------------------------

    @Override
    public boolean keyDown(int keycode) {
        if (!open) {
            return false;
        }
        switch (keycode) {
            case Input.Keys.ESCAPE, Input.Keys.L -> requestClose();
            case Input.Keys.UP, Input.Keys.W -> model.moveCursor(-1);
            case Input.Keys.DOWN, Input.Keys.S -> model.moveCursor(1);
            case Input.Keys.LEFT, Input.Keys.A -> model.cycleCategory(-1);
            case Input.Keys.RIGHT, Input.Keys.D, Input.Keys.TAB -> model.cycleCategory(1);
            case Input.Keys.PAGE_UP -> model.moveCursor(-visibleRows);
            case Input.Keys.PAGE_DOWN -> model.moveCursor(visibleRows);
            case Input.Keys.HOME -> model.setCursor(0);
            case Input.Keys.END -> model.setCursor(Integer.MAX_VALUE);
            case Input.Keys.ENTER -> apply();
            default -> {
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean keyUp(int keycode) {
        return open;
    }

    @Override
    public boolean keyTyped(char character) {
        return open;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        if (!open) {
            return false;
        }
        model.moveCursor(Math.round(amountY));
        return true;
    }

    /**
     * Sends the request. Only changed fields travel ({@code KEEP_CURRENT} elsewhere), so
     * applying an untouched picker sends nothing at all rather than a no-op packet the server
     * would have to reason about.
     */
    private void apply() {
        PacketLoadoutUpdate packet = model.toPacket();
        if (packet.isEmpty()) {
            statusLine = "Nothing changed.";
            return;
        }
        sender.accept(packet);
        model.commit();
        statusLine = "Requested. " + LoadoutPickerModel.APPLIES_AT_RESPAWN;
    }

    // --- Drawing ----------------------------------------------------------------------------------

    void layout(int screenWidth, int screenHeight, float scale, BitmapFont font) {
        this.scale = scale;
        this.screenWidth = Math.max(1, screenWidth);
        this.screenHeight = Math.max(1, screenHeight);
        this.panelWidth = screenWidth * theme.pickerWidthFraction;
        this.panelHeight = screenHeight * theme.pickerHeightFraction;
        this.panelX = (screenWidth - panelWidth) / 2f;
        this.panelY = (screenHeight - panelHeight) / 2f;
        this.columnWidth = theme.pickerColumnWidth * scale;
        this.rowHeight = Math.max(theme.pickerRowHeight * scale, font.getLineHeight() * 2.1f);
        float listHeight = panelHeight - headerHeight(font) - footerHeight(font);
        this.visibleRows = Math.max(1, (int) (listHeight / rowHeight));
    }

    private float headerHeight(BitmapFont font) {
        return font.getLineHeight() * 2.4f + theme.innerPadding * scale * 2f;
    }

    private float footerHeight(BitmapFont font) {
        return font.getLineHeight() * 3f + theme.innerPadding * scale * 2f;
    }

    /** First visible row, keeping the cursor inside the window without jumping it to the top. */
    private int firstVisibleRow() {
        int size = model.currentOptions().size();
        int cursor = model.cursor();
        if (size <= visibleRows) {
            return 0;
        }
        int first = cursor - visibleRows / 2;
        return Math.max(0, Math.min(size - visibleRows, first));
    }

    void drawShapes(ShapeRenderer shapes, BitmapFont font, HudFrame frame) {
        if (!open) {
            return;
        }
        // The dimmer: the match is still visible behind the picker, but not competing with it.
        Color panel = theme.panelFill();
        shapes.setColor(0f, 0f, 0f, 0.45f);
        shapes.rect(0f, 0f, screenWidth, screenHeight);

        shapes.setColor(panel);
        shapes.rect(panelX, panelY, panelWidth, panelHeight);
        Color edge = theme.panelEdge();
        shapes.setColor(edge);
        float edgeWidth = Math.max(1f, 1.5f * scale);
        shapes.rect(panelX, panelY + panelHeight - edgeWidth, panelWidth, edgeWidth);
        shapes.rect(panelX, panelY, panelWidth, edgeWidth);

        // The category rail down the left.
        float railTop = panelY + panelHeight - headerHeight(font);
        LoadoutPickerModel.Category[] categories = LoadoutPickerModel.Category.values();
        float categoryHeight = font.getLineHeight() * 1.9f;
        for (int i = 0; i < categories.length; i++) {
            float rowY = railTop - (i + 1) * categoryHeight;
            boolean current = categories[i] == model.category();
            shapes.setColor(current ? theme.slotFillActive : theme.slotFill);
            shapes.rect(panelX + theme.innerPadding * scale, rowY,
                columnWidth, categoryHeight - 2f);
            if (current) {
                shapes.setColor(theme.slotEdgeActive);
                shapes.rect(panelX + theme.innerPadding * scale, rowY, edgeWidth,
                    categoryHeight - 2f);
            }
        }

        // The option list.
        float listX = panelX + theme.innerPadding * scale * 2f + columnWidth;
        float listWidth = panelWidth - (listX - panelX) - previewWidth() - theme.innerPadding * scale * 3f;
        int first = firstVisibleRow();
        List<LoadoutPickerModel.Option> options = model.currentOptions();
        for (int row = 0; row < visibleRows && first + row < options.size(); row++) {
            float rowY = railTop - (row + 1) * rowHeight;
            boolean selected = first + row == model.cursor();
            shapes.setColor(selected ? theme.slotFillActive : theme.slotFill);
            shapes.rect(listX, rowY, listWidth, rowHeight - 2f);
            if (selected) {
                shapes.setColor(theme.slotEdgeActive);
                shapes.rect(listX, rowY, edgeWidth, rowHeight - 2f);
            }
        }

        // The preview plate on the right, behind the sprite drawn in the batch pass.
        shapes.setColor(theme.slotFill);
        shapes.rect(panelX + panelWidth - previewWidth() - theme.innerPadding * scale,
            railTop - previewWidth(), previewWidth(), previewWidth());
    }

    private float previewWidth() {
        return Math.min(panelHeight * 0.42f, panelWidth * 0.26f);
    }

    void drawText(SpriteBatch batch, BitmapFont font, HudFrame frame) {
        if (!open) {
            return;
        }
        float pad = theme.innerPadding * scale;
        float lineHeight = font.getLineHeight();
        float railTop = panelY + panelHeight - headerHeight(font);

        // Header.
        font.setColor(theme.text(theme.textPrimary));
        font.draw(batch, "LOADOUT", panelX + pad, panelY + panelHeight - pad);
        font.setColor(theme.text(theme.textDim));
        font.draw(batch, CONTROLS, panelX + pad, panelY + panelHeight - pad - lineHeight * 1.2f);

        // The category rail: slot key, name, and what that slot currently points at.
        LoadoutPickerModel.Category[] categories = LoadoutPickerModel.Category.values();
        float categoryHeight = font.getLineHeight() * 1.9f;
        for (int i = 0; i < categories.length; i++) {
            LoadoutPickerModel.Category category = categories[i];
            float rowY = railTop - (i + 1) * categoryHeight;
            boolean current = category == model.category();
            font.setColor(theme.text(current ? theme.textAccent : theme.textDim));
            font.draw(batch, category.slotLabel() + "  " + category.title(),
                panelX + pad * 1.6f, rowY + categoryHeight - lineHeight * 0.25f);
            LoadoutPickerModel.Option selected = model.selected(category);
            if (selected != null) {
                font.setColor(theme.text(
                    model.isChanged(category) ? theme.textWarning : theme.textDim));
                drawClipped(batch, font,
                    (model.isChanged(category) ? "* " : "") + selected.label(),
                    panelX + pad * 1.6f, rowY + categoryHeight - lineHeight * 1.15f,
                    columnWidth - pad);
            }
        }

        // The option rows.
        float listX = panelX + pad * 2f + columnWidth;
        float listWidth = panelWidth - (listX - panelX) - previewWidth() - pad * 3f;
        int first = firstVisibleRow();
        List<LoadoutPickerModel.Option> options = model.currentOptions();
        for (int row = 0; row < visibleRows && first + row < options.size(); row++) {
            LoadoutPickerModel.Option option = options.get(first + row);
            float rowY = railTop - (row + 1) * rowHeight;
            boolean selected = first + row == model.cursor();
            font.setColor(theme.text(selected ? theme.textPrimary : theme.textDim));
            drawClipped(batch, font, option.label(), listX + pad * 0.6f,
                rowY + rowHeight - lineHeight * 0.3f, listWidth - pad);
            font.setColor(theme.text(theme.textDim));
            drawClipped(batch, font, option.detail(), listX + pad * 0.6f,
                rowY + rowHeight - lineHeight * 1.2f, listWidth - pad);
        }

        // Position in a list that is 150 guns long.
        if (!options.isEmpty()) {
            String position = (model.cursor() + 1) + " / " + options.size();
            measurer.setText(font, position);
            font.setColor(theme.text(theme.textDim));
            font.draw(batch, position, listX + listWidth - measurer.width,
                panelY + panelHeight - pad);
        }

        drawPreview(batch, font, railTop, pad);

        // The footer: what this does, and when.
        font.setColor(theme.text(theme.textWarning));
        font.draw(batch, LoadoutPickerModel.APPLIES_AT_RESPAWN, panelX + pad,
            panelY + pad + lineHeight * 2.2f);
        if (!statusLine.isEmpty()) {
            font.setColor(theme.text(theme.textAccent));
            font.draw(batch, statusLine, panelX + pad, panelY + pad + lineHeight);
        } else if (model.isDirty()) {
            font.setColor(theme.text(theme.textDim));
            font.draw(batch, "Enter sends the changes marked *.", panelX + pad,
                panelY + pad + lineHeight);
        }
        font.setColor(Color.WHITE);
    }

    /**
     * The product shot for the highlighted row, from the same {@code assets/sprites} atlas the
     * held weapon is drawn with. Throwables and gadgets have no art yet, so the plate carries
     * their name instead of an empty frame.
     */
    private void drawPreview(SpriteBatch batch, BitmapFont font, float railTop, float pad) {
        LoadoutPickerModel.Option option = model.selected();
        if (option == null) {
            return;
        }
        float size = previewWidth();
        float plateX = panelX + panelWidth - size - pad;
        float plateY = railTop - size;

        Integer wireId = previewWireId(option);
        Texture texture = wireId == null ? null : sprites.texture(wireId);
        if (texture != null) {
            float inset = size * 0.08f;
            batch.setColor(Color.WHITE);
            batch.draw(texture, plateX + inset, plateY + inset, size - inset * 2f, size - inset * 2f);
        } else {
            font.setColor(theme.text(theme.textDim));
            measurer.setText(font, option.label());
            font.draw(batch, option.label(),
                plateX + Math.max(0f, (size - measurer.width) / 2f), plateY + size / 2f);
        }
        font.setColor(theme.text(theme.textPrimary));
        drawClipped(batch, font, option.label(), plateX, plateY - font.getLineHeight() * 0.2f, size);
        font.setColor(theme.text(theme.textDim));
        drawClipped(batch, font, option.detail(), plateX,
            plateY - font.getLineHeight() * 1.1f, size);
    }

    /** The sprite atlas is keyed by wire id; only guns and melee weapons have one. */
    private Integer previewWireId(LoadoutPickerModel.Option option) {
        LoadoutPickerModel.Category category = model.category();
        if (category == LoadoutPickerModel.Category.PRIMARY
            || category == LoadoutPickerModel.Category.SIDEARM) {
            // Gun wire ids are the ordinals themselves; melee ids are offset, utilities and
            // gadgets have no art yet.
            return option.ordinal();
        }
        if (category == LoadoutPickerModel.Category.MELEE
            && MeleeId.isValidOrdinal(option.ordinal())) {
            return MeleeId.fromOrdinal(option.ordinal()).wireId();
        }
        return null;
    }

    private void drawClipped(
            SpriteBatch batch, BitmapFont font, String text, float x, float baseline,
            float maxWidth) {
        String drawn = text == null ? "" : text;
        measurer.setText(font, drawn);
        if (measurer.width <= maxWidth) {
            font.draw(batch, drawn, x, baseline);
            return;
        }
        while (drawn.length() > 1) {
            drawn = drawn.substring(0, drawn.length() - 1);
            measurer.setText(font, drawn + ".");
            if (measurer.width <= maxWidth) {
                break;
            }
        }
        font.draw(batch, drawn + ".", x, baseline);
    }
}
