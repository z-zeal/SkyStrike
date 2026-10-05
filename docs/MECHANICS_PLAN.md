## CONCEPT

A side-view 2D multiplayer arena shooter. Two teams fight in a single symmetric arena. Players run, crouch, jump and fly with a limited-fuel jetpack, aim freely at 360° with the mouse independently of which way the body is tilted, and fight with a loadout of guns, a melee weapon, throwable utilities and deployable gadgets.

The defining mechanic is **restricted vision**: you only see what your character can actually see. A cone of sight extends from the player, terrain blocks line of sight, and everything outside that cone is black. Smoke clouds, flashbangs, surveillance drones and sticky cameras are therefore primary tools, not side-grades. Positioning and information control matter as much as aim.

Everything is simulated authoritatively on one host; players only sample input and display results.

---

## 1. ARENA

A single fixed map, 3000 × 2000 units, with a solid ground plane at height 100. All geometry is axis-aligned rectangles. The layout is **mirror-symmetric** so neither team has an advantage.

**Each side has:** a stepped ramp of five 120×18 platforms climbing 50 units per step (left ramp from x=120 at height 200 up to x=600 at height 400; right ramp mirrored), a mid-level traversal lane (250×18 at height 500), a two-tier crate stack (a 70×120 crate and a 70×160 crate, with standable 70×16 tops at heights 220 and 260), a tall lane pillar (24×260), and a tunnel choke support (22×170).

**The centre** is a fortified room: two full-height walls (24×560) forming the shell, two short inner pillars (16×130) at the entrances, a room floor (760×20 at height 280) and a roof catwalk (640×20 at height 620). Two low tunnel roof segments (180×14 at height 190) create a cramped ground-level corridor running underneath the room, giving a risky flanking route. One high platform (240×18 at height 760) sits above the room as a sniper perch.

Team A spawns at (240, 180), Team B at (2760, 180) — both on their own ramp, far apart, with the centre room as the contested space.

The same geometry must serve three purposes simultaneously: collision, rendering, and line-of-sight blocking. A wall that stops a bullet must also stop sight and block explosion damage.

---

## 2. THE PLAYER CHARACTER

150 maximum health. 30 units wide, 50 tall standing, 30 tall crouched.

### 2.1 Ground movement

Move speed 200 units/s, hard-capped at 300 units/s total velocity. Gravity −800. Crouching halves movement speed and shrinks the hitbox to 30 tall — a real trade of mobility for a smaller target and access under the tunnel roof.

Ground feel is deliberately grippy: ground linear damping 16 per second, air linear damping only 1.0. The result is precise, snappy ground control but floaty, committed air movement where momentum carries.

### 2.2 Jump and jetpack

Jump force 420. The jetpack applies 1000 units of thrust while held and burns fuel at 20 per second from a 100 capacity, giving **five seconds of continuous flight**. Fuel recharges at 20 per second but **only while grounded** — so flight is a resource you must land to replenish, and the recharge is exactly as slow as the burn.

Jetpack thrust is mostly vertical but subtly steerable: 0.85 of the thrust is straight up and 0.15 is modulated by where you are aiming, so aiming downward while flying lets you hover lower and aiming up climbs faster. This is a gentle lean, not a directional thruster.

### 2.3 Body rotation — the signature feel

The body is a physical object that tips and tumbles, while the aim stays independent. This is what makes the movement feel distinctive.

- **Grounded, the character always snaps upright to 0°.** There is no slope angle and no standing at an angle. A spring pulls rotation toward upright at 120 deg/s², with angular damping of 20 per second so landings settle fast.
- **Airborne, rotation is free.** The upright spring weakens to 50 deg/s² and angular damping drops to 4.0, so the body can tumble and bank in flight.
- **Coyote time of 0.10 s** — rotation stays locked upright for a tenth of a second after leaving a ledge, so brief hops and step-downs don't make the character flail.
- While airborne, three subtle torques combine: jetpack thrust direction contributes a lean (scale 0.04), aim direction contributes a lean (factor 0.12, capped so aim can never contribute more than 30° of influence), and horizontal velocity contributes perpendicular banking (factor 0.08) so moving right tips you right.
- **Backpedalling special case:** when horizontal speed exceeds 25 units and the player is moving *opposite* to the direction they are facing, aim torque is cut to zero and velocity banking is multiplied by 2.40. Flying backwards while shooting forwards produces a dramatic trailing lean instead of a muddled mix of torques.

### 2.4 Aim

Aim angle is 360° and fully independent of body rotation — the gun points at the cursor regardless of how the body is tipped. Aiming down sights (hold right mouse) does four things at once: reduces weapon spread, reduces recoil, extends vision range, and pans the camera 150 units toward the aim direction so you see further in the direction you're looking.

---

## 3. VISION AND FOG OF WAR

The core system. Treat this as a first-class mechanic, not a visual filter.

- Sight radiates from the player's head position as a **cone**. Hip-fire reach is 640 units; aiming down sights extends it to 1024 units. Aiming is therefore also a scouting action.
- **Terrain hard-blocks line of sight.** Anything behind a wall, crate or platform from the viewer's perspective is invisible, including enemies, projectiles and effects.
- Brightness must fall off **continuously**, never in visible bands or tiers: a quadratic falloff with distance, plus a feathered soft edge across the cone boundary, plus a small peripheral floor so the immediate surroundings outside the cone are dimly readable rather than pitch black. Flat percentage tiers produce ugly banding and must be avoided.
- Enemies outside the cone or behind terrain render pure black — they are genuinely hidden, not merely dimmed.
- Smoke volumes also block sight, independently of terrain, and can be queried at any world position.
- Teammates are optionally exempt from cone culling, and optionally always shown on the minimap — two separate toggles, defaulting to "teammates are culled like anyone else" and "teammates visible on minimap".

---

## 4. COMBAT

### 4.1 Hit zones

Two zones on the body plus one on a gadget:

| Zone | Region | Effect |
| --- | --- | --- |
| Head | Top 28% of current height (above 72% of body height) | 2.0× damage |
| Body | Everything below | 1.0× damage |
| Fuel tank | Rear-mounted gadget, if equipped | Triggers an explosion instead of taking normal damage |

Because the head zone is a fraction of *current* height, crouching lowers the head zone with the body — crouching is genuinely harder to headshot, not just shorter.

### 4.2 Ballistics

Bullets are physical travelling projectiles, not instant hitscan. They have muzzle speed, drop, drag and damage falloff, so leading a moving target at range is a real skill.

- Each weapon has a bullet speed from 520 (snub pistols) to 1820 (the Cathedral sniper rifle), clamped to the 520–1950 band.
- Bullets drop under a per-class gravity: snipers 1.0, DMRs 1.8, rifles 2.5, SMGs/PDWs 3.5, pistols and revolvers 4.0, shotguns 5.0. Gravity ramps in over a short time (0.5–1.8 s depending on weapon) so bullets fly flat at close range and arc at distance. For the big map the gravity scale is halved, and rounds past maximum range keep flying at floor damage (up to 4× range, 6 s airborne) instead of vanishing mid-air.
- A per-shot drag coefficient (0.980 on the slowest shotguns and pistols up to 0.998 on the fastest snipers) bleeds velocity over distance.
- **Damage falls off linearly with distance travelled**, from full damage at the muzzle down to a per-weapon minimum ratio at maximum range: sawed-off shotguns decay to 35%, SMGs to ~42–50%, pistols to ~55%, rifles to ~70%, DMRs to ~75%, snipers to 80%. A sniper keeps almost all its damage at any range; the Short Gospel is useless beyond a couple of body lengths.
- Very fast projectiles must never tunnel through thin geometry — above roughly 100 units/s, sweep the path from the previous position to the new one each tick rather than testing a point.

### 4.3 Spread — dynamic, stance-driven

Spread is a live value per player per weapon, not a constant. It is continuously pulled toward a target that depends on stance:

| Stance | Target spread |
| --- | --- |
| Standing still, hip fire | Base hip spread |
| Standing still, aiming | Base aim spread (much tighter) |
| Moving, hip fire | Hip spread × the weapon's moving multiplier (1.8–2.8×) |
| Moving, aiming | Aim spread × moving multiplier × 0.6 |

Every shot adds an instant **spread kick** (2.1° to 4.8° depending on weapon), capped at a per-weapon ceiling of roughly 2.7–4× the base spread. Spread then recovers toward the stance target at 4–9.5 degrees per second, and **recovery is 1.5× faster while aiming**. The spread kick itself is scaled by the current recoil multiplier, so aiming reduces both the kick and the recovery time.

The net effect: tapping while scoped and stationary is laser-accurate; holding the trigger while sprinting opens the cone wide within a second. Shot deviation is sampled from a normal distribution across the current cone, so most shots cluster near the centre.

### 4.4 Recoil

Three separate recoil effects fire on every shot:

1. **Linear recoil** — a physical push to the player's velocity, opposite the aim direction. This is real: heavy weapons shove you backwards, and firing downward while airborne is a legitimate way to gain height.
2. **Angular recoil** — spin applied to the body, which in air makes the character tumble.
3. **Visual recoil kick** — the gun's rendered angle offsets upward and decays back at 120 deg/s, capped at 35°.

All three are multiplied by the current recoil multiplier, which lerps smoothly toward the weapon's ADS value (0.45–0.78) while aiming, and by an additional moving multiplier (1.4–2.0×) while in motion. Airborne recoil is further scaled to 0.25 so flying players aren't thrown uncontrollably. Burst weapons apply 1.20× recoil for the burst.

### 4.5 Fire modes

- **Automatic** weapons fire continuously while held.
- **Semi-automatic** weapons fire once per trigger press at their cooldown rate.
- **Bolt, pump and break actions** are semi-automatic triggers with long cycles: one round (or shell) per press, and a fresh press required each time.
- **Burst** weapons fire 3 rounds per trigger pull in one instant, spaced 0.55° apart in a fixed pattern, each with a small random offset of 20% of current spread. The pattern makes bursts land as a tight readable group rather than a random clump. The trigger cadence is throttled so the quoted rounds-per-minute holds across whole bursts.
- **Shotguns** fire a per-weapon pellet count (up to 8, most commonly 8; the Brass Judge revolver throws 4) distributed evenly across the full spread cone, each pellet with random jitter — 1.5° hip-fire, 0.8° aiming. Every pellet is a full damage instance, so point-blank hits are lethal and the falloff makes range useless. A shell costs one magazine round regardless of pellet count.

### 4.6 Friendly fire

Friendly fire is **on by default, including self-damage**. Your own grenades, molotov fire and explosions hurt you and your teammates. Neutral-team players both take and deal damage to everyone. This makes throwables genuinely risky in close quarters.

---

## 5. WEAPONS

### 5.1 Guns

The roster is **89 guns across ten classes**, generated from the sprite catalog
`assets/sprites/guns.json` by `tools/weapons/generate_weapon_tables.py`. The full stat table
lives in [`docs/WEAPONS_TABLE.md`](WEAPONS_TABLE.md); the generated enums and registries are the
single source of truth in code (`shared/weapons/WeaponId`, `WeaponRegistry`, `WeaponBallistics`).

| Class | Count | Role |
| --- | --- | --- |
| Pistol | 12 | Fast-swap sidearms; 25–40 damage, quick reloads |
| Revolver | 8 | Heavy sidearms; the Longspur .44 two-shots a body |
| SMG | 12 | High rate-of-fire hoses (up to 20 rounds/s) that fall off hard past mid range |
| PDW | 6 | Compact autos between pistols and SMGs |
| Assault rifle | 16 | The 5–6 body-shot workhorses (Iron Carbine is the default) |
| Battle rifle | 2 | Slower, harder-hitting autos |
| DMR | 8 | Semi-auto precision; 2–3 body shots |
| Sniper | 10 | Bolt and semi; every one of them is a one-shot headshot, two-shot body |
| Shotgun | 11 | Pump/break one-shell kills up close, autoloaders two-shell |
| LMG | 4 | Big belts, heavy recoil, slow reloads |

Representative flagships, given 150 health: the **Cathedral** (126.5 damage, bolt) and every
other sniper one-shot a headshot and two-shot a body. The **Magpie** semi sniper tops out at
149.5 — no gun body-one-shots in a single projectile. The **Iron Carbine** kills in five body
hits at 12 rounds/s. The **Scatter Bench** pump and **Cinder Tube** break-action kill with one
point-blank shell (8 pellets); autoloading shotguns like the **Room Sweeper** need two. The
**Longspur .44** magnum two-shots a body; the **Smoke Stitch** LMG shreds at 15 rounds/s from a
50-round belt.

Per-weapon ADS behaviour varies meaningfully: snipers tighten to ~18% of hip spread, SMGs only
to ~42% — snipers reward scoping, SMGs barely care.

### 5.2 Melee

Always available in slot 3 and can never be removed, so a player is never defenceless. Melee
swings deal damage in an arc in front of the player and apply knockback.

The roster is **21 melee weapons**, generated from `assets/sprites/melee.json` into
`shared/weapons/MeleeId` and `MeleeRegistry`; the full table is in
[`docs/WEAPONS_TABLE.md`](WEAPONS_TABLE.md). Flavour spans knuckles, knives, machetes, tools and
long blades. Representative rows:

| Weapon | Damage | Swings/s | Range | Knockback |
| --- | --- | --- | --- | --- |
| Trench Knuckle (default) | 45 | 1.5 | 64 | 290 |
| Winter Katana | 76 | 1.067 | 78 | 190 |
| Mill Zwei | 106 | 0.667 | 89 | 330 |
| Frost Naginata | 81 | 0.833 | 103 | 210 |
| Night Letter | 34 | 2.167 | 64 | 130 |

No melee weapon one-shots a body (the Mill Zwei's 106 is the ceiling). Knockback is a real
physics impulse — a heavy swing at an airborne enemy launches them, and can be used to shove
opponents off ledges or into fire.

---

## 6. THROWABLE UTILITIES

Two utility slots, each holding a type with a limited carried count. Throwables arc under their own gravity of −500, bouncing off terrain with 0.35 vertical restitution and 0.70 horizontal friction per bounce — so they settle quickly instead of skating away, and bank shots around corners are practical. A predictive trajectory arc is drawn while a throwable is equipped, using identical physics to the real projectile.

Explosion damage is **always terrain-occluded**. Damage is resolved by casting rays outward from the detonation point: if terrain blocks the path to a target, that target takes nothing regardless of proximity. Hiding behind a crate genuinely saves you. Damage within line of sight falls off linearly with distance. A target can only be hit once per explosion.

| Utility | Throw force | Fuse | Radius | Damage | Cooldown | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| Frag grenade | 800 | 2.5 s | 350 | 100 | 1.0 s | 950 explosion impulse — launches bodies |
| Impact grenade | 850 | On contact | 280 | 85 | 1.2 s | No fuse, 750 impulse; faster but less damage |
| Smoke grenade | 700 | 1.0 s | 250 | 0 | 0.8 s | Blocks vision for 8 s |
| Stun grenade | 720 | 1.8 s | 600 | 0 | 1.0 s | See below |
| Molotov | 650 | On contact | 180 | 21/tick | 1.2 s | Burns 6 s, spreads along surfaces |
| Poison smoke | 680 | 1.2 s | 220 | 12/tick | 1.0 s | 7 s toxic cloud, damage over time |
| Flashbang | 750 | 1.5 s | 300 | 0 | 0.8 s | 3 s blind |
| Claymore | Placed | Proximity | — | — | — | Directional cone blast, triggers on proximity |

### 6.1 Stun grenade — distance-tiered and sight-dependent

The most tactically detailed throwable. Within its 600-unit blast radius, effects scale by distance band:

| Band | Blind duration | Movement slow duration |
| --- | --- | --- |
| Inner 30% | 7.0 s | 4.0 s |
| 30–70% | 4.5 s | 2.5 s |
| 70–100% | 2.0 s | 1.0 s |

Critically, **a target who is in range but does not have line of sight to the detonation takes only a 0.3 s reduced concussion**. Turning away or ducking behind cover genuinely saves you, exactly as it should. Stunned players move at 30% speed and cannot fire.

Blindness itself is a heavy screen effect that decays exponentially — overwhelming at first, clearing quickly toward the end — combining a white overlay, chromatic aberration, a vignette and animated grain, plus an audio ringing effect.

### 6.2 Molotov — surface spread

The molotov breaks on first contact and spreads fire **along the surface it hit**, using the surface tangent — so it works correctly on floors, ramps and vertical walls. The impact produces one central fire zone plus additional zones cast outward along the surface at a 2.0-unit offset with slight jitter. The central zone deals 21 damage per tick, spread zones 17, for 6 seconds. Fire zones are area denial: they deny a corridor, flush campers out of the tunnel, and happily kill the thrower who stands in their own flames.

---

## 7. GADGETS

Two independent gadget slots (Q and E) that work regardless of which main slot is active. Three activation behaviours exist: passive (always on), manual (press to use), and hybrid (passive effect plus a manual toggle).

### 7.1 Drone — manual

Press to deploy a flying drone 1 unit above you; press again to switch your point of view to it. The drone flies freely at speed 8 with smooth velocity lerping and damping, clamps to the arena bounds and pushes out of walls. It has 30 health and is destroyed by gunfire.

The drone projects **its own vision cone** — 10 units at 70°, narrower and dimmer than a player's — so it reveals territory you cannot see yourself. While piloting the drone your body is **completely vulnerable**: all your own movement and weapons are locked out. Scouting is a genuine risk, not free information.

### 7.2 Throw camera — manual

Thrown along your aim direction at speed 12 on a gravity arc, sticking to the first surface it touches and becoming a permanent fixed observation post. Press again to view through it, at 1.2× zoom. 20 health, destructible, and destroyed if it leaves the arena. Unlike the drone it cannot move — it trades mobility for persistence, letting you watch a lane indefinitely.

### 7.3 Shield — hybrid

150 durability. Toggles between two states, each protecting a different side:

- **Equipped** — held in front, absorbing anything that hits within a 90° frontal arc. While equipped you may only use your handgun, making it a deliberate push/defend tool rather than a free buff.
- **Stowed** — worn on the back, protecting your rear from anything hitting from behind.

Absorbed damage is subtracted from durability instead of health. At zero durability the shield breaks permanently and offers no protection. The front/back trade-off means a shield player must consciously choose whether they are advancing or retreating.

### 7.4 Fuel tank — passive

Multiplies jetpack capacity by 1.75 and thrust by 1.40 — dramatically more air time and climb rate. The cost is a **new weak point**: a rear-mounted tank that, when shot, detonates for 120 damage in a 4-unit radius, killing the wearer and anyone nearby. It turns mobility into a liability from behind and rewards flanking.

---

## 8. LOADOUT AND SLOTS

Five main slots plus two gadget slots:

1. Primary weapon
2. Handgun
3. Melee (always filled, cannot be empty)
4. Utility A
5. Utility B
- Q and E — gadgets, used independently of the active slot

Switching rules:

- Keys 1–5 select a slot directly, but only if it holds something.
- Pressing **1 or 2 while that same slot is already active** quick-swaps down to melee and remembers which weapon you came from; pressing it again returns to that weapon. This gives a fast melee tap-swap without losing your place.
- The mouse wheel cycles through filled slots only, skipping empties.
- Throwing a utility decrements its count; at zero the slot empties and is skipped by cycling.
- Weapons track magazine ammo and reserve ammo separately, with per-weapon reload times.
- Switching weapons resets that weapon's accumulated spread and recoil state.

---

## 9. CONTROLS

| Input | Action |
| --- | --- |
| A / D (or Left / Right) | Move |
| W (or Up) | Jump |
| Space | Jetpack |
| S / Down / Left Ctrl | Crouch |
| Mouse | Aim (360°, independent of body) |
| Left mouse | Use active slot — fire, swing or throw |
| Right mouse | Aim down sights |
| 1–5 | Select main slot |
| Mouse wheel | Cycle filled slots |
| Q / E | Activate gadget |
| 6 | Cycle view: self → drone → camera → self, skipping unavailable |
| Escape | Exit surveillance view |

While viewing through a drone or camera, movement keys drive **that device** and the player's own body is frozen and defenceless. Only the view-cycle and gadget keys remain live.

---

## 10. TEAMS, DAMAGE FEEDBACK, DEATH

Three allegiances: Team A, Team B and Neutral. Neutral players fight everyone and are fought by everyone. Teams are visually distinguished by tint — Team A blue, Team B red, Neutral untinted.

Damage feedback the player must receive:

- Floating damage numbers at the hit location, tinted by the damage ratio so falloff is legible, and visually distinct for headshots.
- A screen-edge pulse when a single hit lands for 35 or more damage, so heavy hits are unmistakable.
- Directional and distance-scaled camera shake from nearby explosions.
- Persistent decals: bullet holes lasting 10–15 s, grenade scorch marks sized to blast radius lasting 20–30 s, larger rocket scorches lasting 30–40 s. Decals let players read where a fight happened.

At zero health the player dies, drops out of the simulation and may no longer act. Respawning restores full health and fuel, clears every timer, status effect and gadget state, resets the loadout to default, and places the player upright at their team spawn.

---

## 11. FEEL CHECKLIST

The build is correct when all of the following are true:

- Ground movement is crisp and immediate; air movement is floaty and committed.
- Five seconds of flight feels precious, and landing to refuel is a real tactical decision.
- The body visibly tips, banks and tumbles in flight while the gun stays locked on the cursor.
- Firing a heavy weapon while airborne noticeably pushes you.
- Standing still and scoping is visibly far more accurate than running and spraying, and the difference develops over about a second rather than instantly.
- A bullet fired across the full arena visibly drops and deals clearly less damage than one fired point-blank.
- Fog of war brightness is smooth with no visible bands; an enemy behind a crate is completely invisible.
- A grenade on the far side of a wall does zero damage.
- Turning away from a stun grenade in time meaningfully reduces its effect.
- Your own molotov kills you if you stand in it.
- Piloting a drone feels like a real gamble because your body is helpless.
- Getting shot in the fuel tank from behind is explosive, fatal and entirely fair.