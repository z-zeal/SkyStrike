package io.github.skystrike.server.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.DoorConfig;
import io.github.skystrike.shared.config.WorldConfig;
import io.github.skystrike.shared.gadget.SurveillanceView;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.map.BlockKind;
import io.github.skystrike.shared.map.DoorSpec;
import io.github.skystrike.shared.map.DoorState;
import io.github.skystrike.shared.map.MapBlock;
import io.github.skystrike.shared.map.MapDamageState;
import io.github.skystrike.shared.map.MapDoorState;
import io.github.skystrike.shared.map.Material;
import io.github.skystrike.shared.map.Rect;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.model.ThrownUtility;
import io.github.skystrike.shared.utility.UtilityId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The server-side door rules of plan §4 and D3, layered over the shared machine: the use key
 * resolves to one door, access and occupancy refuse, and only time can open or shut it once the
 * press passed.
 *
 * <p>The machine's own semantics (state transitions, swing fractions, the mirror's remote-apply
 * rules) are pinned in {@code MapDoorStateTest}; this file pins what the server alone
 * contributes — where occupancy comes from, who may press at all, and that a swing the world
 * refuses to finish swings back instead of trapping anyone.
 */
class DoorSystemTest {

    private static final float AXIS = WorldConfig.MIRROR_AXIS_X;

    /** The doorway: centred on the mirror axis, so the test map is one door and nothing else. */
    private static final Rect DOOR_RECT = new Rect(1488f, 100f, 24f, 90f);
    /** Beside the door: inside {@link DoorConfig#USE_REACH}, outside the swing region. */
    private static final float BESIDE_X = 1440f;
    private static final float STANDING_Y = 140f;

    private DoorSystem doors;
    private MapDoorState machine;
    private MapBlock door;

    @BeforeEach
    void setUp() {
        doors = doorsWith(DoorSpec.all(1));
    }

    private DoorSystem doorsWith(DoorSpec spec) {
        MapBlock block = new MapBlock(
            BlockKind.DOOR, Material.METAL, DOOR_RECT, MapBlock.DEFAULT_DURABILITY_MULTIPLIER, spec);
        ArenaMap map = ArenaMap.builder(AXIS).centred(block).build();
        MapDamageState damageState = MapDamageState.intact(map);
        this.door = map.doors().get(0);
        this.machine = damageState.doors();
        return new DoorSystem(map, machine);
    }

    private static Player player(int id, int team, float x, boolean alive) {
        Player player = new Player(id, "P" + id, team, x, STANDING_Y);
        player.alive = alive;
        return player;
    }

    private static ThrownUtility frag(float x, float y) {
        return new ThrownUtility(9, 1, 0, UtilityId.FRAG.ordinal(), x, y, 0f, 0f, 3f);
    }

    @Test
    @DisplayName("a use press next to a closed door swings it open")
    void usePressOpensTheDoorInReach() {
        Player presser = player(1, 0, BESIDE_X, true);
        DoorSystem.PressResult result = doors.usePressed(presser, List.of(presser), List.of());

        assertEquals(MapDoorState.Outcome.OPENING, result.outcome());
        assertEquals(door, result.door(), "the reach rule resolved the doorway the presser faced");
        assertEquals(DoorState.OPENING, machine.stateOf(door));
    }

    @Test
    @DisplayName("a press with no door in reach never reaches a decision")
    void pressBeyondReachResolvesSilently() {
        Player presser = player(1, 0, 800f, true);
        assertNull(doors.usePressed(presser, List.of(presser), List.of()));
        assertEquals(DoorState.CLOSED, machine.stateOf(door), "nothing moved: no warning, no record");
    }

    @Test
    @DisplayName("the presser's own body in the swing region blocks their press")
    void presserStandingInTheSwingIsRefused() {
        // The hitbox of a player centred in the doorway overlaps the swung panel plus clearance.
        Player presser = player(1, 0, AXIS, true);
        DoorSystem.PressResult result = doors.usePressed(presser, List.of(presser), List.of());

        assertEquals(MapDoorState.Outcome.REFUSED_BLOCKED, result.outcome());
        assertEquals(DoorState.CLOSED, machine.stateOf(door));
    }

    @Test
    @DisplayName("another live body blocks the swing; a dead one does not")
    void otherBodiesBlockOnlyWhileAlive() {
        Player presser = player(1, 0, BESIDE_X, true);
        Player occupant = player(2, 0, AXIS, true);

        assertEquals(MapDoorState.Outcome.REFUSED_BLOCKED,
            doors.usePressed(presser, List.of(presser, occupant), List.of()).outcome());
        assertEquals(DoorState.CLOSED, machine.stateOf(door));

        occupant.alive = false;
        assertEquals(MapDoorState.Outcome.OPENING,
            doors.usePressed(presser, List.of(presser, occupant), List.of()).outcome(),
            "the plan refuses to swing onto a live body, never onto a corpse that blocks nothing");
    }

    @Test
    @DisplayName("a thrown utility in the swing region blocks the swing")
    void thrownUtilityBlocksTheSwing() {
        Player presser = player(1, 0, BESIDE_X, true);
        assertEquals(MapDoorState.Outcome.REFUSED_BLOCKED,
            doors.usePressed(presser, List.of(presser), List.of(frag(AXIS, STANDING_Y))).outcome());
        assertEquals(MapDoorState.Outcome.OPENING,
            doors.usePressed(presser, List.of(presser), List.of(frag(400f, STANDING_Y))).outcome(),
            "a utility clear of the doorway does not count");
    }

    @Test
    @DisplayName("a dead, stunned or piloting player presses nothing")
    void incapacitatedPlayersDoNotPress() {
        Player dead = player(1, 0, BESIDE_X, false);
        assertNull(doors.usePressed(dead, List.of(dead), List.of()));

        Player stunned = player(2, 0, BESIDE_X, true);
        stunned.slowRemaining = 1f;
        assertNull(doors.usePressed(stunned, List.of(stunned), List.of()));

        Player piloting = player(3, 0, BESIDE_X, true);
        piloting.surveillanceView = SurveillanceView.DRONE.ordinal();
        assertNull(doors.usePressed(piloting, List.of(piloting), List.of()));

        assertEquals(DoorState.CLOSED, machine.stateOf(door),
            "all three are consumed and dropped, like every dead-tick edge");
    }

    @Test
    @DisplayName("a team door refuses the other team and answers its own")
    void teamDoorFollowsTheAuthoredAccess() {
        doors = doorsWith(DoorSpec.team(1, 1));

        Player wrongTeam = player(1, 0, BESIDE_X, true);
        assertEquals(MapDoorState.Outcome.REFUSED_ACCESS,
            doors.usePressed(wrongTeam, List.of(wrongTeam), List.of()).outcome());
        assertEquals(DoorState.CLOSED, machine.stateOf(door));

        Player rightTeam = player(2, 1, BESIDE_X, true);
        assertEquals(MapDoorState.Outcome.OPENING,
            doors.usePressed(rightTeam, List.of(rightTeam), List.of()).outcome());
    }

    @Test
    @DisplayName("time alone finishes the swing, holds, and shuts the cleared doorway")
    void advanceRunsTheWholeCycle() {
        Player presser = player(1, 0, BESIDE_X, true);
        doors.usePressed(presser, List.of(presser), List.of());

        assertEquals(List.of(door),
            doors.advance(DoorConfig.SWING_TICKS, List.of(presser), List.of()),
            "the swing finished exactly at SWING_TICKS");
        assertEquals(DoorState.OPEN, machine.stateOf(door));

        long holdOver = (long) DoorConfig.SWING_TICKS + DoorConfig.HOLD_TICKS;
        assertEquals(List.of(door),
            doors.advance(holdOver, List.of(presser), List.of()),
            "the hold elapsed with nobody in the doorway, so the panel swings back");
        assertEquals(DoorState.SHUTTING, machine.stateOf(door));

        doors.advance(holdOver + DoorConfig.SWING_TICKS, List.of(presser), List.of());
        assertEquals(DoorState.CLOSED, machine.stateOf(door));
    }

    @Test
    @DisplayName("a swing that would finish on someone swings back open instead")
    void blockedShuttingSwingReopens() {
        Player presser = player(1, 0, BESIDE_X, true);
        doors.usePressed(presser, List.of(presser), List.of());
        doors.advance(DoorConfig.SWING_TICKS, List.of(presser), List.of());
        long holdOver = (long) DoorConfig.SWING_TICKS + DoorConfig.HOLD_TICKS;
        doors.advance(holdOver, List.of(presser), List.of());
        assertEquals(DoorState.SHUTTING, machine.stateOf(door));

        // The occupant steps into the doorway while the panel is coming down (bullets and thrown
        // utilities reach inside a swing that bodies cannot, per the machine's own comment).
        Player occupant = player(2, 0, AXIS, true);
        doors.advance(holdOver + DoorConfig.SWING_TICKS, List.of(presser, occupant), List.of());
        assertEquals(DoorState.OPENING, machine.stateOf(door),
            "the swing refused to finish on a body and swings back, per plan §4");

        // From here it completes only once the doorway is clear again.
        doors.advance(holdOver + DoorConfig.SWING_TICKS * 2L, List.of(presser), List.of());
        assertEquals(DoorState.OPEN, machine.stateOf(door));
    }

    @Test
    @DisplayName("pressing a moving door moves nothing further")
    void pressDuringSwingIsIgnored() {
        Player presser = player(1, 0, BESIDE_X, true);
        doors.usePressed(presser, List.of(presser), List.of());

        assertEquals(MapDoorState.Outcome.IGNORED_MOVING,
            doors.usePressed(presser, List.of(presser), List.of()).outcome());
        assertEquals(DoorState.OPENING, machine.stateOf(door),
            "both sides ignore alike, so ignoring can never desynchronise them");
    }

    @Test
    @DisplayName("an open doorway accepts a press from either team to start shutting")
    void openDoorShutsForAnyone() {
        doors = doorsWith(DoorSpec.team(1, 1));
        Player owner = player(1, 1, BESIDE_X, true);
        doors.usePressed(owner, List.of(owner), List.of());
        doors.advance(DoorConfig.SWING_TICKS, List.of(owner), List.of());
        assertEquals(DoorState.OPEN, machine.stateOf(door));

        Player visitor = player(2, 0, BESIDE_X, true);
        assertEquals(MapDoorState.Outcome.SHUTTING,
            doors.usePressed(visitor, List.of(owner, visitor), List.of()).outcome(),
            "the access rule only guards opening: leaving a door open is no privilege");
        assertTrue(machine.blocks(door),
            "and the closing panel blocks again from the moment it starts moving (DoorState.blocks)");
    }
}
