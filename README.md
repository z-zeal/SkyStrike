# SkyStrike

A 2D multiplayer jetpack arena shooter with fog of war, built on [libGDX](https://libgdx.com/).

The client renders only what the authoritative server says a player can see: an SDF-baked
visibility cone with an all-round close-range "vision bubble", smoke that subtracts sight,
terrain-occluded explosions, and a fog gate the minimap obeys. The simulation is plain Java and
shared verbatim with the client for prediction.

## Modules

| Module | Role |
| --- | --- |
| `shared` | Everything both sides need: config, maths, physics, ballistics, maps, vision, wire packets. No LibGDX. |
| `server` | Authoritative simulation: tick loop, combat, throwables, gadgets, chat and commands. Plain Java, no game framework. |
| `core` | Client: rendering, fog/lighting pipeline, HUD, console/chat dialog, menus, networking and prediction. |
| `lwjgl3` | Desktop launcher (`lwjgl3:run`). |
| `android` | Android launcher (needs the Android SDK). |
| `tools` | Headless map tooling: `MapTool check|bake` and the SDF bake tool. Depends on `shared` only, no LibGDX. |

Dependency direction is enforced by the `checkModuleDependencies` Gradle task and mirrored in
`tools/scratch/static_check.py`: `shared` must not reference `core` or `server`; `server` must not
reference LibGDX or `core`; `core` must not reference `server`.

Tests live in `shared/src/test` and `server/src/test`, so CI can run them.

## Status

Phases 0–7 of `docs/archive/IMPLEMENTATION_ROADMAP.md` have landed source; phases 8–11 are partly landed.
`NetConfig.PROTOCOL_VERSION` is **18**. Progress chronology is in `docs/PHASE7_AUDIT.md` §6.
Use [COMPLETED_TASKS.md](docs/COMPLETED_TASKS.md) for the landed-work summary and
[NOT_DONE.md](docs/NOT_DONE.md) for outstanding work. Archived plans are historical design
references and may contain stale status snapshots.

Nothing has been verified at runtime here: there is no JDK in the authoring sandbox, so the game,
the server, Gradle and every manual acceptance checklist (M6–M14, `docs/DEBUG_LOCAL_TEST.md`,
`docs/VISION_AUDIO_DRONE_ACCEPTANCE.md`, the mechanics §11 feel checklist) remain **unexecuted**.
GitHub Actions (`shared:test`, `server:test`, `*:compileJava`, `checkModuleDependencies`) is the
only authority on compilation and tests.

## Building and running

Use the Gradle wrapper:

```sh
./gradlew shared:test                 # shared unit tests
./gradlew server:test                 # server unit tests
./gradlew checkModuleDependencies     # module-boundary gate
./gradlew lwjgl3:run                  # desktop client
./gradlew server:run                  # dedicated server
./gradlew lwjgl3:jar                  # runnable desktop jar in lwjgl3/build/libs
./gradlew tools:mapCheck              # CI's map gate: reachability over the shipped arena
./gradlew tools:mapBakeCheck          # CI's bake gate: assets/data/arena.sdf is that map's bake
./gradlew tools:mapTool --args="check maps/arena.map"   # the map CLI itself (§6 of the plan)
```

The debug master switch is off by default; enable it with `-Dskystrike.debug=true`, the
`SKYSTRIKE_DEBUG=1` environment variable, or `--dev` (see `shared/config/DebugFlags`). The desktop
menu then offers a local test host (`core/LocalHost`) and the map editor
(`core/screens/MapEditorScreen`, plan §6), which places mirrored blocks on a map and writes `.map`
and `.sdf` files into the user's own directory — never into the repository, and never into a match.

Maps are audited and baked headlessly: `tools:mapCheck` runs the shared reachability gate over the
map this build ships (it fails when the map's spawns cannot reach each other and warns, naming every
stance, when the map is merely not fully reachable), and `tools:mapBakeCheck` re-bakes that map in
memory and compares it byte-for-byte with `assets/data/arena.sdf`. `MapTool check <file.map>` audits
any map file, and `MapTool check --strict` asks the stricter reading of §7.

When no JDK is available, `python3 tools/scratch/static_check.py`,
`static_api_check.py`, `static_audio_check.py`, `synth_sfx.py --check` and
`phase3_ballistics.py` are the local approximations. They are not a compiler and not a substitute
for CI.

## Documentation

- [NOT_DONE.md](docs/NOT_DONE.md) — active backlog, remaining decisions and verification work;
  includes an archive index mapping the former plan paths.
- [COMPLETED_TASKS.md](docs/COMPLETED_TASKS.md) — landed-work summary (not runtime verification).
- `docs/ANDROID_PLAN.md`, `docs/ACCOUNTS_PLAN.md`, `docs/MAP_BUILDER_V2_PLAN.md`,
  `docs/SERVER_LOBBY_PLAN.md` — active plans excluded from the consolidation.
- `docs/archive/` — preserved plans and historical references, including the effects and
  multiplayer-menu designs whose outstanding work is now tracked in `NOT_DONE.md`.
- `docs/archive/MECHANICS_PLAN.md` — what the game is (the spec).
- `docs/PROJECT_STRUCTURE.md` — where code goes.
- `docs/archive/IMPLEMENTATION_ROADMAP.md` — build order and phase gates.
- `docs/archive/PLAYABLE_BUILD_PLAN.md`, `docs/archive/EFFECTS_PLAN.md`, `docs/archive/CONSOLE_CHAT_PLAN.md` — subsystem
  build plans (plans, not status trackers).
- `docs/PHASE7_AUDIT.md` — the Phase 7 audit and the chronological record of what has landed since.
- `docs/archive/M6_…`–`docs/archive/M14_…_ACCEPTANCE.md` — original milestone acceptance
  scripts; all 233 pending checks are tracked in `docs/NOT_DONE.md` §10.
- `docs/DEBUG_LOCAL_TEST.md`, `docs/VISION_AUDIO_DRONE_ACCEPTANCE.md` and other remaining
  acceptance checklists — manual desktop checks, currently unexecuted.
- `docs/WEAPONS_TABLE.md` — generated weapon tables.
