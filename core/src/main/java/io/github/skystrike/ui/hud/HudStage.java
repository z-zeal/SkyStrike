package io.github.skystrike.ui.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import io.github.skystrike.input.InputRouter;
import io.github.skystrike.render.RenderLayers;
import io.github.skystrike.render.WeaponSprites;
import io.github.skystrike.shared.hud.KillFeedModel;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.model.PlayerLoadout;
import io.github.skystrike.shared.net.c2s.PacketLoadoutUpdate;
import io.github.skystrike.shared.net.s2c.PacketDamageEvent;
import io.github.skystrike.shared.net.s2c.PacketKillEvent;
import io.github.skystrike.ui.text.FontManager;
import java.util.function.Consumer;

/**
 * The HUD: one viewport, one batch, one font, drawn last (playable build plan M4 §5).
 *
 * <p>Every widget below — bars, loadout bar, crosshair, minimap, kill feed, damage vignette,
 * debug panel and the loadout picker — draws through this class's single screen-space projection
 * and its
 * single {@link SpriteBatch}/{@link ShapeRenderer} pair. That is not tidiness for its own sake:
 * a {@code ShapeRenderer} batch and a {@code SpriteBatch} batch cannot be open at the same
 * time, so the frame is strictly two passes — <b>all shapes, then all text</b> — and widgets
 * expose {@code drawShapes}/{@code drawText} rather than a single {@code render}. Adding a
 * widget that opens its own batch would silently double the draw-call cost and break the
 * ordering.
 *
 * <p>Order within each pass is back-to-front: vignette (the screen you are looking through),
 * then the map, then the readouts, then the crosshair, then the picker, which covers everything
 * when open.
 * The whole stage is {@link RenderLayers#HUD}: after the fog composite and the post stack,
 * before the console dialog, because being blinded is a gameplay state but being unable to
 * read your ammo is a UI failure.
 *
 * <p>The font follows the console's rule: the baked outline-and-shadow {@link FontManager} when
 * {@code fonts/dialog.ttf} ships, the stock bitmap font when it does not, so a missing asset
 * costs legibility rather than the HUD.
 */
public final class HudStage implements Disposable {

    private static final String CUSTOM_FONT_PATH = "fonts/dialog.ttf";

    private final HudTheme theme = new HudTheme();
    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final Matrix4 projection = new Matrix4();

    private final KillFeedModel killFeed = new KillFeedModel();
    private final HealthFuelBars vitals = new HealthFuelBars(theme);
    private final LoadoutBar loadoutBar = new LoadoutBar(theme);
    private final Crosshair crosshair = new Crosshair(theme);
    private final Minimap minimap = new Minimap(theme);
    private final SurveillanceBanner surveillanceBanner = new SurveillanceBanner(theme);
    private final KillFeedWidget killFeedWidget = new KillFeedWidget(theme, killFeed);
    private final DamageVignette damageVignette = new DamageVignette(theme);
    private final DebugPanel debugPanel = new DebugPanel(theme);
    private final LoadoutPicker picker;

    /**
     * The picker's product shots. Borrowed from the player renderer rather than loaded a
     * second time — browsing 150 guns would otherwise fill a second texture cache with the
     * same 512x512 PNGs — so this stage never disposes it.
     */
    private final WeaponSprites sprites;

    private FontManager fontManager;
    private BitmapFont fallbackFont;
    private boolean fontChecked;

    private int screenWidth = 1;
    private int screenHeight = 1;
    private boolean laidOut;

    /**
     * Whether the map is being drawn, tracked only so that toggling {@code cl_minimap} can re-run
     * the layout: the debug panel shares its corner and has to move out of the way or back into it.
     */
    private boolean minimapShown;

    /** Tracks the local player's alive flag so a respawn can clear the damage tint exactly once. */
    private boolean wasAlive = true;

    /**
     * @param router        the focus stack the picker takes the keyboard from
     * @param loadoutSender where a picker request goes, normally {@code ClientSession::sendReliable}
     * @param sprites       the held-weapon atlas, owned and disposed by the player renderer
     */
    public HudStage(
            InputRouter router,
            Consumer<PacketLoadoutUpdate> loadoutSender,
            WeaponSprites sprites) {
        this.sprites = sprites;
        this.picker = new LoadoutPicker(theme, sprites, router, loadoutSender);
    }

    /** The layer this stage belongs to: after post, before the dialog. */
    public RenderLayers layer() {
        return RenderLayers.HUD;
    }

    public LoadoutPicker picker() {
        return picker;
    }

    /** The feed is filed from the session's kill listener; the widget only draws it. */
    public void onKill(PacketKillEvent kill, long nowMillis) {
        killFeed.add(kill, nowMillis);
    }

    /** Damage against the local player drives the vignette; damage dealt does not. */
    public void onDamage(PacketDamageEvent damage, Player localPlayer) {
        damageVignette.onDamage(damage, localPlayer);
    }

    /** Who "you" are in the kill feed, as soon as the server has said so. */
    public void setLocalPlayerId(int localPlayerId) {
        killFeed.setLocalPlayerId(localPlayerId);
    }

    public void resize(int width, int height) {
        screenWidth = Math.max(1, width);
        screenHeight = Math.max(1, height);
        projection.setToOrtho2D(0f, 0f, screenWidth, screenHeight);
        if (fontManager != null) {
            fontManager.resize(screenHeight, Gdx.graphics == null ? 1f : Gdx.graphics.getDensity());
        }
        laidOut = false;
    }

    /**
     * Per-frame state that is not drawing: the font lifecycle, the vignette's decay, and the
     * picker's open state, which follows the {@code ui_loadout} cvar rather than its own flag.
     *
     * @param deltaSeconds    frame time
     * @param pickerRequested the current value of {@code ui_loadout}
     * @param minimapShown    the current value of {@code cl_minimap}
     * @param live            the predicted loadout the picker re-reads when it opens
     */
    public void update(
            float deltaSeconds, boolean pickerRequested, boolean minimapShown, PlayerLoadout live) {
        ensureFont();
        damageVignette.update(deltaSeconds);
        picker.setOpen(pickerRequested, live);
        if (minimapShown != this.minimapShown) {
            this.minimapShown = minimapShown;
            laidOut = false;
        }
    }

    private void ensureFont() {
        if (fontChecked) {
            return;
        }
        fontChecked = true;
        if (Gdx.files != null && Gdx.files.internal(CUSTOM_FONT_PATH).exists()) {
            fontManager = new FontManager(CUSTOM_FONT_PATH);
            fontManager.resize(screenHeight, Gdx.graphics == null ? 1f : Gdx.graphics.getDensity());
        } else {
            fallbackFont = new BitmapFont();
            fallbackFont.setColor(Color.WHITE);
        }
    }

    private BitmapFont font() {
        return fontManager != null ? fontManager.font() : fallbackFont;
    }

    /**
     * Draws the whole HUD for one frame. Must be called on the render thread after the fog
     * composite and before the console dialog.
     */
    public void render(HudFrame frame) {
        ensureFont();
        BitmapFont font = font();
        if (font == null || frame == null) {
            return;
        }
        trackRespawn(frame.player());

        float scale = theme.scale(screenHeight);
        if (!laidOut) {
            layout(font, scale);
        }

        Gdx.gl.glEnable(GL20.GL_BLEND);

        shapes.setProjectionMatrix(projection);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        damageVignette.drawShapes(shapes, frame);
        minimap.drawShapes(shapes, frame);
        surveillanceBanner.drawShapes(shapes, font, frame);
        debugPanel.drawShapes(shapes, font, frame);
        killFeedWidget.drawShapes(shapes, font, frame);
        vitals.drawShapes(shapes, font, frame);
        loadoutBar.drawShapes(shapes, frame);
        crosshair.drawShapes(shapes, frame);
        picker.drawShapes(shapes, font, frame);
        shapes.end();

        batch.setProjectionMatrix(projection);
        batch.begin();
        surveillanceBanner.drawText(batch, font, frame);
        debugPanel.drawText(batch, font, frame);
        killFeedWidget.drawText(batch, font, frame);
        vitals.drawText(batch, font, frame);
        loadoutBar.drawText(batch, font, frame);
        picker.drawText(batch, font, frame);
        batch.end();

        font.setColor(Color.WHITE);
        batch.setColor(Color.WHITE);
    }

    /** A new life starts on a clean screen: the last death's damage tint does not carry over. */
    private void trackRespawn(Player player) {
        boolean alive = player == null || player.alive;
        if (alive && !wasAlive) {
            damageVignette.reset();
        }
        wasAlive = alive;
    }

    private void layout(BitmapFont font, float scale) {
        // Four corners, no overlaps. Along the bottom the console owns the left: its passive
        // view prints up to ConsoleTheme.passiveLines of chat from the bottom-left upwards, so
        // the vitals clear that strip entirely while the loadout bar, right-aligned beyond the
        // dialog's 62%-width panel, sits on the bottom edge. Along the top the map owns the left
        // corner when it is on and the kill feed owns the right, so the readout takes an inset
        // from the map rather than a position of its own — one widget can move the other without
        // either of them knowing the other exists.
        float chatStrip = theme.console().margin * scale
            + theme.console().passiveLines
                * (font.getLineHeight() + theme.console().lineGap * scale);
        vitals.layout(screenWidth, screenHeight, scale, chatStrip);
        loadoutBar.layout(screenWidth, screenHeight, scale, 0f);
        crosshair.layout(screenWidth, screenHeight, scale);
        surveillanceBanner.layout(screenWidth, screenHeight, scale);
        killFeedWidget.layout(screenWidth, screenHeight, scale, 0f);
        damageVignette.layout(screenWidth, screenHeight);
        // The map owns the top-left corner when it is on, so the readout starts below it; when it
        // is off the inset is zero and the readout takes the corner back, leaving no hole.
        minimap.layout(screenWidth, screenHeight, scale);
        debugPanel.layout(screenWidth, screenHeight, scale, minimap.occupiedHeight(minimapShown));
        picker.layout(screenWidth, screenHeight, scale, font);
        laidOut = true;
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
        if (fontManager != null) {
            fontManager.dispose();
        }
        if (fallbackFont != null) {
            fallbackFont.dispose();
        }
    }
}
