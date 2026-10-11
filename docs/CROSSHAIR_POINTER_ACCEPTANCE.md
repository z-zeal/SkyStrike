# Crosshair at the cursor — acceptance note

**Status: unexecuted.** Every box below is unchecked, and no part of this change has been run: the
authoring sandbox has no JDK, so the client was never launched and nothing was seen on a screen.
Source reading confirms the code paths exist — `HudFrame` now carries a `PointerPosition`,
`GameScreen.pointerPosition()` builds it from `Gdx.input` on desktop, `Crosshair` draws its arms,
dot and hit marker at it, and `HudStage` hides the OS pointer while the reticle stands in for it —
but **nothing below may be treated as passed until a human has seen it on a running desktop build.**

## What changed

The crosshair was drawn at the centre of the screen while the aim was already mouse-driven:
`InputSampler` unprojects the mouse pixel through the camera and takes the angle from the player's
eye (or the piloted device) to that world point. On PC the reticle therefore sat at the middle of
the screen while the shots went wherever the mouse was — the crosshair was not the player's cursor.

- **The crosshair is drawn at the pointer.** `HudFrame.pointer` (`PointerPosition`, window pixels
  in the HUD's own bottom-left projection) is the anchor; a frame with no pointer falls back to the
  screen centre, where the crosshair has always been. `GameScreen` supplies the pointer on desktop
  only, so touch keeps the old behaviour.
- **The gap is untouched.** `CrosshairMath` is still the only authority for the arm gap and the ADS
  form, and the hit marker still expands and recolours off the same functions. The arms, the dot
  and the marker simply hang off the pointer instead of the screen middle.
- **ADS, lock dimming and camera pan are unchanged.** ADS still only thins the arms and adds the
  dot; piloting still dims the whole reticle to 0.35; the camera, the freecam and the piloted
  zoom move the world, never the reticle. While piloting, the aim anchor is the drone or camera
  (`SurveillanceController.aimAnchor`) — the crosshair stays on the pointer and the device's ray
  still passes through the world point under it, so it remains truthful.
- **Consumers checked (no change needed):** `HitboxOverlay` draws world-space hitbox zones,
  `DamageNumberWidget` anchors through `WorldProjection`, and `TrajectoryRenderer` steps the
  throwable arc in world space from the eye. None of them read a screen centre.
- **Decision — the OS pointer is hidden while playing.** With the reticle on the cursor, a visible
  arrow would either cover it or be a second cursor, so the desktop system pointer is set to
  `SystemCursor.None` exactly while the crosshair is drawn at it (a live local player, and no
  dialog on the focus stack). It is restored to `Arrow` for every state that needs a pointer: the
  loadout picker, the console, pause and settings (all of which take modal focus), a dead local
  player (whose crosshair is not drawn), touch devices (which have no system pointer and never
  call the API), and on leaving or disposing the match screen.
- **No wire change, no gameplay change.** Nothing here crosses the network; the server still gets
  the same aim angle from the same sampling path.

## Checklist

- [ ] **Reticle on the cursor:** in a live match on desktop, move the mouse. The crosshair tracks
  it exactly, with no lag and no drift when the camera pans, when the aim is near the screen edge,
  or while the player moves.
- [ ] **No second cursor:** the OS arrow is gone while playing (no arrow sitting on the crosshair),
  and it comes back in every menu.
- [ ] **Spread is still truthful:** empty a magazine and watch the arms bloom at the pointer; the
  gap is the same function as before, just anchored elsewhere.
- [ ] **ADS:** hold aim. The arms narrow and the centre dot appears at the pointer; releasing it
  returns the hip form.
- [ ] **Piloting:** deploy and pilot a drone. The reticle stays on the pointer, dimmed to about a
  third, and steering with the mouse matches the reticle.
- [ ] **Camera:** toggle `cl_freecam` and pan. The reticle follows the mouse, not the camera.
- [ ] **Dialogs:** open the loadout picker (L), the console, pause and settings. In each, the OS
  arrow must be visible, and the crosshair must not be the only pointer.
- [ ] **Death:** die while playing. The crosshair disappears and the OS arrow comes back, so the
  player is not left without any pointer.
- [ ] **Leave the match:** disconnect to the menu, and open the loadout screen from the main menu.
  The OS arrow must be visible there and on every later screen.
- [ ] **Touch fallback:** on a phone or tablet (or an emulated touch input), confirm the crosshair
  sits at the centre of the screen and no cursor API is attempted.
- [ ] **Window:** resize and toggle fullscreen. The reticle must stay exactly under the pointer at
  every size, including HiDPI.
- [ ] **Alt-tab:** focus another window and come back. The pointer must not be left hidden with no
  reticle drawn, and the reticle must resync to the pointer position.
- [ ] **Multiple monitors:** move the window between displays and confirm the pointer/hit-marker
  alignment holds.

## Verification note

Nothing was run. There is no JDK in the authoring sandbox, so the client was not compiled or
launched, and the cursor behaviour was not observed. What *was* verified statically:

- `tools/scratch/static_check.py` and `static_api_check.py` are clean after the change (390 Java
  files, no unresolved references, no unused imports, module boundaries intact).
- The two `HudFrame` construction sites (`GameScreen`, `LoadoutScreen`) were updated with the new
  record component; there are no others.
- The libGDX APIs used are checked against the pinned version (`gdxVersion = 1.14.2`):
  `Graphics.setSystemCursor(Cursor.SystemCursor)` and both enum constants `None` and `Arrow` exist
  in that release. This is the only new libGDX surface the change introduces.
