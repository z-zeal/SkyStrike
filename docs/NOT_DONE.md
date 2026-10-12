# SkyStrike — not done (summary)

> What has **not** landed, compiled 2026-10-12. Companion file: `docs/COMPLETED_TASKS.md`.
> The four planning documents written this session (`MAP_BUILDER_V2_PLAN.md`,
> `SERVER_LOBBY_PLAN.md`, `MULTIPLAYER_MENU_PLAN.md`, `ACCOUNTS_PLAN.md`) are excluded here by
> request. Their open decisions are listed in `COMPLETED_TASKS.md` §8.
>
> **Global gap:** nothing in the game has been compiled or run in the authoring sandbox (no JDK,
> no Gradle). Every "not executed" item below stays open until a human runs it on a desktop build.

---

## 1. Map building system

| Item | State | Source |
| --- | --- | --- |
| **Milestone 6** — move the arena to `assets/maps/arena.map`; map list as a server setting | Not started. Do not start unless asked. | `MAP_BUILDER_PLAN.md` §9 |
| Physical `assets/maps/arena.map` move (deferred from milestone 1) | Not done. Arena text still lives in `StandardArenaMap.java`. | `MAP_BUILDER_PLAN.md` §9, milestone 1 |
| **§11.1 Glass** — movement-only (D5) or see-through-but-blocking, or another variant | Open decision | `MAP_BUILDER_PLAN.md` §11 |
| **§11.3 Door access** — manual only, or also "open for the owning team" at spawn | Open decision. No `TEAM` door is authored, so the team-refusal acceptance box has nothing to test. | `MAP_BUILDER_PLAN.md` §11 |
| Acceptance checklist for the map builder | 39 unchecked boxes; header says "unexecuted" | `MAP_BUILDER_ACCEPTANCE.md` |
| 10 stances unreachable from either spawn, all in the fixture alcove | Known and named by a warning. Not a fix. | `PHASE7_AUDIT.md` §6, fixture slice E |
| Stale passages in `MAP_BUILDER_PLAN.md`, not yet edited: TerrainRenderer door drawing; the protocol-16 map-hash guard missing from "where the map comes from"; the fixture door position; the persistence section; §3 claims `DoorOccluders` and the first door are unbuilt (they are built); §5 claims door ids are rejected (they are not); §4 and D3 say `E` where the code uses `F`; §7 strict reading versus the policy decided in §11.4; §9 milestone 3 and 5 PR status; §11.4 alcove wording after the door move; §11.1 glass status; §7 header test-directory note | Docs debt. Awaiting direction. | `MAP_BUILDER_PLAN.md` |

## 2. Phase 7 — HUD, dialog and console

| Item | State | Source |
| --- | --- | --- |
| `ServerConsole` — the server's stdin terminal front-end | Not landed | `IMPLEMENTATION_ROADMAP.md` §0; `PHASE7_AUDIT.md` §5 D4 |
| Mobile polish: soft keyboard, quick-chat wheel | Not landed | `IMPLEMENTATION_ROADMAP.md` §0 |

## 3. Phase 8 — effects

| Item | State | Source |
| --- | --- | --- |
| Decals | Not landed | `IMPLEMENTATION_ROADMAP.md` §0 |
| Shockwave | Not landed | `IMPLEMENTATION_ROADMAP.md` §0 |
| Heat haze | Not landed | `IMPLEMENTATION_ROADMAP.md` §0 |
| Bloom | Not landed | `IMPLEMENTATION_ROADMAP.md` §0 |
| Camera shake | Not landed | `IMPLEMENTATION_ROADMAP.md` §0 |
| Mobile hardening of effects | Not landed | `IMPLEMENTATION_ROADMAP.md` §0 |
| Effects-plan infrastructure: profiler and debug overlay | Not built. `FxBudget`, `FxClock` and `FxEventQueue` did land. | `PHASE7_AUDIT.md` §5 D5 |

## 4. Phases 10–11 — platform and mobile

| Item | State | Source |
| --- | --- | --- |
| `core/platform/` | Not landed | `IMPLEMENTATION_ROADMAP.md` §0 |
| Touch controls | Not landed | `IMPLEMENTATION_ROADMAP.md` §0 |
| Quick chat | Not landed | `IMPLEMENTATION_ROADMAP.md` §0 |
| Scoreboard and round flow | Not landed. Round reset exists on the wire (protocol 18); no round state machine exists. | `IMPLEMENTATION_ROADMAP.md` §0; `MAP_BUILDER_PLAN.md` §11.2 |
| Mobile hardening | Not landed | `IMPLEMENTATION_ROADMAP.md` §0 |

## 5. Runtime verification (everything shipped)

Each feature milestone has an acceptance checklist. Every one says "unexecuted." The counts are
unchecked boxes in each file.

| Checklist | Unchecked boxes |
| --- | --- |
| `M6_LIGHTING_ACCEPTANCE.md` | 6 |
| `M7_FX_ACCEPTANCE.md` | 23 |
| `M8_ARENA_ACCEPTANCE.md` | 12 |
| `M9_AUDIO_ACCEPTANCE.md` | 24 |
| `M10_GADGETS_ACCEPTANCE.md` | 54 |
| `M11_HUD_ACCEPTANCE.md` | 56 |
| `M12_UTILITY_VISIBILITY_ACCEPTANCE.md` | 14 |
| `M13_HUD_ACCEPTANCE.md` | 33 |
| `M14_VISION_BUBBLE_ACCEPTANCE.md` | 11 |
| `CROSSHAIR_POINTER_ACCEPTANCE.md` | 13 |
| `MUZZLE_GEOMETRY_ACCEPTANCE.md` | 10 |
| `PLAYER_LIGHT_ACCEPTANCE.md` | 12 |
| `DEBUG_LOCAL_TEST.md` | 7 |
| `MAP_BUILDER_ACCEPTANCE.md` | 39 |
| `VISION_AUDIO_DRONE_ACCEPTANCE.md` | no checkboxes; a manual list, unexecuted |
| `MECHANICS_PLAN.md` §11 feel checklist | 12 lines, unexecuted |

`ADMIN_ACCESS_FLOW.md` has 7 boxes, all ticked. It is not in this table.

## 6. Documentation debt

| Doc | Stale claim |
| --- | --- |
| `IMPLEMENTATION_ROADMAP.md` §0 | Says `PROTOCOL_VERSION = 13` and describes the tree at `a293f80`. The live value is 18. |
| `PLAYABLE_BUILD_PLAN.md` | Status line is as of `a293f80`. Later map and gadget work is not reflected. |
| `PHASE7_AUDIT.md` §5 | Historical table. Its own header says D1–D4 are resolved. Kept as is, because §6 is append-only. |
| `MAP_BUILDER_PLAN.md` | Stale passages listed in §1 above. |

## 7. Protocol

| Item | State |
| --- | --- |
| Wire changes for lobbies, accounts, room flow | None made. Each depends on a plan phase that has not started. |
| `PROTOCOL_VERSION` | 18, unchanged |
