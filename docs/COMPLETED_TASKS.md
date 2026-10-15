# SkyStrike — completed work (summary)

> One place for everything that has **landed**. Compiled 2026-10-12 from `origin/main` history
> (262 commits) and the status lines in each doc. Companion file: `docs/NOT_DONE.md`.
>
> **What "completed" means here.** Merged to `main` (or, for planning documents, written and
> committed on `arena/365d2221-skystrike`). **Nothing below has been compiled, run, or observed in
> the authoring sandbox** — it has no JDK or Gradle. Where a doc says "written and not executed",
> that is the status recorded here. The only runtime evidence in the docs is one live run of
> map milestone 3 on the user's desktop (see `archive/MAP_BUILDER_PLAN.md` §9, milestone 3).

---

## 1. Engine foundations — Phases 0–2

| Work | Evidence |
| --- | --- |
| Four-module skeleton, tick loop, transport, router, handlers | `4a0122e`, `7259af1` |
| Player movement, rotation dynamics, aim, client prediction | `50849e9` |
| Vision, fog of war, SDF bake and sampler, shaders, authoritative culling | `15f915b` (PR #5) |
| Base package unified on `io.github.skystrike` | `04708f6` |
| Module boundaries enforced by a static check | `4d3dffb` |

## 2. Combat and weapons — Phases 3–4

| Work | Evidence |
| --- | --- |
| Ballistics, hit zones, spread, recoil, fire modes | `8cefb16` |
| Weapons, melee and loadout slots | `c71cff7` |
| Sprite catalogue: 13-gun roster replaced by **89 guns and 21 melee**, held weapons rendered | `e50fbaa` |
| Per-weapon muzzle to the drawn barrel | `41fba39` |
| Ballistics tuning (big-map flight, pistol trajectories, sidearm rebalance) | `9a44d82`, `286de4d`, `30befa2` |
| Melee knockback from stun only | `b811bba` |
| Server loadout refusal diagnostics | `7cfe486` |

## 3. Throwables, utility and gadgets — Phases 5–6, and M10

| Work | Evidence |
| --- | --- |
| Shared throwable foundation and lifecycle (occluded explosions, stun, zones) | `abbdee4`, `5a9bdf2` |
| Utility inventory and wire contract | `08246b3` |
| Gadget foundation: identity, configuration, live slot state, shield arc maths | `402b75b` |
| Worn gadgets authoritative on the server | `751ec69` |
| **M10** shared gadget-device contract, drone, surveillance lock, view cycling, camera server, gadget rendering | `be93500`, `a8b8077`, `e222ecb`, `decc11e` |

## 4. Client UI, menus and debug tools

| Work | Evidence |
| --- | --- |
| **M1 + M2** debug backbone, command core, one-key console/chat dialog | `0101093` |
| Text foundation, chat transport, server-authoritative chat, capability gating | `9fed8da`, `338119b`, `e8e4007`, `a043c78` |
| **M3** debug toolkit: F1–F12 keys bound to cvars and commands, cheat sessions, timescale, dumpstate | `4e0cc15` |
| Built-in dev account, gated on the debug master switch | `78884ad` |
| Embedded local test menu (debug) | `9c8769d` |
| **M4** HUD (`core/ui/hud`) and shared read models | `221d483` |
| **M5** menus: main menu shell, settings, connecting flow, loadout picker, Scene2D controls, in-game settings dialog, persistent bindings | `062be6a` … `3f239de`, `c2eb2c6`, `98ecce9`, `a72e165`, `5367647` |
| Crosshair is the desktop cursor | Recorded in `PHASE7_AUDIT.md` §6 |
| **M13** gadget panel and floating damage numbers | `4febee0` |
| Held utility rendering correction | `39c09cc` |
| Command-line dialog fixes (slash usage, Damerau suggestions, lowercase enums) | `bdeffbd` |

## 5. Presentation — M6–M9, M11, M12, M14

| Work | Evidence |
| --- | --- |
| **M6** player lighting pass; silhouette readable under fog | `34c64b5`, `503c70a` |
| **M7** FX event transport and particles | `0d69d56` |
| **M8** arena traversal rework | `ec9178f` |
| **Phase 9 / M9** audio: one mixer, one catalogue, server-authenticated gunfire cues, world sound pack, firearm SFX library | `5205104`, `4e37ea9`, `ecb7ab3`, `1685db6` |
| **M11** fog-gated minimap | `ec1134d` |
| **M12** player light on by default; utility visibility | `26fc2e8` |
| **M14** vision bubble (the circular player light acts as vision) | `d3a85f8` |
| Vision clarity, gunfire sync and drone handling | `33d9dc2` |
| Line-of-sight answer fixed (was inverted) | `be0dc39` |
| Molotov fire stops spread at terrain | `b8a934c` |
| Glass shatter effect for a pane letting go | `0424577` |

## 6. Map building system — milestones 1–5 and the fixture door

Source of truth: `docs/archive/MAP_BUILDER_PLAN.md` §9 and `docs/PHASE7_AUDIT.md` §6.

| Milestone | What landed | PRs and evidence |
| --- | --- | --- |
| **1. Model and format** | `BlockKind`, `Material`, `MapBlock`; shared rule table; strict version-1 text format; `ArenaMap` loads the embedded canonical text; SDF version 2 map-hash guard | PR #47 |
| **2. Rule-aware consumers** | Line queries take a `Blocking` purpose; sight, bullet, blast and movement consumers ask their own column; one-way platform tops; kind-aware terrain rendering; consumer gates in the static check | PR #48, #49 (`daada30`, `b85509d`, `34c15bb`, `05c2582`) |
| **3. Breakables and glass** | Durability, three damage sources (round, blast, swing), destruction state, re-bake worker and changed-region patch; shipped arena gains a mirrored fixture range; SDF re-baked | PR #51, #52 (`765f66d`, `99a04b6`, `78962c8`, `b10c2f8`, `858e1c3`, `36a8476`, `f4a6db5`) |
| **3, first live run** | Finding F2 fixed in source; findings F1, F3 and F4 traced, not all fixed | `PHASE7_AUDIT.md` §6, "first live run triaged" |
| **4. Destruction wire** | **Protocol 14** — block break event, join baseline, snapshot revision; client mirror | PR #54 (`a13249c`, `c515be4`, `ee2f5be`, `19b0fae`) |
| **4. Doors** | Door data (id, access, kind); door state machine; use key; server `DoorSystem` authority; **protocol 15** door-state wire; dynamic occluders in visibility and light shaders | PR #55, #56 (`dfaadf4`, `2a513db`, `9bf032b`, `31be177`, `6e461fc`, `79299b1`, `5428deb`, `d18d013`) |
| **4. Close-out A** | **Protocol 16** — server refuses a join on a different map (map-hash gate) | PR #59 (`df29f43`) |
| **4. Close-out B** | **Protocol 17** — per-hit crack sync | PR #60 (`ce571ae`) |
| **4. Close-out C** | **Protocol 18** — round-reset event; `/mapreset` runs it | PR #61 (`d83dd17`) |
| **4. Close-out D** | Fixture door pair authored into the shipped arena; re-baked | PR #62 (`4e3f22d`) |
| **4. Close-out E** | Fixture door moved out of the sealed alcove to a reachable position; re-baked; hash `ed6b335d…e173e` | PR #66 (`dde8b9e`) |
| **5. Editor and CLI, slice A** | `MapReachability` (the gate) and `MapDraft`; reachability fixture pin | PR #63 (`c218573`, `1c16007`) |
| **5, slice B** | `tools` as a Gradle module; `MapAudit` check/bake core; `MapTool check|bake`; CI runs `tools:mapCheck` and `tools:mapBakeCheck`; decided: fail on disconnected spawns, warn on a sealed pocket | PR #64 (`a54506a`, `26bcae2`, `d735716`) |
| **5, slice C** | `MapPalette`; `MapEditorScreen`, behind the debug switch only; saves to the user's directory through the canonical writer | PR #65 (`1e1ab91`, `0d1477a`, `fc1d567`), merged as `e8759bf`; follow-up `eeae88a` |

**Protocol history (current: 18; `NetConfig.PROTOCOL_VERSION = 18`):**

| Version | Change |
| --- | --- |
| 13 | Baseline before the map work |
| 14 | Destruction sync |
| 15 | Door state |
| 16 | Map-hash join gate |
| 17 | Crack sync |
| 18 | Round-reset event |

**Shipped-arena facts at last change:** door at `837 100 36 170 id=1 access=ALL`; mirror at 2127..2163. Reachability: both spawns reach each other; 10 stances are reachable from no spawn, all in the fixture alcove.

## 7. Architecture, CI and docs maintenance

| Work | Evidence |
| --- | --- |
| Stale Markdown docs rebaselined against the source tree | `338919a` |
| Phase-by-phase roadmap; base package unified | `04708f6` |
| Game design and architecture docs | `770e959` |
| Build hygiene: JUnit Platform launcher on the test runtime classpath | `c715bae` |
| Tests: shared and server suites; CI runs `shared:test` and `server:test` | Roadmap §0 |
| Scratch static checks made clean on their own baseline | `f5ac365` |
| Weapons tables generated from the sprite catalogue (`tools/weapons/generate_weapon_tables.py`) | `docs/WEAPONS_TABLE.md` |

## 8. Planning documents written this session (not implemented)

These are plans, so they count as completed only as documents. No phase in them has started.

| Document | Commit | Status |
| --- | --- | --- |
| `docs/MAP_BUILDER_V2_PLAN.md` | `50ad383` | Written. Decisions D1–D8 open. |
| `docs/SERVER_LOBBY_PLAN.md` | `3839bb8`, decision in `02fdd00` | Written. L1 decided; L2–L10 open. |
| `docs/archive/MULTIPLAYER_MENU_PLAN.md` | `1e90d76`, decisions in `02fdd00`, link in `316a765` | Written. M1, M2, M5 decided; M3, M4, M6–M9 open. |
| `docs/ACCOUNTS_PLAN.md` | `316a765`, decisions in `f7239d6` | Written. A1, A3 decided; A2, A4–A8 open. |
| `PROTOCOL_VERSION` | — | Unchanged at 18 by all four plans. |
