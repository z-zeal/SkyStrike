# M6 Lighting — Desktop Acceptance Checklist

These checks require a runnable desktop build with a local match. They were documented for manual
verification; source-only sandbox checks do not prove shader compilation or visual behavior.

> **M12 update:** the player light now ships **on by default** and its cvars are real graphics
> settings, registered in every build — no dev console or debug master switch needed. `/r_player_light
> off` turns it off, F10 still toggles it in dev builds (`-Dskystrike.debug=true` / `--dev`), and
> `/r_player_light_shadows off` disables the lights' SDF occlusion sampling. The defaults are a
> 140-unit radius, 0.35 intensity, and SDF shadows enabled. `r_player_light_radius` accepts 32–512
> units and `r_player_light_intensity` accepts 0–1. The checks below were written for the opt-in
> version; the "enable" step is now "confirm it is on, and toggle it off to compare".

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
