package io.github.skystrike.shared.map;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A map being authored: the mutable block list behind the editor, and the shape a raw edit has to
 * pass through to become a map.
 *
 * <p>A draft holds <b>authored lines</b>, not resolved blocks. Every entry is one
 * {@link ArenaMap.Builder#mirrored(MapBlock) mirrored} or {@link ArenaMap.Builder#centred(MapBlock)
 * centred} verb, which is what makes symmetry structural rather than something a save has to check:
 * there is no way to hold a block without its twin, and deleting either twin deletes the verb. The
 * editor's three touch controls — place, mirror, delete — are these methods and nothing else.
 *
 * <p>A draft is permissive and {@link #build()} is strict, deliberately. Mid-edit a draft can be
 * inconsistently shaped (two blocks occupying one rectangle, a door id authored twice) and the
 * editor has to be able to hold that state for the frame or two before the author fixes it. Every
 * invariant lives in {@link ArenaMap.Builder}, so there is exactly one statement of what a map is:
 * {@code build()} is where a draft becomes one, and it throws exactly what the loader would.
 *
 * <p>{@link #isDirty()} answers "would saving this change the map?" by comparing the map's
 * {@link ArenaMap#mapHash()} with the one it was loaded from — the same identity the SDF header and
 * the join gate use, so a draft cannot be dirty in one sense and clean in another.
 */
public final class MapDraft {

    /** One authored line: a block and whether the map also authors its reflection. */
    private record Entry(MapBlock block, boolean mirrored) {
    }

    private final List<Entry> entries = new ArrayList<>();
    private final List<SpawnPoint> spawns = new ArrayList<>();
    private final float mirrorAxisX;
    private final ArenaMap baseline;

    private MapDraft(float mirrorAxisX, ArenaMap baseline) {
        this.mirrorAxisX = mirrorAxisX;
        this.baseline = baseline;
    }

    /** An empty draft around a mirror axis, for a map being authored from nothing. */
    public static MapDraft empty(float mirrorAxisX) {
        // The builder is the only place that validates an axis, so validate by asking it: a draft
        // that cannot be built is not a draft worth editing.
        ArenaMap.builder(mirrorAxisX);
        return new MapDraft(mirrorAxisX, null);
    }

    /**
     * A draft of {@code map}, ready to edit: every authored block becomes the verb the canonical
     * writer would have used for it, and every spawn is kept in order.
     *
     * <p>Reconstructing the verbs from the block list rather than from the text keeps the round trip
     * honest for a map that was never a file — {@link ArenaMap#standard()} is embedded text today —
     * and it is the same pairing rule the writer applies: a block whose reflection is present was
     * authored {@code mirrored}, and a block that is its own reflection was authored {@code centred}.
     */
    public static MapDraft of(ArenaMap map) {
        Objects.requireNonNull(map, "map");
        MapDraft draft = new MapDraft(map.mirrorAxisX(), map);
        List<MapBlock> remaining = new ArrayList<>(map.blocks());
        for (MapBlock block : map.blocks()) {
            if (!remaining.remove(block)) {
                continue;
            }
            if (block.rect().mirror(draft.mirrorAxisX).equals(block.rect())) {
                draft.entries.add(new Entry(block, false));
                continue;
            }
            MapBlock reflection = block.mirror(draft.mirrorAxisX);
            if (remaining.remove(reflection)) {
                draft.entries.add(new Entry(block, true));
            } else {
                // An asymmetric map cannot exist as an ArenaMap in the first place: the only map
                // that reaches here was derived by ArenaMap.without, and a draft of a match's live
                // map is not a draft of the map. Say so rather than silently dropping a block.
                throw new IllegalArgumentException(
                    "map is not symmetric; it is not a draft: " + block.rect());
            }
        }
        for (SpawnPoint spawn : map.spawns()) {
            draft.spawns.add(spawn);
        }
        return draft;
    }

    /** The axis every mirrored entry reflects about. */
    public float mirrorAxisX() {
        return mirrorAxisX;
    }

    /**
     * The map this draft was loaded from, or {@code null} when it was authored from nothing.
     *
     * <p>The editor uses it for the one question a draft cannot answer about itself: has this been
     * edited since it was opened.
     */
    public ArenaMap baseline() {
        return baseline;
    }

    /**
     * Adds {@code block} and its reflection — the editor's first touch gesture.
     *
     * <p>Validated here rather than at {@link #build()} because this one has no mid-edit excuse: a
     * block straddling the axis is not a pair and never becomes one, so the caller is told at the
     * gesture instead of at the save.
     */
    public MapDraft mirrored(MapBlock block) {
        Objects.requireNonNull(block, "block");
        if (block.mirror(mirrorAxisX).equals(block)) {
            throw new IllegalArgumentException(
                "block straddles the mirror axis, use centred(): " + block.rect());
        }
        entries.add(new Entry(block, true));
        return this;
    }

    /** Adds {@code block} as its own reflection, failing loudly when it is not. */
    public MapDraft centred(MapBlock block) {
        Objects.requireNonNull(block, "block");
        if (!block.rect().mirror(mirrorAxisX).equals(block.rect())) {
            throw new IllegalArgumentException(
                "block is not symmetric about x=" + mirrorAxisX + ": " + block.rect());
        }
        entries.add(new Entry(block, false));
        return this;
    }

    /** Adds a spawn in authored order. */
    public MapDraft spawn(SpawnPoint spawn) {
        spawns.add(Objects.requireNonNull(spawn, "spawn"));
        return this;
    }

    /**
     * Deletes the authored line that owns {@code block} — which deletes its twin with it.
     *
     * <p>A match between the target and a mirrored entry's <i>reflection</i> counts too, because that
     * is what the editor's delete gesture hands over: the block the author tapped, which may be the
     * one the builder derived. A target no line owns — a block from another map, or one already
     * deleted — removes nothing and answers {@code false}, so a stale gesture is visible rather than
     * silently harmless.
     */
    public boolean remove(MapBlock block) {
        Objects.requireNonNull(block, "block");
        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            if (entry.block().equals(block)
                    || (entry.mirrored() && entry.block().mirror(mirrorAxisX).equals(block))) {
                entries.remove(index);
                return true;
            }
        }
        return false;
    }

    /** Deletes a spawn, answering whether it was there. */
    public boolean removeSpawn(SpawnPoint spawn) {
        return spawns.remove(Objects.requireNonNull(spawn, "spawn"));
    }

    /** Removes every authored block, keeping the spawns and the axis. */
    public MapDraft clear() {
        entries.clear();
        return this;
    }

    /**
     * The blocks this draft would build, in authored order: each entry's block followed by its
     * reflection for a mirrored one.
     *
     * <p>Derived, not stored: the editor draws from this list and measures its own edits against it,
     * and nothing may hand back a block the draft no longer owns.
     */
    public List<MapBlock> blocks() {
        List<MapBlock> blocks = new ArrayList<>(entries.size() * 2);
        for (Entry entry : entries) {
            blocks.add(entry.block());
            if (entry.mirrored()) {
                blocks.add(entry.block().mirror(mirrorAxisX));
            }
        }
        return List.copyOf(blocks);
    }

    /** How many authored lines the draft holds; a mirrored line is two blocks. */
    public int authoredLineCount() {
        return entries.size();
    }

    /** The spawns in authored order. */
    public List<SpawnPoint> spawns() {
        return List.copyOf(spawns);
    }

    /**
     * Builds the map, enforcing every authored invariant in one place.
     *
     * <p>This is where a draft becomes a map, and where it fails with exactly the message the parser
     * would have used: the same builder backs both paths, so the in-game editor and
     * {@link ArenaMapFormat} cannot disagree about what a valid map is.
     */
    public ArenaMap build() {
        ArenaMap.Builder builder = ArenaMap.builder(mirrorAxisX);
        for (Entry entry : entries) {
            if (entry.mirrored()) {
                builder.mirrored(entry.block());
            } else {
                builder.centred(entry.block());
            }
        }
        for (SpawnPoint spawn : spawns) {
            builder.spawn(spawn);
        }
        return builder.build();
    }

    /**
     * The draft as canonical version-1 text, exactly what {@link ArenaMapFormat} would save.
     *
     * <p>The editor's save button and the CLI's {@code bake} both go through this, so a map authored
     * on a phone and a map saved on a desktop are the same bytes.
     */
    public String toText() {
        return ArenaMapFormat.write(build());
    }

    /**
     * Whether saving this draft would change the map it was loaded from.
     *
     * <p>Compared by {@link ArenaMap#mapHash()}, so an entry that was added and then removed is not
     * an edit, and a draft authored from nothing is dirty as soon as it holds anything. A draft with
     * no baseline is never clean: there is nothing for it to be equal to.
     */
    public boolean isDirty() {
        if (baseline == null) {
            return true;
        }
        return !Arrays.equals(build().mapHash(), baseline.mapHash());
    }
}
