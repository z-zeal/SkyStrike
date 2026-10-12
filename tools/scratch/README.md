# tools/scratch

Throwaway Python helpers used while authoring a phase. **Nothing here is part of the Gradle
build** (`tools` is not a module in `settings.gradle`), nothing here ships, and nothing here is a
substitute for `./gradlew test`.

| script | what it does |
| --- | --- |
| `phase3_ballistics.py` | Reimplements the Phase 3 ballistics / spread / recoil model in Python and prints the resulting drop, time-of-flight, falloff and spray-vs-tap tables for all 13 weapons. Used to pick `CombatConfig.BULLET_GRAVITY_SCALE` and to sanity-check that every weapon travels further per tick than the 14-unit tunnel roof is thick. Exits non-zero if a tuning invariant breaks. |
| `static_check.py` | Repo-wide source hygiene: brace/paren/bracket balance, `package` path agreement, unused imports, import roots, tabs, trailing whitespace, final newline, and module boundaries resolved against the module that owns each imported or fully qualified project type. It also carries the map building plan's two consumer gates: no main source outside `shared/map` may name a block kind (or test one in a `switch`), and the core terrain renderer must draw every `BlockRenderStyle` the shared enum declares — both lists are read from the shared enums, so a new kind or treatment is checked without editing this file. |
| `static_api_check.py` | Indexes every type in the repo and verifies that each *static* call (`Foo.bar(...)`), constructor (`new Foo(...)`) and static field reference resolves to a real declaration with a compatible arity, including varargs and nested types. Also resolves the unqualified calls inside the generated weapon tables, where every `put(table, …)` row must find the `put` its own generator template writes: `SpriteScales.java` once shipped 110 rows without that helper, and javac reported it 110 times before this check saw it once. Catches the "method does not exist / wrong number of arguments" class of error that only javac would otherwise find. |
| `synth_sfx.py` | Phase 9 sound generator: deterministically synthesises the world and status WAVs under `assets/sfx/world/` and `assets/sfx/status/`, plus `assets/sfx/world-sfx.json`. `--check` re-verifies format, duration, peak and the loop seam of every generated file. |
| `arena_sdf_check.py` | Re-bakes the shipped SDF from the committed map text (`StandardArenaMap.TEXT`) and compares it with `assets/data/arena.sdf` field for field: the header's map hash, dimensions and scale, then every texel. The asset is derived data whose header names the map it came from, and editing the map without re-baking it is the mistake this catches. `--write` regenerates the asset instead, for an authoring sandbox with no JDK. |
| `arena_reachability_check.py` | Re-implements the milestone-5 reachability gate (`MapReachability`) from the committed Java: the walkable grid, the M8 step and 40%-fuel hop envelope, the two manoeuvre shapes, the per-spawn closures. Prints the shipped arena's verdict, re-runs it at half the pitch and with a door read as a solid panel, then runs the maps `shared`'s own tests build (`--fixtures`) and five probe maps the gate has to catch. Exit non-zero if a probe's expected verdict does not come back. |
| `static_audio_check.py` | Phase 9 audio catalogue: every catalogue row names a real WAV in the project format with a matching duration, every `EffectType` has a switch case, and both manifests list exactly the shipped files — no missing asset and no orphan. |

Run them with plain `python3`, from the repository root:

```sh
python3 tools/scratch/phase3_ballistics.py
python3 tools/scratch/static_check.py
python3 tools/scratch/static_api_check.py
python3 tools/scratch/synth_sfx.py --check
python3 tools/scratch/static_audio_check.py
python3 tools/scratch/arena_sdf_check.py
python3 tools/scratch/arena_reachability_check.py [--fixtures]
```

These are approximations of a compiler, not a compiler. They exist because the environment that
wrote Phase 3 had no JDK; CI remains the only authority on whether the code builds.
