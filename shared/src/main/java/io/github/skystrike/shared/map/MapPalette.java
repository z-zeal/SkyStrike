package io.github.skystrike.shared.map;

import io.github.skystrike.shared.config.WorldConfig;
import java.util.List;
import java.util.Objects;

/**
 * The authoring presets the editor's palette offers, and the geometry a tap authors with one.
 *
 * <p>This is the editor's rule book rather than its view: what a palette entry <i>is</i> (a kind, a
 * material and a default size) and where a tap puts it. Keeping it in the map model package is
 * deliberate. Map plan §9's gate is that no consumer names a block kind — behaviour has to come
 * from {@link BlockRules} — and the editor is an authoring surface rather than a consumer, so its
 * palette has to name kinds; the two ways to allow that are an exemption carved out of the gate for
 * a client file, or the table living where a kind may be named anyway. This is the second, and it
 * is the smaller change: {@code shared}'s own tests pin the palette with the rest of the model, and
 * the gate stays exactly as tight for every other file in the project.
 *
 * <p>{@link Preset#door()} is what keeps the <i>screen</i> free of kinds: the editor asks a preset
 * whether it needs a door id, instead of comparing its kind against one. A preset cannot disagree
 * with itself about that, because the record refuses the mismatch where kinds may be named.
 *
 * <p>Sizes match the geometry the shipped arena authors, so a palette-authored map has the same
 * proportions as the one the game ships: a 220 × 18 slab, a 20 × 180 pane, a 36 × 170 door and a
 * 60 × 60 crate are the fixtures' own numbers.
 */
public final class MapPalette {

    /**
     * The grid a tap snaps to, in world units: {@link MapReachability#COLUMN_PITCH}, the same
     * resolution reachability is searched at.
     *
     * <p>Five units is small enough that a tap lands where the author pointed and large enough that
     * two taps meant to abut do abut — a block placed at 237.5 and one at 240.2 would otherwise
     * leave a 2.7-unit slit a bullet and a body can both pass, which is exactly the sort of seam no
     * screenshot shows.
     */
    public static final float TAP_SNAP = MapReachability.COLUMN_PITCH;

    /**
     * One palette entry.
     *
     * @param label   what the button says
     * @param kind    the structural kind the preset authors
     * @param material what the block is made of, i.e. which {@link MaterialTable} row gives it its
     *                 colours, its durability and its impact effect
     * @param width   authored width in world units
     * @param height  authored height in world units
     * @param door    whether this preset needs a door id from the caller; true exactly for a door,
     *                enforced here so the screen can ask without naming a kind
     */
    public record Preset(
            String label,
            BlockKind kind,
            Material material,
            float width,
            float height,
            boolean door) {

        public Preset {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(material, "material");
            if (!(width > 0f) || !(height > 0f)) {
                throw new IllegalArgumentException(
                    "preset size must be positive: " + width + "x" + height);
            }
            if (door != (kind == BlockKind.DOOR)) {
                throw new IllegalArgumentException(
                    "a preset is a door exactly when its kind is: " + label);
            }
        }

        /**
         * The block a tap authors on {@code rect}.
         *
         * <p>The multiplier is {@link MapBlock#DEFAULT_DURABILITY_MULTIPLIER}: a palette that
         * quietly made one crate brittler than another would be taking a game-balance decision by
         * accident. A file that wants a different multiplier says so in its text, which is where
         * that decision belongs.
         *
         * @param spec {@link DoorSpec#NONE} for everything but a {@link #door()} preset, which needs
         *             the id the caller allotted; {@link MapBlock} refuses the mismatch either way
         */
        public MapBlock block(Rect rect, DoorSpec spec) {
            return new MapBlock(
                kind, material, rect, MapBlock.DEFAULT_DURABILITY_MULTIPLIER, spec);
        }
    }

    private static final List<Preset> PRESETS = List.of(
        new Preset("Ledge", BlockKind.WALL, Material.STONE, 120f, 18f, false),
        new Preset("Pillar", BlockKind.WALL, Material.STONE, 24f, 260f, false),
        new Preset("Platform", BlockKind.PLATFORM, Material.WOOD, 220f, 18f, false),
        new Preset("Pane", BlockKind.GLASS, Material.GLASS, 20f, 180f, false),
        new Preset("Crate", BlockKind.BREAKABLE, Material.WOOD, 60f, 60f, false),
        new Preset("Door", BlockKind.DOOR, Material.METAL, 36f, 170f, true));

    private MapPalette() {
    }

    /** Every palette entry, in the order the editor shows them. Immutable. */
    public static List<Preset> presets() {
        return PRESETS;
    }

    /**
     * The rectangle a tap at {@code (tapX, tapY)} authors with {@code preset}.
     *
     * <p>Three rules, in order. The centre snaps to {@link #TAP_SNAP}, so neighbouring taps line up.
     * The rectangle is then pushed inside the arena, because a block outside
     * {@link WorldConfig#ARENA_WIDTH} × {@link WorldConfig#ARENA_HEIGHT} is a load error the author
     * would meet at save time instead of at the tap. And a rectangle that would straddle the mirror
     * axis without being symmetric about it is re-centred on the axis: {@link MapDraft} can hold
     * only a mirrored pair or a self-reflective block, so a tap near the middle of the map has to
     * become the centred block the author is plainly asking for rather than a refusal. A
     * self-reflective rectangle is returned untouched, and so is one clear of the axis.
     */
    public static Rect tapRect(Preset preset, float tapX, float tapY, float mirrorAxisX) {
        Objects.requireNonNull(preset, "preset");
        float centreX = Math.round(tapX / TAP_SNAP) * TAP_SNAP;
        float centreY = Math.round(tapY / TAP_SNAP) * TAP_SNAP;
        Rect rect = centredRect(preset, centreX, centreY);
        rect = translateIntoArena(rect);
        if (rect.left() < mirrorAxisX && rect.right() > mirrorAxisX
                && !isCentred(rect, mirrorAxisX)) {
            rect = centredRect(preset, mirrorAxisX, rect.centerY());
        }
        return rect;
    }

    /** True when {@code rect} is its own reflection, i.e. a block {@code centred} may author. */
    public static boolean isCentred(Rect rect, float mirrorAxisX) {
        Objects.requireNonNull(rect, "rect");
        return rect.mirror(mirrorAxisX).equals(rect);
    }

    /**
     * The first door id no block in {@code blocks} uses.
     *
     * <p>The caller allots it to the block it is about to author; a mirrored placement then authors
     * the twin as {@link DoorSpec#reflection()} does, one id higher, which is why the caller must
     * allot the id <i>before</i> the verb runs and not read it back afterwards.
     */
    public static int nextDoorId(Iterable<MapBlock> blocks) {
        Objects.requireNonNull(blocks, "blocks");
        int next = 1;
        for (MapBlock block : blocks) {
            if (block.door().isDoor()) {
                next = Math.max(next, block.door().id() + 1);
            }
        }
        return next;
    }

    private static Rect centredRect(Preset preset, float centreX, float centreY) {
        return new Rect(
            centreX - preset.width() / 2f,
            centreY - preset.height() / 2f,
            preset.width(),
            preset.height());
    }

    /**
     * Shifts a rectangle inside the arena without resizing it: an author tapping the very edge
     * means "against the wall", not "half a block off the map".
     */
    private static Rect translateIntoArena(Rect rect) {
        float dx = 0f;
        float dy = 0f;
        if (rect.left() < 0f) {
            dx = -rect.left();
        } else if (rect.right() > WorldConfig.ARENA_WIDTH) {
            dx = WorldConfig.ARENA_WIDTH - rect.right();
        }
        if (rect.bottom() < 0f) {
            dy = -rect.bottom();
        } else if (rect.top() > WorldConfig.ARENA_HEIGHT) {
            dy = WorldConfig.ARENA_HEIGHT - rect.top();
        }
        return dx == 0f && dy == 0f ? rect : rect.translated(dx, dy);
    }
}
