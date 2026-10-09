# M13 HUD — Desktop Acceptance Checklist (gadget panel and floating damage numbers)

These checks require a runnable desktop build with a local match and two clients. They were
documented for manual verification; the source-only sandbox could compile none of this (no JDK), so
the checks below are what a player will see, and the shared maths is covered by unit tests in
`shared/src/test`.

This increment closes the last two items of the Phase 7 HUD list in `PHASE7_AUDIT.md` §3.1:

- **Gadget panel** — `shared/hud/GadgetPanelModel` (the words, numbers and cooldown countdown for
  the Q and E slots) and `core/ui/hud/GadgetPanel` (the plate, bars and text).
- **Floating damage numbers** — `shared/hud/DamageNumberModel` (which hits become numbers, how they
  age, their falloff ratio), `shared/hud/WorldProjection` (world to screen, the one projection rule)
  and `core/ui/hud/DamageNumberWidget` (the text).

**No wire change.** `PROTOCOL_VERSION` is unchanged. Damage numbers read `PacketDamageEvent`, which
was already delivered to both parties. The gadget panel reads the predicted `PlayerLoadout`, which
already carries durability, active, broken and cooldown on each gadget slot.

Setup: host a match and join with two clients on **opposite** teams. Equip a shield (`Q`), a drone
(`E`) and, for the tank case, a fuel tank through the loadout picker (`L`).

## The gadget panel

- [ ] **Position:** with at least one gadget equipped, a plate sits bottom right, directly above the
  loadout bar and above its reload/swap captions. It does not cover the bar or the captions.
- [ ] **Hidden when empty:** with both gadget slots empty, there is no plate. The bar's two "Empty"
  boxes are the only gadget readout.
- [ ] **Each row names its key:** `Q` and `E` are printed in the accent colour, with the gadget's
  name beside them. A long name is trimmed by the plate's width rather than running off it.
- [ ] **Shield, intact:** reads `112/150`-style durability with a bar under it. The bar is green and
  full at `150/150`. The state word is `REAR` while stowed and `FRONT` after `Q` equips it.
- [ ] **Shield, wearing:** take shield hits (have client B shoot you while your shield faces them).
  The bar shrinks, the count falls with it, and the bar and count agree.
- [ ] **Shield, broken:** at zero durability the state word reads `BROKEN`, the bar and count turn
  red, and the shield stays broken for the rest of the life.
- [ ] **Drone, deployed:** after `E` deploys it, the state word reads `DEPLOYED` and the count shows
  the drone's health (`30/30` fresh). Shoot it: the count falls with the drone's health.
- [ ] **Drone, destroyed:** the state word reads `DESTROYED` and the bar empties. The slot does not
  reset until respawn.
- [ ] **Camera:** throw a camera (its slot's key) and the count reads the camera's health out of
  `20`. A destroyed camera reads `DESTROYED`.
- [ ] **Fuel tank:** reads `WORN` with no bar and no count, and never reads `READY`. A detonated tank
  reads `DETONATED`.
- [ ] **Agreement with the bar:** the gadget boxes on the loadout bar and the panel show the same
  state at every moment. The bar's `ON`/`OFF`/`BROKEN`/`WORN` and the panel's `FRONT`/`REAR`/
  `DEPLOYED`/`DESTROYED`/`DETONATED` are the same facts in two vocabularies.
- [ ] **Predicted, not authoritative-lagged:** toggling the shield updates the panel on the same
  frame the bar does. Both read the predicted loadout.
- [ ] **Dead or surveilling:** while dead the panel still shows your gadgets (they survive death, the
  state resets on respawn). While piloting a drone the panel still shows the drone's state.

## Cooldowns — decided: none

Product decision (M13): **gadgets have no gameplay cooldowns.** Mechanics §7 gives none, and the
owner chose not to invent them. Consequences for testing:

- [ ] **No countdown ever shows:** no gadget starts a cooldown, so the panel never prints a
  `1.2s`-style countdown. Every pressable gadget reads `READY` in its state word, and a fuel tank
  reads `WORN`.
- [ ] **Rapid presses behave as before:** pressing `Q`/`E` repeatedly is limited only by the
  gadget's own rules (a drone's deploy-then-pilot sequence, a shield toggle), not by a timer.

The countdown code (`GadgetSlot.cooldownRemaining`, `GadgetPanelModel.cooldownText`) is kept dormant
rather than removed: it is harmless, it is covered by unit tests, and removing the wire-adjacent
field is a protocol change with no gain. If cooldowns are ever added, the panel already renders them.

## Floating damage numbers

- [ ] **Your hits, at the impact:** shoot an enemy. A white number of the damage applied appears
  at the point the round hit, not above the enemy's head.
- [ ] **It stays on the wall:** while the number is rising, pan with `cl_freecam` and confirm it
  stays on the spot the round landed, not on the screen.
- [ ] **Only your outgoing damage:** damage taken from others does not produce a number (the
  directional vignette covers it). Your own molotov or fuel tank does not either.
- [ ] **Headshot:** a head-zone hit prints in gold, visibly different from a body hit.
- [ ] **Killing blow:** the hit that kills prints in red, and wins over the headshot gold when both
  apply.
- [ ] **Falloff reads at a glance:** a point-blank body hit is white; a long-range body hit of the
  same weapon is visibly dimmer. A headshot at range is still gold, not dimmed.
- [ ] **Rounding:** a 23.6-damage round reads `24`. The number is the damage applied, so a headshot
  at range reads the doubled figure.
- [ ] **Short life:** a number holds for about 0.15 s, then fades and rises over about 0.85 s in
  total. A burst of hits stacks as separate numbers and does not hold the screen.
- [ ] **Capacity:** rapid fire (a full magazine of an automatic weapon) keeps the newest numbers. The
  model holds 24, dropping the oldest, so the frame never fills.
- [ ] **Off-screen hits:** a number whose impact is well outside the view is not drawn, and it does
  not reappear when you pan back before it expires.
- [ ] **Melee and utility:** a melee hit and a grenade or molotov hit you cause print a number at the
  point of impact, at full white (they have no falloff to show).
- [ ] **Legible over sky and wall:** the numbers use the console's contrast clamp and the baked font
  outline. Check one over bright sky and one over a dark interior.
- [ ] **No overlap with the HUD:** numbers draw under the kill feed, the vitals, the gadget panel,
  the loadout bar and the picker. A number over the crosshair is expected; it moves with the hit.

## Edge cases

- [ ] **Loadout screen:** open the loadout picker from the menu. Nothing from the match draws, and no
  damage number appears there (the loadout screen has no arena to project into).
- [ ] **Resize and fullscreen:** the gadget panel keeps its place above the loadout bar, and numbers
  stay on their impacts after a resize.
- [ ] **Reconnect:** disconnect and reconnect. No numbers from the last connection remain on screen;
  anything still rising expires within a second.
- [ ] **Other widgets:** the crosshair, vitals, loadout bar, kill feed, minimap and surveillance banner
  are placed exactly as in M11 and M12.
- [ ] **Banner:** the first debug line reads `SkyStrike - M13 (HUD: gadget panel, floating damage numbers)`.

## Known limitations (documented, not bugs)

- **No gadget cooldowns, by decision.** The countdown path is dormant (see above). Adding cooldowns
  would be a new gameplay rule, not a HUD change.
- **Provisional timing and colour.** The number hold (0.15 s), life (0.85 s), rise (25 world units),
  capacity (24) and the falloff tint ramp are chosen values. No plan document sets them; tune them
  by playing.
- **The panel sits above the loadout bar's captions.** When both captions are showing, the panel is
  stacked above them and may crowd the top of a tall HUD on a small window.
- **Damage numbers are not de-overlapped.** Hits that land on the same point draw on top of each
  other. There is no spreading pass; if a burst reads as a smear in play, that is the first thing to
  tune.
- **The minimap still does not draw utility zones.** This was an M11 limitation and is unchanged.
