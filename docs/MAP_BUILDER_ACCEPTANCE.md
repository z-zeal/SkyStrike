# Map building system — acceptance checklist

**Status: unexecuted. Every box below is unchecked.** Milestone 1 landed the shared block model,
rule table and strict version-1 text parser/writer, with the legacy arena loaded through its
canonical embedded text, a version-2 SDF map-hash guard, and shared tests written for migration,
round trips and SDF identity. Milestone 2 then made every consumer read the shared table: the
rule-aware line queries (a shared `Blocking` purpose on `MapQueries`, one cached per-purpose
rectangle view on `ArenaMap`, the sight column in `VisionMath`, the bullet column in the raycaster
and the muzzle guard, and the blast column — pinned to sight — in the frag, claymore-cone and
fire-spread consumers); the movement column with one-way platform tops and the crouch+jump
drop-through window in `PlayerMotion`, plus the explicit columns in `DroneMotion`,
`ThrowablePhysics` and the camera's release check; and kind-aware rendering, where `TerrainRenderer`
draws every `BlockRenderStyle` and the minimap reads the same block list. Two static gates now
enforce the milestone's contract, and tests were written for the one-way rules, the movement
questions and the wire layout.
None of that changes the shipped wall-only arena's gameplay: it contains no platform, no glass, no
door and no breakable, so its collision, sight, bullets, bake and picture are the same rectangles
as before. The work does **not** yet ship an `assets/maps` file, an editor or `MapTool`, material
damage, or any glass, door or breakable *runtime* behaviour, and no dynamic state exists to draw
(doors are drawn closed and breakables undamaged because that is all the game knows). All of those
checks remain unattempted, and the movement box below can only be exercised once a map carrying a
platform ships.
Nothing in this repository was
built, launched or observed: the authoring sandbox has no JDK, so not even the shared tests could
be executed here.

Tick a box only from a running desktop build (two clients where the check says so), after the
automated tests for that milestone are green in CI. The CI-side tests are listed in
`docs/MAP_BUILDER_PLAN.md` §7 and are the first gate; this document is the second.

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
- [ ] Crouch+jump on a platform drops the player through, and the fall does not re-land on the same
  platform mid-drop.
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
