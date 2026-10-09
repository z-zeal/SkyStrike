# Project File Structure

> The full layout for a new build of the game: four modules, organised by system within each.
> Base package throughout: `io.github.skystrike`.

---

## 1. PRINCIPLES

**1. Organise by system, not by layer.** Everything to do with gadgets lives in a `gadget` package. Not an `entities` package plus a `systems` package plus a `data` package that each contain one third of the gadget code. When you fix a drone bug you should open one folder.

**2. The dependency graph is a line, not a web.** `shared` knows nothing. `server` and `core` both know `shared` and never each other. Launchers know `core`. Any arrow that violates this is a bug in the structure.

**3. The server must never depend on the game framework.** It is plain Java. If a class the server needs imports a rendering type, that class is in the wrong module or doing too much.

**4. No god files.** A single 400-line constants class holding network ports, map geometry, weapon tuning, gadget stats and debug flags is the most common structural failure in a project like this. Split constants by system and put each next to the system that owns it.

**5. One canonical implementation.** Line/AABB intersection, damage falloff, hit-zone resolution and visibility maths each exist exactly once, in `shared`, used by both sides. Duplicating them is how client and server quietly diverge.

**6. Platform specifics live behind interfaces.** `core` declares what it needs from a platform; each launcher implements it. `core` never imports anything desktop- or Android-specific.

---

## 2. MODULE GRAPH

```
        ┌──────────┐
        │  shared  │  Plain Java. No framework, no rendering, no platform.
        └────┬─────┘  Contract between client and server.
             │
     ┌───────┴────────┐
     │                │
┌────▼────┐     ┌─────▼─────┐
│ server  │     │   core    │  Game framework + rendering. All client logic.
└─────────┘     └─────┬─────┘
 Headless.            │
 Authoritative.  ┌────┴─────┬──────────┐
 Plain Java +    │          │          │
 admin SDK.  ┌───▼───┐ ┌────▼────┐ ┌───▼────┐
             │lwjgl3 │ │ android │ │ (future)│
             └───────┘ └─────────┘ └────────┘
              Launchers: platform entry points and platform service impls.
```

### Dependency rules

| Module | May depend on | Must never depend on |
| --- | --- | --- |
| `shared` | Serialisation library only | Game framework, rendering, server, core, platform code |
| `server` | `shared`, networking, auth SDK | Game framework, rendering, `core`, any launcher |
| `core` | `shared`, game framework, rendering | `server`, any launcher, platform-specific APIs |
| `lwjgl3` / `android` | `core`, `shared`, platform APIs | `server` |

Enforce the two that matter with a build check: `server` must fail to compile if it imports a rendering package, and `core` must fail if it imports `server`.

---

## 3. ROOT LAYOUT

```
.
├── shared/              Client–server contract
├── server/              Authoritative headless simulation
├── core/                All client logic and rendering
├── lwjgl3/              Desktop launcher
├── android/             Mobile launcher
├── assets/              Shared runtime assets (sprites, shaders, fonts, data)
├── tools/               Offline build tools (SDF baker, atlas packer, asset list)
├── docs/                Design and planning documents
├── gradle/              Wrapper
├── build.gradle         Root build
├── settings.gradle      Module inclusion
└── gradle.properties    Pinned dependency versions
```

---

## 4. `shared/` — THE CONTRACT

Plain Java, no game framework. Types used by both the server and the client live here; a type only the client needs may still live here when its value is that it can be unit-tested — `hud/` and `audio/` are pure presentation maths and data, and the client module has no test source set.

```
shared/src/main/java/io/github/skystrike/shared/
│
├── config/                      Tuning constants, split by system
│   ├── NetConfig                Ports, tick rate, timeouts, packet caps
│   ├── WorldConfig              Arena bounds, ground level, gravity, spawn points
│   ├── PlayerConfig             Speeds, jump, jetpack, fuel, damping, rotation springs
│   ├── CombatConfig             Hit-zone ratios, multipliers, falloff, friendly fire
│   ├── WeaponConfig             Burst length, pellet counts, recoil caps, spread thresholds
│   ├── UtilityConfig            Per-throwable fuses, radii, damage, cooldowns, bounce
│   ├── GadgetConfig             Drone, shield, fuel tank, camera parameters
│   ├── VisionConfig             Cone ranges, angles, falloff, smoke density
│   └── DebugFlags               Master debug switch and per-feature toggles
│
├── model/                       Authoritative game state types
│   ├── Player                   Full player state record, surveillance view included
│   ├── PlayerLoadout            Five main slots plus two gadget slots
│   ├── WeaponItem               Weapon id, current ammo, reserve ammo
│   ├── UtilitySlot              Type, count, max count
│   ├── GadgetSlot               Type, count, charge, active flag
│   ├── DroneEntity              Deployed surveillance drone: kinematics, health, cone aim
│   ├── CameraEntity             Thrown camera: flight state, stuck flag, contact normal
│   ├── Team                     A, B, Neutral, with cycling
│   ├── HitZone                  Head, body, fuel tank
│   └── ShieldState              Stowed, equipped, broken
│
├── map/                         Arena definition
│   ├── Rect                     Axis-aligned rectangle with overlap helpers
│   ├── ArenaMap                 Platform and wall arrays, spawn points, bounds
│   └── MapQueries               Ground height, platform-at-point, containment
│
├── weapons/
│   ├── Weapon                   Base type
│   ├── Gun, Melee, Utility      Weapon families
│   ├── WeaponSlotType           Primary, handgun, melee
│   ├── WeaponRegistry           Id → weapon definition, returns copies
│   ├── WeaponBallistics         Speed, drop, drag, falloff, gravity ramp
│   └── FireMode                 Auto, semi, burst, shotgun
│
├── utility/
│   ├── UtilityType              Frag, impact, smoke, stun, molotov, poison, flash, claymore, drone
│   └── UtilityDefinition        Per-type physical and gameplay parameters
│
├── gadget/
│   ├── GadgetId                 None, drone, shield, fuel tank, camera — frozen wire ordinal
│   ├── GadgetBehavior           Passive, manual, hybrid
│   ├── GadgetDefinition, GadgetRegistry   One immutable record per real gadget
│   ├── SurveillanceView         The view enum + the one view-cycle state machine (self/drone/camera)
│   ├── GadgetPress              The Q/E press state machine for the manual gadgets
│   ├── DroneMotion              Pure drone flight: lerp, damping, clamp, wall push-out
│   └── CameraFlight             The camera's flight/stick decision on the one throwable integrator
│
├── combat/                      Maths both sides must agree on
│   ├── BallisticsMath           Damage falloff, velocity falloff, drop integration
│   ├── HitZoneMath              Zone resolution from impact height and stance
│   ├── SpreadMath               Stance-adjusted target spread, kick, recovery
│   └── ShieldArcMath            Front/rear arc absorption test
│
├── physics/                     Pure simulation functions shared verbatim by both sides
│   ├── PlayerInput              One input sample, prediction and authority alike
│   └── PlayerMotion             Movement, collision, rotation — enforces the surveillance lock
│
├── hud/                         Pure HUD read models (client has no test source set)
│   ├── HudLoadoutView           Slot/ammo/utility/gadget views behind the loadout bar
│   ├── HudVitals                Health/fuel fractions and the recharge-grounded rule
│   ├── HudSurveillance          The surveillance banner's read model
│   ├── CrosshairMath, DamageVignetteMath   The other widget maths
│   └── KillFeedModel, LoadoutPickerModel   Feed and picker state
│
├── effect/
│   ├── EffectType               The spawnable visual effect vocabulary
│   └── EffectSpawn              Type plus position plus parameters
│
├── net/
│   ├── Packet                   Base type
│   ├── NetworkRegistration      Serialisation registration, called by both sides
│   ├── c2s/                     Client → server
│   │   ├── PacketJoinRequest
│   │   ├── PacketPlayerInput
│   │   ├── PacketLoadoutUpdate
│   │   ├── PacketTeamSwitch
│   │   ├── PacketGadgetAction
│   │   ├── PacketChatRequest
│   │   └── PacketCommandRequest
│   └── s2c/                     Server → client
│       ├── PacketJoinAccept
│       ├── PacketGameState      Players, bullets, grenades, effects, drones, cameras
│       ├── PacketPlayerDisconnect
│       ├── PacketEffectSpawn
│       ├── PacketShieldState
│       ├── PacketFuelTankDestroyed
│       ├── PacketChatMessage
│       └── PacketCommandResponse
│
├── command/                     Shared command framework (see console plan)
│   ├── CommandSpec, CommandArg, ArgType, ArgTypes
│   ├── CommandRegistry, CommandParser, CommandContext, CommandResult
│   ├── Permission
│   └── Cvar, CvarRegistry
│
├── text/
│   ├── ChatChannel, ChatMessage
│   ├── TextSanitizer            Strip control chars, markup, bidi overrides
│   ├── RateLimiter              Token bucket
│   └── TextLimits
│
├── audio/                       Phase 9 presentation maths and catalogue data (no libGDX)
│   ├── AudioBus                 Master/music/effects mixing — one formula, applied once
│   ├── SoundSpec                One playable sound: asset, bus and playback policy
│   ├── SoundPriority            What may take a voice when the pool is full
│   ├── EffectSoundTable         Effect type → sound; exhaustive by construction
│   ├── SpatialAudio             Distance attenuation, stereo panning, occlusion damp
│   └── TinnitusMath             The stun ring's attack and its tail
│
└── math/
    ├── Geometry                 Segment/AABB intersection — the only copy
    ├── Angles                   Wrapping, shortest delta, degree/radian helpers
    └── Lerp                     Framerate-independent interpolation helpers
```

---

## 5. `server/` — AUTHORITATIVE SIMULATION

Headless, plain Java. No rendering, no game framework, no physics engine for the world.

```
server/src/main/java/io/github/skystrike/server/
│
├── ServerLauncher               Entry point, argument parsing, stdin forwarding
├── GameServer                   Composition root and tick orchestration
├── ServerConfig                 Port, max players, tick rate, paths, flags
│
├── net/
│   ├── NetworkEndpoint          Transport setup, lifecycle
│   ├── ConnectionRegistry       Connection ↔ player id mapping
│   ├── PacketRouter             Inbound packet dispatch
│   ├── StateBroadcaster         Per-tick state packet, object-reused
│   └── handlers/                One handler per inbound packet type
│
├── sim/
│   ├── TickLoop                 Fixed-rate loop, accumulator, drift correction
│   ├── SimulationClock          Tick count, elapsed time, delta
│   └── TickProfiler             Per-phase timing
│
├── physics/
│   ├── MovementIntegrator       Gravity, velocity clamp, position integration
│   ├── GroundResolver           Ground and platform landing, coyote window
│   ├── WallResolver             Collision push-out
│   └── RotationDynamics         Upright spring, jetpack lean, aim lean, banking
│
├── player/
│   ├── PlayerSession            Connection, identity, authoritative Player
│   ├── PlayerRegistry           Lookup by id, name, team
│   ├── InputProcessor           Applies one input packet to one player
│   ├── SpawnService             Team spawn selection and placement
│   └── RespawnService           State reset and respawn rules
│
├── combat/
│   ├── BulletSystem             Stepped projectile lifecycle
│   ├── RaycastBulletSystem      Swept path for high-velocity rounds
│   ├── DamageService            Application, zones, shield, fuel tank, friendly fire
│   ├── MeleeSystem              Arc test, damage, knockback impulse
│   ├── KillFeedService          Kill and death events
│   └── LineOfSight              Ray-vs-terrain for occlusion checks
│
├── weapons/
│   ├── WeaponStats              Immutable per-weapon fire parameters
│   ├── GunInstance              Per-player live spread and recoil state
│   ├── FireController           Cooldown, fire mode, burst and pellet spawning
│   ├── RecoilService            Linear, angular and visual recoil application
│   └── AmmoService              Magazine, reserve, reload timing
│
├── utility/
│   ├── GrenadeSystem            Arc flight, bounce, fuse, detonation
│   ├── AreaEffectSystem         Lingering smoke, fire and poison zones
│   ├── ExplosionDamage          Ray-based, terrain-occluded, de-duplicated
│   └── UtilityThrowService      Consumption, cooldown, spawn
│
├── gadget/
│   ├── DroneSystem              Deploy/pilot/exit, motion, destruction, owner sweep
│   ├── CameraSystem             Throw, flight, stick, view, destruction, owner sweep
│   ├── ShieldSystem             State toggle, arc absorption, durability
│   ├── FuelTankSystem           Boost multipliers, rear hit, explosion
│   └── SurveillanceService      View transitions and the lock question
│
│   (The device entities themselves — `DroneEntity`, `CameraEntity` — live in
│   `shared/model` next to `ThrownUtility`: they are snapshot payload, and `core`
│   must never import `server`. Their systems live here.)
│
├── chat/
│   ├── ChatService              Validate, sanitise, rate-limit, scope, broadcast
│   ├── ChatModeration           Mutes, bans, masking
│   └── ChatHistory              Optional transcript
│
├── command/
│   ├── ServerCommandService     Parse, authorise, execute
│   ├── ServerConsole            Standard-input front-end
│   ├── PermissionResolver       Identity → permission level
│   └── commands/                One file per command, plus one registration module
│
├── auth/
│   ├── AuthService              Token verification
│   └── IdentityStore            Verified identity cache
│
├── persistence/
│   ├── ProfileRepository        Player profiles
│   ├── StatsRepository          Scores and match stats
│   └── DataSource               Backend abstraction
│
└── debug/
    ├── ActionLogger             Timestamped player action log
    ├── PlayerStateTracker       Transition detection, so logs fire once
    └── ServerDiagnostics        Tick timing, packet counts, memory
```

---

## 6. `core/` — CLIENT

All client logic and rendering. Imports `shared` and the game framework. Imports nothing from `server`, nothing platform-specific.

```
core/src/main/java/io/github/skystrike/
│
├── Main                         Application root, screen switching, platform injection
├── Assets                       Asset manager, load groups, handles
│
├── screens/
│   ├── LoadingScreen            Async asset load, shader warm-up, SDF load
│   ├── MainMenuScreen           Play, settings, account, exit
│   ├── GameScreen               Composition root for a match — thin orchestrator
│   └── transition/              Fades and screen transitions
│
├── net/
│   ├── GameClient               Connection lifecycle, send, receive
│   ├── StateBuffer              Snapshot history for interpolation
│   ├── Interpolator             Smooth remote entity motion between snapshots
│   ├── LocalPrediction          Local player responsiveness and reconciliation
│   ├── NetDiagnostics           Ping, loss, jitter, bandwidth
│   └── handlers/                One handler per inbound packet type
│
├── world/
│   ├── WorldState               Current interpolated view of the match
│   ├── EntityView               Client-side view of one networked entity
│   ├── TerrainRenderer          Platforms, walls, ground
│   └── VisibilityCache          Per-frame entity visibility results
│
├── render/
│   ├── GameCamera               Follow, ADS pan, surveillance target, bounds, shake
│   ├── RenderLayers             Explicit draw-order definition
│   ├── SpriteCatalog            Sprite lookup by weapon, gadget, utility id
│   ├── PlayerRenderer           Bodies, rotation, team tint, crouch
│   ├── WeaponRenderer           Held weapon, muzzle transform, recoil offset
│   ├── GadgetRenderer           Drones, cameras, shields, fuel tanks
│   ├── ProjectileRenderer       Bullets, grenades in flight
│   └── TrajectoryRenderer       Predictive throw arc
│
├── fx/                          The effects stack — see the effects plan
│   ├── FxSystem, FxPipeline, FxBudget, FxClock
│   ├── gl/                      Shader library, render targets, batching
│   ├── sdf/                     Baked occlusion field
│   ├── particles/               Two-tier particle system
│   ├── lighting/                Lights and vision cones
│   ├── post/                    Composite, bloom, blindness, distortion
│   ├── decals/                  Persistent impact marks
│   ├── shapes/                  Shockwaves, trails, aim cone
│   ├── events/                  Thread-safe effect event queue
│   └── debug/                   Overlay and profiler
│
├── ui/
│   ├── hud/
│   │   ├── HudStage             Composition of HUD elements (shapes pass, then text pass)
│   │   ├── HealthFuelBars
│   │   ├── LoadoutBar           Slots, ammo, utility counts, gadget slots
│   │   ├── SurveillanceBanner   The viewed device, its HP, the live controls
│   │   ├── GadgetPanel          State labels, durability, cooldowns
│   │   ├── Crosshair            Spread-reactive, dimmed while surveilling
│   │   ├── Minimap
│   │   ├── KillFeed
│   │   ├── DamageNumbers        Floating, world-space
│   │   └── DamageVignette       Screen-edge pulse
│   ├── console/                 The shared chat and console dialog
│   ├── text/                    Message buffer, wrapping, fonts, formatting
│   ├── menu/                    Main menu, settings, account dialogs
│   └── widget/                  Reusable buttons, fields, sliders, panels
│
├── chat/
│   ├── ChatClient               Send, receive, local echo reconciliation
│   ├── ChatMuteList             Local and persisted
│   └── QuickChat                Preset phrases for touch
│
├── command/
│   ├── ClientCommandService     Local execution or forward to server
│   ├── CompletionSources        Player names, weapon ids, cvars, enums
│   └── commands/                One file per command, plus one registration module
│
├── input/
│   ├── InputSampler             Polls gameplay input into an input packet
│   ├── InputRouter              Focus stack: UI consumes before gameplay polls
│   ├── KeyBindings              Rebindable actions, persisted
│   └── TouchControls            Virtual stick and buttons for mobile
│
├── audio/
│   ├── AudioSystem              Buses, volume, voice pooling, spatial playback
│   ├── SoundCatalog             Event → sound mapping; weapon and world halves
│   ├── EffectAudio              Audio consumer of the effect-event channel
│   ├── GunAudio                 Snapshot-driven weapon-state bridge (M7)
│   ├── OcclusionTest            Injected line-of-sight query for muffling
│   └── TinnitusEffect           Stun ringing
│
├── gameplay/
│   ├── LocalPlayer              Local view of own state and loadout
│   ├── LoadoutController        Slot switching rules and input mapping
│   ├── SurveillanceController   POV switching and input lock
│   └── TeamController           Team display and switching
│
├── platform/                    Interfaces implemented by launchers
│   ├── PlatformServices         Aggregate accessor
│   ├── AuthProvider             Sign-in, tokens, current user
│   ├── StorageProvider          Settings and local persistence
│   ├── KeyboardProvider         Soft keyboard control
│   └── DeviceInfo               DPI, tier hints, capabilities
│
└── debug/
    ├── DebugOverlay             Frame timings, net stats, entity counts
    ├── HitboxRenderer
    ├── LoadoutDialog            Debug loadout editor
    └── DebugCommands            Registration of debug-only commands
```

---

## 7. LAUNCHERS

```
lwjgl3/src/main/java/io/github/skystrike/lwjgl3/
├── Lwjgl3Launcher               Window config, entry point
├── StartupHelper                macOS thread handling
└── platform/                    Desktop implementations of core/platform interfaces
    ├── DesktopAuthProvider
    ├── DesktopStorageProvider
    ├── DesktopKeyboardProvider
    └── DesktopDeviceInfo

android/src/main/java/io/github/skystrike/android/
├── AndroidLauncher              Activity, surface config
└── platform/                    Android implementations of the same interfaces
    ├── AndroidAuthProvider
    ├── AndroidStorageProvider
    ├── AndroidKeyboardProvider  Soft keyboard raise and dismiss
    └── AndroidDeviceInfo        DPI and quality tier detection
```

The two `platform/` folders implement the **same interfaces** declared in `core/platform/`. When you add a platform capability, you add one interface in `core` and one implementation per launcher. `core` never branches on platform.

---

## 8. `assets/`

```
assets/
├── sprites/
│   ├── player/
│   ├── guns/
│   ├── melee/
│   ├── utility/
│   ├── gadgets/
│   └── ui/
├── shaders/                     See the effects plan for the full tree
│   ├── lib/                     #include-only GLSL
│   ├── particle/
│   ├── light/
│   ├── post/
│   ├── decal/
│   └── shapes/
├── fonts/                       Source TTFs, generated at runtime per DPI
├── sfx/                         Sound effects, all 22,050 Hz mono 16-bit
│   ├── guns/                    CC0 Free Firearm Sound Library takes (ASSET_ATTRIBUTION)
│   ├── world/                   Phase 9 effect sounds, synthesised in-repo
│   ├── status/                  The stun ring loop
│   ├── guns-sfx.json            Gun manifest: paths, variants, per-weapon events
│   └── world-sfx.json           World and status manifest
├── data/
│   ├── arena.sdf                Baked occlusion field
│   └── emitters/                Particle preset definitions, if externalised
└── assets.txt                   Generated manifest
```

```
tools/
├── SdfBakeTool                  Offline distance-field bake
├── AtlasPackTool                Sprite atlas packing
└── AssetManifestTool            Generates assets.txt
```

---

## 9. WHERE DOES NEW CODE GO?

A decision procedure, in order:

1. **Do both the client and the server need it?** → `shared`. If you are tempted to copy a type into both, that is the signal.
2. **Does it change authoritative game state?** → `server`. Never trust the client with it.
3. **Does it draw something?** → `core`, under `render/`, `fx/` or `ui/`.
4. **Is it platform-specific?** → declare an interface in `core/platform/`, implement it in each launcher.
5. **Is it tuning data?** → `shared/config/`, in the file for that system. Never a new god-constants class.
6. **Is it a one-off tool?** → `tools/`, not the game modules.

Then place it **in the folder of the system it belongs to**, not a folder named after its technical kind. A new drone behaviour goes in `gadget/`, not in a generic `entities/` or `systems/` folder.

---

## 10. CONVENTIONS

**Naming.** A type whose name ends in `System` owns per-tick or per-frame updates for a category. `Service` performs discrete operations on request. `Registry` is a lookup table. `Renderer` draws. `Controller` mediates input and state. Keep these meanings strict — a `Renderer` that mutates game state is misnamed and will be misused.

**Constants.** Live in `shared/config/`, grouped by system, one file each. A constant used by exactly one class may stay private in that class; a constant used by two belongs in config.

**Packets.** Split by direction into `c2s` and `s2c`. The direction of a packet is the single most useful thing to know about it, and folders communicate it for free.

**Commands.** One file per command, inside the owning system's `commands/` folder, plus one registration module per system. Discoverable by folder listing, with no reflection.

**Shaders.** Files under `assets/shaders/`, never string literals in source. Shared GLSL goes in `lib/` and is `#include`d.

**Tests.** Mirror the main source tree. Prioritise `shared/combat/`, `shared/math/` and `shared/command/` — they are pure functions, trivially testable, and the places where a silent client/server divergence hurts most.

**Documentation.** Every `System`, `Service` and `Renderer` carries a class-level comment stating what it owns, what it must be called between, and what it must never do (for example: never mutate state from a network thread). Ordering contracts that live only in someone's head get broken.

---

## 11. STRUCTURAL ANTI-PATTERNS TO AVOID

| Anti-pattern | Why it hurts | Instead |
| --- | --- | --- |
| One giant constants class | Everything depends on it; every change touches every module | `shared/config/`, one file per system |
| A 2,000-line screen or server class | Becomes the place code goes to hide | Thin composition root delegating to systems |
| Folders named by technical kind (`entities/`, `managers/`, `utils/`) | Related code scatters across three folders | Folders named by system |
| Duplicating maths between client and server | Silent divergence, impossible to debug | One copy in `shared/combat/` or `shared/math/` |
| `core` reaching into `server` for "just one type" | Collapses the module boundary permanently | Move the type to `shared` |
| GLSL in source string literals | No highlighting, no diffing, useless line numbers | Files under `assets/shaders/` |
| Reflective command or system discovery | Breaks native image, needs Android config, invisible to static analysis | Explicit registration, one line each |
| A `util/` package that accumulates everything | Becomes a dependency magnet with no owner | Narrow, named modules: `math/`, `text/`, `net/` |
```