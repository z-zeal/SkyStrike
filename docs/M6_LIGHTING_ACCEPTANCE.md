# M6 Lighting — Desktop Acceptance Checklist

These checks require a runnable desktop build with a local match. They were documented for manual
verification; source-only sandbox checks do not prove shader compilation or visual behavior.

Enable the client development console (`-Dskystrike.debug=true` or the existing client dev switch),
then use `/r_player_light on`. The player-light defaults are a 140-unit radius, 0.35 intensity, and
SDF shadows enabled. `r_player_light_radius` accepts 32–512 units and
`r_player_light_intensity` accepts 0–1.

- [ ] **Local silhouette:** In a dark corner, enable the player light and confirm the local player
  remains readable while the surrounding scene still follows the vision/fog rules.
- [ ] **Wall occlusion:** Put a solid crate or wall between the light and a surface. With
  `r_player_light_shadows on`, confirm the far side stays occluded. Toggle `r_shadows off`: the
  shadow edge should become hard, not disappear. Toggle `r_player_light_shadows off` separately
  to confirm that cvar controls player-light SDF sampling.
- [ ] **Hidden enemies:** Keep a remote player within 140 units but behind an occluder or outside
  the local vision cone. Confirm the player remains black/dark and no glow identifies their
  position. Move them clearly into the cone with line of sight; their player light may then appear.
- [ ] **Live cvars:** While the match is running, set `/r_player_light_radius 220` and
  `/r_player_light_intensity 0.6`; verify the circle and brightness change immediately. Confirm
  out-of-range values are rejected without changing the last valid value.
- [ ] **Resize:** Resize the window and toggle fullscreen at least once. Confirm the half-resolution
  light and visibility targets stay aligned, with no stale-sized or stretched lighting.
- [ ] **Resource teardown:** Disconnect to the menu, re-enter a match, and repeat at least twice.
  Confirm there are no GL errors, stale lights, or growing/leaked render resources.
