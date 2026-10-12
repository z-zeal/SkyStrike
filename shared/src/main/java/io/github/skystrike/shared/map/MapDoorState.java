package io.github.skystrike.shared.map;

import io.github.skystrike.shared.config.DoorConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The runtime state of one map's doors (plan D3, §4), and the state machine that moves it.
 *
 * <p>An {@link ArenaMap} says where a door is, what id names it and who may open it. This says
 * which doors are in the way. It is the only place that answers that question, and every consumer
 * that cares reads {@link #blocks(MapBlock)} — collision, sight and bullets through
 * {@link MapDamageState#liveMap()}, which leaves an open door out — while the picture reads
 * {@link #swingFraction(MapBlock)}. Collision can therefore never disagree with the drawing,
 * because both come from the state below and the pose is derived from it.
 *
 * <p><b>The machine is</b> {@code CLOSED → OPENING → OPEN → SHUTTING → CLOSED}, and only two things
 * move it:
 *
 * <ul>
 *   <li>A <b>use request</b> ({@link #requestUse}) toggles the ends. A shut door starts opening or
 *       is refused; an open door starts shutting or is refused; a door that is already moving
 *       ignores the press. The authored {@link DoorSpec#allows(int)} rule decides who may open a
 *       door and applies to <i>opening</i> only — anyone within reach may shut one that is standing
 *       open, because leaving a door open is not a privilege the plan grants.</li>
 *   <li><b>Time</b> ({@link #advance}, authority only) finishes a swing and, after the hold,
 *       shuts a door nobody is standing in.</li>
 * </ul>
 *
 * <p><b>A swing is refused, never forced.</b> A request whose swing region is occupied is refused
 * ({@link Outcome#REFUSED_BLOCKED}), and a shutting door that reaches the end of its swing with
 * something in the region swings back open rather than closing on it. Because a door blocks from
 * the moment it starts shutting (see {@link DoorState#blocks()}), a body cannot get into the region
 * mid-swing in the first place; the end-of-swing check is the backstop for what can still arrive
 * there — a thrown object, a dropped-in device. The question "is something in the way" is the
 * caller's ({@link Occupancy}): the server asks with the bodies and thrown objects it simulates,
 * and a client predicting its own press asks with itself.
 *
 * <p><b>Time is the caller's clock, in ticks.</b> The server drives {@link #advance} with
 * {@code SimulationClock.tick()}; a client sets {@link #setClock} from a per-frame tick counter at
 * the server's rate, so a swing it draws lasts the same wall-clock time as the one the authority
 * simulates. No wall-clock time crosses the wire: the packet carries a tick, and the pose is
 * derived from whichever clock is local.
 *
 * <p><b>A mirror applies states, never time.</b> {@link #advance} is authority-only on purpose. A
 * client that decided by itself that a swing had finished, or that a shut door should re-open,
 * would be able to disagree with the server about a doorway — and the disagreement would be
 * invisible until somebody walked into it. So a client's copy changes for exactly two reasons: an
 * authoritative record ({@link #applyRemote}), or its own optimistic prediction of its own press
 * ({@link #requestUse}, reconciled the moment the authority answers).
 */
public final class MapDoorState {

    /**
     * The caller's answer to "is anything standing where this door's panel will sweep".
     *
     * <p>A callback rather than a list of rectangles because the server's occupants are the live
     * player and thrown-object lists it is already holding and a client's is usually one body:
     * neither should have to build a collection per door per tick to be asked.
     */
    @FunctionalInterface
    public interface Occupancy {

        /** True when anything occupies {@code swingRegion}, the panel's cell plus its clearance. */
        boolean occupied(Rect swingRegion);
    }

    /** An {@link Occupancy} that never blocks: for a state nobody is simulating but time. */
    public static final Occupancy CLEAR = swingRegion -> false;

    /** What a use request did, so the caller can tell a swing from a refusal. */
    public enum Outcome {

        /** The door began swinging open. */
        OPENING,

        /** The door began swinging shut. */
        SHUTTING,

        /** A door that is already moving ignores the press, and both sides ignore it alike. */
        IGNORED_MOVING,

        /** The door answers to another team. */
        REFUSED_ACCESS,

        /** Something stands in the doorway, so the panel would swing into it. */
        REFUSED_BLOCKED;

        /** True when nothing moved, which is the answer the caller has to report back. */
        public boolean refused() {
            return this == REFUSED_ACCESS || this == REFUSED_BLOCKED;
        }
    }

    private final List<MapBlock> doors;
    private final Map<Integer, Integer> indexById;
    private final DoorState[] states;
    /**
     * When each door entered its current state, in this state's own clock. The pose is derived from
     * it, and it is anchored locally rather than taken from the wire — a swing then lasts its whole
     * duration on the client that is drawing it, instead of however much of it the network ate.
     */
    private final long[] sinceTick;
    /**
     * The authority's tick for the last record applied to each door. Kept apart from
     * {@link #sinceTick} because the two are different clocks, and used to drop a stale record: the
     * join baseline and the live stream are two paths to the same door, and an old baseline applied
     * after a newer live record would resurrect a state nothing will ever correct.
     */
    private final long[] recordTick;
    private long nowTick;
    /** Bumped on every state change, so a derived live map can tell that it is out of date. */
    private long revision;

    private MapDoorState(ArenaMap authoredMap) {
        List<MapBlock> found = new ArrayList<>();
        for (MapBlock block : authoredMap.blocks()) {
            if (block.door().isDoor()) {
                found.add(block);
            }
        }
        this.doors = List.copyOf(found);
        this.indexById = new HashMap<>(this.doors.size() * 2);
        this.states = new DoorState[this.doors.size()];
        this.sinceTick = new long[this.doors.size()];
        this.recordTick = new long[this.doors.size()];
        for (int index = 0; index < this.doors.size(); index++) {
            this.indexById.put(this.doors.get(index).door().id(), index);
            this.states[index] = DoorState.CLOSED;
        }
    }

    /**
     * Every door of {@code authoredMap} shut, which is the state a round begins in — doors are the
     * one piece of authored geometry a round starts by putting back.
     *
     * <p>A map without doors gets a state whose every loop is empty, so the callers that run every
     * tick pay for a length check rather than for the absence of a feature.
     */
    public static MapDoorState closed(ArenaMap authoredMap) {
        return new MapDoorState(Objects.requireNonNull(authoredMap, "authoredMap"));
    }

    /** Every door this state knows, in authored order. Immutable. */
    public List<MapBlock> doors() {
        return doors;
    }

    /** True when the map authors no doors, in which case every question here is a no-op. */
    public boolean isEmpty() {
        return doors.isEmpty();
    }

    /**
     * How many times a door has changed state. Only ever compared with a previous value, which is
     * how {@link MapDamageState#liveMap()} notices that its cached map needs rebuilding.
     */
    public long revision() {
        return revision;
    }

    /** The tick this state was last set to — the server's simulation tick or a client's own. */
    public long nowTick() {
        return nowTick;
    }

    /**
     * Moves this state's clock without running any transition.
     *
     * <p>The presenting side's half of the tick contract above: a client sets the clock every frame
     * so {@link #swingFraction(MapBlock)} can be derived, and changes nothing else. A client never
     * opens or shuts a door by the passage of time, only the authority does.
     */
    public void setClock(long nowTick) {
        this.nowTick = nowTick;
    }

    /** The state of {@code door}, which must be one of {@link #doors()}. */
    public DoorState stateOf(MapBlock door) {
        return states[indexOf(door)];
    }

    /**
     * The state of the door with this id, or {@code null} when the map authors no such door.
     *
     * <p>Null rather than a default, because the callers are the wire paths and an unknown id there
     * is a client playing another map — something to ignore, not a shut door to guess at.
     */
    public DoorState stateOfId(int doorId) {
        Integer index = indexById.get(doorId);
        return index == null ? null : states[index];
    }

    /** The tick {@code door} entered its current state, in this state's own clock. */
    public long sinceTickOf(MapBlock door) {
        return sinceTick[indexOf(door)];
    }

    /** True while {@code door} is in the way. The one question the blocking consumers ask. */
    public boolean blocks(MapBlock door) {
        return stateOf(door).blocks();
    }

    /**
     * The doors that are <b>not</b> in the way, which is exactly what a live map leaves out:
     * {@link MapDamageState#liveMap()} is the standing geometry minus this list.
     */
    public List<MapBlock> openDoors() {
        List<MapBlock> open = new ArrayList<>();
        for (int index = 0; index < doors.size(); index++) {
            if (!states[index].blocks()) {
                open.add(doors.get(index));
            }
        }
        return open;
    }

    /**
     * How far this door's panel has swung, in {@code [0, 1]}: 0 shut, 1 fully open. Presentation
     * only — the plan's "collision consults the state, never the animation".
     *
     * <p>Derived here rather than in the renderer so there is one answer per frame for the panel
     * and anything else that wants to know, and so a caller that draws a door does not need its own
     * clock arithmetic. The caller anchors and foreshortens the panel however it likes; the number
     * only says how far along the swing is.
     */
    public float swingFraction(MapBlock door) {
        int index = indexOf(door);
        float elapsed = (float) (nowTick - sinceTick[index]) / DoorConfig.SWING_TICKS;
        return switch (states[index]) {
            case CLOSED -> 0f;
            case OPEN -> 1f;
            case OPENING -> clamp(elapsed);
            case SHUTTING -> clamp(1f - elapsed);
        };
    }

    /**
     * Applies one use press to {@code door}.
     *
     * <p>The authority calls this for a press it has decided to honour, and a client calls it to
     * predict its own press; both get the same answer for the same world, which is what keeps a
     * predicted swing and the authoritative one the same swing.
     *
     * @param teamIndex the opener's team index, resolved against the door's authored access rule
     * @param occupancy what the caller knows about bodies and thrown objects
     * @return what happened, including the two ways a press is refused
     */
    public Outcome requestUse(MapBlock door, int teamIndex, Occupancy occupancy) {
        Objects.requireNonNull(occupancy, "occupancy");
        int index = indexOf(door);
        return switch (states[index]) {
            case OPENING, SHUTTING -> {
                // A moving panel does not answer: reversing a swing halfway would have to decide
                // what the reversed sweep means for whoever is in it. Both sides ignore the press,
                // so ignoring it cannot desynchronise them.
                yield Outcome.IGNORED_MOVING;
            }
            case CLOSED -> {
                if (!door.door().allows(teamIndex)) {
                    yield Outcome.REFUSED_ACCESS;
                }
                if (occupancy.occupied(swingRegion(door))) {
                    yield Outcome.REFUSED_BLOCKED;
                }
                enter(index, DoorState.OPENING);
                yield Outcome.OPENING;
            }
            case OPEN -> {
                if (occupancy.occupied(swingRegion(door))) {
                    yield Outcome.REFUSED_BLOCKED;
                }
                enter(index, DoorState.SHUTTING);
                yield Outcome.SHUTTING;
            }
        };
    }

    /**
     * Runs the clock-driven transitions for every door: the swing ends, and an open door shuts
     * again after its hold.
     *
     * <p><b>Authority only.</b> The other half of the machine deliberately does not run on a
     * mirror; see the class comment. Doors a round should not have to lose are the ones the
     * authority reports changing, which is why this returns them: the caller broadcasts exactly
     * what moved, and nothing when nothing did.
     *
     * @param nowTick the authority's current tick, which also becomes this state's clock
     * @return the doors whose state changed, in authored order
     */
    public List<MapBlock> advance(long nowTick, Occupancy occupancy) {
        Objects.requireNonNull(occupancy, "occupancy");
        this.nowTick = nowTick;
        List<MapBlock> changed = List.of();
        for (int index = 0; index < doors.size(); index++) {
            long held = nowTick - sinceTick[index];
            MapBlock door = doors.get(index);
            DoorState next = switch (states[index]) {
                case OPENING -> held >= DoorConfig.SWING_TICKS ? DoorState.OPEN : null;
                case OPEN -> held >= DoorConfig.HOLD_TICKS
                    && !occupancy.occupied(swingRegion(door)) ? DoorState.SHUTTING : null;
                case SHUTTING -> held >= DoorConfig.SWING_TICKS
                    ? (occupancy.occupied(swingRegion(door))
                        // Not onto a body: a swing that would finish on top of something swings
                        // back open and waits, which is the safe answer — a body inside a shut door
                        // could not walk out of it.
                        ? DoorState.OPENING
                        : DoorState.CLOSED)
                    : null;
                case CLOSED -> null;
            };
            if (next == null) {
                continue;
            }
            enter(index, next);
            if (changed.isEmpty()) {
                changed = new ArrayList<>();
            }
            changed.add(door);
        }
        return changed;
    }

    /**
     * Applies one authoritative state received over the wire.
     *
     * <p>Nothing is decided here — the authority already resolved the access, the swing and the
     * occupancy — and the record is stamped with the tick the caller says the door entered the
     * state, so a swing starts being drawn where the authority actually started it. An unknown id,
     * a record older than the one already applied to that door, and a state the door is already in
     * all change nothing: the first two mean the client is looking at another map or at stale
     * history, and the third keeps a redundant record from restarting a panel mid-swing.
     *
     * @param wireTick the authority's tick for this record, used only to order the two wire paths
     * @param sinceTick the tick, in <i>this</i> state's clock, at which the door entered the state
     * @return true when the door actually changed state
     */
    public boolean applyRemote(int doorId, DoorState state, long wireTick, long sinceTick) {
        Objects.requireNonNull(state, "state");
        Integer index = indexById.get(doorId);
        if (index == null || wireTick < recordTick[index]) {
            return false;
        }
        recordTick[index] = wireTick;
        if (states[index] == state) {
            return false;
        }
        enter(index, state);
        this.sinceTick[index] = sinceTick;
        return true;
    }

    /**
     * Puts every door back to {@link DoorState#CLOSED} — the authored state, for the next round.
     *
     * <p>Doors have no history to replay and nothing to repair, so unlike destruction a reset is a
     * plain state write. {@link MapDamageState#reset()} calls it beside clearing the damage, because
     * a round that repaired the map but left its doors open would hand the next round a different
     * arena than the one both teams agreed to fight in.
     */
    public void reset() {
        for (int index = 0; index < states.length; index++) {
            states[index] = DoorState.CLOSED;
            sinceTick[index] = nowTick;
            recordTick[index] = 0L;
        }
        revision++;
    }

    /**
     * The rectangle a swing sweeps: the panel's own cell, grown by
     * {@link DoorConfig#SWING_CLEARANCE}.
     *
     * <p>A door's panel swings out of the doorway, so its cell is the whole region it can occupy;
     * the clearance covers the body standing against it, which is what "a body blocks the swing" has
     * to mean for a door only 24 units thick.
     */
    public static Rect swingRegion(MapBlock door) {
        Objects.requireNonNull(door, "door");
        Rect rect = door.rect();
        float clearance = DoorConfig.SWING_CLEARANCE;
        return new Rect(
            rect.x() - clearance,
            rect.y() - clearance,
            rect.width() + 2f * clearance,
            rect.height() + 2f * clearance);
    }

    private void enter(int index, DoorState state) {
        states[index] = state;
        sinceTick[index] = nowTick;
        revision++;
    }

    private int indexOf(MapBlock door) {
        Integer index = indexById.get(Objects.requireNonNull(door, "door").door().id());
        if (index == null || !doors.get(index).equals(door)) {
            throw new IllegalArgumentException("block is not a door of this map: " + door);
        }
        return index;
    }

    private static float clamp(float value) {
        return Math.min(1f, Math.max(0f, value));
    }
}
