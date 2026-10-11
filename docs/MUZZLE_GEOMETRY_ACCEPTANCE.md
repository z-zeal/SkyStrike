# Muzzle geometry — acceptance note

**Status: unexecuted.** Every box below is unchecked, and no part of this change has been run:
the authoring sandbox has no JDK, so the shared and server tests, the client and the shaders are
all unverified. Source reading confirms the code paths exist — one per-weapon muzzle point in
`shared/weapons/HeldWeaponGeometry`, derived from the generated `SpriteScales` table (from
`assets/sprites/{guns-scale,melee-scales}.json`), consumed by `BulletSystem.spawn`,
`FireController.emitMuzzleEffects` and `PlayerRenderer`'s barrel line — but **nothing below may be
treated as passed until a human has seen it on a running desktop build.**

## What changed

The muzzle was one constant, `CombatConfig.MUZZLE_OFFSET = 22f`, documented as "matching the drawn
barrel" and consumed by the round spawn, the muzzle flash and shell eject, and the client's
barrel-line fallback. It matched no barrel but a compact pistol's: on a sniper the flash and the
tracer left the eye at 22 units while the drawn gun's barrel tip was ~62 units out, so the flash
appeared near the body's centre.

- **One derivation, in `shared`.** `HeldWeaponGeometry.muzzleDistance(wireId)` is
  `HAND_FORWARD_OFFSET + frameSize × (SPRITE_MUZZLE_FRACTION − gripAnchorX)`: the hand offset, plus
  the length of the sprite's art ahead of the grip. `frameSize` is the catalog scale
  (`SpriteScales`, generated) times 46 world units for a gun or 49 for melee, times the global 1.5
  readability multiplier. Every shipped sprite fits its art to the frame from x = 28 to x = 483 of
  512, so the barrel tip is the same fraction of the frame (0.943) for every weapon — what differs
  is how large that frame is drawn.
- **One muzzle point, three consumers.** `BulletSystem.spawn` places the round there,
  `FireController.emitMuzzleEffects` places the flash and the shell there, and
  `PlayerRenderer`'s missing-sprite barrel line is drawn to the same length. The drawn sprite is
  unchanged: it already used `frameSize`/`gripAnchorX`, now read from the shared class instead of a
  copy in `WeaponSprites`. There is no second implementation of the maths.
- **Derived distances** (world units from the eye, along the aim): a 0.42-scale pocket pistol ≈ 22
  (the old constant), the reference carbine ≈ 46, a compact SMG ≈ 36, the Short Gospel ≈ 32, the
  Twin Barrel ≈ 57, Long Silence ≈ 58, the Cathedral ≈ 62. Melee tips, used only by the fallback
  line, run from ≈ 26 (paring knife) to ≈ 118 (naginata).
- **No wire change.** The value is derived, so nothing new crosses the network and
  `PROTOCOL_VERSION` stays 13. `CombatConfig.MUZZLE_OFFSET` is gone.
- **The terrain guard is unchanged and matters more.** `BulletSystem.spawn` still tests the
  eye-to-muzzle segment with `blockedByTerrain`, so a shot fired while hugging a wall is absorbed
  rather than appearing beyond it. With a longer barrel the round now starts further out, so the
  guard is doing more work, not less.

## Checklist

- [ ] **Barrel flash, pistols:** fire a small pistol and confirm the flash and the shell sit at the
  visible barrel tip, not ahead of it or behind it.
- [ ] **Barrel flash, long guns:** fire the reference carbine and a sniper (Long Silence, Cathedral).
  The flash must sit at the drawn muzzle, not at the body's centre; a rifle's flash should read as
  leaving the barrel the sprite draws, especially while aiming down sights.
- [ ] **Tracers agree:** the tracer and the impact come off the same point the flash does. Fire
  along a wall at range and confirm the tracer does not appear to start inside the body.
- [ ] **Melee fallback only:** this is a barrel-line fallback for missing art. With art present, the
  melee sprite is unchanged and no line is drawn.
- [ ] **Ads and recoil:** aiming down sights and firing a burst leaves the flash on the barrel; the
  recoil kick moves the drawn gun (and therefore the visual barrel) without moving the round's
  path, which still follows the un-kicked aim.
- [ ] **Wall hug still absorbs:** stand touching a wall, aim into it, and fire. No round, no damage,
  no impact spark, and no shot that appears on the far side of the wall. A longer gun must not be
  able to shoot past the wall it is standing against.
- [ ] **Clear space for a long barrel:** with a sniper, stand right under a low roof (the tunnel,
  the crate tops) and fire upward. The shot is legitimately absorbed when the barrel itself is in
  the roof; check that this reads as "the barrel is in the wall", not as a broken gun.
- [ ] **Straight down from the floor:** stand on the ground and aim straight down. A long gun
  (a rifle or a sniper) fires nothing, because the muzzle is drawn inside the floor and the guard
  absorbs the shot at it; a pistol's shorter barrel still fires and the round dies on the floor.
  Neither case may put a round below the floor, and the absorbed shot must not read as a broken
  gun any more than the low-roof case above does.
- [ ] **Point-blank shots (known trade-off):** a round now leaves the *barrel tip*, so a body
  closer to the eye than the barrel length is no longer inside the first sweep. With a carbine or
  a sniper, fire at an enemy about 30 units directly ahead and decide whether the resulting miss
  reads as a bug. If it does, the fix is a clip or an eye-origin first sweep, not a shorter muzzle.
- [ ] **Missing sprite:** run with a weapon whose PNG is absent and confirm the fallback line is
  drawn to the weapon's own muzzle length instead of a pistol's.

## Verification note

Nothing was run. No JDK exists in the authoring sandbox, so `shared:test`, `server:test`,
`compileJava`, the shaders and the client are all unexecuted; GitHub Actions is the only authority
on compilation and tests. What *was* verified statically:

- `tools/scratch/static_check.py`, `static_api_check.py`, `static_audio_check.py`,
  `synth_sfx.py --check` and `phase3_ballistics.py` are clean after the change.
- **The one generated file this change added did not compile.** `SpriteScales.java` is written by
  `gen_sprite_scales`, whose rows call a private `put(table, id, scale)` — and that template, alone
  among the five generated tables, never emitted the helper, so every row was a
  `cannot find symbol: method put(Map<WeaponId,Float>,WeaponId,float)`. The generator now emits one
  `put` per map, next to the `buildGuns`/`buildMelee` it serves.
- **Why the static checks missed it, and what changed.** `static_api_check.py` treated the row
  `put(table, …);` as a *declaration* — a line that is nothing but a name and a `(` matched its
  signature pattern, and enum constants like `IRON_SIDEARM("Iron Sidearm", "iron-sidearm"),` were
  indexed the same way — so the call appeared to declare the method it called. The indexer now
  requires a declaration to carry modifiers, a return type, or the type's own name, and the tool
  resolves unqualified calls inside generated files against the declarations of that same file. It
  reports one aggregated finding (`put(...) is called but this generated file declares no put
  (x110)`) on the file as merged and is clean on the fixed one.
- `tools/weapons/generate_weapon_tables.py` re-run reproduces every generated file byte-for-byte,
  including `SpriteScales.java` (89 guns, 21 melee) and `docs/WEAPONS_TABLE.md`.
- The barrel tip fraction and the uniform horizontal art fitting were measured across all 110
  shipped gun and melee sprites in this sandbox, and the muzzle distances above were computed from
  the catalogs by hand.
