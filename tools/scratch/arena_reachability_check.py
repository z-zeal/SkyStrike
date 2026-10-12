#!/usr/bin/env python3
"""Check the shipped arena against the reachability gate `MapReachability` implements in Java.

Reachability is the one property of a map that no picture shows and no playtest of *this* map can
prove for the next one: a stray block can seal a lane, and the map still loads, bakes, renders and
plays exactly as before right up to the moment a player walks into a wall that should not be there.
Map plan §7 therefore makes it a deterministic search over a map's walkable grid, run for every
shipped map, so a sealed map fails a check rather than a playtest.

The search is float arithmetic over the shared movement column — no JDK-only behaviour, no GPU, no
random — so it can be reproduced here the way `arena_sdf_check.py` reproduces the bake. This script
re-implements `MapReachability` from the committed Java: the same column pitch, the same stance
test, the same M8 step and hop envelope, the same two manoeuvre shapes, the same per-spawn closures.
Running it therefore says three things this sandbox could not otherwise say:

  1. the shipped arena's verdict under the gate as that Java states it,
  2. the gate is not vacuous — hand-sealed probe maps, and probe maps whose only route needs more
     fuel than a tank's 40%, are reported, and
  3. the verdict does not turn on the readings the code has to pick one of: the column pitch, and
     whether a door is read as an opening (the Java's choice) or as the solid panel the movement
     column calls it.

It is a source-only check: it proves a verdict about the committed map text, not that the Java
compiles or that a player can walk the route. The `shared` unit tests remain the check of record.

Implementation note, because the two are not line-for-line twins: this script indexes the geometry
per column and per stance height (so a crossing test is an interval-slice comparison) where the Java
walks the rectangles directly. Both compute the same predicate — "does any solid rectangle overlap
this body box" — and both apply it to the same two manoeuvre shapes, which is what makes the verdicts
comparable. Neither is a simulation.

Usage:
    python3 tools/scratch/arena_reachability_check.py [--pitch 5.0] [--verbose] [--fixtures]
Exit code 0 = every probe the gate is expected to catch was caught, and the shipped arena reports
what the Java's own test pins for it.
"""

from __future__ import annotations

import argparse
import pathlib
import re
import struct
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
MAP_SOURCE = ROOT / "shared/src/main/java/io/github/skystrike/shared/map/StandardArenaMap.java"

# PlayerConfig / WorldConfig / the M8 envelope: the same literals the Java reads.
ARENA_WIDTH = 3000.0
ARENA_HEIGHT = 2000.0
GROUND_HEIGHT = 100.0
MIRROR_AXIS_X = 1500.0
PLAYER_WIDTH = 30.0
STAND_HEIGHT = 50.0
GROUND_SKIN = 2.0
WALK_SPEED = 200.0
MAX_FUEL = 100.0
FUEL_BURN_RATE = 20.0
JUMP_SPEED = 420.0
GRAVITY = -800.0
JETPACK_THRUST = 1000.0
JETPACK_VERTICAL_SPLIT = 0.85
EPSILON = 0.01

MAX_JUMP_RISE = JUMP_SPEED * JUMP_SPEED / (2.0 * -GRAVITY)
MAX_JUMP_GAP = 210.0
FUEL_BUDGET = MAX_FUEL * 0.40
JETPACK_ACCELERATION = JETPACK_THRUST * JETPACK_VERTICAL_SPLIT + GRAVITY
MAX_HOP_REACH = WALK_SPEED * FUEL_BUDGET / FUEL_BURN_RATE

# MapReachability.COLUMN_PITCH: the x resolution of the walkable grid.
DEFAULT_PITCH = 5.0

# BlockRules.buildRules(), by kind. A door is SOLID in the movement column like a wall, but the gate
# reads the map in the state a player can always put it in — open — so a door is neither a solid nor
# a support. `--doors-solid` re-runs with the other reading to show the verdict does not turn on it.
MOVEMENT_SOLID_KINDS = {"wall", "glass", "door", "breakable"}
PASSABLE_KINDS = {"door"}
ONE_WAY_TOP_KINDS = {"platform"}


def f32(value):
    """The float32 value of `value`, which is what every Java float arithmetic step produces."""
    return struct.unpack("f", struct.pack("f", value))[0]


def read_map_text():
    """The canonical arena text, straight out of the committed Java source."""
    source = MAP_SOURCE.read_text()
    marker = 'String.join("\\n",'
    start = source.index(marker, source.index("TEXT =")) + len(marker)
    end = source.index('");', start)
    return [line for line in re.findall(r'"([^"]*)"', source[start:end]) if line != "\\n"]


def parse_map(lines, doors_solid=False):
    """(mirror, spawns, blocks) with blocks already reflected, in the builder's order."""
    mirror = None
    spawns = []
    blocks = []
    for line in lines:
        tokens = line.split()
        if not tokens or tokens[0] == "skystrike-map":
            continue
        if tokens[0] == "mirror":
            mirror = f32(float(tokens[1]))
            continue
        if tokens[0] == "spawn":
            spawns.append((int(tokens[1]), f32(float(tokens[2])), f32(float(tokens[3]))))
            continue
        verb, kind = tokens[0], tokens[1]
        if verb not in ("mirrored", "centred"):
            raise ValueError("unknown verb: " + line)
        if kind not in MOVEMENT_SOLID_KINDS | ONE_WAY_TOP_KINDS:
            raise ValueError("unknown kind: " + line)
        if doors_solid:
            kind = {"door": "wall"}.get(kind, kind)
        x, y, width, height = (f32(float(value)) for value in tokens[3:7])
        blocks.append((kind, (x, y, f32(x + width), f32(y + height))))
        if verb == "mirrored":
            left = f32(2.0 * mirror - (x + width))
            blocks.append((kind, (left, y, f32(left + width), f32(y + height))))
    if mirror is None:
        raise ValueError("the map text declares no mirror axis")
    return mirror, spawns, blocks


class Grid:
    """The walkable grid `MapReachability` builds, with its two manoeuvre shapes."""

    def __init__(self, spawns, blocks, pitch):
        self.spawns = spawns
        self.pitch = f32(pitch)
        self.solids = [rect for kind, rect in blocks
                       if kind in MOVEMENT_SOLID_KINDS and kind not in PASSABLE_KINDS]
        self.supports = [rect for kind, rect in blocks
                         if (kind in MOVEMENT_SOLID_KINDS or kind in ONE_WAY_TOP_KINDS)
                         and kind not in PASSABLE_KINDS]
        self.column_count = int(-(-ARENA_WIDTH // self.pitch))
        self.xs = [self.column_x(index) for index in range(self.column_count)]
        # Per column, the feet heights the standing body is blocked at, as sorted closed intervals:
        # the body occupies [feet, feet + STAND_HEIGHT] and the shared overlap counts a shared edge,
        # so a solid covering [bottom, top] blocks feet in [bottom - STAND_HEIGHT - ε, top - ε].
        self.blocked_feet = [self._blocked_feet(x) for x in self.xs]
        self.stances = self._build_stances()
        self.stance_index = {stance: index for index, stance in enumerate(self.stances)}
        self.by_column = [[] for _ in range(self.column_count)]
        for index, (column, _) in enumerate(self.stances):
            self.by_column[column].append(index)
        self.foot_cache = {}
        self.fuel_cache = {}

    def column_x(self, index):
        return f32(f32(self.pitch * (index + 0.5)))

    def _blocked_feet(self, x):
        left = f32(x - PLAYER_WIDTH / 2.0)
        right = f32(left + PLAYER_WIDTH)
        intervals = []
        for rect in self.solids:
            if rect[0] <= right and left <= rect[2]:
                intervals.append((f32(rect[1] - STAND_HEIGHT - EPSILON), f32(rect[3] - EPSILON)))
        intervals.sort()
        return intervals

    def fits(self, column, feet):
        return not any(low <= feet <= high for low, high in self.blocked_feet[column])

    def _build_stances(self):
        stances = []
        highest = f32(ARENA_HEIGHT - STAND_HEIGHT)
        for column, x in enumerate(self.xs):
            tops = {0.0}
            for rect in self.supports:
                if rect[0] <= x <= rect[2] and rect[3] <= highest:
                    tops.add(rect[3])
            for feet in sorted(tops):
                if self.surface_below(column, f32(feet + GROUND_SKIN)) == feet \
                        and self.fits(column, f32(feet + EPSILON)):
                    stances.append((column, feet))
        return stances

    def surface_below(self, column, y):
        """`MapQueries.surfaceBelow` at this column: the highest support top at or below `y`."""
        x = self.xs[column]
        best = 0.0
        for rect in self.supports:
            if x < rect[0] or x > rect[2]:
                continue
            if rect[3] <= y and rect[3] > best:
                best = rect[3]
        return best

    # --- the two manoeuvre shapes --------------------------------------------------------------

    def column_free(self, column, feet_from, feet_to):
        """Is the body's own column clear between two feet heights (a rise or a hover)?"""
        low, high = min(feet_from, feet_to), max(feet_from, feet_to)
        steps = max(1, int((high - low) / self.pitch) + 1)
        for step in range(steps + 1):
            if not self.fits(column, f32(low + (high - low) * step / steps)):
                return False
        return True

    def crossing_free(self, height, column_from, column_to):
        """Is the body's horizontal crossing at `height` clear, column by column?"""
        for column in range(min(column_from, column_to), max(column_from, column_to) + 1):
            if not self.fits(column, height):
                return False
        return True

    def hop_clear(self, source, target):
        """The rise-cross-descend shape first, then the straight flight shape."""
        source_column, source_feet = source
        target_column, target_feet = target
        crossing = max(source_feet, target_feet)
        if (self.column_free(source_column, source_feet, crossing)
                and self.crossing_free(crossing, source_column, target_column)
                and self.column_free(target_column, crossing, target_feet)):
            return True
        return self._straight_clear(source, target)

    def _straight_clear(self, source, target):
        source_column, source_feet = source
        target_column, target_feet = target
        span = abs(self.xs[target_column] - self.xs[source_column])
        rise = target_feet - source_feet
        steps = max(1, int((span ** 2 + rise ** 2) ** 0.5 / self.pitch))
        for step in range(steps + 1):
            progress = step / steps
            column = min(self.column_count - 1,
                         int(source_column + (target_column - source_column) * progress + 0.5))
            if not self.fits(column, source_feet + rise * progress):
                return False
        return True

    # --- the search ----------------------------------------------------------------------------

    def candidates(self, source_index, horizontal_limit):
        """Every stance within `horizontal_limit` of the source, as indices, in column order."""
        source = self.stances[source_index]
        span = int(-(-horizontal_limit // self.pitch))
        result = []
        for column in range(max(0, source[0] - span),
                            min(self.column_count, source[0] + span + 1)):
            if abs(self.xs[column] - self.xs[source[0]]) > horizontal_limit + EPSILON:
                continue
            for index in self.by_column[column]:
                if index != source_index:
                    result.append(index)
        return result

    def foot_targets(self, source_index):
        """Every stance one step reaches without burning fuel, memoised per stance."""
        if source_index not in self.foot_cache:
            source = self.stances[source_index]
            self.foot_cache[source_index] = [
                index for index in self.candidates(source_index, MAX_JUMP_GAP)
                if self.stances[index][1] - source[1] <= MAX_JUMP_RISE + EPSILON
                and self.hop_clear(source, self.stances[index])]
        return self.foot_cache[source_index]

    def fuel_targets(self, source_index):
        """Every stance one 40%-fuel hop reaches, memoised per stance."""
        if source_index not in self.fuel_cache:
            launch = self.stances[source_index]
            targets = []
            for index in self.candidates(source_index, MAX_HOP_REACH):
                target = self.stances[index]
                rise = target[1] - launch[1]
                if rise <= 0.0:
                    continue
                seconds = self.jetpack_seconds_for_rise(rise)
                if seconds * FUEL_BURN_RATE > FUEL_BUDGET + EPSILON:
                    continue
                if abs(self.xs[target[0]] - self.xs[launch[0]]) > WALK_SPEED * seconds + EPSILON:
                    continue
                if self.hop_clear(launch, target):
                    targets.append(index)
            self.fuel_cache[source_index] = targets
        return self.fuel_cache[source_index]

    @staticmethod
    def jetpack_seconds_for_rise(rise):
        discriminant = JUMP_SPEED * JUMP_SPEED + 2.0 * JETPACK_ACCELERATION * rise
        return (discriminant ** 0.5 - JUMP_SPEED) / JETPACK_ACCELERATION

    def start_stance(self, spawn):
        """The stance a spawn starts from: the standable stance nearest the spawn point."""
        _, spawn_x, spawn_y = spawn
        best = None
        best_key = None
        for index, stance in enumerate(self.stances):
            key = (abs(f32(self.xs[stance[0]] - spawn_x)), abs(f32(stance[1] - spawn_y)))
            if best_key is None or key < best_key:
                best, best_key = index, key
        return best

    def closure(self, start, targets_of):
        reached = {start}
        pending = [start]
        while pending:
            for index in targets_of(pending.pop(0)):
                if index not in reached:
                    reached.add(index)
                    pending.append(index)
        return reached

    def report(self):
        """The `Report` the Java builds: per spawn, and the two verdicts."""
        if not self.spawns:
            # MapReachability.Search.run()'s own short circuit: a map without a spawn is searched
            # from nothing and reports nothing sealed, because there is nowhere for a body to start.
            return [], [], [], True, []
        starts = [self.start_stance(spawn) for spawn in self.spawns]
        on_foot = [self.closure(start, lambda index: self.foot_targets(index))
                   for start in starts]
        with_fuel = []
        for start in starts:
            reached = {start}
            pending = [start]
            while pending:
                source = pending.pop(0)
                for index in self.foot_targets(source) + self.fuel_targets(source):
                    if index not in reached:
                        reached.add(index)
                        pending.append(index)
            with_fuel.append(reached)
        spawns_connected = all(j in with_fuel[i] for i in range(len(starts))
                               for j in range(len(starts)) if i != j)
        unreachable_from_every = [stance for index, stance in enumerate(self.stances)
                                  if not any(index in reached for reached in with_fuel)]
        return starts, on_foot, with_fuel, spawns_connected, unreachable_from_every



def m8_surfaces():
    """The M8 traversal inventory `ArenaReachabilityTest` asserts against, mirrored here.

    Each entry is one authored rectangle from the test's own list, as the test writes it: (name,
    left, bottom, width, height, mirrored). The test adds the reflection of every `mirrored` one, so
    this does too; the point of the run is that a named surface carries stances and every one of
    them is inside the envelope, which is what makes the Java test's list a claim and not a comment.
    Stances inside the fixture alcove are left out, as the Java test leaves them out: that pocket is
    the map's one seal and is pinned separately, and the ground lane runs through it.
    """
    surfaces = []
    def add(name, x, y, width, height, mirrored):
        surfaces.append((name, f32(x), f32(x + width), f32(y), f32(y + height), mirrored))
    add("ground", 0.0, 0.0, ARENA_WIDTH, GROUND_HEIGHT, False)
    for step in range(5):
        add("ramp " + str(step), 120.0 + step * 120.0, 200.0 + step * 50.0, 120.0, 18.0, True)
    add("mid lane", 820.0, 500.0, 250.0, 18.0, True)
    add("lower crate top", 820.0, 220.0, 70.0, 16.0, True)
    add("upper crate top", 890.0, 260.0, 70.0, 16.0, True)
    add("outer room floor", 1120.0, 280.0, 75.0, 20.0, True)
    add("centre room floor", 1285.0, 280.0, 430.0, 20.0, False)
    add("wide tunnel roof lip", 1120.0, 190.0, 75.0, 14.0, True)
    add("tunnel hatch step", 1195.0, 192.0, 90.0, 18.0, True)
    add("doorway landing", 1000.0, 342.0, 120.0, 18.0, True)
    add("room access riser", 1220.0, 400.0, 100.0, 18.0, True)
    add("catwalk shelf", 1100.0, 520.0, 120.0, 18.0, True)
    add("catwalk", 1180.0, 620.0, 640.0, 20.0, False)
    add("perch ladder", 1300.0, 700.0, 100.0, 16.0, True)
    add("sniper perch", 1380.0, 760.0, 240.0, 18.0, False)
    add("outer upper ledge", 760.0, 900.0, 240.0, 18.0, True)
    add("high centre platform", 1380.0, 1000.0, 240.0, 18.0, False)
    return surfaces


def in_fixture_alcove(x, feet):
    """The Java test's own predicate: the milestone-4 fixture door's sealed alcove, mirrors included.

    It takes the stance's *world* x, because a stance is a (column, feet) pair on the grid and only
    the grid can turn the column back into a position: comparing a column index against an x range
    is the one mistake this helper exists to keep out of the checks below.
    """
    half = PLAYER_WIDTH / 2.0
    mirrored = f32(2.0 * MIRROR_AXIS_X - x)
    inside = any(1034.0 - half <= candidate <= 1092.0 + half for candidate in (x, mirrored))
    return inside and feet <= 360.0


def fixture_maps():
    """The maps `ArenaReachabilityTest` builds, with the verdict each of its assertions expects."""
    floor = "centred wall stone 0 0 3000 100"
    return {
        # name: (lines, expected connected, expected unreachable count, extra expectation)
        "sealed": ([
            "skystrike-map 1", "mirror 1500", "spawn 0 240 180", "spawn 1 2760 180", floor,
            "mirrored wall stone 1480 100 40 1160",
        ], False, None),
        "out of fuel": ([
            "skystrike-map 1", "mirror 1500", "spawn 0 240 180", "spawn 1 2760 180", floor,
            "centred wall stone 1200 1300 600 20",
        ], True, None),
        "in fuel": ([
            "skystrike-map 1", "mirror 1500", "spawn 0 240 180", "spawn 1 2760 180", floor,
            "centred wall stone 1200 1000 600 20",
        ], True, 0),
        "tunnel door": ([
            "skystrike-map 1", "mirror 1500", "spawn 0 240 180", "spawn 1 2760 180", floor,
            "mirrored wall stone 1400 400 100 860",
            "centred door metal 1440 100 120 300 id=1",
        ], True, None),
        "pit": ([
            "skystrike-map 1", "mirror 1500", "spawn 0 240 180", "spawn 1 2760 180", floor,
            "mirrored wall stone 1400 100 40 400",
        ], True, 0),
        "spawnless": ([
            "skystrike-map 1", "mirror 1500", floor,
        ], True, 0),
        "no blocks": ([
            "skystrike-map 1", "mirror 1500", "spawn 0 240 180", "spawn 1 2760 180",
        ], True, 0),
    }


def check_m8_surfaces(findings):
    """Every named M8 surface carries stances, and none of them is out of the envelope."""
    _, spawns, blocks = parse_map(read_map_text())
    grid = Grid(spawns, blocks, DEFAULT_PITCH)
    starts, on_foot, with_fuel, connected, unreachable = grid.report()
    print("=== M8 traversal inventory, against the shared gate ===")
    sealed = sorted(unreachable)
    print("  spawns: %d, connected: %s, sealed stances: %d"
          % (len(starts), connected, len(sealed)))
    for name, left, right, bottom, top, mirrored in m8_surfaces():
        name = name + " left" if mirrored else name
        rects = [(name, left, right, top)]
        if mirrored:
            mirror_left = f32(2.0 * MIRROR_AXIS_X - right)
            rects.append((name.replace(" left", " right"), mirror_left,
                          f32(mirror_left + (right - left)), top))
        for label, rect_left, rect_right, rect_top in rects:
            stances = [stance for stance in grid.stances
                       if abs(stance[1] - rect_top) <= EPSILON
                       and rect_left - EPSILON <= grid.xs[stance[0]] <= rect_right + EPSILON]
            if not stances:
                findings.append("surface '%s' carries no stance (top %g, x %g..%g)"
                                % (label, rect_top, rect_left, rect_right))
                continue
            stances = [stance for stance in stances
                       if not in_fixture_alcove(grid.xs[stance[0]], stance[1])]
            sealed_here = [stance for stance in stances if stance in set(sealed)]
            flag = "UNREACHABLE" if sealed_here else "reachable"
            if sealed_here:
                findings.append("surface '%s' has %d unreachable stance(s), e.g. %s"
                                % (label, len(sealed_here), sealed_here[0]))
            print("  %-34s %4d stance(s)  %s" % (label, len(stances), flag))


def check_fixtures(findings):
    """The maps the Java test builds, run through the same search."""
    print("=== fixtures ArenaReachabilityTest builds ===")
    for name, (lines, expect_connected, expect_unreachable) in fixture_maps().items():
        _, spawns, blocks = parse_map(lines)
        grid = Grid(spawns, blocks, DEFAULT_PITCH)
        starts, on_foot, with_fuel, connected, unreachable = grid.report()
        if spawns:
            per_spawn = [len([index for index in range(len(grid.stances)) if index not in reached])
                         for reached in with_fuel]
        else:
            per_spawn = []
        print("  %-12s %4d stances, %d spawn(s), %s, unreachable %s, per spawn %s"
              % (name, len(grid.stances), len(starts),
                 "connected" if connected else "DISCONNECTED", len(unreachable), per_spawn))
        if connected != expect_connected:
            findings.append("fixture '%s': connected is %s, the test expects %s"
                            % (name, connected, expect_connected))
        if expect_unreachable is not None and len(unreachable) != expect_unreachable:
            findings.append("fixture '%s': %d unreachable, the test expects %d"
                            % (name, len(unreachable), expect_unreachable))
        if name == "no blocks" and len(grid.stances) != grid.column_count:
            findings.append("fixture 'no blocks': %d stances on %d columns, the test expects one"
                            " stance per column" % (len(grid.stances), grid.column_count))
        if name == "tunnel door":
            cap_only = all(abs(stance[1] - 1260.0) < 0.001 for stance in unreachable)
            print("  %-12s wall cap only: %s" % ("", cap_only))
            if not cap_only:
                findings.append("fixture 'tunnel door': unreachable stances are not only the wall"
                                " cap: " + repr(unreachable[:4]))
        if name == "pit":
            pit_floor = [stance for stance in grid.stances
                         if stance[1] == GROUND_HEIGHT
                         and 1440.0 < grid.xs[stance[0]] < 1560.0]
            print("  %-12s pit floor stances: %d" % ("", len(pit_floor)))
            if not pit_floor:
                findings.append("fixture 'pit': no stance on the pit floor")


def check_arena_verdict(findings):
    """The shipped arena's own pin: two spawns, agreeing, and one sealed pocket — the alcove."""
    _, spawns, blocks = parse_map(read_map_text())
    grid = Grid(spawns, blocks, DEFAULT_PITCH)
    starts, on_foot, with_fuel, connected, unreachable = grid.report()
    print("=== the shipped arena's pin ===")
    per_spawn = [len([index for index in range(len(grid.stances)) if index not in reached])
                 for reached in with_fuel]
    sealed_stances = sorted(unreachable)
    print("  spawns: %d, connected: %s, sealed: %d, per spawn: %s"
          % (len(starts), connected, len(sealed_stances), per_spawn))
    if len(starts) != 2:
        findings.append("the arena authors %d spawns, the test expects 2" % len(starts))
    if not connected:
        findings.append("the arena's spawns must reach each other")
    if not sealed_stances:
        findings.append("the arena seals nothing, but the test pins the fixture alcove as sealed")
    outside = [stance for stance in sealed_stances
               if not in_fixture_alcove(grid.xs[stance[0]], stance[1])]
    if outside:
        findings.append("the arena seals %d stance(s) outside the fixture alcove, e.g. %s"
                        % (len(outside), outside[:4]))
    if len(set(per_spawn)) != 1:
        findings.append("the arena's spawns disagree on what is sealed: " + repr(per_spawn))
    print("  sealed stances all inside the fixture alcove: %s" % (not outside))

def probe_maps():
    """Small maps the gate has to get right, so a passing arena is not a vacuous pass.

    Each probe is a claim about the envelope's edge, not about the shipped arena: 40% of a tank
    reaches very nearly 940 of rise, so a 1160-tall wall seals a half off and a 900-rise shelf does
    not. The expected verdicts beside each one — and, for the probe that has a door, the verdict
    under the other door reading — are what makes them evidence.
    """
    floor = "centred wall stone 0 0 3000 100"
    return {
        # (lines, spawns connected?, unreachable stances?, doors solid: connected?)
        "open": ([
            "skystrike-map 1", "mirror 1500", "spawn 0 240 180", "spawn 1 2760 180", floor,
        ], True, False, None),
        # One mirrored wall, 1160 above the floor's top, sealing the halves: more rise than the
        # budget buys, so the spawns cannot reach each other — the hard failure the gate is for.
        "sealed": ([
            "skystrike-map 1", "mirror 1500", "spawn 0 240 180", "spawn 1 2760 180", floor,
            "mirrored wall stone 1480 100 40 1160",
        ], False, True, None),
        # A centred shelf 1220 above the ground: reachable from neither spawn at 40% fuel, while the
        # spawns still reach each other. This is the difference between the two verdicts.
        "out of fuel": ([
            "skystrike-map 1", "mirror 1500", "spawn 0 240 180", "spawn 1 2760 180", floor,
            "centred wall stone 1200 1300 600 20",
        ], True, True, None),
        # The same shelf 900 up: inside the envelope, so the map is clean.
        "in fuel": ([
            "skystrike-map 1", "mirror 1500", "spawn 0 240 180", "spawn 1 2760 180", floor,
            "centred wall stone 1200 1000 600 20",
        ], True, False, None),
        # A passage in this format is a tunnel — a wall held clear of the floor — and a door is the
        # panel filling it: the map is walkable through the tunnel while the door is an opening, and
        # sealed by the wall's unhoppable height when it is not. The one probe whose connectedness
        # the door reading decides; the wall's own top is unreachable either way, 1160 up.
        "tunnel door": ([
            "skystrike-map 1", "mirror 1500", "spawn 0 240 180", "spawn 1 2760 180", floor,
            "mirrored wall stone 1400 400 100 860",
            "centred door metal 1440 100 120 300 id=1",
        ], True, True, False),
    }


def main():
    parser = argparse.ArgumentParser(description="arena reachability check")
    parser.add_argument("--pitch", type=float, default=DEFAULT_PITCH)
    parser.add_argument("--verbose", action="store_true")
    parser.add_argument("--fixtures", action="store_true",
                        help="also run the maps shared/src/test builds for the gate")
    arguments = parser.parse_args()

    findings = []
    for pitch in sorted({DEFAULT_PITCH, arguments.pitch}):
        print("=== shipped arena, pitch %.1f ===" % pitch)
        _, spawns, blocks = parse_map(read_map_text())
        grid = Grid(spawns, blocks, pitch)
        starts, on_foot, with_fuel, connected, unreachable = grid.report()
        print("  %d columns, %d stances" % (grid.column_count, len(grid.stances)))
        for index, spawn in enumerate(spawns):
            print("  team %d spawn (%.0f, %.0f): start stance x=%.1f feet=%.1f; %d on foot,"
                  " %d with fuel, %d unreachable"
                  % (spawn[0], spawn[1], spawn[2], grid.xs[grid.stances[starts[index]][0]],
                     grid.stances[starts[index]][1], len(on_foot[index]),
                     len(with_fuel[index]), len(grid.stances) - len(with_fuel[index])))
        if not connected:
            findings.append("pitch %.1f: the shipped arena's spawns cannot reach each other" % pitch)
        for stance in unreachable:
            print("  unreachable: x=%.1f feet=%.0f" % (grid.xs[stance[0]], stance[1]))
        if arguments.verbose:
            print("  reachable heights: %s"
                  % ", ".join("%.0f" % height for height in
                              sorted({grid.stances[index][1]
                                      for index in range(len(grid.stances))
                                      if index in with_fuel[0]})))

    print("=== sensitivity: the other reading of a door ===")
    _, spawns, blocks = parse_map(read_map_text(), doors_solid=True)
    solid_grid = Grid(spawns, blocks, DEFAULT_PITCH)
    starts, on_foot, with_fuel, connected, unreachable = solid_grid.report()
    print("  doors read as their movement row's SOLID panel: %d stances, spawns %s,"
          " %d unreachable from no spawn"
          % (len(solid_grid.stances), "connected" if connected else "DISCONNECTED",
             len(unreachable)))
    if not connected:
        findings.append("the shipped arena's spawns cannot reach each other with doors read as"
                        " solid, which is not a difference a door reading may make")

    check_m8_surfaces(findings)
    check_arena_verdict(findings)
    if "--fixtures" in sys.argv:
        check_fixtures(findings)

    print("=== probe maps the gate has to catch ===")
    for name, (lines, expect_connected, expect_unreachable, doors_solid_connected) in \
            probe_maps().items():
        verdicts = []
        for doors_solid in ((False, True) if doors_solid_connected is not None else (False,)):
            _, spawns, blocks = parse_map(lines, doors_solid=doors_solid)
            grid = Grid(spawns, blocks, DEFAULT_PITCH)
            starts, on_foot, with_fuel, connected, unreachable = grid.report()
            verdicts.append((doors_solid, len(grid.stances), connected, len(unreachable)))
            print("  %-12s%s %d stances, spawns %s, %d unreachable from no spawn"
                  % (name, " (doors solid)" if doors_solid else "",
                     len(grid.stances), "connected" if connected else "DISCONNECTED",
                     len(unreachable)))
        first = verdicts[0]
        if first[2] != expect_connected:
            findings.append("probe '%s': spawns are %s, expected %s" % (
                name, "connected" if first[2] else "disconnected",
                "connected" if expect_connected else "disconnected"))
        if bool(first[3]) != expect_unreachable:
            findings.append("probe '%s': %d stances unreachable from no spawn, expected %s"
                            % (name, first[3], "some" if expect_unreachable else "none"))
        if doors_solid_connected is not None and verdicts[1][2] != doors_solid_connected:
            findings.append("probe '%s' with doors solid: spawns are %s, expected %s" % (
                name, "connected" if verdicts[1][2] else "disconnected",
                "connected" if doors_solid_connected else "disconnected"))

    for finding in findings:
        print("FINDING: %s" % finding)
    if findings:
        return 1
    print("the gate behaves: the shipped arena's spawns reach each other under both door readings,"
          " the sealed and out-of-fuel probes are caught, and the open and in-fuel probes pass")
    return 0


if __name__ == "__main__":
    sys.exit(main())
