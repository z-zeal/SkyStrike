# Map builder v2 — plan

**Status: plan only.** Nothing in this document is implemented. Nothing in it has been compiled,
run or observed: this sandbox has no JDK and no Gradle, so every slice below is to be written and
not executed, with the first real run in CI and on the user's desktop build. Every acceptance
box in §9 is unchecked.

This plan follows `docs/MAP_BUILDER_PLAN.md` (milestones 1–5 of the first map builder, which are
landed or nearly so). Its milestone 6 (`assets/maps/arena.map` and the map list as a server
setting) overlaps the "create and edit maps" request in §2 and is treated as a decision in §3,
not silently absorbed.

Baseline the plan was checked against (`5faaac0` plus PR #65, merged as `e8759bf`):

- The game path builds the map from `ArenaMap.standard()` in four places: `GameServer`,
  `GameScreen`, `ClientSession` (its hash) and `SdfCache`. `MapEditorScreen` seeds its draft from
  it, and the `tools` module reads it for the CLI and the bake.
- Block sizes are fixed per palette preset (`MapPalette.Preset`: `Ledge`, `Pillar`, `Platform`,
  `Pane`, `Crate`, `Door`), with no resize.
- The mirror axis is referenced in 10 main-source files (64 lines) across `shared`, `server`,
  `core` and `tools`. `MapHash` hashes the mirror axis, so a mirror change changes the map's
  identity.
- `Material` is `STONE`, `METAL`, `WOOD`, `GLASS`, `ENERGY`. `BlockKind` is `WALL`, `GLASS`,
  `DOOR`, `BREAKABLE`, `PLATFORM`. `WALL` is `INDESTRUCTIBLE` in the rule table, and
  `BREAKABLE` is the only kind that takes durability damage.
- Team placement is `SpawnService`: `selectBalancedTeam` returns team 0 or 1, and the spawn
  index is `abs(teamIndex) % spawns.size()`. The game has two teams.
- World size is compile-time: `WorldConfig.ARENA_WIDTH = 3000`, and `VisionConfig` fixes the SDF
  at 3000 × 2000 units (1500 × 1000 texels at 2.0).
- Rectangles are axis-aligned everywhere (`Rect`), and player movement, sight, bullets and
  the SDF bake all read AABBs. The first plan's non-goals (§10) exclude rotation and asymmetric
  maps.

## 1. The requests, mapped to what the code allows

| # | Request | Current constraint | Proposed answer |
| --- | --- | --- | --- |
| R1 | Map is always mirrored; make it optional | §10 of the first plan calls symmetry "a fairness rule". 10 files and 64 lines reference the axis. | **Decision D1.** Per-map option, default on for new maps. Needs a format change (§4.4). |
| R2 | Move spawn points; add spawns | Spawns are data, but the editor cannot select or move them. Two teams exist. | Move in V2-1 (no format change). Adding spawns beyond two is **decision D6**. |
| R3 | Sizes are fixed; rotate instead of separate objects | Palette presets are fixed-size. `Rect` is axis-aligned. Doors model a vertical panel. | Free resize with numeric fields (V2-1). Quarter-turn rotation (V2-4). **Decision D2** on doors. |
| R4 | More building materials (wood wall, metal wall, …) | `Material` has five values. `WALL` is indestructible; `BREAKABLE` is the damageable kind. | Presets that pair existing kinds with materials (V2-3). New material values need a list from the user (**D3**, **D4**). |
| R5 | Create and edit new maps | Milestone 6 (map files and the map list). Maps are embedded text today. | V2-7, gated on **D5** (how custom maps reach other players). |
| R6 | Quick "play this map" button, with a return to the editor | Four game-path call sites hard-code `ArenaMap.standard()`. The client refuses a map hash mismatch (protocol 16). | V2-2: an active-map holder, a debug-only Play button, and a Return button. No save or bake needed. |
| R7 | Name the map when saving | The text header has no name. The hash excludes free text. | V2-5: `name=` in the header, excluded from the hash. |

## 2. Decisions to take before the slices that depend on them

The slices below are written so the safe parts can start first. Each decision is one the
user must make; the recommendation is what this plan would do without an answer.

- **D1 — Optional mirror.** Reverse the first plan's §10 non-goal for maps that opt out.
  *Recommendation:* the mirror is an option per map, on by default for new maps, and the
  standard arena stays mirrored. A map that is not mirrored should show a fairness note in the
  editor, because the two spawns are then not guaranteed equal.
- **D2 — Rotation for doors.** Doors swing a vertical panel about a fixed hinge, and the door
  state machine and renderer pose that panel. *Recommendation:* quarter turns for every kind
  except `DOOR`; a door can flip its hinge side (0° or 180°) but not turn sideways in v2.
- **D3 — "Wood wall", "metal wall", and destructibility.** `WALL` is indestructible, so a
  "wood wall" built as `WALL` + `WOOD` would look like wood and never break. *Recommendation:*
  presets, not new kinds. "Wood wall (solid)" is `WALL` + `WOOD`; "Wood wall (breakable)" is
  `BREAKABLE` + `WOOD`; "Metal wall" follows the same pattern with `METAL`. The user can say
  which of these they want to see.
- **D4 — Which new materials.** Each new `Material` needs a `MaterialTable` row (durability,
  bullet, blast and melee retention, colours, impact effect, impact sound), a recording in the
  audio catalogue, and a `MaterialTableTest` pin. The user's list decides the scope.
- **D5 — How a custom map reaches other players.** Options:
  - (a) Quick test only, until distribution is designed.
  - (b) Each side loads the same map file. The protocol 16 join gate already refuses a
    mismatch, so a wrong file fails loudly instead of desyncing. *Recommendation:* (b) first.
  - (c) The server sends the map text over the wire. This needs an append-only packet and a
    protocol bump, and is a later slice.
- **D6 — Spawn count.** Two spawns (one per team) is what the game uses today. More spawns need
  a rule for choosing among them. *Recommendation:* exactly two spawns for v2; more is a later
  slice that needs a spawn-selection rule.
- **D7 — Map size.** World size is a compile-time constant that feeds the mirror axis, the SDF
  dimensions and the camera. *Recommendation:* fixed size for v2. Per-map size is a separate
  slice because it changes the SDF's dimensions and every consumer of `ARENA_WIDTH`.
- **D8 — Milestone 6.** Does "create and edit maps" (R5) absorb milestone 6 (`assets/maps/`
  and the map list as a server setting)? *Recommendation:* yes, as V2-7, with the shipped arena
  moved to `assets/maps/arena.map` in the same slice and its SDF unchanged.

## 3. Slices

Each slice is one PR, one concern per commit. Code commits come before docs commits. Every
slice runs the seven repo checks (`tools/scratch/static_check.py`, `static_api_check.py`,
`static_audio_check.py`, `synth_sfx.py --check`, `phase3_ballistics.py`,
`weapons/generate_weapon_tables.py`, `arena_sdf_check.py`) before each commit, with real output
quoted. Any commit that changes the standard arena's text re-bakes `assets/data/arena.sdf` in
the same commit.

### V2-1 — Editing foundations (no format change; mirror stays on)

- Select a block or a spawn by clicking; multi-select with a box or shift-click.
- Move the selection by drag, by arrow keys (nudge by the grid step), or by typing `x` and `y`.
- Resize by dragging the edge handles, or by typing `width` and `height`. The minimum size is
  2 units, and the editor refuses sizes that would leave the map.
- Snap to a grid step (default 5 units, the reachability pitch). The step is a setting.
- Delete the selection; undo and redo for every edit (a bounded history, a few hundred steps).
- Spawns are selectable and movable, with the team label shown. Spawn placement respects the
  same bounds rule as blocks.
- Writes go through `MapDraft` (the shared model), so every edit still passes `ArenaMap.Builder`
  and the mirror rule. The editor adds no rule of its own.

*Gate:* a `MapDraftTest` pin for each edit verb (move, resize, delete, undo, redo, spawn move),
and an editor test that the draft's `toText()` after an edit sequence is the canonical text of
the resulting map. Written and not executed.

### V2-2 — Quick test: Play and Return

This is the slice that makes everything after it testable without a save or a bake.

- A **Play** button in `MapEditorScreen`, shown only in a debug build (`DebugFlags.enabled()`),
  like the editor entry in the menu. It starts a local host built from the **current draft**,
  in memory, and joins it as the local player.
- A **Return to editor** button, shown only in that mode. It stops the local host, releases the
  match, and reopens the editor with the same draft instance. The draft survives the round trip
  exactly, saved or not.
- An **active-map holder** in `shared` (for example `ActiveMap`) that the game path reads instead
  of calling `ArenaMap.standard()`. It holds the standard arena by default and the draft's map
  while a quick test runs. The four game-path call sites (`GameServer`, `GameScreen`,
  `ClientSession`, `SdfCache`) change to read the holder; the editor's seed and the tools keep
  `ArenaMap.standard()`. The standard arena is what the shipping game uses, so its behaviour must
  not change.
- **The SDF.** A quick test has no baked field for an unsaved map. The debug development path
  already logs and re-bakes a field whose hash does not match (`SdfCache`), so the quick test
  uses that path. Shipping builds keep the refusal. Confirm during the slice that the runtime
  bake (`FxPipeline.rebakeSdf`) is what runs on the first frame, not a second copy.
- **Stale hosts.** Today `Main.startLocalTest` joins an existing server when the embedded host
  cannot bind. That is how the first live run's "two processes on different maps" finding could
  happen silently. A quick test must not do that: if the host cannot bind, Play refuses with a
  message, and the protocol 16 join gate remains the backstop.

*Gate:* tests that the holder returns the standard arena by default, returns the draft's map
while a quick test runs, and restores the default on return; a test that the join gate admits a
local host built from the same draft and refuses a different one. Written and not executed. The
desktop run is the real check.

### V2-3 — Materials and presets

- Presets that pair kinds with materials, as data in `MapPalette`: wood wall (solid),
  wood wall (breakable), metal wall (solid), metal wall (breakable), and the existing presets.
  Their kind and material come from D3.
- New `Material` values only after D4. Each one lands with its `MaterialTable` row, its audio
  entry, and the `MaterialTableTest` pin that the table has one profile per material.
- Presets are palette data. No rule of the existing rule table changes, so the standard arena's
  hash and SDF do not move.

*Gate:* `MaterialTableTest` and a palette test for the new presets; `static_audio_check` must
still agree for any new impact sound. Written and not executed.

### V2-4 — Rotation

- Rotate the selection in quarter turns (90° steps). A quarter turn swaps width and height about
  the block's centre and keeps the block axis-aligned, so no consumer changes.
- The saved text stores the resulting rectangle, so **the file format does not change for
  rotation**, and the hash of an unrotated map is unchanged.
- Doors follow D2. A door's hinge side can flip; a sideways door is not offered in v2.
- The mirror rule still applies: a rotation of a mirrored block rotates its mirror too.

*Gate:* rotation tests on `MapDraft` (four turns are the identity; a turn keeps the block
inside the map; a mirrored block's mirror is rotated with it). Written and not executed.

### V2-5 — Map name at save

- The text header gains an optional `name=` line. The version becomes 2 for files that carry it;
  version 1 files still load, and a version 1 file loads with no name.
- The name is **excluded from `MapHash`**, so renaming a map does not change its identity, does
  not invalidate its SDF and does not break the join gate. The standard arena's hash is
  unchanged.
- Validation: 1 to 40 printable characters. The file name is a slug of the name. Saving over an
  existing name asks before overwriting.

*Gate:* `ArenaMapFormatTest` round trip with and without a name; a `MapHash` test that two maps
differing only in name hash the same; the standard arena's hash pin unchanged. Written and not
executed.

### V2-6 — Optional mirror

Depends on D1. This is the largest slice, because the mirror axis is part of the hash, the
builder's symmetry rule, the door pairing (`DoorSpec`), the reachability search and the render
and minimap code.

- The header's `mirror` line becomes optional. A map without it is unmirrored and uses the
  builder's `free` verbs; the mirrored verbs stay for mirrored maps. A mirrored map is still
  checked for symmetry; an unmirrored one is not.
- `ArenaMap` answers whether it is mirrored; code that needs an axis (for example the door
  pairing) must say what it does without one. Every one of the 10 files and 64 lines is listed
  in the slice's audit and each one is hand-read, because `static_api_check.py` cannot see calls
  made through a variable.
- `MapHash` hashes "unmirrored" explicitly, so no unmirrored map can collide with a mirrored one.
- The editor gets a per-map mirror toggle. Turning the mirror on for a map that is asymmetric
  asks the user to mirror or delete the unpaired blocks, rather than silently doing either.

*Gate:* the existing `ArenaMapTest`, `ArenaReachabilityTest` and `MapAuditTest` pins unchanged
for mirrored maps; new tests for an unmirrored map's load, save, hash and reachability. Written
and not executed.

### V2-7 — Create, open, duplicate and delete maps; the map list

- A **New map** action: name, mirrored or not (D1), and a fixed size (D7).
- An in-editor **map list** in `.skystrike/maps/` (the path the editor already uses), with open,
  duplicate and delete. Nothing is written into the repository.
- The shipped arena moves to `assets/maps/arena.map` (D8), read through the same strict parser,
  and its bake must stay byte-identical to the committed `assets/data/arena.sdf`.
- Distribution to other players follows D5. Under (b), the server and each client load the
  same file, and the protocol 16 gate refuses a mismatch with both hashes printed.

*Gate:* save → open → duplicate → delete round trips in a test over a temporary directory; the
shipped arena's file loads to the standard arena's hash. Written and not executed.

## 4. Design notes that the slices depend on

### 4.1 The draft is the single source

Every edit goes through `MapDraft`, which goes through `ArenaMap.Builder`. The editor, the quick
test and the save path all read the same draft, so there is one definition of a valid map. This
is the first plan's D10 ("the editor is a view; the rules are shared") and V2 keeps it.

### 4.2 Quick test is a host, not a preview

The quick test runs the real server and the real client code on the draft's map, so it tests
what the players would get. A preview renderer would test something else. The cost is that it
needs the active-map holder (§3, V2-2) and a bake on the first frame, which the debug path
already does.

### 4.3 Rotation and sizing stay axis-aligned

`Rect` is axis-aligned, and every consumer relies on that. Rotation is therefore quarter turns
that swap the rectangle's dimensions, and sizing is a rectangle edit. A free angle would need a
new rectangle type in every consumer, and the first plan's non-goals rule that out.

### 4.4 Optional mirror is a format change

Today the mirror is a required header line and every block placement is a mirrored verb or a
centred verb, so the format cannot express an unmirrored map. V2-6 adds the unmirrored form.
Because the hash includes the mirror, an unmirrored map has a different identity from a
mirrored one by construction, and no existing map changes identity.

### 4.5 Name is metadata, not identity

The name is written to the file and shown in the UI, but it is not hashed. Identity is the
geometry, the spawns, the mirror and the materials, which is what the SDF and the join gate
need to agree about.

## 5. Risks

- **Nothing has been compiled.** The first real run of every slice is CI or the desktop build.
  Each slice's commit message must say so.
- **The mirror assumption is spread out.** 10 files and 64 lines reference the axis. V2-6 is
  the slice most likely to hide a regression, so its audit must list each one.
- **Quick test can silently join the wrong server.** V2-2 changes the local-host path to refuse
  rather than join. The join gate is the backstop, and the desktop run has to exercise a
  deliberate mismatch.
- **Materials are data with consequences.** A new material is not just a colour: retention,
  effects and sounds must agree, and the existing tests pin the one-profile-per-material rule.
- **Size is not free.** Changing D7 later means changing the SDF dimensions, the camera and the
  mirror constant together. Keeping it fixed in v2 keeps V2-1 to V2-7 from becoming a renderer
  rewrite.

## 6. Order and dependencies

```
V2-1 editing ──► V2-2 quick test ──► V2-3 materials ──► V2-4 rotation ──► V2-5 name
                                                                          │
                                       V2-6 optional mirror (after D1) ◄──┘
                                       V2-7 maps, list, distribution (after D5, D8)
```

V2-1 and V2-2 need no decision and can start first. V2-3 needs D3 and D4. V2-4 needs D2. V2-6
needs D1. V2-7 needs D5, D7 and D8.

## 7. What is deliberately not in v2

- Per-map world size (D7), and spawns beyond two (D6).
- Sideways doors and free-angle rotation (D2 and §4.3).
- Wire transfer of custom maps (D5 option c).
- Editing a map while a match on it is running. A map change remains a revision the server
  owns, as the first plan's §10 says.

## 8. Test plan

Every slice is verified the same way: the seven repo checks run before each commit with real
output quoted, plus the Java tests named in that slice, written and not executed. The desktop
re-run script is part of each slice's report, and it names which acceptance boxes the slice
makes attemptable.

## 9. Acceptance (all unchecked; none is executable in this sandbox)

- [ ] Select, move, resize and delete blocks and spawns with the mouse, the keyboard and numeric
  fields (V2-1).
- [ ] Undo and redo every edit (V2-1).
- [ ] Play runs the current draft in a debug build, without a save or a bake; Return brings back
  the same draft (V2-2).
- [ ] Play refuses with a message when the local host cannot bind, instead of joining another
  server (V2-2).
- [ ] Wood and metal presets, solid and breakable, behave as their kind and material say (V2-3).
- [ ] A rotated block keeps its size and stays inside the map (V2-4).
- [ ] A saved map carries its name; renaming does not change its hash or its SDF (V2-5).
- [ ] An unmirrored map loads, saves, shows the fairness note, and passes reachability (V2-6).
- [ ] New, open, duplicate and delete work from the map list; the shipped arena loads from
  `assets/maps/arena.map` with the same hash (V2-7).

## 10. Desktop re-run script (per slice, on the user's build)

1. Pull the slice's branch. Confirm the seven checks' output in its commit message.
2. Start the desktop client with debug on (`SKYSTRIKE_DEBUG=1`, `-Dskystrike.debug=true`, or
   `--dev`), using the usual run task.
3. Open **Map editor** from the main menu. Run the slice's checkboxes from §9 in order.
4. For V2-2, start Play, move, then press Return and confirm the draft is unchanged. Then start a
   second client against a mismatched server and confirm the join is refused with both hashes.
5. Record anything that differs from this plan as a finding against the slice, not as a silent
   change.
