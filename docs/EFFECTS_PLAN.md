# Effect & Particle System — Build Plan for a New Project

> A from-scratch plan for building the lighting, fog-of-war, particle and post-processing layer of a 2D shooter.
> Assumes an empty project. Nothing here depends on an existing codebase.

**Hard constraints, set up front:**

1. **Pure OpenGL.** Every visual effect is a vertex/fragment shader program we write and own.
2. **No Box2DLights.** No third-party lighting library of any kind. Lighting, shadows and fog are ours.
3. **No physics engine in the effects path.** Particle collision and light occlusion must not depend on Box2D or any rigid-body engine.
4. **Mobile-first.** OpenGL ES 2.0 is the baseline. Anything that cannot run on ES 2.0 is not in the design.

---

## 1. WHAT THIS LAYER MUST DELIVER

The effects layer is responsible for everything the player sees that is not terrain, a sprite or HUD text. Concretely, it must produce:

**Fog of war / vision.** A cone of sight from the player with continuous distance falloff, a soft feathered edge, a dim peripheral floor, and hard terrain occlusion. Additional cones from deployable drones and cameras, unioned with the player's. Smoke clouds that locally subtract visibility.

**Dynamic lighting.** Coloured point lights with animated colour and radius curves, terrain-shadowed, used by explosion flashes, fire zones, muzzle flashes, flares and vehicle/gadget glows.

**Particles.** Smoke, fire, sparks, embers, debris, dust, glass shards, casings — with per-material variants for bullet impacts (concrete, metal, wood, dirt).

**Multi-phase explosions.** Time-staggered sequences combining flash, shockwave, fireball, debris, embers, smoke and a ground scorch, each phase firing on its own schedule.

**Screen effects.** Flash/stun blindness with chromatic aberration, vignette and grain; camera shake; screen-edge damage pulse.

**Persistent marks.** Bullet holes and scorch decals that survive for tens of seconds and fade out.

**Trails and distortion.** Bullet tracers, expanding shockwave refraction, heat haze above fire.

Everything above must scale down gracefully to a low-end phone without any effect disappearing entirely.

---

## 2. WHAT THE HOST GAME MUST PROVIDE

This layer is a consumer. Before building it, the surrounding project must supply four things. Define these interfaces first; they are the seams that keep the effects layer reusable.

**1. Static terrain as a list of axis-aligned rectangles**, available at load time and never changing during a match. This is the single most important prerequisite — the entire occlusion design depends on it. (If your terrain is dynamic or arbitrary polygons, see §4.3.)

**2. A 2D orthographic camera** exposing centre position, zoom and viewport size.

**3. An effect event vocabulary** — a closed enum of effect types the game can request (`EXPLOSION_FRAG`, `BULLET_HIT_METAL`, `MOLOTOV_IMPACT`, `STUN_DETONATE`, and so on), plus a thread-safe way to enqueue them. Network code will raise these from a non-render thread, so the queue must be lock-free and the effects layer must drain it on the render thread. **No effect may ever touch GL state from a network callback.**

**4. Observer state** — position, aim direction and vision range of the local player and any active surveillance device.

---

## 3. GL BASELINE AND MOBILE RULES

**Baseline: OpenGL ES 2.0 / OpenGL 2.1.** An ES 3.0 path may be detected and used opportunistically, but nothing may *require* it.

What ES 2.0 forbids, and how the design accommodates it:

| Not available | Design consequence |
| --- | --- |
| Instanced drawing | Particles emit 4 real vertices each with duplicated per-particle attributes. No instancing anywhere. |
| Transform feedback / compute shaders | GPU particle simulation must be **stateless and analytic** — position is a closed-form function of elapsed time, never integrated frame to frame. |
| Guaranteed vertex texture fetch | The particle vertex shader must never sample a texture. This is precisely why collision-aware particles stay on the CPU. |
| Multiple render targets | Each buffer is its own pass. Pass count is a budget. |
| Guaranteed `highp` in fragment shaders | See §3.1. This is the subtle one. |
| Float textures | All buffers and the distance field pack into 8-bit channels. |
| NPOT with mipmaps or REPEAT wrap | Every texture is CLAMP_TO_EDGE with no mipmaps — legal for NPOT on ES 2.0. |

### 3.1 The precision rule

`highp` support in fragment shaders is *optional* on ES 2.0. On a device that silently drops to `mediump` (~10-bit mantissa), world coordinates in the thousands lose enough precision to visibly quantise the fog mask and stair-step shadow edges.

**Rule: no shader ever works in absolute world coordinates.** All position maths is camera-relative — the CPU uploads positions already offset by the camera centre, keeping magnitudes within roughly ±1000 where `mediump` is safe. The only conversion to world space is a normalised 0–1 texture coordinate for the distance field.

Shaders that genuinely need range declare `highp` behind a `GL_FRAGMENT_PRECISION_HIGH` guard with a documented fallback. Build a desktop debug mode that **forces `mediump`** from day one, and test in it regularly. Discovering this class of bug on a user's phone is miserable; discovering it on your desktop takes a keystroke.

### 3.2 Tile-based GPU rules

Mobile GPUs are tile-based deferred renderers and are fill-rate bound, not triangle bound:

- **Always clear at the start of a render pass.** Skipping the clear forces the tile to load previous framebuffer contents from main memory — a large, invisible bandwidth cost.
- **Never read back a framebuffer mid-frame.** No pixel reads, no sampling a target you are still writing to.
- **Minimise framebuffer switches** — each one flushes tiles. Target four or fewer render targets per frame on mobile.
- **Render light, visibility and distortion buffers at half resolution.** These are low-frequency signals; the difference is invisible and it quarters the cost of the most expensive passes.
- **Avoid `discard`** where a blend equation achieves the same result.
- **Cap full-screen passes**: five on desktop, three on mobile.

---

## 4. THE FOUNDATION: A BAKED SIGNED DISTANCE FIELD

### 4.1 The decision

Both hard problems in this layer — "does light reach this pixel?" and "has this particle hit the ground?" — are the same question: *where is the nearest solid surface?*

Because terrain is static and known at load time, that question can be answered once and baked into a texture: a **signed distance field** storing, for every point in the arena, the distance to the nearest solid surface.

One asset then serves five consumers:

| Consumer | Uses the SDF for |
| --- | --- |
| Fog-of-war shader | Occlusion between observer and pixel |
| Light shader | Shadowing between light and pixel |
| Particle collision | Hit test, plus surface normal from the field gradient |
| Particle spawning | Occlusion test so nothing spawns through a wall |
| Gameplay queries | Line-of-sight for entity culling and stun-grenade checks |

Because all five read the same data, they cannot disagree. That matters more than it sounds: a CPU visibility check that contradicts the GPU fog makes entities pop in and out at cone edges, and it is a miserable bug to chase.

This is also what lets us honour constraint 3 — no physics engine in the effects path. Particle collision becomes arithmetic: sample the field, compare to the particle radius, reflect the velocity along the gradient. No rigid bodies, no contact listeners, no deferred-destruction hazards, and no zero-length-vector assertions.

### 4.2 Format and baking

- **Resolution: 2 world units per texel.** For a 3000 × 2000 arena that is 1500 × 1000 texels.
- **Storage: a single 8-bit channel** → 1.5 MB on the GPU.
- **Encoding: signed and biased.** Value 128 is the surface; above is empty space, below is solid interior. One distance unit per value step, clamped to ±127 — beyond that, clearance is effectively infinite for marching purposes.
- **Filtering: LINEAR, CLAMP_TO_EDGE.** Bilinear interpolation is what makes shadow edges smooth rather than blocky; clamping makes off-map samples read as solid.

Resolution rationale: size the texel against your **thinnest** piece of geometry, not your average. A 14-unit-thick platform is 7 texels at this resolution — comfortably resolved. At 4 units/texel it would be 3.5 texels and would start leaking light through floors.

**Baking is two stages:** rasterise the rectangles into a solid/empty bitmask, then run an 8SSEDT (8-point signed sequential Euclidean distance transform) — two sweeps with eight neighbour comparisons each, O(n), roughly 24 M operations and 50–100 ms in Java.

Do **not** brute-force "distance to nearest of N rectangles" per texel. For a few dozen rectangles that is 50 M+ distance evaluations and will visibly stall your loading screen.

**Ship the field as a baked asset.** Bake offline via a build task and load the file at startup for zero cost. Keep runtime baking behind a flag as a development fallback and for future map editing, caching its result to local storage.

### 4.3 If your terrain is not static rectangles

The design still holds, with one substitution:

- **Arbitrary static polygons** — same plan; the rasterise step is just a polygon fill instead of a rectangle fill.
- **Occasionally-changing terrain** (destructible walls) — keep the bake, and re-run it incrementally for the affected region only. A localised 8SSEDT over a dirty rectangle is cheap.
- **Fully dynamic occluders** (moving platforms, physics debris blocking light) — keep the static SDF for terrain and add a small uniform array of dynamic occluder segments (cap around 8), taking the minimum of the SDF march and an analytic segment test. Do not try to re-bake a full field every frame.

### 4.4 Sampling

**On the GPU**, three operations live in a shared shader include and are used by every lighting shader:

- *Sample* — convert a camera-relative position to normalised field coordinates, fetch, decode to signed world distance.
- *Sphere-trace* — march from the fragment toward the light, stepping by the sampled distance each iteration. That step is always safe: by definition nothing is closer than the distance to the nearest surface. Terminate on hit, on arrival, or on exhausting the step budget. Typical cost is 8–20 iterations in open space and far fewer near walls.
- *Soft shadow* — while marching, track the minimum of `k × distance / distance-travelled`. This standard penumbra estimator yields physically plausible soft shadows whose softness grows with distance from the occluder, for free.

Step budget is a quality-tier uniform: 48 desktop, 24 mid mobile, 12 low.

**On the CPU**, keep the same baked array in RAM (1.5 MB) and expose sampling, gradient (central difference of four neighbours) and marching. This backs particle collision, spawn occlusion and gameplay line-of-sight.

**Parity is a hard requirement.** The CPU and GPU paths must use the same field, the same step budget, the same bias and the same falloff formula. Define the falloff once and mirror it exactly. Build a debug overlay that draws the CPU visibility verdict over the GPU fog so any disagreement is immediately obvious.

---

## 5. FRAME PIPELINE

One orchestrator owns pass order, explicitly. Pass ordering bugs — clearing the wrong buffer, drawing additive effects before the darkness pass so they get multiplied away — are the most common failure mode in a stack like this, and they only stay fixed if ordering lives in exactly one place.

**Per frame:**

1. **Update** — advance the effect clock, drain the event queue from the network thread, retire dead emitters, lights and decals, simulate CPU-tier particles against the SDF.
2. **Scene pass** → scene target. Terrain, decal composite, entities, weapons, and alpha-blended particles (smoke, dust, debris) which *should* be darkened by fog.
3. **Visibility pass** → half-res single channel. Player vision cone plus any active drone/camera cone, each SDF-occluded, combined with **max blending** so overlapping cones union rather than double-brighten.
4. **Light pass** → half-res RGB, additive. One quad per emissive light, each SDF-shadowed.
5. **Distortion pass** → half-res two-channel. Shockwave rings and heat haze write screen-space UV offsets. Skipped entirely on the low tier.
6. **Composite** → `scene × max(visibility, ambientFloor) + light`, sampling the scene through the distortion offset. One pass, three texture reads.
7. **Additive overlay** — fire and spark particles that must glow *through* darkness, drawn after composite so fog does not multiply them down.
8. **Post chain** — optional bloom (bright-pass plus two quarter-res blur taps), then blindness/aberration/vignette/grain, then final blit.
9. **HUD** — drawn last, untouched by any of the above.

**The composite rule that defines the game's look: visibility multiplies, light adds.** An explosion behind you brightens a wall you can see even though that wall is outside your cone, because light is emissive — but it is still SDF-shadowed, so an explosion on the far side of a wall stays invisible. This gives correct, readable behaviour without special cases.

---

## 6. LIGHTING AND VISIBILITY

### 6.1 Lights

A light is plain data: position, radius, colour, intensity, falloff exponent, a shadow-casting flag, and an optional animation curve (start colour → mid colour → end colour over start/transition/end times). That curve alone covers explosion flashes going white → orange → transparent, fire flicker and flare decay.

**Lights are pooled and referenced by integer handle, never by object reference.** Handles make leaks impossible, let the system retire lights under budget pressure, and keep gameplay code from holding a GL resource.

**Rendering:** one quad per light, sized to its radius, drawn into the half-res light target with additive blending. The fragment shader computes radial attenuation × SDF soft shadow × animated colour. No mesh generation, no CPU raycasting, no per-light framebuffer.

Cost therefore scales with total lit **area**, not light count — twenty small muzzle flashes are cheaper than one large explosion. When live lights exceed the tier cap, keep the brightest and nearest to camera; flag explosion flashes high-priority so they are never the ones culled.

### 6.2 Visibility

Its own single-channel half-res target, kept conceptually separate from lighting.

One quad per observer, covering that observer's reach, with a fragment shader computing:

- **Distance falloff** — quadratic ease-out from full visibility at the observer to the fog floor at maximum reach. If hip-fire and aimed reach differ, **interpolate between them over time**; snapping between ranges makes scoping strobe.
- **Angular falloff** — a smooth feather across the cone edge, holding a small peripheral floor outside it so the player's immediate surroundings stay faintly readable.
- **Occlusion** — the SDF soft shadow from the observer's eye position.

**Smoke is sampled in this same pass**: upload smoke volumes as a small uniform array of circles (position, radius, density) that subtract from visibility. Doing it here rather than in a separate system means smoke obscures vision in exactly the pass that computes vision, and the gameplay query and the visuals cannot drift apart.

The target look is **continuous brightness with no visible banding**. Avoid any design that assigns flat brightness tiers to distance or angle bands — it produces obvious percentage rings and is the most common way 2D fog looks cheap.

---

## 7. PARTICLE SYSTEM

### 7.1 Two tiers, split by whether a particle collides

**Tier 1 — GPU stateless particles.** The large majority: sparks, embers, smoke puffs, muzzle flash, glass shards, dust, fireballs, flare glow, tracers. Their motion is a closed-form function of age, so there is nothing to integrate frame to frame.

- On spawn, write each particle's **descriptor once** into a persistent vertex buffer: spawn position, initial velocity, spawn time, lifetime, start/end size, packed start/end colour, gravity scale, drag, rotation speed, turbulence amplitude, plus a per-vertex corner offset.
- Each frame, the only thing that changes is a single time uniform. **No CPU work and no buffer upload for the particle's entire life.**
- The vertex shader derives age, computes position analytically (ballistic integral with exponential drag), evaluates size and colour curves, applies curl-noise turbulence, and collapses the quad to zero size past end-of-life so dead particles cost nothing downstream.
- Manage slots as a ring buffer; reclaim a burst's range once its longest lifetime has elapsed.

This is what makes thousands of particles affordable. A 400-particle explosion is one buffer write and then free.

**Tier 2 — CPU collision particles.** The minority that must bounce and settle: debris on floors, shell casings, embers pooling on the ground. Simulated on the CPU against the SDF — sample distance, compare to radius, reflect along the gradient, apply restitution — then written as pre-transformed quads each frame. Keep this tier capped in the low hundreds; it is the expensive one, and it should be the exception.

Both tiers share one fragment shader and one renderer.

### 7.2 Rendering

- Batch by **blend mode first, then texture.** Minimising state changes matters more than minimising draw calls.
- Two blend modes: **alpha** (smoke, dust, debris — drawn into the scene *before* fog so they are correctly darkened) and **additive** (fire, sparks, flashes — drawn *after* composite so they glow through darkness).
- The default soft circle needs **no texture at all** — a radial gradient is a couple of instructions in the fragment shader. This removes both a texture upload and a fetch per fragment.
- Textured particles come from a single atlas so an entire frame's particles can batch together.

### 7.3 Emitter configuration

Make effects **data, not code**. One config type describes count, lifetime range, size and colour curves, speed and spread, gravity, drag, blend mode, turbulence, an optional attached light, and the tier. A central library holds the named presets.

This is the part you will iterate on most, so make it cheap to iterate: data-driven presets plus shader hot reload means tuning an explosion is a two-second loop, not a recompile.

---

## 8. SECONDARY EFFECTS

**Decals.** Do not re-draw a quad per decal per frame for 40 seconds. Stamp each decal **once** into a persistent half-resolution world-space decal target and composite that target as a single quad during the terrain pass. Cost becomes one stamp at creation plus one read per frame, independent of decal count. Fade by periodically blending the whole target toward transparent. Memory is about 6 MB at half res for a 3000 × 2000 arena; the low tier falls back to plain quads with a hard cap.

**Shockwaves.** Implement as **screen-space refraction**, not additive rings. Each ring writes a radial UV offset into the distortion buffer and the composite samples the scene through it. This reads as real air displacement, and it is cheaper than a sprite ring because the pass already exists. Pair with an additive flash sphere for the detonation itself.

**Heat haze.** Fire zones write a low-amplitude noise-driven offset into the same distortion buffer. Essentially free once the buffer exists.

**Bullet trails.** A Tier-1 particle variant with a stretched quad and a **procedurally generated** gradient in the fragment shader. No separate system and no gradient texture.

**Blindness.** A full-screen post pass: white overlay driven by an intensity uniform, per-channel chromatic aberration (shift red and blue in opposite directions on X, green slightly on Y), a vignette that deepens with intensity, and animated grain. Recovery should be **exponential** — overwhelming at first, clearing fast toward the end — which feels far better than a linear fade.

**Bloom.** Optional, desktop and high-tier mobile only. Quarter-res bright-pass plus separable blur in two passes. Explosions and muzzle flashes are the payoff.

**Aim guides.** Draw spread cones and dotted guide lines as a single procedural quad with an analytic fragment shader, rather than an immediate-mode shape renderer. Avoids a second renderer, a state switch, and the begin/end batch juggling that immediate-mode drawing forces on you.

---

## 9. EFFECT CATALOGUE TO BUILD

The concrete presets the layer should ship with.

**Bullet impacts, per surface material:** concrete (white dust cloud plus small chips), metal (bright white-yellow sparks plus molten droplets), wood (brown splinters plus floating dust motes), dirt (dark clumps plus fine dust). Each spawns a bullet-hole decal.

**Frag explosion**, as a time-staggered phase sequence rather than one burst:

| Time | Phase |
| --- | --- |
| 0.00 s | Intense white-yellow flash sphere, bright particle burst, first shockwave ring |
| 0.03 s | Second smaller inner ring |
| 0.05 s | Orange fireball puffs |
| 0.08 s | Debris chunks and ember shower |
| 0.10 s | Black-grey smoke cloud (longest-lived) |
| 0.12 s | Ground scorch decal |

A larger rocket variant reuses the same schedule with bigger radii and higher counts. Drive phases from a pending-event queue so nothing happens synchronously and network-thread callers only enqueue.

**Molotov impact:** glass-shard sparks at the point of contact, a fire splash spread along the **surface tangent** (so it behaves correctly on floors, ramps and vertical walls), and a rising fire column at the centre. Each fire zone carries an attached flickering light and writes heat haze.

**Smoke grenade:** a billowing cloud that registers a smoke volume for the visibility pass, with particles spawned only where unoccluded.

**Stun/flash detonation:** white flash particles, a concussion ring in the distortion buffer, wispy residual smoke, plus the full-screen blindness pass scaled by distance and line of sight.

**Muzzle flash:** a three-layer composite — a core flash disc, a hot gas burst, and a smoke wisp on semi-automatic weapons only — plus casing ejection as a Tier-2 collision particle so shells bounce and settle.

**Flare:** a long-lived sticky emitter trickling ambient smoke and glow sparks with a persistent coloured light.

**Universal rule: occlusion-test every particle spawn position.** No particle may ever appear on the far side of a wall from its source. This single rule is what separates effects that feel grounded in the world from effects that feel pasted on top of it.

---

## 10. SHADER INFRASTRUCTURE

Build this *before* writing the second shader, not after the tenth.

**Shader source lives in `assets/shaders/` as `.vert` and `.frag` files** — never as string literals in source code. Concatenated GLSL in a host language cannot be syntax-highlighted, diffed usefully, or debugged by the line numbers the driver reports.

- **A loader with `#include` support**, resolving against a shared `lib/` directory and prepending a generated header (precision qualifiers, version shim, tier defines). This is how SDF sampling, noise and colour-ramp helpers are shared across a dozen programs without copy-paste.
- **Variants via injected `#define`s**, not duplicated files — one particle shader compiled with and without turbulence, one light shader with and without shadows.
- **Readable compile diagnostics**: on failure, log the shader name, the injected defines, and the full source with line numbers.
- **Hot reload on desktop.** Watch the directory, rebuild on save. This pays for itself in the first afternoon of tuning fog falloff.
- **Warm-up at load.** Mobile drivers compile lazily on first draw, so the first explosion of a session hitches. Compile and issue one off-screen draw for every program during the loading screen.
- **A fallback shader** (flat magenta) so a compile failure is loudly visible but never crashes.

---

## 11. BUDGETS AND QUALITY TIERS

One budget object, resolved at startup from device capability and adjustable at runtime, read by every subsystem. **No subsystem hardcodes its own cap.**

| Setting | Low (old mobile) | Mid (typical mobile) | High (desktop) |
| --- | --- | --- | --- |
| GPU particles | 500 | 1,500 | 6,000 |
| CPU collision particles | 60 | 150 | 400 |
| Simultaneous lights | 4 | 8 | 32 |
| Light/visibility buffer scale | ¼ | ½ | 1 |
| SDF march steps | 12 | 24 | 48 |
| Soft shadows | off (hard edges) | on | on |
| Distortion buffer | off | on | on |
| Bloom | off | off | on |
| Decals | quads, cap 32 | persistent target | persistent target |
| Full-screen passes | 2 | 3 | 5 |

**Degrade gracefully, never binary.** At the low tier the game still has fog, lights and particles — fewer, cheaper, harder-edged. Only bloom and distortion vanish entirely, and both are garnish.

Targets: ≤ 4 ms/frame for effects on desktop at 1080p, ≤ 8 ms on mid-tier mobile. Instrument every pass with GPU timer queries where available and a CPU fallback, surfaced in a debug overlay alongside draw calls, state changes and bytes uploaded. **Build the overlay in Phase 0**, not when you first suspect a problem.

---

## 12. FILE STRUCTURE

### 12.1 Code

```
fx/
├── FxSystem               Facade — the only type gameplay code touches
├── FxPipeline             Frame orchestration, pass order, render targets
├── FxBudget               Quality tiers and runtime caps
├── FxClock                Effect time base (pausable, independent of game time)
│
├── gl/                    Thin reusable GL layer — no game knowledge
│   ├── GlCaps             ES2/ES3 detection, precision and extension probing
│   ├── ShaderLibrary      Load, #include resolve, define injection, cache, hot reload
│   ├── ShaderHandle       Compiled program plus uniform location cache
│   ├── ShaderWarmup       Pre-compile and pre-draw every program at load
│   ├── RenderTarget       Framebuffer wrapper with scale factor and resize
│   ├── RenderTargetPool   Reuse of transient targets
│   ├── FullscreenQuad     Shared screen-space quad
│   ├── QuadBatch          Generic world-space textured quad batcher
│   ├── DynamicMesh        Per-frame streaming vertex buffer
│   ├── StaticMesh         Write-once persistent buffer (GPU particles)
│   └── BlendState         Named blend modes, redundant-change filtering
│
├── sdf/                   The occlusion field — build this first
│   ├── SdfBaker           Rasterise rects, 8SSEDT distance transform
│   ├── SdfField           CPU side: sample, gradient, raymarch, visibility
│   ├── SdfTexture         GPU upload, binding, texel/world transforms
│   ├── SdfCache           Load or save the baked field
│   └── SdfDebugView       Visualise the field and march steps
│
├── particles/
│   ├── ParticleSystem     Facade: spawn(config, x, y), spawnEffect(type, x, y)
│   ├── EmitterConfig      Data description of one preset
│   ├── EmitterLibrary     The preset catalogue
│   ├── EmitterInstance    A live emitter: rate, duration, attached light
│   ├── GpuParticlePool    Tier 1 ring-buffer slot allocation
│   ├── GpuParticleWriter  Tier 1 descriptor → vertex attribute encoding
│   ├── CpuParticlePool    Tier 2 pooled simulated particles
│   ├── CpuParticleSim     Tier 2 SDF collision, bounce, settle
│   ├── ParticleRenderer   Draws both tiers, batched by blend then texture
│   ├── ParticleAtlas      Single atlas for textured particles
│   └── ParticleStats      Counters for the debug overlay
│
├── lighting/
│   ├── LightSystem        Pooled lights, handles, budget culling
│   ├── Light              Light data record
│   ├── LightAnimation     Colour and radius curves over lifetime
│   ├── LightRenderer      Additive accumulation pass with SDF shadows
│   ├── VisibilitySystem   Vision cones: player, drone, camera
│   ├── VisibilityQuery    CPU-side isVisible() — must match the shader
│   └── SmokeVolumes       Smoke circles uploaded to the visibility shader
│
├── post/
│   ├── PostChain          Ordered pass list, tier-aware skipping
│   ├── CompositePass      scene × visibility + light, distortion-sampled
│   ├── DistortionBuffer   Shockwave and heat-haze offset accumulation
│   ├── BloomPass          Bright-pass plus separable blur
│   ├── BlindnessPass      Flash/stun overlay
│   └── FinalPass          Tonemap, vignette, grain, blit
│
├── decals/
│   ├── DecalSystem        Stamp requests and fade scheduling
│   ├── DecalTarget        Persistent world-space decal buffer
│   └── DecalType          Bullet hole, grenade scorch, rocket scorch
│
├── shapes/
│   ├── ShockwaveSystem    Expanding rings → distortion plus additive flash
│   ├── TrailSystem        Tracers as stretched GPU particles
│   └── AimConeRenderer    Procedural spread cone and guide dots
│
├── events/
│   ├── FxEventQueue       Lock-free enqueue from the network thread
│   ├── FxEvent            Effect descriptor (type, position, params)
│   └── FxEventType        Maps effect type → preset + light + decal
│
└── debug/
    ├── FxDebugOverlay     Counts, timings, budget state
    └── FxProfiler         GPU timer queries with CPU fallback
```

### 12.2 Shaders

```
assets/shaders/
├── lib/                   #include-only, never compiled standalone
│   ├── header.glsl        Precision qualifiers, version shim, tier defines
│   ├── sdf.glsl           sample, gradient, raymarch, softShadow
│   ├── noise.glsl         hash, value noise, curl noise
│   ├── color.glsl         Gradient ramp, packed-colour unpack, tonemap
│   └── camera.glsl        Camera-relative / screen / SDF-UV transforms
│
├── particle/
│   ├── particle_gpu.vert  Tier 1 analytic simulation from a time uniform
│   ├── particle_cpu.vert  Tier 2 pre-transformed passthrough
│   └── particle.frag      Procedural soft circle or atlas sample
│
├── light/
│   ├── light.vert         Light quad, camera-relative
│   ├── light_point.frag   Radial attenuation × SDF soft shadow
│   ├── visibility.vert
│   └── visibility_cone.frag   Distance + angular falloff × occlusion − smoke
│
├── post/
│   ├── fullscreen.vert    Shared by every post pass
│   ├── composite.frag     scene × visibility + light, distortion-sampled
│   ├── distortion.frag    Shockwave rings and heat haze
│   ├── bloom_bright.frag
│   ├── bloom_blur.frag    Separable, direction by uniform
│   ├── blindness.frag     Overlay, aberration, vignette, grain
│   └── final.frag         Tonemap and blit
│
├── decal/
│   ├── decal_stamp.frag   Write one decal into the persistent target
│   └── decal_apply.frag   Composite the decal target over terrain
│
├── shapes/
│   ├── trail.frag         Procedural gradient along a stretched quad
│   └── aimcone.frag       Analytic cone wedge and dotted guide line
│
└── debug/
    └── sdf_view.frag      Visualise the distance field
```

---

## 13. BUILD ORDER

Ten phases. Each ends with something runnable and a specific gate. **Do not start a phase until the previous gate passes** — the ordering exists so that foundational, high-risk work is proven before anything is built on top of it.

### Phase 0 — Infrastructure
`GlCaps`, `ShaderLibrary` with `#include`, `RenderTarget`, `FullscreenQuad`, `BlendState`, `FxBudget`, `FxClock`, `FxDebugOverlay`, `FxProfiler`, and the forced-`mediump` desktop debug mode.
**Gate:** a trivial shader loads from disk, hot-reloads on save, reports readable errors when broken, and the overlay shows frame timings.

### Phase 1 — SDF foundation
`SdfBaker` (rasterise plus 8SSEDT), `SdfField`, `SdfTexture`, `SdfCache`, `SdfDebugView`, plus the offline bake task.
**Gate:** a debug key renders the distance field over the map; it visibly matches the terrain, your thinnest geometry is cleanly resolved, and bake time is under 100 ms (or zero from the baked asset).

*Everything else depends on this. Do not move on until the field is right.*

### Phase 2 — Visibility and fog of war
`VisibilitySystem`, `VisibilityQuery`, `SmokeVolumes`, the cone shaders, and a minimal composite that multiplies the scene by visibility.
**Gate:** continuous falloff with no banding, hard terrain occlusion, a soft cone edge, and CPU `isVisible` agreeing with the shader at cone edges. Verify in forced-`mediump` mode.

*This is the highest-risk phase and the one that defines the game's look.*

### Phase 3 — Lighting
`LightSystem`, `LightAnimation`, `LightRenderer`, the light shaders, and additive accumulation into the light buffer.
**Gate:** multiple coloured lights with soft terrain shadows, correct animated colour decay, and graceful budget culling that never drops an explosion flash.

### Phase 4 — Pipeline and composite
`FxPipeline` with explicit pass ordering, `CompositePass`, `FinalPass`, `PostChain`.
**Gate:** `scene × visibility + light` composites correctly; emissive light is visible outside the vision cone but still blocked by walls.

### Phase 5 — GPU particles
`GpuParticlePool`, `GpuParticleWriter`, `ParticleRenderer`, the analytic vertex shader, and a few proof presets.
**Gate:** 3,000 simultaneous particles on desktop with no measurable per-frame CPU cost, and curl-noise turbulence that reads organically.

### Phase 6 — CPU particles
`CpuParticlePool`, `CpuParticleSim` against the SDF.
**Gate:** debris bounces and settles correctly on slopes, ledges and thin platforms, with normals derived from the field gradient.

### Phase 7 — Effect catalogue
`EmitterLibrary`, `FxEventQueue`, `FxEventType`, phased explosions, and the full preset list from §9.
**Gate:** every effect type the game can raise has been triggered from the network thread without touching GL state off the render thread.

### Phase 8 — Secondary effects
`DecalTarget`, `ShockwaveSystem` with distortion, heat haze, `TrailSystem`, `AimConeRenderer`, `BlindnessPass`, optional `BloomPass`.
**Gate:** decal count no longer affects frame time; shockwaves refract rather than glow.

### Phase 9 — Mobile hardening
Tier auto-detection, ES 2.0 verification with no extensions, profiling on real low-end hardware, budget tuning.
**Gate:** the low tier holds its frame target on a genuinely old device with fog, lights and particles all present.

---

## 14. RISKS

| Risk | Severity | Mitigation |
| --- | --- | --- |
| `mediump` precision artefacts on untested hardware | High | Camera-relative coordinates everywhere as a rule; forced-`mediump` desktop mode from Phase 0 |
| SDF under-resolves thin geometry | High | Size texels against your thinnest feature, not the average; validate visually in Phase 1 before anything depends on it |
| CPU/GPU visibility disagreement causes entity popping | Medium | One shared falloff definition, identical march parameters, and a debug overlay drawing the disagreement |
| Fill-rate blowout on mobile from too many passes | Medium | Half-res buffers from day one; a hard per-tier pass budget; profile before adding any new pass |
| Fog looks banded or cheap | Medium | Never assign flat brightness tiers to distance or angle bands; hot reload makes falloff tuning fast enough to get right |
| Stateless particles can't express a desired preset | Low | Tier 2 is the escape hatch for anything the analytic path can't do |
| GL calls from the network thread | Low | All effect requests go through the lock-free queue; the queue is the only entry point, enforced by making it the facade's sole spawn path |

---

## 15. DEFINITION OF DONE

- No third-party lighting library anywhere in the dependency graph.
- No physics engine referenced from any file under `fx/`.
- Every visual effect is a shader program loaded from `assets/shaders/`, with zero GLSL in source literals.
- The same distance field serves fog, lights, particle collision, spawn occlusion and gameplay line-of-sight, and they provably agree.
- The full effect catalogue in §9 is implemented and data-driven.
- Three quality tiers auto-detect and degrade gracefully, with no effect vanishing outside bloom and distortion.
- Effects cost ≤ 4 ms/frame on desktop and ≤ 8 ms on mid-tier mobile, verified by the profiler.
- Every shader has been validated in forced-`mediump` mode.
- Shader hot reload works on desktop; shader warm-up eliminates first-use hitching on mobile.