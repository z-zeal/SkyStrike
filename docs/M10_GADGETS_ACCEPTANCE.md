# M10 Gadgets — Desktop Acceptance Checklist

These checks require a runnable desktop build with a local match, ideally two clients. They were
documented for manual verification; source-only sandbox checks do not prove that a drone deploys
above your head, that the body really cannot fire while you pilot, or that the shield arc reads
as front versus rear.

The Phase 6 layer is: two authoritative device systems (`server/gadget/DroneSystem`,
`CameraSystem`) plus the surveillance view (`SurveillanceService`), the shared state machines
(`shared/gadget/{SurveillanceView,GadgetPress,DroneMotion,CameraFlight}`), the input lock enforced
in the shared `PlayerMotion` and the loadout tick, the camera's flight on the one throwable
integrator, device cones in the visibility pass, and `core/render/GadgetRenderer` plus the
surveillance HUD banner. Everything is server-authoritative; the client predicts for feel and is
corrected by the next snapshot.

Setup: host a match, join with two clients. On client A, open the loadout picker (`L`) and put
**Drone** on Q and **Throw Camera** on E (respawn to apply). On client B, put **Shield** on Q and
**Fuel Tank** on E. Client B is the observer/opponent for the legibility checks.

## Drone — deploy, pilot, exit

- [ ] **Deploy:** press Q once. A drone appears 25 units above your head, the Q box in the loadout
  bar lights up, and the HUD banner appears only after the *second* press (deploying alone does
  not switch your view).
- [ ] **Pilot:** press Q again. The camera moves to the drone immediately, the banner reads
  `DRONE` with its HP (`HP 30/30`), and the controls line reads `WASD steer  LMB locked  6 cycle
  Esc exit`.
- [ ] **Steering:** A/D move the drone left/right, W (or Space) climbs, S descends. The motion is
  smooth (lerped), not instant, and releasing the keys coasts to a hover within about a second.
- [ ] **Aim:** the mouse steers the drone's vision cone — move the cursor and the revealed area
  follows it, measured from the drone, not from your body.
- [ ] **Exit with Q:** press Q a third time. The view returns to your body instantly, the banner
  disappears, and the drone **stays deployed** (still visible, still revealing, Q box still lit).
- [ ] **Exit with Escape:** pilot again, then press Escape. The view returns to your body and the
  pause menu does **not** open.
- [ ] **Cycle with 6:** with the drone deployed, press `6`. The view switches to the drone; press
  `6` again and it returns to your body (nothing else is deployed yet). Rebind `viewCycle` in
  settings and confirm the new key works.

## The surveillance lock — the gamble

- [ ] **Body frozen:** while piloting, hold W/A/S/D — your body does not move (it coasts to a
  stop and, if airborne, falls). The movement keys drive the drone instead.
- [ ] **No weapons:** while piloting, hold LMB, press 1–5, and swing melee. Nothing fires, no slot
  changes, no reload starts. The crosshair is dimmed (~⅓ brightness), not full.
- [ ] **Gadget keys stay live:** while piloting, Q still exits the pilot view (and a second Q
  re-enters it). Only the view keys and the gadget keys do anything.
- [ ] **Vulnerable:** have client B shoot your body while you pilot. You take full damage — the
  drone does not shield you, and you cannot dodge. This is the gamble; it must feel real.
- [ ] **Stun while piloting:** client B flashbangs you mid-pilot. The whiteout covers the drone
  view; Escape still exits surveillance (the view keys are live under every lock).
- [ ] **Death while piloting:** client B kills you while you pilot. The drone is removed with you,
  the view is your own eyes on the corpse, and the Q box reads OFF (not BROKEN — death is not
  destruction). On respawn the drone is gone but still *carried* (Q reads OFF, full durability).

## Drone vision

- [ ] **Own cone, always:** with the drone deployed but *not* piloted, its cone still reveals
  territory around it — narrower (70°) and dimmer (70% brightness) than your own cone.
- [ ] **Replaced while piloting:** while piloting, your body's cone is gone from the composite;
  the screen shows the drone's cone. After exiting, your cone is back and the drone's remains.
- [ ] **Occlusion:** the drone's cone is blocked by walls exactly like a player's — park it behind
  the centre room's wall and it reveals nothing through it.
- [ ] **Effects arrive:** with the drone parked watching a lane, have client B fight in that lane.
  You see (and hear, through the drone — the ears follow it) the fight you cannot see from your
  body, including muzzle flashes and impacts your own cone would have culled.

## Drone destruction

- [ ] **Shot down:** client B shoots the drone (30 HP). Sparks (metal impact) appear at it, its
  health bar drains, and at zero it pops (small impact explosion) and disappears. The Q box reads
  `BROKEN` for the rest of the life.
- [ ] **Pilot ejected:** shoot the drone *while piloting it*. The pop happens, the view snaps back
  to your body, and Q reads `BROKEN`.
- [ ] **Never un-breaks mid-life:** press Q with a broken drone — nothing deploys.
- [ ] **Respawn restores:** die and respawn. The drone is carried again, intact, undeployed; Q
  deploys a fresh one.
- [ ] **Teammate's drone:** client B's drone is visible to you with its team colour, and you can
  shoot it (denying eyes is legitimate play).

## Throw camera — throw, stick, view

- [ ] **Throw:** press E once (camera on E). The camera leaves your hand along your aim at
  grenade speed on a visible arc, and the E box lights up. The banner does **not** appear yet.
- [ ] **Mid-flight press:** press E again while it flies — nothing happens (a flying camera cannot
  be viewed).
- [ ] **Stick:** the camera sticks to the first surface it touches — floor, wall or ceiling — with a
  small spark, and stays there permanently (it never despawns on a timer).
- [ ] **View:** once stuck, press E again. The camera centres on the stuck camera, the view zooms
  in 1.2×, and the banner reads `CAMERA` with its HP (`HP 20/20`).
- [ ] **Swivel:** while viewing, move the mouse — the camera does not move, but its cone follows
  your aim, so you can sweep a lane from a fixed post.
- [ ] **Exit:** press E again or Escape — the view returns to your body and the zoom is restored
  (the pre-surveillance viewport comes back, including a `cl_freecam` zoom).
- [ ] **Cycle through it:** with both devices deployed, press `6` repeatedly — the cycle runs
  self → drone → camera → self, and each press switches the camera view to the next available
  device. With only the camera stuck, `6` goes self → camera → self.
- [ ] **Blocked throw:** stand facing a wall and press E — no camera spawns, the E box does not
  light (the release point must be clear).
- [ ] **Destruction:** client B shoots the stuck camera (20 HP). It pops and is gone; the E box
  reads `BROKEN`; a viewer is returned to their own eyes. Respawn restores it.

## Bullets hit devices

- [ ] **Nearest wins:** line up a shot where a drone stands in front of an enemy — the drone takes
  the round (metal sparks), the enemy behind it is unhurt.
- [ ] **Damage falloff:** a drone at long range takes visibly less damage per hit than at close
  range (its health bar drains slower).
- [ ] **Self-hit grace:** your own rounds do not hit your own drone for the first fraction of a
  second after firing (the same grace players get).

## Shield arc and fuel tank — legibility (the "done when" line)

- [ ] **Front arc:** client B equips the shield (Q). You see a 90° arc on the *front* of their
  body, centred on where they aim. When they aim at you, the arc faces you.
- [ ] **Rear arc:** client B presses Q again to stow it. The arc flips to their *back* — the
  front/back choice is legible at a glance, on a teammate and an enemy alike.
- [ ] **Broken:** client B's shield breaks (shoot the arc). The arc disappears; the Q box reads
  `BROKEN`.
- [ ] **Fuel tank:** client B wears the tank. You see an amber strip on their *rear* — the side
  they are not aiming at — between 30% and 72% of their height.
- [ ] **Flank and detonate:** shoot the tank strip from behind. The tank detonates (occluded
  blast), the wearer dies, and nearby players take damage — a flanker can detonate a fuel tank.
- [ ] **Detonated tank gone:** after detonation the strip is gone and the E box reads `BROKEN`.

## HUD

- [ ] **Gadget boxes:** the Q/E boxes in the loadout bar show the gadget name, its key, a
  durability strip (the drone's and camera's live HP, the shield's remaining absorption), and a
  status word (`ON`/`OFF`/`WORN`/`BROKEN`) that never disagrees with the world.
- [ ] **Banner:** while piloting or viewing, the top-centre banner shows the device, its HP and
  the control reminder; it is absent the moment the view returns to your body.
- [ ] **Crosshair:** dimmed while surveilling; normal brightness otherwise.
- [ ] **Debug overlay (F1):** the `surveillance:` line reports the view and the drone's position
  and HP; the controls help line documents `Q/E gadget  6 view cycle  Esc exit/pause`.

## Effects and audio

- [ ] **Destruction pop:** a drone or camera going down plays a small impact explosion at its
  position, visible and audible to anyone who can see it (including through a device).
- [ ] **Stick spark:** a camera sticking to a surface plays a small metal impact there.
- [ ] **Device hits:** rounds hitting a device play the metal impact, not the concrete one.
- [ ] **Ears follow the view:** while piloting, sounds are placed at the drone — a fight beside the
  drone is louder and pans correctly even with your body elsewhere.

## Edge cases and teardown

- [ ] **Pause and console keep Escape:** while piloting, open the console (Enter) — Escape closes
  the console, not the pilot view; with the console closed, Escape exits surveillance; with the
  pause menu open, Escape closes the pause menu. One key, one owner at a time.
- [ ] **Disconnect mid-pilot:** client A disconnects while piloting — the drone is removed from
  the world on the next snapshot; client B never sees a device with no owner.
- [ ] **Reconnect ×2:** reconnect, deploy, pilot, disconnect mid-pilot, reconnect — no stale
  banner, no orphaned drone, no zoom stuck applied.
- [ ] **Resize/fullscreen while viewing:** the zoom survives a resize and is released on exit.
- [ ] **Freecam:** `cl_freecam` still detaches the camera; returning from freecam while piloting
  resumes following the drone.
- [ ] **Protocol:** an old-protocol client is rejected at join (protocol is now 12).

## Known limitations (documented, not bugs)

- Only gunfire damages devices. Melee swings and explosions pass through drones and cameras;
  mechanics §7.1 says "destroyed by gunfire" and §7.2 "destructible", so bullets are the whole
  threat model for now.
- No trajectory preview for the camera throw: `TrajectoryRenderer` is wired for the utility
  slots, and extending it to gadgets is a later polish item.
- "Destroyed when it leaves the arena" is a defensive rule: the arena shell encloses the map, so
  a boundary contact is unreachable on the standard arena — the camera always sticks to the
  shell instead.
- Escape is the hardcoded shared key (console, pause, surveillance), matching the existing
  pause/console paths; the rebindable `viewExit` binding works too, and the default is Escape.
- The drone's cone keeps its last aim while unpiloted — a hovering drone reveals a fixed lane
  until someone steers it again.
- Gadget destruction does not appear in the kill feed (it is not a player kill); the feedback is
  the destruction pop, the health-bar drain and the `BROKEN` slot status.
- The world SFX reused for gadget feedback (impact explosion, metal spark) are the synthesised
  placeholder-quality WAVs under `assets/sfx/world/` (see `docs/ASSET_ATTRIBUTION.md`).
