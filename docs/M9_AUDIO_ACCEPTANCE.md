# M9 Audio — Desktop Acceptance Checklist

These checks require a runnable desktop build with a local match, ideally two clients and a
headset. They were documented for manual verification; source-only sandbox checks do not prove
that a WAV loads, that a voice is stolen where it should be, or that the mix sounds right.

The Phase 9 layer is small on purpose: one mixer (`AudioSystem`), one catalogue (`SoundCatalog`,
half weapon data and half `EffectSoundTable`), one spatial formula (`shared/audio/SpatialAudio`),
one ring envelope (`shared/audio/TinnitusMath`), the effects-event consumers (particles and
`EffectAudio`, fed by `FxPipeline` in the same frame), and `GunAudio`, which reads authoritative
successful-volley cues from `PacketGameState`.

The three sliders this layer obeys already exist: Master, Music and Effects, in both the main
settings screen and the in-game dialog. With `fx_debug` on (F11), the `sfx:` line reports the
mixer's pool, the event channel's counters, and the ring level.

## One event, two layers (the Phase 9 rule)

- [ ] **Explosion:** throw a frag. The flash and the boom arrive in the same frame, once each —
  no doubled detonation, no sound that lags the light.
- [ ] **Molotov:** a glass break at the contact point, then fire ignitions as the patch spreads
  along the surface. The ignition reads as one fire starting, not five clicks (`FIRE_ZONE` is
  capped at two voices and lowest priority, deliberately).
- [ ] **Exactly one gun report:** fire any weapon, including a shotgun and a burst rifle. One
  report per successful shot/volley; the authoritative cue travels with the state snapshot,
  independent of projectile lifetime and visual muzzle-flash culling. The generic muzzle-flash
  effect remains silent, so the shot is never doubled.
- [ ] **Counters:** with `fx_debug on`, the `sfx:` line's `silent` count grows only from visible
  muzzle-flash effects (one per shot) and never from a malformed event.

## Spatialisation and occlusion

- [ ] **Distance:** a frag across the arena is quieter than one beside you, and still audible; a
  fire zone a few hundred units away is faint.
- [ ] **Panning:** a shot to your right arrives on the right channel; walking around to the other
  side swaps it. Panning follows world X, matching the screen, because this camera never rotates.
- [ ] **Occlusion:** a frag on the far side of a wall is muffled, not silent — information survives,
  the "it went off in this room" lie does not. Same for a fight through the centre room's wall.
- [ ] **Smoke:** stand in or beside smoke. A detonation on the far side is muffled exactly as if by
  a wall — the audio occlusion is the same `VisionMath` line-of-sight call, with the same live
  smoke volumes, that the stun bands and the remote-player light gate use.
- [ ] **Edge taper:** walk away from a burning fire zone until it goes silent. The cut-off must be
  inaudible — no pop, no click, no abrupt disappearance.

## Pooling and priorities

- [ ] **Under spam:** a full squad auto-firing plus grenades and fire. The mix stays legible:
  casing tinkles and fire ignitions drop first, detonations always come through, and fresh FIRE
  attacks continue to replace the oldest same-priority gunfire voice when their path/bus cap is
  full. This exception is limited to authoritative gunfire; other sound priorities stay unchanged.
  Watch `voices`, `stolen` and `dropped` in the `sfx:` line.
- [ ] **Bus caps:** `voices` never exceeds the effects bus cap (24); music and UI have their own
  small caps (2 and 4) and nothing else plays on them yet.
- [ ] **Ordinary ties never steal:** a casing arriving while casings are at their two-voice cap
  does not replace one; it is dropped. Only a fresh cue explicitly marked by `GunAudio` may
  replace an older equal-priority `FIRE` voice under pressure.
- [ ] **Missing asset (optional):** rename one world WAV and run a match. One logged error, silence
  for that effect only, no crash, no per-frame retry, and `missing 1` in the `sfx:` line.

## Status effect — the stun ring

- [ ] **Flashbang:** take one at close range. The whiteout and the ring arrive together, and the
  ring keeps ringing for seconds *after* the light clears, falling in pitch as it fades. The tail is
  the point: the stun costs you something after it wears off.
- [ ] **Bands differ:** a fringe (or line-of-sight-blocked) stun rings quieter and for less time than
  a direct hit. A direct hit is loud but never drowns the fight around it.
- [ ] **Local only:** a second client watching the same flashbang hears the bang only. The ring is a
  status effect on the player who was blinded, never a world sound.
- [ ] **No ring in the menu:** disconnect to the menu (or let the match end) while still ringing.
  The menu is silent — the session-reset listener stops the loop.
- [ ] **Zero effects volume:** with Effects at 0 the ring is silent too. It sits on the effects bus
  on purpose: a player who mutes effects has asked for silence, status effect or not.

## Buses, volume and settings

- [ ] **Live sliders:** in-game settings, drag Master and Effects. The world mix follows immediately,
  including the ring, and without restarting the match.
- [ ] **UI bus:** turning Effects to zero does not silence chat, console or menu blips; Master does.
  (Music is wired but nothing plays on it yet, so the Music slider is a documented no-op today.)
- [ ] **Persistence:** set all three sliders, restart the client, confirm the values persist and are
  applied on the first match.

## Teardown and lifecycle

- [ ] **Disconnect / reconnect ×3:** no ring left behind, no growth in `assets` in the `sfx:` line,
  no errors, and the same mix quality after re-entering.
- [ ] **Settings dialog mid-match:** open and close the in-game settings dialog; sound neither stops
  nor stutters.
- [ ] **Resize / fullscreen:** the mix is unchanged; nothing is tied to the window size.

## Known limitations (documented, not bugs)

- Occlusion is a gain reduction, not a low-pass filter: libGDX's `Sound` has no per-voice filter.
  A wall dims a sound rather than dulling it; if a muffled boom ever needs a duller timbre, a
  filtered asset variant is the honest fix.
- Voice lifetime is booked from the declared asset duration, because libGDX exposes no duration, so
  a pool slot can be held for up to ~0.15 s after a sound has finished. The approximation only ever
  costs a slot.
- Metal and wood bullet-impact entries exist and sound, but the server still emits the concrete
  impact type for every surface (see M7's known limitations), so those two rows are unreachable
  until the surface-material pass lands.
- Music has a bus and a slider but no assets, and the UI bus has no sounds yet; both are one
  catalogue entry away.
- The world WAVs under `assets/sfx/world/` are synthesised in-repo (see
  `docs/ASSET_ATTRIBUTION.md`): real, correctly formatted and replaceable one file at a time, but
  deliberately placeholder-quality.
