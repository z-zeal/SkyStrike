# Playable Build Plan — debug toolkit, chat/console, menus, HUD, lighting, FX, map

> Scope: the eight things asked for in this session. It completes most of roadmap **Phase 7**
> (HUD, chat, console), takes a vertical slice of **Phase 8** (lights + particles), and pulls the
> menu/debug parts of **Phase 11** forward because nothing else can be tested without them.
>
> Companion docs: `CONSOLE_CHAT_PLAN.md` (§3, §4, §7 are binding here), `EFFECTS_PLAN.md`
> (§5, §6.1, §7, §9), `IMPLEMENTATION_ROADMAP.md` (phase gates), `PHASE7_AUDIT.md` (status).

---

## 0. WHERE THE CODE ACTUALLY IS

Facts established by reading the tree, not assumptions:

| Area | Reality today |
| --- | --- |
| Debug switch | `shared/config/DebugFlags` is `public static final boolean ENABLED = false` — a compile-time constant with no other flags and **no reader anywhere** |
| Chat dialog | **Does not exist.** `ChatClient`, `MessageBuffer`, `FontManager`, `TextWrapper`, `MessageFormatter` exist; nothing opens, focuses or draws an input bar. `GameScreen` echoes the last 4 lines into the debug text and says so in a comment |
| Console | Only the *capability* half exists: `shared/command/{Permission, ConsoleAccess}`, `server/command/{PermissionResolver, CapabilityBroadcaster}`, `core/command/ClientCapabilities`. There is **no** `CommandSpec`, registry, parser, cvar, request/response packet or handler |
| Debug info | Hardcoded `List<String>` in `GameScreen.statusLines()`, drawn by `render/StatusOverlay` with a stock `BitmapFont`, always on, not toggleable. `F1` toggles the SDF view only |
| HUD | None. `core/ui/` contains `text/` and nothing else |
| Menus | None. `Main.create()` constructs `GameScreen` directly from system properties |
| Lighting | `CompositePass` already has `u_lightTexture` + `u_hasLight` uniforms and is called with `null`. There is **no** light pass, no `Light`, no pool |
| Particles / FX events | None. No `fx/particle/`, no `shared/effect/`, no `PacketEffectSpawn`. Grenades/smoke/molotov are only `ThrownUtility` dots and `UtilityZone` circles, so a detonation is literally invisible |
| Loadout editing | `LoadoutController` F2/F3/F4 "stopgap until the Phase 7 menu owns it" |
| Map | `shared/map/ArenaMap.standard()`, mirror-symmetric about x=1500, enforced by a builder that throws on asymmetry; `ArenaMapTest` asserts it |
| Build | **No JDK in this sandbox.** `tools/scratch/static_check.py` + `static_api_check.py` are the local approximation; GitHub Actions CI (`shared:test`, `server:test`, `*:compileJava`, `checkModuleDependencies`) is the only authority |

### Decisions locked this session

1. Player light **is SDF-shadowed**.
2. Debug access = **client dev flag + server `--dev` mode**, both default off, server re-authorises everything.
3. The arena is **reworked in place**; mirror symmetry and its test stay.
4. Main menu is the **full shell**: title → play/settings/loadout/quit, settings with video + rebindable keys persisted, loadout editor screen.

### Three rules that constrain every item below

- **`core` never decides anything authoritative.** Infinite ammo, noclip, godmode and respawn are *server* commands; freecam, shadows, overlays and the player light are *client* commands. A client flag unlocks UI, never state.
- **`NetworkRegistration` is append-only**, and every change bumps `NetConfig.PROTOCOL_VERSION`.
- **Logic goes in `shared` so CI can test it.** `core` has no test task; a parser or a cvar range check that lives in `core` is a thing CI cannot prove.

---

## 1. MILESTONE ORDER

```
M1  Debug backbone + command core          ← everything else registers into this
M2  Chat/console dialog (fixes "can't open chat")
M3  Debug toolkit: keys, cvars, server debug commands
M4  HUD: status/debug panel, health-fuel-ammo, crosshair, loadout bar
M5  Main menu, settings, loadout screen, pause
M6  Light pass + circular player light
M7  FX: event transport, particles, grenade/smoke/molotov/flash/muzzle/impact presets
M8  Arena rework
```

M1 first is not negotiable: "all debug should also be registered as console commands" means the
registry must exist before the first toggle is written, or every toggle gets written twice.
M8 is last because it is the only item whose only reviewer is a human walking the map — and
walking it needs M2 (console), M3 (freecam, noclip) and M5 (menu) to be comfortable.

---

## 2. M1 — DEBUG BACKBONE AND COMMAND CORE

### 2.1 Turn `DebugFlags` into a runtime switch

`shared/config/DebugFlags` becomes a holder with a master switch plus per-feature state, and a
`DebugFlags.ENABLED` *default* that stays `false` for shipping builds.

- Master switch sources, in order: `-Dskystrike.debug=true` system property, `SKYSTRIKE_DEBUG=1`
  env var, `--dev` launcher argument. Resolved once, in `shared`, so both processes read the same
  rule.
- Per-feature state lives in a new `shared/debug/DebugState` (plain mutable booleans/floats behind
  getters): `overlay`, `hitboxes`, `sdfView`, `freecam`, `shadows`, `playerLight`,
  `playerLightShadows`, `infiniteAmmo`, `noclip`, `godmode`, `fxDebug`, `timescale`.
- **When the master switch is false every read returns the safe default**, so a stray `/noclip 1`
  in a release build is inert at the read site, not just at the parse site.

### 2.2 Shared command framework (`shared/command/`)

Per console plan §7.3 and §10, the minimum that makes "add a command = one file + one line" true:

```
shared/command/
├── ArgType            parse + validate + complete, one instance per type
├── ArgTypes           INT(range), FLOAT(range), BOOL(on/off/1/0/yes/no), STRING,
│                      GREEDY_STRING, ENUM, PLAYER, TEAM, DURATION
├── CommandArg         name, type, required, default, completion source
├── CommandSpec        name, aliases, description, args, permission, side, handler
├── CommandParser      quote-aware tokeniser + spec-driven coercion
├── CommandRegistry    lookup, permission-filtered listing, edit-distance "did you mean"
├── CommandContext     caller id, name, level, side, output sink
├── CommandResult      ok/error + output lines + severity
├── Cvar               typed, ranged, described, with a change hook
└── CvarRegistry       shares lookup with commands: `/r_shadows` with no value prints
                       current, default and description
```

`side` is `CLIENT`, `SERVER` or `BOTH`. A `CLIENT` command never leaves the process; a `SERVER`
command is sent as a **raw line** and re-parsed and re-authorised server-side (§7.5) — the client's
parse is a convenience, never a boundary.

### 2.3 Transport

Append to `NetworkRegistration` (and bump `PROTOCOL_VERSION`):

- `c2s/PacketCommandRequest` — raw line, nothing else.
- `s2c/PacketCommandResponse` — output lines + severity.

New `server/net/handlers/CommandRequestHandler` → `server/command/ServerCommandService`
(parse → `PermissionResolver.resolve` → authorise → execute → respond). Output also mirrors into
`ChatChannel.CONSOLE` so there is one sink, per console plan §7.1.

### 2.4 The "debug variable" that grants commands

Two independent switches, which is why this is not a single flag:

| Switch | Set by | Grants |
| --- | --- | --- |
| Client dev flag | `-Dskystrike.debug=true` on the client | The console UI exists locally: `/` parses, completion and hint line appear, client commands run |
| Server dev mode | `server:run --dev` | `PermissionResolver` resolves every joined player to `ADMIN`; `CapabilityBroadcaster` pushes `consoleAccess=true` as it already does |

A client with the dev flag and a non-dev server sees a working console that refuses every server
command — which is exactly the intended security property, and worth one system line explaining it
rather than a silent refusal.

Also add `--grant <name>=<level>` to `ServerConfig` so a named player can be promoted without dev
mode (the resolver already supports name grants; nothing parses them today).

### 2.5 Starter commands

Client: `help`, `clear`, `bind`, `unbind`, `fps`, `quality`, `mute`/`unmute`, `disconnect`.
Server: `respawn`, `kill`, `teleport`, `give`, `setammo`, `sethealth`, `setteam`, `kick`, `say`,
`players`, `noclip`, `godmode`, `infiniteammo`.

### 2.6 Tests (shared, so CI runs them)

`CommandParserTest` (quotes, escapes, missing required arg, range violation, enum coercion),
`CommandRegistryTest` (permission filtering hides rather than refuses; suggestion by edit distance),
`CvarRegistryTest` (range clamp, bool spellings, change hook fires once), `DebugStateTest`
(master off ⇒ every read is the default).

**Gate:** `/help` output is generated entirely from specs; an unprivileged client cannot execute a
server command even with a hand-forged `PacketCapabilities`.

---

## 3. M2 — THE CHAT/CONSOLE DIALOG

This is the "player couldn't open the dialog to chat" item. There is nothing to fix; there is a
dialog to build. Console plan §3 and §6 are the spec.

```
core/ui/console/
├── ConsoleDialog           panel, scrollback view, passive fade, animation
├── ConsoleInputField       caret, editing, history ring, live command detection
├── ChatTargetButton        ALL ↔ TEAM, click/tap + Tab accelerator, persisted
├── ConsoleCompletionPopup  candidates, click/tap select
├── ConsoleHintLine         live usage from the spec being typed
├── ConsoleTheme            colours, metrics, accent stripes, contrast clamp
└── ConsoleFocus            focus ownership + the single gameplay gate
```

Wiring in `GameScreen`:

1. `Enter` (rebindable `openChat` action in `KeyBindings`) pushes `ConsoleDialog` onto the existing
   `InputRouter` focus stack. `InputRouter.isGameplayActive()` already gates `InputSampler` and
   `LoadoutController`, so capture works the moment focus is pushed — **the opening keystroke must
   be consumed** and must not reach the field.
2. On open, **zero the movement intent** for one packet (console plan §6.4) so a held `D` does not
   strand the player running.
3. `Enter` submits and closes; `Enter` on empty closes; `Escape` closes and keeps the draft.
4. Scroll wheel drives scrollback while open — `LoadoutController.scrolled` already no-ops when a
   modal has focus, so this is a dialog-side addition only.
5. Replace `GameScreen.appendRecentChat()` and `RECENT_CHAT_LINES` with the dialog's passive view.

**Known collision to fix in the same change:** `KeyBindings.viewExit` is `ESCAPE`, which M5 also
wants for the pause menu. Resolution: Escape is consumed by the top of the focus stack — dialog
first, then surveillance view, then pause menu. One owner per press, decided by the stack, not by
three systems each polling Escape.

**Gate:** one key opens it; no keystroke leaks to gameplay; closing never strands the player
moving; the ALL/TEAM choice survives a restart; with the dev flag, typing `/` restyles the bar live
and `//x` sends literal `/x`.

---

## 4. M3 — DEBUG TOOLKIT: KEYS THAT ARE COMMANDS

Every toggle is **one cvar**, with the key bound to it through the same `bind` path a player would
use. No key handler anywhere flips a boolean directly — that is how a key and a command drift apart.

| Key | Cvar / command | Side | Effect |
| --- | --- | --- | --- |
| `F1` | `cl_debug_overlay` | client | The status readout (now toggleable, off by default) |
| `F2` | `cl_freecam` | client | Detach camera; WASD/arrows pan, `-`/`=` zoom; input packets keep sending zeroed intent |
| `F3` | `r_shadows` | client | SDF soft shadows in the visibility + light passes (falls back to hard edges, as the low tier does) |
| `F4` | `sv_infinite_ammo` | **server** | `FireController`/`LoadoutSystem` skip mag/reserve decrement |
| `F5` | `respawn` (command) | **server** | Force-respawn the caller via `RespawnService`/`SpawnService` |
| `F6` | `r_show_sdf` | client | The existing SDF debug view, moved off F1 |
| `F7` | `sv_noclip` | **server** | `PlayerMotion` skips swept-AABB and gravity for that player |
| `F8` | `sv_godmode` | **server** | `DamageService` ignores damage to that player |
| `F9` | `r_show_hitboxes` | client | Body/head/fuel-tank zones from `HitZoneMath` |
| `F10` | `r_player_light` | client | The M6 light (and `r_player_light_shadows`) |
| `F11` | `fx_debug` | client | Pass timings, particle/light counts, draw calls |
| `F12` | `ui_contrast_test` | client | The four-background text test the console plan §5.2 requires and `PHASE7_AUDIT` flags as missing |

Plus `timescale <0.1–4>` (server), `dumpstate` (server), `spawnfx <preset>` (client, lands in M7).

Mechanics:
- Keys are only live when the master debug switch is on; otherwise F-keys are inert and the
  commands are not even registered (invisible, not refused — console plan §4.2).
- Server-side toggles are **per-player session state** on `PlayerSession`, cleared on disconnect,
  and are echoed back so the HUD can show a "CHEATS" tag. A match with any server debug toggle on
  sets a flag in the snapshot so no one is confused about why they cannot die.
- `LoadoutController`'s F2/F3/F4 composition stopgap is **deleted** here; M5's loadout screen
  replaces it. Do not leave two owners of F2.

**Gate:** every row above works identically from the key and from the console; with the master
switch off, `/noclip` is an unknown command and F7 does nothing.

---

## 5. M4 — HUD

```
core/ui/hud/
├── HudStage            one viewport, one batch, drawn last (after composite, after post)
├── HealthFuelBars      health 150 max, fuel 100 with the recharge-grounded rule
├── LoadoutBar          5 slots + Q/E gadgets, per-slot ammo / utility counts, reload sweep,
│                       active-slot highlight, quick-swap origin marker
├── Crosshair           spread-reactive: gap = f(Player.spread, gunKick), ADS form, hit marker
├── KillFeed            moves off the debug text onto a real widget
├── DamageVignette      direction-tinted, driven by PacketDamageEvent
└── DebugPanel          what statusLines() prints today, behind cl_debug_overlay
```

The loadout HUD asked for has two halves; both go here:

- **In-match bar** (always visible): the five slots with ammo, the two gadget slots with charge.
- **Loadout picker** (`ui_loadout`, default `L`, and the menu screen in M5): a grid of weapons from
  `WeaponRegistry`/`MeleeRegistry`/`UtilityRegistry` using the existing `assets/sprites/guns.json`
  atlas, writing a `PacketLoadoutUpdate`. The server already applies composition **at next
  respawn** (`LoadoutSystem`), so the picker must say so on screen — otherwise it reads as broken.

`StatusOverlay` stays as the debug panel's renderer initially, then is absorbed into `DebugPanel`
and deleted; `FontManager` (already built, with baked outline + shadow) replaces its stock
`BitmapFont`.

**Gate:** ammo, reload, quick-swap and utility counts on the HUD never disagree with the predicted
`PlayerLoadout`; text is legible on all four `ui_contrast_test` backgrounds.

---

## 6. M5 — MAIN MENU, SETTINGS, LOADOUT, PAUSE

`Main` stops constructing `GameScreen` and becomes a screen router (the project already depends on
`libgdx-screenmanager`; use it rather than hand-rolling transitions).

```
core/screens/
├── MainMenuScreen      title, Play, Loadout, Settings, Quit; name + host/tcp/udp fields
│                       pre-filled from the existing system properties
├── SettingsScreen      video (resolution, vsync, fullscreen, quality tier), audio stubs,
│                       controls (rebind every KeyBindings action, including openChat)
├── LoadoutScreen       the M4 picker, full-screen
├── ConnectingScreen    connection state from ClientSession + JoinReject reason + retry
└── GameScreen          unchanged role, plus a PauseOverlay (Resume / Loadout / Settings /
                        Disconnect) that pushes onto the InputRouter focus stack
```

Supporting work:

- `KeyBindings` becomes serialisable (action name → keycode) and persists via libGDX `Preferences`,
  which is also what `bind`/`unbind` write — one store, two front-ends.
- `core/settings/Settings` holds video/quality/chat-target preferences; quality tier feeds
  `FxBudget` in M7.
- Disconnect must tear down `ClientSession` and `FxPipeline` cleanly and return to the menu without
  leaking GL resources (`GameScreen.dispose()` is already correct; the router must actually call it).

**Gate:** launch → menu → connect → play → Escape → disconnect → menu → connect again, twice, with
no GL leak and no stale session; a rebound key survives a restart.

---

## 7. M6 — LIGHT PASS AND THE CIRCULAR PLAYER LIGHT

The composite already expects a light texture; this fills the hole it was built for
(`EFFECTS_PLAN` §5 pass 4, §6.1).

```
core/fx/lighting/
├── Light               data: x, y, radius, colour, intensity, falloff, castsShadow, curve
├── LightPool           integer handles, never object references; retires under budget
└── LightPass           half-res RGB additive target, one quad per light

assets/shaders/light/
├── light_radial.frag   radial attenuation × SDF soft shadow (shared lib/sdf.glsl) × colour curve
└── (reuses light/visibility.vert)
```

`FxPipeline.composite()` passes `lightPass.texture()` and `u_hasLight = 1`.

**The player light**, per the decision above: radius ≈ 140 u, warm white, intensity ~0.35,
`castsShadow = true`, attached to `Player.centerX/centerY`, drawn for the local player and for
visible remote players. It is shadowed, so standing behind a crate does not light the far side of
it; it is additive, so it brightens your own body independently of the cone — which is the whole
point of the request.

Guards that matter:
- Keep intensity low enough that it cannot reveal enemies *outside* the vision cone. Light adds
  after visibility multiplies; a bright self-light is effectively a wallhack-lite in a fog game.
  Tune against `VisionConfig.AMBIENT_FLOOR = 0.04` and verify an enemy at 200 u stays black.
- Cvars: `r_player_light` (on/off), `r_player_light_radius`, `r_player_light_intensity`,
  `r_player_light_shadows` — live-tunable, which is the fastest way to find the value above.

**Gate:** your own silhouette is readable in a dark corner; an enemy standing in your light but
outside your cone is still pure black; disabling `r_shadows` degrades to hard edges, not to nothing.

---

## 8. M7 — EFFECTS FOR THROWABLES AND WEAPONS

Today a frag detonating is *silent and invisible* — the zone vanishes from the snapshot and that is
all. Three pieces are missing, in this order.

### 8.1 The event channel (the actual blocker)

```
shared/effect/EffectType     FRAG_EXPLOSION, IMPACT_EXPLOSION, SMOKE_BURST, MOLOTOV_SPLASH,
                             FIRE_ZONE, POISON_BURST, FLASH_DETONATION, CLAYMORE_BLAST,
                             BULLET_IMPACT_{CONCRETE,METAL,WOOD}, MUZZLE_FLASH, SHELL_EJECT
shared/effect/EffectSpawn    type, x, y, angle, scale, seed  (small, unreliable-safe)
shared/net/s2c/PacketEffectSpawn
```

`UtilitySystem.detonate()`, `BulletSystem`/`RaycastBulletSystem` impacts and `FireController`
muzzle events emit `EffectSpawn`s; the server **culls them with `VisionMath`** exactly as it culls
entities, then batches them into the snapshot broadcast. Appending to `NetworkRegistration`, bump
protocol version. Client drains on the render thread into an `FxEventQueue` — never spawns from the
network thread.

### 8.2 Particles — tier 1 only for now

`EFFECTS_PLAN` §7.1 GPU stateless particles: one persistent vertex buffer, descriptors written once,
one time uniform per frame, analytic motion in the vertex shader. Tier 2 (CPU, colliding) is
deferred except for shell casings, which are the cheapest possible proof the tier works.

```
core/fx/particle/{GpuParticleSystem, ParticleBuffer, EmitterConfig, EmitterLibrary, FxClock}
core/fx/FxBudget            quality tiers from Settings; no subsystem hardcodes a cap
assets/shaders/particle/{particle.vert, particle.frag}
```

Alpha particles (smoke, dust) draw **inside** the scene pass so fog darkens them; additive ones
(sparks, fire, flash) draw **after** composite so they glow through darkness. Getting this backwards
is the single most common bug in this stack, which is why pass order stays owned by `FxPipeline`.

### 8.3 The presets asked for

Data, not code (`EmitterLibrary`), following the §9 catalogue:

- **Frag / impact explosion** — phased at 0.00/0.03/0.05/0.08/0.10 s: white-yellow flash + light
  (high priority, never culled), fireball puffs, debris + embers, long-lived smoke.
- **Smoke grenade** — billowing alpha cloud that *grows into* the existing `UtilityZone` radius, so
  the visual and the shader circle are the same thing (they already share `SmokeVolume`; keep it).
- **Molotov** — glass sparks, fire splash along the surface tangent, one flickering attached light
  per fire zone.
- **Flash/stun** — white burst, residual wisps, plus the full-screen blindness post pass scaled by
  distance and line of sight (reuse `StunMath`, which already computes the bands).
- **Muzzle flash** — core disc + gas burst + attached 1-frame light; shell casing on the CPU tier.
- **Bullet impacts** — per-surface dust/sparks/splinters. Decals deferred.

**Universal rule from §9: occlusion-test every spawn position against the SDF.** No particle on the
far side of a wall from its source.

**Gate:** a frag you throw round a corner is seen as light on the wall but not as particles through
it; smoke particles and the vision-blocking circle grow and die together; `fx_debug` shows the
particle and light counts staying inside the tier budget.

---

## 9. M8 — ARENA REWORK

Reworked in place in `ArenaMap.standard()`, keeping the mirrored/centred builder and
`ArenaMapTest`'s symmetry assertion.

### 9.1 What is actually wrong today

Movement envelope, from `PlayerConfig`: a standing jump apexes at **110 u**
(420² / 2·800) and clears roughly 210 u of gap at walk speed. Jetpack climbs at only +50 u/s²
net (850 thrust vs 800 gravity), so it is a *sustainer*, not a launcher — jump first, then burn.

Against that envelope:

| Area | Problem |
| --- | --- |
| Centre room | Floor at y=280–300 with 560-tall shell walls standing on it and **no doorway**. The interior (catwalk y=620, perch y=760) is a sealed box open only above y=860 — jetpack-only in, jetpack-only out. |
| Sniper perch | y=760, 120 u above the catwalk deck at 640 — just past the 110 u jump. Unreachable on foot by 10 units, which reads as a bug rather than a choice. |
| Ground tunnel | Walkable (90 u clear under the roof at y=190) but **has no connection upward** — the room floor above it is solid for its full 760 u width. It is a corridor to nowhere. |
| Mid lane → room | 50 u horizontal gap at a 200 u height difference; fine, but it is the *only* route toward the centre, so losing it loses the whole half. |
| Above y=860 | 1100 u of empty arena with nothing in it. |

### 9.2 Target layout rules

1. **Every standable surface is reachable on foot** (chained ≤110 u steps) **or with ≤40% fuel**,
   from both spawns.
2. **No area is a trap**: wherever you can get into, you can get out of without fuel.
3. At least **two routes into the centre** per side, at different heights (ground tunnel, lane).
4. Mirror symmetry preserved; every rect still added through `mirrored()` or `centred()`.

### 9.3 Concrete changes

- **Doorways:** split each shell wall into a lower jamb (deck → +60) and an upper section
  (deck+110 → deck+560), leaving a 50 u doorway a standing player walks through. Two rects instead
  of one, still mirrored.
- **Tunnel → room hatch:** shorten the room floor to two segments with a 90 u gap at x≈1240 / 1760,
  and add a 110 u step platform under each gap so the tunnel connects upward.
- **Perch ladder:** insert a 100 × 16 step at (1300, 700) mirrored, turning the 120 u reach into
  two 60 u hops.
- **Catwalk access:** add a mirrored 120 × 18 shelf at (1100, 520) bridging the mid lane to the
  doorway height. Add a mirrored **provisional** 120 × 18 doorway landing at (1000, 342), whose
  top meets the opening at y=360 so the lane can actually cross it, plus a mirrored
  **provisional** 100 × 18 interior riser at (1220, 400): the specified shelf otherwise sits
  220 u above the room floor, while the riser keeps each footstep in the room-floor → riser →
  shelf → catwalk chain at or below 110 u.
- **Upper arena:** one centred high platform at y≈1000 over the perch and two mirrored outer
  ledges at y≈900, reachable by jetpack only, giving the vertical half of the map a purpose.
- Re-bake the SDF (`tools/SdfBakeTool` → `assets/data/arena.sdf`) **in the same commit** — a stale
  distance field means shadows and particle collision silently disagree with collision.

### 9.4 Optional but cheap

A `shared` reachability test: flood-fill standable surfaces using the jump envelope and assert
every surface is reachable from both spawns. It is ~80 lines, runs in CI, and turns "every area
should be accessible" into something that cannot regress. Recommended, not assumed.

**Gate:** walk (no jetpack) from either spawn to the room floor, the catwalk, the perch and through
the tunnel; `cl_freecam` reveals no sealed pockets; `ArenaMapTest` still passes.

---

## 10. PROTOCOL AND CONFIG CHANGES, COLLECTED

| Change | Where | Note |
| --- | --- | --- |
| `PacketCommandRequest`, `PacketCommandResponse` | M1 | append-only, bump `PROTOCOL_VERSION` |
| `PacketEffectSpawn` + `EffectSpawn`, `EffectType` | M7 | same |
| Debug flags in the snapshot (cheats-on indicator) | M3 | one int bitmask on `PacketGameState` |
| `--dev`, `--grant name=LEVEL` | M1 | `ServerConfig.fromArgs` + `usage()` |
| `-Dskystrike.debug`, `SKYSTRIKE_DEBUG` | M1 | read in `shared`, used by both sides |

---

## 11. VERIFICATION

Local (no JDK here): `python3 tools/scratch/static_check.py` and `static_api_check.py` after every
milestone — they catch unbalanced braces, bad imports, module-boundary violations and calls to
methods that do not exist, which is most of what javac would catch.

CI is the authority. New tests, all in `shared`/`server` so CI actually runs them:

| Milestone | Tests |
| --- | --- |
| M1 | `CommandParserTest`, `CommandRegistryTest`, `CvarRegistryTest`, `DebugStateTest`, `ServerCommandServiceTest` (unprivileged caller refused; forged capability grants nothing) |
| M3 | `DebugCommandTest` per server toggle; `RespawnCommandTest` |
| M7 | `EffectSpawnCullingTest` (no event for an occluded detonation), `NetworkRegistrationTest` extension |
| M8 | `ArenaMapTest` symmetry (existing) + optional `ArenaReachabilityTest` |

Human gates are the per-milestone "Gate" lines above; they are the acceptance test, not the unit
tests.

---

## 12. RISKS

| Risk | Mitigation |
| --- | --- |
| The player light becomes a wallhack | Low intensity, shadowed, explicit test: enemy outside the cone but inside the light stays black |
| Escape owned by three systems (dialog, surveillance view, pause) | Single focus stack; top of stack consumes. Fix in M2, before the pause menu exists |
| Two owners of F2–F4 (debug keys vs loadout stopgap) | Delete the stopgap in M3, in the same change that binds the keys |
| Snapshot bloat from effect events | Cull by `VisionMath`, cap per tick, send unreliable — a dropped spark is invisible, a dropped state packet is not |
| Map rework breaks the SDF | Re-bake in the same commit; `SdfBakerTest` guards the format, not the content |
| Scope: M7 is a Phase-8 slice | Decals, bloom, distortion and CPU particles are explicitly deferred; only the presets listed in §8.3 ship |
```
