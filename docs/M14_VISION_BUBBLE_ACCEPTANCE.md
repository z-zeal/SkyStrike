# M14 Vision Bubble — Desktop Acceptance Checklist

These checks require a runnable desktop build with a local match, ideally two clients. They were
documented for manual verification; source-only sandbox checks do not prove shader compilation or
visual behavior.

## What changed

The circular player light used to light only the body and the cone. Open space beside or behind
the player stayed dark even though the light was drawn there. M14 gives every eye a **vision
bubble**: a close, all-round circle of sight, drawn in the same visibility pass as the cone and
read by the same CPU rule.

- **Bubble geometry.** A bubble is an ordinary observer whose cone is 360° wide
  (`Observer.bubble`, `VisionConfig.FULL_CIRCLE_HALF_ANGLE_DEGREES = 180`) with zero feather. The
  shader's angular term is 1 in every direction, and `VisionMath.calculateConeFactor` returns 1 for
  a full-circle observer, so the GPU and the CPU agree. Distance falloff is the same quadratic as a
  cone's, so the bubble fades to nothing at its edge rather than cutting off.
- **Body bubble:** radius `VisionConfig.BODY_BUBBLE_RADIUS = 140` (the player light's default), full
  brightness. It exists whenever the body's cone exists, so it is gone while piloting.
- **Device bubbles:** radius `GadgetConfig.DEVICE_BUBBLE_RADIUS = 70`, for each deployed drone
  (own, and the piloted one) and each stuck camera the viewer owns. A drone's bubble is dimmed by
  `DRONE_VISION_BRIGHTNESS` like its cone; a camera's is not.
- **Device lights.** Each device bubble also gets a light of its own: the bubble's radius and the
  player light's intensity, multiplied by the bubble's brightness. The light glow is gated by the
  visibility texture like every other light, so it shows only where the device can see.
- **Light admission.** A remote player's light is admitted when `ObserverSet.isLit` says the viewer's
  eyes light them, which now includes bubbles. Before, it was the cone alone.
- **Minimap.** The blip gate asks the same `ObserverSet`, so an enemy inside a bubble appears on
  the minimap exactly when the screen shows them.
- **Effect culling (server).** `EffectBroadcaster` sends an effect that is inside a recipient's body
  or device bubble, not only one inside a cone, so a detonation right behind you is on your screen.

Gameplay is unchanged. Damage, stun, blasts and camera placement use line of sight only, never the
cone or the bubble (`UtilitySystem`, `StunMath`, `ExplosionMath`, `DirectionalBlastMath`,
`CameraSystem`). Only what is **drawn** and what the **effect channel** sends changed.

## Decision: full bubble, enemies included (supersedes the M6 and M12 rule)

The user chose a full bubble with enemies included. Anything within the bubble radius that has line
of sight is visible all round the player, whether or not it is in the cone. This overrides the
M6/M12 rule that a remote player outside the cone stays black. The rule still holds beyond the
bubble: a remote player outside both the cone and the bubble is dark, and their light never shows.

**Trade-off:** an enemy directly behind you, within 140 units (70 for a device) and with line of
sight, is now visible and lit. The fog no longer protects close flanks. It still protects anything
further away, anything behind a wall, and anything in smoke.

## Checklist

- [ ] **Body bubble, all round:** stand in an open room with no enemy in front. An enemy about 100
  units behind you, with line of sight, is visible and lit, and your light shows around them.
- [ ] **Beyond the bubble:** an enemy about 200 units behind you stays dark, with no glow.
- [ ] **Occluded close enemy:** an enemy within 140 units but behind a wall or crate stays dark.
- [ ] **Light follows the bubble:** the warm light glows around the body in every direction inside
  the bubble. It fades to nothing at the bubble's edge, not at a hard cut.
- [ ] **Drone bubble:** deploy a drone. A 70-unit light and bubble surround it whichever way it
  faces. An enemy about 100 units behind it, outside its cone, stays dark.
- [ ] **Piloting:** while piloting, your body's bubble and cone are gone. The surroundings of your
  body stay dark (the body's light is gated by visibility, and the body rectangle still shows your
  silhouette). The drone's surroundings light.
- [ ] **Stuck camera:** a stuck camera you own lights a 70-unit circle around itself. A flying one
  does not.
- [ ] **Minimap agrees:** an enemy that appears on the minimap is one the screen shows lit, and
  the reverse. Check an enemy just behind you, both inside and outside the bubble.
- [ ] **Smoke:** an opaque cloud between you and an enemy inside the bubble hides them.
- [ ] **Effects:** an explosion about 100 units behind you, not hidden by walls, is shown.
  One about 300 units away behind you is not sent.
- [ ] **Light radius cvar:** at the default `r_player_light_radius 140` the light and the bubble are
  the same disc. Raising the cvar (for example to 220) enlarges the glow only where the bubble
  already sees. Light beyond the bubble is still masked by visibility, so the cvar cannot widen
  what you see.

## Known limitations

- **The sight radius is not the light cvar.** `BODY_BUBBLE_RADIUS` is a gameplay constant. Changing
  `r_player_light_radius` changes only the glow, and the glow is still masked by visibility. The two
  can disagree beyond 140 units.
- **Device radius is provisional.** 70 units is the chosen value, not a tuned one.
- **Enemy light admission is by any of five hitbox samples.** A remote player's light is admitted
  when any of five hitbox points clears the presentation bar (`ObserverSet.isLit`). The light itself
  is centred on the player's centre, so a player lit only at a corner gets a light centred in the
  dark, which the per-pixel visibility mask may hide.
- **Effect culling disc.** An effect is judged on five samples of its culling disc, as before. A
  bubble's edge can therefore cut an effect that is only partly inside it.

## Verified by inspection only

- Compile of every touched Java file, and all shared and server tests, which were not run.
- The bubble edge and its brightness, and the device light's intensity.
- The light pool's device-light lifetime (lights released when a device goes), under real
  drone and camera churn.
- Shader compile of `visibility_cone.frag`, which is unchanged. The 180° and zero-feather path is
  read from the shader, not run.
