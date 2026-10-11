# Map building system — acceptance checklist

**Status: unexecuted. Every box below is unchecked.** Increment 1 has landed the shared block
model, rule table and strict version-1 text parser/writer, with the legacy arena loaded through its
canonical embedded text and shared tests written for migration and round trips. It does **not** yet
ship an `assets/maps` file, editor, SDF map-hash guard, material damage, glass, platform, door or
breakable runtime behaviour; those checks remain unattempted. Nothing in this repository was
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
sandbox. The source-only checks run after the increment were:

- `python3 tools/scratch/static_check.py` — clean (including module-boundary checks).
- `python3 tools/scratch/static_api_check.py` — clean.
- `python3 tools/scratch/static_audio_check.py` — clean.
- `python3 tools/scratch/synth_sfx.py --check` — clean.
- `python3 tools/scratch/phase3_ballistics.py` — completed its analytical report without findings.
- `python3 tools/weapons/generate_weapon_tables.py` — regenerated no tracked diff.

Those checks do not compile or execute the new shared tests and do not validate runtime map loading;
every manual box above remains unchecked.
