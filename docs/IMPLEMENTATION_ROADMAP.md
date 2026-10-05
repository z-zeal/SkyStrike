# SkyStrike — Phase-by-Phase Implementation Roadmap

> The master build order. `MECHANICS_PLAN.md` says *what the game is*, `PROJECT_STRUCTURE.md` says *where code goes*,
> `EFFECTS_PLAN.md` and `CONSOLE_CHAT_PLAN.md` are deep dives into two subsystems.
> **This document says what to build, in what order, and how to know a phase is finished.**

---

## 0. WHERE THE PROJECT ACTUALLY IS TODAY

A bare gdx-liftoff skeleton. Three Java files exist in total:

| File | State |
| --- | --- |
| `core/.../Main.java` | `Game` subclass, sets `FirstScreen` |
| `core/.../FirstScreen.java` | Empty stub screen |
| `server/.../ServerLauncher.java` | Empty `main` |

`shared/` has **no sources at all**. Dependencies (Ashley, Box2D, gdx-ai, Kryo, KryoNet, screenmanager, FreeType)
are already declared in `core/build.gradle`; the server only depends on `shared`.

### The base package

**`io.github.skystrike` throughout, in every module.** This matches the existing sources, the Android manifest
and both launchers, and `PROJECT_STRUCTURE.md` now uses it as well — there is no second naming to reconcile.

```
shared/src/main/java/io/github/skystrike/shared/
server/src/main/java/io/github/skystrike/server/
core/src/main/java/io/github/skystrike/
lwjgl3/src/main/java/io/github/skystrike/lwjgl3/
android/src/main/java/io/github/skystrike/android/
```

### Two discrepancies to settle before Phase 1

1. **`shared` has no dependencies.** It needs Kryo for serialisation (`shared/build.gradle` is empty).
   Move `kryo`/`kryonet` from `core` to `shared` as `api` so `server` gets them transitively.
2. **Box2D vs. custom physics.** `core` pulls in Box2D, but `server` must be plain Java with no game-framework
   dependency, and it is the authority for movement, bullets and rotation springs.
   → **Decision: hand-rolled AABB physics in `shared/physics/`**, used verbatim by both sides.
   Drop Box2D/Bullet from the dependency list; the arena is axis-aligned rectangles and the
   jetpack/rotation feel in §2 of the mechanics plan is spring-driven, not solver-driven.

### Global rules that hold for every phase

- **Nothing gameplay-authoritative lives in `core`.** The client samples input, predicts, and draws.
- **One canonical implementation** of every maths routine, in `shared`. If both sides need it, it goes there.
- **Each phase ends runnable.** No phase may leave the game in a state you cannot launch and look at.
- **Tuning values go to `shared/config/`**, one file per system, from the first commit. Never a god constants class.

---

## PHASE 0 — Foundations (no gameplay yet)

**Goal:** the skeleton becomes a real four-module project with a tick loop, a connection, and a window that draws.

### 0.1 Build and module hygiene
- Create the `shared` source tree and give it Kryo. Remove Box2D/Bullet/Ashley if unused (keep gdx-ai only if you
  commit to using it for drone steering — otherwise drop it; dead dependencies rot).
- Add the dependency-direction build check: `server` fails to compile on a `com.badlogic.gdx` import,
  `core` fails on a `...server` import. A simple source-scanning Gradle task is enough.
- Create `tools/` (SDF baker, atlas packer) as a non-shipped module, and `assets/` subfolders per
  `PROJECT_STRUCTURE.md` §8.

### 0.2 `shared/config/` and `shared/math/`
Write the config classes as empty-but-named holders now, filled per phase as the systems land:
`NetConfig`, `WorldConfig`, `PlayerConfig`, `CombatConfig`, `WeaponConfig`, `UtilityConfig`, `GadgetConfig`,
`VisionConfig`, `DebugFlags`.
Write `math/Geometry` (segment↔AABB, the only copy), `math/Angles` (wrap, shortest delta),
`math/Lerp` (framerate-independent `lerp(a, b, 1 - exp(-rate*dt))` — use this everywhere, never raw `lerp(a,b,dt)`).

### 0.3 The arena as data
`shared/map/Rect`, `ArenaMap`, `MapQueries`. Hard-code the full 3000×2000 layout from mechanics §1:
both ramps, mid lanes, crate stacks, pillars, tunnel supports, centre room shell/floor/catwalk,
tunnel roofs, sniper perch, spawns at (240,180) and (2760,180). Verify mirror symmetry with a unit test —
reflecting every rect about x=1500 must reproduce the same set.

### 0.4 Tick loop and transport
- `server/sim/TickLoop` with a fixed-rate accumulator and drift correction, `SimulationClock`, `TickProfiler`.
- `shared/net/Packet` + `NetworkRegistration` (both sides call the same registration — a divergence here is a
  silent, catastrophic bug class). `c2s/PacketJoinRequest`, `s2c/PacketJoinAccept`, and a stub `PacketGameState`.
- `server/net/NetworkEndpoint`, `ConnectionRegistry`, `PacketRouter`, one handler per packet.
- `core/net/NetworkClient`, lifecycle, send/receive. **Inbound packets are queued on the network thread and
  drained on the render thread** — set this rule now, it is unfixable later.

### 0.5 Client shell
Replace `FirstScreen` with a thin composition root: `GameScreen` + `GameCamera` + `RenderLayers` (explicit
draw-order enum, even if most layers are empty). Draw the arena rects as flat colour.

**Done when:** `server:run` ticks at a stable rate with profiling output, `lwjgl3:run` connects, and the client
draws the real arena geometry with a camera you can pan. No players yet.

---

## PHASE 1 — The character: movement, rotation, aim

The single highest-risk phase for *feel*. Mechanics §2 is the spec and §11 is the acceptance test.

### 1.1 Authoritative movement (`shared/physics/` + `server/world/`)
- `shared/model/Player` as the full state record; `shared/physics/PlayerMotion` as a **pure function**
  `(state, input, dt, map) → state`. Purity is what makes client prediction and server simulation agree.
- Walk 200 u/s, cap 300, gravity −800, crouch halves speed and takes height 50 → 30.
- Ground damping 16/s, air damping 1.0 — this contrast *is* the "grippy ground, floaty air" feel.
- Swept AABB against `ArenaMap`; ground detection with a small skin so landings are stable.

### 1.2 Jump and jetpack
Jump 420. Jetpack 1000 thrust, 20 fuel/s burn from 100 (exactly 5 s), recharge 20/s **grounded only**.
Thrust split 0.85 vertical / 0.15 aim-modulated.

### 1.3 Body rotation — the signature
Implement exactly as specced, it is a stack of small torques and every one matters:
- Grounded: upright spring 120 deg/s², angular damping 20/s, snap to 0°.
- Airborne: spring 50 deg/s², damping 4.0.
- Coyote lock 0.10 s after leaving ground.
- Airborne torques: jetpack lean ×0.04, aim lean ×0.12 capped at 30° of influence, velocity bank ×0.08.
- Backpedal case: horizontal speed > 25 **and** moving opposite to facing → aim torque 0, velocity bank ×2.40.

### 1.4 Aim, ADS, camera
360° aim independent of body. ADS (hold RMB) does four things at once — tightens spread, cuts recoil,
extends vision reach, pans the camera 150 u toward aim. Transitions are **interpolated, never snapped**
(a snapping vision range strobes; see effects §6.2).

### 1.5 Input and prediction
`core/input/InputSampler`, `InputRouter` (focus stack — UI consumes before gameplay polls, needed in Phase 7),
`KeyBindings` (rebindable, persisted) per the control table in mechanics §9.
`PacketPlayerInput` (c2s), `core/net/LocalPrediction` + reconciliation, `StateBuffer` + `Interpolator` for
remote players. Prediction replays the *same* `PlayerMotion` function the server runs.

**Done when:** two clients see each other move smoothly; ground control is crisp, air is committed; the body
visibly banks and tumbles in flight while the gun tracks the cursor; the backpedal lean reads dramatically.

---

## PHASE 2 — Vision and fog of war

Deliberately before combat. It is the defining mechanic, it constrains the renderer, and bolting it on later
means rewriting the render pipeline. Follow `EFFECTS_PLAN.md` phases 0–4 here.

### 2.1 SDF foundation
`tools/SdfBakeTool` bakes the static arena to a distance field; `assets/data/arena.sdf`; `core/fx/sdf/` loader
and sampler. Everything downstream — shadows, occlusion, particle collision — reads this one texture.

### 2.2 Visibility pass
Half-res single-channel target. One quad per observer. Fragment shader = distance falloff × angular feather ×
SDF soft shadow, minus smoke circles sampled in the **same** pass.
- Reach 640 hip / 1024 ADS, interpolated over time.
- **Quadratic falloff, feathered cone edge, small peripheral floor.** Any flat brightness tier is a bug —
  visible banding is the single most common way 2D fog looks cheap.
- Multiple observers combine with **max blending** so cones union rather than double-brighten.

### 2.3 Composite
`scene × max(visibility, ambientFloor) + light`. Lights are a Phase 6 concern but wire the slot now.
Enemies outside the cone render **pure black**, not dimmed.

### 2.4 Gameplay-side visibility
`shared/vision/VisionMath` — the authoritative "can A see B" query, used for server-side culling, the stun
line-of-sight check (Phase 5) and the minimap. **Same maths as the shader**, or players will be shot by
things the renderer swore were invisible.
Server-side culling matters for anti-cheat: do not send entity state the client should not be able to see.

**Done when:** an enemy behind a crate is genuinely invisible, brightness is smooth everywhere, ADS visibly
extends sight, and the server never transmits hidden players.

---

## PHASE 3 — Combat core

### 3.1 Ballistics (`shared/combat/BallisticsMath`, `shared/weapons/WeaponBallistics`)
Travelling projectiles, never hitscan. Per-weapon muzzle speed 520–1950; per-class drop
(sniper 1.0 → shotgun 5.0) ramping in over 0.5–1.8 s; per-shot drag 0.980–0.998;
linear damage falloff to a per-weapon floor (sawed-off shotguns 35% → snipers 80%).
**Above ~100 u/s, sweep previous→current position each tick.** Fast bullets tunnelling through the 14-unit
tunnel roof is the bug you will otherwise spend a week finding.

### 3.2 Hit zones (`shared/combat/HitZoneMath`)
Head = top 28% of *current* height → 2.0×; body 1.0×; fuel tank is a separate zone resolved in Phase 6.
Fractional-of-current-height is what makes crouching genuinely harder to headshot.

### 3.3 Spread (`shared/combat/SpreadMath`)
A live per-player-per-weapon value pulled toward a stance target (the 2×2 table in mechanics §4.3).
Per-shot kick 2.1–4.8°, ceiling ~2.7–4× base, recovery 4–9.5 deg/s, **1.5× faster while aiming**, kick scaled
by the current recoil multiplier. Deviation sampled from a **normal** distribution, not uniform.

### 3.4 Recoil
All three channels: linear velocity push (so heavy weapons shove you, and firing down in air gains height),
angular spin on the body, and a visual gun-angle kick decaying at 120 deg/s capped at 35°.
Multipliers: ADS 0.45–0.78 (lerped, not snapped), moving 1.4–2.0×, airborne 0.25×, burst 1.20×.

### 3.5 Fire modes and friendly fire
Auto / semi / bolt / pump / break / 3-round burst (0.55° fixed spacing + 20%-of-spread jitter) / shotgun
(per-weapon pellet count up to 8, even across the cone, 1.5° hip / 0.8° ADS jitter, each a full damage instance).
Friendly fire **and self-damage on by default**; Neutral fights everyone.

**Done when:** the weapon table's TTK math holds in practice (sniper headshot = 1 shot, magnum = 2 body),
a full-arena shot visibly drops and deals clearly less damage, and spray-vs-tap accuracy differs over
roughly a second rather than instantly.

---

## PHASE 4 — Weapons, melee and loadout

### 4.1 Registry and data
`shared/weapons/WeaponRegistry` — id → definition, **returns copies** (a shared mutable weapon instance is a
cross-player state leak). All 89 guns generated from the sprite catalog (see §5.1 and `docs/WEAPONS_TABLE.md`),
plus per-weapon ADS spread ratio (snipers ~18%, SMGs ~42%).

### 4.2 Melee
Twenty-one weapons from §5.2. Arc damage in front of the player, knockback as a **real physics impulse** —
bats launch airborne enemies, shove them off ledges and into fire. Slot 3 can never be empty.

### 4.3 Slots and switching
Five main slots + Q/E gadgets. Keys 1–5 select filled slots only; **pressing 1 or 2 while already active
quick-swaps to melee and remembers the origin weapon**, pressing again returns. Wheel cycles filled slots only.
Mag/reserve ammo tracked separately, per-weapon reload times, and **switching resets that weapon's accumulated
spread and recoil state**.
`core/gameplay/LoadoutController` for input mapping, `shared/model/PlayerLoadout` for authoritative state.

**Done when:** every weapon is usable, the tap-swap round-trips correctly, and reload/ammo/cycling never
desync between client prediction and server state.

---

## PHASE 5 — Throwables

### 5.1 Throwable physics (`shared/utility/`)
Gravity −500, bounce restitution 0.35 vertical / 0.70 horizontal friction — they settle fast and bank shots
around corners work. `core/render/TrajectoryRenderer` draws a predictive arc using the **identical** integrator,
not an approximation.

### 5.2 Terrain-occluded explosions
Cast rays outward from the detonation; terrain blocks damage entirely regardless of proximity.
Linear falloff with distance inside line of sight. **One hit per target per explosion.**

### 5.3 The catalogue
Frag, impact, smoke, molotov, poison smoke, flashbang, claymore per the §6 table.

### 5.4 Stun grenade — the detailed one
Three distance bands (inner 30% → 7.0 s blind / 4.0 s slow; 30–70% → 4.5/2.5; 70–100% → 2.0/1.0).
**No line of sight to the detonation → 0.3 s concussion only.** Reuse `VisionMath` from Phase 2 —
this is exactly why it was built as shared gameplay maths.
Stunned: 30% move speed, cannot fire.

### 5.5 Molotov — surface spread
Breaks on contact, spreads **along the surface tangent** so it works on floors, ramps and walls.
One central zone at 21 dmg/tick plus outward-cast zones at 17, 2.0-unit offset with jitter, 6 s.
Self-damage applies — standing in your own fire kills you.

### 5.6 Smoke ↔ vision
Register smoke volumes as the circle array the Phase 2 visibility shader already samples, and in
`VisionMath` for gameplay. One source of truth; the visual and the query cannot drift.

**Done when:** a grenade behind a wall does zero damage, turning away from a stun meaningfully saves you,
smoke blocks sight for both the shader and the server, and your own molotov kills you.

---

## PHASE 6 — Gadgets

### 6.1 Drone (manual)
Deploys above you; press again to take POV. Speed 8, velocity lerp + damping, clamped to arena, pushed out of
walls, 30 HP. Projects **its own cone** (10 units, 70°) into the visibility pass — it is already a multi-observer
system from Phase 2, so this is additive.
**While piloting, your body is fully locked and vulnerable.** `core/gameplay/SurveillanceController` owns the
input lock; the server must enforce it, not just the client.

### 6.2 Throw camera (manual)
Arc-thrown at speed 12, sticks to first surface, permanent, 1.2× zoom, 20 HP, destroyed off-arena.

### 6.3 Shield (hybrid)
150 durability. Equipped = 90° frontal arc, **handgun only**; stowed = rear protection.
`shared/combat/ShieldArcMath` resolves absorption. Breaks permanently at 0.

### 6.4 Fuel tank (passive)
Jetpack capacity ×1.75, thrust ×1.40. Adds the rear fuel-tank hit zone: shot → 120 damage in a 4-unit radius,
killing the wearer. Mobility bought with a flanking weakness.

### 6.5 View cycling
Key `6` cycles self → drone → camera → self, skipping unavailable. `Escape` exits surveillance.

**Done when:** piloting a drone feels like a genuine gamble, the shield's front/back choice is legible,
and a flanker can detonate a fuel tank.

---

## PHASE 7 — HUD, chat and console

Follow `CONSOLE_CHAT_PLAN.md` build order (§11) in full. Key points not to compromise:

- **Two pieces of state only**: `chatTarget` (ALL/TEAM, client-persisted) and `consoleAccess` (server-pushed).
  **No mode enum** — a line is a command iff you have access *and* it starts with a single `/`.
- The dialog draws in the **HUD pass, last** — after fog, bloom, and the flashbang whiteout. Being blinded is a
  gameplay state; being unable to read "enemy pushing B" is a UI failure. Dim the panel, never the glyphs.
- Four contrast layers: backing panel, baked 1px outline, drop shadow, minimum luminance floor on every colour
  including team colours. Validate against black / white / grey / noise backgrounds.
- Permission absence makes the console **invisible**, not disabled — no greyed items, no probing for commands.
- Server is authoritative: chat relayed and validated server-side, privileged commands re-authorised at
  execution time regardless of the client's claimed flag.

HUD alongside it: health/fuel bars, loadout bar with ammo and utility counts, gadget panel with durability and
cooldowns, spread-reactive crosshair, minimap, kill feed, floating damage numbers, damage vignette.

**Done when:** one key opens chat, the ALL/TEAM button works on touch, a `/` line restyles live, and text is
readable on all four test backgrounds.

---

## PHASE 8 — Full effects stack

`EFFECTS_PLAN.md` phases 5–8. Phases 0–4 of that document were absorbed into Phase 2 here.

- **Lights**: pooled, referenced by **integer handle, never object reference** — handles make leaks impossible
  and let the budget system retire lights. One additive quad per light, SDF-shadowed, animated colour curve.
  Cost scales with lit **area**, not count. Explosion flashes are high-priority and never culled first.
- **Composite rule**: *visibility multiplies, light adds.* An explosion lights a wall outside your cone, but an
  explosion behind a wall stays invisible — correct behaviour with zero special cases.
- **Particles, two tiers**: GPU (non-colliding, vertex-animated) and CPU (collides against the SDF).
- **Secondary**: decals (bullet holes 10–15 s, grenade scorch 20–30 s sized to radius, rocket 30–40 s),
  shockwave distortion, heat haze, bloom, blindness/aberration/vignette/grain, directional distance-scaled
  camera shake.
- **Budgets and quality tiers**, with the distortion pass skipped entirely on the low tier.

**Done when:** the full effect catalogue (§9 of the effects plan) is implemented and the frame budget holds on
the low tier.

---

## PHASE 9 — Audio

`AudioSystem` (buses, pooling), `SoundCatalog` (event → sound), `SpatialAudio` (distance attenuation + panning),
`TinnitusEffect` for stun ringing. Audio is driven by the same effect-event queue as visuals, so a gameplay
system fires one event and both layers react.

---

## PHASE 10 — Mobile, platform services and hardening

- `core/platform/` interfaces (`AuthProvider`, `StorageProvider`, `KeyboardProvider`, `DeviceInfo`) with
  desktop and Android implementations. **`core` never branches on platform.**
- `TouchControls` virtual stick and buttons; `QuickChat` presets.
- Font regeneration on resolution/DPI change, size ≈2.2% of viewport height, floored at 12 physical pixels.
- Effects plan Phase 9 mobile hardening: precision rules, tile-GPU rules, quality-tier detection.
- Netcode hardening: packet caps, rate limiting (`shared/text/RateLimiter` token bucket), text sanitisation,
  timeout handling, `NetDiagnostics`.

---

## PHASE 11 — Match flow and polish

Spawning and respawn (full health and fuel, **every timer, status effect and gadget state cleared**, loadout
reset, placed upright at team spawn), team assignment and switching, round/score logic, scoreboard,
main menu and settings screens, debug overlay and hitbox renderer behind `DebugFlags`.

Then walk the **Feel Checklist** (mechanics §11) end to end. It is the real acceptance test for the whole
project, and any failing line points at a specific phase above.

---

## SUMMARY

| Phase | Deliverable | Depends on | Risk |
| --- | --- | --- | --- |
| 0 | Modules, config, arena data, tick loop, transport, client shell | — | Low |
| 1 | Movement, jetpack, rotation, aim, prediction | 0 | **High — feel** |
| 2 | SDF, vision cones, fog composite, shared vision maths | 0, 1 | **High — look + anti-cheat** |
| 3 | Ballistics, hit zones, spread, recoil, fire modes | 1 | Medium |
| 4 | Weapon registry, melee, loadout and slots | 3 | Low |
| 5 | Throwables, occluded explosions, stun, molotov, smoke | 2, 3 | Medium |
| 6 | Drone, camera, shield, fuel tank, view cycling | 2, 5 | Medium |
| 7 | HUD, chat and console dialog | 1, 6 | Medium |
| 8 | Lights, particles, decals, post chain, budgets | 2 | **High — performance** |
| 9 | Audio | 8 | Low |
| 10 | Mobile, platform services, net hardening | 7, 8 | Medium |
| 11 | Match flow, menus, feel-checklist pass | all | Low |

**Critical path: 0 → 1 → 2.** Those three decide whether the game feels right and whether the renderer can
support the rest. Everything after is additive. Do not start Phase 3 until the Phase 1 and 2 checklist lines pass.
