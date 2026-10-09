# M7 FX — Desktop Acceptance Checklist

These checks require a runnable desktop build with a local match. They were documented for manual
verification; source-only sandbox checks do not prove shader compilation or visual behavior.

Enable the client development console (`-Dskystrike.debug=true` or the existing client dev switch).
The relevant cvars: `fx_debug` (F11) shows the particle/light/phase counts against the tier budget,
and `quality` (`low`/`mid`/`high`) retunes the budget live. `r_player_light` (M6) still works
alongside all of this.

## Event transport (§8.1)

- [ ] **Explosions are seen:** throw a frag or impact grenade and confirm the phased detonation —
  flash, ring, fireball, debris, smoke — at the detonation point, for every client that can see it.
- [ ] **Transport is unreliable-safe:** with the server under load (or a lossy link), a missing
  effect costs a missing spark, never a stale one, a wrong game state, or a desync.
- [ ] **Culling is per-recipient:** two clients, one looking at a detonation and one facing away —
  only the first sees it. A detonation inside the centre room is not described to a client outside
  the room wall.
- [ ] **Muzzle events:** fire any gun and confirm the muzzle flash, gas burst and ejected shell
  casing at the barrel — for the local player and for remote players you can see. A trigger click
  on cooldown is silent.
- [ ] **Bullet impacts:** fire into the ground and into a wall; confirm a dust-and-chips impact at
  the hit point, hugging the surface. Body hits emit no surface impact.

## The gate (the one that matters most)

- [ ] **Frag round a corner:** throw a frag so it detonates just behind a wall from your position.
  Confirm you see the explosion's **light on the wall face**, and **no particles through the wall**
  — no embers, sparks or smoke glowing on the far side. Turn to face the detonation directly and
  the full effect appears.
- [ ] **Additive glow respects fog of war:** an explosion outside your vision cone (but within
  reach) does not light up or sparkle at you. The additive batch is gated by the visibility
  texture exactly like M6's light pass.
- [ ] **Alpha particles are fogged:** smoke and dust inside the scene darken with the fog like
  everything else in the scene pass.

## Particles and budget (§8.2)

- [ ] **Pass order:** alpha particles (smoke, dust, debris, shell casings) are darkened by the fog;
  additive particles (sparks, fire, flash, muzzle) glow through darkness. Confirm by watching a
  smoke grenade in a dark corner: the cloud is dim, the molotov next to it is bright.
- [ ] **fx_debug budget gate (F11):** with `fx_debug on`, the `fx:` line shows
  `particles A/B alpha + C/D add + E/F cpu  lights G/H  phases I  events J  tier T`. Under heavy
  spam (a full squad auto-firing plus grenades), every live count stays at or below its cap —
  `A ≤ B`, `C ≤ D`, `E ≤ F`, `G ≤ H` — and the line still renders.
- [ ] **Live tier:** set `/quality low`, `/quality mid`, `/quality high` mid-match. Confirm the
  caps in the `fx:` line change immediately (500/1500/6000 GPU, 60/150/400 CPU, 2/6/16 effect
  lights) and that the low tier still shows fog, lights and particles, just fewer.
- [ ] **Settings fallback:** with the `quality` cvar untouched, change Quality in the in-game
  settings dialog (Low/Medium/High/Ultra) and confirm the tier follows (Ultra maps to High).
- [ ] **Shell casings (tier-2 proof):** fire and confirm the ejected casing arcs out of the barrel,
  bounces off the floor against the SDF, and settles. The full CPU collision tier (bouncing debris
  generally) is deferred beyond casings.
- [ ] **Determinism:** two clients watching the same frag see the same particle layout (the server
  assigns the seed).

## Presets (§8.3)

- [ ] **Frag / impact:** the phased schedule at 0.00/0.03/0.05/0.08/0.10 s, with the white-yellow
  flash and a light that is never culled while ordinary effect lights are.
- [ ] **Smoke grenade:** a billowing alpha cloud that grows into its vision-blocking shape. The
  occlusion is no longer one full-radius disc at the detonation point: the shared `SmokeCloud`
  cluster (three sagging circles, swollen by a growth factor over the cloud's first 40% of life)
  is derived identically by the server's sight queries and the visibility shader, so what blocks
  sight is the cloud you see, growing as it billows. See `docs/M12_UTILITY_VISIBILITY_ACCEPTANCE.md`.
- [ ] **Molotov:** glass sparks, a fire splash along the surface tangent (correct on floors,
  ramps and walls), one flickering attached light per fire zone that expires with the zone, and —
  from M12 — persistent flame rendering (`core/render/UtilityZoneRenderer`) that lives exactly
  as long as the zone burns and covers exactly the circle the damage-over-time resolves.
- [ ] **Flashbang / stun:** a white burst and residual wisps at the detonation, plus the full-screen
  whiteout scaled by the server's distance band and line of sight (`StunMath`). The whiteout
  recovers exponentially — overwhelming at first, clearing fast — and the HUD, chat and console
  stay readable throughout.
- [ ] **Bullet impacts:** concrete dust and chips today; the metal (sparks) and wood (splinters)
  catalogue entries exist and are ready for a surface-material pass. Decal bullet holes are
  deferred.

## Preservation checks (must not regress)

- [ ] **M6 lighting:** `r_player_light` and its cvars, the fog-of-war protections, and the
  M6_LIGHTING_ACCEPTANCE checklist all still pass. Effect lights share the bounded light pool with
  player lights; neither starves the other in a normal match.
- [ ] **Menus, audio, permissions:** Scene2D menus and settings, gun SFX, server-authoritative
  permissions and gameplay state are untouched by M7.
- [ ] **Resize:** resize the window and toggle fullscreen; particles, lights and the whiteout stay
  aligned (they are world-space and post passes, not sized targets).
- [ ] **Resource teardown:** disconnect to the menu, re-enter a match, and repeat at least twice.
  Confirm there are no GL errors, stale particles, lingering lights, or growing/leaked render
  resources. Effect lights are released from the pool on disposal.

## Known limitations (documented, not bugs)

- All arena solids emit the concrete impact; metal and wood await a surface-material pass.
- The muzzle-flash preset has no semi-automatic-only smoke wisp: the event carries no weapon
  identity, and adding one is a wire change.
- The blindness pass has no chromatic aberration or film grain beyond the animated grain it does
  have; the full secondary-effects treatment is a later phase.
- The particle vertex shader uses 12 vertex attributes. Desktop GL guarantees 16; every real
  GLES2 device reports 16 as well, but the ES2 spec minimum is 8 — the first true mobile target
  should confirm `GL_MAX_VERTEX_ATTRIBS` before shipping there.
- Effect-light budget is a share of the shared light pool (2/6/16 by tier), reserving the rest for
  M6 player lights; the pool's hard capacity is unchanged from M6.
