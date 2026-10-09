# M11 HUD — Desktop Acceptance Checklist (the fog-gated minimap)

These checks require a runnable desktop build with a local match and two clients. They were
documented for manual verification; source-only sandbox checks cannot prove that a marker appears
where the fog lights an enemy and nowhere else, that a lost sighting fades rather than blinks, or
that the map and the debug readout share the top-left corner without drawing through each other.

The increment is: `shared/hud/MinimapModel` (which markers may exist, where they sit in normalised
arena space, and how long a sighting is still worth showing), `shared/vision/{Observer,ObserverSet}`
(the set of eyes a viewer looks through, lifted out of the client's render loop so the GPU cone pass
and the map's blip gate ask one question and get one answer), `VisionMath.isTargetLit` plus
`VisionConfig.LIT_VISIBILITY_THRESHOLD` (the presentation bar, above the 6% peripheral floor),
`Team.areAllies`, `core/ui/hud/Minimap` (shapes only), the two cvars `cl_minimap` and
`cl_minimap_allies`, and the debug inset that moves the F1 readout below the map.

**No wire change.** `PROTOCOL_VERSION` is still 12. `GameServer.broadcastSnapshot` has always sent
every player's true position to every client — it is `sendEffectSpawns` that culls per recipient —
so the map is presentation over state the client already holds. That is exactly why the gate is the
whole feature: a minimap that read the snapshot directly would be a wallhack the game ships with.

Setup: host a match, join with two clients. Put client A and client B on **opposite** teams for the
gate checks; the teammate toggle section says when to move B onto A's team. Press `F1` on client A
for the debug readout — the new `minimap:` line is the fastest way to see what the model believes.

## The frame — whole arena, never a radar

- [ ] **Position:** the map is top-left, inside the margin, on the same panel fill as the console
  and the debug readout, with a thin edge.
- [ ] **Aspect:** the arena's 3:2 shape is preserved. The map is not stretched to fill its frame —
  a marker must be as far from the centre as the world puts it.
- [ ] **Absolute, not relative:** walk to the far right of the arena and confirm your marker is at
  the far right of the map. The map does not re-centre on you and does not rotate.
- [ ] **Terrain reads as a silhouette:** the floor, both side walls, the ceiling, the ramp steps,
  the crate stacks, the centre room, the catwalk and the sniper perch are all recognisable as the
  arena you are standing in, dimmer than any marker. Thin geometry (the 14-unit tunnel roof, the
  16-unit perch ladder) is still visible rather than vanishing below a pixel.
- [ ] **Nothing is cropped:** a jetpack fight in open sky, above every platform, is on the map.
- [ ] **Your view rectangle:** a hollow rectangle shows what the camera is currently framing. Fly
  the camera around with a detached pan (`cl_freecam`) and confirm the rectangle follows and stays
  inside the arena even when the camera looks past the edge.

## Your own marker

- [ ] **Always there:** your marker is the brightest, slightly larger square, and it is drawn on
  top of anything it overlaps.
- [ ] **Centre, not feet:** standing on the ground, your marker sits at your body's centre, not at
  your feet and not at eye level. Crouching moves it down slightly.
- [ ] **Alive or dead:** die and confirm your own marker is still on the map, at your corpse. You
  never lose track of where you fell.
- [ ] **Before a spawn:** while joining, before you have spawned, the map already draws the arena
  and the view rectangle with no markers at all — it is a map, not a blank box.

## The gate — enemies only where the fog lights them

This is the security-relevant half. Every check here is "the snapshot knows, the map must not say".

- [ ] **In the cone, in reach:** an enemy in front of you within hip reach (640 units) appears as a
  red square. Walk towards them and the marker tracks them smoothly.
- [ ] **Behind you:** an enemy directly behind you is **not** on the map, even though they are a few
  metres away and the snapshot carries their exact position. Turn around and they appear.
- [ ] **Outside the cone:** an enemy 90° off your facing, in reach, is not marked; swing your aim
  across them and the marker appears as the cone sweeps over them.
- [ ] **Out of reach:** an enemy further than hip reach is not marked. Aim down sights (`RMB`) and
  confirm the gate widens with the eased ADS reach (up to 1024) — a distant enemy comes onto the map
  as you aim, without the reach snapping.
- [ ] **The last few percent of reach:** the gate is the same distance falloff the composite draws
  with (`1 − (d/reach)²`), so a marker disappears at about 97% of reach — the band where a body is
  drawn as essentially black. There is no separate, more generous "map range" to discover.
- [ ] **Peripheral floor never marks:** an enemy outside your cone — past its 60° half-angle, where
  the composite leaves only the faint peripheral wash — is not marked, however close they are. The
  gate sits just above that 6% floor precisely so the wash cannot earn a dot; inside the cone's
  feathered edge, where a body really does start to appear, the marker appears with it.
- [ ] **Behind cover:** an enemy behind the centre room's wall, or behind a crate stack, is not
  marked even when they are inside your cone and in reach. Step into the doorway and they appear.
- [ ] **In smoke:** throw smoke between you and an enemy. Their marker goes away while the smoke
  hides them and comes back when it clears — the map consults the same live smoke volumes the
  visibility shader draws with.
- [ ] **Never disagrees with the screen:** the rule to hold every check above against — if the
  enemy is lit enough on your screen to see, they are on the map; if the screen draws them black,
  they are not. A marker for a body the fog is hiding would be a cheat sheet.
- [ ] **Marker at the centre:** an enemy's marker is at their body centre, matching where they are
  drawn in the world, not at a hitbox corner.

## Memory — ghosts hold, fade, and do not track

- [ ] **No strobing at the cone edge:** turn away from a lit enemy. Their marker does **not** blink
  out; it holds for about a second, then fades over about 2.5 seconds, then is gone (~3.5 s total).
- [ ] **Hollow means remembered:** while fading, the marker is drawn hollow (an outline) rather
  than filled, so a memory can never be mistaken for something you can currently see.
- [ ] **Frozen, not following:** this is the important one. Turn away from a moving enemy and have
  them keep walking. The ghost stays exactly where you last saw them — it does **not** creep after
  them, and it does not update at a lower rate. It is a memory of an observation.
- [ ] **Death retires it at once:** turn away from an enemy so their marker is a fading ghost, then
  have them die (or kill them). The ghost disappears immediately, mid-fade. A corpse is not a
  sighting, and the kill feed already said why.
- [ ] **Respawn empties the map:** die and respawn. Every remembered sighting is gone — the last
  life's memories are about a body you left, seen from a position you are no longer at.
- [ ] **A frame spike cannot empty or hoard the map:** the envelope runs on the wall clock. Drop to
  a very low frame rate (or alt-tab) and confirm markers age at the same real-world rate rather
  than living forever or expiring instantly.

## The teammate toggle (`cl_minimap_allies`)

- [ ] **Default on:** with both clients on the same team, a teammate behind you or behind cover is
  shown on the map in the ally colour (green), with no gating — mechanics §4's "teammates visible
  on minimap".
- [ ] **Off:** `/cl_minimap_allies false` in the console. That same teammate is now gated exactly
  like an enemy: shown when your vision lights them, and a ghost afterwards.
- [ ] **Back on:** `/cl_minimap_allies true` restores it live, with no respawn and no reconnect.
- [ ] **Applies to devices too:** an allied drone or stuck camera follows the same toggle as an
  allied body — one rule, not two that can drift apart.
- [ ] **Neutral has no teammates:** with both clients moved to Neutral (`/setteam neutral`, which
  needs moderator console access), the toggle exempts nobody. Two Neutrals fight each other, so
  neither is shown for free.
- [ ] **The other §4 toggle is not implemented:** teammates are **not** exempt from cone culling in
  the world — a teammate you cannot see is still drawn dark. This cvar touches the map only.

## Devices — drones and throw cameras

- [ ] **Your own, always:** your deployed drone and your stuck camera are on the map however far
  away they are and whatever you are looking at. They are your property, and the gadget slots
  already report their state.
- [ ] **Device shape:** devices are drawn as small dots, bodies as squares — you can tell them apart
  at a glance.
- [ ] **An enemy device is gated:** an enemy drone or camera is marked only where your vision lights
  it, exactly like an enemy body, and ghosts the same way.
- [ ] **Destroyed devices are forgotten:** have client B shoot your drone down (or destroy their
  camera). Its marker disappears at once rather than fading — absence from the snapshot is
  authoritative, so a destroyed device is gone, not hidden.
- [ ] **A camera in flight is not an eye:** throw a camera and confirm it is still marked as your
  own device while it flies, but that it reveals nothing (its cone only exists once it sticks).

## Surveillance — the map while piloting

- [ ] **The gate moves with the view:** deploy and pilot a drone. Your map is now gated by the
  *drone's* eyes — its narrower 70° cone and 250-unit reach — not by your body's. Enemies near your
  body but outside the drone's cone are not marked.
- [ ] **Your body's cone is gone:** while piloting, an enemy standing in front of your (frozen,
  unattended) body is not marked unless the drone also lights them.
- [ ] **Your body marker stays put:** while piloting, your own marker remains at your body, which
  is not moving. It does not jump to the drone.
- [ ] **The view rectangle follows the drone:** while piloting, the hollow rectangle is around the
  drone's camera, and it widens/narrows with the surveillance zoom.
- [ ] **Viewing a stuck camera:** cycle to your stuck camera (`6`) and confirm the same three
  things — the gate is the camera's cone, the rectangle is the camera's view, your body marker is
  still at your body.
- [ ] **Exit restores everything:** press `Q`/`Escape` to return to your body and confirm the map
  is gated by your own cone again immediately, with no stale markers left over from the device.

## Sharing the corner, toggling, and resizing

- [ ] **F1 readout moves:** with the map on, press `F1`. The debug readout starts *below* the map —
  it does not draw through it, and no line is clipped.
- [ ] **F1 readout takes the corner back:** `/cl_minimap false`, then `F1`. The readout moves up
  into the top-left corner with no hole left where the map was, and no gap at the top.
- [ ] **Toggling live:** `/cl_minimap false` and `/cl_minimap true` add and remove the map with no
  respawn, no reconnect, and no other widget moving (kill feed, vitals, loadout bar, crosshair and
  the surveillance banner all stay put).
- [ ] **Debug line:** the `minimap:` line reports the marker count, how many are live versus
  remembered, whether allies are shown or gated, and the arena size. With the map off it reads
  `minimap: off (cl_minimap)`. Counts agree with what is drawn.
- [ ] **Banner:** the first debug line now reads `SkyStrike - M11 (HUD: fog-gated minimap)`.
- [ ] **Resize and fullscreen:** resize the window and toggle fullscreen. The map keeps its 3:2
  aspect, stays in the corner, scales with the HUD's design-pixel scale (so it is legible at 1080p
  and at 4K), and the readout inset follows it.
- [ ] **Legible over a bright sky and a dark interior:** the map has no text pass — there is
  nothing on a map this size worth a glyph — so the console's four-background contrast test
  (`ui_contrast_test`, F12, which covers the whole screen including the HUD) does not apply to it.
  Check it the way a player meets it instead: standing in open sky with the sun-bright composite
  behind the panel, and inside the dark centre room. The terrain, the panel edge, the view
  rectangle and all three marker colours stay distinguishable in both, and ally/enemy/self read as
  different *sides*, not as different brightnesses of one colour. Colour-only distinction is
  reinforced by shape (bodies are squares, devices dots, memories hollow).

## Edge cases and teardown

- [ ] **No other HUD element is affected:** the crosshair, health/fuel bars, loadout bar, kill feed,
  damage vignette and surveillance banner all behave exactly as they did before this increment.
- [ ] **Loadout screen has no map:** open the loadout picker/menu screen. There is no minimap there
  (no arena, no live local player) and the readouts are laid out as they were.
- [ ] **Reconnect ×2:** disconnect and reconnect twice. The map is empty of stale sightings after a
  reconnect — you do not inherit the last connection's memories.
- [ ] **A crowded match does not stutter:** the model caps remembered sightings at 64, forgetting
  the least recently seen first. With many players and devices, live markers are never dropped to
  make room for memories.
- [ ] **Team switch:** `/setteam b` mid-match. Allegiance colours update immediately — a former
  teammate is now an enemy colour and gated, a former enemy now an ally colour and exempt. Old
  ghosts keep the allegiance they were seen under rather than recolouring, which is what makes a
  memory a memory.
- [ ] **Spectator/dead view:** while dead and waiting to respawn, the map still draws the arena and
  your own marker, and the gate follows whatever eyes you still have.

## Known limitations (documented, not bugs)

- The map shows **bodies and devices only**. Thrown utilities in flight, projectiles, persistent
  smoke/poison/fire zones, shield arcs and fuel tanks are not drawn: smoke is consulted as a gate
  (it hides markers) but is not itself a picture on the map. Zone rendering is a later HUD item.
- The gadget panel (durability and cooldowns) and floating damage numbers — the other two items in
  the Phase 7 HUD list — are **not** in this increment. The loadout bar's gadget boxes remain the
  only durability readout.
- Ghost timing (1.0 s hold, 2.5 s fade, 3.5 s total) is provisional: no plan document sets a
  minimap memory. It is long enough that stepping behind a crate does not blink, short enough that a
  stale marker is clearly stale.
- The map is a fixed whole-arena view with no zoom, no crop and no "centre on me" mode. A radar mode
  is not implemented and is not planned until the whole-arena view has been played.
- Markers are fixed-size squares and dots rather than scaled bodies: at whole-arena zoom a 30-unit
  player is under a pixel wide, and a marker you cannot see is not a map.
- There is no key binding for the map. It is console-only (`cl_minimap`), because the plan names no
  key and the default is on.
- The map does not show who is *shooting*: muzzle flashes, tracers and kill-feed events are not
  markers. A gunshot only reaches the map if it lights the shooter.
