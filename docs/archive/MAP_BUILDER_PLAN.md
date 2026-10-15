# Map building system — plan

> **Archived 2026-10-14 — design/history reference, not an active progress tracker.**
> Outstanding implementation and verification work is tracked in [NOT_DONE.md](../NOT_DONE.md).
> Archiving does not mean every feature is implemented or runtime-verified.

**Status: milestone 3's source has landed, its first live run has happened, and milestone 4 has
landed in source — the close-out's last slice, the fixture door, closed it.** The shared
`BlockKind`/`Material`/`MapBlock` model, central `BlockRules` table, strict version-1 text
parser/writer, standard-arena migration and SDF map-hash guard landed in PR #47.
`ArenaMap.standard()` loads the embedded canonical text through that parser; every version-2 SDF
names its semantic map, and shared tests cover the rule rows, migration geometry, strict failures,
canonical save → load → save and the SDF hash contract. Milestone 2's first increment landed in
PR #48: line queries take a shared `Blocking` purpose, `ArenaMap` derives one cached rectangle view
per purpose from the rule table, and the sight, bullet and blast consumers ask their own column
instead of the compatibility rect list. The rest of milestone 2 followed: `PlayerMotion` sweeps the
movement column with one-way platform tops and the drop-through window (started by the S press
alone since the first live run; before that it was the crouch+jump chord), `TerrainRenderer`
draws every `BlockRenderStyle` the table declares, and `tools/scratch/static_check.py` now carries
the two consumer gates (no kind named outside `shared/map`, every treatment drawn). Milestone 3 has
now landed in source: the shared `MaterialTable` and its damage rules, the per-block authored
durability multiplier, `MapDamageState` (the per-round destruction state, reset by the round layer —
§11.2), block damage from rounds, blasts and swings, the `GLASS_SHATTER` effect, and the runtime
re-bake path (`SdfField.diff`, `SdfRebakeWorker`, the changed-region `SdfTexture.patch`). What
milestone 3 deliberately does **not** close: the client is not yet told that a block is gone, so
nothing requests a re-bake at runtime — that is the protocol-14 block event of milestone 4. So that
the milestone can be exercised in a running game anyway, one mirrored **fixture range** was authored
into the shipped arena and the SDF asset re-baked with it (§1, §9): the glass, the crate and the
one-way platform can all be met, shot and broken today, while what a destroyed block *looks* like
still waits on milestone 4. The `assets/maps` move, door ids and state and every later milestone
remain unfinished; no Java was compiled or executed by the authoring sandbox, and no checklist in
`docs/MAP_BUILDER_ACCEPTANCE.md` has been executed.

**The first live run (the user's desktop build) happened after the fixture range landed and
produced four findings, resolved this session.** The drop-through trigger moved from the
crouch+jump chord to a fresh crouch press (`S` alone) — D6 as amended, implemented in
`PlayerMotion` with the fresh-press gate and the inert jump on a slab. The other three — a pane
that ignored bullets, and a body that passed through the pane and the crate on a plain,
non-debug body — were traced through every consumer (rule table, both derived views, the live
map, the sweeper, the muzzle guard, the round walk, the shatter) and cannot be produced by the
current source; the fixture platform, from the same three map lines, worked in that same run.
The one explanation left standing is two processes on different maps, which the local-test path
can produce silently: when the embedded host fails to bind because an older server process still
owns the default ports, the client logs an error and joins that server anyway
(`Main.startLocalTest`). Both sides therefore now announce the map they actually parsed at
startup (`ArenaMap.describe()`: census, mirror axis, hash), the re-run is pending a human, and
the known milestone-4 gaps still apply — a shattered pane keeps rendering and keeps colliding in
the client's authored copy until the block event tells it otherwise.

**Since then the re-run has confirmed the server side and the destruction half of milestone 4
has landed in source.** The user rebuilt on a current build and saw destruction effects fire
(`GLASS_SHATTER`, wood debris), which proves the server's destruction path works end to end —
and reported the deliberate gap as a visual issue: a broken pane or crate keeps rendering and
keeps blocking the client's player and rounds, because the client is never told. The protocol
13 → 14 contract below (§8) now carries exactly that: the block's wire identity is its authored
index, `PacketBlockBreak` is broadcast reliably on the destruction tick, the join accept replays
the round's break history against the stated map hash, the snapshot carries the revision, and
the client holds a `MapDamageState` mirror feeding prediction, rendering, the minimap and the
first `rebakeSdf` caller. Doors, the dynamic occluder array, per-hit crack sync and the
map-hash join rejection are still milestone-4 remainder; the desktop re-run of the destruction
slice is pending a human, and no acceptance box has been executed.

**Milestone 4's door slice has now landed in source.** PR #55 landed the authored data
(`DoorSpec`/`DoorAccess`, the format verbs, the builder's mirror and cap rules) and the shared
machine and live-map half (`MapDoorState`, the door-aware `MapDamageState`, `swingFraction`,
`applyRemote`); this slice closes the loop over it: the use key (`F`, its own binding, with the
shared reach rule and an optimistic local prediction that retransmits the press with the usual
birth-sequence discipline), the server authority (`PlayerSession`'s one-shot edge, `DoorSystem`
resolving each press against authored reach plus the true occupancy of bodies and thrown
utilities, warnings through the console-response channel and a corrective record on a refused
blocked swing, `advance` re-armed by the tick), the protocol-15 door packet and join baseline,
the `DoorOccluders` uniform array in both the visibility and light shaders, and
`TerrainRenderer` drawing every door from the static map posed by the machine's swing fraction
while the SDF keeps naming the destruction-only static map. Still milestone-4 remainder at that
point: per-hit crack sync, the round-reset wire event and the server-side map-hash join rejection
— all three have since landed (the close-out ¶s below). The desktop re-run is pending a human, and
at that slice's landing the shipped arena authored no door yet, so the door boxes in the
acceptance checklist had nothing in a running build to open; the fixture door that fixes that
came with the close-out's last slice.

**The milestone-4 close-out has opened with the handshake's map guard fully enforced.** Slice A
lands the server half of §8's half-landed guard: `PacketJoinRequest` states the map the client
parsed (protocol 16), the server refuses any join whose stated hash is not the map it is running,
and the refusal carries the server's own hash so the refusal line has both sides of the comparison.
Nothing is a wildcard — an empty stated hash is an unstated map and is refused with the rest — and
the client's check of the accept stays the second line of the same gate. The desktop re-run is
pending a human, and no acceptance box has been executed.

**Slice B then closed the crack half of the same gap.** Per-hit crack sync is landed: a hit that
leaves a breakable standing is recorded as a `BlockChip` carrying the authority's running total,
`PacketBlockDamage` (protocol 17) broadcasts the tail reliably and the join accept carries the
per-block totals as the crack baseline, and the client's mirror applies totals rather than adding
them — so a cracked crate now reads cracked on every client that can see it, and a late joiner's
picture arrives cracked without a re-bake. Every acceptance box remains unexecuted.

**Slice C closed the third gap: the round-reset event, and `/mapreset` to run it.** §11.2's decision
— destruction resets per round — now has its wire: `PacketRoundReset` (protocol 18) goes out from
one round-reset transaction that puts the authored map and every door back, re-seeds the door diff
baseline and tells every client before the next tick can reference the restored map. The event says
"reset" rather than replaying the restored set, because both sides derive that set from the authored
map with the same call. The match layer that decides a round has ended is still later work, so the
operator's `/mapreset` (ADMIN) is the hand that runs it today — which is also what makes the reset
box in the acceptance checklist attemptable in a two-client run.

**The close-out's last slice has landed: the fixture door.** The shipped arena authors one mirrored
metal door — ids 1 and 2, both `ALL` — sealing the ground lane between the tall wall at
`x` 1010..1034 and the shorter one at 1070..1092 (the pair stands at 1034..1070 and 1930..1966,
`y` 100..270, and a shut panel is 170 tall against a jump rise of about 110, so it seals the gap to
movement and not only to sight). The whole Doors section of the acceptance checklist now has a
doorway a running build can walk up to and press `F` at; the one box it still cannot reach is team
refusal, because no `TEAM` door is authored — a mirrored pair shares one access by construction, and
per-side ownership would mean authoring two separate lines with their own ids. No protocol change
came with it: doors have been stateful and broadcast since protocol 15. The map's identity did
move, so `assets/data/arena.sdf` was re-baked in the same commit, the identity pins followed, and
`tools/scratch/arena_sdf_check.py` learned the door half of the format grammar so it can still
prove the committed bytes are the bake of the committed map. Milestone 4 is complete in source; the
desktop re-run is pending a human, and no acceptance box has been executed.

**The fixture door has been moved out of the sealed alcove (next slice, same branch).** The door pair
that sat inside the sealed alcove at `x` 1034..1070 (and its mirror at 1930..1966) has been moved to
a reachable position inside the 70-wide mirrored wall at `x` 820..890 — the door now stands at `x`
837..873 and its mirror at 2127..2163, `y` 100..270, centred in that wall so a body can reach the
door's own stances from ground level on either side and through the door's upper stances from a jump
off the ground below the wall. The map text changed, so `assets/data/arena.sdf` was re-baked in the
same commit under the new map hash `ed6b335d...e173e` (still 1500 × 1000 texels at 2.0 units/texel,
1,500,060 bytes) and `arena_sdf_check.py` re-confirmed the committed asset is the bake of the
committed map. The door move does **not** eliminate the 10 unreachable stances the CI gate reports for
the shipped arena: those stances sit inside the volume the tall wall at `x` 1010..1034 and the shorter
one at 1070..1092 enclose, and they are blocked by that wall geometry, not by the door (the door is
read as an opening by both the Java gate and the Python mirror, so it never blocks anything). They
persist at their old positions — `x` 1052.5 @ 100 and `x` 1072.5..1087.5 @ 270 (plus mirrors) — and
the test pins that name them by those coordinates still hold; the test pin that names the fixture alcove
by its old `x` range likewise still holds, because the unreachable stances have not moved. What the
move does accomplish is that the door itself is now reachable: a player can walk to it and press `F`,
which is what the §Doors boxes of the acceptance checklist need. The section's team-refusal box stays
unreachable for the same reason it was before — no `TEAM` door is authored.

## 1. What exists today

Every consumer now reads the shared rule table, so authored geometry has one meaning across
movement, sight, bullets, the bake and the picture, and a strict persistence path behind it.

- **`shared/map/Rect`** — an immutable axis-aligned rectangle (`x`, `y`, `width`, `height`, y up),
  with `mirror(axis)`, `translated`, `contains` and `overlaps`. It is geometry only; a block's
  shared rule row now decides whether that geometry blocks, bakes or draws.
- **`shared/map/{BlockKind, Material, MapBlock, BlockRules}`** — the immutable block triple and
  the one rule table for movement, sight, bullets, SDF inclusion and renderer treatment. Permanent
  versus destructible is the table's `DamageRule` column (`INDESTRUCTIBLE`, `DURABILITY`,
  `SHATTER`), and `MapBlock` carries the optional authored `durability=` multiplier that scales its
  material's pool.
- **`shared/map/{MaterialTable, BlockBreak, BlockChip, MapDamageState}`** — the destruction layer, all
  data.
  `MaterialTable` holds one immutable row per material: the durability pool, how much of each damage
  source's damage it accepts (`BULLET`, `BLAST`, `MELEE`), its fill and line colours, and its impact
  effect — so a surface's whole survivability and appearance are one row. `MapDamageState` is the
  per-round authority: it accumulates damage, removes a block from its derived **live map** when the
  pool is spent (or when a `SHATTER` block is hit at all), bumps the revision, and reports a
  `BlockBreak` for the debris, the sound and the eventual wire event. A hit that leaves the block
  standing is recorded as a `BlockChip` carrying the running damage total — the picture has to
  travel even though the map does not. `reset()` restores the authored map, clears the chips and the
  revision for the next round.
- **`shared/map/ArenaMap`** — immutable `blocks()`, rule-derived `blockers(Blocking)` (one cached
  rectangle view per query purpose), `movementBlockers(MovementRule)` (the same treatment for the
  movement column, which is not a line-query purpose) and a backwards-compatible `List<Rect>
  solids()` that is exactly `blockers(Blocking.BULLETS)`. `spawns()`, `width()`, `height()`, `mirrorAxisX()` and
  `bounds()` remain. Its public builder enforces `mirrored(block)`/`centred(block)` symmetry and
  rejects duplicate blocks. `ArenaMap.standard()` now parses `StandardArenaMap`'s canonical
  version-1 text; `ArenaMapTest` still pins its legacy geometry.
- **The shipped arena's fixture range** — what `ArenaMap.standard()` loads is no longer wall-only.
  Milestone 3 authored one mirrored range into it: a one-way platform high over the centre
  (`x` 1620..1840, `y` 1300..1318) carrying a pane (`1660..1680 × 1318..1498`) and a wooden crate
  (`1740..1800 × 1318..1378`, `durability=0.4`, so two carbine hits open it and a frag at its own
  height breaks it), with the whole range reflected to `x` 1160..1380. The reason is testability,
  not level design: with the editor still milestone 5 and no map file to edit, a running game has
  to be able to meet what the milestone built. It is authored data like anything else — mirrored by
  the builder, drawn from the material table, moved through and shot at by the shared columns, and
  baked into the SDF (the crate; the pane and the platform are not, exactly as the table says). The
  height is deliberate: `y ≈ 1300` is above every lane the existing tests use, so nothing that was
  pinned before this range existed moved, and `ArenaMapFormatTest` now pins the fixture census
  instead of the wall-only one. The close-out then authored a second fixture — the door pair the
  status ¶s above describe — one mirrored metal door with ids 1 and 2, both `ALL`, in the ground
  lane between the tall wall at `x` 1010..1034 and the shorter one at 1070..1092. It is authored data
  under the same contract (mirrored by the builder, moved through, blocked and shot at through the
  shared columns) and it is *not* baked: a door is a dynamic occluder, so that re-bake left every
  texel of `assets/data/arena.sdf` unchanged and moved only the header's map hash.
- **`shared/map/ArenaMapFormat`** — a dependency-free, strict line parser/writer. It rejects a
  malformed header, unknown verbs/kinds/materials, non-positive or out-of-bounds blocks,
  duplicate blocks and wrong symmetry verbs rather than exposing a partial map.
- **`shared/map/MapQueries`** — the stateless questions. Line queries (`lineBlocked`,
  `firstBlocker`, their five-argument forms are the sight convenience) answer a `Blocking` purpose
  from the shared rule table through `ArenaMap.blockers(Blocking)`. Movement questions
  (`overlapsMovementBlocker`, `surfaceBelow`) answer the movement column through
  `ArenaMap.movementBlockers(MovementRule)`, which is why intact glass stops a body here even
  though no line query notices it. `solidAt`, `overlapsSolid` and `solidsOverlapping` remain
  documented as the legacy bullet-view point/box questions — the tests that pin the old rect set
  use them, and no runtime consumer does.
- **`shared/physics/PlayerMotion`** — the shared swept-AABB movement with its own ground skin,
  ceiling clearance for uncrouching, and `noclip`. Server authority and client prediction run this
  same function; the server has no separate collision code. It sweeps the **movement column**:
  `MovementRule.SOLID` rects block on every face, and a `MovementRule.ONE_WAY_TOP` slab answers only
  a falling body whose feet were at or above its top on the previous step. A fresh crouch press on
  a platform — `S` alone, no jump; the body not already crouched when the sample arrived — starts
  the shared drop-through window (`PlayerConfig.PLATFORM_DROP_THROUGH_SECONDS`,
  carried as transient motion state on `Player`, so the snapshot layout and protocol 13 are
  untouched); the window ends as soon as the feet leave the slab, and while it runs the platform
  cannot catch the body again. A jump press while crouched on a one-way top is deliberately
  inert, and one riding the drop's own sample is consumed by it. The shipped arena's own one-way
  top is the sky platform of its fixture range (below), and a body on ordinary ground is never
  standing on it, so crouch and jump on the floor play exactly as they always did.
- **`core/world/TerrainRenderer`** — the picture of the same map: one loop over `blocks()` per draw
  pass, with the treatment chosen from the shared `BlockRenderStyle` column and the fill and outline
  colours from the block's `MaterialTable` row, so no block kind and no surface material is named in
  core. A `BREAKABLE` is cracked in proportion to its `damageFraction` when the renderer was handed
  a `MapDamageState` (and drawn pristine when it was not — a client that has been told nothing draws
  the authored map, which is the only honest picture). A door is still drawn closed because door
  state does not exist yet, and a platform is a slab whose top edge alone is highlighted.
- **`server/combat/RaycastBulletSystem`** — the swept round path: nearest entry across terrain and
  player hitboxes, with the terrain case recorded as the `MapBlock` that was hit (its material is
  what the impact looks and sounds like). Terrain is the `BULLETS` column of the shared table, and
  `blockedByTerrain` takes the purpose explicitly so the muzzle guard and the round's own sweep
  always ask the same question. `damageableCrossings` answers the complementary question — what a
  segment crosses that a damage source could change — because a window is in *no* blocking view and
  a damage path would otherwise never find it.
- **`server/combat/BulletSystem`** — per-tick projectile integration plus the muzzle guard
  (`raycaster.blockedByTerrain(eye, muzzle)`), which the per-weapon muzzle made load-bearing. A
  round's path is now walked nearest-first: a surface it merely chips, a wall, a closed door, a body
  or a device spends the round where it stands, while a block the round itself takes out — a pane,
  or the last hit on a breakable — lets it fly on through the hole at
  `CombatConfig.PASS_THROUGH_SPEED_LOSS_FRACTION`. Every terrain query it makes reads the live map.
- **`server/combat/MeleeSystem`, `server/utility/UtilitySystem`, `server/gadget/FuelTankSystem`** —
  the other damage sources. A swing lands on the blocks its arc covers, measured to each block's
  nearest point; a frag, an impact grenade, a claymore cone and a fuel-tank blast resolve their
  blocks through the shared `ExplosionMath`/`DirectionalBlastMath`; and all of them read the live map
  for every terrain question they ask. `DroneSystem`/`CameraSystem` follow the live map too, because
  a device obeys the same movement column a body does.
- **SDF** — `tools/.../SdfBakeTool` bakes `ArenaMap.standard()` through `shared/sdf/SdfBaker` into
  `assets/data/arena.sdf`, a version-2 binary (`SdfField.MAGIC`/`VERSION`) whose header carries a
  SHA-256 map hash. `core/fx/sdf/SdfCache` rejects a missing, old or mismatched field in a
  shipping-default run; an explicitly enabled development run logs and re-bakes it. `SdfTexture`
  uploads the verified field and the visibility, light, particle and debug passes read it. The
  runtime **re-bake path** (D4) now exists: a change asks `FxPipeline.rebakeSdf(liveMap)`, an
  `SdfRebakeWorker` bakes off the frame (one bake at a time, newest map wins), and the next frame
  patches only the texels `SdfField.diff` reports and re-supplies the new field to the particle
  systems that hold it. The client asks for it when the destruction mirror changes (milestone 4's
  block event) and again when the protocol-18 round reset puts the authored map back. The shipped
  `assets/data/arena.sdf` was re-baked by this milestone, for the one reason that the
  map itself changed: the fixture range below. The per-block durability multiplier did *not* move
  the hash — it is hashed only when the map authors one — so the re-bake is entirely the new
  geometry, and `tools/scratch/arena_sdf_check.py` proves the committed bytes are the bake of the
  committed map. The **server never reads the SDF** — the client uses it for what is drawn (cone
  shadows, light shadows, particle collision).
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
  `NetworkRegistrationTest` asserts the counts (40 registered types as of protocol 18, the newest
  protocol's test owning the exact size pin). `PROTOCOL_VERSION = 18`. Milestone 3 added **no**
  packet — destruction was server authority and the client was not told about it yet — and
  milestone 4 has since appended the block event, the door state, the chip and the round reset, with
  the join accept, the join request and the snapshots gaining the fields §8 lists.

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
backwards-compatible derived bullet-blocking view, so a call site that has not been taught about a
kind keeps the answer it always got while a new one can ask by rule.

**D2 — One rule table, four readers.** Every kind declares, in one shared table, what it does to
each consumer: movement, sight, bullets, the SDF bake, and rendering. The table is the plan's
heart (§3), and every consumer reads it rather than testing kinds by name. **Implemented:** the
shared rows and tests, `SdfBaker`'s SDF column, milestone 2's line-query threading —
`ArenaMap.blockers(Blocking)`, purpose-aware `MapQueries` line queries, `VisionMath`, the bullet
raycaster and muzzle guard, and the blast consumers — `PlayerMotion`'s movement column with the
one-way drop-through window, and `TerrainRenderer`'s `BlockRenderStyle` treatments. Two gates in
`tools/scratch/static_check.py` keep it that way: no main source outside `shared/map` may name a
block kind (or test one in a `switch`), and the renderer must mention every declared treatment.

**D3 — Doors are server-owned dynamic blockers.** A door has an id, an `access` rule and a state
(`CLOSED`/`OPENING`/`OPEN`/`SHUTTING`) with the opening tick. Collision, sight and bullets consult
the *state*, never the animation. Because a door changes every few seconds, it is **not** baked
into the SDF: doors that occlude are handed to the visibility and light shaders as a small fixed
array of dynamic occluders, using the pattern the smoke circles already use. Closed doors are in
the collision/bullet sets; open doors are in neither. **Implemented:** the shared
`MapDoorState` machine (§7's transitions, with `swingFraction` the only thing the picture reads)
inside `MapDamageState`; `DoorSystem` as the server authority feeding it presses and its tick;
the use key's client half (`DoorUseController`) predicting with the very same functions against
its own door-clock estimate; `PacketDoorState` carried reliably per change plus the join
payload's door baseline (protocol 15); `DoorOccluders` uploaded per pass from exactly the doors
whose state still blocks, with the analytic segment-vs-rect test in both `visibility_cone.frag`
and `light_radial.frag` beside the smoke attenuation; and `TerrainRenderer` drawing doors from
the static map posed by the fraction — never from the live map, so no door blinks.

**D4 — Breakables are baked and re-baked.** A breakable wall is a full solid while intact, so it
is part of the SDF (it really does cast shadows). When it is destroyed, the block leaves the map
and the SDF is re-baked once, on a worker, and the changed texel region is uploaded. Destruction is
rare and authoritative, so the cost is a single hitch on the frame it happens — acceptable, and
measurable; doors never take this path. **Implemented:** `SdfRebakeWorker` (one worker, one pending
request, newest map wins), `SdfField.diff(SdfField)` as the changed-texel region, and
`SdfTexture.patch(SdfField)` for the region upload. The field is held by three parties (the texture,
which also reaches the visibility, light and debug passes, and the particle systems, which keep the
`SdfField` object), so the frame that patches the texture re-supplies all of them — the field is
never mutated under a reader. **Landed:** the play screen's destruction sync is the first caller —
every applied block event and every non-empty join baseline re-bakes for the client's live map,
whose hash the re-baked field names, while the load-time cache guard still verifies the shipped
asset against the authored hash (D9). The path itself is pinned by the determinism tests in
`shared`.

**D5 — Glass is a movement blocker you can shoot through.** Intact glass stops bodies, not sight
and not bullets (the window's reason to exist is to look and shoot through it). Any round shatters
it — it has no durability — the round continues with a small speed loss, and the fragments leave
the map entirely: no movement block, no occlusion, no SDF involvement either way. Movement through
intact glass is impossible; breaking it is the only way through. **Implemented:** the movement
column was milestone 2's work; the shatter is the `GLASS` row of `BlockRules.damage`, which makes any
accepted hit take the block out in one call, and the round's continuation is
`BulletSystem.resolvePath`, which loses `CombatConfig.PASS_THROUGH_SPEED_LOSS_FRACTION` (12%) per
block it removes. The shards are the appended `EffectType.GLASS_SHATTER`, whose recipe and recording
landed with the effect catalogue. (Partial-attenuation glass —
visible through, dimmed — is explicitly deferred: it needs a per-pixel visibility modifier the
pass does not have.)

**D6 — One-way platforms are thin and see-through.** Collision from above only (landing), never
from below, never a ceiling, and never a movement block for a falling body; no bullet blocking, no
sight blocking, and so no SDF presence and no shadow. Drop-through is a fresh crouch press (`S`
alone) while standing on the slab, which starts a short timer during which the body ignores
one-way tops; the timer ends when the body has cleared the slab. **Amended after the first live
run asked for `S` alone** — the chord this decision originally shipped was crouch+jump, and plain
`S` was reserved as the crouch/dodge; the user's request overrides that, and the consequences are
pinned in `PlayerMotion`'s javadoc: on a one-way top a fresh `S` press always drops (so a crouch
cannot be *started* while standing on a platform — crouch-walking onto one with the key already
held still works), and a jump press while crouched on a slab is inert so the body can never be
launched back up through it. **Implemented** in `PlayerMotion` and pinned by
`shared/src/test/.../physics/PlatformMotionTest`.

**D7 — Materials decide everything survivable.** One shared `MaterialTable`: durability,
bullet-damage retention, whether explosions/melee can hurt it, the render tint/line colour, impact
effect and impact sound. Stone: tough, no bullet damage. Metal: tougher, reflects nothing, louder.
Wood: breakable by bullets. Glass: shatters on any hit. Energy: scenario blocks, only explosions.
**Implemented** as `shared/map/MaterialTable` with exactly one `Profile` per `Material` (the table
refuses to build with a row missing) and `DamageSource` for the three columns. The "whether a source
can hurt it" question and its measurement are one pair of calls — `takesDamage` and
`acceptedDamage` — so a damage path filters with the same row it measures with. Per-block variation
is the authored `durabilityMultiplier`, which scales the *pool* rather than the damage taken, so a
reinforced crate survives more hits rather than absorbing each one better. The retention numbers were
chosen so the four sources read as themselves: rounds open wood and glass, stone shrugs them off and
only yields to blasts, metal chips slowly, energy answers to explosions alone.

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

**Implemented for every row above** (the `DOOR` rows with milestone 4's door slice). The damage
column is
`MapDamageState` plus each system's own call — a round's walk, `ExplosionMath`/`DirectionalBlastMath
.resolveBlocks`, `MeleeSystem.damageBlocks` — and every gameplay consumer reads the *live* map, so
"gone" means gone from movement, sight, bullets and the next bake in the same tick the block
leaves. The picture is the one place that reads the *static* map instead: `TerrainRenderer` poses
an open door's panel from the machine's swing fraction rather than letting it vanish with the live
map, and the re-bake path of D4 covers the SDF half of the `BREAKABLE` row while the `DOOR` rows'
SDF column ("excluded") is the occluder array of D3.

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
  call (they ask `BLAST`). The sweeper followed in the next increment, on its own column:
  `ArenaMap.movementBlockers(MovementRule)`, asked by `PlayerMotion`, the two extra sweeps in
  `shared/gadget/DroneMotion` and `shared/utility/ThrowablePhysics` (a device follows the movement
  column as a body does; a thrown object follows `BULLETS` as a round does), and
  `MapQueries.overlapsMovementBlocker`/`surfaceBelow`. No main source outside `shared/map` reads
  the compatibility view any more, and the static check enforces it.
- **One-way platforms cannot be expressed by the current sweeper.** `PlayerMotion.integrateAndCollide`
  tests every rect for both axes; platform support is an added rule — tops are considered only when
  `vy <= 0` and the body's feet were above the slab on the previous step, and skipped while the
  drop-through timer runs. This is *shared* code, so prediction and authority change together.
  **Landed**, with one extra condition the sweeper needed: a body *inside* a slab is neither caught
  by its top face nor pushed up out of it, and the drop-through window treats contact with the top
  as inside, because that is the position the drop starts from.
- **Doors need the occluder array before they can occlude.** Until the visibility and light shaders
  take a dynamic occluder array (bounded, e.g. 16 rects, the smoke-circle pattern), a closed door
  must be non-occluding, and the plan's first shippable door therefore opens and closes *movement
  and bullets* only. That is stated here rather than discovered in the acceptance pass.
- **Rendering stays a function of the map.** `TerrainRenderer` reads `map.blocks()` and draws one
  treatment per row of the table: a filled slab with an outline for `SOLID_FILL` and
  `BREAKABLE_FILL`, a translucent pane with a sheen for `GLASS_PANE`, the closed panel with its
  seam for `DOOR_PANEL`, and a slab whose top edge alone is highlighted for `PLATFORM_SLAB`. Fill
  and outline colours are the block's `MaterialTable` row, and a breakable's cracks are its damage
  fraction, so the picture is the same data the collision is. It still owns no state of its own,
  which is why the one treatment whose appearance depends on state the game does not have yet — the
  door's swing — is still drawn in its current, honest form instead of being faked. The minimap's
  static geometry reads the same block list, so the two pictures cannot disagree; a client that has
  been handed a destruction state draws its live map, and one that has not draws the authored one.

## 4. Doors, breakables, glass and platforms in detail

**Doors.** Fields: `id` (stable per map), `access` (`ALL`, `TEAM` with a team index matching a
spawn side), `state`, `openingTick`, `holdSeconds`. Interaction: `E`/the use key on the nearest
door within reach (a shared `MapQueries.doorAt`), or a cvar-free automatic mode for doors that
should just swing (not planned; manual only). The server owns the state machine, resolves the
"whose team" question against the opener's team, and refuses while a body or a thrown object
blocks the swing. Client: predicts the swing from its own use press (optimistic), and reconciles
when the authoritative state arrives — the same predict/reconcile discipline as movement. A door
blocked by a body stops opening (server decision, replicated as a state, so both sides agree).

**Implemented, with the two deviations from the sketch noted.** (1) The use key is `F`, not `E`
— `E` is the E-gadget slot, and doubling one binding onto two presses was never an option;
`F` is its own binding in the table and in the settings dialog. (2) The hold is in ticks
(`DoorConfig.HOLD_TICKS`), not seconds — everything door-related measures against the
simulation clock, so the wire carries only ticks and no wall-clock time ever crosses it (D3).
Everything else is as sketched: `MapQueries.doorAt` resolves the press against the authored map
on both sides within `USE_REACH`; the shared `MapDoorState` machine answers access (`DoorSpec`)
and occupancy refusals; `server/world/DoorSystem` runs it against the true world (every live
body by hitbox — the presser's included, so a press from inside the swing is refused; every
thrown utility by position point); the client predicts optimistically through the same functions
and reconciles on the authoritative record; and a blocked press is refused, never queued.

**Breakables.** Durability lives in the material plus a per-block multiplier. Damage sources:
explosion damage at the block's nearest point (scaled by distance and the material's blast
sensitivity), bullet impacts (scaled by the material's bullet retention), and melee hits. Below
zero the block is destroyed: an effect is emitted (dust/debris colour from the material), the block
leaves the authoritative map, the revision increments, and the SDF re-bakes. Nothing is repaired in
a round; per-round reset is a server-side map revision reset (decision to confirm, §8).

**Implemented, and the two places it differs from the sketch.** (1) Durability is not *in* the
material alone: the material row holds the pool and the block holds the authored multiplier
(`durability=<x>` on a breakable line of the map text), so two crates of the same wood can differ
without a second material. (2) The measurement lives with the source, not here: a round measures
the same falloff it would apply to a body at that distance, a blast measures at the block's nearest
face, a swing uses its own damage — and all three hand the number to `MapDamageState.damage`, which
asks the material's retention, the kind's rule and the block's multiplier. A hit that does not
spend the pool returns `null`; the hit that does returns the `BlockBreak` the caller turns into
debris and its recording (`BlockBreak.effect()` is the material's impact effect, and the recording
already followed from the type through `EffectSoundTable`). The SDF half is D4. **§11.2 is settled:
destruction resets per round**, and the hook is `GameServer.damageState()`'s `reset()` — nothing
calls it yet because no round state machine exists.

**Glass.** One hit, any hit. The shattering round keeps going with a small speed loss, so shooting
through a window does not behave like shooting a wall. No durability, no partial breakage, no
half-shattered collider.

**Implemented:** `DamageRule.SHATTER` on the `GLASS` kind means the first accepted hit takes the
pane out of the live map in that call, whatever the number was — the material's durability is only
there so a *breakable* pane authored out of glass still reads as glass. The round's continuation is
the patched-block branch of `BulletSystem.resolvePath` (12% of its speed per block removed, per
`CombatConfig.PASS_THROUGH_SPEED_LOSS_FRACTION`), the shards are the appended
`EffectType.GLASS_SHATTER`, and the fragments leave the map entirely because a shattered pane is
simply absent from the live map's views. A pane in front of a device is shattered *before* the round
reaches the device: the damage walk is bounded by whatever spends the round, so nothing behind a
window is hit through intact glass.

**Platforms.** Only the top surface collides, only for a body that is falling and was above it on
the previous step, and never while the drop-through timer is running. The timer starts on a fresh
crouch press (`S` alone) while standing on the slab, and ends when the body's box no longer
overlaps the slab (so a body cannot re-land on it while inside it). **Implemented** in
`PlayerMotion`; §3 records the two conditions the sweeper
needed on top of the rule — contact with the top counts as "inside the slab" for the window's end
test, and a body inside a slab is never pushed up out of it.

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

**Implemented, and the trigger has landed too.** The in-game half exists as
`FxPipeline.rebakeSdf(ArenaMap)` → `SdfRebakeWorker` → `SdfTexture.patch` +
`GpuParticleSystem.setSdfField`, with `SdfField.diff` naming the changed texel region and
`SdfField.matchesMap` still the only staleness guard: every bake names the map it was made from, and
the worker's request carries the live map, so a stale field can only come from a caller that passes
the wrong map. Milestone 4's destruction half supplied the *trigger* it was waiting for — the
client's authoritative removed set rides the protocol-14 block event and `GameScreen` asks
`rebakeSdf(damageState.staticMap())` when that set changes — and the protocol-18 round reset asks
for the same refresh when the authored map comes back. `MapTool bake` is milestone 5's CLI.
The shipped `assets/data/arena.sdf` was re-baked in the same commit as the map text it names: the
fixture range below is authored geometry, the crate is baked into the field while the pane and the
platform are not, and `tools/scratch/arena_sdf_check.py` re-implements `MapHash`/`SdfBaker` to
prove the committed bytes are that bake — the only way this sandbox can verify a binary the client
loads by hash. The close-out re-baked it once more, in the commit that authored the fixture door: a
door is not baked geometry, so the field is unchanged texel for texel and only the header's hash
moved, and the checker learned the door half of the grammar (`id=`, `access=`, duplicate ids,
`DoorSpec.reflection()`'s next-id rule) so it can still prove that.

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

*Landed as slice B (PR #64).* The shared core is `MapAudit`, in `shared`: `check(map, label,
reading)` returns the verdict and the report lines both surfaces print, and `bake`/`bakeField` are
the one bake. The CLI is a `tools` module of its own — the only module that may not be reached from
the game modules and the only one that reaches `shared` and nothing else — with `check [--strict]`,
`bake` and `bake --check`, wired as `tools:mapCheck` and `tools:mapBakeCheck` in CI. Format
validation is not re-implemented: every map reaching the audit has already been through
`ArenaMap.Builder`, which the parser, the editor's draft and the tests all use.

*Landed as slice C.* The in-game surface is `MapEditorScreen`, and it is a view in the sense this
section means: a canvas with the game's own `TerrainRenderer` on it, over a `MapDraft`, with the
palette's entries the only shapes it can place. The verb it places with is `mirrored` — a tap clear
of the mirror axis authors a pair and a tap on the axis authors one centred block, because that is
what the format's two verbs mean — and a duplicate is refused at the tap rather than at the save,
since the twin is what usually collides. Validation is `MapAudit.check` over the draft written and
parsed back, i.e. over what a session would load rather than the in-memory list, and it marks
`Result.pocket()` on the canvas. Save goes through `ArenaMapFormat`'s writer and proves itself by
parsing its own text back first, and Bake is `MapAudit.bake` — the same call `MapTool bake` makes,
so the screen and the CLI cannot bake two different files. Files land in the user's own directory
(`Gdx.files.external(".skystrike/maps/")`, `local("maps/")` as the fallback) under a name reduced
to letters, digits, `-` and `_`, so authoring cannot write into the repository and cannot name a
path. The screen names no block kind, which is why §9's consumer gate needed no exemption for it:
the kind-naming table is `MapPalette`, inside the map package. The entry point is a debug-only main
menu action, built only while `DebugFlags.enabled()` — absent from a release build rather than
hidden in one — and the screen's constructor is handed the route back to the menu and nothing else,
so it has no session, no socket and no match state to publish with.

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
  lands; a fresh crouch press (`S` alone) drops it through and it does not re-land while
  overlapping; a body already inside a platform is not ejected.
- **Door rules (implemented, milestone 4's door slice):** `MapDoorStateTest` pins the machine's
  transitions, refusals and mirror-reconciliation rules; `MapDamageStateTest` pins the column
  contract — a closed door fills every blocking column (movement, sight, bullets, blast) and an
  open one leaves them all — beside the live-map parity with destruction; and in
  `server/src/test`, `DoorSystemTest` pins the authority half the machine cannot know: the reach
  resolution, the incapacitated-player gates, the team rule, bodies and thrown utilities as
  occupancy (a dead body blocks nothing), the blocked-shutting-swing reopen, and that the press
  during a swing is ignored identically on both sides. `PlayerSessionTest` pins the use edge's
  one-shot latch and its dedupe by birth sequence.
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
  the wall behind a window.
- **Movement column and one-way rules (implemented, the rest of this milestone):**
  `PlatformMotionTest` authors one thin slab in open air and proves the four properties of §7 —
  a rising body is never stopped or landed on, a falling body lands on the top face and stays
  there, a fresh crouch press (`S` alone) drops a body through without ever re-landing on it while
  the feet are inside it, and a body already inside a slab is neither ejected nor supported. It
  also pins the fresh-press gate the first live run's amendment added — a crouch held from before
  the landing never drops (crouch-walking on a platform works) while a release-then-repress does —
  and that a jump press while crouched on a slab is inert and one riding the drop's sample is
  consumed by the drop. And it pins that a platform is not a horizontal collider and that crouch
  and jump on the floor of the shipped arena (whose only one-way top is 1200 units overhead) still
  behave as they always did. `MapQueryPurposeTest` proves the movement questions read the movement
  column
  (glass and a closed door stop a body, a platform never does; every rule presents a standable top
  for `surfaceBelow`), and `NetworkRegistrationTest` proves the drop-through window stays off the
  snapshot layout. The two milestone gates are checked by `tools/scratch/static_check.py`: no kind
  named outside `shared/map`, and every declared treatment drawn.
- **Breakables, glass and the re-bake (implemented, milestone 3):** `MaterialTableTest` pins one
  profile per material, the retention columns and the pool arithmetic (`durability × multiplier`,
  infinite for a permanent kind); `BlockRulesTest`/`ArenaMapFormatTest` gained the `DamageRule` rows,
  the `durability=` attribute (accepted only where the kind has a pool, rejected when duplicated,
  malformed or non-positive, absent from a canonical save when default) and the unchanged canonical
  arena hash; `MapDamageStateTest` proves damage lands only through the material's sources, that a
  breakable leaves the live map and every rule view in the same call the pool is spent, that a pane
  shatters on any hit and never keeps a partial state, that the live map's hash is reproducible from
  `ArenaMap.without`, that `reset()` restores the authored map and revision 0, that a block from
  another map is refused, and that the shipped arena's permanent blocks can never leave its
  resting state while its authored fixtures can.
  `ExplosionMathTest`/`DirectionalBlastMathTest` cover the nearest-face measurement, the
  self-shadow exclusion, the cone-on-the-nearest-corner rule and the chip-then-break resolution;
  `EffectTypeTest`, with `EffectSoundTableTest`'s catalogue sweep, covers the appended
  `GLASS_SHATTER`. `SdfRebakeTest` is the milestone gate: baking twice is byte-identical, a re-bake
  after a break names the live map and repeats exactly, the changed region stays inside the room the
  crate stood in, copying that region reproduces the re-bake byte for byte, and a field on another
  grid diffs to everything. In `server/src/test`, `BlockDestructionTest` pins the round's walk (a
  pane costs exactly the configured pass-through fraction, measured against a control round in the
  same room without the pane; a pane in front of a device shatters before the device is reached; a
  chip stops the round and the hit that opens the block lets it through; a swing chips then breaks
  what its arc covers; a stone breakable refuses both the round and the blade) and
  `UtilityBlockDestructionTest` pins the blast (one break at the near face, a chip further off, the
  debris event raised before the explosion's own, and a wall as absolute cover).
- **Destruction wire (implemented, milestone 4's first half):** `MapDamageStateTest` proves the
  authored index names each block's list position, that breaks are recorded in destruction order
  with the revision each one moved, that a mirror fed remote breaks lands on the authority's
  live map and revision (re-application and out-of-range indices change nothing), and that a
  reset clears the history with the round. `NetworkRegistrationTest` pins the appended
  `DamageSource`/`PacketBlockBreak` at indices 35–36 with 37 types and protocol 14, and
  round-trips the event, the join baseline (map hash, revision, break history) and the snapshot
  revision. `MinimapModelTest` proves destruction re-points the minimap's terrain without
  forgetting sightings. The server broadcast diff, the join payload assembly and the client's
  application path are source-read, not suite-covered: no test boots a server or a screen.
- **Join map gate (implemented, the close-out's first slice):** `NetworkRegistrationTest` pins the
  protocol-16 handshake — the request's stated hash round-trips, an unstated one stays empty, the
  refusal carries the server's map and no other refusal states one — and, in `server/src/test`,
  `JoinRequestHandlerTest` pins the decision itself: the authored map's own hash is admitted, any
  other tuple of hex and an unstated map are refused with the server's hash named, and a map that
  differs only in the order its lines were authored still joins.
- **Per-hit crack sync (implemented, the close-out's second slice):** `NetworkRegistrationTest`
  pins the protocol-17 append and round-trips the packet and the join accept's crack baseline;
  `MapDamageStateTest` proves a chip carries the running total, that a mirror applies totals
  (idempotent, order-tolerant, refusing out-of-range, destroyed, NaN and non-positive records
  alike) and that the baseline holds one record per standing damaged block rather than the hits;
  and, in `server/src/test`, `BlockDestructionTest` pins the same through the real path — one
  chip per non-lethal carbine hit, the record equal to the authority's accumulated damage and
  short of the pool, the break that follows superseding it, and a mirror fed both landing on the
  authority's fraction and revision.
- **Round reset (implemented, the close-out's third slice):** `NetworkRegistrationTest` pins the
  protocol-18 append and round-trips the event; and, in `server/src/test`,
  `ServerCommandServiceTest` pins the command layer — `/mapreset` hands the round's transaction to
  the simulation exactly once, answers with the count it was given, and is refused for a caller
  below ADMIN without ever reaching the simulation. The transaction itself (the state, the door
  machine, the re-seeded diff baseline and the broadcast) is source-read: no test here boots a
  server.

## 8. Wire contract (protocol 13 → 18; destruction, doors, cracks and the round reset have
landed, and the close-out's last slice needed no wire change)

- **Block identity — decided: the authored index.** `MapBlock` carries no numeric id, and both
  sides build the identical authored list from the same canonical text, so a block's position in
  that list (`MapDamageState.authoredIndexOf`) is its stable, compact wire identity; carrying the
  whole triple was the fallback and was not needed. The identity is only meaningful against the
  map hash the join accept states — a client whose map differs must not apply indices at all.
- **Block events (landed):** `PacketBlockBreak` — tick, authored index, `DamageSource` cause,
  impact position, the shattered flag, revision — broadcast reliably in destruction order on the
  tick the block dies. Reliable, unlike the effect batches: a lost batch costs a flash, a lost
  break would strand a wall on one client, and destruction is rare enough that TCP costs nothing.
  The server diffs `MapDamageState.breaks()` every tick; history position `i` is the break that
  moved the revision from `i` to `i + 1`.
- **Join payload (landed):** the accept carries the authored map hash, the destruction revision
  and the round's break history as the same records the live path carries, so the live path and
  the late-join path apply identical state through one code path. The client checks the hash
  first and refuses a foreign index set loudly rather than removing the wrong blocks.
- **Snapshot (landed):** a `mapRevision` rides the game-state packet, and nothing else: the block
  set itself is static per map and shipped. It belongs in this slice rather than the door slice
  because destruction defines the revision — but it is advisory, not a sync mechanism, since
  unreliable snapshots race reliable breaks; the client reads it for its debug readout and the
  door slice will give it a corrective use.
- **Handshake map guard (landed, protocol 16):** both halves now exist. The server states the map
  hash in the accept and the client verifies it before touching its mirror, which turns the
  first-live-run stale-process failure into a loud client error; and the client states its own hash
  in the join request, which the server compares against the map it runs and refuses on mismatch
  (`REASON_MAP_MISMATCH`), carrying its own hash in the refusal so the player has both lines to
  compare. An empty stated hash is an unstated map, not a wildcard, and is refused with the rest.
  The comparison is over hashes, so a map re-authored with its lines shuffled is still the same
  map — the tolerance the SDF guard keeps too. The decision is one pure function
  (`JoinRequestHandler.mapRefusal`, covered in `server/src/test`) plus one check in the handler,
  which logs both hashes because the mismatch that motivated the guard is a host problem whose
  only evidence is that line.
- **Door state (landed):** `PacketDoorState` — dispatch tick, authored door id, state ordinal,
  opening tick — broadcast reliably per change immediately after the tick's door step, and
  replayed on the join accept as the every-door baseline; a refused blocked swing earns the
  presser an authoritative correction unicast of the same record. Reliable for the same reason
  the block event is: a lost record would leave a doorway open on one client and walled on
  another, and doors change at human pace, so reliable transport costs nothing. The state rides
  as a plain int ordinal with a defensive `DoorState.fromOrdinal` decode — the enum itself stays
  off the registered list — because a door answer that cannot decode fails shut instead of
  walking a body through a panel.
- **Per-hit crack sync (landed, protocol 17):** `PacketBlockDamage` — dispatch tick, authored
  index, the authority's accumulated damage after the hit — broadcast reliably per chip and replayed
  as the crack baseline on the join accept. Reliable rather than the unreliable channel the plan
  originally sketched: the record is tiny, hits are far rarer than snapshots, and a lost chip would
  leave one client's cracks behind the other's for the rest of the round, which is exactly the
  visible disagreement the record exists to remove. The number is the running total, not the hit's
  size: the receiving side applies it (max-wins) instead of adding it, so a duplicate or a late
  record can never chip a block further than the authority did, and no fraction travels because the
  fraction follows from the total through the material row both sides read. A hit that takes the
  block out stays a break — the two records are disjoint per hit — and a chip changes no rule view,
  no live map and no SDF, so applying one never re-bakes.
- **Round-reset event (landed, protocol 18):** `PacketRoundReset` — dispatch tick and the
  authority's restored count — broadcast reliably when the round layer puts the authored map back,
  and replayed to a client whose play screen has not registered yet. It states an instruction, not a
  restored set: the authored map and the door machine are identical on both sides and
  `MapDamageState.reset()` is the same call on both, so the receiving end cannot misapply it. One
  server-side transaction owns it — the state, the doors, the door diff baseline and the event —
  because a client told the walls are back while the server still holds the chewed ones is exactly
  the desync the event prevents. The break and chip diff counters need no part of it: both follow a
  history that shrank, which is the case they were written for. `/mapreset` is the operator's hand
  on the round layer that does not exist yet; the automatic caller is later work.
- **Anti-cheat stance:** the server decides every door, every break and every shatter; the client
  predicts optimistically with the same shared functions and reconciles. A modified client that
  claims a wall is broken simply walks into a wall: movement, bullets and line of sight are all
  resolved server-side against the authoritative block set. The client can at most make its own
  copy of a door look open for a round trip.
- `NetworkRegistrationTest`'s counts move with the appended registrations, and the version bump is
  in the same commit as the first packet — as with protocols 12 and 13. Protocol 14 appends
  `DamageSource` and `PacketBlockBreak` at indices 35–36 (37 types); protocol 15 appends
  `PacketDoorState` at 37 (38 types); protocol 16 appends nothing — it changes the shape of
  `PacketJoinRequest` and `PacketJoinReject` — so the list stays at 38; protocol 17 appends
  `PacketBlockDamage` at 38 (39 types); protocol 18 appends `PacketRoundReset` at 39 (40 types);
  the close-out's last slice, the fixture door, added no packet, so the list stays at 40 and the
  newest protocol test still owns the exact size pin and the version pin.

## 9. Milestones

1. **Model and format (no behaviour change; landed).** **Landed:**
   `BlockKind`/`Material`/`MapBlock`, the shared rule table, strict version-1 text format and
   parser/writer, `ArenaMap` re-expressed through the embedded canonical loader, migration plus
   round-trip tests, and the version-2 SDF map-hash header/cache guard. `SdfBaker` now reads the
   SDF column of the shared table. The standard arena's legacy `solids()` geometry and SDF texels
   were unchanged by this milestone, and stayed so through milestone 2 — milestone 3 then authored
   the fixture range described below, which moved both on purpose. **Still deferred in this
   milestone:** the physical
   `assets/maps/arena.map` move. Gate: `shared`'s tests must prove the loaded arena is today's
   arena when a human/CI can execute them.
2. **Rule-aware consumers (landed).** `ArenaMap.blockers(Blocking)` derives one cached rectangle
   view per purpose from the rule table; `MapQueries` line queries take a purpose while their
   five-argument forms stay the `SIGHT` convenience; `VisionMath` and the sight consumers ask the
   `SIGHT` column; `RaycastBulletSystem` and `BulletSystem.spawn`'s muzzle guard ask the `BULLETS`
   column, with the purpose threaded through `blockedByTerrain`; the frag, claymore-cone and
   fire-spread consumers ask `BLAST`, which the table pins to sight. `PlayerMotion` learns one-way
   platform tops through the movement column, with the drop-through window (its trigger then the
   crouch+jump chord; moved to the S-alone press after the first live run — D6, §1, §4) and the
   transient `Player` state that keeps protocol 13 untouched; `DroneMotion`, `ThrowablePhysics` and
   the camera's release check each moved to their explicit column; and `TerrainRenderer` draws every
   `BlockRenderStyle`, with the minimap reading the same block list. `tools/scratch/static_check.py`
   carries the two gates: no consumer names a block kind, and every declared treatment is drawn.
   The CI tests for the one-way rules and the kind table are written (`PlatformMotionTest`,
   `BlockRulesTest`, the movement cases in `MapQueryPurposeTest`). Gate: those tests must pass in
   CI, which no sandbox can check — no Java was compiled or run here.
3. **Breakables and glass (landed in source; the pull request is open).** Durability from the
   material row times the block's authored multiplier, three damage sources (round, blast, swing),
   destruction through `MapDamageState` and its derived live map, the material's own effect and
   recording, and the re-bake path (worker plus changed-region patch). **No `PROTOCOL_VERSION`
   change:** the client is told about none of it yet, which is milestone 4's block event. **Still
   deferred in this milestone:** the client half of the picture (a destruction state to hand
   `TerrainRenderer`, and a caller for `FxPipeline.rebakeSdf`). **Deliberate deviation from the
   plan as written:** the arena no longer stays wall-only — one mirrored fixture range was authored
   into it (see §1) so the milestone can be exercised in a running game while the editor is still
   milestone 5, and `assets/data/arena.sdf` was re-baked with it. Gate: the destruction tests and
   the re-bake determinism proof
   (`SdfRebakeTest` plus the server destruction cases) must pass in CI, which no sandbox can check —
   no Java was compiled or run here.
4. **Destruction wire, then doors with dynamic occluders.** The destruction half has landed in
   source: the protocol 13 → 14 contract (§8) — authored-index identity, the reliably broadcast
   `PacketBlockBreak`, the join baseline, the snapshot revision — the client's `MapDamageState`
   mirror feeding prediction, rendering, the minimap and the first `rebakeSdf` caller, and the
   map-hash check that refuses a foreign index set. The door half has now followed: the door
   state machine and its collision/bullet/sight wiring (in `MapDoorState`/`MapDamageState`),
   the use key and its client prediction, the server `DoorSystem` authority with body-and-utility
   occupancy and a refused-blocked correction unicast, the protocol-15 `PacketDoorState` wire
   (join baseline included), the `DoorOccluders` array in the visibility and light shaders, and
   `TerrainRenderer` posing doors from the static map while the SDF names the destruction-only
   static map. The close-out has since closed the handshake's server half: the client states its
   map in the join request and the server refuses a mismatch (protocol 16, §8) — and then the crack
   half: a chip carries the authority's running total, live and on the join baseline (protocol 17),
   and the round's reset now travels as its own event with `/mapreset` as the hand that runs it
   (protocol 18). The close-out's last piece has since landed too: the shipped arena authors the
   fixture door pair, so the acceptance document's door boxes have a doorway to be walked through
   (no protocol change — doors were already on the wire). Gate: the destruction tests below plus the
   door tests, the join-gate, chip and reset cases, and a manual two-client pass (the acceptance
   document).
5. **Editor and CLI.** `MapDraft`, `MapEditorScreen` behind the debug switch, `MapTool check|bake`
   in CI, reachability in the gate. Gate: a map authored in-game saves, validates, bakes and loads
   in a fresh session. **Slice A has landed** (branch `arena/2acaeacc-skystrike`, PR #63):
   `MapReachability`, the §7 gate itself, and `MapDraft`, the editor's data model, both in `shared`
   main source, with the M8 surface inventory rewritten to ask the shared gate and the draft's own
   tests beside it. `MapEditorScreen` (slice C) and `MapTool` (slice B — which also has to decide
   where that task lives, because `tools` is not a Gradle module) are untouched. Written and not
   executed: this environment has no JDK. §11.3 and §11.4 below now carry the readings slice A had
   to take to run at all, for confirmation rather than as settled answers. **Slice B has landed
   too** (branch `arena/78110530-skystrike`, PR #64): `tools` is a Gradle module that reaches
   `shared` and nothing else, `MapAudit` is the check/bake core the editor and the CLI both read
   (§6), `MapTool check|bake` exists with CI running `tools:mapCheck` and `tools:mapBakeCheck`, and
   §11.4 is decided — a map whose spawns cannot reach each other fails CI, a map that is merely not
   fully reachable warns with the sealed pocket named, and §7's strict reading stays available as
   `check --strict`. `MapAuditTest` was written and not executed, like everything else here.
   **Slice C has landed too**, which finishes the milestone's source: `MapPalette` (the presets and
   tap geometry, in the map package so the §9 kind gate needs no exemption) and
   `MapEditorScreen` behind the debug switch, entered from a main-menu action that a release build
   does not have, over a `MapDraft`, with the shared gate's pocket marked on the canvas, saving and
   baking into the user's own directory through the canonical writer and the one bake. Everything
   here is written and not executed — this environment has no JDK — and no acceptance box is
   ticked.
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
2. **Round reset — decided: destruction resets per round.** The map revision returns to zero and
   the authored blocks come back, because a map that has been chewed through stops being the map
   both teams agreed to fight on. The state exposes `MapDamageState.reset()`, the server hands it
   out through `GameServer.damageState()`, and the match layer that decides a round has ended is
   what will call it — no round state machine exists yet, so today the operator's `/mapreset` is the
   hand that runs the server's reset transaction and every client hears about it through
   `PacketRoundReset` (protocol 18, §8).
3. **Door access:** manual only, or also "open for the owning team" doors at spawn?

   *Slice A's reading (PR #63), for confirmation.* The reachability gate reads a door as an
   **opening** — out of the solids and out of the supports at once — because the map is searched
   in the state a player can always put it in, and the only access authored today is `ALL`. A
   `TEAM` door would make that a lower bound on what a *given* player can reach: the gate would
   be answering for a player who may open everything. Finer readings (a closure per team, an
   `allows(teamIndex)` gate, a report that says whose route it is) wait on this decision, and
   none of them is in slice A. Worth noting while deciding: no `TEAM` door is authored anywhere
   in the project, so the acceptance document's team-refusal box still has nothing to walk up
   to.
4. **Reachability strictness:** does a map that fails reachability ship with a warning or fail the
   build? (Recommendation: fail — the M8 gate already treats traversal as a hard requirement.)

   *Slice A's evidence (PR #63), for confirmation.* Both readings are already available to the
   caller, so the decision is the CLI's rather than the gate's: `Report.spawnsConnected()`
   (every spawn reaches every other spawn) and `Report.fullyReachable()` (every stance reachable
   from every spawn, §7's strict reading), each with the complete list of stances that failed.
   On the shipped arena the two disagree by exactly one pocket. At the gate's 5-unit pitch the
   map has 1526 stances on 600 columns and both spawns reach each other under either door
   reading, while 10 stances are reachable from no spawn — all of them inside the milestone-4
   fixture door's alcove, floor included, whose panel is flush against the tall wall that
   carries it. That is a fixture artifact rather than a map defect, which is why the
   recommendation above is worth softening to: **fail** a map whose spawns cannot reach each
   other, and **warn** with the pocket named on one that is merely not fully reachable — unless
   the map should be able to declare a pocket intended. Awaiting the decision before slice B
   wires the CI step.

   *Slice B's answer (PR #64), decided 2026-10-12.* **Decided: fail on disconnected spawns, warn
   with the pocket named.** A map whose spawns cannot reach each other fails the build; a map that
   is merely not fully reachable passes with a warning that lists every stance no spawn can reach,
   which is what the shipped arena does — so CI is green on the map this build ships while the
   alcove is still named in the log. The strict reading is not discarded: it is the other value of
   `MapAudit.Reading` (`FULL`) and `MapTool check --strict` asks for it, so §7's letter can be
   applied to any map on demand. The policy lives in the shared core rather than in the CLI,
   because the editor's report has to answer the same question about the same map, and a declared
   intentional pocket (the third option on the table) is *not* implemented: the format has no way
   to say it, the editor cannot author it, and today's only pocket is a fixture artifact that a
   warning names precisely enough to act on.
