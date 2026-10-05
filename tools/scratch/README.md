# tools/scratch

Throwaway Python helpers used while authoring a phase. **Nothing here is part of the Gradle
build** (`tools` is not a module in `settings.gradle`), nothing here ships, and nothing here is a
substitute for `./gradlew test`.

| script | what it does |
| --- | --- |
| `phase3_ballistics.py` | Reimplements the Phase 3 ballistics / spread / recoil model in Python and prints the resulting drop, time-of-flight, falloff and spray-vs-tap tables for all 13 weapons. Used to pick `CombatConfig.BULLET_GRAVITY_SCALE` and to sanity-check that every weapon travels further per tick than the 14-unit tunnel roof is thick. Exits non-zero if a tuning invariant breaks. |
| `static_check.py` | Repo-wide source hygiene: brace/paren/bracket balance, `package` path agreement, unused imports, import roots, tabs, trailing whitespace, final newline, module boundary rules. |
| `static_api_check.py` | Indexes every type in the repo and verifies that each *static* call (`Foo.bar(...)`), constructor (`new Foo(...)`) and static field reference resolves to a real declaration with a compatible arity, including varargs and nested types. Catches the "method does not exist / wrong number of arguments" class of error that only javac would otherwise find. |

Run them with plain `python3`, from the repository root:

```sh
python3 tools/scratch/phase3_ballistics.py
python3 tools/scratch/static_check.py
python3 tools/scratch/static_api_check.py
```

These are approximations of a compiler, not a compiler. They exist because the environment that
wrote Phase 3 had no JDK; CI remains the only authority on whether the code builds.
