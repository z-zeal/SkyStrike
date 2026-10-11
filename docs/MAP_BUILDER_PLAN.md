# Map building system — plan

**Status: milestone 1 has landed and milestone 2 is under way.** The shared
`BlockKind`/`Material`/`MapBlock` model, central `BlockRules` table, strict version-1 text
parser/writer, standard-arena migration and SDF map-hash guard landed in PR #47.
`ArenaMap.standard()` loads the embedded canonical text through that parser; every version-2 SDF
names its semantic map, and shared tests cover the rule rows, migration geometry, strict failures,
canonical save → load → save and the SDF hash contract. Milestone 2's first increment has landed
too: line queries take a shared `Blocking` purpose, `ArenaMap` derives one cached rectangle view
per purpose from the rule table, and the sight, bullet and blast consumers ask their own column
instead of the compatibility rect list. The `assets/maps` move, the movement column (one-way
platform tops), kind-aware rendering and every later milestone remain unfinished; no Java was
compiled or executed, and no checklist in `docs/MAP_BUILDER_ACCEPTANCE.md` has been executed.

## 1. What exists today

The current runtime still uses the old rect view, but authored geometry now has a shared model
and a strict persistence path.

- **`shared/map/Rect`** — an immutable axis-aligned rectangle (`x`, `y`, `width`, `height`, y up),
  with `mirror(axis)`, `translated`, `contains` and `overlaps`. It is geometry only; a block's
  shared rule row now decides whether that geometry blocks, bakes or draws.
- **`shared/map/{BlockKind, Material, MapBlock, BlockRules}`** — the immutable block triple and
  the one rule table for movement, sight, bullets, SDF inclusion and renderer treatment. The table
  describes intact/closed blocks; dynamic state is still later work.
- **`shared/map/ArenaMap`** — immutable `blocks()`, rule-derived `blockers(Blocking)` (one cached
  rectangle view per query purpose) and a backwards-compatible `List<Rect> solids()` that is
  exactly `blockers(Blocking.BULLETS)`. `spawns()`, `width()`, `height()`, `mirrorAxisX()` and
  `bounds()` remain. Its public builder enforces `mirrored(block)`/`centred(block)` symmetry and
  rejects duplicate blocks. `ArenaMap.standard()` now parses `StandardArenaMap`'s canonical
  version-1 text; `ArenaMapTest` still pins its legacy geometry.
- **`shared/map/ArenaMapFormat`** — a dependency-free, strict line parser/writer. It rejects a
  malformed header, unknown verbs/kinds/materials, non-positive or out-of-bounds blocks,
  duplicate blocks and wrong symmetry verbs rather than exposing a partial map.
- **`shared/map/MapQueries`** — the stateless questions: `solidAt`, `overlapsSolid`,
  purpose-aware `lineBlocked`/`firstBlocker` (their five-argument forms are the sight
  convenience), `surfaceBelow`, `solidsOverlapping`. Line queries answer from the shared rule
  table through `ArenaMap.blockers(Blocking)`; the point/box questions still read the
  bullet-blocking compatibility view until the movement column migrates.
- **`shared/physics/PlayerMotion`** — the shared swept-AABB movement with its own ground skin,
  ceiling clearance for uncrouching, and `noclip`. Server authority and client prediction run this
  same function; the server has no separate collision code. It still sweeps the bullet-blocking
  compatibility view: the movement column and one-way platform tops are the next milestone-2
  increment, because half a movement column would be worse than none.
- **`server/combat/RaycastBulletSystem`** — the swept round path: nearest entry across terrain and
  player hitboxes, with the terrain case recorded as the `Rect` that was hit. Terrain is the
  `BULLETS` column of the shared table, and `blockedByTerrain` takes the purpose explicitly so the
  muzzle guard and the round's own sweep always ask the same question.
- **`server/combat/BulletSystem`** — per-tick projectile integration plus the muzzle guard
  (`raycaster.blockedByTerrain(eye, muzzle)`), which the per-weapon muzzle made load-bearing.
- **SDF** — `tools/.../SdfBakeTool` bakes `ArenaMap.standard()` through `shared/sdf/SdfBaker` into
  `assets/data/arena.sdf`, a version-2 binary (`SdfField.MAGIC`/`VERSION`) whose header carries a
  SHA-256 map hash. `core/fx/sdf/SdfCache` rejects a missing, old or mismatched field in a
  shipping-default run; an explicitly enabled development run logs and re-bakes it. `SdfTexture`
  uploads the verified field and the visibility, light, particle and debug passes read it. The
  **server never reads the SDF** — the client uses it for what is drawn (cone shadows, light
  shadows, particle collision).
- **Where the map comes from** — the server builds `ArenaMap.standard()` in `GameServer`; the
  client builds its own `ArenaMap.standard()` in `GameScreen`. Both sides agree because both call
  the same Java. Nothing about the layout crosses the wire, and nothing checks that the two sides
  agree.
- **Persistence** — `ArenaMapFormat` now provides a versioned, hand-parsed text save/load path in
  `shared`, with no JSON dependency. The canonical arena source is embedded until milestone 6 moves
  it verbatim to `assets/maps/arena.map`; user-map file I/O and the editor do not exist yet. The
  sprite catalogs remain build-time JSON consumed only by `tools/weapons/generate_weapon_tables.py`,
  so neither `shared` nor the client parses JSON while running.
- **Wire** — `shared/net/NetworkRegistration` registers packet types in an append-only order and
  `NetworkRegistrationTest` asserts the counts (13 registration kinds / 35 registered types as of
  protocol 13). `PROTOCOL_VERSION = 13`.

Two traps follow directly from that list, and the whole plan is shaped by them:

1. **One rect, four consumers.** Collision, sight, bullets and rendering all read `solids()`.
   A new block kind is only real when all four know about it — a kind added to rendering alone is
   a decor bug, and one added to collision alone is a wall you can see through.
2. **A stale SDF disagrees silently.** The baked field is derived data and nothing ties it to the
   map it was baked from. A saved map edited at runtime would keep the old shadows and the old
   particle collision while bullets and movement follow the new rects — with no error anywhere.

## 2. Decisions

**D1 — Blocks, not rects.** A map is a list of `MapBlock`s: `(BlockKind kind, Material material,
Rect rect)`. `kind` is `WALL`, `GLASS`, `DOOR`, `BREAKABLE`, `PLATFORM`(one-way). `material` names
the surface (see D7). **Implemented:** `ArenaMap.blocks()` is that list and `solids()` is the
backwards-compatible derived bullet-blocking view, so existing call sites keep their wall-only
behaviour while new call sites can ask by rule.

**D2 — One rule table, four readers.** Every kind declares, in one shared table, what it does to
each consumer: movement, sight, bullets, the SDF bake, and rendering. The table is the plan's
heart (§3), and every consumer reads it rather than testing kinds by name. **Implemented so far:**
the shared rows and tests, `SdfBaker`'s SDF column, and milestone 2's line-query threading —
`ArenaMap.blockers(Blocking)`, purpose-aware `MapQueries` line queries, `VisionMath`, the bullet
raycaster and muzzle guard, and the blast consumers. The sweeper's movement column and the
renderer remain milestone 2 work.

**D3 — Doors are server-owned dynamic blockers.** A door has an id, an `access` rule and a state
(`CLOSED`/`OPENING`/`OPEN`/`SHUTTING`) with the opening tick. Collision, sight and bullets consult
the *state*, never the animation. Because a door changes every few seconds, it is **not** baked
into the SDF: doors that occlude are handed to the visibility and light shaders as a small fixed
array of dynamic occluders, using the pattern the smoke circles already use. Closed doors are in
the collision/bullet sets; open doors are in neither.

**D4 — Breakables are baked and re-baked.** A breakable wall is a full solid while intact, so it
is part of the SDF (it really does cast shadows). When it is destroyed, the block leaves the map
and the SDF is re-baked once, on a worker, and the changed texel region is uploaded. Destruction is
rare and authoritative, so the cost is a single hitch on the frame it happens — acceptable, and
measurable; doors never take this path.

**D5 — Glass is a movement blocker you can shoot through.** Intact glass stops bodies, not sight
and not bullets (the window's reason to exist is to look and shoot through it). Any round shatters
it — it has no durability — the round continues with a small speed loss, and the fragments leave
the map entirely: no movement block, no occlusion, no SDF involvement either way. Movement through
intact glass is impossible; breaking it is the only way through. (Partial-attenuation glass —
visible through, dimmed — is explicitly deferred: it needs a per-pixel visibility modifier the
pass does not have.)

**D6 — One-way platforms are thin and see-through.** Collision from above only (landing), never
from below, never a ceiling, and never a movement block for a falling body; no bullet blocking, no
sight blocking, and so no SDF presence and no shadow. Drop-through is crouch+jump (`S` and jump in
this game's bindings — plain `S` is the crouch/dodge and must stay one), which starts a short timer
during which the body ignores one-way tops; the timer ends when the body has cleared the slab.

**D7 — Materials decide everything survivable.** One shared `MaterialTable`: durability,
bullet-damage retention, whether explosions/melee can hurt it, the render tint/line colour, impact
effect and impact sound. Stone: tough, no bullet damage. Metal: tougher, reflects nothing, louder.
Wood: breakable by bullets. Glass: shatters on any hit. Energy: scenario blocks, only explosions.

**D8 — The map travels as data.** The shipped arena becomes a text map file; `ArenaMap.standard()`
is re-expressed through the loader in the first milestone and a test proves the resulting rect list
is identical to today's (same order-insensitive set, same symmetry), so the migration cannot move
a wall. The hand-written format (§5) is versioned and parsed in `shared`, with no new dependency.

**D9 — The SDF names its map.** **Implemented:** `SdfField` version 2 carries the SHA-256
identity of `ArenaMap`'s sorted semantic contents (axis, spawns, kinds, materials and exact rect
float bits). `SdfCache` refuses a missing, old or mismatched field in the shipping-default path;
an explicitly debug-enabled client logs and re-bakes. Reordering equivalent text lines does not
change the hash, while any authored content change does.

**D10 — The editor is a view; the rules are shared.** An in-game editor screen behind the debug
master switch (`DebugFlags.enabled()`, the `GameSettingsDialog`/`LoadoutScreen` patterns) plus a
headless `tools` CLI (`MapTool check|bake`), with every rule — symmetry, kinds, one-way logic,
reachability — living in `shared` and proved in `shared/src/test` without a GPU. No second editor
implementation in `tools`, and no UI-only rules.

**D11 — Authoritative changes are a wire contract.** Door and destruction state are
server-authoritative. Protocol 13 → 14: a map hash and revision in the handshake/snapshot, a door
state packet, and block events; registration stays append-only. The client predicts optimistically
with the same shared functions and reconciles on the next snapshot; it never tells the server what
is open or broken.

## 3. How a kind travels

Each row is the whole contract for one kind; every consumer reads this table rather than
re-deriving it.

| Kind | `PlayerMotion` / `MapQueries` (movement) | `MapQueries.lineBlocked` (sight) | `RaycastBulletSystem` / `BulletSystem` (bullets) | SDF bake | Rendering |
| --- | --- | --- | --- | --- | --- |
| `WALL` | blocks | blocks | blocks | baked | filled slab, `SOLID_FILL` palette |
| `BREAKABLE` while intact | blocks | blocks | blocks (damage chips it) | baked | wall palette + crack overlay from durability |
| `BREAKABLE` destroyed | gone | gone | gone | re-baked (D4) | gone; debris is an effect |
| `GLASS` intact | blocks | **passes** | **passes**; any hit shatters it, round loses speed | excluded | glass tint + highlight, sparkle on hit |
| `GLASS` shattered | gone | gone | gone | never was | gone; shards are an effect |
| `DOOR` closed | blocks | blocks | blocks | excluded | panel drawn from the state's swing angle |
| `DOOR` open | passes | passes | passes | excluded | panel drawn swung aside |
| `PLATFORM` (one-way) | blocks only a *falling* body from above, and only until drop-through starts | passes | passes | excluded | thin slab, top edge highlighted |

Consequences the table forces, spelled out because they are the places a plan usually lies:

- **"Solid" is reached through three doors, not one.** `MapQueries.lineBlocked` serves the
  utility/zone occlusion query, `shared/vision/VisionMath.hasLineOfSight` and
  `server/combat/RaycastBulletSystem` each run their own `Geometry` loop over `arena.solids()`
  (the raycaster also feeds `BulletSystem.spawn`'s muzzle guard), and `PlayerMotion` reads the
  rects again with its own sweeper. Giving sight and bullets different answers therefore means
  threading a purpose through all four readers, not editing one function: the API becomes
  `lineBlocked(map, x0, y0, x1, y1, Blocking.SIGHT|BULLETS|BLAST)` plus the matching purpose
  parameter on the raycaster and the sweeper's set, with the old 5-argument signature kept as a
  `SIGHT` convenience so the existing call sites do not change meaning. The blast decision:
  explosion occlusion follows **sight**, because a blast is an area of effect, and glass that
  passes sight also passes the blast. **Landed:** the purpose API, `ArenaMap`'s per-purpose views,
  the raycaster parameter, `VisionMath`, and every frag, claymore-cone and fire-spread occlusion
  call (they ask `BLAST`). The sweeper's movement set is the one reader still on the compatibility
  view.
- **One-way platforms cannot be expressed by the current sweeper.** `PlayerMotion.integrateAndCollide`
  tests every rect for both axes; platform support is an added rule — tops are considered only when
  `vy <= 0` and the body's feet were above the slab on the previous step, and skipped while the
  drop-through timer runs. This is *shared* code, so prediction and authority change together.
- **Doors need the occluder array before they can occlude.** Until the visibility and light shaders
  take a dynamic occluder array (bounded, e.g. 16 rects, the smoke-circle pattern), a closed door
  must be non-occluding, and the plan's first shippable door therefore opens and closes *movement
  and bullets* only. That is stated here rather than discovered in the acceptance pass.
- **Rendering stays a function of the map.** `TerrainRenderer` reads `map.solids()` today and draws
  filled rects plus an outline; it grows one draw pass per kind (glass tint, door panel with a
  swing angle, platform top line, breakable cracks) and still owns no state of its own.

## 4. Doors, breakables, glass and platforms in detail

**Doors.** Fields: `id` (stable per map), `access` (`ALL`, `TEAM` with a team index matching a
spawn side), `state`, `openingTick`, `holdSeconds`. Interaction: `E`/the use key on the nearest
door within reach (a shared `MapQueries.doorAt`), or a cvar-free automatic mode for doors that
should just swing (not planned; manual only). The server owns the state machine, resolves the
"whose team" question against the opener's team, and refuses while a body or a thrown object
blocks the swing. Client: predicts the swing from its own use press (optimistic), and reconciles
when the authoritative state arrives — the same predict/reconcile discipline as movement. A door
blocked by a body stops opening (server decision, replicated as a state, so both sides agree).

**Breakables.** Durability lives in the material plus a per-block multiplier. Damage sources:
explosion damage at the block's nearest point (scaled by distance and the material's blast
sensitivity), bullet impacts (scaled by the material's bullet retention), and melee hits. Below
zero the block is destroyed: an effect is emitted (dust/debris colour from the material), the block
leaves the authoritative map, the revision increments, and the SDF re-bakes. Nothing is repaired in
a round; per-round reset is a server-side map revision reset (decision to confirm, §8).

**Glass.** One hit, any hit. The shattering round keeps going with a small speed loss, so shooting
through a window does not behave like shooting a wall. No durability, no partial breakage, no
half-shattered collider.

**Platforms.** Only the top surface collides, only for a body that is falling and was above it on
the previous step, and never while the drop-through timer is running. The timer starts on
crouch+jump, and ends when the body's box no longer overlaps the slab (so a body cannot re-land on
it while inside it).

## 5. The map format and save/load

**Format: text, line-based, versioned, hand-parsed in `shared`.** Justification, against the
alternatives:

- *JSON* would mean a new dependency in `shared` (the one place where an extra library is most
  expensive: it is used by the server, the client and Android) or a second hand-rolled parser
  beside the sprite one. A 12-line line format needs neither.
- *Binary (the `SdfField` style)* is compact and already precedented, but maps are tiny (hundreds
  of rects) and are things a human reviews, diffs and merges — a text format makes every map edit
  a reviewable git diff, and makes a test's expected value readable in the test.
- *Kryo* is tuned for network/state serialisation and would tie an asset to a library version.

Format sketch (the real header is what the parser will enforce):

```
skystrike-map 1
mirror 1500
spawn 0 240 180
spawn 1 2760 180
mirrored wall stone 120 200 120 18
centred wall stone 0 0 3000 100
mirrored platform wood 820 500 250 18
centred door metal 1500 100 24 90 access ALL
mirrored breakable wood 1120 190 180 14
mirrored glass glass 1300 400 20 80
```

- The verb is the *builder* verb from `ArenaMap.Builder` (`mirrored`, `centred`), so symmetry is a
  property of the file, not something the editor hopes for: loading runs the same assertions, and a
  file that straddles the axis fails to load with the builder's own message.
- `spawn` and `mirror` are data; every other line is `kind material x y width height [key=value]`.
- Parsing is strict: unknown verbs, unknown kinds/materials, non-positive or out-of-bounds sizes,
  duplicate blocks and a block straddling the mirror axis are load errors, not warnings — a map
  that half-loads must never become the live map. **Increment 1:** door ids and other per-kind
  attributes are rejected until their shared model can preserve them, rather than being accepted
  and silently lost; duplicate door-id validation arrives with that model.
- Save writes a canonical file, so save → load is a round trip a test can assert (both directions,
  plus byte-stability of a re-save). **Implemented:** `ArenaMapFormatTest` covers that canonical
  round trip for the migrated arena and a mixed-kind map.
- Shipped maps will live in `assets/maps/*.map`; user maps will live beside the save directory and
  go through the same validation, then the same bake. The embedded canonical arena text is an
  intentional bridge until milestone 6 supplies those file paths.

**When the SDF is baked.** Any path that changes the *static* set (load, breakable destroyed,
glass shattered, wall edited) re-bakes: `MapTool bake` writes the shipped asset at build time;
in-game the bake happens on a worker at the change and patches the texture. The client loads
`data/<map>.sdf` only when its header hash matches the map in play (D9); otherwise a dev build
re-bakes and logs, and a shipping build refuses. The server never bakes — it does not draw — but it
holds the authoritative block set, which is what the hash describes.

## 6. The editor

Two surfaces, one rule set:

| | In-game `MapEditorScreen` (core, debug switch) | Headless `tools` CLI (`MapTool check/bake`) |
| --- | --- | --- |
| Symmetry | Builder verbs, live | Same verbs, in CI |
| Testability | None (needs a GPU) | Full (`shared/src/test` + CLI in CI) |
| Mobile | Yes — the reason it exists | No |
| Live preview of kinds | Yes (collision heat, sight, bullet windows) | No |
| Load/save | Same parser | Same parser |
| Reachability report | On demand | In CI |

The in-game screen is a view: it edits a `MapDraft` (a shared, mutable block list), calls the same
`mirrored`/`centred` verbs, and asks `MapTool`'s shared core for validation, reachability and the
bake. It gets the debug master switch and cannot be reached in a release build; it does not need
the network, and it must not be able to publish a map to a running match. The CLI is the CI
surface: `MapTool check maps/*.map` validates symmetry, ids, kinds and reachability, and
`MapTool bake` writes the SDF the client loads.

## 7. Tests (all in `shared/src/test`, no GPU)

- **Symmetry:** every shipped map loads, and for every block its reflection about `mirrorAxisX()`
  is present (the property `ArenaMapTest` already pins, now proved for arbitrary files); a
  hand-broken file fails to load.
- **Save → load round trip:** parse(write(map)) equals map; write(parse(write(map))) is
  byte-identical; unknown-verb and out-of-bounds files are rejected with the documented messages.
- **Migration:** the arena expressed in the format loads to exactly today's rect set (order-
  insensitive comparison against `ArenaMap.standard()`), so the format conversion cannot move
  geometry.
- **One-way rules:** a body rising through a platform is never blocked; a body falling onto it
  lands; crouch+jump drops it through and it does not re-land while overlapping; a body already
  inside a platform is not ejected.
- **Door rules:** closed blocks movement/bullets/sight, open does not, a blocked swing stops, and
  the state machine's transitions are driven only through the server-side API.
- **Breakable rules:** damage accumulates from the material's sources only; at zero the block
  leaves the collision, bullet and query sets in the same tick.
- **Reachability:** a deterministic BFS over a coarse walkable grid (a cell is walkable when a
  standing player fits and `MapQueries.surfaceBelow` supports it) with the M8 jump envelope
  (110-unit step, 40% fuel ascent), asserting every spawn reaches every objective marker; run for
  every shipped map, so a saved map that seals itself fails CI rather than a playtest.
- **SDF hash guard (implemented):** a field whose header hash does not match the map is refused,
  and the old header version is refused with a version message.
- **Rule-aware line queries (implemented, this milestone's first increment):** `MapQueryPurposeTest`
  authors a mixed-kind map and asks every kind and every `Blocking` purpose through
  `MapQueries.lineBlocked`/`firstBlocker`, comparing the answer with `BlockRules.blocks` and the
  `ArenaMap.blockers(purpose)` view; `VisionMathTest` shows sight, and the gameplay visibility
  query with it, passes intact glass and a one-way platform and stops on a wall and a closed door.
  In `server/src/test`, `RaycastBulletSystemTest` proves a round passes intact glass and a platform,
  stops on a wall, an intact breakable and a closed door, and cannot reach a body standing behind
  the wall behind a window. The one-way sweeper tests are still to come with their increment.

## 8. Wire contract (protocol 13 → 14)

- **Handshake/join:** the server states the map hash and the destruction revision; a client whose
  map (or SDF) does not match is told so and does not join, instead of silently playing a different
  arena.
- **Snapshot:** a `mapRevision` (uint32) rides the game-state packet, and nothing else: the block
  set itself is static per map and shipped.
- **Door state:** a small reliable packet — door id, state, opening tick — sent on change and
  re-sent in the join payload. Append-only registration.
- **Block events:** destruction/glass-shatter as an event (block id, cause, position for the
  effect), plus the join payload carrying the current revision's removed/opened sets, so a late
  joiner is correct without replaying history.
- **Anti-cheat stance:** the server decides every door, every break and every shatter; the client
  predicts optimistically with the same shared functions and reconciles. A modified client that
  claims a wall is broken simply walks into a wall: movement, bullets and line of sight are all
  resolved server-side against the authoritative block set. The client can at most make its own
  copy of a door look open for a round trip.
- `NetworkRegistrationTest`'s counts move with the appended registrations, and the version bump is
  in the same commit as the first packet — as with protocols 12 and 13.

## 9. Milestones

1. **Model and format (no behaviour change; in progress).** **Landed:**
   `BlockKind`/`Material`/`MapBlock`, the shared rule table, strict version-1 text format and
   parser/writer, `ArenaMap` re-expressed through the embedded canonical loader, migration plus
   round-trip tests, and the version-2 SDF map-hash header/cache guard. `SdfBaker` now reads the
   SDF column of the shared table. The standard arena remains wall-only, so its legacy `solids()`
   geometry and SDF texels are unchanged. **Still deferred in this milestone:** the physical
   `assets/maps/arena.map` move. Gate: `shared`'s tests must prove the loaded arena is today's
   arena when a human/CI can execute them.
2. **Rule-aware consumers (in progress).** **Landed:** `ArenaMap.blockers(Blocking)` derives one
   cached rectangle view per purpose from the rule table; `MapQueries` line queries take a purpose
   while their five-argument forms stay the `SIGHT` convenience; `VisionMath` and the sight
   consumers ask the `SIGHT` column; `RaycastBulletSystem` and `BulletSystem.spawn`'s muzzle guard
   ask the `BULLETS` column, with the purpose threaded through `blockedByTerrain`; the frag,
   claymore-cone and fire-spread consumers ask `BLAST`, which the table pins to sight. **Still
   deferred in this milestone:** `PlayerMotion` learning platform tops through the movement column
   (it still reads the bullet compatibility view), `TerrainRenderer` drawing per kind (with glass,
   platforms and doors as flats), and the static check that no consumer tests a kind by name.
   Gate: the CI tests for one-way rules and the kind table, plus that static check.
3. **Breakables and glass.** Durability, damage sources, destruction, effects, SDF re-bake and
   hash update. Gate: destruction tests and the re-bake path's determinism (baking twice gives the
   same field).
4. **Doors, with dynamic occluders.** The occluder array in the visibility and light shaders, the
   door state machine and its collision/bullet/sight wiring, then the packets and the protocol
   bump. Gate: door tests plus a manual two-client pass (the acceptance document).
5. **Editor and CLI.** `MapDraft`, `MapEditorScreen` behind the debug switch, `MapTool check|bake`
   in CI, reachability in the gate. Gate: a map authored in-game saves, validates, bakes and loads
   in a fresh session.
6. **Shipped maps.** The arena moves to `assets/maps/arena.map`; additional maps become possible,
   with the map list a server setting.

## 10. Non-goals (this plan, deliberately)

- Free-form drawing, curves, rotation, non-axis-aligned blocks: every consumer is AABB-based, and a
  rotated collider would break the shared sweeper's contract for the whole game.
- Live editing of a running match, voting, or hot-reloading maps into a session: a map change is a
  revision bump the server owns, not an edit tool.
- Terrain deformation (destructible *ground*), caverns, and background parallax layers.
- Asymmetric map support: the builder's symmetry is a fairness rule and stays one. If asymmetric
  maps are ever wanted, that is a decision to take deliberately, not a format switch.
- Partial glass (dimmed sight, half-broken panes) and doors that are soft-occluding: both need
  per-pixel visibility modifiers the pipeline does not have today.

## 11. Open decisions to confirm

1. **Glass** as movement-only (D5) versus see-through-but-blocking-bullets or any partial variant.
2. **Round reset:** does destruction reset between rounds (the map revision returns to zero) or
   persist for the match? (Recommendation: reset per round, because a map that has been chewed
   through stops being the map both teams agreed to fight on.)
3. **Door access:** manual only, or also "open for the owning team" doors at spawn?
4. **Reachability strictness:** does a map that fails reachability ship with a warning or fail the
   build? (Recommendation: fail — the M8 gate already treats traversal as a hard requirement.)
