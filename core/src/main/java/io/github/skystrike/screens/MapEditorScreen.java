package io.github.skystrike.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.skystrike.render.GameCamera;
import io.github.skystrike.shared.config.WorldConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.map.ArenaMapFormat;
import io.github.skystrike.shared.map.DoorSpec;
import io.github.skystrike.shared.map.MapAudit;
import io.github.skystrike.shared.map.MapBlock;
import io.github.skystrike.shared.map.MapDraft;
import io.github.skystrike.shared.map.MapPalette;
import io.github.skystrike.shared.map.MapReachability;
import io.github.skystrike.shared.map.Rect;
import io.github.skystrike.world.TerrainRenderer;
import java.util.ArrayList;
import java.util.List;

/**
 * The map editor (map plan §6): a touch-first view over a {@link MapDraft}, drawn with the same
 * {@link TerrainRenderer} the game draws the arena with.
 *
 * <p><b>A view, and nothing else.</b> Every authoring rule lives in {@code shared}:
 * {@link MapPalette} says what a tap places and where, {@link MapDraft} holds the authored verbs —
 * so a block cannot lose its twin — and {@link MapAudit} answers what the map is worth and
 * produces the one bake. This class owns the parts a screen has to own: a camera, a pointer,
 * buttons, and the file the author asked for. It names no block kind, which is why the map plan §9
 * consumer gate needs no exemption for it: the palette tells it what a preset is, and
 * {@link MapPalette.Preset#door()} tells it whether that preset needs an id.
 *
 * <p><b>Reached only from a debug build.</b> The screen is opened by a main-menu action the
 * composition root offers only while {@code DebugFlags.enabled()} is true, which is the same gate
 * the local test host sits behind; the menu's own list is rebuilt without the action in a release
 * build, so there is no path to this screen at all.
 *
 * <p><b>It cannot publish.</b> The screen holds no session, no socket and no client: its only
 * outbound edge is {@link #back}, which returns to the menu. A map reaches a match through a
 * server's revision and nothing else (plan §10), so what an author can do here is write a file.
 *
 * <p><b>Save and bake are the shipped paths.</b> Saving writes {@link MapDraft#toText()} — the
 * canonical writer — and proves it by parsing the text back with {@link ArenaMapFormat#parse}
 * before it writes anything, which is the loader's own check including the spawn-in-block rule.
 * Baking writes {@link MapAudit#bake}'s bytes, the same call {@code MapTool bake} makes, so an
 * asset baked on a phone and one baked in CI are the same bytes.
 *
 * <p><b>Where files go.</b> {@code .skystrike/maps/} in the user's own directory — beside the
 * client's preference store, never inside the repository — falling back to {@code maps/} beside
 * the working directory on a backend without external storage. The name is reduced to letters,
 * digits, {@code -} and {@code _}, so a name can never be a path.
 *
 * <p><b>Gestures.</b> A tap places the armed palette entry at the tapped point, or deletes the
 * block under it when the delete tool is armed; a drag pans; the buttons zoom. Placement goes
 * through the same verb the file format uses: a rectangle that clears the mirror axis is authored
 * {@code mirrored} and gets its twin, one on the axis is authored {@code centred}, and the
 * reachability overlay marks the stances no spawn can reach after {@code Validate}.
 */
public final class MapEditorScreen extends de.eskalon.commons.screen.ManagedScreenAdapter {

    /** The main-menu action that opens this screen; a debug build is the only build offered it. */
    public static final String ACTION = "Map editor";

    private static final float BUTTON_WIDTH = 168f;
    private static final float BUTTON_HEIGHT = 42f;
    private static final float PANEL_PAD = 12f;
    /** A gesture that moves less than this many pixels is a tap; anything more is a pan. */
    private static final float TAP_SLOP_PIXELS = 14f;
    private static final float ZOOM_IN_FACTOR = 0.8f;
    private static final float ZOOM_OUT_FACTOR = 1.25f;
    private static final String SAVE_DIRECTORY = ".skystrike/maps/";
    private static final String FALLBACK_DIRECTORY = "maps/";
    private static final String DEFAULT_NAME = "draft";
    private static final float STANCE_MARKER_RADIUS = 9f;

    private static final Color BACKGROUND = new Color(.025f, .035f, .06f, 1f);
    /** The sealed pocket: the colour the acceptance document calls an unreachable objective. */
    private static final Color UNREACHABLE_COLOR = new Color(0.95f, 0.30f, 0.26f, 1f);
    /** Where the next tap would put the armed preset. */
    private static final Color GHOST_COLOR = new Color(0.95f, 0.85f, 0.35f, 0.9f);
    private static final Color ARMED_COLOR = new Color(1f, 0.86f, 0.46f, 1f);
    private static final Color STATUS_COLOR = new Color(0.72f, 0.82f, 0.95f, 1f);

    /** What a tap does. The palette arms {@link #PLACE}; the delete button arms {@link #DELETE}. */
    private enum Tool {
        PLACE,
        DELETE
    }

    private final Runnable back;
    private final MapDraft draft;
    private final Skin skin;
    private final Stage stage;
    private final InputMultiplexer input = new InputMultiplexer();
    private final GameCamera camera =
        new GameCamera(WorldConfig.ARENA_WIDTH, WorldConfig.ARENA_HEIGHT);
    /** The reachability overlay's own renderer; the terrain draws with its own. */
    private final ShapeRenderer markers = new ShapeRenderer();
    /** One reusable unprojection target, so drawing the ghost allocates nothing per frame. */
    private final Vector3 screenPoint = new Vector3();
    private final TextField nameField;
    private final Label status;
    private final TextButton deleteButton;
    private final List<TextButton> presetButtons = new ArrayList<>();
    private final List<MapPalette.Preset> presets = MapPalette.presets();

    private Tool tool = Tool.PLACE;
    private MapPalette.Preset armed;
    /** The stances no spawn can reach, from the last {@code Validate}; empty until then. */
    private List<MapReachability.Stance> pocket = List.of();
    /** The map the picture is drawn from; rebuilt whenever the draft changes. */
    private TerrainRenderer terrain;
    private boolean terrainDirty = true;
    /** Whether the last build attempt failed, so its reason is reported once and not per frame. */
    private boolean buildFailed;
    private boolean closing;
    private boolean disposed;

    public MapEditorScreen(Runnable back) {
        if (back == null) {
            throw new IllegalArgumentException("back is required");
        }
        this.back = back;
        // The shipped arena is the only map there is (the shipped-maps milestone has not moved it
        // to a file yet), so the editor opens a draft of it: an author starts from a real arena
        // rather than from an empty field, and isDirty() answers whether they changed anything.
        this.draft = MapDraft.of(ArenaMap.standard());
        this.skin = new Skin(Gdx.files.internal("ui/uiskin.json"));
        this.stage = new Stage(new ScreenViewport());
        this.nameField = new TextField(DEFAULT_NAME, skin);
        this.nameField.setMessageText("map name");
        this.status = new Label("", skin);
        this.status.setWrap(true);
        this.status.setAlignment(Align.left);
        this.status.setColor(STATUS_COLOR);
        this.deleteButton = new TextButton("Delete", skin);

        stage.addActor(buildLayout());
        // The stage is the first processor so a button keeps its own taps: Scene2D returns true
        // only where an actor was hit, and the canvas gets the rest of the screen.
        input.addProcessor(stage);
        input.addProcessor(new CanvasInput());
        addInputProcessor(input);
        arm(presets.get(0));
        camera.centreOn(draft.mirrorAxisX(), WorldConfig.ARENA_HEIGHT / 2f);
        report("editing the shipped arena. Tap to place, drag to pan, Validate for reachability.");
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    private Table buildLayout() {
        Table root = new Table();
        root.setFillParent(true);
        root.top().left().pad(PANEL_PAD);

        Table header = new Table();
        Label title = new Label("MAP EDITOR", skin, "window");
        Label note = new Label("debug build only — a map cannot reach a match from here", skin,
            "subtitle");
        TextButton backButton = new TextButton("Back", skin);
        backButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                close();
            }
        });
        header.add(title).left();
        header.add(note).left().padLeft(12f).expandX();
        header.add(backButton).right().width(BUTTON_WIDTH).height(BUTTON_HEIGHT);
        root.add(header).growX().padBottom(PANEL_PAD).row();

        Table body = new Table();
        body.top().left();
        body.add(buildPalettePanel()).top().left();
        // The cell that keeps the palette in the corner: the canvas is whatever is behind this.
        body.add().expand();
        root.add(body).grow().row();

        root.add(buildActionPanel()).growX().bottom();
        return root;
    }

    private Table buildPalettePanel() {
        Table panel = new Table();
        panel.top().left();
        panel.defaults().width(BUTTON_WIDTH).height(BUTTON_HEIGHT).padBottom(6f);

        Label paletteLabel = new Label("PLACE", skin);
        panel.add(paletteLabel).left().row();
        for (MapPalette.Preset preset : presets) {
            TextButton button = new TextButton(preset.label(), skin);
            button.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    arm(preset);
                }
            });
            presetButtons.add(button);
            panel.add(button).left().row();
        }

        deleteButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                armDelete();
            }
        });
        panel.add(deleteButton).left().padTop(6f).row();

        Table zoom = new Table();
        TextButton zoomIn = new TextButton("+", skin);
        TextButton zoomOut = new TextButton("-", skin);
        zoomIn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                camera.zoomBy(ZOOM_IN_FACTOR);
            }
        });
        zoomOut.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                camera.zoomBy(ZOOM_OUT_FACTOR);
            }
        });
        zoom.add(zoomIn).width(BUTTON_WIDTH / 2f - 3f).height(BUTTON_HEIGHT);
        zoom.add(zoomOut).width(BUTTON_WIDTH / 2f - 3f).height(BUTTON_HEIGHT).padLeft(6f);
        panel.add(zoom).left().row();
        return panel;
    }

    private Table buildActionPanel() {
        Table panel = new Table();
        panel.bottom().left().padTop(PANEL_PAD);

        Table row = new Table();
        row.defaults().height(BUTTON_HEIGHT).padRight(6f);
        row.add(action("Validate", this::validate));
        row.add(action("Save .map", this::save));
        row.add(action("Bake .sdf", this::bake));
        row.add(action("Clear", this::clearBlocks));
        row.add(nameField).width(BUTTON_WIDTH * 1.4f);
        panel.add(row).left().row();
        panel.add(status).growX().padTop(8f).row();
        return panel;
    }

    private TextButton action(String label, Runnable action) {
        TextButton button = new TextButton(label, skin);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                action.run();
            }
        });
        return button;
    }

    /** Arms a palette entry for placement, which is the mirror gesture: its twin comes with it. */
    private void arm(MapPalette.Preset preset) {
        armed = preset;
        tool = Tool.PLACE;
        for (int index = 0; index < presetButtons.size(); index++) {
            boolean selected = presets.get(index).equals(preset);
            presetButtons.get(index).setColor(selected ? ARMED_COLOR : Color.WHITE);
        }
        deleteButton.setColor(Color.WHITE);
        report("placing " + preset.label().toLowerCase() + " (" + preset.width() + " x "
            + preset.height() + "): a tap places it and its twin, or a centred block on the axis.");
    }

    private void armDelete() {
        tool = Tool.DELETE;
        for (TextButton button : presetButtons) {
            button.setColor(Color.WHITE);
        }
        deleteButton.setColor(ARMED_COLOR);
        report("deleting: a tap removes the block under it, and its twin goes with it.");
    }

    /** The canvas gesture: a tap authors or deletes, everything else is a pan. */
    private void tapAt(float worldX, float worldY) {
        if (tool == Tool.DELETE) {
            deleteAt(worldX, worldY);
        } else {
            placeAt(worldX, worldY);
        }
    }

    private void placeAt(float worldX, float worldY) {
        if (armed == null) {
            report("pick a palette entry first.");
            return;
        }
        Rect rect = MapPalette.tapRect(armed, worldX, worldY, draft.mirrorAxisX());
        boolean centred = MapPalette.isCentred(rect, draft.mirrorAxisX());
        DoorSpec spec = armed.door()
            ? DoorSpec.all(MapPalette.nextDoorId(draft.blocks()))
            : DoorSpec.NONE;
        MapBlock block = armed.block(rect, spec);
        // A duplicate is a load error the author would otherwise meet at save time, and the twin
        // counts: a mirrored placement adds the reflection too, and that is what usually collides.
        if (draft.blocks().contains(block)
                || (!centred && draft.blocks().contains(block.mirror(draft.mirrorAxisX())))) {
            report("there is already a " + armed.label().toLowerCase() + " there.");
            return;
        }
        if (centred) {
            draft.centred(block);
        } else {
            draft.mirrored(block);
        }
        edited();
        report("placed " + armed.label().toLowerCase() + " at (" + rect.x() + ", " + rect.y() + ")"
            + (centred ? ", centred on the axis." : " and its twin."));
    }

    private void deleteAt(float worldX, float worldY) {
        List<MapBlock> blocks = draft.blocks();
        for (int index = blocks.size() - 1; index >= 0; index--) {
            MapBlock block = blocks.get(index);
            if (!block.rect().contains(worldX, worldY)) {
                continue;
            }
            int before = blocks.size();
            if (draft.remove(block)) {
                edited();
                report("deleted " + (before - draft.blocks().size()) + " block(s) at ("
                    + block.rect().x() + ", " + block.rect().y() + ").");
            } else {
                report("that block belongs to no authored line.");
            }
            return;
        }
        report("nothing to delete there.");
    }

    /**
     * The reachability gate's report, and the overlay it marks, over what the loader would read.
     *
     * <p>The audit runs on the map a fresh session would get — the draft written and parsed back —
     * rather than on the draft's in-memory blocks, so what the overlay draws and what a player
     * would meet are the same map.
     */
    private void validate() {
        try {
            ArenaMap map = ArenaMapFormat.parse(draft.toText());
            MapAudit.Result result = MapAudit.check(map, mapName());
            pocket = result.pocket();
            List<String> lines = new ArrayList<>(result.lines());
            if (!pocket.isEmpty()) {
                lines.add("  " + pocket.size()
                    + " unreachable stance(s) marked on the map in red.");
            }
            if (!result.reachability().fullyReachable()) {
                lines.add("  the strict reading of plan §7 fails this map; CI enforces the"
                    + " spawns-connect reading above.");
            }
            report(String.join("\n", lines));
        } catch (IllegalArgumentException invalid) {
            pocket = List.of();
            report("cannot validate: " + invalid.getMessage());
        }
    }

    private void save() {
        try {
            String text = draft.toText();
            ArenaMap map = ArenaMapFormat.parse(text);
            FileHandle file = file(mapName(), ".map");
            file.writeString(text, false);
            report("saved " + file.path() + ": " + map.blocks().size() + " block(s), "
                + map.spawns().size() + " spawn(s), hash " + map.mapHashHex());
        } catch (IllegalArgumentException invalid) {
            report("cannot save: " + invalid.getMessage());
        }
    }

    private void bake() {
        try {
            ArenaMap map = ArenaMapFormat.parse(draft.toText());
            byte[] bytes = MapAudit.bake(map);
            FileHandle file = file(mapName(), ".sdf");
            file.writeBytes(bytes, false);
            report("baked " + file.path() + ": " + bytes.length + " bytes for hash "
                + map.mapHashHex() + " (MapTool bake writes the same bytes).");
        } catch (IllegalArgumentException invalid) {
            report("cannot bake: " + invalid.getMessage());
        }
    }

    private void clearBlocks() {
        int removed = draft.authoredLineCount();
        draft.clear();
        edited();
        report("cleared " + removed + " authored line(s); the spawns and the axis stay.");
    }

    /** Called after every edit: the picture is rebuilt, and the overlay is no longer the map's. */
    private void edited() {
        terrainDirty = true;
        pocket = List.of();
    }

    private void report(String message) {
        status.setText(message);
    }

    /**
     * The map name as a file name: letters, digits, {@code -} and {@code _} only.
     *
     * <p>Anything else is dropped rather than escaped, so a typed name cannot address a directory
     * or a file outside the maps directory — the one piece of input the editor takes is also the
     * one that could leave it.
     */
    private String mapName() {
        String typed = nameField.getText() == null ? "" : nameField.getText().trim();
        StringBuilder clean = new StringBuilder(typed.length());
        for (int index = 0; index < typed.length(); index++) {
            char character = typed.charAt(index);
            if (Character.isLetterOrDigit(character) || character == '-' || character == '_') {
                clean.append(character);
            }
        }
        return clean.length() == 0 ? "map" : clean.toString();
    }

    /** Where a save or a bake goes: the user's directory, or the workspace as a fallback. */
    private FileHandle file(String name, String suffix) {
        if (Gdx.files.isExternalStorageAvailable()) {
            return Gdx.files.external(SAVE_DIRECTORY + name + suffix);
        }
        return Gdx.files.local(FALLBACK_DIRECTORY + name + suffix);
    }

    /** Leaves for the menu, once: a second Escape must not stack a second menu. */
    private void close() {
        if (closing || disposed) {
            return;
        }
        closing = true;
        back.run();
    }

    @Override
    public void show() {
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(BACKGROUND.r, BACKGROUND.g, BACKGROUND.b, BACKGROUND.a);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        TerrainRenderer picture = terrain();
        if (picture != null) {
            picture.render(camera);
        }
        drawOverlay();

        stage.act(delta);
        stage.draw();
    }

    /**
     * The renderer for the current draft, rebuilt when the draft changed.
     *
     * <p>A draft mid-edit can fail to build — two blocks on one rectangle, a door id authored
     * twice — and that is reported rather than drawn: the picture keeps the last map that was a
     * map, and the status line says what the author has to fix.
     */
    private TerrainRenderer terrain() {
        if (!terrainDirty) {
            return terrain;
        }
        try {
            ArenaMap map = draft.build();
            if (terrain != null) {
                terrain.dispose();
            }
            terrain = new TerrainRenderer(map);
            terrainDirty = false;
            buildFailed = false;
        } catch (IllegalArgumentException invalid) {
            // Keep drawing the last good picture and keep the draft dirty, so the next edit tries
            // again. The reason is reported once rather than once per frame, or it would overwrite
            // everything else an author is reading.
            if (!buildFailed) {
                buildFailed = true;
                report("this draft does not build yet: " + invalid.getMessage());
            }
        }
        return terrain;
    }

    /** The reachability overlay and the placement ghost, both in world space. */
    private void drawOverlay() {
        boolean ghost = tool == Tool.PLACE && armed != null;
        if (pocket.isEmpty() && !ghost) {
            return;
        }
        markers.setProjectionMatrix(camera.combined());
        if (!pocket.isEmpty()) {
            markers.begin(ShapeRenderer.ShapeType.Filled);
            markers.setColor(UNREACHABLE_COLOR);
            for (MapReachability.Stance stance : pocket) {
                // A dot sitting on the surface rather than centred on the feet, so a sealed ledge
                // and a sealed floor read the same way.
                markers.circle(stance.x(), stance.feet() + STANCE_MARKER_RADIUS,
                    STANCE_MARKER_RADIUS);
            }
            markers.end();
        }
        if (ghost) {
            Vector3 pointer = worldAt(Gdx.input.getX(), Gdx.input.getY());
            Rect rect = MapPalette.tapRect(armed, pointer.x, pointer.y, draft.mirrorAxisX());
            // The ghost is where the next tap would land; on a touch device there is no hover, so
            // it sits at the last touch point, which is the point the next tap starts from.
            markers.begin(ShapeRenderer.ShapeType.Line);
            markers.setColor(GHOST_COLOR);
            markers.rect(rect.x(), rect.y(), rect.width(), rect.height());
            if (!MapPalette.isCentred(rect, draft.mirrorAxisX())) {
                Rect twin = rect.mirror(draft.mirrorAxisX());
                markers.rect(twin.x(), twin.y(), twin.width(), twin.height());
            }
            markers.end();
        }
    }

    /** A window point in world units, through the editor's own camera. */
    private Vector3 worldAt(int screenX, int screenY) {
        screenPoint.set(screenX, screenY, 0f);
        return camera.raw().unproject(screenPoint,
            0f, 0f, stage.getViewport().getScreenWidth(), stage.getViewport().getScreenHeight());
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
        camera.resize(width, height);
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
        if (terrain != null) {
            terrain.dispose();
        }
        markers.dispose();
        stage.dispose();
        skin.dispose();
    }

    /**
     * The canvas' own input: taps author, drags pan.
     *
     * <p>Registered after the stage, so a touch that lands on a button is the button's and a touch
     * anywhere else is the map's. A gesture is classified on release: a movement past
     * {@link #TAP_SLOP_PIXELS} was a pan and authors nothing, which is what makes one finger
     * enough for both without a modifier key.
     */
    private final class CanvasInput extends InputAdapter {

        private float downX;
        private float downY;
        private float lastX;
        private float lastY;
        private boolean down;
        private boolean panning;

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (pointer != 0) {
                return false;
            }
            down = true;
            panning = false;
            downX = lastX = screenX;
            downY = lastY = screenY;
            return true;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            if (!down || pointer != 0) {
                return false;
            }
            float dx = screenX - lastX;
            float dy = screenY - lastY;
            lastX = screenX;
            lastY = screenY;
            if (Math.abs(screenX - downX) > TAP_SLOP_PIXELS
                    || Math.abs(screenY - downY) > TAP_SLOP_PIXELS) {
                panning = true;
            }
            if (panning) {
                // The map follows the finger: a rightward drag moves the view left, and a downward
                // drag moves it up, because screen y grows downwards and world y upwards.
                float perPixelX = camera.viewportWidth() / Math.max(1, Gdx.graphics.getWidth());
                float perPixelY = camera.viewportHeight() / Math.max(1, Gdx.graphics.getHeight());
                camera.pan(-dx * perPixelX, dy * perPixelY);
            }
            return true;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            if (!down || pointer != 0) {
                return false;
            }
            down = false;
            if (!panning) {
                Vector3 world = worldAt((int) downX, (int) downY);
                tapAt(world.x, world.y);
            }
            return true;
        }

        @Override
        public boolean keyDown(int keycode) {
            if (keycode == Input.Keys.ESCAPE) {
                close();
                return true;
            }
            return false;
        }
    }
}
