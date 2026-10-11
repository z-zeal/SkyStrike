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

Dependency direction is enforced by the `checkModuleDependencies` Gradle task and mirrored in
`tools/scratch/static_check.py`: `shared` must not reference `core` or `server`; `server` must not
reference LibGDX or `core`; `core` must not reference `server`.

Tests live in `shared/src/test` and `server/src/test`, so CI can run them.

## Status

Phases 0–7 of `docs/IMPLEMENTATION_ROADMAP.md` have landed source; phases 8–11 are partly landed.
`NetConfig.PROTOCOL_VERSION` is **13**. Progress chronology is in `docs/PHASE7_AUDIT.md` §6, and
current-state summaries are the rebaselined `docs/IMPLEMENTATION_ROADMAP.md` §0 and
`docs/PLAYABLE_BUILD_PLAN.md` §0.

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
```

The debug master switch is off by default; enable it with `-Dskystrike.debug=true`, the
`SKYSTRIKE_DEBUG=1` environment variable, or `--dev` (see `shared/config/DebugFlags`). The desktop
menu then offers a local test host (`core/LocalHost`).

When no JDK is available, `python3 tools/scratch/static_check.py`,
`static_api_check.py`, `static_audio_check.py`, `synth_sfx.py --check` and
`phase3_ballistics.py` are the local approximations. They are not a compiler and not a substitute
for CI.

## Documentation

- `docs/MECHANICS_PLAN.md` — what the game is (the spec).
- `docs/PROJECT_STRUCTURE.md` — where code goes.
- `docs/IMPLEMENTATION_ROADMAP.md` — build order and phase gates.
- `docs/PLAYABLE_BUILD_PLAN.md`, `docs/EFFECTS_PLAN.md`, `docs/CONSOLE_CHAT_PLAN.md` — subsystem
  build plans (plans, not status trackers).
- `docs/PHASE7_AUDIT.md` — the Phase 7 audit and the chronological record of what has landed since.
- `docs/M6_…`–`docs/M14_…_ACCEPTANCE.md`, `docs/DEBUG_LOCAL_TEST.md`,
  `docs/VISION_AUDIO_DRONE_ACCEPTANCE.md` — manual desktop acceptance checklists, all currently
  unexecuted.
- `docs/WEAPONS_TABLE.md` — generated weapon tables.
