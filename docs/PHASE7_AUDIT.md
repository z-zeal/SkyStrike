# Phase 7 Audit — HUD, Chat and Console

> Status snapshot taken at commit `bc081e9` (merge of PR #13, "Begin Phase 7 text foundation").
> Compares the repository against `IMPLEMENTATION_ROADMAP.md` Phase 7 and
> `CONSOLE_CHAT_PLAN.md` §11 build order / §12 definition of done.
>
> This document is deliberately blunt. Phase 7 is **not** complete *at the snapshot commit*, and
> two earlier phases were skipped entirely.

> **Read §§1–3 as chronology, not as current status.** They are the point-in-time findings at
> `bc081e9` and are kept verbatim; each stale section carries a *Historical* pointer to **§6**,
> which is the running record of what landed afterwards. All lists here say "absent" or "does not
> exist" about things that exist today.
>
> **Current state of the tree at `a293f80`** (merge of PR #43) by source reading: Phases 5 and 6
> are implemented, the HUD list in §3.1 is complete, the dialog in §3.2 has landed (including the
> four-background contrast overlay §3.2 flagged as missing), chat/capabilities in §§3.3–3.4 have
> landed, and the command core in §3.5 has landed for commands and cvars — the dedicated server
> `ServerConsole` stdin front-end, soft-keyboard handling and the quick-chat wheel are still
> absent. `PROTOCOL_VERSION` is **13**. None of this has been runtime-verified: see §7.

---

## 1. Headline findings

> **Historical.** These four findings are true of `bc081e9` and false of the tree today; §6 records
> what landed afterwards and the header block summarises where each now stands.

1. **Phase 5 (Throwables) was never started.** `shared/utility/` does not exist. There is no
   throwable physics, no terrain-occluded explosion, no frag/impact/smoke/molotov/poison/flashbang/
   claymore, no stun banding, no self-damage fire. `UtilityConfig` is still an empty holder whose
   javadoc reads "Filled in Phase 5".
2. **Phase 6 (Gadgets) was never started.** No drone, no throw camera, no shield arc, no fuel-tank
   hit zone, no view cycling, no `SurveillanceController`. `GadgetConfig` is an empty holder whose
   javadoc reads "Filled in Phase 6".
3. **Phase 7 is roughly 10% done.** Only the text foundation landed — and even
   `CONSOLE_CHAT_PLAN.md` build-order Phase 0 is not fully satisfied (the four-background contrast
   test mode is missing). There is **no HUD at all**: `core/ui/` contains only `text/`.
4. The last runnable client banner still reads *"SkyStrike — Phase 4 (Weapons, Melee and Loadout)"*
   in `GameScreen.statusLines()`, which is an accurate description of where the game actually is.

The roadmap's own dependency table lists Phase 7 as depending on **1 and 6**, and Phase 8 as
depending on **2**. So Phase 8 is not formally blocked by the Phase 5/6 gap, but large parts of the
Phase 8 effect catalogue (frag explosion, molotov impact, smoke grenade, stun/flash detonation)
have **no gameplay event to subscribe to**, because the throwables that raise them do not exist.

---

## 2. What is actually implemented

### 2.1 Phases 0–4 — complete and CI-covered

| Area | Evidence |
| --- | --- |
| Four-module build, dependency-direction check | `build.gradle` `checkModuleDependencies` |
| Config holders, `math/`, arena data | `shared/config/*`, `shared/math/*`, `shared/map/*` |
| Tick loop, transport, packet router, handlers | `server/sim/*`, `server/net/*`, `shared/net/*` |
| Movement, jetpack, rotation, aim, prediction | `shared/physics/PlayerMotion`, `core/net/LocalPrediction` |
| SDF bake + sampler, visibility pass, composite | `shared/sdf/*`, `core/fx/sdf/*`, `core/fx/lighting/VisibilitySystem`, `core/fx/post/CompositePass` |
| Shared vision maths | `shared/vision/VisionMath`, `SmokeVolume` |
| Ballistics, hit zones, spread, recoil, fire modes | `shared/combat/*`, `server/combat/*`, `server/weapons/*` |
| Weapon registry (89 guns), melee (wire ids 1000+), loadout/slots | `shared/weapons/*`, `shared/model/PlayerLoadout`, `core/gameplay/LoadoutController` |

### 2.2 Phase 7 — what landed in PR #13

`CONSOLE_CHAT_PLAN.md` build order **Phase 0 — Text foundation**, partially:

| File | State |
| --- | --- |
| `shared/text/ChatChannel` | Done — seven channels, `requiresConsoleAccess()`, `isVisibleTo()` |
| `shared/text/ChatTarget` | Done — ALL/TEAM only, `toggle()`, no mode enum |
| `shared/text/TextLimits` | Done — body cap, buffer size, chat token budget, duplicate window |
| `shared/text/TextSanitizer` | Done — code-point safe, strips controls/format/bidi/`[` `]`, collapses whitespace |
| `shared/text/RateLimiter` | Done — deterministic token bucket with injected clock |
| `core/ui/text/FontManager` | Done — FreeType regeneration, baked 1px outline + shadow, 2.2%/12px/28px clamp |
| `core/ui/text/MessageLine` | Done — structured record, channel-filtered |
| `core/ui/text/MessageBuffer` | Done — 512-line ring, filters on read so a promotion reveals history |
| `core/ui/text/MessageFormatter` | Done |
| `core/ui/text/TextWrapper` | Done — width-aware with cache |
| `core/ui/text/TextContrast` | Done — minimum luminance floor |
| `core/ui/text/MessageSeverity` | Done |
| Shared tests | `TextSanitizerTest`, `RateLimiterTest`, `ChatTextContractTest` |

---

## 3. What is missing

### 3.1 Phase 7 — HUD (roadmap, "HUD alongside it")

**Nothing exists.** `core/ui/` contains only `text/`. All of the following are absent:

- health and fuel bars
- loadout bar with per-slot ammo and utility counts
- gadget panel with durability and cooldowns
- spread-reactive crosshair
- minimap
- kill feed widget (the *data* exists — `server/combat/KillFeedService`, `PacketKillEvent`,
  `ClientSession.killFeed()` — but it is printed into a debug text overlay, not rendered)
- floating damage numbers (`PacketDamageEvent` exists and is delivered; nothing draws it)
- damage vignette

`core/render/StatusOverlay` is a 59-line debug text dump, not a HUD.

**Historical — read with §6.** The list above describes the tree as of PR #13 and is now wrong
about most of itself. `core/ui/hud/` exists and holds the HUD: `HudStage` (one screen-space
projection, a shapes pass then a text pass, no widget owning a batch) with `HealthFuelBars`,
`LoadoutBar` (per-slot ammo, utility counts and the two gadget slots), `Crosshair`,
`KillFeedWidget` (rendered, not printed), `DamageVignette`, `SurveillanceBanner`, `LoadoutPicker`,
`DebugPanel` (the absorbed `StatusOverlay`) and, from M11, `Minimap`. `StatusOverlay` is gone.
Still missing from this list, and tracked in §6: the **gadget panel** with durability and cooldowns
(the loadout bar's gadget boxes are the only durability readout today) and **floating damage
numbers** (`PacketDamageEvent` is still delivered and still drawn by nothing).

### 3.2 Phase 7 — the dialog (`CONSOLE_CHAT_PLAN.md` build order Phase 1)

Absent in full: `ConsoleDialog`, `ConsoleInputField`, `ConsoleFocus`, `ChatTargetButton`,
`CommandLineDetector`, `ConsoleCompletionPopup`, `ConsoleHintLine`, `ConsoleTheme`.
Consequences: no one-key open, no ALL/TEAM button, no `Tab` accelerator, no persistence of the
target choice, no draft preservation on `Escape`, no passive fade-out mode, no scrollback
scrolling, no input focus capture. `core/input/InputRouter` has the focus-stack hook the plan
needs, but nothing claims focus.

Build-order Phase 0's own gate — *"the four-background contrast test mode"* (black / white / grey /
noise) — is also missing, so the readability requirement is asserted but unverified.

**Historical — read with §6.** Now wrong. `core/ui/console/` holds `ConsoleDialog`,
`ConsoleInputField`, `ConsoleFocus`, `ChatTargetButton`, `ConsoleCompletionPopup`, `ConsoleHintLine`
and `ConsoleTheme`, and `GameScreen` claims focus through the router. The four-background check
exists as `core/ui/text/ContrastTestOverlay` (`ui_contrast_test`, F12): it tiles black / white /
grey / noise and draws the dialog's own panel fill and luminance-clamped text. Whether it *looks*
legible on all four is still a runtime check — the M11/M13 checklists own it.

### 3.3 Phase 7 — chat transport and server authority (build order Phase 2)

Absent: `ChatMessage`, `PacketChatRequest`, `PacketChatMessage`, `server/chat/ChatService`,
`ChatModeration`, `ChatHistory`, `core/chat/ChatClient`, `ChatMuteList`, `QuickChat`.
`TextSanitizer` and `RateLimiter` exist but **nothing calls them** — they are currently dead code
with tests.

**Historical — read with §6.** Now wrong except for `QuickChat`, which is still absent.
`ChatMessage`, both chat packets, `server/chat/*`, `ChatRequestHandler`, `core/chat/*` and the
mute list all landed; `TextSanitizer` and `RateLimiter` are live callers in the relay path.

### 3.4 Phase 7 — capabilities (build order Phase 3)

Absent: `shared/command/Permission`, `ConsoleAccess`, `PacketCapabilities`,
`server/command/PermissionResolver`, `CapabilityBroadcaster`, `core/command/ClientCapabilities`.
`ChatChannel.isVisibleTo(boolean)` takes the flag the server is supposed to push, but no packet
carries it.

**Historical — read with §6.** All of these landed; `PacketCapabilities` carries the flag on join
and on change, and `CapabilityBroadcaster` re-pushes it mid-match.

### 3.5 Phase 7 — commands, completion, server commands, cvars, terminal, mobile

Build order Phases 4–9 are absent in full: no `shared/command/` package at all, no
`CommandSpec`/`ArgType`/`CommandParser`/`CommandRegistry`/`CommandContext`, no `help`/`clear`, no
completion or history, no `ServerCommandService`, no `Cvar`/`CvarRegistry`, no `ServerConsole`
stdin front-end, no soft-keyboard handling or quick-chat wheel.

**Historical — read with §6.** Mostly wrong now. `shared/command/` exists with `CommandSpec`,
`ArgTypes`, `CommandParser`, `CommandRegistry`, `CommandContext`, `CommandResult`, `Cvar` and
`CvarRegistry`; `help`/`clear`, completion (`ConsoleCompletionPopup`) and input history are in;
`server/command/{ServerCommandService,ServerCommandModule}` and the client-side modules are wired.
Still genuinely absent: the `ServerConsole` stdin front-end (its absence is noted in
`ServerCommandService`'s own javadoc), soft-keyboard handling and the quick-chat wheel, which are
build-order Phase 9 and roadmap Phase 10 items.

### 3.6 Earlier phases skipped — Phase 5 and Phase 6

See §1. These are listed here because Phase 7's own HUD scope includes a *"gadget panel with
durability and cooldowns"*, which cannot be implemented against gadgets that do not exist, and
because Phase 8's effect catalogue is largely keyed to throwables.

**Historical — read with §6.** Both phases have since landed. The gadget panel exists
(`shared/hud/GadgetPanelModel` + `core/ui/hud/GadgetPanel`), and the throwable-keyed effect
presets have their events. Cooldowns were deliberately not added (M13 decision), so the panel's
countdown is wired but dormant.

### 3.7 Phase 8 prerequisites that are also missing

`EFFECTS_PLAN.md` phases 0–4 were supposed to be absorbed into roadmap Phase 2. Most landed, but
these Phase-0/3/4 items did not:

| Missing | Plan reference |
| --- | --- |
| `GlCaps` (ES2/ES3 + precision probing) | effects §12.1, build order Phase 0 |
| `FxBudget` (quality tiers, the single budget object) | effects §11 |
| `FxClock` (pausable effect time base) | effects §12.1 |
| `FxDebugOverlay`, `FxProfiler` | effects §11 — "build the overlay in Phase 0" |
| Forced-`mediump` desktop debug mode | effects §3.1 |
| Shader hot reload, injected `#define` variants, fallback magenta shader, warm-up | effects §10 |
| `LightSystem` / `Light` / `LightAnimation` / `LightRenderer` and the light shaders | effects build order Phase 3 |
| `PostChain`, `FinalPass` | effects build order Phase 4 |
| `VisibilityQuery` as a named CPU/GPU parity type | effects §12.1 |

`FxPipeline.composite()` already passes `null` for the light texture, so the light slot is wired
but unfilled — exactly as the roadmap's Phase 2.3 instructed.

**Historical — read with §6.** `FxBudget`, `FxClock` (`core/fx/particle/FxClock`) and
`FxEventQueue` have landed, and the light slot is now filled (`core/fx/lighting/{Light,LightPass,
LightPool}`; the light lives on the HUD-visible `r_player_light*` cvars and the budget counters are
surfaced by the `fx_debug`/`quality` cvars). Still absent from this table: `GlCaps`,
`FxDebugOverlay`/`FxProfiler` as named types, `PostChain`, a `LightSystem` class (the role is split
across `LightPass`/`LightPool`), and `VisibilityQuery` as a named parity type
(`shared/vision/{Observer,ObserverSet}` plus `VisionMath` play that role). The effects-plan gates
themselves have not been re-checked line by line against the source.

---

## 4. Is Phase 7 complete enough to move to Phase 8?

> **Historical (answer at the snapshot: no).** At `bc081e9` nothing passed. As of `a293f80` most of
> §12's bullets have source, but the roadmap's four "done when" clauses are all still unverified on
> a running build, and one of them — "the ALL/TEAM button works on touch" — has no source at all,
> since touch controls are a roadmap Phase 10 item. The honest answer remains "not verified", not
> "passes".

**No, not as "complete".** Measured against §12 of `CONSOLE_CHAT_PLAN.md`, zero of the eleven
definition-of-done bullets pass today. Measured against the roadmap's Phase 7 "done when" line —
*"one key opens chat, the ALL/TEAM button works on touch, a `/` line restyles live, and text is
readable on all four test backgrounds"* — none of the four clauses pass.

**But Phase 8 is not technically blocked by it.** The roadmap's dependency table makes Phase 8
depend on Phase 2 only (SDF + visibility + composite), and that foundation is in place. The
practical blockers for Phase 8 are instead:

- the missing effects-plan Phase 0 infrastructure (`FxBudget`, `FxClock`, `FxEventQueue`,
  profiler/overlay) — these are cheap and should be built first;
- the missing Phase 5/6 gameplay events that half the §9 effect catalogue exists to visualise.

So the honest sequencing recommendation is:

1. Finish the Phase 7 slices that are server-authoritative and CI-verifiable now
   (chat transport, capabilities) — they unblock nothing else but they stop `TextSanitizer` and
   `RateLimiter` being dead code, and they are the parts that cannot be validated by eye later.
2. Build the Phase 8 **infrastructure** increment (`FxClock`, `FxBudget`, `FxEventQueue`,
   `FxEventType`, profiler counters) — presentation-side, low risk, needed by everything after.
3. Decide explicitly whether Phases 5 and 6 are being deferred, because the Phase 8 effect
   catalogue and the Phase 7 gadget panel both assume them.

---

## 5. Deferrals that need explicit confirmation

> **Historical — read with §6.** D1–D4 have since been resolved by landing the work (D1: Phases 5
> and 6 are implemented; D2: the HUD landed, M4/M11/M13; D3: the dialog landed; D4: the command
> core, cvars and server commands landed — the `ServerConsole` terminal is the one piece still
> open). D5 is partly resolved: `FxBudget`, `FxClock` and `FxEventQueue` landed, the profiler and
> debug overlay did not. No new deferrals are recorded here.

| # | Deferral | Why it needs a decision |
| --- | --- | --- |
| D1 | Phase 5 (throwables) and Phase 6 (gadgets) skipped | Half the Phase 8 effect catalogue and the Phase 7 gadget panel depend on them |
| D2 | Phase 7 HUD (health/fuel, loadout bar, crosshair, minimap, kill feed, damage numbers, vignette) | Large client-only slice; cannot be verified in a JDK-less sandbox, only compiled |
| D3 | Phase 7 dialog (one-key chat, ALL/TEAM button, focus capture, passive mode) | Same — interactive, needs a runnable client to gate |
| D4 | Phase 7 command core, completion/history, server commands, cvars, server terminal | Build-order Phases 4–8; large, but mostly plain-Java and CI-verifiable |
| D5 | Effects-plan Phase 0 infrastructure not yet built | Should precede any further Phase 8 work |

---

## 6. Progress since this audit

> **Chronological record.** Rows and paragraphs below were written as each increment landed and are
> kept in order; later entries supersede earlier ones. Where the prose says "Phase 5 is under way"
> or "still missing", read it against the newest row, not the top of the section.

| Slice | State |
| --- | --- |
| Console plan build-order Phase 2 — chat transport and server authority | Implemented: `ChatMessage`, `PacketChatRequest`, `PacketChatMessage`, `server/chat/{ChatService,ChatModeration,ChatHistory}`, `ChatRequestHandler`, `core/chat/{ChatClient,ChatMuteList}` |
| Console plan build-order Phase 3 — capabilities | Implemented: `shared/command/{Permission,ConsoleAccess}`, `PacketCapabilities`, `server/command/{PermissionResolver,CapabilityBroadcaster}`, `core/command/ClientCapabilities` |
| Phase 5 increment 1 — shared throwable foundation | Implemented: `shared/config/UtilityConfig`, `shared/utility/{UtilityId,DetonationMode,UtilityEffect,UtilityDefinition,UtilityRegistry,ThrowablePhysics,ExplosionMath,StunMath}`, `shared/model/ThrownUtility` |
| Phase 5 increment 2A — utility inventory and wire contract | Implemented: populated loadout slots 4–5 with carried counts and respawn refill; utility choices added to the server-authoritative loadout request; `ThrownUtility` appended at Kryo index 25 and added to `PacketGameState` |
| Phase 5 increment 2B — authoritative lifecycle, zones and preview | Implemented: utility trigger edges and cooldowns; `server/utility/UtilitySystem` flight, fuse/contact/proximity detonation, occluded blast/impulse damage, stun lock, smoke/poison/fire zones, claymore placement; snapshots now carry `UtilityZone` at Kryo index 26; live smoke zones feed the client shader and `TrajectoryRenderer` uses `ThrowablePhysics` |
| Phase 6 increment 1 — shared gadget foundation and contracts | Implemented and merged in source commit `402b75b75d9864bd1dbee1a2207fbba8feddb63c`: `shared/gadget/{GadgetId,GadgetBehavior,GadgetDefinition,GadgetRegistry}`, populated `GadgetConfig` with the centralised §7 reference-unit conversion, `shared/model/{GadgetSlot,ShieldState}`, `shared/combat/ShieldArcMath`, two Q/E gadget slots inside `PlayerLoadout` with explicit respawn reset, gadget choices in `PacketLoadoutUpdate`, `GadgetSlot` appended at Kryo index 27, fuel-tank hit zone connected through `HitZoneMath.resolve(x, y, player)` into `DamageService` |
| Phase 6 increment 2 — authoritative worn-gadget behavior and gadget input | Implemented and merged: Q/E input edges with birth-sequence retransmission and protocol 9; server `ShieldSystem`/`FuelTankSystem`; shield arc interception in the central damage path, handgun-only locking, shared fuel-tank jetpack multipliers, and tank detonation through occluded `ExplosionMath` with gadget wire-id kill attribution |
| Phase 6 increment 3 (M10) — drone, throw camera, surveillance lock, gadget vision, rendering and HUD | Implemented (branch `arena/f6b096ea-skystrike`, PR #37): `shared/model/{DroneEntity,CameraEntity}` snapshot state; `shared/gadget/{SurveillanceView,GadgetPress,DroneMotion,CameraFlight}`; server `gadget/{DroneSystem,CameraSystem,SurveillanceService}` following the `ShieldSystem`/`FuelTankSystem` patterns; `ThrowablePhysics.stepInPlaceUntilContact` (the one integrator, bounce off) for the camera's stick-to-first-surface; the surveillance lock enforced server-side in `PlayerMotion` and the loadout tick; view cycling (key 6) and Escape exit routed through the focus stack; rounds hit devices; `EffectBroadcaster.cullFor` counts a recipient's live drone/stuck camera as extra observers via `VisionMath.canObserverSeeTarget`; client `gameplay/SurveillanceController` prediction, camera follow + 1.2× zoom, device cones in the visibility pass (dimmer for the drone, per-observer brightness in the shader), `render/GadgetRenderer` (drones, cameras, shield front/rear arc, rear fuel tank) and the surveillance HUD banner; `DroneEntity`/`CameraEntity` appended at Kryo indices 33/34, `PROTOCOL_VERSION` 11 → 12 |
| Phase 7 HUD increment (M11) — the fog-gated minimap | Implemented (branch `arena/52f46dde-skystrike`): `shared/hud/MinimapModel` (which markers may exist, normalised y-up arena space, ghost hold/fade/expiry on the wall clock, memory emptied on respawn and pruned the moment a target is absent from the unculled snapshot); `shared/vision/{Observer,ObserverSet}` lifting the client's inline observer assembly into one shared rule so the GPU cone pass and the map's blip gate cannot disagree; `VisionMath.calculateVisibility`'s per-observer cone-half-angle overload, `VisionMath.isTargetLit` and `VisionConfig.LIT_VISIBILITY_THRESHOLD` (6.1%, just above the peripheral floor) as the presentation bar — `canObserverSeeTarget`'s 1% bar reports a body standing behind the viewer as seen, which would put a marker where the screen draws nothing; `Team.areAllies` adopted by `MinimapModel`, `DamageService` and `KillFeedService`; `core/ui/hud/{Minimap,HudTheme}` plus the debug-panel top inset; cvars `cl_minimap` and `cl_minimap_allies` (mechanics §4's "teammates visible on minimap", default on, registered ungated like `ui_loadout`); **no protocol change** — `broadcastSnapshot` was never culled, so the map is presentation over state every client already holds. Checklist: `docs/M11_HUD_ACCEPTANCE.md` |
| Phase 7 HUD increment (M13) — the gadget panel and floating damage numbers | Implemented: `shared/hud/{GadgetPanelModel,DamageNumberModel,WorldProjection}` (the panel's words, durability and cooldown countdown from the predicted `PlayerLoadout`; the outgoing-hit filter, falloff ratio and life of each number; the one world-to-screen rule) with tests; `core/ui/hud/{GadgetPanel,DamageNumberWidget}`; `HudFrame` gains a nullable `worldView`, set only by `GameScreen`. **No wire change.** The damage numbers ride the `PacketDamageEvent` the client already receives. No gadget starts a cooldown, by M13 decision, so the countdown is wired but dormant. Checklist: `docs/M13_HUD_ACCEPTANCE.md` |
| Phase 7 HUD — complete after M13 | All eight items of §3.1's list now have a home in `core/ui/hud/`. The §3.1 list is historical; the open Phase 7 work from this row was the dialog (§3.2) and chat-plan build-order Phases 4–9 (§3.5), both of which have since landed except for the mobile tail noted above |
| Phase 7 increment (M14) — the vision bubble | Implemented: every eye gets an all-round short-range circle in the same visibility pass as its cone — `Observer.bubble` with `VisionConfig.FULL_CIRCLE_HALF_ANGLE_DEGREES`, `BODY_BUBBLE_RADIUS = 140` and `GadgetConfig.DEVICE_BUBBLE_RADIUS = 70`; the shader's angular term and `VisionMath.calculateConeFactor` agree; the minimap blip gate asks the same `ObserverSet`; a device bubble gets its own light; `EffectBroadcaster` culls effects against the bubbles too. Gameplay (damage, stun, blasts, camera placement) still uses line of sight only, and the M6/M12 "outside the cone stays black" rule is superseded inside the bubble only. Checklist: `docs/M14_VISION_BUBBLE_ACCEPTANCE.md` |
| Presentation and audio increment (PR #42) — midtones, gunfire cues, FIRE rollover, drone retune | Implemented: display-only midtone lift in `assets/shaders/post/composite.frag` + `CompositePass` (the raw visibility texture remains the gameplay/light mask); server-authenticated gunfire cues appended to `PacketGameState.gunfireEvents` and consumed by `GunAudio`, with the generic muzzle-flash audio path no longer doubling the report — **`PROTOCOL_VERSION` 12 → 13**; fresh FIRE attacks roll over the oldest explicitly marked equal-priority FIRE voice; drone free-flight retuned to 500 world units/s with a 16/s velocity lerp (`GadgetConfig`). Checklist: `docs/VISION_AUDIO_DRONE_ACCEPTANCE.md` |
| Source-module boundary hardening (PR #43) | Merged: the module-dependency check was strengthened (both in `build.gradle`'s `checkModuleDependencies` and in `tools/scratch/static_check.py`) so `shared` may not reach `core`/`server`, `server` may not reach `core` or LibGDX, and `core` may not reach `server`. As of `a293f80` the independent scan finds zero such imports |

Phase 5 is under way, so §3.6's "never started" now applies to Phase 6 only. What landed is the
shared half: identity and wire encoding (2000 + ordinal, a third range in the existing `weaponId`
int), the mechanics §6 catalogue, the one substepped bouncing integrator the server and the
trajectory preview call, absolute terrain occlusion for blasts, and stun banding. Increment 2A has
its contract slice: loadout slots 4–5 carry utility identity and authoritative per-life counts,
depleted slots are skipped, and utility composition changes remain respawn-only.

Increment 2B writes the operational half: trigger edges spawn an authoritative `ThrownUtility`
only after its type cooldown permits it and consume carried count only after spawn succeeds; the
server steps that state with the shared integrator, resolves fuses, impact contact and armed
claymore proximity, then routes all damage through `DamageService`. Frag/impact and claymore
impulses become real player velocity, stun blindness/slow replicate in `Player` (slow blocks
weapons and targets 30% movement), and respawn clears those status effects. Persistent smoke,
poison and tangent-spread molotov zones are `UtilityZone` snapshot state at appended Kryo index 26.
Smoke is converted to the same opaque `SmokeVolume` circles for the server-facing query list and
for the client visibility shader; molotov overlap uses its strongest patch once per shared DOT
tick. `core/render/TrajectoryRenderer` predicts with the identical `ThrowablePhysics` step and
the client renders/interpolates both thrown state and zones. The protocol is now 7. Claymore's
arming/trigger/cone numbers, fire jitter, zone cap and the damage-over-time tick interval remain
explicitly provisional where the plan gives no values.

Phase 6 is now under way too, so §1.2's "never started" is itself historical. Increment 1 is
the shared, deterministic foundation and the authoritative contracts — deliberately not the
client surveillance experience:

- **Identity and behaviour.** `shared/gadget/GadgetId` freezes the structure plan's documented
  order (none, drone, shield, fuel tank, camera) as the wire ordinal, reserves wire range 3000+
  in the shared weapon-id int, and carries the three `GadgetBehavior` activation modes
  (drone/camera manual, shield hybrid, fuel tank passive). `GadgetRegistry` holds one immutable
  record per real gadget (30/150/—/20 durability); nothing in the registry is provisional.
- **Configuration.** `GadgetConfig` is populated from mechanics §7. The §7 gadget numbers are
  quoted in the plan's old small-scale reference units; the conversion (25 world units per
  reference unit, derived from the 50-unit player against the reference two-unit character)
  exists exactly once as `WORLD_UNITS_PER_REFERENCE_UNIT`, giving the original drone
  speed 200 u/s, drone cone 250 u @ 70°, camera launch 300 u/s, tank blast radius 100 u.
  Genuinely unspecified tuning (drone lerp/damping and cone brightness, camera cone, rear shield
  arc width, tank impulse, shield overflow-to-health) is explicitly flagged provisional in the
  javadoc. The original drone 200 u/s speed and 8/s lerp were later retuned to 500 u/s and
  16/s for more responsive piloting; the faster path remains collision-safe at the authoritative
  60 Hz tick.
- **Live state and loadout.** `shared/model/GadgetSlot` (gadget ordinal, durability, active,
  broken, cooldown) rides as two independent Q/E slots inside `PlayerLoadout`, outside the 1–5
  selection system. Both default to empty — the plan names no default gadget. Duplicates are
  rejected per slot (provisional rule; stacking passive multipliers silently would be worse).
  Respawn semantics are explicit and tested: the chosen gadget survives death, its state (a
  broken shield included) resets.
- **Deterministic maths.** `shared/combat/ShieldArcMath` resolves equipped-front/stowed-rear
  coverage with `Angles.shortestDelta` (no ±180 wrap bugs), splits damage between durability
  and health, and absorbs nothing when broken. `HitZoneMath` gained the gadget-aware
  `resolve(impactX, impactY, player)` overload that feeds the *existing* fuel-tank hook from
  loadout state — there is still exactly one hit-zone implementation — and `DamageService` now
  calls it. The `FUEL_TANK` zone multiplier stays 1.0, so until the authoritative detonation
  lands, a tank hit deals plain body damage: reported, never regressing.
- **Wire contract.** `GadgetSlot` appended at Kryo index 27 (nothing reordered), gadget fields
  appended to `PacketLoadoutUpdate` with `KEEP_CURRENT` distinct from the valid explicit
  `NONE`, server-side per-field validation in `LoadoutUpdateHandler`, respawn-only application
  through `PlayerSession`. `PROTOCOL_VERSION` moved 7 → 8 for the gadget payload and 8 → 9 for the appended Q/E input edges.

**Still missing from Phase 6 after increment 2**, by design of the increment: the authoritative
drone/camera entity lifecycle (`server/gadget/{DroneEntity,DroneSystem,CameraEntity,CameraSystem}`),
view cycling, surveillance input locking, drone/camera observers in the visibility shader, and
all gadget rendering/HUD. Increment 2 adds the Q/E input contract, authoritative shield toggling,
arc interception and handgun lock, shared fuel-tank movement multipliers, and the rear-tank
detonation path. The shield covers bullets, melee, explosions and utility damage through the
central damage service; this broad non-bullet coverage and the provisional shield overflow rule
remain tuning decisions where the plan is silent. The tank wearer's unconditional death is an
intentional exception to shield interception. **Increment 3 (M10) has since landed every item
on that list** — the entities live in `shared/model` (snapshot payload, like `ThrownUtility`)
with their systems in `server/gadget`, and the acceptance checklist is
`docs/M10_GADGETS_ACCEPTANCE.md`.

M11 is the first HUD increment since the widgets themselves landed, and its content is a rule
rather than a drawing. `GameServer.broadcastSnapshot` sends every player's true position to every
client — only `sendEffectSpawns` culls per recipient — so a minimap that read the snapshot would
have been a wallhack the game ships with, marking enemies the renderer had just sworn were
invisible. Every marker is therefore gated by the same eyes, the same cone angles and the same
presentation bar that decide what the visibility pass lights, and the gate is deliberately stricter
than the sight test the effect culler uses: `VISIBILITY_THRESHOLD` (1%) is cleared by the 6%
peripheral floor alone, so a body standing directly *behind* the viewer counts as "seen" there,
which is the right answer for "could this recipient plausibly have seen this detonation" and the
wrong one for "may I draw a dot on their head". Gating alone would strobe markers at the cone edge,
so a lost sighting becomes a ghost: it holds, fades and expires on the wall clock, frozen at the
position it was genuinely seen at, and is retired immediately when its target stops existing —
because the snapshot is unculled, absence means dead or destroyed, not hidden. The observer
assembly that used to live inline in `GameScreen.render` moved to `shared/vision/ObserverSet` for
this: one rule, two consumers, and no way for the cones on screen and the markers on the map to
drift apart.

`TextSanitizer` and `RateLimiter` are no longer dead code — the relay is their only caller.
`PROTOCOL_VERSION` moved 4 → 5 for chat/capabilities, 5 → 6 for the throwable/loadout contract,
6 → 7 for live utility zones and replicated throwable status, 7 → 8 for the gadget
payload, 8 → 9 for the appended Q/E input edges, 9 → 10 for the M1 command packets, 10 → 11
for the M7 effect channel, 11 → 12 for the M10 gadget device entities, and 12 → 13 for the
server-authenticated gunfire cue list appended to `PacketGameState`. §3.3 and §3.4 above describe
the state *before* this work; the rest of §3 is likewise historical and now carries its own
*Historical* pointer.

Still outstanding for Phase 7 as of `a293f80`: the HUD list in §3.1 is complete, the dialog in
§3.2 landed, and §3.5's command core landed. What genuinely remains is the mobile tail —
the dedicated `ServerConsole` stdin front-end, soft-keyboard handling and the quick-chat wheel —
plus the effects-plan gaps listed in §3.7 (no `GlCaps`, no named
`FxDebugOverlay`/`FxProfiler`/`PostChain`/`VisibilityQuery`). Gadget cooldowns were considered and
deliberately not added (M13 decision), so the panel's countdown stays dormant. **None of the
acceptance checklists for any of this work have been executed** (see §7).

A later pass on top of `c6f220a` (branch `arena/eccbf8f9-skystrike`, presented as one pull request)
changed three pieces of presentation behaviour and added one plan. Nothing in it was compiled or
run here either; each change carries its own unexecuted acceptance note.

- **The muzzle is per weapon.** `CombatConfig.MUZZLE_OFFSET` — one 22-unit value for all 89 guns,
  documented as "matching the drawn barrel" — is gone. `shared/weapons/HeldWeaponGeometry` derives
  the muzzle point from the generated per-weapon draw scale (`SpriteScales`) and the sprite's
  drawn barrel, and `BulletSystem.spawn`, `FireController.emitMuzzleEffects` and `PlayerRenderer`'s
  fallback barrel line all use that one point; the eye-to-muzzle terrain guard is unchanged. No
  wire change. `docs/MUZZLE_GEOMETRY_ACCEPTANCE.md` is new and unexecuted.
- **The player light is the sight field's own light.** The composite is now
  `scene × (max(visibility, ambientFloor) + light)`: the light buffer joins the illumination
  instead of being added to the finished frame, and the radial profile is the sight field's own
  quadratic (`Light.SIGHT_FALLOFF = 1`, with `EFFECT_FALLOFF = 2` for emissive effect lights). The
  `EFFECTS_PLAN` composite rule, the roadmap and the build plan were amended where they pinned the
  old rule; `docs/PLAYER_LIGHT_ACCEPTANCE.md` is new and unexecuted.
- **The crosshair is the cursor on desktop.** `HudFrame` carries a `PointerPosition`, and the
  reticle, the ADS dot and the hit marker draw at it, with the screen centre as the touch and menu
  fallback. The OS pointer is hidden while the crosshair stands in for it, and restored for every
  modal dialog, a dead local player, and when the match screen is left or disposed.
  `docs/CROSSHAIR_POINTER_ACCEPTANCE.md` is new and unexecuted.
- **Planned, not built:** the map building system (`docs/MAP_BUILDER_PLAN.md`, with
  `docs/MAP_BUILDER_ACCEPTANCE.md`): doors, breakables, glass, one-way platforms, materials, the
  text map format, the SDF re-bake and hash guard, the editor/CLI split, and the protocol 13 → 14
  additions it will need. No code from it exists in the tree.
- **Map building system, increment 1 — shared model and text format.** Implemented:
  `shared/map/{BlockKind,Material,MapBlock,MovementRule,BlockRenderStyle,Blocking,BlockRules}`;
  `ArenaMap.blocks()` with its legacy `solids()` view derived from the bullet column; and a public
  symmetry builder. `ArenaMapFormat` is a strict, dependency-free version-1 reader/writer with
  canonical save → load → save output and source-line errors for malformed headers, unknown
  verbs/kinds/materials, invalid bounds/sizes, duplicate blocks and wrong symmetry verbs.
  `ArenaMap.standard()` now loads `StandardArenaMap`'s embedded canonical text, and shared tests
  preserve the old complete rect set while covering the model rows, format failures and round trip.
  All current standard blocks are stone walls, so no running collision, sight, bullet, SDF or
  rendering behaviour changes in this increment; there is no wire or protocol change. Door ids and
  state, material damage, rule-aware consumers, SDF hash/rebake, an `assets/maps` file, editor/CLI
  and protocol 14 remain future work. `docs/MAP_BUILDER_ACCEPTANCE.md` remains wholly unexecuted.
- **Map building system, increment 1 — SDF identity guard.** Implemented: order-independent
  `MapHash` (SHA-256 over the authored axis, spawns, kinds, materials and exact rectangle bits),
  cached through `ArenaMap.mapHash()`; `SdfField` version 2 embeds that 32-byte identity;
  `SdfBaker` reads the SDF column of `BlockRules`; and `SdfCache` verifies the requested map before
  upload. A shipping-default client now fails loudly for a missing, old or mismatched asset, while
  an explicitly debug-enabled client logs and re-bakes. `assets/data/arena.sdf` was re-headered
  with the current standard-map hash; the 1.5M texel payload is unchanged because the migrated
  arena geometry is unchanged. Shared tests cover hash mismatch, header round-trip and SDF-column
  selection. No wire or protocol change; the manual map checklist remains wholly unexecuted.
- **Map building system, increment 2 — rule-aware line queries.** Implemented: `ArenaMap` now
  derives one cached, immutable rectangle view per `Blocking` purpose from `BlockRules` at build
  time (`blockers(Blocking)`), with `solids()` kept as exactly `blockers(BULLETS)`;
  `MapQueries.lineBlocked`/`firstBlocker` take an explicit purpose (the five-argument forms remain
  the `SIGHT` convenience, and a null map still means open space);
  `shared/vision/VisionMath.hasLineOfSight` asks the `SIGHT` column instead of sweeping
  `arena.solids()` itself, so a kind that passes sight passes the cone shader's maths with it;
  `server/combat/RaycastBulletSystem` sweeps and reports terrain through the `BULLETS` column and
  its `blockedByTerrain` takes the purpose explicitly, which `BulletSystem.spawn`'s muzzle guard
  now passes; and the frag, claymore-cone and molotov fire-spread occlusion queries ask `BLAST`,
  the column the rule table pins to sight. New tests: `MapQueryPurposeTest` (every kind × every
  purpose against the rule table, on a mixed-kind map), a sight-column test in `VisionMathTest`,
  and `RaycastBulletSystemTest` (a round passes intact glass and a one-way platform, stops on a
  wall, an intact breakable and a closed door, and cannot reach a body standing behind the wall
  behind a window). Behaviourally inert for the shipped arena — it contains only walls, so every
  purpose view holds the same rects — and no wire or protocol change. Still deferred in milestone
  2: `PlayerMotion`'s movement column and one-way platform tops (it still sweeps the bullet
  compatibility view), the kind-aware `TerrainRenderer`, and the static check that no consumer
  tests a kind by name.
- **Map building system, milestone 2 complete — movement column, kind-aware rendering and the
  consumer gates.** Implemented: `PlayerMotion` sweeps the movement column of the shared rule table
  (`ArenaMap.movementBlockers(MovementRule)`, `BlockRules.movement`), so `MovementRule.SOLID` rects
  block on every face while a `ONE_WAY_TOP` slab only answers a falling body whose feet were at or
  above its top on the previous step, is never a ceiling, never a horizontal collider and never
  ejects a body already inside it. Crouch+jump on a platform starts the drop-through window
  (`PlayerConfig.PLATFORM_DROP_THROUGH_SECONDS`), which suspends one-way tops until the feet leave
  the slab; it is *transient* motion state on `Player`, so the snapshot layout and protocol 13 are
  untouched and `NetworkRegistrationTest` pins that it does not reach the wire. The same column now
  drives `DroneMotion` and the camera's release point (a device follows a body), while
  `ThrowablePhysics` asks `Blocking.BULLETS` explicitly (a thrown object follows a round);
  `MapQueries` gained `overlapsMovementBlocker` and answers `surfaceBelow` from the movement
  column, and its `solidAt`/`overlapsSolid`/`solidsOverlapping` are documented legacy bullet-view
  questions with no runtime consumer left. `TerrainRenderer` stopped drawing the rect list: it draws
  every authored block through `BlockRules.renderStyle`, one fill pass and one outline pass, each
  switching over all five `BlockRenderStyle` values (translucent glass pane with a sheen, closed
  door panel with its seam, platform slab with only its landing edge highlighted, and the two fills),
  so no block kind is named in core; the minimap's static geometry reads the same block list. New
  tests: `PlatformMotionTest` (the four one-way rules, stability on a slab, no horizontal collision,
  and crouch+jump still jumping on the wall-only arena), the movement cases in `MapQueryPurposeTest`
  (movement column, `surfaceBelow` per kind), the snapshot-layout case in `NetworkRegistrationTest`,
  and `MinimapModelTest`'s box count now counting authored blocks. `tools/scratch/static_check.py`
  gained the milestone's two gates, both read from the shared enums: no main source outside
  `shared/map` may name a block kind or test one in a `switch`, and every declared treatment must be
  drawn. No packet changed, the shipped arena stays wall-only and no runtime behaviour of a new kind
  can be observed yet; `docs/MAP_BUILDER_ACCEPTANCE.md` remains wholly unexecuted.
- **Held utility rendering correction.** The user-reported F1 reading showed slot 4 selected with a
  positive utility count and the pending press acknowledged. Source tracing found that the held
  weapon renderer passed utility wire ids (2000+) through `WeaponId.fromOrdinal`, whose defensive
  fallback is the default rifle; selection could therefore succeed while the player still visibly
  held a gun. Implemented: utility ids no longer request a gun sprite or barrel fallback, and the
  player renderer draws a small utility-colored hand marker. No wire or protocol change. The server
  throw refusal gates still need diagnostic surfacing, addressed in the following increment; no
  runtime throw or rendering behaviour was observed in this source-only pass.
- **Server-side loadout and utility refusal diagnostics.** Implemented: authoritative slot taps and
  utility trigger edges now retain the latest refusal reason per session, log it to the server
  console, and send a rate-limited warning to the affected client's existing command-response
  console. Typed utility results distinguish invalid/dead requests, the active-throwable cap,
  out-of-arena or blocked release points, a blocked eye-to-release path and blocked placed-device
  positions; the loadout path also reports empty/invalid slots, stun/surveillance/shield locks,
  missing utility state and cooldown remaining. `/loadout` dumps the caller's live
  `PlayerLoadout.toString()`, latest input sequence/edge and processed sequence, active slot, life
  state and latest refusal. It uses the already-registered `PacketCommandResponse`; no packet layout,
  `NetworkRegistration` entry or protocol version changed. Server tests were added for typed cap
  refusal, empty-slot notification, cooldown refusal and the live dump, but were not executed.
  This gives the next human desktop run evidence for a server rejection; it does not establish that
  the user's actual throw was accepted or identify which gate their runtime will hit. The confirmed
  gun-in-hand rendering fallback is separate and fixed above. Milestone-2 map work was already
  complete and was not duplicated.
- **Map building system, milestone 3 — breakables, glass and the SDF re-bake.** Implemented across
  nine commits on `arena/55c2317c-skystrike` (`0424577` `765f66d` `99a04b6` for the shared
  destruction layer, then the shared damage plumbing, the SDF re-bake path, the renderer, the server
  server tests and this documentation; PR open against `main`). The shared destruction layer is
  `shared/map/MaterialTable` (one immutable `Profile` per `Material`: durability pool, per-source
  retention for `BULLET`/`BLAST`/`MELEE`, tint and line colours as `0xRRGGBBAA`, impact effect, and
  the impact recording derived through the existing `EffectSoundTable`), `DamageSource`/`DamageRule`
  (`INDESTRUCTIBLE`/`DURABILITY`/`SHATTER`, decided by the kind, never by the material),
  `MapBlock.durabilityMultiplier` with the authored `durability=<x>` attribute in the version-1 map
  text (hashed only when non-default, so the shipped arena's hash and SDF asset are unchanged), and
  `MapDamageState`: the per-round authority that accumulates damage, removes a block from a derived
  live map when the pool is spent (or on any accepted hit to a `SHATTER` block), bumps the revision,
  reports `BlockBreak`, and `reset()`s to the authored map. Damage sources are wired end to end: a
  round's path is walked nearest-first so a shattered pane or an opened breakable lets it fly on
  through at `CombatConfig.PASS_THROUGH_SPEED_LOSS_FRACTION` (12%) while a chip, a wall, a body or a
  device spends it; blasts resolve through `ExplosionMath`/`DirectionalBlastMath` at each block's
  nearest face with self-exclusion by rectangle; a swing measures to the nearest point inside its
  arc; and `DroneSystem`/`CameraSystem`/`FuelTankSystem` follow the live map like the rest.
  `GameServer` owns the one `MapDamageState`, wires it into all six systems, and exposes
  `damageState()` as the per-round reset hook. The client half is the SDF re-bake path: `SdfField
  .diff` reports the changed texel region, `SdfRebakeWorker` bakes off the frame (one busy flap,
  newest map wins), and `SdfTexture.patch` uploads only that region and re-supplies the field to the
  particle systems that hold it. `TerrainRenderer` takes an optional `MapDamageState`, draws material
  colours from the shared rows and cracks a breakable in proportion to its damage fraction.
  **No packet, registration or `PROTOCOL_VERSION` change** — protocol stays 13 — because the client
  is not told about a destroyed block until milestone 4's block event, which is also why nothing
  requests a re-bake at runtime yet; the shipped arena is still wall-only and cannot lose a block.
  Tests were written and not executed: `MaterialTableTest`, `MapDamageStateTest`, `SdfRebakeTest`
  (the determinism gate: two bakes byte-identical, a break's re-bake reproducing exactly, and the
  changed region reproducing the re-bake when copied), `EffectTypeTest` for the appended
  `GLASS_SHATTER`, the `BlockRulesTest`/`ArenaMapFormatTest`/`ExplosionMathTest`/
  `DirectionalBlastMathTest` additions, and the server `BlockDestructionTest`/
  `UtilityBlockDestructionTest`. Milestone-1/2 work was not redone and milestones 4–6 were not
  started.
- **Map building system, milestone 3, shipped-arena fixture range (supersedes the "still wall-only"
  statements above).** Source-checked; requested by the user so the milestone can be tried in a
  running build instead of only in authored test rooms. `StandardArenaMap.TEXT` gained one mirrored
  fixture range high over the centre — `platform wood 1620 1300 220 18`, `glass glass 1660 1318 20
  180`, `breakable wood 1740 1318 60 60 durability=0.4`, mirrored to x 1160..1380 — so the arena now
  carries 62 blocks (WALL 56, GLASS 2, BREAKABLE 2, PLATFORM 2; STONE 56, WOOD 4, GLASS 2) and
  `assets/data/arena.sdf` was re-baked in the same commit (41,576 of 1,500,000 texels changed, header
  hash `bef76535…6693`), because a shipping-default client refuses a field that does not name the map
  in play. `ArenaMapFormatTest`'s census, solid list and hash pin; `MapDamageStateTest`'s resting-state
  test and class note; and an `ExplosionMathTest` case at the range's height (a frag there takes both
  panes and only chips the crates) were updated, and the "wall-only" wording the change falsified was
  swept out of nine files in its own commit. A separate commit fixes the ambiguous
  `hasLineOfSight(null, …)` call a Windows build reported in `VisionMathTest:287` — pre-existing on
  `main` from the #51 merge — by casting to `ArenaMap`. Exactly the permitted checks were run:
  `static_check.py` (clean: 419 Java files, 419 declared types), `static_api_check.py` (clean: 520
  project types from 415 files), `static_audio_check.py` (clean: 13 world entries, 6 weapon events, 1
  loop, 14 effect types, 30 manifest entries), `synth_sfx.py --check` (clean: 14 sounds agree),
  `phase3_ballistics.py` (completed), `tools/weapons/generate_weapon_tables.py` (89 gun and 21 melee
  rows regenerated, no tracked diff), plus the new `tools/scratch/arena_sdf_check.py` — a
  dependency-free re-implementation of `MapHash`, the `SdfBaker` rasterisation, its 8SSEDT pass,
  `Math.round`/float32 rounding and the 60-byte version-2 header, validated by reproducing the
  pre-change asset byte-for-byte before it re-baked the new one, and now reporting the committed asset
  as the bake of the committed map. No Java was compiled, no test, server, client, game or CI run was
  performed or observed, and no acceptance box was ticked.
- **Map building system, milestone 3 — first live run triaged (F2 fixed in source; F1/F3/F4 traced to
  a stale-process explanation and instrumented).** The user's desktop run of the fixture range
  reported four findings; the user's answers established that all of them happened on a plain,
  non-debug body reached by jetpack, with both sides believed rebuilt. **F2 (drop-through on S
  alone)** is a deliberate change, landed as `af255ea` on this session's branch: the drop trigger in
  `PlayerMotion` moved from the crouch+jump chord to a *fresh* crouch press — `input.crouch` with the
  body not already crouched when the sample arrived, an edge derived from simulated state so no
  packet, registration or `PROTOCOL_VERSION` change — with the documented consequence that a crouch
  cannot be *started* while standing on a one-way top (crouch-walking onto one with the key already
  held still works, and a held key never re-drops), a jump press while crouched on a slab is
  deliberately inert, and a jump press riding the drop's sample is consumed by the drop.
  `PlatformMotionTest` renamed the chord test to the press test, kept the ground case, and added
  three (held-crouch landing does not drop and a re-press does; jump while crouched on a slab is
  inert; a jump riding the drop sample is consumed). D6 and the plan's crouch+jump wording were
  amended, the acceptance box reworded (still unchecked), and the client's control help line now reads
  "S crouch/drop". **F1 (pane ignores bullets), F3 (body passes the pane) and F4 (body passes the
  crate)** were then traced through every consumer of the current source — `BlockRules` (GLASS and
  BREAKABLE are `MovementRule.SOLID`), both derived views, `MapDamageState.liveMap()`, `PlayerMotion`'s
  sweep, the muzzle guard, `RaycastBulletSystem.resolve`/`damageableCrossings`,
  `BulletSystem.resolvePath` and `MaterialTable.takesDamage` — and none of them can be produced by
  this source, while the fixture platform, parsed from the same three map lines, held the body in
  that same run. The one remaining explanation is two processes on different maps, and the local-test
  path can produce it silently: when the embedded host fails to bind because a leftover older server
  process owns the default ports, `Main.startLocalTest` logs an error and joins that server anyway —
  a pre-#52 server runs a wall-only map, which is exactly the observed behaviour. Landed as `18826e6`:
  `ArenaMap.describe()` (census by kind, mirror axis, map hash — computed inside `shared/map`, the
  one package that may name a kind), announced by `GameServer` at startup, logged by `GameScreen` at
  construction, with a short map line in the client debug panel and a shared test pinning the line.
  No packet, registration or protocol change; no Java compiled or executed here; the re-run is
  pending a human.
- **Map building system, milestone 4's destruction half — protocol 14 reaches the client.**
  Implemented in four code commits on `arena/cb494bfd-skystrike` plus this documentation. The
  user's rebuilt run had confirmed the server side (destruction effects firing proves the
  server-side path end to end) and reported the deliberate gap: a broken pane or crate keeps
  rendering and keeps blocking the client's player and rounds. The fix is plan §8's destruction
  half and nothing else from milestone 4. The block's wire identity is its authored index — both
  sides build the identical authored list, so an index names a block without carrying the triple,
  and only against the map hash the join states. `MapDamageState` gained the ordered break
  history the server diffs, the public index lookup, and `applyRemoteBreak`, the mirror's only
  mutation: it records the packet's own break without measuring anything, idempotently, and
  refuses out-of-range indices instead of throwing. The wire is `PacketBlockBreak` (tick, index,
  cause, position, shattered flag, revision), broadcast reliably in destruction order on the
  destruction tick; the join accept carries the authored map hash, the revision and the round's
  history as the same records the live path carries; the snapshot carries the advisory revision.
  `PROTOCOL_VERSION` moved 13 → 14 in the same commit as the first packet, with `DamageSource`
  and `PacketBlockBreak` appended at indices 35–36 (37 types). The client holds a
  `MapDamageState` mirror: the join baseline resets it after the map-hash check refuses a foreign
  index set, live breaks feed the same path (breaks arriving before the play screen exists are
  buffered and replayed after the baseline), and every change re-points the minimap and calls
  `FxPipeline.rebakeSdf` — the path's first caller. Prediction (predict and reconcile), the
  piloted drone, the grenade preview, the player-light gate and the audio occlusion all read the
  live map; the debug map line shows both revisions. Deliberately deferred with the door slice:
  doors and the occluder array, the server-side map-hash join rejection, per-hit crack sync
  (chips do not travel — a breakable reads pristine until the hit that opens it) and the
  round-reset event. Tests were written and not executed: the `MapDamageStateTest` index/history/
  mirror cases, the `NetworkRegistrationTest` pins and round trips, and the `MinimapModelTest`
  re-pointing case. No Java was compiled, executed or observed here; the desktop re-run —
  rebuild both sides, confirm the identity lines, shoot the pane and the crate, walk and shoot
  through where they stood, confirm the re-bake and the late joiner, press S on the platform —
  is outstanding human work, and every acceptance box remains unchecked.
- **Map building system, milestone 4's door half — the use key, server authority, protocol 15
  and the occluder array.** Implemented on `arena/a395c41c-skystrike` plus this documentation,
  on top of PR #55's authored-data and shared-machine/live-map halves. The use key is its own
  binding (default `F`; `E` is the E-gadget slot end to end), and its press rides
  `PacketPlayerInput` as the fourth birth-sequenced edge (`usePress`/`usePressSeq`, deduped by
  the same one-shot latch in `PlayerSession` the other three use). The client predicts the press
  with the shared resolution (`MapQueries.doorAt`, `DoorConfig.USE_REACH`) and machine
  (`MapDoorState.requestUse`) on its own door-clock estimate — it latches the edge even when the
  prediction refuses, because the authority is the only voice a refusal is allowed to have, and
  the server answers with a console warning and, for `REFUSED_BLOCKED`, an authoritative
  correction record. `server/world/DoorSystem` consumes every press each tick (refused or not)
  and advances the machine against the true occupancy: every live player hitbox counts, a dead
  one counts nothing, and every thrown utility counts by its position point. The wire is
  `PacketDoorState` (dispatch tick, authored door id, state ordinal, opening tick), broadcast
  reliably per change right after the tick's door step and replayed on the join accept as the
  every-door baseline; the state rides as a plain int with the defensive `DoorState.fromOrdinal`
  (fails shut to `CLOSED`), the enum deliberately off the registered list. `PROTOCOL_VERSION`
  14 → 15 in the same commit as the first packet, with `PacketDoorState` appended at index 37
  (38 types). Doors occlude without touching the SDF: `DoorOccluders` — the rectangle
  counterpart of `SmokeVolumes`'s circle array — feeds `u_doorRects` in both
  `visibility_cone.frag` and `light_radial.frag`, uploaded once per pass from exactly the doors
  whose state still blocks, and `TerrainRenderer` draws every door from the destruction-scarred
  static map posed by `swingFraction`, so an open door never blinks out of the picture while the
  SDF keeps naming the destruction-only `staticMap()`. Deliberately deferred: the server-side
  map-hash join rejection, per-hit crack sync and the round-reset wire event. Tests were written
  and not executed: the server `DoorSystemTest` (reach, gates, team rule, body/utility
  occupancy, blocked-swing reopen, press-during-swing), the `MapDamageStateTest` per-column
  closed-vs-open case, the `NetworkRegistrationTest` protocol-15 pins and round trips, and the
  `PlayerSessionTest` use-edge latch/dedupe cases. No Java was compiled, executed or observed
  here; the desktop re-run is outstanding human work — and the door boxes in the acceptance
  checklist additionally stay unreachable until a door is authored into a playable map (the
  shipped arena has none, by decision the fixture range was not extended for this slice).
- **Map building system, milestone 4's close-out, slice A — the map-hash join gate (protocol 16).**
  Implemented on `arena/b24b8b2c-skystrike`, on top of the door slice. The handshake's map guard was
  half a guard: the accept stated the map and the client verified it before touching its mirror,
  which turns a stale-process mismatch into a loud client error but lets the mismatched join
  complete first. Now the join request states the client's own hash (`PacketJoinRequest.mapHashHex`,
  empty meaning "unstated" and never a wildcard) and `JoinRequestHandler` refuses anything but the
  map it is running before it allocates a slot, a session or a spawn; the refusal is
  `REASON_MAP_MISMATCH` and the packet carries the server's hash, so the client's status line has
  both sides to compare. The decision is one pure function (`mapRefusal`) because the handler
  around it needs a socket while the decision is the whole risk, and the handler logs both hashes
  for the host problem the guard was written for. `PROTOCOL_VERSION` 15 → 16 in the same commit:
  both packets changed shape and no registration was appended, so the list stays at 38 and the
  newest protocol test owns the exact size pin. Tests were written and not executed: the shared
  handshake round trips, the size/version pin, and the server-side gate cases (admitted, refused
  with the server's map named, unstated refused, and a line-shuffled copy admitted as the same
  map). No Java was compiled, executed or observed here; the close-out's remaining slices —
  per-hit crack sync and the round-reset event — are still open.
- **Map building system, milestone 4's close-out, slice B — per-hit crack sync (protocol 17).**
  Implemented on `arena/b24b8b2c-skystrike`, on top of slice A. Breaks already travelled; chips did
  not, so a breakable a client had been shooting still read pristine until the hit that opened it.
  `MapDamageState` now records every non-lethal hit as a `BlockChip` — the block and the damage it
  has accumulated once that hit landed — beside the break history and disjoint from it (a hit that
  takes the block out is a break, and the two records never both exist for one hit). The record
  carries the authority's running total rather than the hit's size, so it is idempotent and
  order-tolerant and no fraction has to travel: the overlay's fraction follows from the total
  through the material row both sides read. `PacketBlockDamage` (tick, authored index, damage) is
  appended after `PacketDoorState` and broadcast reliably per chip from the same tail-diff pattern
  the breaks use, and the join accept carries the crack baseline — one record per standing damaged
  block, its current total, not the hits, so a joiner's picture lands cracked without a re-bake.
  The mirror applies totals (max-wins; out-of-range, destroyed, NaN and non-positive records are
  ignored) and clears them with `reset()`, buffered across the join window like the breaks; a chip
  changes no rule view, no live map and no SDF, so nothing downstream of the destruction set is
  refreshed. `PROTOCOL_VERSION` 16 → 17 in the same commit: `PacketBlockDamage` at index 38 (39
  types), pinned by the newest protocol test. Tests were written and not executed: the
  chip/total/baseline cases in `MapDamageStateTest`, the bullet-path chip case in
  `BlockDestructionTest`, and the registration round trips. No Java was compiled, executed or
  observed here; the close-out's remaining slice — the round-reset event — is still open.
- **Map building system, milestone 4's close-out, slice C — the round-reset event and `/mapreset`
  (protocol 18).** Implemented on `arena/b24b8b2c-skystrike`, on top of slice B. Plan §11.2 decided
  destruction resets per round, but nothing called `reset()` in a running server and nothing told a
  client when a reset happened, so a client kept playing the chewed map while the server had already
  put the walls back. `PacketRoundReset` (dispatch tick, the authority's restored count) is appended
  after `PacketBlockDamage` and broadcast reliably by `GameServer`'s reset transaction — one
  transaction on purpose: the destruction state and the door machine it owns come back together,
  the door diff baseline is re-seeded to the state every client just received (else the next press
  would look like no change and never be broadcast), and the event goes out before the next tick can
  reference the restored map. The two history counters need no help: both dispatch diffs follow a
  history that shrank. The event states an instruction rather than the restored set, because the
  authored map and the door machine are identical on both sides and `reset()` is the same call on
  both — so nothing per-block travels and the receiving end cannot misapply it; the client runs that
  same reset and refreshes the minimap and the SDF unconditionally, and the record is replayed
  across the join window like the others. `/mapreset` (ADMIN, in the shared catalog) is the
  operator's hand on the round layer that does not exist yet, handled through a new `MapReset`
  component on `ServerCommandModule.Deps` so the command stays a keyboard rather than a second
  simulation path — that component is why both `Deps` call sites in `ServerCommandServiceTest` had
  to grow an argument. `PROTOCOL_VERSION` 17 → 18 in the same commit: `PacketRoundReset` at index 39
  (40 types), pinned by the newest protocol test. Tests were written and not executed: the shared
  round trip and pin, and the command-layer cases (transaction handed over once, the answer's count,
  and the ADMIN gate keeping a refused command away from the simulation). No Java was compiled,
  executed or observed here.
- **Map building system, milestone 4's close-out, slice D — the fixture door, and the arena re-baked
  under it.** Implemented on `arena/b24b8b2c-skystrike`, on top of slice C. The door half landed at
  protocol 15, but the shipped arena authored no door, so the acceptance checklist's door boxes had
  nothing in a running build to open — with the editor still milestone 5, the shipped arena is the
  only map there is. It now authors one mirrored metal door — ids 1 and 2, both `ALL`, sealing the
  ground lane between the tall wall at `x` 1010..1034 and the shorter one at 1070..1092 (the pair
  stands at 1034..1070 and 1930..1966, `y` 100..270, and a shut panel is 170 tall against a jump
  rise of about 110, so it seals the gap to movement and not only to sight). No protocol change came
  with it: doors have been stateful and broadcast since protocol 15, so the pair simply arrives as
  two more entries in the join's door baseline. The map's identity moved, so `assets/data/arena.sdf`
  was re-baked in the same commit — a door is a dynamic occluder, not baked geometry, so every texel
  stayed and only the header's hash moved (`bef76535…6693` → `686c9165…a597`, still 1500 × 1000
  texels at 2.0 units/texel and 1,500,060 bytes) — and the identity pins followed: the hash literal
  and the describe line in `ArenaMapFormatTest` (62 → 64 blocks, `DOOR 2`), its hand-written bullet
  view (a shut panel stops a bullet where the pane beside the crate does not), `ArenaMapTest`'s new
  pin of the pair, and the wire tests' sample hash. Since the shipped arena is no longer doorless,
  `MapDoorStateTest`'s empty case authors its own doorless map instead of borrowing `standard()`.
  `tools/scratch/arena_sdf_check.py` learned the door grammar the Java format enforces (`id=`
  required on a door and refused elsewhere, `access=ALL|TEAM<n>` case-insensitively, duplicate ids
  refused, `durability=` only on a kind that pools it and positive), `DoorSpec.reflection()`'s
  next-id rule and the door triple in the hash's sort key and canonical row, and re-baked the asset
  here, where no JDK can run `SdfBakeTool`. Tests were written and not executed: the census,
  describe, hash and solids pins, the fixture-door pin, and the doorless-map case. No Java was
  compiled, executed or observed here; the close-out's four slices have all landed, and every
  acceptance box remains unchecked.
- **Map building system, milestone 4's close-out, slice E — the fixture door moved out of the sealed
  alcove.** The door pair that sat inside the sealed alcove at `x` 1034..1070 (and its mirror at
  1930..1966, `y` 100..270) has been moved to a reachable position inside the 70-wide mirrored wall
  at `x` 820..890 — the door now stands at `x` 837..873 and its mirror at 2127..2163, `y` 100..270,
  centred in that wall so a body can reach the door's own stances from ground level on either side and
  through the door's upper stances from a jump off the ground below the wall. The map text changed, so
  `assets/data/arena.sdf` was re-baked in the same commit under the new map hash
  `ed6b335d...e173e` (still 1500 × 1000 texels at 2.0 units/texel, 1,500,060 bytes) and
  `arena_sdf_check.py` re-confirmed the committed asset is the bake of the committed map. The door
  move does **not** eliminate the 10 unreachable stances the CI gate reports for the shipped arena:
  those stances sit inside the volume the tall wall at `x` 1010..1034 and the shorter one at 1070..1092
  enclose, and they are blocked by that wall geometry, not by the door — the door is read as an opening
  by both the Java gate and the Python mirror, so it never blocks anything. They persist at their old
  positions (`x` 1052.5 @ 100 and `x` 1072.5..1087.5 @ 270, plus mirrors), the test pins that name
  them by those coordinates still hold, and the test pin that names the fixture alcove by its old `x`
  range likewise still holds because the unreachable stances have not moved. What the move does
  accomplish is that the door itself is now reachable: the door's stances at `x` 837.5 (`y` 236, 518,
  918) are all reachable from both spawns, so a player can walk to the door and press `F`, which is
  what the §Doors boxes of the acceptance checklist need. The team-refusal box stays unreachable for
  the same reason it was before — no `TEAM` door is authored. `PROTOCOL_VERSION` stays 18 — no wire
  change, same as the fixture door slice. Tests were written and not executed: `ArenaMapTest`'s door
  pin was updated to the new position, `MapAuditTest`'s shipped-arena pins and `ArenaReachabilityTest`'s
  fixture-alcove pin still hold because the unreachable stances have not moved (the test comment was
  updated to say so), and the seven permitted Python checks were run clean before the commit (static
  check 442 files / 442 types, no findings; static API check 552 types from 438 files, no findings;
  audio 13 world entries, 6 weapon events, 1 loop, 14 effect types, 30 manifest entries; 14 generated
  sounds agree; ballistics report clean; 89 guns / 21 melee; arena_sdf `ed6b335d...e173e`, 1500×1000
  texels @ 2.0, 1,500,060 bytes, the shipped asset is the bake of the committed map). Nothing was
  compiled, executed or observed: no JDK in this sandbox.

---

## 7. Verification note

No Java was compiled or executed locally while producing this audit — there is no JDK in the
authoring sandbox. Everything above is a source-tree and documentation comparison. Compilation and
test status come only from GitHub Actions.

The same limit applies to the rebaseline pass at `a293f80`: it re-read the source and ran the
permitted Python checks (`tools/scratch/static_check.py`, `static_api_check.py`, both clean; the
module-boundary rules find zero violations), and it did not run the game, the server, Gradle, the
test suites or any acceptance checklist. The map-model, format and SDF-identity increments likewise
ran only `tools/scratch/static_check.py`, `static_api_check.py`, `static_audio_check.py`,
`synth_sfx.py --check` and `phase3_ballistics.py` (all clean), plus the weapon-table generator
(which left no tracked diff); it did not compile or execute its new shared tests. The map-building
rule-query increment ran that same set (`static_check.py` clean over 405 Java files,
`static_api_check.py` clean over 502 indexed project types, `static_audio_check.py`,
`synth_sfx.py --check` and `phase3_ballistics.py` clean, and the weapon-table generator left no
tracked diff); it did not execute the shared `MapQueryPurposeTest`/`VisionMathTest` additions or the
new `server/src/test` `RaycastBulletSystemTest`, and it observed no runtime map query. The
movement/rendering increment (milestone 2 complete) ran that same set again — `static_check.py`
clean over 406 Java files with the two new consumer gates, `static_api_check.py` clean over 503
indexed project types from 402 files, `static_audio_check.py`, `synth_sfx.py --check` and
`phase3_ballistics.py` clean, and the weapon-table generator left no tracked diff — and it verified
that the new gates fire by temporarily introducing a kind-naming consumer and by deleting a
treatment from the renderer, then reverting both. It did not execute `PlatformMotionTest`, the
`MapQueryPurposeTest`/`NetworkRegistrationTest`/`MinimapModelTest` additions, or any one-way
movement step, and it did not compile or run the terrain renderer. **Every
runtime
checklist referenced by §6 — M6 through M14, `DEBUG_LOCAL_TEST.md`,
`VISION_AUDIO_DRONE_ACCEPTANCE.md`, the mechanics §11 feel checklist and this pass's
`PLAYER_LIGHT_ACCEPTANCE.md`, `MUZZLE_GEOMETRY_ACCEPTANCE.md`,
`CROSSHAIR_POINTER_ACCEPTANCE.md` and `MAP_BUILDER_ACCEPTANCE.md` — is still unexecuted. The map
checklist's format/model, rule-aware line-query, movement-column and rendering sources are now
present, but all desktop validation and its later-milestone items remain pending — the milestone-3
fixture range means a one-way platform, a pane and a crate per side can now be met in a runnable
build, while a door, a saved map file, the editor and a client-side re-bake still cannot, and
`MAP_BUILDER_ACCEPTANCE.md` gained a Rendering section that is likewise unticked; no checkbox in any
of them may be ticked from source reading alone.**

The shipped-arena fixture-range increment ran the same permitted set plus the new
`tools/scratch/arena_sdf_check.py`, which re-implements the bake and the map hash in Python and was
validated against the pre-change asset before it re-baked the new one; it compiled and executed
nothing, and §6 above records the detail. The one-line `VisionMathTest` disambiguation in the same
increment is read from the compiler error a Windows build reported, not from a build run here.

The held-utility rendering correction was checked only with `python3 tools/scratch/static_check.py`
(clean: 406 Java files, no findings), `python3 tools/scratch/static_api_check.py` (clean: 503
project types indexed from 402 files), `python3 tools/scratch/static_audio_check.py` (clean),
`python3 tools/scratch/synth_sfx.py --check` (clean: 13 generated sounds agree),
`python3 tools/scratch/phase3_ballistics.py` (analytical report completed), and
`python3 tools/weapons/generate_weapon_tables.py` (completed with no tracked generated-table diff).
No Java, test suite, server, game, desktop build or CI was run or observed; no map acceptance item
was executed.

The map-building milestone-3 increment (breakables, glass, the destruction state and the SDF
re-bake path) was source-checked with exactly the permitted Python checks: `python3
tools/scratch/static_check.py` (clean: 419 Java files scanned, 419 declared types, no findings —
including the module boundaries and the two milestone-2 consumer gates), `python3
tools/scratch/static_api_check.py` (clean: 520 project types indexed from 415 files),
`static_audio_check.py` (clean: 13 world entries, 6 weapon events, 1 loop, 14 effect types, 30
manifest entries), `synth_sfx.py --check` (clean: 14 generated sounds agree), `phase3_ballistics.py`
(analytical report completed) and `tools/weapons/generate_weapon_tables.py` (89 gun rows and 21 melee
rows regenerated with no tracked diff). `static_api_check.py` cannot see an instance call made
through a field or a variable, so the new cross-module calls were additionally read by hand. A
one-off analysis script (not one of the standing six checks) re-implemented `SdfBaker` in Python
from the committed source and reproduced every texel of `assets/data/arena.sdf`, and re-implemented
`MapHash` to confirm that the shipped asset's header still names the standard arena now that
`MapBlock` carries a durability multiplier. No Java was compiled, no test, server, client, game or
desktop build was run or observed, and no `MAP_BUILDER_ACCEPTANCE.md` box was ticked: the
milestone's tests (`MaterialTableTest`, `MapDamageStateTest`, `SdfRebakeTest`, `BlockDestructionTest`,
`UtilityBlockDestructionTest`) and its client-side re-bake wiring both await a human with a JDK, and
the shipped wall-only arena still cannot exercise a pane, a crate or a shatter.

The server-side loadout and utility refusal diagnostics increment was then source-checked with
exactly the permitted Python checks: `python3 tools/scratch/static_check.py` (clean: 406 Java files,
no findings), `python3 tools/scratch/static_api_check.py` (clean: 505 project types indexed from 402
files), `python3 tools/scratch/static_audio_check.py` (clean: catalogue paths, formats and durations
agree), `python3 tools/scratch/synth_sfx.py --check` (clean: 13 generated sounds agree),
`python3 tools/scratch/phase3_ballistics.py` (analytical report completed), and
`python3 tools/weapons/generate_weapon_tables.py` (completed with no tracked generated-table diff).
The new server tests were not executed; no Java compilation, server/game run, desktop build, CI check
or human acceptance was performed. Every `MAP_BUILDER_ACCEPTANCE.md` checkbox remains unchecked.

The first-live-run triage increment (S-alone drop-through, plus the map-identity announcement)
was source-checked with exactly the permitted Python checks, run twice — once after the F2
change and once after the diagnostics: `python3 tools/scratch/static_check.py` (clean both
times: 419 Java files scanned, 419 declared types, no findings, including the module boundaries
and the two milestone-2 consumer gates), `python3 tools/scratch/static_api_check.py` (clean:
520 project types indexed from 415 files), `python3 tools/scratch/static_audio_check.py`
(clean: 13 world entries, 6 weapon events, 1 loop, 14 effect types, 30 manifest entries),
`python3 tools/scratch/synth_sfx.py --check` (clean: 14 generated sounds agree),
`python3 tools/scratch/phase3_ballistics.py` (analytical report completed without findings),
`python3 tools/weapons/generate_weapon_tables.py` (completed with no tracked generated-table
diff), and `python3 tools/scratch/arena_sdf_check.py` (clean: the committed
`assets/data/arena.sdf` is still the bake of the committed map — neither `StandardArenaMap.TEXT`
nor the asset changed this session). The triage's own evidence is source reading plus the user's
answers, not a run of anything here: the F1/F3/F4 path was traced hop by hop through
`BlockRules`, both `ArenaMap` view derivations, `MapDamageState`, `PlayerMotion`'s sweep, the
muzzle guard, `damageableCrossings`/`resolvePath` and `MaterialTable.takesDamage`, and the
local-test port-fallback join was read in `Main.startLocalTest`/`EmbeddedLocalHost`. Nothing was
compiled, executed or observed in this sandbox; the rewritten and added `PlatformMotionTest`
cases, the new `describeAnnouncesTheCensusMirrorAxisAndHash` test, and every earlier suite
remain unexecuted, and every `MAP_BUILDER_ACCEPTANCE.md` box remains unchecked.

The milestone-4 destruction increment (protocol 14: the block event, the join baseline, the
snapshot revision, the client mirror and the first re-bake caller) was source-checked with
exactly the permitted Python checks: `python3 tools/scratch/static_check.py` (clean: 420 Java
files scanned, 420 declared types, no findings, including the module boundaries and the two
milestone-2 consumer gates), `python3 tools/scratch/static_api_check.py` (clean: 521 project
types indexed from 416 files), `python3 tools/scratch/static_audio_check.py` (clean: 13 world
entries, 6 weapon events, 1 loop, 14 effect types, 30 manifest entries), `python3
tools/scratch/synth_sfx.py --check` (clean: 14 generated sounds agree), `python3
tools/scratch/phase3_ballistics.py` (analytical report completed without findings) and `python3
tools/weapons/generate_weapon_tables.py` (completed with no tracked generated-table diff).
`python3 tools/scratch/arena_sdf_check.py` was not run: neither `StandardArenaMap.TEXT` nor
`assets/data/arena.sdf` changed this session. `static_api_check.py` cannot see calls made
through fields or variables, so the session's cross-module calls were additionally read by
hand. Nothing was compiled, executed or observed in this sandbox: the new
`MapDamageStateTest`, `NetworkRegistrationTest` and `MinimapModelTest` cases and every earlier
suite remain unexecuted, the two-client destruction pass and the late-join check are outstanding
human work, and every `MAP_BUILDER_ACCEPTANCE.md` box remains unchecked.

The door slice of milestone 4 (protocol 15: the use key and its client-side door prediction,
the server's door authority with body/utility occupancy, the reliable door-state wire and join
baseline, and the dynamic door occluder array in the visibility and light shaders) was
source-checked with exactly the permitted Python checks: `python3 tools/scratch/static_check.py`
(clean: 431 Java files scanned, 431 declared types, no findings — including both consumer
gates), `python3 tools/scratch/static_api_check.py` (clean: 534 project types indexed from 427
files), `python3 tools/scratch/static_audio_check.py` (clean: 13 world entries, 6 weapon
events, 1 loop, 14 effect types, 30 manifest entries), `python3 tools/scratch/synth_sfx.py
--check` (clean: 14 generated sounds agree), `python3 tools/scratch/phase3_ballistics.py`
(analytical report completed without findings) and `python3 tools/weapons/generate_weapon_tables.py`
(completed with no tracked generated-table diff). `python3 tools/scratch/arena_sdf_check.py` was
run as belt-and-braces even though neither `StandardArenaMap.TEXT` nor `assets/data/arena.sdf`
changed this increment (clean: the shipped asset still agrees with the committed map on header
hash, header shape and every texel). `static_api_check.py` cannot see an instance call made
through a field or a variable, so the slice's cross-module calls were additionally read by
hand. No Java was compiled, no test, server, client, game or desktop build was run or observed,
and no `MAP_BUILDER_ACCEPTANCE.md` box was ticked: the new `DoorSystemTest`,
`MapDamageStateTest`, `NetworkRegistrationTest` and `PlayerSessionTest` cases and every earlier
suite await a human with a JDK, and the door boxes stay unreachable until a door is authored
into a playable map (the shipped arena has none, and by decision the fixture range was not
extended for this slice).

The milestone-4 close-out's first slice (protocol 16: the map-hash join gate — the client's stated
map in the join request, the server's refusal on a mismatch with its own hash attached, the empty
hash treated as an unstated map rather than a wildcard, and a registration list that stays at 38
because no type was appended) was source-checked with exactly the permitted Python checks:
`python3 tools/scratch/static_check.py` (clean: 432 Java files scanned, 432 declared types, no
findings — including both consumer gates), `python3 tools/scratch/static_api_check.py` (clean: 535
project types indexed from 428 files), `python3 tools/scratch/static_audio_check.py` (clean: 13
world entries, 6 weapon events, 1 loop, 14 effect types, 30 manifest entries), `python3
tools/scratch/synth_sfx.py --check` (clean: 14 generated sounds agree), `python3
tools/scratch/phase3_ballistics.py` (analytical report completed without findings), `python3
tools/weapons/generate_weapon_tables.py` (89 gun rows and 21 melee rows regenerated with no tracked
diff) and `python3 tools/scratch/arena_sdf_check.py` (clean: the shipped asset still agrees with the
committed map on header hash, header shape and every texel; neither changed this slice).
`static_api_check.py` cannot see an instance call made through a field or a variable, so the slice's
cross-module calls (`ArenaMap.standard().mapHashHex` on the client's join path,
`MapDamageState.authoredMap` in the gate's caller, the two packet schema changes and
`ClientSession`'s refusal-detail assembly) were additionally read by hand. No Java was compiled, no
test, server, client, game or desktop build was run or observed, and no
`MAP_BUILDER_ACCEPTANCE.md` box was ticked: the checklist has no box for the handshake, so the
guard's only pending human work is the staged-client check that watches the refusal fire.

The close-out's second slice (protocol 17: per-hit crack sync — `BlockChip` as the running-total
record, `PacketBlockDamage` appended at index 38 with 39 types, the reliable tail broadcast beside
the breaks, the per-block crack baseline on the join accept, and the max-wins mirror that clears
with the round) was source-checked with exactly the permitted Python checks: `python3
tools/scratch/static_check.py` (clean: 434 Java files scanned, 434 declared types, no findings —
including both consumer gates), `python3 tools/scratch/static_api_check.py` (clean: 537 project
types indexed from 430 files), `python3 tools/scratch/static_audio_check.py` (clean: 13 world
entries, 6 weapon events, 1 loop, 14 effect types, 30 manifest entries), `python3
tools/scratch/synth_sfx.py --check` (clean: 14 generated sounds agree), `python3
tools/scratch/phase3_ballistics.py` (analytical report completed without findings), `python3
tools/weapons/generate_weapon_tables.py` (89 gun rows and 21 melee rows regenerated with no tracked
diff) and `python3 tools/scratch/arena_sdf_check.py` (clean: the shipped asset still agrees with the
committed map on header hash, header shape and every texel; neither changed this slice).
`static_api_check.py` cannot see an instance call made through a field or a variable, so the slice's
cross-module calls (`MapDamageState.chips`/`damagedBlocks`/`applyRemoteDamage`, `GameServer`'s
`dispatchBlockChips`, `JoinRequestHandler`'s baseline assembly, `ClientSession`'s replay buffer and
`GameScreen`'s `onBlockDamage`) were additionally read by hand. No Java was compiled, no test,
server, client, game or desktop build was run or observed, and no `MAP_BUILDER_ACCEPTANCE.md` box
was ticked: the crack-state box is now attemptable in a build, but only a human with a desktop and
two clients can tick it.

The close-out's third slice (protocol 18: `PacketRoundReset` appended at index 39 with 40 types, the
one server-side reset transaction that puts the map and the doors back and re-seeds the door diff
baseline, the `MapReset` command seam behind `/mapreset`, and the client's unconditional refresh
with the join-window replay) was source-checked with exactly the permitted Python checks: `python3
tools/scratch/static_check.py` (clean: 435 Java files scanned, 435 declared types, no findings —
including both consumer gates), `python3 tools/scratch/static_api_check.py` (clean: 539 project
types indexed from 431 files; its arity check caught the `Deps` call sites that had to grow the new
component before they were fixed), `python3 tools/scratch/static_audio_check.py` (clean: 13 world
entries, 6 weapon events, 1 loop, 14 effect types, 30 manifest entries), `python3
tools/scratch/synth_sfx.py --check` (clean: 14 generated sounds agree), `python3
tools/scratch/phase3_ballistics.py` (analytical report completed without findings), `python3
tools/weapons/generate_weapon_tables.py` (89 gun rows and 21 melee rows regenerated with no tracked
diff) and `python3 tools/scratch/arena_sdf_check.py` (clean: the shipped asset still agrees with the
committed map on header hash, header shape and every texel; neither changed this slice).
`static_api_check.py` cannot see an instance call made through a field or a variable, so the slice's
cross-module calls (`GameServer.resetMap` through `MapDamageState.reset` and the door state's own
reset and revision bump, the re-seeded door baseline, the clock read for the event's tick,
`ServerCommandModule`'s new `MapReset` component, `ClientSession`'s replay buffer and
`GameScreen`'s `onRoundReset`) were additionally read by hand. No Java was compiled, no test,
server, client, game or desktop build was run or observed, and no `MAP_BUILDER_ACCEPTANCE.md` box
was ticked: the reset box is now attemptable through `/mapreset`, but only a human with a desktop
and two clients can tick it.

The close-out's last slice (protocol-free: the fixture door pair authored into
`StandardArenaMap.TEXT`, `assets/data/arena.sdf` re-baked in the same commit with the field unchanged
texel for texel and only the header's hash moving, the moved identity pins in `ArenaMapFormatTest`,
`ArenaMapTest` and `NetworkRegistrationTest`, the doorless-map case in `MapDoorStateTest`, and the
`arena_sdf_check.py` door grammar) was source-checked with exactly the permitted Python checks:
`python3 tools/scratch/static_check.py` (clean: 435 Java files scanned, 435 declared types, no
findings — including both consumer gates), `python3 tools/scratch/static_api_check.py` (clean: 539
project types indexed from 431 files), `python3 tools/scratch/static_audio_check.py` (clean: 13
world entries, 6 weapon events, 1 loop, 14 effect types, 30 manifest entries), `python3
tools/scratch/synth_sfx.py --check` (clean: 14 generated sounds agree), `python3
tools/scratch/phase3_ballistics.py` (analytical report completed without findings), `python3
tools/weapons/generate_weapon_tables.py` (89 gun rows and 21 melee rows regenerated with no tracked
diff) and `python3 tools/scratch/arena_sdf_check.py` (clean, and this is the slice where it matters:
it now parses the same door grammar the Java format enforces, re-baked the asset from the new map
text here, and then reported the committed asset as the bake of the committed map — header hash
`686c9165…a597`, 1500 × 1000 texels at 2.0 units/texel, 1,500,060 bytes, every texel equal, with
only the header's hash differing from the previous bake). `static_api_check.py` cannot see an
instance call made through a field or a variable, but this slice adds none — it is authored map text,
test pins, a tool and a binary — so the identity strings the pins hold
(`ArenaMapFormatTest`'s literal and describe assertions, `ArenaMapTest`'s door pair and
`NetworkRegistrationTest`'s sample hash) were additionally compared, by hand, against the checker's
own digest. No Java was compiled, no test, server, client, game or desktop build was run or observed,
and no `MAP_BUILDER_ACCEPTANCE.md` box was ticked: the fixture door makes the whole Doors section —
except the team-refusal box, which no authored door can reach — attemptable in a build, but only a
human with a desktop and two clients can tick it.

**Map milestone 5, slice A (the reachability gate and the editor's draft; PR #63)** —
implemented in source, with nothing deferred out of the slice and no wire change
(`PROTOCOL_VERSION` stays 18; the draft builds a map and cannot publish one). Map plan §7's gate
is now shared main source: `MapReachability` builds a walkable grid of *stances* — a column
where the shared standing box (`PlayerConfig.WIDTH` by `STAND_HEIGHT`) clears every block whose
`BlockRules` row is `MovementRule.SOLID`, and where a support really is under the feet the way
`MapQueries.surfaceBelow` reads one — with one deliberate difference: a door leaves both the
solids *and* the supports, because the map is searched in the state a player can always put it
in, which is open. Two manoeuvres connect stances — the M8 step (`JUMP_SPEED² / (2 · -GRAVITY)`
≈ 110.25 of rise over a 210-unit running gap, no fuel) and a jetpack hop (a rise 40% of a tank
buys, with the horizontal reach that ascent's flight time buys at walk speed, repeatable after
each landing) — and each passes through the body's own space in one of two shapes.
`Report.spawnsConnected()` is the hard requirement and `Report.fullyReachable()` is §7's strict
reading; both findings lists are complete rather than sampled, because a cap would have made the
editor's overlay reconstruct the pocket it has to draw, and there is deliberately no "is this
stance escapable without fuel" column: on a per-stance grid that question fires on ground a
player walks across, and a signal that noisy is worse than none. `MapDraft` is the editor's data
model: authored `mirrored`/`centred` verbs, so a block can never lose its twin and deleting
either twin deletes the verb; `build()` through `ArenaMap.Builder`, so the in-game editor and
the file parser cannot disagree about what a valid map is; and `isDirty()` by the map hash the
SDF header and the join gate already use. `ArenaReachabilityTest` was rewritten to ask the
shared gate — its M8 surface inventory is now a test of record against one statement of the
algorithm rather than a private copy of it — and `MapDraftTest` was added beside it.

The source-only mirror `tools/scratch/arena_reachability_check.py`, re-implemented from the
committed Java, is the evidence this environment can produce: the shipped arena at a 5-unit
pitch is 600 columns and 1526 stances, both spawns reach each other under either door reading,
and 10 stances are reachable from no spawn — `(1052.5, 100)`,
`1072.5`/`1077.5`/`1082.5`/`1087.5` at feet 270 and their mirrors, and `(1947.5, 100)` — all of
them inside the milestone-4 fixture door's alcove, which is what makes §11.4 a decision rather
than a defect. The probes the gate has to catch: an open map (clean), a 1160-tall wall (spawns
DISCONNECTED, 8 stances sealed), a shelf 1220 up (connected, 120 unreachable), the same shelf at
900 (clean) and a tunnel door — the format's real idiom, a wall held clear of the floor with the
panel filling its mouth — which stays connected while the door is an opening and goes
DISCONNECTED when a door is read as its movement row's solid panel. The slice was source-checked
with exactly the permitted Python checks: `python3 tools/scratch/static_check.py` (clean: 438
Java files scanned, 438 declared types, no findings — including both consumer gates), `python3
tools/scratch/static_api_check.py` (clean: 546 project types indexed from 434 files), `python3
tools/scratch/static_audio_check.py` (clean: 13 world entries, 6 weapon events, 1 loop, 14
effect types, 30 manifest entries), `python3 tools/scratch/synth_sfx.py --check` (clean: 14
generated sounds agree), `python3 tools/scratch/phase3_ballistics.py` (analytical report
completed without findings), `python3 tools/weapons/generate_weapon_tables.py` (89 gun rows and
21 melee rows regenerated with no tracked diff) and `python3 tools/scratch/arena_sdf_check.py`
(clean: 64 blocks, map hash `686c9165…a597`, 1500 × 1000 texels at 2.0 units/texel, 1,500,060
bytes, the shipped asset the bake of the committed map — no map text changed in this slice).
`static_api_check.py` cannot see an instance call made through a field or a variable, so the
slice's cross-module calls (`BlockRules.movement`, `ArenaMap.movementBlockers` and `Builder`'s
three verbs, `MapBlock.mirror` and `door`, `DoorSpec.reflection`, `Rect.mirror`,
`MapQueries.surfaceBelow`, the `PlayerConfig` and `WorldConfig` constants the envelope derives
from, `MapHash` through `ArenaMap.mapHashHex`, and `ArenaMapFormat.write`/`parse`) were
additionally read by hand. No Java was compiled, no test, server, client, game or desktop build
was run or observed, and no `MAP_BUILDER_ACCEPTANCE.md` box was ticked: the tests in this slice
were written and not executed, and the two decisions they lean on are surfaced in §11.3 (the
gate reads a door as an opening, which a `TEAM` door would turn into a lower bound) and §11.4
(the shipped arena fails the strict reading by one intended pocket, so slice B's CI step has to
know which reading fails a build) rather than settled here.

**Map milestone 5, slice B (the `tools` module, `MapTool check|bake` and CI's map gate)** —
implemented in source on `arena/78110530-skystrike`, on top of slice A (PR #63), with nothing
deferred out of the slice and no wire change (`PROTOCOL_VERSION` stays 18). **§11.4 is now
decided**, and the decision is what the CI step implements: the user chose, on 2026-10-12, that a
map whose spawns cannot reach each other **fails** the build and a map that is merely not fully
reachable **warns with the sealed pocket named**. The shipped arena is the second kind — 600
columns, 1526 stances at the gate's 5-unit pitch, both spawns reaching each other, ten stances
sealed inside the milestone-4 fixture door's alcove — so CI is green on it while still naming the
alcove, which slice A's evidence is what made a decision rather than a defect. The stricter
reading is not deleted, it is a value: `MapAudit.Reading.FULL` is §7's letter and `MapTool check
--strict` asks for it, so a human can put any map to the stricter question without a code change.
The choice lives in the shared core rather than in the CLI because the editor has to answer the
same question about the same map: `MapAudit.check(ArenaMap, label, Reading)` runs slice A's
`MapReachability.check`, maps its two booleans onto `PASS`/`WARN`/`FAIL` (spawns disconnected →
FAIL under either reading; not fully reachable → FAIL under `FULL`, WARN under `SPAWNS_ONLY`; no
spawn authored → WARN, because a spawnless map is a valid map that proves nothing), and *builds
the report lines itself* — `Result.lines()` — so a CI log and the editor's status area cannot say
two different things about one map. `Result.pocket()` hands the editor the gate's own complete
list of stances, never a sample. `MapAudit.bake(ArenaMap)` is the other half both surfaces share:
the field's bytes with its header, i.e. exactly what `data/<map>.sdf` holds, and
`MapAudit.bakeField(ArenaMap)` the field the in-game editor will draw and upload with.

**Where the task lives — decided.** `tools` is a real Gradle module (`include 'tools'`) rather
than a `JavaExec` task hung off `shared`: it depends on `shared` and nothing else, and
`checkModuleDependencies` now indexes it as an owner, forbids `tools` → `core`/`server` *and*
forbids every game module from reaching `tools` (the CLI is a consumer of the map model, never a
dependency of the game), and extends the LibGDX ban to it because the CLI is headless by
construction. Both source-only python checkers gained `tools` to their module lists — which is how
`tools/src/main/java/io/github/skystrike/tools/SdfBakeTool.java` stops being a file no build and no
check had ever read. `MapTool` itself is deliberately a shell: argument parsing, file I/O, printing
and an exit code (0 passed or warned, 1 failed/malformed/unwritable, 2 usage), with the verdict
mapping one line long, so a CI step cannot quietly change what a failed map means.
`MapTool check [--strict] [map.map ...]` audits file maps or, with no path, the shipped arena — the
embedded canonical text is the only map there is until the shipped-maps milestone moves it — and
`MapTool bake [map] [--out <path>]` writes the asset while `bake --check` re-bakes in memory and
compares the committed file byte for byte, naming which of the three differences it found: another
length, a header hash naming another map, or a texel region (its first rectangle, through
`SdfField.diff`). `SdfBakeTool` survives as the desktop one-button bake, now delegating to
`MapAudit`, so the tool, the CLI and CI share one bake implementation rather than three.

**CI.** Two steps were added after the compile step, which now also compiles `tools`:
`./gradlew tools:mapCheck --no-daemon` (the §7 reachability gate over the shipped map, as decided
above) and `./gradlew tools:mapBakeCheck --no-daemon` (`MapTool bake --check` against
`assets/data/arena.sdf`). Both are `JavaExec` tasks in `tools/build.gradle` with the repository root
as their working directory, so a CI step names a task and cannot drift from the class or its
arguments. Tests were written and not executed here: `MapAuditTest`, in `shared/src/test` where
the suite lives, pins both readings against the shipped arena (WARN with the exact ten-stance
alcove under `SPAWNS_ONLY`, FAIL under `FULL`, 1526 stances on 600 columns and 150 on foot / 1516
with fuel per spawn), the two failure modes a small authored map can show (a 1800-tall centred wall
disconnects the spawns under both readings; a sealed chamber — walls with a ceiling flush on them,
800 up — only warns), the spawnless-map warning and its own line, and the bake (deterministic
bytes, 1,500,060 bytes = a 60-byte header plus 1500 × 1000 texels, header hash matching the map it
names and not another). `static_api_check.py` cannot see an instance call made through a field or a
variable, so the slice's cross-module calls were hand-read against their declarations:
`MapReachability.check`, `Report.stanceCount()/columns()/spawns()/spawnsConnected()/fullyReachable()/describe()/unreachableFromEverySpawn()`,
`SpawnReport.teamIndex()/spawnX()/spawnY()/start()/onFoot()/withFuel()/unreachable()`,
`Stance.x()/feet()`, `SdfBaker.bake(ArenaMap)`,
`SdfField.HEADER_BYTES/width()/height()/texelScale()/mapHash()/write()/read()/diff()`,
`SdfDiff.x()/y()/width()/height()/texels()`, `MapHash.hex(byte[])` and
`ArenaMap.standard()/blocks()/spawns()/mirrorAxisX()/mapHashHex()`.

The slice was source-checked with exactly the permitted Python checks: `python3
tools/scratch/static_check.py` (clean: 442 Java files scanned, 442 declared types, no findings —
including both consumer gates, now covering `tools` too), `python3
tools/scratch/static_api_check.py` (clean: 552 project types indexed from 438 files),
`python3 tools/scratch/static_audio_check.py` (clean: 13 world entries, 6 weapon events, 1 loop,
14 effect types, 30 manifest entries), `python3 tools/scratch/synth_sfx.py --check` (clean: 14
generated sounds agree), `python3 tools/scratch/phase3_ballistics.py` (analytical report completed
without findings), `python3 tools/weapons/generate_weapon_tables.py` (89 gun rows and 21 melee
rows regenerated with no tracked diff) and `python3 tools/scratch/arena_sdf_check.py` (clean: 64
blocks, map hash `686c9165…a597`, 1500 × 1000 texels at 2.0 units/texel, 1,500,060 bytes, the
shipped asset the bake of the committed map — no map text changed in this slice). The source-only
mirror `tools/scratch/arena_reachability_check.py` was re-run for the numbers the new test pins and
reports them unchanged: 1526 stances on 600 columns, spawns connected under either door reading,
ten sealed stances listed by name, 150 stances on foot and 1516 with fuel for each spawn. No Java
was compiled, no test, server, client, game or desktop build was run or observed, and **no
`MAP_BUILDER_ACCEPTANCE.md` box was ticked**: the CI steps and the CLI have never executed, so
their first run in CI is also the first execution of `MapTool` and of the Java bake against the
committed bytes (the python mirror already reports that asset as the bake of that map).
