package io.github.skystrike.shared.map;

import io.github.skystrike.shared.config.PlayerConfig;
import io.github.skystrike.shared.config.WorldConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The reachability gate: can a body get from a spawn to the rest of the map?
 *
 * <p>This is the one property of a map that no picture shows and no playtest of <i>this</i> map
 * proves for the next one. A stray block can seal a lane, and the map still loads, bakes, renders
 * and plays exactly as before right up to the moment a player walks into a wall that should not be
 * there. Map plan §7 therefore makes it a deterministic search over a map's walkable grid, run for
 * every shipped map, so a sealed map fails a check rather than a playtest.
 *
 * <p><b>What is searched.</b> The grid's columns are {@link #COLUMN_PITCH} units apart, and a
 * <i>stance</i> is a place a standing body fits with its feet on a surface: the shared standing box
 * ({@link PlayerConfig#WIDTH} by {@link PlayerConfig#STAND_HEIGHT}) must clear every block that
 * stops a body, and a support top must be there to stand on. Both halves read the map's movement
 * column — {@link BlockRules#movement(BlockKind)} through
 * {@link ArenaMap#movementBlockers(MovementRule)} — with one deliberate difference: a door is left
 * out of the solids <i>and</i> out of the supports, because the map is read in the state a player
 * can always put it in, which is open (map plan §11.3 leaves the access rule itself open, so the
 * gate does not guess at it either). A shut panel left in would be a wall and a floor at once: a
 * place to stand in the middle of the doorway a body has to walk through.
 *
 * <p><b>What a body may do.</b> Two manoeuvres, both from the M8 traversal contract and both
 * measured between stances. A <i>step</i> is a jump: at most {@code JUMP_SPEED² / (2 · -GRAVITY)} of
 * rise over at most the plan's {@value #MAX_JUMP_GAP}-unit running gap, costing no fuel. A <i>hop</i>
 * is a jetpack ascent: a rise the tank's {@link #FUEL_BUDGET} buys, with the horizontal reach that
 * ascent's flight time buys at {@link PlayerConfig#WALK_SPEED}. Dropping costs nothing and is a
 * step like any other, which is why a closure over steps alone is a meaningful half of the answer.
 *
 * <p>A manoeuvre also has to have somewhere to happen, so each one is checked through the body's own
 * space in two shapes, because a body leaves a ledge in two different ways: the rise-cross-descend
 * shape (rise beside the launch, cross at the higher of the two feet heights, come down at the
 * landing — how a body climbs a wall cap and steps off its far side), and the straight flight shape
 * (a sampled line from stance to stance — how a body steps off a ledge and drifts under an overhang
 * to land below it). A manoeuvre counts when either shape is clear. This is an approximation of
 * flight, not a simulation: the arc itself, a wall's corner and the drift a long fall buys are not
 * modelled separately, so a map whose only route threads one of those gaps reads as unreachable.
 * The cost of that conservatism is a line in a report a human reads; the cost of the opposite would
 * be a sealed map that ships.
 *
 * <p><b>What comes out.</b> A {@link Report}: per spawn, the stances it reaches on foot, the ones a
 * jetpack adds and the ones it cannot reach at all. {@link Report#spawnsConnected()} is the hard
 * requirement — every spawn reaches every other spawn, which is the plan's "every spawn reaches
 * every objective marker" with the map's own spawns as the objectives, since version 1 authors no
 * others. {@link Report#fullyReachable()} is the strict reading of §7, and whether a map that fails
 * <i>it</i> ships is §11.4's open decision rather than this class's.
 */
public final class MapReachability {

    /**
     * The x resolution of the walkable grid, in world units.
     *
     * <p>Coarse on purpose: map plan §7 asks for a coarse walkable grid, and 5 units is a sixth of
     * the body it stands up. It also sets the gate's blind spot — a passage whose <i>standable</i>
     * window is narrower than one pitch can be stepped over, because the grid samples columns rather
     * than measuring them. Every doorway this project authors is several times wider than that, and
     * {@code tools/scratch/arena_reachability_check.py} re-runs the shipped arena at half the pitch
     * to show the verdict is not an artifact of it.
     */
    public static final float COLUMN_PITCH = 5f;

    /**
     * The rise one jetpack ascent may buy: 40% of a tank, map plan §7's "40% fuel ascent".
     *
     * <p>A share of the tank rather than all of it, because the gate is about a route
     * <i>existing</i>: a map that needs a full tank to reach a platform is a map a player reaches it
     * on only when the tank happens to be full, which is a different claim. Fuel recharges on the
     * ground ({@link PlayerConfig#FUEL_RECHARGE_RATE}), so this is the endurance of one ascent, and
     * the closure below makes ascents repeatable after each landing.
     */
    public static final float FUEL_BUDGET = PlayerConfig.MAX_FUEL * 0.40f;

    /** The M8 running-jump gap: how far a body travels horizontally off one launch. */
    public static final float MAX_JUMP_GAP = 210f;

    /**
     * Slack for the "just about reaches" comparisons, matching {@code PlayerMotion}'s own touch
     * epsilon. A stance sits on a surface top that two routes through the arithmetic can land a hair
     * apart; without slack, one of them reads as outside the envelope and a legitimate route reads
     * as sealed. It is two orders of magnitude below the thinnest authored geometry.
     */
    private static final float EPSILON = 0.01f;

    /** The jump's apex: {@code JUMP_SPEED² / (2 · -GRAVITY)}, the M8 rise envelope. */
    private static final float MAX_JUMP_RISE = PlayerConfig.JUMP_SPEED * PlayerConfig.JUMP_SPEED
        / (2f * -PlayerConfig.GRAVITY);

    /** The jetpack's net vertical acceleration: the constant of an ascent's flight time. */
    private static final float JETPACK_ACCELERATION = PlayerConfig.JETPACK_THRUST
        * PlayerConfig.JETPACK_VERTICAL_SPLIT + PlayerConfig.GRAVITY;

    /** The furthest a hop can carry a body horizontally, used to bound the candidate scan. */
    private static final float MAX_HOP_REACH = PlayerConfig.WALK_SPEED * FUEL_BUDGET
        / PlayerConfig.FUEL_BURN_RATE;

    private MapReachability() {
    }

    /**
     * Searches {@code map} and reports what a body can reach from each of its spawns.
     *
     * <p>Deterministic: the grid, the candidate order, the queue order and every finding list are
     * ordered by column and then by height, so two runs and two machines report the same thing. A
     * map with no spawn is searched from nothing and reports nothing unreachable — the format does
     * not require a spawn, so that is a valid map rather than an error.
     */
    public static Report check(ArenaMap map) {
        Objects.requireNonNull(map, "map");
        return new Search(map).run();
    }

    /**
     * One place a standing body fits, with its feet on a surface: a grid column and a feet height.
     *
     * <p>Records compare by value and order by {@code x} then {@code feet}, so a finding list prints
     * the same way on every run.
     */
    public record Stance(float x, float feet) implements Comparable<Stance> {

        @Override
        public int compareTo(Stance other) {
            int byX = Float.compare(x, other.x);
            return byX != 0 ? byX : Float.compare(feet, other.feet);
        }
    }

    /**
     * What one spawn reaches.
     *
     * <p>{@code onFoot} is how many stances the step envelope alone reaches and {@code withFuel} how
     * many the full envelope does; {@code unreachable} is every stance this spawn cannot reach at
     * all, complete rather than sampled, because a caller that draws a sealed pocket — the editor's
     * overlay, the CLI's listing — has to be handed all of it.
     *
     * <p>There is deliberately no "is this stance escapable without fuel" column. The M8 contract
     * stated that property for a hand-written list of whole surfaces, where the arena's floor was
     * one node; on a per-stance grid every pocket between two walls answers it, so the shipped arena
     * would report hundreds of stances "trapped" on ground a player walks across, and a signal that
     * noisy is worse than none. What the gate answers is reachability, and the reachability it
     * answers is the question both the CLI gate and the editor's overlay need.
     */
    public record SpawnReport(
            int teamIndex,
            float spawnX,
            float spawnY,
            Stance start,
            int onFoot,
            int withFuel,
            List<Stance> unreachable) {

        public SpawnReport {
            Objects.requireNonNull(start, "start");
            unreachable = List.copyOf(unreachable);
        }
    }

    /**
     * The verdict for one map.
     *
     * <p>{@link #spawnsConnected()} is the requirement a map has to meet to be playable;
     * {@link #fullyReachable()} is the strict reading whose strictness is §11.4's open decision.
     * {@link #describe()} is the one-line form the CLI and the editor both log.
     */
    public record Report(
            float pitch,
            int columns,
            List<Stance> stances,
            List<SpawnReport> spawns,
            boolean spawnsConnected,
            List<Stance> unreachableFromEverySpawn) {

        public Report {
            stances = List.copyOf(stances);
            spawns = List.copyOf(spawns);
            unreachableFromEverySpawn = List.copyOf(unreachableFromEverySpawn);
        }

        /** How many stances the grid found: the denominator of every count in this report. */
        public int stanceCount() {
            return stances.size();
        }

        /**
         * True when every spawn reaches every other spawn within the envelope.
         *
         * <p>A map with no spawns, or with one, is connected: there is nothing it fails to reach.
         */
        public boolean spawnsConnected() {
            return spawnsConnected;
        }

        /**
         * True when every stance on the grid is reachable from every spawn — §7's strict reading.
         *
         * <p>Vacuously true for a map with no spawns, for the same reason.
         */
        public boolean fullyReachable() {
            return spawns.stream().allMatch(spawn -> spawn.unreachable().isEmpty());
        }

        /** One line naming the grid, the verdict and the counts, for a log or a CLI. */
        public String describe() {
            return "reachability: " + stances.size() + " stance(s) on " + columns + " columns at"
                + " pitch " + pitch + "; " + (spawnsConnected
                    ? "every spawn reaches every spawn"
                    : "SPAWNS DISCONNECTED")
                + "; " + unreachableFromEverySpawn.size() + " stance(s) reachable from no spawn";
        }
    }

    /**
     * One search over one map: the grid it built, then one closure per spawn.
     *
     * <p>A short-lived object rather than a set of static helpers, so the two passes that need the
     * grid — building the stances and walking the closures — share the one filtered movement column
     * they were both derived from.
     */
    private static final class Search {

        private final ArenaMap map;

        /** The movement column's SOLID rects with doors left out: what stops a body. */
        private final List<Rect> solids = new ArrayList<>();

        /** Those same rects plus the one-way slabs: what a body can stand on. */
        private final List<Rect> supports = new ArrayList<>();

        private final int columns;
        private final float[] columnX;

        /** Per column, the SOLID rects whose rectangle overlaps the standing body's x-band. */
        private final List<List<Rect>> columnSolids = new ArrayList<>();

        /** Every stance in grid order: by column, then by height within a column. */
        private final List<Stance> stances = new ArrayList<>();

        /** For column {@code i}, its stances run from {@code firstStance[i]} to the next entry. */
        private final int[] firstStance;

        /** Step targets per stance, computed on first use: the closures walk them repeatedly. */
        private int[][] footTargetCache;

        /** Hop targets per stance, computed on first use for the same reason. */
        private int[][] fuelTargetCache;

        Search(ArenaMap map) {
            this.map = map;
            collectGeometry();
            this.columns = (int) Math.ceil(WorldConfig.ARENA_WIDTH / COLUMN_PITCH);
            this.columnX = new float[columns];
            for (int column = 0; column < columns; column++) {
                this.columnX[column] = COLUMN_PITCH * (column + 0.5f);
            }
            for (int column = 0; column < columns; column++) {
                columnSolids.add(solidsOverlapping(column));
            }
            this.firstStance = new int[columns + 1];
            buildStances();
            this.footTargetCache = new int[stances.size()][];
            this.fuelTargetCache = new int[stances.size()][];
        }

        /**
         * Splits the map's one rule table into the two lists the gate reads.
         *
         * <p>Both are built from the authored blocks rather than from {@link ArenaMap}'s cached
         * views, for one reason: a door has to leave the SOLID view and stay out of the supports,
         * and a cached view cannot express that. Nothing here names a block kind —
         * {@link BlockRules#movement(BlockKind)} decides what stops a body and {@link MapBlock#door()}
         * decides which of those are openings.
         */
        private void collectGeometry() {
            for (MapBlock block : map.blocks()) {
                if (block.door().isDoor()) {
                    continue;
                }
                if (BlockRules.movement(block.kind()) == MovementRule.SOLID) {
                    solids.add(block.rect());
                }
                supports.add(block.rect());
            }
        }

        private List<Rect> solidsOverlapping(int column) {
            float left = columnX[column] - PlayerConfig.WIDTH / 2f;
            float right = left + PlayerConfig.WIDTH;
            List<Rect> overlapping = new ArrayList<>();
            for (Rect rect : solids) {
                if (rect.left() <= right && left <= rect.right()) {
                    overlapping.add(rect);
                }
            }
            return overlapping;
        }

        /**
         * The grid: for every column, every surface top a body could stand on there, kept when the
         * standing box fits and a support is really beneath it.
         *
         * <p>The support half mirrors {@link MapQueries#surfaceBelow(ArenaMap, float, float)}: the
         * highest support top at or below the feet plus the ground skin must be the feet themselves,
         * which is what keeps a stance from being invented in mid-air. Feet are clamped to
         * {@code ARENA_HEIGHT - STAND_HEIGHT} because {@code PlayerMotion} clamps the body there: a
         * surface above that ceiling is not a place a body can be, however well its box fits.
         */
        private void buildStances() {
            float highest = WorldConfig.ARENA_HEIGHT - PlayerConfig.STAND_HEIGHT;
            for (int column = 0; column < columns; column++) {
                firstStance[column] = stances.size();
                float x = columnX[column];
                List<Float> tops = new ArrayList<>();
                tops.add(0f);
                for (Rect rect : supports) {
                    if (rect.left() <= x && x <= rect.right() && rect.top() <= highest
                            && !tops.contains(rect.top())) {
                        tops.add(rect.top());
                    }
                }
                tops.sort(Float::compare);
                for (float feet : tops) {
                    if (surfaceBelow(column, feet + PlayerConfig.GROUND_SKIN) == feet
                            && fits(column, feet + EPSILON)) {
                        stances.add(new Stance(x, feet));
                    }
                }
            }
            firstStance[columns] = stances.size();
        }

        /** The highest support top at or below {@code y} in this column; the arena floor if none. */
        private float surfaceBelow(int column, float y) {
            float x = columnX[column];
            float best = 0f;
            for (Rect rect : supports) {
                if (x < rect.left() || x > rect.right()) {
                    continue;
                }
                if (rect.top() <= y && rect.top() > best) {
                    best = rect.top();
                }
            }
            return best;
        }

        /**
         * True when the standing body fits at this column with its feet at {@code feet}.
         *
         * <p>The body occupies {@code [feet, feet + STAND_HEIGHT]}, a solid covers
         * {@code [bottom, top]} and the shared box overlap counts a shared edge as an overlap, so
         * this solid blocks exactly the feet in {@code [bottom - STAND_HEIGHT - ε, top - ε]}. The
         * box is lifted by the epsilon before the test, so a body standing on a wall top is not read
         * as being inside the wall it stands on.
         */
        private boolean fits(int column, float feet) {
            for (Rect rect : columnSolids.get(column)) {
                if (feet >= rect.bottom() - PlayerConfig.STAND_HEIGHT - EPSILON
                        && feet <= rect.top() - EPSILON) {
                    return false;
                }
            }
            return true;
        }

        private int columnOf(int stance) {
            return Math.min(columns - 1, (int) (stances.get(stance).x() / COLUMN_PITCH));
        }

        /** The body's own column, clear from one feet height to another: a rise or a hover. */
        private boolean columnFree(int column, float feetFrom, float feetTo) {
            float low = Math.min(feetFrom, feetTo);
            float high = Math.max(feetFrom, feetTo);
            int steps = Math.max(1, (int) ((high - low) / COLUMN_PITCH) + 1);
            for (int step = 0; step <= steps; step++) {
                if (!fits(column, low + (high - low) * step / steps)) {
                    return false;
                }
            }
            return true;
        }

        /** The body crossing horizontally at one height, from one column to another. */
        private boolean crossingFree(float height, int from, int to) {
            for (int column = Math.min(from, to); column <= Math.max(from, to); column++) {
                if (!fits(column, height)) {
                    return false;
                }
            }
            return true;
        }

        /**
         * True when a body can leave one stance and arrive at another through free space.
         *
         * <p>Two shapes, either of which is enough; the class comment says what each one stands for.
         * The rise-cross-descend shape is tried first because it is the cheaper one and the common
         * case: a body climbs a wall cap by rising beside the wall, crossing at the cap's height and
         * stepping down onto it.
         */
        private boolean hopClear(int source, int target) {
            int fromColumn = columnOf(source);
            int toColumn = columnOf(target);
            Stance from = stances.get(source);
            Stance to = stances.get(target);
            float crossing = Math.max(from.feet(), to.feet());
            if (columnFree(fromColumn, from.feet(), crossing)
                    && crossingFree(crossing, fromColumn, toColumn)
                    && columnFree(toColumn, crossing, to.feet())) {
                return true;
            }
            return straightClear(fromColumn, from.feet(), toColumn, to.feet());
        }

        /**
         * True when the sampled straight line between two stances stays inside the body's space.
         *
         * <p>Sampled every {@link #COLUMN_PITCH} of travel, endpoints included, so a step between
         * neighbouring stances is tested at both of them and a long drift is tested 160-odd times.
         */
        private boolean straightClear(int fromColumn, float fromFeet, int toColumn, float toFeet) {
            float span = Math.abs(columnX[toColumn] - columnX[fromColumn]);
            float rise = toFeet - fromFeet;
            int steps = Math.max(1, (int) (Math.sqrt(span * span + rise * rise) / COLUMN_PITCH));
            for (int step = 0; step <= steps; step++) {
                float progress = step / (float) steps;
                int column = Math.min(columns - 1,
                    (int) (fromColumn + (toColumn - fromColumn) * progress + 0.5f));
                if (!fits(column, fromFeet + rise * progress)) {
                    return false;
                }
            }
            return true;
        }

        /**
         * The flight time of the jetpack ascent that adds {@code rise}, inverted from the ascent's
         * own closed form: with the jump's impulse already spent, the jetpack's net acceleration
         * carries the body from the launch to the landing. This is the formula map plan §7 states
         * the fuel envelope in, so the gate and the plan quote the same arithmetic.
         */
        private static float jetpackSecondsForRise(float rise) {
            float discriminant = PlayerConfig.JUMP_SPEED * PlayerConfig.JUMP_SPEED
                + 2f * JETPACK_ACCELERATION * rise;
            return ((float) Math.sqrt(discriminant) - PlayerConfig.JUMP_SPEED)
                / JETPACK_ACCELERATION;
        }

        /**
         * The stance indices within {@code horizontalLimit} of {@code source}, in column order.
         *
         * <p>Ordered, not a set: the closures' queue order is part of the determinism this class
         * promises, and two runs of a gate must not disagree about which stances a report samples.
         */
        private int[] candidates(int source, float horizontalLimit) {
            Stance stance = stances.get(source);
            int span = (int) Math.ceil(horizontalLimit / COLUMN_PITCH);
            int from = Math.max(0, columnOf(source) - span);
            int to = Math.min(columns - 1, columnOf(source) + span);
            List<Integer> result = new ArrayList<>();
            for (int column = from; column <= to; column++) {
                if (Math.abs(columnX[column] - stance.x()) > horizontalLimit + EPSILON) {
                    continue;
                }
                for (int index = firstStance[column]; index < firstStance[column + 1]; index++) {
                    if (index != source) {
                        result.add(index);
                    }
                }
            }
            int[] indices = new int[result.size()];
            for (int index = 0; index < result.size(); index++) {
                indices[index] = result.get(index);
            }
            return indices;
        }

        private boolean footEdge(int source, int target) {
            Stance from = stances.get(source);
            Stance to = stances.get(target);
            return to.feet() - from.feet() <= MAX_JUMP_RISE + EPSILON && hopClear(source, target);
        }

        private boolean fuelEdge(int source, int target) {
            Stance launch = stances.get(source);
            Stance to = stances.get(target);
            float rise = to.feet() - launch.feet();
            if (rise <= 0f) {
                return false;
            }
            float seconds = jetpackSecondsForRise(rise);
            if (seconds * PlayerConfig.FUEL_BURN_RATE > FUEL_BUDGET + EPSILON) {
                return false;
            }
            if (Math.abs(to.x() - launch.x()) > PlayerConfig.WALK_SPEED * seconds + EPSILON) {
                return false;
            }
            return hopClear(source, target);
        }

        private int[] footTargets(int source) {
            if (footTargetCache[source] == null) {
                List<Integer> targets = new ArrayList<>();
                for (int target : candidates(source, MAX_JUMP_GAP)) {
                    if (footEdge(source, target)) {
                        targets.add(target);
                    }
                }
                footTargetCache[source] = toArray(targets);
            }
            return footTargetCache[source];
        }

        private int[] fuelTargets(int source) {
            if (fuelTargetCache[source] == null) {
                List<Integer> targets = new ArrayList<>();
                for (int target : candidates(source, MAX_HOP_REACH)) {
                    if (fuelEdge(source, target)) {
                        targets.add(target);
                    }
                }
                fuelTargetCache[source] = toArray(targets);
            }
            return fuelTargetCache[source];
        }

        private static int[] toArray(List<Integer> values) {
            int[] array = new int[values.size()];
            for (int index = 0; index < values.size(); index++) {
                array[index] = values.get(index);
            }
            return array;
        }

        /**
         * The stances reachable by stepping alone, in one queue order: the M8 envelope's no-fuel
         * half. Walking a surface, hopping up to the jump's rise and dropping any distance are all
         * steps, because dropping costs nothing and is what makes the escape question meaningful.
         */
        private boolean[] footClosure(int start) {
            boolean[] reached = new boolean[stances.size()];
            ArrayDeque<Integer> pending = new ArrayDeque<>();
            reached[start] = true;
            pending.add(start);
            while (!pending.isEmpty()) {
                for (int target : footTargets(pending.poll())) {
                    if (!reached[target]) {
                        reached[target] = true;
                        pending.add(target);
                    }
                }
            }
            return reached;
        }

        /**
         * The stances reachable when a jetpack ascent is allowed from any stance the body has landed
         * on: the full envelope. Fuel recharges on the ground, so the budget is the endurance of one
         * ascent and this closure is what makes ascents repeatable.
         */
        private boolean[] anyClosure(int start) {
            boolean[] reached = new boolean[stances.size()];
            ArrayDeque<Integer> pending = new ArrayDeque<>();
            reached[start] = true;
            pending.add(start);
            while (!pending.isEmpty()) {
                int source = pending.poll();
                for (int target : footTargets(source)) {
                    if (!reached[target]) {
                        reached[target] = true;
                        pending.add(target);
                    }
                }
                for (int target : fuelTargets(source)) {
                    if (!reached[target]) {
                        reached[target] = true;
                        pending.add(target);
                    }
                }
            }
            return reached;
        }

        /**
         * The stance a spawn starts from: the one nearest the spawn point.
         *
         * <p>Nearest rather than "the one under the spawn", because the format only refuses a spawn
         * inside a block: a spawn can sit over a spot too narrow to stand on, and the honest reading
         * of that is the closest ground the body can actually occupy. Distance is compared
         * horizontally first and by feet height second, ties going to the earlier stance, so the
         * choice is stable. A map whose spawn cannot be stood on at its own column is therefore not
         * an error; it is a spawn on a ledge, and the search answers from where a body would land.
         */
        private int startStance(SpawnPoint spawn) {
            int best = 0;
            for (int index = 1; index < stances.size(); index++) {
                if (nearer(spawn, stances.get(index), stances.get(best))) {
                    best = index;
                }
            }
            return best;
        }

        private static boolean nearer(SpawnPoint spawn, Stance candidate, Stance current) {
            float candidateDx = Math.abs(candidate.x() - spawn.x());
            float currentDx = Math.abs(current.x() - spawn.x());
            if (candidateDx != currentDx) {
                return candidateDx < currentDx;
            }
            return Math.abs(candidate.feet() - spawn.y()) < Math.abs(current.feet() - spawn.y());
        }

        Report run() {
            List<SpawnPoint> spawns = map.spawns();
            if (spawns.isEmpty()) {
                return new Report(COLUMN_PITCH, columns, stances, List.of(), true, List.of());
            }
            List<SpawnReport> reports = new ArrayList<>();
            List<boolean[]> closures = new ArrayList<>();
            int[] starts = new int[spawns.size()];
            for (int index = 0; index < spawns.size(); index++) {
                starts[index] = startStance(spawns.get(index));
            }

            boolean[] reachableFromSomeone = new boolean[stances.size()];
            boolean connected = true;
            for (int index = 0; index < spawns.size(); index++) {
                SpawnPoint spawn = spawns.get(index);
                int start = starts[index];
                boolean[] onFoot = footClosure(start);
                boolean[] withFuel = anyClosure(start);
                closures.add(withFuel);
                for (int stance = 0; stance < stances.size(); stance++) {
                    reachableFromSomeone[stance] |= withFuel[stance];
                }

                List<Stance> unreachable = new ArrayList<>();
                int onFootCount = 0;
                for (int stance = 0; stance < stances.size(); stance++) {
                    if (onFoot[stance]) {
                        onFootCount++;
                    }
                    if (!withFuel[stance]) {
                        unreachable.add(stances.get(stance));
                    }
                }
                reports.add(new SpawnReport(spawn.teamIndex(), spawn.x(), spawn.y(),
                    stances.get(start), onFootCount, stances.size() - unreachable.size(),
                    unreachable));
            }
            for (int from = 0; from < spawns.size(); from++) {
                for (int to = 0; to < spawns.size(); to++) {
                    if (from != to && !closures.get(from)[starts[to]]) {
                        connected = false;
                    }
                }
            }
            List<Stance> unreachableFromEvery = new ArrayList<>();
            for (int stance = 0; stance < stances.size(); stance++) {
                if (!reachableFromSomeone[stance]) {
                    unreachableFromEvery.add(stances.get(stance));
                }
            }
            return new Report(COLUMN_PITCH, columns, stances, reports, connected,
                unreachableFromEvery);
        }
    }
}
