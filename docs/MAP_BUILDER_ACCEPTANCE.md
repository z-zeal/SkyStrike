# Map building system — acceptance checklist

**Status: unexecuted. Every box below is unchecked.** Milestone 1 landed the shared block model,
rule table and strict version-1 text parser/writer, with the legacy arena loaded through its
canonical embedded text, a version-2 SDF map-hash guard, and shared tests written for migration,
round trips and SDF identity. Milestone 2 then made every consumer read the shared table: the
rule-aware line queries (a shared `Blocking` purpose on `MapQueries`, one cached per-purpose
rectangle view on `ArenaMap`, the sight column in `VisionMath`, the bullet column in the raycaster
and the muzzle guard, and the blast column — pinned to sight — in the frag, claymore-cone and
fire-spread consumers); the movement column with one-way platform tops and the drop-through
window in `PlayerMotion` (its trigger moved from the crouch+jump chord to a fresh S press after
the first live run — plan §4, D6), plus the explicit columns in `DroneMotion`,
`ThrowablePhysics` and the camera's release check; and kind-aware rendering, where `TerrainRenderer`
draws every `BlockRenderStyle` and the minimap reads the same block list. Two static gates now
enforce the milestone's contract, and tests were written for the one-way rules, the movement
questions and the wire layout.
Milestone 3 then added the material table and its damage rules, the authored per-block durability
multiplier, the per-round destruction state, block damage from rounds, blasts and swings, the
`GLASS_SHATTER` effect, and the runtime SDF re-bake path (worker plus changed-region texture patch),
with tests written for all of it.
So that those kinds can be met before the editor exists (milestone 5), the shipped arena now authors
one mirrored **fixture range** — a timber one-way platform high over the centre of the map carrying
a pane and a wooden crate (`durability=0.4`) — and `assets/data/arena.sdf` was re-baked in the same
change, so the boxes about standing on, passing through, shattering, chipping and breaking a pane, a
crate or a platform are reachable in a running build today. The destruction half of milestone 4
has since landed in source: the protocol-14 block event and join baseline, the client's
destruction mirror feeding prediction, rendering and the minimap, and the first runtime re-bake
caller — so a destroyed block should now disappear, stop colliding and stop shadowing on every
client. The work does **not** yet ship an `assets/maps` file, an editor or `MapTool`, door state,
per-hit crack sync, or the server-side map-hash join rejection. Boxes that need a door, a saved
map file or the editor therefore remain unreachable; the destruction boxes are now within reach
but unattempted, like all the rest.

**Update — the door slice of milestone 4 has now landed in source as well:** the use key with the
shared reach rule and the optimistic client prediction, the server's door authority (occupancy
from every live body and every thrown utility; refused presses answered by a console warning and,
for a blocked swing, an authoritative correction record), the protocol-15 `PacketDoorState` wire
with its join-payload baseline, the dynamic door occluder array in both the visibility and the
light shader, and posing of every door's panel from the machine's swing fraction while the SDF
keeps naming the destruction-only static map. Nothing above is ticked: every box remains
unchecked, and the "Doors" section in particular stays **unreachable** in a running build for
now — the shipped arena still authors no door, by decision the fixture range was not extended
for this slice, so there is no contained doorway to walk up to and press `F` at. The day one is
authored, those boxes become attemptable without further code work.
Nothing in this repository was
built, launched or observed: the authoring sandbox has no JDK, so not even the shared tests could
be executed here.

**Update — the milestone-4 close-out has opened with the handshake's map guard, now enforced on
both sides (protocol 16).** The join request states the map the client parsed, the server refuses a
join whose stated hash is not the map it is running, and the refusal carries the server's own hash,
so the line a refused player sees has both sides of the comparison. There is no box below for this
one — it is a handshake fact rather than a thing to tick — but it is watchable in a desktop run in
a way the earlier half was not: stage a client on a different map text and the join is refused with
`map hash mismatch (server plays …, this client parsed …)` instead of a match that silently
disagrees about what a wall is. Still open in the close-out: per-hit crack sync and the round-reset
event, so the crack-state boxes ("the breakable's crack state matches the number of hits it has
taken") and the reset box ("destruction resets … the two clients agree") stay unreachable for now.
Every box above remains unchecked.

**Update — the close-out's second slice has landed: per-hit crack sync (protocol 17).** A hit that
leaves a breakable standing now travels as its own record — the authority's accumulated damage for
that block, not the hit's size — broadcast reliably to every client and replayed to a joiner as a
per-block crack baseline, so the client's mirror can only converge on the authority's number and
never overshoot it. That makes the crack-state box ("Bullets stop on walls and on intact
breakables, and the breakable's crack state matches the number of hits it has taken") attemptable
on a desktop build for the first time: until now a client drew every standing breakable pristine.
The reset box still needs the round-reset event, which is the close-out's next slice. Every box
above remains unchecked.

**Update — the close-out's third slice has landed: the round-reset event, with `/mapreset` to run
it (protocol 18).** One server-side transaction puts the authored map back, shuts every door,
re-seeds the door diff baseline and broadcasts `PacketRoundReset` reliably before the next tick can
reference the restored map; the client answers by running the same `MapDamageState.reset()` the
server ran, so the event cannot be misapplied and no per-block detail travels. That makes the reset
box in "Breakables and the SDF" attemptable in a desktop run for the first time: chew the map up
with two clients (and leave a door open), run `/mapreset` from the server console as an ADMIN, and
both clients plus a late joiner should agree that the walls, the crates and the doors are back. The
match layer that decides a round has ended is still later work — the operator command is the hand
that runs it today — and the door boxes below stay unreachable until the close-out's last slice
authors a door into the shipped arena. Every box above remains unchecked.

**Update — the close-out's last slice has landed: the fixture door.** The shipped arena authors one
mirrored metal door — ids 1 and 2, both `ALL` — sealing the ground lane between the tall wall at
`x` 1010..1034 and the shorter one at 1070..1092 (the pair stands at 1034..1070 and 1930..1966,
`y` 100..270), so the whole "Doors" section is attemptable in a running build, with one exception: no
`TEAM` door is authored, so the team-refusal box (a team door opens for its own team and refuses the
other) stays unreachable — a mirrored pair shares one access by construction, and per-side ownership
would mean authoring two separate lines with their own ids. No protocol change came with it — doors
have been stateful and broadcast since protocol 15 — but the map's identity moved and
`assets/data/arena.sdf` was re-baked in the same change: a door is not baked geometry, so every texel
stayed and only the header's hash moved. Every box above remains unchecked.

**Update — the fixture door has been moved to a reachable position (next slice, same branch, not
milestone 6).** The door pair that sealed the ground lane at `x` 1034..1070 (and its mirror at
1930..1966) has been moved to a reachable position inside the 70-wide mirrored wall at `x` 820..890:
the door now stands at `x` 837..873 and its mirror at 2127..2163, `y` 100..270, centred in that wall.
The map text changed, so `assets/data/arena.sdf` was re-baked under the new map hash
`ed6b335d...e173e` (still 1500 × 1000 texels at 2.0 units/texel, 1,500,060 bytes) and
`arena_sdf_check.py` re-confirmed the committed asset is the bake of the committed map. The door
stances at `x` 837.5 (`y` 236, 518, 918) are reachable from both spawns, so a player can walk to the
door and press `F` — the §Doors boxes now name a real doorway. The 10 unreachable stances the CI gate
reports for the shipped arena are **not** eliminated by this move: they sit inside the volume the tall
wall at `x` 1010..1034 and the shorter one at 1070..1092 enclose, blocked by that wall geometry, not
by the door (the door is read as an opening by both the Java gate and the Python mirror, so it never
blocks anything). They persist at their old positions, and the test pins that name them by those
coordinates still hold. The team-refusal box stays unreachable for the same reason it was before.

Tick a box only from a running desktop build (two clients where the check says so), after the
automated tests for that milestone are green in CI. The CI-side tests are listed in
`docs/archive/MAP_BUILDER_PLAN.md` §7 and are the first gate; this document is the second.

## Format, load and save

- [ ] A shipped map (`assets/maps/*.map`) loads in a fresh session; the arena looks and behaves
  exactly as the built-in one did before the migration.
- [ ] A map authored in the editor saves, reloads in a new session, and is byte-identical to what
  was saved (save → load → save).
- [ ] A deliberately broken file (unknown verb, unknown kind, out-of-bounds block, duplicate door
  id, a block straddling the mirror axis) is rejected with the documented message, and the game
  falls back to the last valid map instead of a half-loaded one.
- [ ] A map file and its baked SDF from different edits are refused (no shadows or particle
  collision that disagree with the walls); a development build re-bakes and says so in the log.

## Symmetry and layout

- [ ] `cl_freecam` inspection of a newly authored map shows every block mirrored about `x = 1500`:
  walls, glass, doors, platforms and breakables alike.
- [ ] Spawns are on the ground, inside the arena, and not inside geometry.
- [ ] Every spawn can reach every objective without the geometry trapping a player (the CI
  reachability test is the first line, this is the eyeball).

## Movement and one-way platforms

- [ ] Jumping up through a platform passes through it, and landing on it from above stands on it.
- [ ] Pressing S (crouch) on a platform drops the player through, and the fall does not re-land on
  the same platform mid-drop; a crouch held from before the landing does not drop, and a jump
  press while crouched on the slab does not launch the body up through it.
- [ ] A player standing under a platform is not blocked from jumping; a player whose head is in the
  slab is not ejected.
- [ ] Glass stops a running player while intact; a shattered window does not.
- [ ] A closed door stops a player and the player cannot open it while standing in the swing.

## Bullets, blast and glass

- [ ] Bullets pass through intact glass, shatter it, and continue with a visible (small) speed
  loss; the shattering round does not stop dead.
- [ ] Bullets stop on walls and on intact breakables, and the breakable's crack state matches the
  number of hits it has taken.
- [ ] Explosions damage breakables within their radius, do not damage them through a wall, and
  destroy them at zero durability with the material's debris and sound.
- [ ] Bullets that pass through a window cannot hit a player standing behind a wall behind it.
- [ ] A destroyed breakable stops blocking movement, sight and bullets in the same tick its debris
  appears.

## Rendering

- [ ] Each kind is visibly its own thing on a mixed map: a wall and an intact breakable are fills, a
  window reads as a translucent pane with a sheen, a platform's landing edge is highlighted rather
  than boxed, and a door is drawn as a panel (closed, until door state ships).
- [ ] The picture matches the collision: the drawn rectangle is the rectangle that stops a body,
  and a block that only stops sight or only stops rounds never looks like a wall.
- [ ] The minimap shows the same geometry the terrain pass draws, including glass and platforms.

## Doors

- [ ] A closed door blocks movement, bullets and sight; an open one blocks none of them.
- [ ] Opening and closing is smooth on both clients; a late-joining client sees the correct state
  immediately.
- [ ] A door blocked by a body or a thrown object stops opening, and both clients show the same
  stop.
- [ ] A team door opens for its own team and refuses the other; the refusal is visible (no silent
  no-op) and the client prediction corrects cleanly.
- [ ] The open door does not occlude: standing in a doorway, the visibility cone and the player
  light pass through it exactly as through open air.
- [ ] The closed door *does* occlude once the dynamic occluder array lands: the cone does not see
  through a closed door, and the SDF still shadows it if the design keeps both.
- [ ] A client modified to believe a door is open cannot walk through it (server-authoritative), and
  its own door re-syncs on the next snapshot.

## Breakables and the SDF

- [ ] Destroying a breakable re-bakes the SDF once: shadows, light occlusion and particle collision
  update, with one visible hitch at most on the frame it happens.
- [ ] Two clients see the same destroyed set after a late join and after a reconnect.
- [ ] Destruction resets (or persists) exactly as decided for the match; the two clients agree.
- [ ] The debug SDF view (`cl_sdf`, if enabled) after a re-bake matches the existing map geometry,
  with no ghost shadow where the wall used to be.

## Editor and CLI

- [ ] The editor is reachable only behind the debug master switch (`-Dskystrike.debug=true` /
  `--dev`) and never in a release build.
- [ ] Editing on a touch device works: blocks can be placed, mirrored and deleted without a mouse.
- [ ] `MapTool check` catches a broken map in CI (the run is visible in the checks list) and
  `MapTool bake` reproduces the shipped SDF byte-for-byte for an unchanged map.
- [ ] The editor's reachability report marks an unreachable objective, and the CI gate fails on the
  same map.
- [ ] The editor cannot publish to a running match: a session's map changes only through a server
  revision.

**Status — milestone 5 slice A (PR #63), appended 2026-10-12.** None of the five boxes above is
ticked, and none is attemptable in this environment, which has no JDK: everything in this slice was
written and not executed. What the slice does add is the half of the first four boxes that is not a
screen: `MapReachability.check(ArenaMap)` returns a `Report` naming every stance nothing can reach,
whether the spawns reach each other and the full list of sealed stances, so the editor's overlay and
the CLI's listing both draw from one statement of what reachability means; and `MapDraft` holds the
place / mirror / delete gestures as authored verbs, with `build()` going through the same
`ArenaMap.Builder` the file format uses and `toText()` through the same canonical writer, so an
in-game save and a saved file are the same bytes. Still missing for these boxes: `MapEditorScreen`
and the debug-switch gating (slice C), the `MapTool check|bake` task and its CI step (slice B, which
also has to decide where that task lives — `tools` is not a Gradle module), and a desktop build. The
reachability report against the shipped arena, from the source-only mirror in `tools/scratch`, is
1526 stances on 600 columns, spawns connected, 10 stances sealed in the fixture alcove; see §11.4 of
the plan for why that last number is the decision slice B is waiting on rather than a defect.

**Status — milestone 5 slice B (PR #64), appended 2026-10-12.** None of the five boxes is ticked
here: this environment has no JDK, so the CLI, the Gradle tasks and both new CI steps were written
and not executed, and no manual box can be ticked without a desktop. The third box is the one this
slice moves from "missing" to "attemptable by a human watching CI": `MapTool check` exists and CI
runs it as its own named step (`tools:mapCheck`), so a broken map is caught in the run rather than
in a playtest — with §11.4 now decided (a map whose spawns cannot reach each other fails; a merely
unreachable pocket is a named warning), and `MapTool bake --check` as a second step
(`tools:mapBakeCheck`) re-bakes the shipped arena in memory and compares it byte for byte with
`assets/data/arena.sdf`, which is the "reproduces the shipped SDF byte-for-byte" half of that same
box. What is left to a human is the observing: the checks list on a real run, and the desktop
re-run. The fourth box stays half-attemptable: the CI gate and its reading exist, but "the editor's
reachability report marks an unreachable objective" is `MapEditorScreen` and waits for slice C.
Nothing here touches the wire: `PROTOCOL_VERSION` stays 18, and a map cannot reach a running match
through the CLI — `MapTool` reads and writes files and has no network path at all.

**Status — milestone 5 slice C, appended 2026-10-12.** None of the five boxes is ticked, and none
can be: this environment has no JDK, so the screen, the palette and their tests were written and
not executed, and the boxes are desktop observations by definition. Three of them become
attemptable for a human with a debug desktop build. The first — reachable only behind the debug
master switch — is now a run with `--dev` (the menu lists "Map editor") and a run without it (the
menu is the four standard actions and there is no editor entry to find, because the action is
spliced in only while `DebugFlags.enabled()`); the release-build half is still a build nobody has
made. The second — touch editing — is the canvas: one finger places the armed palette entry, a
finger that moves more than 14 pixels pans instead, and a tap with Delete armed removes the block
under it; what a human cannot check here is the feel of it, and no emulator or device has run it.
The fifth — the editor cannot publish — is attemptable two ways: structurally, by reading
`Main.openMapEditor()`, which constructs the screen with the route back to the menu and nothing else
(no session, no socket, no match state, so there is no path to publish with), and by behaviour, by
authoring a map while a session runs and seeing the session unaffected. The fourth box is the one
this slice completes on the editor side: Validate runs the shared gate over the draft written and
parsed back — the map a fresh session would load, not the in-memory block list — prints
`MapAudit.Result.lines()` and marks `Result.pocket()` in red on the canvas, and the strict-reading
note appears exactly when the two readings disagree, which on the shipped arena is the alcove the CI
step deliberately warns about (§11.4). The third box is unchanged from slice B and still waits on a
CI run being watched by a human. `PROTOCOL_VERSION` stays 18, and no map text was touched, so
`assets/data/arena.sdf` is the same asset it was — the editor writes new files into the user's own
directory and never into the repository.

## Performance

- [ ] A map with the maximum shipped block count holds 60 Hz on the reference desktop with the
  visibility, light and particle passes running.
- [ ] The door occluder array is capped as designed; a map over the cap is a load error, not a
  silent drop of a door.
- [ ] Repeated breakable destruction in a long match does not grow memory or the SDF uploading cost
  per frame.

## Verification note

No Java, Gradle task, unit test, server, desktop client or checklist item was run in the authoring
sandbox. The source-only checks run after this increment were:

- `python3 tools/scratch/static_check.py` — clean (406 Java files scanned, including the
  module-boundary rules and the two new map gates: no main source outside `shared/map` names a
  block kind, and every declared `BlockRenderStyle` is drawn by `TerrainRenderer`).
- `python3 tools/scratch/static_api_check.py` — clean (503 project types indexed from 402 files).
- `python3 tools/scratch/static_audio_check.py` — clean.
- `python3 tools/scratch/synth_sfx.py --check` — clean (13 generated sounds agree).
- `python3 tools/scratch/phase3_ballistics.py` — completed its analytical report without findings.
- `python3 tools/weapons/generate_weapon_tables.py` — regenerated no tracked diff.

The two new gates were confirmed to fire rather than merely to pass: a temporary consumer that
named `BlockKind`, and one that switched over a block kind with bare `case` labels, were both
reported, and deleting a treatment from `TerrainRenderer` was reported as undrawn. Those edits were
reverted before the checks above were re-run clean.

These checks do not compile or execute the shared and server tests written for milestones 1 and 2
(`MapQueryPurposeTest`, `VisionMathTest`, `RaycastBulletSystemTest`, `PlatformMotionTest`,
`NetworkRegistrationTest`, `MinimapModelTest`) and do not validate runtime map loading, the
rendered treatments, one-way platform behaviour or any other kind's runtime behaviour; every manual
box above remains unchecked.

A separate held-utility rendering correction was source-checked in this pass. It is not map
validation, and every manual box above remains unchecked. The exact checks run were:

- `python3 tools/scratch/static_check.py` — clean (406 Java files; no findings).
- `python3 tools/scratch/static_api_check.py` — clean (503 project types indexed from 402 files).
- `python3 tools/scratch/static_audio_check.py` — clean.
- `python3 tools/scratch/synth_sfx.py --check` — clean (13 generated sounds agree).
- `python3 tools/scratch/phase3_ballistics.py` — analytical report completed.
- `python3 tools/weapons/generate_weapon_tables.py` — completed with no tracked generated-table diff.

A separate server-side utility/loadout diagnostics increment was also source-checked; it is not map
validation, and every manual box above remains unchecked. The exact checks run were:

- `python3 tools/scratch/static_check.py` — clean (406 Java files; no findings).
- `python3 tools/scratch/static_api_check.py` — clean (505 project types indexed from 402 files).
- `python3 tools/scratch/static_audio_check.py` — clean (catalogue paths, formats and durations agree).
- `python3 tools/scratch/synth_sfx.py --check` — clean (13 generated sounds agree).
- `python3 tools/scratch/phase3_ballistics.py` — analytical report completed.
- `python3 tools/weapons/generate_weapon_tables.py` — completed with no tracked generated-table diff.

No Java compilation, tests, server/game run, desktop build, CI check or manual acceptance was run.

Milestone 3 (breakables, glass and the SDF re-bake) was source-checked only; it is not map
validation, and every box above remains unchecked. The exact checks run were:

- `python3 tools/scratch/static_check.py` — clean (419 Java files scanned, 419 declared types; no
  findings).
- `python3 tools/scratch/static_api_check.py` — clean (520 project types indexed from 415 files).
- `python3 tools/scratch/static_audio_check.py` — clean (13 world entries, 6 weapon events, 1 loop,
  14 effect types, 30 manifest entries).
- `python3 tools/scratch/synth_sfx.py --check` — clean (14 generated sounds agree).
- `python3 tools/scratch/phase3_ballistics.py` — analytical report completed without findings.
- `python3 tools/weapons/generate_weapon_tables.py` — regenerated 89 gun rows and 21 melee rows with
  no tracked diff.

What those checks do and do not mean. `static_check.py` re-verified the module boundaries and both
milestone-2 consumer gates (no main source outside `shared/map` names a block kind or switches on
one; `TerrainRenderer` mentions every declared `BlockRenderStyle`) over the new damage paths, and
`static_api_check.py` re-resolved the project's declared type surface — but neither can see an
instance call made through a field or a variable, so the new calls (`RaycastBulletSystem
.damageableCrossings`, `MapDamageState.damage`, `SdfTexture.patch`, `GpuParticleSystem.setSdfField`,
`FxPipeline.rebakeSdf`, `MeleeSystem.damageBlocks`) were also read by hand, and only a JDK compile
can truly confirm them. A one-off analysis script (not one of the standing six checks)
re-implemented `SdfBaker` in Python from the committed source and reproduced every texel of the
shipped `assets/data/arena.sdf`, and re-implemented `MapHash` to confirm the asset's header still
names the standard arena after the per-block durability field landed — a default multiplier is not
hashed, so the shipped asset needed no re-bake and its hash is unchanged. No Java was compiled, no
test was executed, and no server, client or game was run: the new `MaterialTableTest`,
`MapDamageStateTest`,
`SdfRebakeTest`, `BlockDestructionTest` and `UtilityBlockDestructionTest` additions, and every
earlier suite, remain unexecuted. Everything a human with a runnable desktop build must do — run the
Java tests in `shared` and `server`, then walk the boxes above on two clients — remains outstanding.

The shipped-arena fixture range was then source-checked (the arena text, the re-baked
`assets/data/arena.sdf`, the format/state/blast test updates and the new asset checker). It is not
map validation, and every box above remains unchecked. The exact checks run were:

- `python3 tools/scratch/static_check.py` — clean (419 Java files scanned, 419 declared types; no
  findings).
- `python3 tools/scratch/static_api_check.py` — clean (520 project types indexed from 415 files).
- `python3 tools/scratch/static_audio_check.py` — clean (13 world entries, 6 weapon events, 1 loop,
  14 effect types, 30 manifest entries).
- `python3 tools/scratch/synth_sfx.py --check` — clean (14 generated sounds agree).
- `python3 tools/scratch/phase3_ballistics.py` — analytical report completed without findings.
- `python3 tools/weapons/generate_weapon_tables.py` — regenerated 89 gun rows and 21 melee rows with
  no tracked diff.
- `python3 tools/scratch/arena_sdf_check.py` — clean: it re-implemented `MapHash`, the `SdfBaker`
  rasterisation and its 8SSEDT pass, Java's `Math.round` and float32 rounding, and the 60-byte
  version-2 header in Python; the harness was validated first by reproducing the **pre-change**
  asset byte-for-byte, then used to re-bake `assets/data/arena.sdf` for the new map text. It now
  reports the committed asset as the bake of the committed map — header hash
  `bef76535455be93d0fd3f845a92dee9a6c68053c4e2dc8159c7210a9cca26693`, 1500 × 1000 texels at 2.0
  units/texel, 1,500,060 bytes, every texel equal.

What that does and does not mean. The Python bake is an independent second implementation, so a
texel-level agreement is real evidence for the parts both implementations share (rasterisation
order, distance transform, header layout, the map hash), but it is not the client: it cannot prove
that `SdfTexture` uploads the field, that a re-bake patches the right region at runtime, or that
the field the shipping-default guard accepts is the one drawn. The arena change itself was requested
so that a running game *can* meet glass, a crate and a one-way platform before the editor ships; the
manual boxes about shooting, shattering and dropping through them are now within reach, and the boxes
about a saved map, a door or a client-side re-bake are not. No Java was compiled, no test was
executed, and no server, client or game was run.

The first live run's triage and the S-alone drop-through (this session) were then source-checked
only; they are not map validation, and every box above remains unchecked. The run itself — the
user's desktop build, jetpack ascent to the fixture platform, shooting the pane from several
angles, walking through the pane and the crate on a plain non-debug body — is the authoritative
observation this triage worked from. What the source reading established: the drop-through
trigger is now a fresh S press (see the amended box above; its test is
`PlatformMotionTest.crouchPressAloneDropsThroughAndDoesNotReland`, unexecuted), and the three
pass-through/no-shatter findings cannot be produced by the current source — every hop from the
rule table through `ArenaMap`'s derived views, `MapDamageState.liveMap()`, `PlayerMotion`'s
sweep, the muzzle guard, `RaycastBulletSystem`/`BulletSystem`'s nearest-first walk and the
shatter was read and is correct, while the fixture platform, from the same three map lines, held
the body in that same run. The leading remaining explanation is two processes on different maps,
which the local-test path can produce silently (`Main.startLocalTest` joins whatever already
owns the default ports when the embedded host fails to bind); both sides now print
`ArenaMap.describe()` — census, mirror axis, hash — at startup so the next run settles it in one
glance against the pinned hash `686c9165…a597` and the census `64 blocks [WALL 56, GLASS 2,
BREAKABLE 2, PLATFORM 2, DOOR 2]`. The exact checks run after these changes were:

- `python3 tools/scratch/static_check.py` — clean (419 Java files scanned, 419 declared types; no
  findings, including the module boundaries and the two consumer gates).
- `python3 tools/scratch/static_api_check.py` — clean (520 project types indexed from 415 files).
- `python3 tools/scratch/static_audio_check.py` — clean (13 world entries, 6 weapon events, 1
  loop, 14 effect types, 30 manifest entries).
- `python3 tools/scratch/synth_sfx.py --check` — clean (14 generated sounds agree).
- `python3 tools/scratch/phase3_ballistics.py` — completed its analytical report without findings.
- `python3 tools/weapons/generate_weapon_tables.py` — completed with no tracked generated-table
  diff.
- `python3 tools/scratch/arena_sdf_check.py` — clean: the committed asset is still the bake of
  the committed map (header hash `bef76535…6693`, every texel equal); neither `TEXT` nor the
  asset changed this session.

What those checks do and do not mean. They prove no syntax, boundary, gate or generated-table
regression across the session's edits; `static_api_check.py` cannot see calls made through
fields or variables, so the session's cross-module calls (`ArenaMap.describe`,
`ArenaMap.mapHashHex`, the S-alone drop branch's use of the transient `dropThroughTimer`) were
also read by hand. Nothing was compiled, nothing was executed, nothing was observed: the new
`describeAnnouncesTheCensusMirrorAxisAndHash` test, the two rewritten and three added
`PlatformMotionTest` cases, and every earlier suite remain unexecuted, and the re-run of the
fixture range with the startup identity lines visible is outstanding human work.

The destruction half of milestone 4 (protocol 14) was then source-checked only; it is not map
validation, and every box above remains unchecked. The user's rebuilt run had confirmed the
server side first — destruction effects firing proves the server destroys blocks end to end —
and this slice wires the client to that fact: the authored-index identity, the reliably
broadcast block event, the join baseline against the stated map hash, the snapshot revision,
and the client's destruction mirror feeding prediction, the terrain pass, the minimap and the
first runtime SDF re-bake. What the client still cannot show is per-hit cracks (chips do not
travel — a breakable reads pristine until the hit that opens it), and what the wire still does
not carry is doors, the server-side map-hash join rejection or a round-reset event. The exact
checks run after these changes were:

- `python3 tools/scratch/static_check.py` — clean (420 Java files scanned, 420 declared types; no
  findings, including the module boundaries and the two consumer gates).
- `python3 tools/scratch/static_api_check.py` — clean (521 project types indexed from 416 files).
- `python3 tools/scratch/static_audio_check.py` — clean (13 world entries, 6 weapon events, 1
  loop, 14 effect types, 30 manifest entries).
- `python3 tools/scratch/synth_sfx.py --check` — clean (14 generated sounds agree).
- `python3 tools/scratch/phase3_ballistics.py` — completed its analytical report without findings.
- `python3 tools/weapons/generate_weapon_tables.py` — completed with no tracked generated-table
  diff.
- `python3 tools/scratch/arena_sdf_check.py` — not run: neither `StandardArenaMap.TEXT` nor
  `assets/data/arena.sdf` changed this session.

What those checks do and do not mean. They prove no syntax, boundary, gate or generated-table
regression across the session's edits; `static_api_check.py` cannot see calls made through
fields or variables, so the session's cross-module calls (`MapDamageState.breaks`,
`authoredIndexOf` and `applyRemoteBreak`, `MinimapModel.setArena`, the `PacketBlockBreak`
broadcast and join assembly, `ClientSession`'s retained accept and break replay,
`GameScreen`'s mirror application and `FxPipeline.rebakeSdf`) were also read by hand. Nothing
was compiled, nothing was executed, nothing was observed: the new `MapDamageStateTest`,
`NetworkRegistrationTest` and `MinimapModelTest` cases and every earlier suite remain
unexecuted. Everything a human with a runnable desktop build must do — rebuild both sides,
confirm the startup identity lines, then shoot the pane and the crate, walk and shoot through
where they stood, confirm the re-bake and the late joiner, and press S on the platform —
remains outstanding, and every box above stays unchecked until that run.

The door slice of milestone 4 (protocol 15: the use key and its client prediction, the server's
door authority with body/utility occupancy and refused-press answers, the reliable door-state
wire and join baseline, the dynamic occluder array in both shaders, and the posed-door
rendering that leaves the SDF naming the destruction-only static map) was then source-checked
only; it is not map validation, and every box above remains unchecked. The exact checks run
after these changes were:

- `python3 tools/scratch/static_check.py` — clean (431 Java files scanned, 431 declared types;
  no findings, including the module boundaries and both consumer gates).
- `python3 tools/scratch/static_api_check.py` — clean (534 project types indexed from 427
  files).
- `python3 tools/scratch/static_audio_check.py` — clean (13 world entries, 6 weapon events, 1
  loop, 14 effect types, 30 manifest entries).
- `python3 tools/scratch/synth_sfx.py --check` — clean (14 generated sounds agree).
- `python3 tools/scratch/phase3_ballistics.py` — completed its analytical report without
  findings.
- `python3 tools/weapons/generate_weapon_tables.py` — completed with no tracked generated-table
  diff.
- `python3 tools/scratch/arena_sdf_check.py` — run as belt-and-braces even though neither
  `StandardArenaMap.TEXT` nor `assets/data/arena.sdf` changed this increment; clean (the
  shipped asset agrees with the committed map on header hash, header shape and every texel).

What those checks do and do not mean. They prove no syntax, boundary, gate or generated-table
regression across the slice's edits; `static_api_check.py` cannot see calls made through fields
or variables, so the slice's cross-module calls (`DoorUseController` → the shared `MapQueries`,
`MapDoorState` and `PacketPlayerInput` surfaces; `PlayerSession.consumeUsePress`; the
`DoorSystem` press/advance/occupancy paths; `JoinRequestHandler`'s door-baseline assembly; the
`GameServer` diff broadcast and correction unicast; `ClientSession`'s door replay buffer;
`GameScreen`'s record translation, occlusion feed, door-clock and posed-panel call; the
`DoorOccluders` → `VisibilitySystem`/`LightPass` shader uploads; and
`TerrainRenderer.doorPanelRect`) were also read by hand. Nothing was compiled, nothing was
executed, nothing was observed: the new `MapDamageStateTest`, `NetworkRegistrationTest`,
`DoorSystemTest` and `PlayerSessionTest` cases and every earlier suite remain unexecuted.
Everything a human with a runnable desktop build must do — rebuild
both sides, author one door into a playable map (none is shipped), then walk the door boxes
above with two clients and a late joiner — remains outstanding, and every box above stays
unchecked until that run.

The close-out's first slice (protocol 16: the map-hash join gate) was then source-checked only; it
is not map validation, and every box above remains unchecked. The exact checks run were:

- `python3 tools/scratch/static_check.py` — clean (432 Java files scanned, 432 declared types; no
  findings, including the module boundaries and both consumer gates).
- `python3 tools/scratch/static_api_check.py` — clean (535 project types indexed from 428 files).
- `python3 tools/scratch/static_audio_check.py` — clean (13 world entries, 6 weapon events, 1 loop,
  14 effect types, 30 manifest entries).
- `python3 tools/scratch/synth_sfx.py --check` — clean (14 generated sounds agree).
- `python3 tools/scratch/phase3_ballistics.py` — completed its analytical report without findings.
- `python3 tools/weapons/generate_weapon_tables.py` — regenerated 89 gun rows and 21 melee rows with
  no tracked diff.
- `python3 tools/scratch/arena_sdf_check.py` — clean: the committed asset is still the bake of the
  committed map (header hash `bef76535…6693`, every texel equal); neither `StandardArenaMap.TEXT`
  nor the asset changed this slice.

What those checks do and do not mean. They prove no syntax, boundary, gate or generated-table
regression across the slice's edits; `static_api_check.py` cannot see calls made through fields or
variables, so the slice's cross-module calls (`ArenaMap.standard().mapHashHex` on the client's join
path, `MapDamageState.authoredMap` in the gate's caller, the `PacketJoinRequest`/`PacketJoinReject`
schema change, `ClientSession`'s refusal-detail assembly and the package-private `mapRefusal` the
new server test calls) were also read by hand. Nothing was compiled, nothing was executed, nothing
was observed: the new handshake round trips, the protocol-16 pin, the `mapRefusal` cases and every
earlier suite remain unexecuted, and the staged-client check that watches the refusal fire is
outstanding human work.

The close-out's second slice (protocol 17: per-hit crack sync — the chip record carrying the
authority's running total, the reliable broadcast of the tail, the per-block crack baseline on the
join accept, and the mirror that applies totals instead of adding them) was then source-checked
only; it is not map validation, and every box above remains unchecked. The exact checks run were:

- `python3 tools/scratch/static_check.py` — clean (434 Java files scanned, 434 declared types; no
  findings, including the module boundaries and both consumer gates).
- `python3 tools/scratch/static_api_check.py` — clean (537 project types indexed from 430 files).
- `python3 tools/scratch/static_audio_check.py` — clean (13 world entries, 6 weapon events, 1 loop,
  14 effect types, 30 manifest entries).
- `python3 tools/scratch/synth_sfx.py --check` — clean (14 generated sounds agree).
- `python3 tools/scratch/phase3_ballistics.py` — completed its analytical report without findings.
- `python3 tools/weapons/generate_weapon_tables.py` — regenerated 89 gun rows and 21 melee rows with
  no tracked diff.
- `python3 tools/scratch/arena_sdf_check.py` — clean: the committed asset is still the bake of the
  committed map (header hash `bef76535…6693`, every texel equal); neither `StandardArenaMap.TEXT`
  nor the asset changed this slice.

What those checks do and do not mean. They prove no syntax, boundary, gate or generated-table
regression across the slice's edits; `static_api_check.py` cannot see calls made through fields or
variables, so the slice's cross-module calls (`MapDamageState.chips`/`damagedBlocks`/
`applyRemoteDamage`, the `PacketBlockDamage` broadcast diff and join-baseline assembly,
`ClientSession`'s chip replay buffer and `GameScreen`'s mirror application) were also read by hand.
Nothing was compiled, nothing was executed, nothing was observed: the new chip cases in
`MapDamageStateTest` and `BlockDestructionTest`, the protocol-17 pin and round trips, and every
earlier suite remain unexecuted, and the two-client crack pass — shoot a crate short of breaking it
on both clients and watch the cracks match, then have a third client join late — is outstanding
human work.

The close-out's third slice (protocol 18: the round-reset event, its one server-side transaction,
the command seam `/mapreset` runs it through, the client's unconditional refresh, and the replayed
record across the join window) was then source-checked only; it is not map validation, and every
box above remains unchecked. The exact checks run were:

- `python3 tools/scratch/static_check.py` — clean (435 Java files scanned, 435 declared types; no
  findings, including the module boundaries and both consumer gates).
- `python3 tools/scratch/static_api_check.py` — clean (539 project types indexed from 431 files).
- `python3 tools/scratch/static_audio_check.py` — clean (13 world entries, 6 weapon events, 1 loop,
  14 effect types, 30 manifest entries).
- `python3 tools/scratch/synth_sfx.py --check` — clean (14 generated sounds agree).
- `python3 tools/scratch/phase3_ballistics.py` — completed its analytical report without findings.
- `python3 tools/weapons/generate_weapon_tables.py` — regenerated 89 gun rows and 21 melee rows with
  no tracked diff.
- `python3 tools/scratch/arena_sdf_check.py` — clean: the committed asset is still the bake of the
  committed map (header hash `bef76535…6693`, every texel equal); neither `StandardArenaMap.TEXT`
  nor the asset changed this slice.

What those checks do and do not mean. They prove no syntax, boundary, gate or generated-table
regression across the slice's edits; `static_api_check.py` cannot see calls made through fields or
variables (it did, however, catch the `Deps` arity at both test call sites — the one class of
mistake it can see), so the slice's cross-module calls (`GameServer.resetMap`'s use of
`MapDamageState.reset`, `MapDoorState.reset`'s revision bump behind the live map, the re-seeded
`doorBroadcast` baseline, `loop.clock().tick`, `ClientSession`'s reset replay buffer and
`GameScreen`'s `onRoundReset` refresh) were also read by hand. Nothing was compiled, nothing was
executed, nothing was observed: the new round-trip and pin, the command-layer cases and every
earlier suite remain unexecuted, and the two-client reset pass through `/mapreset` is outstanding
human work.

The close-out's last slice (the fixture door pair authored into `StandardArenaMap.TEXT`, the asset
re-baked in the same change, the moved map-identity pins and the checker's door grammar) was then
source-checked only; it is not map validation, and every box above remains unchecked. The exact
checks run were:

- `python3 tools/scratch/static_check.py` — clean (435 Java files scanned, 435 declared types; no
  findings, including the module boundaries and both consumer gates).
- `python3 tools/scratch/static_api_check.py` — clean (539 project types indexed from 431 files).
- `python3 tools/scratch/static_audio_check.py` — clean (13 world entries, 6 weapon events, 1 loop,
  14 effect types, 30 manifest entries).
- `python3 tools/scratch/synth_sfx.py --check` — clean (14 generated sounds agree).
- `python3 tools/scratch/phase3_ballistics.py` — completed its analytical report without findings.
- `python3 tools/weapons/generate_weapon_tables.py` — regenerated 89 gun rows and 21 melee rows with
  no tracked diff.
- `python3 tools/scratch/arena_sdf_check.py` — clean: the checker learned the door grammar the Java
  format already enforces (`id=` required on a door and refused elsewhere, `access=ALL|TEAM<n>`
  case-insensitively, duplicate ids refused, `durability=` only on a kind that pools it and positive,
  `DoorSpec.reflection()`'s next-id rule) and the door triple in the hash's sort key and canonical
  row; it re-baked `assets/data/arena.sdf` for the new map text here and then reported the committed
  asset as the bake of the committed map — header hash
  `686c9165aa23baecbd21c45e2139c16e13e3b43fc81be2e7ed16c7c0559da597`, 1500 × 1000 texels at 2.0
  units/texel, 1,500,060 bytes, every texel equal, with only the header's hash differing from the
  previous bake.

**The fixture door has been moved to a reachable position (next slice, same branch, not milestone 6).**
The door pair that sat inside the sealed alcove at `x` 1034..1070 (and its mirror at 1930..1966,
`y` 100..270) has been moved to a reachable position inside the 70-wide mirrored wall at `x` 820..890:
the door now stands at `x` 837..873 and its mirror at 2127..2163, `y` 100..270, centred in that wall.
The map text changed, so `assets/data/arena.sdf` was re-baked under the new map hash
`ed6b335d...e173e` (still 1500 × 1000 texels at 2.0 units/texel, 1,500,060 bytes) and
`arena_sdf_check.py` re-confirmed the committed asset is the bake of the committed map. The door
stances at `x` 837.5 (`y` 236, 518, 918) are reachable from both spawns, so a player can walk to the
door and press `F` — the §Doors boxes now name a real doorway. The 10 unreachable stances the CI gate
reports for the shipped arena are **not** eliminated by this move: they sit inside the volume the tall
wall at `x` 1010..1034 and the shorter one at 1070..1092 enclose, blocked by that wall geometry, not
by the door (the door is read as an opening by both the Java gate and the Python mirror, so it never
blocks anything). They persist at their old positions, and the test pins that name them by those
coordinates still hold. The team-refusal box stays unreachable for the same reason it was before.

What those checks do and do not mean. They prove no syntax, boundary, gate or generated-table
regression across the slice's edits, and the SDF check is the one that carries the most weight here:
the map text and the asset moved together in one commit, and the Python bake is an independent
second implementation of `MapHash`/`SdfBaker`, so the header agreement plus texel-level equality is
real evidence for the parts both implementations share — but it is not the client: it cannot prove
that `SdfTexture` uploads the field the shipping-default guard accepts. This slice adds no
main-source call site (authored text, test pins and the checker), so `static_api_check.py`'s usual
blind spot — an instance call made through a field or a variable — does not arise, and the strings
the map's identity is pinned by were compared against the checker's own digest by hand. Nothing was
compiled, nothing was executed, nothing was observed: the new fixture-door pin, the doorless-map
case, the moved pins and every earlier suite remain unexecuted, and the desktop passes the door
unlocks — the whole "Doors" section except the team-refusal box, the crack pass, and the reset pass
through `/mapreset` — are outstanding human work.
