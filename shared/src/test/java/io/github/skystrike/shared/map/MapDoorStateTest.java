package io.github.skystrike.shared.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.DoorConfig;
import io.github.skystrike.shared.config.WorldConfig;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The door rules of plan §7: a closed door blocks and an open one does not, a blocked swing is
 * refused rather than forced, and every transition is driven by a use request or by the
 * authority's clock — never by anything a client decided on its own.
 *
 * <p>The maps here are authored one door at a time, because a property like "a swing that would
 * finish on a body swings back open" needs one door whose occupancy the test controls. The shipped
 * arena's own fixtures are exercised by {@code ArenaMapFormatTest} and by the server's door tests.
 */
class MapDoorStateTest {

    private static final float AXIS = WorldConfig.MIRROR_AXIS_X;
    private static final Rect DOOR_RECT = new Rect(1488f, 100f, 24f, 90f);
    /** A body-sized box standing in the doorway, used as an occupancy answer. */
    private static final Rect BODY = new Rect(1475f, 100f, 50f, 60f);

    private static MapDoorState stateOf(DoorSpec spec) {
        MapBlock block = new MapBlock(
            BlockKind.DOOR, Material.METAL, DOOR_RECT, MapBlock.DEFAULT_DURABILITY_MULTIPLIER, spec);
        return MapDoorState.closed(ArenaMap.builder(AXIS).centred(block).build());
    }

    /** The occupancy answer of a world holding exactly one body at {@link #BODY}. */
    private static MapDoorState.Occupancy bodyInTheDoorway() {
        return region -> region.overlaps(BODY);
    }

    @Test
    @DisplayName("a round starts with every door shut, blocking in every column")
    void aRoundStartsShut() {
        MapDoorState state = stateOf(DoorSpec.all(1));
        MapBlock door = state.doors().get(0);

        assertEquals(1, state.doors().size());
        assertFalse(state.isEmpty());
        assertEquals(DoorState.CLOSED, state.stateOf(door));
        assertEquals(DoorState.CLOSED, state.stateOfId(1));
        assertTrue(state.blocks(door), "a shut door is in the way");
        assertTrue(state.openDoors().isEmpty());
        assertEquals(0f, state.swingFraction(door), 0f);
    }

    @Test
    void aMapWithoutDoorsAnswersEveryQuestionAsEmpty() {
        // The shipped arena authors the close-out's fixture door pair, so the doorless map is
        // authored here: this test is about the empty case, not about a particular map.
        MapBlock wall = new MapBlock(
            BlockKind.WALL, Material.STONE, new Rect(0f, 0f, 3000f, 100f),
            MapBlock.DEFAULT_DURABILITY_MULTIPLIER, DoorSpec.NONE);
        MapDoorState state = MapDoorState.closed(ArenaMap.builder(AXIS).centred(wall).build());

        assertTrue(state.isEmpty());
        assertTrue(state.doors().isEmpty());
        assertTrue(state.openDoors().isEmpty());
        assertTrue(state.advance(600L, MapDoorState.CLEAR).isEmpty());
        assertNull(state.stateOfId(7));
    }

    @Test
    void anAllDoorOpensForEitherTeam() {
        MapDoorState state = stateOf(DoorSpec.all(1));
        MapBlock door = state.doors().get(0);

        assertEquals(MapDoorState.Outcome.OPENING, state.requestUse(door, 1, MapDoorState.CLEAR));
        assertEquals(DoorState.OPENING, state.stateOf(door));
        assertTrue(state.blocks(door), "the panel is still in the doorway while it swings");
    }

    @Test
    void aTeamDoorOpensForItsOwnTeamAndRefusesTheOther() {
        MapDoorState teamZero = stateOf(DoorSpec.team(1, 0));
        assertEquals(
            MapDoorState.Outcome.OPENING,
            teamZero.requestUse(teamZero.doors().get(0), 0, MapDoorState.CLEAR));

        MapDoorState teamOne = stateOf(DoorSpec.team(1, 0));
        assertEquals(
            MapDoorState.Outcome.REFUSED_ACCESS,
            teamOne.requestUse(teamOne.doors().get(0), 1, MapDoorState.CLEAR));
        assertEquals(DoorState.CLOSED, teamOne.stateOf(teamOne.doors().get(0)),
            "a refusal is not a swing");
    }

    @Test
    void aBlockedSwingIsRefusedRatherThanForced() {
        MapDoorState state = stateOf(DoorSpec.all(1));
        MapBlock door = state.doors().get(0);

        MapDoorState.Outcome refused = state.requestUse(door, 0, bodyInTheDoorway());
        assertTrue(refused.refused());
        assertEquals(MapDoorState.Outcome.REFUSED_BLOCKED, refused);
        assertEquals(DoorState.CLOSED, state.stateOf(door));
    }

    @Test
    void anyoneMayShutADoorThatStandsOpen() {
        MapDoorState state = stateOf(DoorSpec.team(1, 0));
        MapBlock door = state.doors().get(0);
        state.requestUse(door, 0, MapDoorState.CLEAR);
        state.advance(DoorConfig.SWING_TICKS, MapDoorState.CLEAR);

        // Team 1 was refused the opening and may still shut it: leaving a door open is not a right.
        assertEquals(MapDoorState.Outcome.SHUTTING, state.requestUse(door, 1, MapDoorState.CLEAR));
    }

    @Test
    void aMovingDoorIgnoresTheUseKey() {
        MapDoorState state = stateOf(DoorSpec.all(1));
        MapBlock door = state.doors().get(0);
        state.requestUse(door, 0, MapDoorState.CLEAR);

        assertEquals(
            MapDoorState.Outcome.IGNORED_MOVING,
            state.requestUse(door, 0, MapDoorState.CLEAR));
        state.advance(DoorConfig.SWING_TICKS, MapDoorState.CLEAR);
        state.requestUse(door, 0, MapDoorState.CLEAR);
        assertEquals(
            MapDoorState.Outcome.IGNORED_MOVING,
            state.requestUse(door, 0, MapDoorState.CLEAR),
            "a door that is shutting ignores the key too");
    }

    @Test
    @DisplayName("the swing ends on the authority's clock, not on a request")
    void theSwingEndsOnTheAuthoritysClock() {
        MapDoorState state = stateOf(DoorSpec.all(1));
        MapBlock door = state.doors().get(0);
        state.requestUse(door, 0, MapDoorState.CLEAR);

        assertEquals(0L, state.nowTick());
        assertTrue(state.advance(DoorConfig.SWING_TICKS - 1, MapDoorState.CLEAR).isEmpty());
        assertEquals(DoorState.OPENING, state.stateOf(door));
        assertEquals(
            (DoorConfig.SWING_TICKS - 1) / (float) DoorConfig.SWING_TICKS,
            state.swingFraction(door), 1e-4f);

        List<MapBlock> changed = state.advance(DoorConfig.SWING_TICKS, MapDoorState.CLEAR);
        assertEquals(List.of(door), changed);
        assertEquals(DoorState.OPEN, state.stateOf(door));
        assertFalse(state.blocks(door), "an open door is in no column at all");
        assertEquals(List.of(door), state.openDoors());
        assertEquals(1f, state.swingFraction(door), 0f);
        assertTrue(state.advance(DoorConfig.SWING_TICKS, MapDoorState.CLEAR).isEmpty(),
            "an open door changes nothing until its hold elapses");
    }

    @Test
    @DisplayName("an open door shuts again after its hold, and the pose runs back down")
    void anOpenDoorShutsAgainAfterItsHold() {
        MapDoorState state = stateOf(DoorSpec.all(1));
        MapBlock door = state.doors().get(0);
        state.requestUse(door, 0, MapDoorState.CLEAR);
        state.advance(DoorConfig.SWING_TICKS, MapDoorState.CLEAR);

        long shutAt = DoorConfig.SWING_TICKS + DoorConfig.HOLD_TICKS;
        assertEquals(
            List.of(door), state.advance(shutAt, MapDoorState.CLEAR), "the hold elapsed");
        assertEquals(DoorState.SHUTTING, state.stateOf(door));
        assertTrue(state.blocks(door), "a closing door blocks again on the way down");
        assertTrue(state.openDoors().isEmpty());

        state.advance(shutAt + DoorConfig.SWING_TICKS / 2, MapDoorState.CLEAR);
        assertEquals(0.5f, state.swingFraction(door), 1e-4f, "half-way down after half a swing");
        assertEquals(
            List.of(door), state.advance(shutAt + DoorConfig.SWING_TICKS, MapDoorState.CLEAR));
        assertEquals(DoorState.CLOSED, state.stateOf(door));
        assertEquals(0f, state.swingFraction(door), 0f);
    }

    @Test
    void anOccupiedHoldKeepsTheDoorOpen() {
        MapDoorState state = stateOf(DoorSpec.all(1));
        MapBlock door = state.doors().get(0);
        state.requestUse(door, 0, MapDoorState.CLEAR);
        state.advance(DoorConfig.SWING_TICKS, MapDoorState.CLEAR);

        assertTrue(state.advance(
            DoorConfig.SWING_TICKS + DoorConfig.HOLD_TICKS, bodyInTheDoorway()).isEmpty());
        assertEquals(DoorState.OPEN, state.stateOf(door), "a body in the doorway holds it open");
    }

    @Test
    @DisplayName("a swing that would finish on a body swings back open instead")
    void aDoorWillNotFinishClosingOnABody() {
        MapDoorState state = stateOf(DoorSpec.all(1));
        MapBlock door = state.doors().get(0);
        state.requestUse(door, 0, MapDoorState.CLEAR);
        state.advance(DoorConfig.SWING_TICKS, MapDoorState.CLEAR);
        long shutAt = DoorConfig.SWING_TICKS + DoorConfig.HOLD_TICKS;
        state.advance(shutAt, MapDoorState.CLEAR);

        // Something arrived in the doorway during the closing swing — a thrown object, say.
        assertEquals(List.of(door), state.advance(
            shutAt + DoorConfig.SWING_TICKS, bodyInTheDoorway()));
        assertEquals(DoorState.OPENING, state.stateOf(door));
        assertTrue(state.blocks(door), "and it is in the way again while it opens");
    }

    @Test
    void anOccupiedDoorwayRefusesToShut() {
        MapDoorState state = stateOf(DoorSpec.all(1));
        MapBlock door = state.doors().get(0);
        state.requestUse(door, 0, MapDoorState.CLEAR);
        state.advance(DoorConfig.SWING_TICKS, MapDoorState.CLEAR);

        // A body standing in the doorway: the door stays open rather than swinging into it.
        assertEquals(
            MapDoorState.Outcome.REFUSED_BLOCKED,
            state.requestUse(door, 0, bodyInTheDoorway()));
        assertEquals(DoorState.OPEN, state.stateOf(door));
    }

    @Test
    @DisplayName("a mirror applies a record's state and anchor, and drops a stale one")
    void aMirrorAppliesRecordsAndIgnoresStaleOnes() {
        MapDoorState state = stateOf(DoorSpec.all(1));
        MapBlock door = state.doors().get(0);

        assertTrue(state.applyRemote(1, DoorState.OPENING, 40L, 10L));
        assertEquals(DoorState.OPENING, state.stateOf(door));
        assertEquals(10L, state.sinceTickOf(door));
        assertFalse(state.applyRemote(1, DoorState.OPENING, 41L, 12L), "same state, no change");
        assertEquals(10L, state.sinceTickOf(door), "and no re-anchored swing either");
        assertFalse(state.applyRemote(1, DoorState.OPEN, 39L, 14L),
            "an older record must not resurrect a state the door has already left");
        assertEquals(DoorState.OPENING, state.stateOf(door));
        assertFalse(state.applyRemote(99, DoorState.OPEN, 41L, 14L), "another map's door");
        assertTrue(state.applyRemote(1, DoorState.OPEN, 42L, 14L), "a newer record lands");
        assertEquals(DoorState.OPEN, state.stateOf(door));
        assertEquals(14L, state.sinceTickOf(door), "and re-anchors the swing it describes");
    }

    @Test
    void thePoseIsDerivedFromTheAnchorAMirrorWasGiven() {
        MapDoorState state = stateOf(DoorSpec.all(1));
        state.applyRemote(1, DoorState.OPENING, 40L, -DoorConfig.SWING_TICKS / 2);
        state.setClock(0L);

        assertEquals(0.5f, state.swingFraction(state.doors().get(0)), 1e-4f,
            "a swing that started half a swing ago is drawn half-way through");
    }

    @Test
    void resetShutsEveryDoorAndForgetsTheRecords() {
        MapDoorState state = stateOf(DoorSpec.all(1));
        MapBlock door = state.doors().get(0);
        state.requestUse(door, 0, MapDoorState.CLEAR);
        state.advance(DoorConfig.SWING_TICKS, MapDoorState.CLEAR);
        assertFalse(state.blocks(door));

        state.reset();
        assertTrue(state.blocks(door));
        assertEquals(0f, state.swingFraction(door), 0f);
        assertEquals(0L, state.sinceTickOf(door));
    }

    @Test
    void aBlockThatIsNotADoorOfThisMapIsRefused() {
        MapDoorState state = stateOf(DoorSpec.all(1));
        MapDoorState other = stateOf(DoorSpec.all(9));
        MapBlock foreign = other.doors().get(0);

        assertThrows(
            IllegalArgumentException.class,
            () -> state.requestUse(foreign, 0, MapDoorState.CLEAR));
        assertThrows(IllegalArgumentException.class, () -> state.blocks(foreign));
    }

    @Test
    void theSwingRegionIsTheDoorCellGrownByItsClearance() {
        MapDoorState state = stateOf(DoorSpec.all(1));
        Rect region = MapDoorState.swingRegion(state.doors().get(0));

        assertEquals(DOOR_RECT.x() - DoorConfig.SWING_CLEARANCE, region.x(), 1e-4f);
        assertEquals(DOOR_RECT.width() + 2f * DoorConfig.SWING_CLEARANCE, region.width(), 1e-4f);
        assertEquals(DOOR_RECT.height() + 2f * DoorConfig.SWING_CLEARANCE, region.height(), 1e-4f);
    }
}
