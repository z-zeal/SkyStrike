# Phase 7 Audit — HUD, Chat and Console

> Status snapshot taken at commit `bc081e9` (merge of PR #13, "Begin Phase 7 text foundation").
> Compares the repository against `IMPLEMENTATION_ROADMAP.md` Phase 7 and
> `CONSOLE_CHAT_PLAN.md` §11 build order / §12 definition of done.
>
> This document is deliberately blunt. Phase 7 is **not** complete, and two earlier phases were
> skipped entirely.

---

## 1. Headline findings

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

### 3.2 Phase 7 — the dialog (`CONSOLE_CHAT_PLAN.md` build order Phase 1)

Absent in full: `ConsoleDialog`, `ConsoleInputField`, `ConsoleFocus`, `ChatTargetButton`,
`CommandLineDetector`, `ConsoleCompletionPopup`, `ConsoleHintLine`, `ConsoleTheme`.
Consequences: no one-key open, no ALL/TEAM button, no `Tab` accelerator, no persistence of the
target choice, no draft preservation on `Escape`, no passive fade-out mode, no scrollback
scrolling, no input focus capture. `core/input/InputRouter` has the focus-stack hook the plan
needs, but nothing claims focus.

Build-order Phase 0's own gate — *"the four-background contrast test mode"* (black / white / grey /
noise) — is also missing, so the readability requirement is asserted but unverified.

### 3.3 Phase 7 — chat transport and server authority (build order Phase 2)

Absent: `ChatMessage`, `PacketChatRequest`, `PacketChatMessage`, `server/chat/ChatService`,
`ChatModeration`, `ChatHistory`, `core/chat/ChatClient`, `ChatMuteList`, `QuickChat`.
`TextSanitizer` and `RateLimiter` exist but **nothing calls them** — they are currently dead code
with tests.

### 3.4 Phase 7 — capabilities (build order Phase 3)

Absent: `shared/command/Permission`, `ConsoleAccess`, `PacketCapabilities`,
`server/command/PermissionResolver`, `CapabilityBroadcaster`, `core/command/ClientCapabilities`.
`ChatChannel.isVisibleTo(boolean)` takes the flag the server is supposed to push, but no packet
carries it.

### 3.5 Phase 7 — commands, completion, server commands, cvars, terminal, mobile

Build order Phases 4–9 are absent in full: no `shared/command/` package at all, no
`CommandSpec`/`ArgType`/`CommandParser`/`CommandRegistry`/`CommandContext`, no `help`/`clear`, no
completion or history, no `ServerCommandService`, no `Cvar`/`CvarRegistry`, no `ServerConsole`
stdin front-end, no soft-keyboard handling or quick-chat wheel.

### 3.6 Earlier phases skipped — Phase 5 and Phase 6

See §1. These are listed here because Phase 7's own HUD scope includes a *"gadget panel with
durability and cooldowns"*, which cannot be implemented against gadgets that do not exist, and
because Phase 8's effect catalogue is largely keyed to throwables.

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

---

## 4. Is Phase 7 complete enough to move to Phase 8?

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

| # | Deferral | Why it needs a decision |
| --- | --- | --- |
| D1 | Phase 5 (throwables) and Phase 6 (gadgets) skipped | Half the Phase 8 effect catalogue and the Phase 7 gadget panel depend on them |
| D2 | Phase 7 HUD (health/fuel, loadout bar, crosshair, minimap, kill feed, damage numbers, vignette) | Large client-only slice; cannot be verified in a JDK-less sandbox, only compiled |
| D3 | Phase 7 dialog (one-key chat, ALL/TEAM button, focus capture, passive mode) | Same — interactive, needs a runnable client to gate |
| D4 | Phase 7 command core, completion/history, server commands, cvars, server terminal | Build-order Phases 4–8; large, but mostly plain-Java and CI-verifiable |
| D5 | Effects-plan Phase 0 infrastructure not yet built | Should precede any further Phase 8 work |

---

## 6. Progress since this audit

| Slice | State |
| --- | --- |
| Console plan build-order Phase 2 — chat transport and server authority | Implemented: `ChatMessage`, `PacketChatRequest`, `PacketChatMessage`, `server/chat/{ChatService,ChatModeration,ChatHistory}`, `ChatRequestHandler`, `core/chat/{ChatClient,ChatMuteList}` |
| Console plan build-order Phase 3 — capabilities | Implemented: `shared/command/{Permission,ConsoleAccess}`, `PacketCapabilities`, `server/command/{PermissionResolver,CapabilityBroadcaster}`, `core/command/ClientCapabilities` |
| Phase 5 increment 1 — shared throwable foundation | Implemented: `shared/config/UtilityConfig`, `shared/utility/{UtilityId,DetonationMode,UtilityEffect,UtilityDefinition,UtilityRegistry,ThrowablePhysics,ExplosionMath,StunMath}`, `shared/model/ThrownUtility` |
| Phase 5 increment 2A — utility inventory and wire contract | Implemented: populated loadout slots 4–5 with carried counts and respawn refill; utility choices added to the server-authoritative loadout request; `ThrownUtility` appended at Kryo index 25 and added to `PacketGameState` |

Phase 5 is under way, so §3.6's "never started" now applies to Phase 6 only. What landed is the
shared half: identity and wire encoding (2000 + ordinal, a third range in the existing `weaponId`
int), the mechanics §6 catalogue, the one substepped bouncing integrator the server and the
trajectory preview will both call, absolute terrain occlusion for blasts, and stun banding.
Increment 2 now has its contract slice: loadout slots 4–5 carry utility identity and authoritative
per-life counts, depleted slots are skipped, utility composition changes remain respawn-only, and
`ThrownUtility` is append-only wire state at Kryo index 25 inside `PacketGameState`. Not yet
present: the actual `server/utility/` throw/flight/detonation lifecycle, smoke volumes wired into
the visibility pass, molotov surface spread, claymore placement, and
`core/render/TrajectoryRenderer`. Claymore's numbers and the damage-over-time tick interval are
marked provisional in code, because the plan leaves them blank.

`TextSanitizer` and `RateLimiter` are no longer dead code — the relay is their only caller.
`PROTOCOL_VERSION` moved 4 → 5 for chat/capabilities and 5 → 6 for throwable state and utility
loadouts. §3.3 and §3.4 above describe the state *before* this work; the rest of §3 still stands.

Still outstanding for Phase 7: the HUD in full (§3.1), the dialog in full (§3.2), and build-order
Phases 4–9 (§3.5).

---

## 7. Verification note

No Java was compiled or executed locally while producing this audit — there is no JDK in the
authoring sandbox. Everything above is a source-tree and documentation comparison. Compilation and
test status come only from GitHub Actions.
