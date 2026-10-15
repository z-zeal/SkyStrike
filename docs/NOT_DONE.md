# SkyStrike — not done (summary)

> Active backlog, consolidated 2026-10-14 from the existing plan status notes and summaries.
> Companion file: [COMPLETED_TASKS.md](COMPLETED_TASKS.md). This is a documentation cleanup,
> not a new source audit or a claim that any acceptance gate has passed.
>
> **Excluded from consolidation:** `ANDROID_PLAN.md`, `ACCOUNTS_PLAN.md`,
> `MAP_BUILDER_V2_PLAN.md`, and `SERVER_LOBBY_PLAN.md`. Those four files remain unchanged
> and keep their own work and decisions. The multiplayer-menu backlog is now included here.
>
> **Verification gap:** the existing records say the game was not compiled or run in the
> authoring sandbox. Keep unexecuted acceptance items open until tested on a runnable build;
> the recorded first map-milestone-3 desktop run is not a full acceptance pass.

---

## 1. Map building system

| Item | State | Source |
| --- | --- | --- |
| **Milestone 6** — move the arena to `assets/maps/arena.map`; map list as a server setting | Not started. Do not start unless asked. | `archive/MAP_BUILDER_PLAN.md` §9 |
| Physical `assets/maps/arena.map` move (deferred from milestone 1) | Not done. Arena text still lives in `StandardArenaMap.java`. | `archive/MAP_BUILDER_PLAN.md` §9, milestone 1 |
| **§11.1 Glass** — movement-only (D5) or see-through-but-blocking, or another variant | Open decision | `archive/MAP_BUILDER_PLAN.md` §11 |
| **§11.3 Door access** — manual only, or also "open for the owning team" at spawn | Open decision. No `TEAM` door is authored, so the team-refusal acceptance box has nothing to test. | `archive/MAP_BUILDER_PLAN.md` §11 |
| Acceptance checklist for the map builder | 39 unchecked boxes; header says "unexecuted" | `MAP_BUILDER_ACCEPTANCE.md` |
| 10 stances unreachable from either spawn, all in the fixture alcove | Known and named by a warning. Not a fix. | `PHASE7_AUDIT.md` §6, fixture slice E |
| Stale passages in `archive/MAP_BUILDER_PLAN.md`, not yet edited: TerrainRenderer door drawing; the protocol-16 map-hash guard missing from "where the map comes from"; the fixture door position; the persistence section; §3 claims `DoorOccluders` and the first door are unbuilt (they are built); §5 claims door ids are rejected (they are not); §4 and D3 say `E` where the code uses `F`; §7 strict reading versus the policy decided in §11.4; §9 milestone 3 and 5 PR status; §11.4 alcove wording after the door move; §11.1 glass status; §7 header test-directory note | Historical caveats; not an implementation blocker. | `archive/MAP_BUILDER_PLAN.md` |

## 2. Phase 7 — HUD, dialog and console

| Item | State | Source |
| --- | --- | --- |
| `ServerConsole` — the server's stdin terminal front-end | Not landed | `archive/IMPLEMENTATION_ROADMAP.md` §0; `PHASE7_AUDIT.md` §5 D4 |
| Mobile polish: soft keyboard, quick-chat wheel | Not landed | `archive/IMPLEMENTATION_ROADMAP.md` §0 |
| Console-plan Phase 9: touch completion, mute list, profanity masking | Planned; completion not established by the existing status notes. Audit before implementation. | `archive/CONSOLE_CHAT_PLAN.md` §9, §11 |

## 3. Phase 8 — effects

| Item | State | Source |
| --- | --- | --- |
| Decals | Not landed | `archive/IMPLEMENTATION_ROADMAP.md` §0 |
| Shockwave | Not landed | `archive/IMPLEMENTATION_ROADMAP.md` §0 |
| Heat haze | Not landed | `archive/IMPLEMENTATION_ROADMAP.md` §0 |
| Bloom | Not landed | `archive/IMPLEMENTATION_ROADMAP.md` §0 |
| Camera shake | Not landed | `archive/IMPLEMENTATION_ROADMAP.md` §0 |
| Mobile hardening of effects | Not landed | `archive/IMPLEMENTATION_ROADMAP.md` §0 |
| Effects-plan infrastructure: profiler and debug overlay | Not built. `FxBudget`, `FxClock` and `FxEventQueue` did land. | `PHASE7_AUDIT.md` §5 D5 |
| Shader hot reload | Not landed according to the effects-plan status note | `archive/EFFECTS_PLAN.md` introduction, §10, §13 Phase 0 |
| `GlCaps`, named `PostChain`, `VisibilityQuery` types | Absent according to the effects-plan status note; audit existing equivalents before treating these names as required new classes. | `archive/EFFECTS_PLAN.md` introduction |
| Full effect catalogue and per-phase gates | Not audited line by line or runtime-verified. Check remaining presets and phased effects against existing implementations before building more. | `archive/EFFECTS_PLAN.md` §9, §13, §15 |
| Effects performance / precision gates | Unverified: forced-`mediump`, quality-tier degradation, desktop ≤4 ms/frame and mid-tier mobile ≤8 ms/frame, low-end ES 2.0 profiling | `archive/EFFECTS_PLAN.md` §13, §15 |

## 4. Phases 10–11 — platform and mobile

| Item | State | Source |
| --- | --- | --- |
| `core/platform/` | Not landed | `archive/IMPLEMENTATION_ROADMAP.md` §0 |
| Touch controls | Not landed | `archive/IMPLEMENTATION_ROADMAP.md` §0 |
| Quick chat | Not landed | `archive/IMPLEMENTATION_ROADMAP.md` §0 |
| Scoreboard and round flow | Not landed. Round reset exists on the wire (protocol 18); no round state machine exists. | `archive/IMPLEMENTATION_ROADMAP.md` §0; `archive/MAP_BUILDER_PLAN.md` §11.2 |
| Mobile hardening | Not landed | `archive/IMPLEMENTATION_ROADMAP.md` §0 |

## 5. Runtime verification (everything shipped)

Each feature milestone has an unexecuted acceptance checklist. M6–M14 originals are now
archived; all of their unchecked checks are copied into §10 below as the active backlog.
The counts are unchecked boxes in each original checklist, counted on 2026-10-14.

| Checklist | Unchecked boxes |
| --- | --- |
| [`M6_LIGHTING_ACCEPTANCE.md`](archive/M6_LIGHTING_ACCEPTANCE.md) | 6 |
| [`M7_FX_ACCEPTANCE.md`](archive/M7_FX_ACCEPTANCE.md) | 23 |
| [`M8_ARENA_ACCEPTANCE.md`](archive/M8_ARENA_ACCEPTANCE.md) | 12 |
| [`M9_AUDIO_ACCEPTANCE.md`](archive/M9_AUDIO_ACCEPTANCE.md) | 24 |
| [`M10_GADGETS_ACCEPTANCE.md`](archive/M10_GADGETS_ACCEPTANCE.md) | 54 |
| [`M11_HUD_ACCEPTANCE.md`](archive/M11_HUD_ACCEPTANCE.md) | 56 |
| [`M12_UTILITY_VISIBILITY_ACCEPTANCE.md`](archive/M12_UTILITY_VISIBILITY_ACCEPTANCE.md) | 14 |
| [`M13_HUD_ACCEPTANCE.md`](archive/M13_HUD_ACCEPTANCE.md) | 33 |
| [`M14_VISION_BUBBLE_ACCEPTANCE.md`](archive/M14_VISION_BUBBLE_ACCEPTANCE.md) | 11 |
| `CROSSHAIR_POINTER_ACCEPTANCE.md` | 13 |
| `MUZZLE_GEOMETRY_ACCEPTANCE.md` | 10 |
| `PLAYER_LIGHT_ACCEPTANCE.md` | 12 |
| `DEBUG_LOCAL_TEST.md` | 7 |
| `MAP_BUILDER_ACCEPTANCE.md` | 39 |
| `VISION_AUDIO_DRONE_ACCEPTANCE.md` | no checkboxes; a manual list, unexecuted |
| `archive/MECHANICS_PLAN.md` §11 feel checklist | 12 lines, unexecuted |

`docs/archive/ADMIN_ACCESS_FLOW.md` (moved from `docs/`) has 7 boxes, all ticked. It is not in this table.

## 6. Archived documentation caveats

| Doc | Stale claim |
| --- | --- |
| `archive/IMPLEMENTATION_ROADMAP.md` §0 | Says `PROTOCOL_VERSION = 13` and describes the tree at `a293f80`. The live value is 18. |
| `archive/PLAYABLE_BUILD_PLAN.md` | Status line is as of `a293f80`. Later map and gadget work is not reflected. |
| `PHASE7_AUDIT.md` §5 | Historical table. Its own header says D1–D4 are resolved. Kept as is, because §6 is append-only. |
| `archive/MAP_BUILDER_PLAN.md` | Stale passages listed in §1 above. |

## 7. Multiplayer menu, browser and pre-match lobby

**Status: not started (plan only).** All P0–P11 work and all acceptance criteria remain open.
The complete screen specifications, packet proposals, defaults, security rules, risks and
re-run scripts are preserved in [the archived multiplayer design](archive/MULTIPLAYER_MENU_PLAN.md).
The table below is the active implementation checklist, not permission to skip its phase gates.

| Phase | Outstanding work | Detailed design |
| --- | --- | --- |
| P0 | Audit existing networking/navigation and settle technical decisions before coding | Archived design §11 P0 |
| P1 | Multiplayer navigation shell, screen state, timeouts, Back/Cancel and transitional states | §4–5, §11 P1 |
| P2 | Pure room model and lifecycle, separate from transport | §6, §11 P2 |
| P3 | Server room creation and per-server discovery | §8, §11 P3 |
| P4 | Room browser and create-room UI, wired to P3; loading, empty, error, filtering and validation states | §5.3–5.4, §11 P4 |
| P5 | Public/private joins, passwords, join codes, refusal reasons, attempt limits and cancellation | §5.5–5.6, §10, §11 P5 |
| P6 | Synchronised roster, teams, ready state and lobby UI | §5.8, §11 P6 |
| P7 | Host-only settings/moderation/start; enforce all-ready rule and synchronised match transition | §5.9, §6, §11 P7 |
| P8 | Disconnect/reconnect, seat grace, host departure, room cleanup, kick and account-keyed ban flows | §5.10–5.11, §7, §10, §11 P8 |
| P9 | Quick-match queue, progress, cancellation and no-room policy | §5.7, §11 P9 |
| P10 | Friend invitations, LAN discovery and optional local-network polish | §11 P10 |
| P11 | Integration, performance and polish; desktop flow from menu to match and back | §11 P11, §14–16 |

### Dependencies and decisions

- **Decided:** M1 = per-server room list (Direct connect reaches another server);
  M2 = account login, not guest names; M5 = every member ready, no host override.
  The server plan's L1 = one process, many rooms is also decided.
- **P5 dependency:** the accounts plan exists, but its A0–A6 implementation is still required
  before account-based joining. Keep that implementation in the excluded
  [accounts plan](ACCOUNTS_PLAN.md), not duplicated here. Guest-name/session-ban proposals
  in the archived multiplayer design are superseded by the account decision.
- **Server dependencies:** use [SERVER_LOBBY_PLAN.md](SERVER_LOBBY_PLAN.md) for S0–S7 and
  L2–L10; use [MAP_BUILDER_V2_PLAN.md](MAP_BUILDER_V2_PLAN.md) for map distribution.
  Their implementation is outside this consolidation.
- [ ] **M3:** host transfer to the oldest connected member versus closing the room (blocks P8).
- [ ] **M4:** avatar representation; placeholder initials recommended (blocks P6).
- [ ] **M6:** moderation and mid-match join policy; reconcile original recommendations with
  account-keyed bans and server decision L6 (blocks P8).
- [ ] **M7:** no-room quick-match behaviour: create a public room versus offer Browse (blocks P9).
- [ ] **M8:** Android scope after P0's networking audit (blocks P10).
- [ ] **M9:** regional servers; recommended deferred from v1, not a P0–P11 blocker.

### Acceptance and deferred scope

- [ ] Run every criterion in archived §13 (navigation/cancel, browser states, private/public
  joins and refusals, three-client roster, host authority, all-ready start, reconnect,
  host departure, cleanup, quick match, LAN fallback, and end-to-end desktop flow).
- [ ] Run §14's automated rule/flow/protocol/security suites, multi-client harness,
  desktop/LAN/internet checks and performance measurements and §16's per-phase
  desktop re-run scripts. None was executed in the existing plan records.
- Keep regional servers, connection-quality display, a cross-server list service,
  skill-based matchmaking and invitations to non-members deferred as specified in §14.
  Account-service and general friends-system work remain separate from the P10 invitation slice.

## 8. Archive index

Archive selection uses documented implementation milestones, not unchecked runtime boxes or
an invented exact percentage. The mostly implemented plans are archived as history; partially
implemented effects and unstarted multiplayer designs are retained there as detailed references.
Their outstanding work is tracked above rather than in additional active plan files.

| Original plan | Reason / remaining-work location | Preserved reference |
| --- | --- | --- |
| Console/chat | Phases 0–7 landed; Phase 8 terminal and Phase 9 polish remain (§2) | [CONSOLE_CHAT_PLAN.md](archive/CONSOLE_CHAT_PLAN.md) |
| Playable build | M1–M8 and later M9–M14 increments landed in source; runtime gates remain (§5) | [PLAYABLE_BUILD_PLAN.md](archive/PLAYABLE_BUILD_PLAN.md) |
| Map builder v1 | Milestones 1–5 landed; milestone 6, decisions and verification remain (§1, §5) | [MAP_BUILDER_PLAN.md](archive/MAP_BUILDER_PLAN.md) |
| Mechanics | Core gameplay described by the existing completed-work summary; feel checklist remains (§5) | [MECHANICS_PLAN.md](archive/MECHANICS_PLAN.md) |
| Implementation roadmap | Phases 0–7 and 9 landed; effects/platform/match-flow gaps remain (§2–4) | [IMPLEMENTATION_ROADMAP.md](archive/IMPLEMENTATION_ROADMAP.md) |
| Effects | Partial implementation; remaining features and audit/performance gates remain (§3, §5) | [EFFECTS_PLAN.md](archive/EFFECTS_PLAN.md) |
| Multiplayer menu | Plan only; all P0–P11 phases remain (§7) | [MULTIPLAYER_MENU_PLAN.md](archive/MULTIPLAYER_MENU_PLAN.md) |

The four excluded plans retain their original text, including references using the old plan
paths. Resolve those historical names through this table. M6–M14 acceptance checklists are
also archived, with their pending checks in §10.
Other acceptance checklists, project structure, attribution, weapon tables and the append-only
audit remain in `docs/`.

## 9. Protocol

| Item | State |
| --- | --- |
| Wire changes for lobbies, accounts, room flow | None made. Each depends on a plan phase that has not started. |
| `PROTOCOL_VERSION` | 18, unchanged |

## 10. Milestone acceptance backlog — M6–M14

These are the **active copies of all 233 unchecked milestone checks**, consolidated on
2026-10-14. They are verification tasks, not 233 missing implementations. Record results here;
the archived checklists preserve the original setup instructions, context and limitations.
A source inspection or archive move does not pass a runtime check.

Use the current build's rules: M12 made player lighting on by default, and M14 added the
all-round vision bubble (superseding the older cone-only rule inside that bubble). M13 already
implemented the gadget panel and damage numbers omitted by M11. Older protocol numbers and
"not in this increment" passages in the archived checklists are historical, not current gaps.
Keep cooldowns disabled as decided in M13; adding them would be a new gameplay decision.
For checks tied to old milestone banners or protocol pins, test the current equivalent and
record any superseded expectation with its reason; do not reintroduce historical behaviour.

**Other unverified M14 gates (not checkbox items in the original):** compile the touched Java
sources, run shared/server tests, compile the visibility shader, inspect bubble brightness and
edge quality, and check device-light lifetime under repeated drone/camera churn.

**Historical limitations to review, not automatically new scope:** the archives record absent
camera trajectory previews, utility-zone minimap rendering, music/UI sound assets, a separate
semi-auto muzzle smoke wisp and placeholder-quality world SFX. Audit current equivalents before
scheduling these optional follow-ups. Do not reopen M11's gadget-panel gap (M13 landed), or
assume M7/M9's old concrete-only impacts still apply after the map material work.

### M6 Lighting — Desktop Acceptance Checklist
Full setup and historical context: [M6_LIGHTING_ACCEPTANCE.md](archive/M6_LIGHTING_ACCEPTANCE.md).

- [ ] **Local silhouette:** In a dark corner, confirm the player light is on by default and the
  local player remains readable while the surrounding scene still follows the vision/fog rules.
  Toggle `/r_player_light off` and confirm the silhouette goes dark with the rest of the scene.
- [ ] **Wall occlusion:** Put a solid crate or wall between the light and a surface. With
  `r_player_light_shadows on`, confirm the far side stays occluded. Toggle `r_shadows off`: the
  shadow edge should become hard, not disappear. Toggle `r_player_light_shadows off` separately
  to confirm that cvar controls player-light SDF sampling.
- [ ] **Hidden enemies:** *(M14: the rule below holds beyond the vision bubble only; see
  `M14_VISION_BUBBLE_ACCEPTANCE.md`. Inside the 140-unit bubble an enemy with line of sight is now
  visible all round, by decision.)* Keep a remote player **beyond 140 units**, or behind an occluder,
  and outside the local vision cone. Confirm the player remains black/dark and no glow identifies
  their position. Move them clearly into the cone or the bubble with line of sight; their player light
  may then appear.
- [ ] **Live cvars:** While the match is running, set `/r_player_light_radius 220` and
  `/r_player_light_intensity 0.6`; verify the circle and brightness change immediately. Confirm
  out-of-range values are rejected without changing the last valid value.
- [ ] **Resize:** Resize the window and toggle fullscreen at least once. Confirm the half-resolution
  light and visibility targets stay aligned, with no stale-sized or stretched lighting.
- [ ] **Resource teardown:** Disconnect to the menu, re-enter a match, and repeat at least twice.
  Confirm there are no GL errors, stale lights, or growing/leaked render resources.

### M7 FX — Desktop Acceptance Checklist
Full setup and historical context: [M7_FX_ACCEPTANCE.md](archive/M7_FX_ACCEPTANCE.md).

#### Event transport (§8.1)

- [ ] **Explosions are seen:** throw a frag or impact grenade and confirm the phased detonation —
  flash, ring, fireball, debris, smoke — at the detonation point, for every client that can see it.
- [ ] **Transport is unreliable-safe:** with the server under load (or a lossy link), a missing
  effect costs a missing spark, never a stale one, a wrong game state, or a desync.
- [ ] **Culling is per-recipient:** two clients, one looking at a detonation and one facing away —
  only the first sees it. A detonation inside the centre room is not described to a client outside
  the room wall.
- [ ] **Muzzle events:** fire any gun and confirm the muzzle flash, gas burst and ejected shell
  casing at the barrel — for the local player and for remote players you can see. A trigger click
  on cooldown is silent.
- [ ] **Bullet impacts:** fire into the ground and into a wall; confirm a dust-and-chips impact at
  the hit point, hugging the surface. Body hits emit no surface impact.

#### The gate (the one that matters most)

- [ ] **Frag round a corner:** throw a frag so it detonates just behind a wall from your position.
  Confirm you see the explosion's **light on the wall face**, and **no particles through the wall**
  — no embers, sparks or smoke glowing on the far side. Turn to face the detonation directly and
  the full effect appears.
- [ ] **Additive glow respects fog of war:** an explosion outside your vision cone (but within
  reach) does not light up or sparkle at you. The additive batch is gated by the visibility
  texture exactly like M6's light pass.
- [ ] **Alpha particles are fogged:** smoke and dust inside the scene darken with the fog like
  everything else in the scene pass.

#### Particles and budget (§8.2)

- [ ] **Pass order:** alpha particles (smoke, dust, debris, shell casings) are darkened by the fog;
  additive particles (sparks, fire, flash, muzzle) glow through darkness. Confirm by watching a
  smoke grenade in a dark corner: the cloud is dim, the molotov next to it is bright.
- [ ] **fx_debug budget gate (F11):** with `fx_debug on`, the `fx:` line shows
  `particles A/B alpha + C/D add + E/F cpu  lights G/H  phases I  events J  tier T`. Under heavy
  spam (a full squad auto-firing plus grenades), every live count stays at or below its cap —
  `A ≤ B`, `C ≤ D`, `E ≤ F`, `G ≤ H` — and the line still renders.
- [ ] **Live tier:** set `/quality low`, `/quality mid`, `/quality high` mid-match. Confirm the
  caps in the `fx:` line change immediately (500/1500/6000 GPU, 60/150/400 CPU, 2/6/16 effect
  lights) and that the low tier still shows fog, lights and particles, just fewer.
- [ ] **Settings fallback:** with the `quality` cvar untouched, change Quality in the in-game
  settings dialog (Low/Medium/High/Ultra) and confirm the tier follows (Ultra maps to High).
- [ ] **Shell casings (tier-2 proof):** fire and confirm the ejected casing arcs out of the barrel,
  bounces off the floor against the SDF, and settles. The full CPU collision tier (bouncing debris
  generally) is deferred beyond casings.
- [ ] **Determinism:** two clients watching the same frag see the same particle layout (the server
  assigns the seed).

#### Presets (§8.3)

- [ ] **Frag / impact:** the phased schedule at 0.00/0.03/0.05/0.08/0.10 s, with the white-yellow
  flash and a light that is never culled while ordinary effect lights are.
- [ ] **Smoke grenade:** a billowing alpha cloud that grows into its vision-blocking shape. The
  occlusion is no longer one full-radius disc at the detonation point: the shared `SmokeCloud`
  cluster (three sagging circles, swollen by a growth factor over the cloud's first 40% of life)
  is derived identically by the server's sight queries and the visibility shader, so what blocks
  sight is the cloud you see, growing as it billows. See `docs/archive/M12_UTILITY_VISIBILITY_ACCEPTANCE.md`.
- [ ] **Molotov:** glass sparks, a fire splash along the surface tangent (correct on floors,
  ramps and walls), one flickering attached light per fire zone that expires with the zone, and —
  from M12 — persistent flame rendering (`core/render/UtilityZoneRenderer`) that lives exactly
  as long as the zone burns and covers exactly the circle the damage-over-time resolves.
- [ ] **Flashbang / stun:** a white burst and residual wisps at the detonation, plus the full-screen
  whiteout scaled by the server's distance band and line of sight (`StunMath`). The whiteout
  recovers exponentially — overwhelming at first, clearing fast — and the HUD, chat and console
  stay readable throughout.
- [ ] **Bullet impacts:** concrete dust and chips today; the metal (sparks) and wood (splinters)
  catalogue entries exist and are ready for a surface-material pass. Decal bullet holes are
  deferred.

#### Preservation checks (must not regress)

- [ ] **M6 lighting:** `r_player_light` and its cvars, the fog-of-war protections, and the
  M6_LIGHTING_ACCEPTANCE checklist all still pass. Effect lights share the bounded light pool with
  player lights; neither starves the other in a normal match.
- [ ] **Menus, audio, permissions:** Scene2D menus and settings, gun SFX, server-authoritative
  permissions and gameplay state are untouched by M7.
- [ ] **Resize:** resize the window and toggle fullscreen; particles, lights and the whiteout stay
  aligned (they are world-space and post passes, not sized targets).
- [ ] **Resource teardown:** disconnect to the menu, re-enter a match, and repeat at least twice.
  Confirm there are no GL errors, stale particles, lingering lights, or growing/leaked render
  resources. Effect lights are released from the pool on disposal.

### M8 Arena Rework — Desktop Acceptance Checklist
Full setup and historical context: [M8_ARENA_ACCEPTANCE.md](archive/M8_ARENA_ACCEPTANCE.md).

#### Foot traversal gate

- [ ] From **each spawn**, walk and jump to the room floor through the mid-lane/doorway route.
  Confirm the 50-unit doorway fits a standing player and is not a collision snag.
- [ ] From **each spawn**, use the ground tunnel, jump onto the hatch step, and enter the room
  through both 90-unit floor hatches. Reverse the route without fuel.
- [ ] From the room floor, reach the interior riser, shelf, catwalk, perch ladder, and sniper perch
  without jetpack. Every upward move should feel like a normal chained jump, not a pixel-perfect
  fuel-assisted launch.
- [ ] From the catwalk and perch, return to ground using no fuel. There must be no one-way pocket.
- [ ] Verify that each side has two distinct-height entries to the centre: tunnel/hatch low and
  mid-lane/doorway high.

#### Upper arena and symmetry

- [ ] Reach each outer ledge and the high centre platform from either spawn with a jump-first
  jetpack ascent using no more than 40% of a normal fuel tank. Drop back out without fuel.
- [ ] Use `cl_freecam` to inspect both halves. Doorways, hatches, landing shelves, risers, perch
  steps and upper ledges must be exact left/right reflections about x=1500.
- [ ] Check that the upper platforms make y≈900–1000 tactically useful without exposing a sealed
  pocket or an unreachable-looking ledge.

#### SDF coherence

- [ ] Toggle shadows / inspect effect collision at both room doorways and both hatches. The SDF
  must agree with collision: no invisible wall in an opening and no light/particle path through a
  solid jamb, floor section or roof lip.
- [ ] Throw or fire an SDF-colliding effect near a hatch edge and the new upper platforms. Confirm
  its collision, shadow and rendered terrain all use the rebuilt geometry.

#### Automated follow-up outside this sandbox

- [ ] Run `./gradlew shared:test` with a JDK. This includes `ArenaMapTest`, the new
  `ArenaReachabilityTest`, and `SdfBakerTest`.
- [ ] Run the project compile/module-boundary tasks with a JDK before declaring M8 complete.

### M9 Audio — Desktop Acceptance Checklist
Full setup and historical context: [M9_AUDIO_ACCEPTANCE.md](archive/M9_AUDIO_ACCEPTANCE.md).

#### One event, two layers (the Phase 9 rule)

- [ ] **Explosion:** throw a frag. The flash and the boom arrive in the same frame, once each —
  no doubled detonation, no sound that lags the light.
- [ ] **Molotov:** a glass break at the contact point, then fire ignitions as the patch spreads
  along the surface. The ignition reads as one fire starting, not five clicks (`FIRE_ZONE` is
  capped at two voices and lowest priority, deliberately).
- [ ] **Exactly one gun report:** fire any weapon, including a shotgun and a burst rifle. One
  report per successful shot/volley; the authoritative cue travels with the state snapshot,
  independent of projectile lifetime and visual muzzle-flash culling. The generic muzzle-flash
  effect remains silent, so the shot is never doubled.
- [ ] **Counters:** with `fx_debug on`, the `sfx:` line's `silent` count grows only from visible
  muzzle-flash effects (one per shot) and never from a malformed event.

#### Spatialisation and occlusion

- [ ] **Distance:** a frag across the arena is quieter than one beside you, and still audible; a
  fire zone a few hundred units away is faint.
- [ ] **Panning:** a shot to your right arrives on the right channel; walking around to the other
  side swaps it. Panning follows world X, matching the screen, because this camera never rotates.
- [ ] **Occlusion:** a frag on the far side of a wall is muffled, not silent — information survives,
  the "it went off in this room" lie does not. Same for a fight through the centre room's wall.
- [ ] **Smoke:** stand in or beside smoke. A detonation on the far side is muffled exactly as if by
  a wall — the audio occlusion is the same `VisionMath` line-of-sight call, with the same live
  smoke volumes, that the stun bands and the remote-player light gate use.
- [ ] **Edge taper:** walk away from a burning fire zone until it goes silent. The cut-off must be
  inaudible — no pop, no click, no abrupt disappearance.

#### Pooling and priorities

- [ ] **Under spam:** a full squad auto-firing plus grenades and fire. The mix stays legible:
  casing tinkles and fire ignitions drop first, detonations always come through, and fresh FIRE
  attacks continue to replace the oldest same-priority gunfire voice when their path/bus cap is
  full. This exception is limited to authoritative gunfire; other sound priorities stay unchanged.
  Watch `voices`, `stolen` and `dropped` in the `sfx:` line.
- [ ] **Bus caps:** `voices` never exceeds the effects bus cap (24); music and UI have their own
  small caps (2 and 4) and nothing else plays on them yet.
- [ ] **Ordinary ties never steal:** a casing arriving while casings are at their two-voice cap
  does not replace one; it is dropped. Only a fresh cue explicitly marked by `GunAudio` may
  replace an older equal-priority `FIRE` voice under pressure.
- [ ] **Missing asset (optional):** rename one world WAV and run a match. One logged error, silence
  for that effect only, no crash, no per-frame retry, and `missing 1` in the `sfx:` line.

#### Status effect — the stun ring

- [ ] **Flashbang:** take one at close range. The whiteout and the ring arrive together, and the
  ring keeps ringing for seconds *after* the light clears, falling in pitch as it fades. The tail is
  the point: the stun costs you something after it wears off.
- [ ] **Bands differ:** a fringe (or line-of-sight-blocked) stun rings quieter and for less time than
  a direct hit. A direct hit is loud but never drowns the fight around it.
- [ ] **Local only:** a second client watching the same flashbang hears the bang only. The ring is a
  status effect on the player who was blinded, never a world sound.
- [ ] **No ring in the menu:** disconnect to the menu (or let the match end) while still ringing.
  The menu is silent — the session-reset listener stops the loop.
- [ ] **Zero effects volume:** with Effects at 0 the ring is silent too. It sits on the effects bus
  on purpose: a player who mutes effects has asked for silence, status effect or not.

#### Buses, volume and settings

- [ ] **Live sliders:** in-game settings, drag Master and Effects. The world mix follows immediately,
  including the ring, and without restarting the match.
- [ ] **UI bus:** turning Effects to zero does not silence chat, console or menu blips; Master does.
  (Music is wired but nothing plays on it yet, so the Music slider is a documented no-op today.)
- [ ] **Persistence:** set all three sliders, restart the client, confirm the values persist and are
  applied on the first match.

#### Teardown and lifecycle

- [ ] **Disconnect / reconnect ×3:** no ring left behind, no growth in `assets` in the `sfx:` line,
  no errors, and the same mix quality after re-entering.
- [ ] **Settings dialog mid-match:** open and close the in-game settings dialog; sound neither stops
  nor stutters.
- [ ] **Resize / fullscreen:** the mix is unchanged; nothing is tied to the window size.

### M10 Gadgets — Desktop Acceptance Checklist
Full setup and historical context: [M10_GADGETS_ACCEPTANCE.md](archive/M10_GADGETS_ACCEPTANCE.md).

#### Drone — deploy, pilot, exit

- [ ] **Deploy:** press Q once. A drone appears 25 units above your head, the Q box in the loadout
  bar lights up, and the HUD banner appears only after the *second* press (deploying alone does
  not switch your view).
- [ ] **Pilot:** press Q again. The camera moves to the drone immediately, the banner reads
  `DRONE` with its HP (`HP 30/30`), and the controls line reads `WASD steer  LMB locked  6 cycle
  Esc exit`.
- [ ] **Steering:** A/D move the drone left/right, W (or Space) climbs, S descends. Full flight
  speed is 500 world units/s (2.5× player walk speed); the 16/s velocity response is quick but
  still eased, and releasing the keys coasts to a hover within about a second.
- [ ] **Aim:** the mouse steers the drone's vision cone — move the cursor and the revealed area
  follows it, measured from the drone, not from your body.
- [ ] **Exit with Q:** press Q a third time. The view returns to your body instantly, the banner
  disappears, and the drone **stays deployed** (still visible, still revealing, Q box still lit).
- [ ] **Exit with Escape:** pilot again, then press Escape. The view returns to your body and the
  pause menu does **not** open.
- [ ] **Cycle with 6:** with the drone deployed, press `6`. The view switches to the drone; press
  `6` again and it returns to your body (nothing else is deployed yet). Rebind `viewCycle` in
  settings and confirm the new key works.

#### The surveillance lock — the gamble

- [ ] **Body frozen:** while piloting, hold W/A/S/D — your body does not move (it coasts to a
  stop and, if airborne, falls). The movement keys drive the drone instead.
- [ ] **No weapons:** while piloting, hold LMB, press 1–5, and swing melee. Nothing fires, no slot
  changes, no reload starts. The crosshair is dimmed (~⅓ brightness), not full.
- [ ] **Gadget keys stay live:** while piloting, Q still exits the pilot view (and a second Q
  re-enters it). Only the view keys and the gadget keys do anything.
- [ ] **Vulnerable:** have client B shoot your body while you pilot. You take full damage — the
  drone does not shield you, and you cannot dodge. This is the gamble; it must feel real.
- [ ] **Stun while piloting:** client B flashbangs you mid-pilot. The whiteout covers the drone
  view; Escape still exits surveillance (the view keys are live under every lock).
- [ ] **Death while piloting:** client B kills you while you pilot. The drone is removed with you,
  the view is your own eyes on the corpse, and the Q box reads OFF (not BROKEN — death is not
  destruction). On respawn the drone is gone but still *carried* (Q reads OFF, full durability).

#### Drone vision

- [ ] **Own cone, always:** with the drone deployed but *not* piloted, its cone still reveals
  territory around it — narrower (70°) and dimmer (70% brightness) than your own cone.
- [ ] **Replaced while piloting:** while piloting, your body's cone is gone from the composite;
  the screen shows the drone's cone. After exiting, your cone is back and the drone's remains.
- [ ] **Occlusion:** the drone's cone is blocked by walls exactly like a player's — park it behind
  the centre room's wall and it reveals nothing through it.
- [ ] **Effects arrive:** with the drone parked watching a lane, have client B fight in that lane.
  You see (and hear, through the drone — the ears follow it) the fight you cannot see from your
  body, including muzzle flashes and impacts your own cone would have culled.

#### Drone destruction

- [ ] **Shot down:** client B shoots the drone (30 HP). Sparks (metal impact) appear at it, its
  health bar drains, and at zero it pops (small impact explosion) and disappears. The Q box reads
  `BROKEN` for the rest of the life.
- [ ] **Pilot ejected:** shoot the drone *while piloting it*. The pop happens, the view snaps back
  to your body, and Q reads `BROKEN`.
- [ ] **Never un-breaks mid-life:** press Q with a broken drone — nothing deploys.
- [ ] **Respawn restores:** die and respawn. The drone is carried again, intact, undeployed; Q
  deploys a fresh one.
- [ ] **Teammate's drone:** client B's drone is visible to you with its team colour, and you can
  shoot it (denying eyes is legitimate play).

#### Throw camera — throw, stick, view

- [ ] **Throw:** press E once (camera on E). The camera leaves your hand along your aim at
  grenade speed on a visible arc, and the E box lights up. The banner does **not** appear yet.
- [ ] **Mid-flight press:** press E again while it flies — nothing happens (a flying camera cannot
  be viewed).
- [ ] **Stick:** the camera sticks to the first surface it touches — floor, wall or ceiling — with a
  small spark, and stays there permanently (it never despawns on a timer).
- [ ] **View:** once stuck, press E again. The camera centres on the stuck camera, the view zooms
  in 1.2×, and the banner reads `CAMERA` with its HP (`HP 20/20`).
- [ ] **Swivel:** while viewing, move the mouse — the camera does not move, but its cone follows
  your aim, so you can sweep a lane from a fixed post.
- [ ] **Exit:** press E again or Escape — the view returns to your body and the zoom is restored
  (the pre-surveillance viewport comes back, including a `cl_freecam` zoom).
- [ ] **Cycle through it:** with both devices deployed, press `6` repeatedly — the cycle runs
  self → drone → camera → self, and each press switches the camera view to the next available
  device. With only the camera stuck, `6` goes self → camera → self.
- [ ] **Blocked throw:** stand facing a wall and press E — no camera spawns, the E box does not
  light (the release point must be clear).
- [ ] **Destruction:** client B shoots the stuck camera (20 HP). It pops and is gone; the E box
  reads `BROKEN`; a viewer is returned to their own eyes. Respawn restores it.

#### Bullets hit devices

- [ ] **Nearest wins:** line up a shot where a drone stands in front of an enemy — the drone takes
  the round (metal sparks), the enemy behind it is unhurt.
- [ ] **Damage falloff:** a drone at long range takes visibly less damage per hit than at close
  range (its health bar drains slower).
- [ ] **Self-hit grace:** your own rounds do not hit your own drone for the first fraction of a
  second after firing (the same grace players get).

#### Shield arc and fuel tank — legibility (the "done when" line)

- [ ] **Front arc:** client B equips the shield (Q). You see a 90° arc on the *front* of their
  body, centred on where they aim. When they aim at you, the arc faces you.
- [ ] **Rear arc:** client B presses Q again to stow it. The arc flips to their *back* — the
  front/back choice is legible at a glance, on a teammate and an enemy alike.
- [ ] **Broken:** client B's shield breaks (shoot the arc). The arc disappears; the Q box reads
  `BROKEN`.
- [ ] **Fuel tank:** client B wears the tank. You see an amber strip on their *rear* — the side
  they are not aiming at — between 30% and 72% of their height.
- [ ] **Flank and detonate:** shoot the tank strip from behind. The tank detonates (occluded
  blast), the wearer dies, and nearby players take damage — a flanker can detonate a fuel tank.
- [ ] **Detonated tank gone:** after detonation the strip is gone and the E box reads `BROKEN`.

#### HUD

- [ ] **Gadget boxes:** the Q/E boxes in the loadout bar show the gadget name, its key, a
  durability strip (the drone's and camera's live HP, the shield's remaining absorption), and a
  status word (`ON`/`OFF`/`WORN`/`BROKEN`) that never disagrees with the world.
- [ ] **Banner:** while piloting or viewing, the top-centre banner shows the device, its HP and
  the control reminder; it is absent the moment the view returns to your body.
- [ ] **Crosshair:** dimmed while surveilling; normal brightness otherwise.
- [ ] **Debug overlay (F1):** the `surveillance:` line reports the view and the drone's position
  and HP; the controls help line documents `Q/E gadget  6 view cycle  Esc exit/pause`.

#### Effects and audio

- [ ] **Destruction pop:** a drone or camera going down plays a small impact explosion at its
  position, visible and audible to anyone who can see it (including through a device).
- [ ] **Stick spark:** a camera sticking to a surface plays a small metal impact there.
- [ ] **Device hits:** rounds hitting a device play the metal impact, not the concrete one.
- [ ] **Ears follow the view:** while piloting, sounds are placed at the drone — a fight beside the
  drone is louder and pans correctly even with your body elsewhere.

#### Edge cases and teardown

- [ ] **Pause and console keep Escape:** while piloting, open the console (Enter) — Escape closes
  the console, not the pilot view; with the console closed, Escape exits surveillance; with the
  pause menu open, Escape closes the pause menu. One key, one owner at a time.
- [ ] **Disconnect mid-pilot:** client A disconnects while piloting — the drone is removed from
  the world on the next snapshot; client B never sees a device with no owner.
- [ ] **Reconnect ×2:** reconnect, deploy, pilot, disconnect mid-pilot, reconnect — no stale
  banner, no orphaned drone, no zoom stuck applied.
- [ ] **Resize/fullscreen while viewing:** the zoom survives a resize and is released on exit.
- [ ] **Freecam:** `cl_freecam` still detaches the camera; returning from freecam while piloting
  resumes following the drone.
- [ ] **Protocol:** an old-protocol client is rejected at join. M10 raised the protocol to 12
  (device entities appended at Kryo indices 33/34); it is now **13**, after the server-authenticated
  gunfire cue list was appended to `PacketGameState`. Either way, a client whose
  `PacketJoinRequest.protocolVersion` does not match `NetConfig.PROTOCOL_VERSION` must fail the
  handshake.

### M11 HUD — Desktop Acceptance Checklist (the fog-gated minimap)
Full setup and historical context: [M11_HUD_ACCEPTANCE.md](archive/M11_HUD_ACCEPTANCE.md).

#### The frame — whole arena, never a radar

- [ ] **Position:** the map is top-left, inside the margin, on the same panel fill as the console
  and the debug readout, with a thin edge.
- [ ] **Aspect:** the arena's 3:2 shape is preserved. The map is not stretched to fill its frame —
  a marker must be as far from the centre as the world puts it.
- [ ] **Absolute, not relative:** walk to the far right of the arena and confirm your marker is at
  the far right of the map. The map does not re-centre on you and does not rotate.
- [ ] **Terrain reads as a silhouette:** the floor, both side walls, the ceiling, the ramp steps,
  the crate stacks, the centre room, the catwalk and the sniper perch are all recognisable as the
  arena you are standing in, dimmer than any marker. Thin geometry (the 14-unit tunnel roof, the
  16-unit perch ladder) is still visible rather than vanishing below a pixel.
- [ ] **Nothing is cropped:** a jetpack fight in open sky, above every platform, is on the map.
- [ ] **Your view rectangle:** a hollow rectangle shows what the camera is currently framing. Fly
  the camera around with a detached pan (`cl_freecam`) and confirm the rectangle follows and stays
  inside the arena even when the camera looks past the edge.

#### Your own marker

- [ ] **Always there:** your marker is the brightest, slightly larger square, and it is drawn on
  top of anything it overlaps.
- [ ] **Centre, not feet:** standing on the ground, your marker sits at your body's centre, not at
  your feet and not at eye level. Crouching moves it down slightly.
- [ ] **Alive or dead:** die and confirm your own marker is still on the map, at your corpse. You
  never lose track of where you fell.
- [ ] **Before a spawn:** while joining, before you have spawned, the map already draws the arena
  and the view rectangle with no markers at all — it is a map, not a blank box.

#### The gate — enemies only where the fog lights them

- [ ] **In the cone, in reach:** an enemy in front of you within hip reach (640 units) appears as a
  red square. Walk towards them and the marker tracks them smoothly.
- [ ] **Behind you:** an enemy directly behind you is **not** on the map, even though they are a few
  metres away and the snapshot carries their exact position. Turn around and they appear.
- [ ] **Outside the cone:** an enemy 90° off your facing, in reach, is not marked; swing your aim
  across them and the marker appears as the cone sweeps over them.
- [ ] **Out of reach:** an enemy further than hip reach is not marked. Aim down sights (`RMB`) and
  confirm the gate widens with the eased ADS reach (up to 1024) — a distant enemy comes onto the map
  as you aim, without the reach snapping.
- [ ] **The last few percent of reach:** the gate is the same distance falloff the composite draws
  with (`1 − (d/reach)²`), so a marker disappears at about 97% of reach — the band where a body is
  drawn as essentially black. There is no separate, more generous "map range" to discover.
- [ ] **Peripheral floor never marks:** an enemy outside your cone — past its 60° half-angle, where
  the composite leaves only the faint peripheral wash — is not marked, however close they are. The
  gate sits just above that 6% floor precisely so the wash cannot earn a dot; inside the cone's
  feathered edge, where a body really does start to appear, the marker appears with it.
- [ ] **Behind cover:** an enemy behind the centre room's wall, or behind a crate stack, is not
  marked even when they are inside your cone and in reach. Step into the doorway and they appear.
- [ ] **In smoke:** throw smoke between you and an enemy. Their marker goes away while the smoke
  hides them and comes back when it clears — the map consults the same live smoke volumes the
  visibility shader draws with.
- [ ] **Never disagrees with the screen:** the rule to hold every check above against — if the
  enemy is lit enough on your screen to see, they are on the map; if the screen draws them black,
  they are not. A marker for a body the fog is hiding would be a cheat sheet.
- [ ] **Marker at the centre:** an enemy's marker is at their body centre, matching where they are
  drawn in the world, not at a hitbox corner.

#### Memory — ghosts hold, fade, and do not track

- [ ] **No strobing at the cone edge:** turn away from a lit enemy. Their marker does **not** blink
  out; it holds for about a second, then fades over about 2.5 seconds, then is gone (~3.5 s total).
- [ ] **Hollow means remembered:** while fading, the marker is drawn hollow (an outline) rather
  than filled, so a memory can never be mistaken for something you can currently see.
- [ ] **Frozen, not following:** this is the important one. Turn away from a moving enemy and have
  them keep walking. The ghost stays exactly where you last saw them — it does **not** creep after
  them, and it does not update at a lower rate. It is a memory of an observation.
- [ ] **Death retires it at once:** turn away from an enemy so their marker is a fading ghost, then
  have them die (or kill them). The ghost disappears immediately, mid-fade. A corpse is not a
  sighting, and the kill feed already said why.
- [ ] **Respawn empties the map:** die and respawn. Every remembered sighting is gone — the last
  life's memories are about a body you left, seen from a position you are no longer at.
- [ ] **A frame spike cannot empty or hoard the map:** the envelope runs on the wall clock. Drop to
  a very low frame rate (or alt-tab) and confirm markers age at the same real-world rate rather
  than living forever or expiring instantly.

#### The teammate toggle (`cl_minimap_allies`)

- [ ] **Default on:** with both clients on the same team, a teammate behind you or behind cover is
  shown on the map in the ally colour (green), with no gating — mechanics §4's "teammates visible
  on minimap".
- [ ] **Off:** `/cl_minimap_allies false` in the console. That same teammate is now gated exactly
  like an enemy: shown when your vision lights them, and a ghost afterwards.
- [ ] **Back on:** `/cl_minimap_allies true` restores it live, with no respawn and no reconnect.
- [ ] **Applies to devices too:** an allied drone or stuck camera follows the same toggle as an
  allied body — one rule, not two that can drift apart.
- [ ] **Neutral has no teammates:** with both clients moved to Neutral (`/setteam neutral`, which
  needs moderator console access), the toggle exempts nobody. Two Neutrals fight each other, so
  neither is shown for free.
- [ ] **The other §4 toggle is not implemented:** teammates are **not** exempt from cone culling in
  the world — a teammate you cannot see is still drawn dark. This cvar touches the map only.

#### Devices — drones and throw cameras

- [ ] **Your own, always:** your deployed drone and your stuck camera are on the map however far
  away they are and whatever you are looking at. They are your property, and the gadget slots
  already report their state.
- [ ] **Device shape:** devices are drawn as small dots, bodies as squares — you can tell them apart
  at a glance.
- [ ] **An enemy device is gated:** an enemy drone or camera is marked only where your vision lights
  it, exactly like an enemy body, and ghosts the same way.
- [ ] **Destroyed devices are forgotten:** have client B shoot your drone down (or destroy their
  camera). Its marker disappears at once rather than fading — absence from the snapshot is
  authoritative, so a destroyed device is gone, not hidden.
- [ ] **A camera in flight is not an eye:** throw a camera and confirm it is still marked as your
  own device while it flies, but that it reveals nothing (its cone only exists once it sticks).

#### Surveillance — the map while piloting

- [ ] **The gate moves with the view:** deploy and pilot a drone. Your map is now gated by the
  *drone's* eyes — its narrower 70° cone and 250-unit reach — not by your body's. Enemies near your
  body but outside the drone's cone are not marked.
- [ ] **Your body's cone is gone:** while piloting, an enemy standing in front of your (frozen,
  unattended) body is not marked unless the drone also lights them.
- [ ] **Your body marker stays put:** while piloting, your own marker remains at your body, which
  is not moving. It does not jump to the drone.
- [ ] **The view rectangle follows the drone:** while piloting, the hollow rectangle is around the
  drone's camera, and it widens/narrows with the surveillance zoom.
- [ ] **Viewing a stuck camera:** cycle to your stuck camera (`6`) and confirm the same three
  things — the gate is the camera's cone, the rectangle is the camera's view, your body marker is
  still at your body.
- [ ] **Exit restores everything:** press `Q`/`Escape` to return to your body and confirm the map
  is gated by your own cone again immediately, with no stale markers left over from the device.

#### Sharing the corner, toggling, and resizing

- [ ] **F1 readout moves:** with the map on, press `F1`. The debug readout starts *below* the map —
  it does not draw through it, and no line is clipped.
- [ ] **F1 readout takes the corner back:** `/cl_minimap false`, then `F1`. The readout moves up
  into the top-left corner with no hole left where the map was, and no gap at the top.
- [ ] **Toggling live:** `/cl_minimap false` and `/cl_minimap true` add and remove the map with no
  respawn, no reconnect, and no other widget moving (kill feed, vitals, loadout bar, crosshair and
  the surveillance banner all stay put).
- [ ] **Debug line:** the `minimap:` line reports the marker count, how many are live versus
  remembered, whether allies are shown or gated, and the arena size. With the map off it reads
  `minimap: off (cl_minimap)`. Counts agree with what is drawn.
- [ ] **Banner:** the first debug line now reads `SkyStrike - M11 (HUD: fog-gated minimap)`.
- [ ] **Resize and fullscreen:** resize the window and toggle fullscreen. The map keeps its 3:2
  aspect, stays in the corner, scales with the HUD's design-pixel scale (so it is legible at 1080p
  and at 4K), and the readout inset follows it.
- [ ] **Legible over a bright sky and a dark interior:** the map has no text pass — there is
  nothing on a map this size worth a glyph — so the console's four-background contrast test
  (`ui_contrast_test`, F12, which covers the whole screen including the HUD) does not apply to it.
  Check it the way a player meets it instead: standing in open sky with the sun-bright composite
  behind the panel, and inside the dark centre room. The terrain, the panel edge, the view
  rectangle and all three marker colours stay distinguishable in both, and ally/enemy/self read as
  different *sides*, not as different brightnesses of one colour. Colour-only distinction is
  reinforced by shape (bodies are squares, devices dots, memories hollow).

#### Edge cases and teardown

- [ ] **No other HUD element is affected:** the crosshair, health/fuel bars, loadout bar, kill feed,
  damage vignette and surveillance banner all behave exactly as they did before this increment.
- [ ] **Loadout screen has no map:** open the loadout picker/menu screen. There is no minimap there
  (no arena, no live local player) and the readouts are laid out as they were.
- [ ] **Reconnect ×2:** disconnect and reconnect twice. The map is empty of stale sightings after a
  reconnect — you do not inherit the last connection's memories.
- [ ] **A crowded match does not stutter:** the model caps remembered sightings at 64, forgetting
  the least recently seen first. With many players and devices, live markers are never dropped to
  make room for memories.
- [ ] **Team switch:** `/setteam b` mid-match. Allegiance colours update immediately — a former
  teammate is now an enemy colour and gated, a former enemy now an ally colour and exempt. Old
  ghosts keep the allegiance they were seen under rather than recolouring, which is what makes a
  memory a memory.
- [ ] **Spectator/dead view:** while dead and waiting to respawn, the map still draws the arena and
  your own marker, and the gate follows whatever eyes you still have.

### M12 Utility Visibility — Desktop Acceptance Checklist
Full setup and historical context: [M12_UTILITY_VISIBILITY_ACCEPTANCE.md](archive/M12_UTILITY_VISIBILITY_ACCEPTANCE.md).

#### Smoke / poison occlusion follows the cloud

- [ ] **Growth:** throw a smoke grenade into open ground and watch it from the first frame. No
  vision is blocked at the detonation instant; the blocked area swells with the billowing cloud
  and is fully out about 3 seconds in (40% of the 8 s zone life), not instantly.
- [ ] **Shape:** the occluded region reads as a sagging blob, not a perfect disc centred on the
  impact — denser and lower than the detonation point, matching where the puffs actually are.
  Stand behind the cloud's far edge with an enemy on the other side: you cannot see them once the
  cloud is grown.
- [ ] **Poison:** the poison cloud occludes the same way (same cluster, 7 s life, full at ~2.8 s),
  and its damage-over-time still applies inside it.
- [ ] **Server/client agreement:** with client B standing in the grown smoke, client A's view of
  B is cut exactly where the cloud is — never earlier (empty ground blocked) and never through
  the cloud's core. The stun grenade's and flashbang's line-of-sight checks agree with what is
  drawn (detonate one inside the smoke; the blind applies only where the occlusion is).
- [ ] **Cap behaviour:** throw three smoke grenades in quick succession (two slots, carried
  count 2). The first two clouds occlude fully; the third is partially truncated — identically
  for the shader and for gameplay sight queries, so what you see is what the server resolves.

#### Molotov fire persists and matches the damage zone

- [ ] **Persistence:** throw a molotov on open ground. Flames and the ember bed are visible for the
  full 6 s of the zone's life, flickering, fading out over the final second — not just the ~1.5 s
  the ignition burst's particles live.
- [ ] **Coverage:** the flames sit inside the zone's 40-unit circle, so the fire you see is the
  fire that burns: stand at the flame line's edge and confirm the damage-over-time starts exactly
  there.
- [ ] **Spread:** on a floor the fire reads as one continuous line along the surface — one central
  patch plus three overlapping patches each side at 50-unit spacing (±0.35 jitter), roughly 230
  units long end to end. The outward patches deal 17 per tick against the central 21, and a
  player standing in two overlapping patches still takes one hit per tick, not two.
- [ ] **Surfaces:** the spread follows the tangent on a ramp and on a vertical wall (throw at a
  wall; the line runs vertically). Flames rise in world-up regardless of the surface, matching the
  ignition burst.
- [ ] **Terrain stop:** aim one side of the tangent at a wall or solid platform. The last clear
  patch remains, no patch appears beyond the first blocked segment, and the unobstructed side still
  spreads normally.
- [ ] **Occlusion:** fire around a corner does not show through the wall (the renderer draws in
  the scene pass, so the fog composite gates it); the attached flickering light per zone still
  expires with the zone.

#### Player light ships on

- [ ] **Default on:** in a normal (non-dev) build, the local player carries the warm-white
  140-unit, 0.35-intensity light with no console command. The silhouette stays readable in a dark
  corner while the surrounding scene follows the fog rules (M6's local-silhouette check).
- [ ] **Toggleable:** `/r_player_light off` removes it immediately; `/r_player_light on` restores
  it. F10 toggles it in dev builds. `/r_player_light_shadows off` disables the lights' SDF
  occlusion sampling; `/r_shadows off` hardens the edges.
- [ ] **Hidden enemies, as amended by M14:** a remote player behind cover, or outside both the cone
  and the 140-unit vision bubble, stays dark; no glow identifies their position. *(M14 superseded the
  M6/M12 rule inside the bubble: an enemy within 140 units with line of sight is now visible all
  round, by decision. See `M14_VISION_BUBBLE_ACCEPTANCE.md`.)*

### M13 HUD — Desktop Acceptance Checklist (gadget panel and floating damage numbers)
Full setup and historical context: [M13_HUD_ACCEPTANCE.md](archive/M13_HUD_ACCEPTANCE.md).

#### The gadget panel

- [ ] **Position:** with at least one gadget equipped, a plate sits bottom right, directly above the
  loadout bar and above its reload/swap captions. It does not cover the bar or the captions.
- [ ] **Hidden when empty:** with both gadget slots empty, there is no plate. The bar's two "Empty"
  boxes are the only gadget readout.
- [ ] **Each row names its key:** `Q` and `E` are printed in the accent colour, with the gadget's
  name beside them. A long name is trimmed by the plate's width rather than running off it.
- [ ] **Shield, intact:** reads `112/150`-style durability with a bar under it. The bar is green and
  full at `150/150`. The state word is `REAR` while stowed and `FRONT` after `Q` equips it.
- [ ] **Shield, wearing:** take shield hits (have client B shoot you while your shield faces them).
  The bar shrinks, the count falls with it, and the bar and count agree.
- [ ] **Shield, broken:** at zero durability the state word reads `BROKEN`, the bar and count turn
  red, and the shield stays broken for the rest of the life.
- [ ] **Drone, deployed:** after `E` deploys it, the state word reads `DEPLOYED` and the count shows
  the drone's health (`30/30` fresh). Shoot it: the count falls with the drone's health.
- [ ] **Drone, destroyed:** the state word reads `DESTROYED` and the bar empties. The slot does not
  reset until respawn.
- [ ] **Camera:** throw a camera (its slot's key) and the count reads the camera's health out of
  `20`. A destroyed camera reads `DESTROYED`.
- [ ] **Fuel tank:** reads `WORN` with no bar and no count, and never reads `READY`. A detonated tank
  reads `DETONATED`.
- [ ] **Agreement with the bar:** the gadget boxes on the loadout bar and the panel show the same
  state at every moment. The bar's `ON`/`OFF`/`BROKEN`/`WORN` and the panel's `FRONT`/`REAR`/
  `DEPLOYED`/`DESTROYED`/`DETONATED` are the same facts in two vocabularies.
- [ ] **Predicted, not authoritative-lagged:** toggling the shield updates the panel on the same
  frame the bar does. Both read the predicted loadout.
- [ ] **Dead or surveilling:** while dead the panel still shows your gadgets (they survive death, the
  state resets on respawn). While piloting a drone the panel still shows the drone's state.

#### Cooldowns — decided: none

- [ ] **No countdown ever shows:** no gadget starts a cooldown, so the panel never prints a
  `1.2s`-style countdown. Every pressable gadget reads `READY` in its state word, and a fuel tank
  reads `WORN`.
- [ ] **Rapid presses behave as before:** pressing `Q`/`E` repeatedly is limited only by the
  gadget's own rules (a drone's deploy-then-pilot sequence, a shield toggle), not by a timer.

#### Floating damage numbers

- [ ] **Your hits, at the impact:** shoot an enemy. A white number of the damage applied appears
  at the point the round hit, not above the enemy's head.
- [ ] **It stays on the wall:** while the number is rising, pan with `cl_freecam` and confirm it
  stays on the spot the round landed, not on the screen.
- [ ] **Only your outgoing damage:** damage taken from others does not produce a number (the
  directional vignette covers it). Your own molotov or fuel tank does not either.
- [ ] **Headshot:** a head-zone hit prints in gold, visibly different from a body hit.
- [ ] **Killing blow:** the hit that kills prints in red, and wins over the headshot gold when both
  apply.
- [ ] **Falloff reads at a glance:** a point-blank body hit is white; a long-range body hit of the
  same weapon is visibly dimmer. A headshot at range is still gold, not dimmed.
- [ ] **Rounding:** a 23.6-damage round reads `24`. The number is the damage applied, so a headshot
  at range reads the doubled figure.
- [ ] **Short life:** a number holds for about 0.15 s, then fades and rises over about 0.85 s in
  total. A burst of hits stacks as separate numbers and does not hold the screen.
- [ ] **Capacity:** rapid fire (a full magazine of an automatic weapon) keeps the newest numbers. The
  model holds 24, dropping the oldest, so the frame never fills.
- [ ] **Off-screen hits:** a number whose impact is well outside the view is not drawn, and it does
  not reappear when you pan back before it expires.
- [ ] **Melee and utility:** a melee hit and a grenade or molotov hit you cause print a number at the
  point of impact, at full white (they have no falloff to show).
- [ ] **Legible over sky and wall:** the numbers use the console's contrast clamp and the baked font
  outline. Check one over bright sky and one over a dark interior.
- [ ] **No overlap with the HUD:** numbers draw under the kill feed, the vitals, the gadget panel,
  the loadout bar and the picker. A number over the crosshair is expected; it moves with the hit.

#### Edge cases

- [ ] **Loadout screen:** open the loadout picker from the menu. Nothing from the match draws, and no
  damage number appears there (the loadout screen has no arena to project into).
- [ ] **Resize and fullscreen:** the gadget panel keeps its place above the loadout bar, and numbers
  stay on their impacts after a resize.
- [ ] **Reconnect:** disconnect and reconnect. No numbers from the last connection remain on screen;
  anything still rising expires within a second.
- [ ] **Other widgets:** the crosshair, vitals, loadout bar, kill feed, minimap and surveillance banner
  are placed exactly as in M11 and M12.
- [ ] **Banner:** the first debug line reads `SkyStrike - M13 (HUD: gadget panel, floating damage numbers)`.

### M14 Vision Bubble — Desktop Acceptance Checklist
Full setup and historical context: [M14_VISION_BUBBLE_ACCEPTANCE.md](archive/M14_VISION_BUBBLE_ACCEPTANCE.md).

#### Checklist

- [ ] **Body bubble, all round:** stand in an open room with no enemy in front. An enemy about 100
  units behind you, with line of sight, is visible and lit, and your light shows around them.
- [ ] **Beyond the bubble:** an enemy about 200 units behind you stays dark, with no glow.
- [ ] **Occluded close enemy:** an enemy within 140 units but behind a wall or crate stays dark.
- [ ] **Light follows the bubble:** the warm light glows around the body in every direction inside
  the bubble. It fades to nothing at the bubble's edge, not at a hard cut.
- [ ] **Drone bubble:** deploy a drone. A 70-unit light and bubble surround it whichever way it
  faces. An enemy about 100 units behind it, outside its cone, stays dark.
- [ ] **Piloting:** while piloting, your body's bubble and cone are gone. The surroundings of your
  body stay dark (the body's light is gated by visibility, and the body rectangle still shows your
  silhouette). The drone's surroundings light.
- [ ] **Stuck camera:** a stuck camera you own lights a 70-unit circle around itself. A flying one
  does not.
- [ ] **Minimap agrees:** an enemy that appears on the minimap is one the screen shows lit, and
  the reverse. Check an enemy just behind you, both inside and outside the bubble.
- [ ] **Smoke:** an opaque cloud between you and an enemy inside the bubble hides them.
- [ ] **Effects:** an explosion about 100 units behind you, not hidden by walls, is shown.
  One about 300 units away behind you is not sent.
- [ ] **Light radius cvar:** at the default `r_player_light_radius 140` the light and the bubble are
  the same disc. Raising the cvar (for example to 220) enlarges the glow only where the bubble
  already sees. Light beyond the bubble is still masked by visibility, so the cvar cannot widen
  what you see.
