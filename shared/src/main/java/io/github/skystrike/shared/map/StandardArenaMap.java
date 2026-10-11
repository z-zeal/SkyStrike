package io.github.skystrike.shared.map;

/**
 * Canonical source text for the arena that shipped before map files existed.
 *
 * <p>This remains embedded only until the shipped-maps milestone moves it verbatim to
 * {@code assets/maps/arena.map}. Keeping it as text now makes {@link ArenaMap#standard()} prove
 * the real parser path without making shared depend on a desktop or LibGDX file API.
 *
 * <p>The last three lines are milestone 3's fixture range: one mirrored one-way platform high over
 * the centre carrying a pane and a wooden crate. The arena is deliberately not wall-only any more,
 * because with no map file and no editor (milestone 5) a running game could otherwise never meet
 * the kinds that milestone built — stand on the platform and drop through it, shoot the pane and
 * watch it shatter, chip the crate and break it. Everything else is the pre-existing arena, and
 * the range sits far above every lane the game and the older tests use.
 */
final class StandardArenaMap {

    static final String TEXT = String.join("\n",
        "skystrike-map 1",
        "mirror 1500",
        "spawn 0 240 180",
        "spawn 1 2760 180",
        "centred wall stone 0 0 3000 100",
        "mirrored wall stone 0 100 40 1860",
        "centred wall stone 0 1960 3000 40",
        "mirrored wall stone 120 200 120 18",
        "mirrored wall stone 240 250 120 18",
        "mirrored wall stone 360 300 120 18",
        "mirrored wall stone 480 350 120 18",
        "mirrored wall stone 600 400 120 18",
        "mirrored wall stone 820 500 250 18",
        "mirrored wall stone 820 100 70 120",
        "mirrored wall stone 820 220 70 16",
        "mirrored wall stone 890 100 70 160",
        "mirrored wall stone 890 260 70 16",
        "mirrored wall stone 1010 100 24 260",
        "mirrored wall stone 1070 100 22 170",
        "mirrored wall stone 1120 280 75 20",
        "centred wall stone 1285 280 430 20",
        "mirrored wall stone 1120 300 24 60",
        "mirrored wall stone 1120 410 24 450",
        "mirrored wall stone 1184 300 16 130",
        "centred wall stone 1180 620 640 20",
        "mirrored wall stone 1195 192 90 18",
        "mirrored wall stone 1120 190 75 14",
        "mirrored wall stone 1285 190 15 14",
        "mirrored wall stone 1000 342 120 18",
        "mirrored wall stone 1100 520 120 18",
        "mirrored wall stone 1220 400 100 18",
        "centred wall stone 1380 760 240 18",
        "mirrored wall stone 1300 700 100 16",
        "centred wall stone 1380 1000 240 18",
        "mirrored wall stone 760 900 240 18",
        "mirrored platform wood 1620 1300 220 18",
        "mirrored glass glass 1660 1318 20 180",
        "mirrored breakable wood 1740 1318 60 60 durability=0.4",
        "");

    private StandardArenaMap() {
    }
}
