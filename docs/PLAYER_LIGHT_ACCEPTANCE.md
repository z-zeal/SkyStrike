# Player light unification — acceptance note

**Status: unexecuted.** Every box below is unchecked, and no part of this change has been run: the
authoring sandbox has no JDK and no GPU, so the shaders, the client and the test suites are all
unverified. Source reading confirms the code paths exist — the composite in
`assets/shaders/post/composite.frag` and `CompositePass` now adds the light buffer to the visibility
field before the multiply, the radial profile in `assets/shaders/light/light_radial.frag` is the
sight field's own quadratic, and the falloff split lives in `Light.SIGHT_FALLOFF` /
`Light.EFFECT_FALLOFF` — but **nothing below may be treated as passed until a human has seen it on
a running desktop build.**

## What changed

The reported symptom: the player/camera point light read as a bright white disc that did not blend
with the vision cone, a separate whiter light sitting on top of the sight field.

- **Compositing.** The composite was `scene × max(visibility, ambientFloor) + light`: the cone
  multiplied the scene while the light was added to the finished frame in a *different* colour, so
  it could never read as one light, and over dark terrain it saturated into a flat disc. It is now
  `scene × (max(visibility, ambientFloor) + light)` — the light buffer is illumination added to the
  field, and the two multiply the scene together. A lit surface brightens in its own colour; an
  unlit pixel stays black no matter how bright the lamp is.
- **Falloff.** The radial profile is now the sight field's own curve, `1 − (d/reach)²` — the same
  `distFactor` an observer gets in `light/visibility_cone.frag` — with `u_falloff` as an exponent
  over it. Vision lights (local player, lit remote players, M14 device bubbles) use
  `Light.SIGHT_FALLOFF = 1`, the field's curve exactly, so the lamp's gradient is the bubble's
  gradient and it fades to nothing at the bubble's edge. Effect lights (muzzle flash, explosion,
  flare) keep a tighter `Light.EFFECT_FALLOFF = 2`.
- **Unchanged defaults:** colour `(1.0, 0.94, 0.82)` (warm near-white), intensity **0.35**, radius
  **140** (= `VisionConfig.BODY_BUBBLE_RADIUS`), SDF shading on. No cvar names, ranges or defaults
  changed; the light is simply no longer added to the frame, so the intensity now scales how much
  illumination the lamp contributes. The `r_player_light_intensity` help text no longer says
  "additive".
- **Presentation only.** The visibility texture is still the gameplay mask and the light pass's own
  gate: a light can only brighten pixels the observer's field reaches, SDF occluders still cut it
  off, and `displayVisibility` still keeps the peripheral floor and absolute darkness intact. No
  wire change, no gameplay change; the server never sees any of this.
- **M14 bubbles** share the code path (`FxPipeline.syncPlayerLights` → one `upsertPlayerLight`), so
  the body bubble, drone bubble and stuck-camera bubble get the same colour, falloff and compositing
  as the cone.
- **Effect lights** (`GpuParticleSystem.spawnLight`) now use `Light.EFFECT_FALLOFF` (2) over the same
  quadratic base, i.e. a slightly wider pool than the old `(1 − d/reach)²` profile at the same peak
  intensity. They remain emissive lights that light surfaces, and are still drawn as additive
  particles *after* the composite, so the visible flash itself is unaffected.

## Checklist

- [ ] **One light, not a disc:** stand in the open and sweep the aim. The glow around the body must
  ramp smoothly into the sight field around it — no identifiable disc edge hovering over the
  terrain, and no place where the lamp reads as a second, whiter light.
- [ ] **Surface colour:** look at a lit surface (terrain, crate, another player) under the lamp. It
  should brighten and warm slightly in its own colour, not wash out towards white. Compare with
  `/r_player_light off`.
- [ ] **Black stays black:** stand beside a very dark surface inside the lamp's reach. It must not
  become grey — the light multiplies the scene, so it can only brighten what is already drawn.
- [ ] **Behind you:** turn your back on a wall and walk towards it. The glow should fall off with
  the same shape as the sight bubble and reach nothing at the bubble's edge — no hard cut, no ring.
- [ ] **Cone blend:** look down a long corridor. There must be no visible seam between the lamp
  region and the cone's illumination; the corners of the cone should not look detached from the
  glow at the body.
- [ ] **No wallhack, deliberately:** with `/r_player_light_radius 512` and
  `/r_player_light_intensity 1.0`, an enemy beyond the bubble and outside the cone stays black, and
  one behind a wall or in smoke stays black. Then `/r_player_light_shadows off`: the lamp's shadow
  edges go hard, they do not disappear.
- [ ] **Occlusion:** put a wall between the lamp and a surface; with `r_player_light_shadows on` the
  far side must stay unlit.
- [ ] **Emissive effects still read:** fire a weapon at a wall and detonate a grenade in a room. The
  flash light should brighten the surfaces it can see, and should not leak around a corner or
  through a wall.
- [ ] **Piloting:** deploy and pilot a drone. The body silhouette must remain readable while the
  surroundings of the body stay dark, and the drone's own bubble light must glow with the same
  falloff as its bubble.
- [ ] **Device bubbles (M14):** a stuck camera's bubble glows where the camera can see and fades to
  nothing at the bubble's edge, exactly like the body's.
- [ ] **Live cvars:** `/r_player_light_radius 220` and `/r_player_light_intensity 0.6` change the
  lamp immediately; a larger radius must not reveal anything outside the observer's field.
- [ ] **Resize and teardown:** resize the window and toggle fullscreen (no misalignment between the
  light and visibility targets), then leave to the menu and re-enter twice (no stale lights or GL
  errors). Pass count and cost are unchanged.

## Verification note

Nothing was run. There is no JDK in the authoring sandbox, so the shaders were not compiled, the
client was not launched, the Gradle build and the test suites were not executed, and
`taskset`/GPU inspection was impossible. GitHub Actions is the only authority on compilation and
tests. What *was* verified statically:

- `tools/scratch/static_check.py` and `static_api_check.py` are clean after the change (389 Java
  files, no unresolved references, no unused imports, module boundaries intact); the three other
  permitted checks were re-run clean as well.
- The shader change is three lines: the composite's light term became `illumination +=`, and the
  radial base became `1 - radialDistance * radialDistance`. Neither adds a uniform, a pass or a
  texture; the light buffer's format, blending and gate are untouched.
- The falloff constant rename was checked across the tree: `Light.DEFAULT_FALLOFF` had exactly four
  readers (two in `FxPipeline`, two in `GpuParticleSystem`), all updated; `Light` is core-only and
  crosses no module boundary.
