# M12 Utility Visibility — Desktop Acceptance Checklist

These checks require a runnable desktop build with a local match, ideally two clients. They were
documented for manual verification; source-only sandbox checks do not prove shader compilation or
visual behavior.

M12 fixes four things the M5–M7 increments left broken, plus one compile error found on the way:
the drone renderer referenced an undeclared `DRONE_HULL` constant; smoke and poison occluded one
full-radius disc at the detonation point instead of the rendered cloud's shape; molotov fire
vanished after its one-shot ignition burst long before its six seconds of damage were up; and the
molotov's surface spread landed all seven zones inside the central one. It also promotes the M6
player light from a dev-console opt-in to a shipped default (see `M6_LIGHTING_ACCEPTANCE.md`).

The layer under test: the shared `SmokeCloud` cluster (`shared/vision/SmokeCloud`, pinned by
`SmokeCloudTest`), `UtilityZone.smokeVolumes()`, the server's `UtilitySystem` smoke list, the
client's shader-circle assembly in `GameScreen`, `core/render/UtilityZoneRenderer`, the
`FIRE_SPREAD_OFFSET` conversion in `UtilityConfig`, and the `r_player_light*` cvars in
`ClientCommandModule` read live by `GameScreen`.

Setup: host a match, join with two clients. Client B throws; client A observes.

## Smoke / poison occlusion follows the cloud

- [ ] **Growth:** throw a smoke grenade into open ground and watch it from the first frame. No
  vision is blocked at the detonation instant; the blocked area swells with the billowing cloud
  and is fully out about 3 seconds in (40% of the 8 s zone life), not instantly.
- [ ] **Shape:** the occluded region reads as a sagging blob, not a perfect disc centred on the
  impact — denser and lower than the detonation point, matching where the puffs actually are.
  Stand behind the cloud's far edge with an enemy on the other side: you cannot see them once the
  cloud is grown.
- [ ] **Poison:** the poison cloud occludes the same way (same cluster, 7 s life, full at ~2.8 s),
  and its damage-over-time still applies inside it.
- [ ] **Server/client agreement:** with client B standing in the grown smoke, client A's view of
  B is cut exactly where the cloud is — never earlier (empty ground blocked) and never through
  the cloud's core. The stun grenade's and flashbang's line-of-sight checks agree with what is
  drawn (detonate one inside the smoke; the blind applies only where the occlusion is).
- [ ] **Cap behaviour:** throw three smoke grenades in quick succession (two slots, carried
  count 2). The first two clouds occlude fully; the third is partially truncated — identically
  for the shader and for gameplay sight queries, so what you see is what the server resolves.

## Molotov fire persists and matches the damage zone

- [ ] **Persistence:** throw a molotov on open ground. Flames and the ember bed are visible for the
  full 6 s of the zone's life, flickering, fading out over the final second — not just the ~1.5 s
  the ignition burst's particles live.
- [ ] **Coverage:** the flames sit inside the zone's 40-unit circle, so the fire you see is the
  fire that burns: stand at the flame line's edge and confirm the damage-over-time starts exactly
  there.
- [ ] **Spread:** on a floor the fire reads as one continuous line along the surface — one central
  patch plus three overlapping patches each side at 50-unit spacing (±0.35 jitter), roughly 230
  units long end to end. The outward patches deal 17 per tick against the central 21, and a
  player standing in two overlapping patches still takes one hit per tick, not two.
- [ ] **Surfaces:** the spread follows the tangent on a ramp and on a vertical wall (throw at a
  wall; the line runs vertically). Flames rise in world-up regardless of the surface, matching the
  ignition burst.
- [ ] **Occlusion:** fire around a corner does not show through the wall (the renderer draws in
  the scene pass, so the fog composite gates it); the attached flickering light per zone still
  expires with the zone.

## Player light ships on

- [ ] **Default on:** in a normal (non-dev) build, the local player carries the warm-white
  140-unit, 0.35-intensity light with no console command. The silhouette stays readable in a dark
  corner while the surrounding scene follows the fog rules (M6's local-silhouette check).
- [ ] **Toggleable:** `/r_player_light off` removes it immediately; `/r_player_light on` restores
  it. F10 toggles it in dev builds. `/r_player_light_shadows off` disables the lights' SDF
  occlusion sampling; `/r_shadows off` hardens the edges.
- [ ] **Hidden enemies unchanged:** a remote player behind cover or outside the cone stays dark;
  no glow identifies their position (M6's hidden-enemies check still holds with the light on).

## Known limitations (documented, not bugs)

- The occlusion cluster covers the cloud's core mass. The particle presets' sparse fringes
  (curl-noise drift plus sprites grown to 320 units) can extend a little beyond it: a see-through
  fringe, never vision blocked over empty ground. Taming the drift further would change the
  cloud's look, which this increment deliberately leaves alone.
- `VisionConfig.MAX_SMOKE_VOLUMES` stays 8: three circles per cloud, so two overlapping clouds
  occlude fully and a third concurrent cloud is partially truncated. The truncation is identical
  on server and client (the client sorts snapshot zones by id to match the server's creation-order
  fill), so the CPU/GPU contract holds; only the coverage shrinks under smoke spam.
- The growth window is 40% of the zone's life, tuned by inspection against the particle presets'
  spread (drag saturates in about a second, turbulence is a bounded offset) — not yet playtested.
- Spread zones are still cast along the tangent without testing terrain between patches
  (pre-existing; the tangent logic is unchanged, as scoped).
- The minimap still draws no zones (the M11 known limitation stands): the world-space fire and the
  smoke occlusion are this increment; a map picture of them is not.
