# Android compatibility, touch controls, UI/UX and mobile hardening — plan

**Status: plan only.** Nothing in this document is implemented. Nothing in it has been compiled,
run or observed: this sandbox has no JDK, no Gradle and no Android SDK, so every slice below is to
be written and not executed, with the first real run in CI and on the user's desktop and Android
builds. Every acceptance box in §10 is unchecked.

This plan covers Phases 10–11's mobile and platform items from `docs/IMPLEMENTATION_ROADMAP.md`
(`core/platform/`, `TouchControls`, `QuickChat`, responsive phone and tablet HUD layouts, and
mobile shader/lifecycle hardening), plus the Phase 9 mobile polish items from
`docs/CONSOLE_CHAT_PLAN.md` (§9) and `docs/EFFECTS_PLAN.md` (§3, §11, §13 Phase 9).

---

## 1. Baseline audit (checked against `62950d3` on `origin/main`)

Reading the current tree across `android`, `core`, `lwjgl3`, `shared`, `assets/shaders` and
`tools/scratch` surfaced ten concrete gaps that the slices in §8 resolve in order:

1. **`android/AndroidManifest.xml` is missing `android.permission.INTERNET`.** KryoNet opens TCP
   (`NetConfig.DEFAULT_TCP_PORT = 54555`) and UDP (`54777`) sockets. Without
   `<uses-permission android:name="android.permission.INTERNET" />`, every connection attempt on
   Android fails immediately with a socket security exception.
2. **`android/AndroidManifest.xml` fixes `android:screenOrientation="landscape"` and declares no
   cutout policy.** A player cannot flip the phone 180° (`sensorLandscape`) to move a charging
   cable or camera hole-punch away from their grip, and no `layoutInDisplayCutoutMode` or safe-area
   inset query exists to keep HUD widgets and thumb sticks out of display cutouts and system
   gesture bars.
3. **`android/proguard-rules.pro` has no Kryo or packet rules.** `android/build.gradle` enables
   minification on `release` (`minifyEnabled true`), and Kryo's `FieldSerializer` reflects over
   the fields and no-arg constructors of `io.github.skystrike.shared.net.**`,
   `io.github.skystrike.shared.model.**`, `io.github.skystrike.shared.map.**`,
   `io.github.skystrike.shared.text.**` and `io.github.skystrike.shared.effect.**`. Without
   `-keep` rules for those packages and `com.esotericsoftware.kryo.**` /
   `com.esotericsoftware.kryonet.**`, a release APK strips or renames wire fields. The file also
   retains dead template rules for Box2D (`com.badlogic.gdx.physics.box2d.World`).
4. **`core/platform/` does not exist.** Neither `lwjgl3` nor `android` has a `platform/` package.
   `core` currently branches directly on `Gdx.app.getType() != Application.ApplicationType.Desktop`
   in two places: `GameScreen.pointerPosition()` (`GameScreen.java:1118`) and
   `HudStage.applyPointerCursor(boolean)` (`HudStage.java:277`).
5. **`InputSampler` is keyboard/mouse-only and single-pointer.** `InputSampler.sample` reads
   `KeyBindings` booleans (`moveLeft`, `moveRight`, `jump`, `crouch`, `jetpack`, `ads`, `fire`)
   and unprojects `Gdx.input.getX(), Gdx.input.getY()` (always pointer `0`) to compute `aimAngle`.
   On a touch screen, placing a left thumb on a movement control as pointer `0` immediately snaps
   `aimAngle` toward the bottom-left corner, and lifting the finger leaves `aimAngle` pointing at
   the last touched button.
6. **The crosshair on touch is pinned to the centre of the screen.** When `HudFrame.pointer()` is
   `null` (every non-desktop frame today), `Crosshair` draws at `(screenWidth / 2f, screenHeight / 2f)`
   — directly over the local player's body — instead of along `localPlayer.aimAngle`. Because aim
   is 360° and independent of body rotation, a centre-locked reticle on touch does not show where
   the next round will go.
7. **Existing overlays and screens have touch dead-ends.**
   - `LoadoutPicker` (`core/ui/hud/LoadoutPicker.java`) overrides `keyDown`, `keyUp`, `keyTyped`
     and `scrolled`, but **does not override `touchDown`**. Opening `LoadoutScreen` from the main
     menu or opening the in-match picker on a touch device traps the player with no way to choose a
     category, pick a weapon, apply, or close.
   - `ConnectingScreen` draws plain text at fixed coordinates `(80, 420 / 360 / 300)` without
     updating its batch projection on `resize`, and its `touchDown` checks hardcoded `y` bands
     (`270..325`, `215..270`) with no visible Retry or Cancel buttons.
   - System Back (`Input.Keys.BACK`) is never caught (`Gdx.input.setCatchKey(Input.Keys.BACK, true)`)
     and is not routed alongside `Input.Keys.ESCAPE` in `GameScreen`, `PauseOverlay`,
     `GameSettingsDialog`, `ConsoleDialog`, `LoadoutPicker`, `MainMenuScreen`, `SettingsScreen`,
     `ConnectingScreen` or `MapEditorScreen`.
   - `ConsoleDialog` has no on-screen opener button, never raises or dismisses the soft keyboard,
     does not offset its bottom-anchored panel above the soft keyboard (so the IME covers the input
     bar), does not scroll on `touchDragged`, and has no `QuickChat` wheel.
8. **GLSL ES 2.0 `mediump` overflow and underflow in `assets/shaders/`.** `assets/shaders/lib/header.glsl`
   declares `precision mediump float;` under `GL_ES`. On mobile GPUs where `mediump` is IEEE-754
   half-precision (max `65504.0`, min normal `6.1e-5`):
   - `visibility.vert` passes `varying vec2 v_worldPos` in absolute world coordinates (`0..3000`),
     and `visibility_cone.frag` computes `float segLenSq = dot(seg, seg);` — at hip reach `640`
     (`segLenSq = 409,600`) or ADS reach `1024` (`segLenSq = 1,048,576`), `segLenSq` overflows fp16
     to `+inf`.
   - `light_radial.frag` and `sdf.glsl` evaluate `length(v_worldPos - u_lightPos)` and
     `length(target - origin)` in `mediump`, where the squared distance inside `length()` overflows
     fp16 whenever a ray exceeds `255.9` world units (`256² = 65536 > 65504`).
   - `door_occluders.glsl` tests `abs(d.x) < 1e-6`, which underflows to `0.0` in fp16.
   - `blindness.frag` and `noise.glsl` evaluate `hash21(v_texCoords * vec2(1920.0, 1080.0) + ...)`
     where `dot(p, p + 45.32)` reaches `~4.8e6`, overflowing fp16 to `+inf`.
9. **Module and test boundaries.** Tests exist only in `shared/src/test` and `server/src/test` (CI
   runs `shared:test` and `server:test`, and compiles `shared`, `server`, `core`, `tools`;
   `static_api_check.py` scans `shared`, `server`, `core`, `tools`, while `static_check.py` scans
   all six modules including `lwjgl3` and `android`). Therefore all touch-stick maths, multi-touch
   pointer arbitration, button hit-testing, responsive HUD layout calculations and quality-tier
   selection rules belong in `shared` (`shared/input/`, `shared/hud/`) as pure Java records and
   functions covered by `shared/src/test`.
10. **Wire protocol.** `NetConfig.PROTOCOL_VERSION` is **18** and stays **18** across every slice
    in this plan. `PacketPlayerInput` already carries continuous `float moveX` (`[-1, 1]`),
    `float aimAngle` (`[-180, 180]`), `jump`, `crouch`, `jetpack`, `ads`, `fire`, `slotPress`,
    `gadgetPress`, `viewAction` and `usePress` with their birth sequences.

---

## 2. Platform abstraction (`core/platform/`)

Per `docs/PROJECT_STRUCTURE.md` §§6–7 and `docs/IMPLEMENTATION_ROADMAP.md` Phase 10, `core`
declares narrow platform interfaces in `core/src/main/java/io/github/skystrike/platform/` and
never branches on `Application.ApplicationType` or imports `android.*` or `lwjgl3.*`.

### 2.1 Interfaces in `core/platform/`

```
core/src/main/java/io/github/skystrike/platform/
├── PlatformServices      Immutable aggregate passed into Main (with a safe default for tests)
├── DeviceInfo            Form factor, density, physical size, cursor availability, safe insets, tier hint
├── SafeAreaInsets        Left, right, top, bottom insets in physical pixels (cutouts, gesture bars)
├── KeyboardProvider      Raise/dismiss soft keyboard, query visible state and bottom IME inset
├── StorageProvider       Preferences and local/external file resolution
└── AuthProvider          Stub/hook aligned with docs/ACCOUNTS_PLAN.md (token load/save/clear)
```

| Interface | Methods and contract | Desktop (`lwjgl3/platform/`) | Android (`android/platform/`) |
| --- | --- | --- | --- |
| `DeviceInfo` | `FormFactor formFactor()` (`DESKTOP`, `PHONE`, `TABLET`), `boolean hasHardwareCursor()`, `boolean isTouchPrimary()`, `float density()` (logical dp scale, `Gdx.graphics.getDensity()`), `float ppiX()`, `float ppiY()`, `SafeAreaInsets safeInsets()`, `FxBudget.Tier recommendedFxTier()` | `DESKTOP`, `hasHardwareCursor = true`, `isTouchPrimary = false`, `SafeAreaInsets.ZERO`, `FxBudget.Tier.HIGH` | Classifies `PHONE` vs `TABLET` by shortest-width dp (`sw >= 600 dp` → `TABLET`, else `PHONE`), `hasHardwareCursor = false`, `isTouchPrimary = true`, reads `WindowInsets` display cutout + system bars into `SafeAreaInsets`, recommends `MID` (or `LOW` on low-RAM / older GLES2 drivers) |
| `KeyboardProvider` | `void show(boolean visible)`, `boolean isVisible()`, `int bottomInsetPixels()` | Delegates to `Gdx.input.setOnscreenKeyboardVisible(visible)`; `isVisible() = false`; `bottomInsetPixels() = 0` | Calls `Gdx.input.setOnscreenKeyboardVisible(visible)` and tracks actual IME height via `View.OnApplyWindowInsetsListener` / global layout frame so `ConsoleDialog` shifts above the keyboard |
| `StorageProvider` | `Preferences preferences(String name)`, `FileHandle userMapsDir()` | `Gdx.app.getPreferences(name)`, `Gdx.files.external(".skystrike/maps/")` with `local("maps/")` fallback | `Gdx.app.getPreferences(name)`, app-specific external/local storage without requiring broad storage permissions |
| `AuthProvider` | `Optional<String> loadRefreshToken()`, `void saveRefreshToken(String token)`, `void clearRefreshToken()` | File/preferences stub ready for `ACCOUNTS_PLAN.md` A4 | `SharedPreferences` (upgradeable to encrypted storage in `ACCOUNTS_PLAN.md` A4) |

### 2.2 Removing direct platform checks in `core`

- `Main` accepts `PlatformServices` (with `Main()` and `Main(LocalHost)` constructing
  `PlatformServices.defaultServices()` so existing callers and tests stay source-compatible).
- `GameScreen.pointerPosition()` and `HudStage.applyPointerCursor(boolean)` ask
  `platform.deviceInfo().hasHardwareCursor()` instead of `Gdx.app.getType() == ApplicationType.Desktop`.
- `static_check.py` gains a rule forbidding `ApplicationType` in `core/src/` outside `platform/`
  default fallback construction, enforcing the roadmap rule that "`core` never branches on
  platform."

---

## 3. Touch controls plan (movement, aim, fire, utilities, gadgets, surveillance)

All pure control geometry and state transitions live in `shared/src/main/java/io/github/skystrike/shared/input/`
(`TouchConfig`, `TouchZone`, `VirtualStickState`, `TouchControlModel`, `TouchAction`) so unit tests
in `shared/src/test` verify every edge and deadzone without LibGDX. `core/input/TouchControls`
adapts `InputProcessor` touch events (`touchDown`, `touchDragged`, `touchUp`, `touchCancelled`) into
`TouchControlModel` and feeds `InputSampler`, `LoadoutController`, `SurveillanceController` and
`DoorUseController`.

### 3.1 Multi-touch pointer routing and focus discipline

1. **Modal focus zeroes touch immediately.** Whenever `InputRouter.hasModalFocus()` is true
   (`ConsoleDialog`, `LoadoutPicker`, `PauseOverlay`, `GameSettingsDialog`), `TouchControls.reset()`
   clears all active pointer bindings, hides the in-world virtual stick rings, and produces zeroed
   movement and fire intent (`CONSOLE_CHAT_PLAN.md` §6.4).
2. **Per-pointer ownership (`pointer` IDs `0..9`).** Each `touchDown(screenX, screenY, pointer, button)`
   is converted to HUD y-up coordinates and matched once in priority order:
   - **Priority 1 — Discrete HUD buttons** (Pause, Chat, Quick Chat, Loadout Picker, Main Slots
     1–5, Gadget Slots Q/E, contextual Use `F`, View Cycle `6` / Exit View, ADS, Fire, Jump,
     Jetpack, Crouch).
   - **Priority 2 — Left movement stick zone** (left half of screen below the top HUD bar, outside
     button rects). Claims `movePointer = pointer`.
   - **Priority 3 — Right aim zone** (right half of screen below the top HUD bar, outside button
     rects, or initiated on the Aim/Fire stick). Claims `aimPointer = pointer`.
3. **Drag isolation.** `touchDragged(screenX, screenY, pointer)` updates *only* the control owned by
   `pointer`. Dragging the left thumb across the midline never hijacks `aimAngle`; tapping a slot
   or gadget button with a third finger never perturbs either stick; releasing `movePointer` leaves
   `aimPointer` untouched.

### 3.2 Movement, Jump, Jetpack, Crouch and One-Way Platform Drop-Through

| Action | Desktop input | Touch control (default, Decision AN1) | Wire field on `PacketPlayerInput` |
| --- | --- | --- | --- |
| Move left / right | `A` / `D` (`±1.0`) | **Left virtual thumbstick** horizontal axis `dx / maxRadius`, radial deadzone `0.12`, continuous analogue `[-1.0, 1.0]` | `float moveX` (`PlayerMotion` and `DroneMotion` already clamp `[-1, 1]`) |
| Jump | `W` / `Up` | **Jump button** (`JUMP`, right thumb arc, lower-inner) | `boolean jump` |
| Jetpack | `Space` | **Jetpack button** (`JET` — hold to thrust; right thumb arc, upper-inner) + optional double-tap/slide-up from Jump | `boolean jetpack` |
| Crouch / Platform drop | `S` / `Down` / `Ctrl` | **Crouch button** (`CROUCH`, bottom-left beside stick or lower-right arc). On solid ground: toggles crouch stance (`height 50 → 30`, `CROUCH_SPEED`). On a one-way platform (`MovementRule.ONE_WAY_TOP`): emits a fresh crouch press edge, triggering `PlayerMotion`'s `dropThroughTimer` | `boolean crouch` |
| Piloting a Drone (`SurveillanceView.DRONE`) | `A`/`D` + `W`/`Space` (up) + `S` (down) | **Left virtual thumbstick becomes a 2D flight stick automatically:** horizontal axis → `moveX`, vertical axis `stickY > +0.20` → `jump = true` (`up` in `DroneMotion`), `stickY < -0.20` → `crouch = true` (`down` in `DroneMotion`) | `moveX`, `jump`, `crouch` |

**Why on-foot vertical actions use dedicated buttons by default (Decision AN1):**
In `PlayerMotion.stepInPlace`, a fresh `crouch` edge while standing on a `MovementRule.ONE_WAY_TOP`
slab immediately starts `dropThroughTimer = PlayerConfig.PLATFORM_DROP_THROUGH_SECONDS` and drops
the player through the floor, while `jetpack` burns fuel at `20/s` from a 5-second tank. If a
single 2D left stick mapped slight downward deflection to `crouch` and upward deflection to
`jetpack`, a player strafing across the catwalk would drop through it on any diagonal thumb drift
and waste jetpack fuel on every jump. Dedicated Jump, Jetpack and Crouch buttons keep ground feel
crisp and air commitment deliberate (`MECHANICS_PLAN.md` §11), while piloting a drone switches the
left stick into full 360° 2D flight where `DroneMotion` has no gravity or drop-through slabs.

### 3.3 Aim, Fire, ADS and Reload (Decision AN2 & AN3)

SkyStrike's combat requires 360° aim independent of body rotation (`PlayerMotion` aim-lean torque),
smooth ADS camera pan (`150 u` along `aimAngle`, `VisionConfig.REACH_ADS = 1024`), five fire modes
(auto, semi, bolt/pump/break, 3-round burst, shotgun), and predictive throw arcs.

Three right-thumb schemes are defined in `TouchControlModel` (Decision **AN2** chooses whether to
ship Scheme A only or Scheme A + B as a user setting):

1. **Scheme A (Recommended default) — Right Aim Stick + Dedicated Fire & ADS buttons, with optional
   outer-ring auto-fire:**
   - Touching and dragging the right virtual stick computes `aimAngle = Angles.ofVector(dx, dy)`
     whenever radial deflection exceeds `TouchConfig.AIM_DEADZONE = 0.10f`.
   - **Angle retention on release:** when `aimPointer` is lifted (or inside the deadzone),
     `TouchControlModel` **retains the last valid `aimAngle`** (initialised from spawn facing `0°`
     for Team A / `180°` for Team B). Releasing the stick never snaps aim to `0°` or to a button's
     pixel coordinate.
   - **Dedicated Fire button (`FIRE`):** hold for automatic fire (`fire = true`), tap for semi /
     bolt / pump / burst / melee swing / utility throw. Remember: `LoadoutSystem` on the server
     derives trigger press edges by comparing successive packets, and `TouchControlModel` latches a
     tap that starts and ends within a single frame so a fast tap between ticks still sends one
     `fire = true` sample.
   - **Outer-ring Fire (optional setting, on by default in Scheme A for guns):** pushing the right
     aim stick beyond `0.85` of its radius (`FIRE_RING_THRESHOLD`) also asserts `fire = true`,
     allowing one-thumb aim-and-shoot while keeping the dedicated `FIRE` button for stationary
     tapping or claw grip.
   - **ADS button (`ADS`, Decision AN3):** defaults to **toggle** on touch (tap once to enter ADS,
     tap again to return to hip-fire; configurable to **hold** in Settings). Entering ADS interpolates
     `adsAlpha` at `VisionConfig.ADS_TRANSITION_RATE`, narrows spread (`CrosshairMath.ADS_GAP_MULTIPLIER`),
     extends sight to `1024 u`, and pans the camera `150 u` along `aimAngle`.
2. **Scheme B — Twin-Stick Aim-and-Fire (flick / release-to-fire for single-shot & throwables):**
   - Deflecting the right stick beyond `0.10` aims; beyond `0.35` fires automatic weapons
     continuously. When holding a semi/bolt gun or a throwable utility (Slots 4/5), deflecting the
     right stick aims and previews the shot/arc, and **releasing** the stick outside the inner
     cancel radius (`0.20`) fires one round or throws the utility; sliding the thumb back inside
     `0.20` before release cancels without firing.
3. **Scheme C — Relative Touchpad / Virtual Cursor:**
   - Dragging anywhere in the right zone moves a screen-space virtual cursor `(cursorX, cursorY)` by
     `delta * touchSensitivity`, clamped to the viewport, and `InputSampler` unprojects that
     virtual cursor exactly like the desktop mouse.

**Reload on touch (no new wire field):**
In `server/weapons/LoadoutSystem.java` (lines 400–433), reload starts automatically when a magazine
hits zero (`!item.hasRounds()`) or when `fire` is pressed on an empty magazine (for example after
switching away mid-reload and switching back). There is no manual `R` reload key in `KeyBindings`
or `PacketPlayerInput`. On touch, tapping `FIRE` (or tapping the active gun slot when dry) sends
the same trigger sample and starts the reload, while `LoadoutBar` draws the amber reload sweep.

### 3.4 Touch Crosshair Projection

Today `GameScreen.pointerPosition()` returns `null` off desktop, which makes `Crosshair` draw at
`(screenWidth / 2f, screenHeight / 2f)` — over the player's torso. On touch:

- `CrosshairMath` gains a pure helper
  `touchReticleWorldPoint(float anchorX, float anchorY, float aimAngleDeg, float lookDistance)`
  where `lookDistance` blends between `TouchConfig.RETICLE_DISTANCE_HIP` (`220 u`) and
  `TouchConfig.RETICLE_DISTANCE_ADS` (`340 u`) by `adsAlpha`.
- `GameScreen` projects that world point through the frame's `WorldProjection` into a
  `PointerPosition(screenX, screenY)` (clamped inside the screen margin) whenever
  `!platform.deviceInfo().hasHardwareCursor()`.
- `Crosshair` then draws at `(screenX, screenY)` with the exact `CrosshairMath.gapPixels(player, adsAlpha)`
  spread/kick bloom and hit-marker X, while `HudStage.applyPointerCursor` still skips
  `Gdx.graphics.setSystemCursor` because `hasHardwareCursor()` is `false`.

### 3.5 Utilities (Throwables — Slots 4 and 5)

- **Equipping:** Tapping Slot 4 (`Utility A`) or Slot 5 (`Utility B`) on the bottom-center
  `LoadoutBar` selects that utility slot via `LoadoutController` (`slotPress = 4` or `5` with its
  birth sequence). Empty utility slots (`count == 0`) are ignored, matching `PlayerLoadout.tapSlot`.
- **Aiming and trajectory preview:** While a utility slot is active in the predicted `PlayerLoadout`,
  `TrajectoryRenderer.render(camera, localPlayer, liveMap())` (`GameScreen.java:937`) already draws
  the predictive bounce arc along `localPlayer.aimAngle` using `ThrowablePhysics`. Aiming with the
  right thumbstick updates `localPlayer.aimAngle` every frame, sweeping the trajectory arc across
  walls, ramps and bank-shot corners in real time.
- **Throwing:** Pressing `FIRE` (or releasing the right stick outside the cancel ring in Scheme B)
  emits `fire = true`, spawning the throwable along the previewed arc and decrementing the slot
  count.

### 3.6 Gadgets (Q and E), Doors (`F`), and Surveillance (`6` / `Esc`)

- **Gadgets Q and E:** The two gadget boxes on `LoadoutBar` (`Q` and `E`) are direct touch buttons
  (`>= 48 dp × 48 dp`). Tapping `Q` or `E` calls `LoadoutController`, which runs the shared
  `GadgetPress.resolve` prediction and stamps `PacketPlayerInput.GADGET_Q_PRESS` or `GADGET_E_PRESS`
  with its birth sequence:
  - **Drone (manual):** first tap deploys the drone above the player (`DRONE_DEPLOYED`); second tap
    enters drone pilot view (`DRONE_PILOT`).
  - **Throw Camera (manual):** first tap throws the camera along `aimAngle` (`CAMERA_THROWN`); once
    stuck (`cameraReady`), tapping again enters the `1.2×` zoomed camera view (`CAMERA_VIEW`).
  - **Shield (hybrid):** each tap toggles between frontal `90°` arc protection (handgun-only) and
    rear back-mounted protection.
  - **Fuel Tank (passive):** displays `WORN` (or `BROKEN`); tapping is a safe no-op.
- **Contextual Door Use button (`USE` / `F`):**
  - Each frame `GameScreen` evaluates `MapQueries.doorAt(arena, localPlayer.centerX(), localPlayer.centerY(), DoorConfig.USE_REACH)`
    — the exact shared query `DoorUseController` already uses.
  - When a door is within `DoorConfig.USE_REACH` (`90 u`) and the player is alive, not stunned, and
    not surveillance-locked, a prominent **USE DOOR** touch button activates above the right action
    arc (showing the door state: `OPEN` / `CLOSE`). Tapping it triggers `DoorUseController`,
    predicting the swing locally and stamping `PacketPlayerInput.USE_PRESS` with its birth sequence.
- **Surveillance View Controls (`SurveillanceView != SELF`):**
  - Whenever `localPlayer.isSurveillanceLocked()` is true (`DRONE` or `CAMERA`) or the player owns a
    live drone or stuck camera (`SurveillanceController.OwnedDevices`), a **CYCLE VIEW (`6`)**
    button is shown beside the gadget slots.
  - While locked in `DRONE` or `CAMERA` view:
    - `SurveillanceBanner` at top-center shows the device name, `HP`, and touch control hint
      (`"LEFT STICK steer  CYCLE switch  EXIT return"` on touch).
    - A high-contrast **EXIT VIEW** button appears next to the right thumb (and the Android hardware
      / gesture **Back** button also calls `surveillance.exitSurveillance(localPlayer)` before
      opening `PauseOverlay`, preserving the single-owner Escape rule of `GameScreen.java:740`).

---

## 4. HUD and screen layouts for phones and tablets

### 4.1 Why the desktop corner layout cannot be used verbatim on touch

On desktop (`HudStage.layout`, `HudStage.java:290`), `HealthFuelBars` and the passive chat strip sit
in the **bottom-left corner**, and `LoadoutBar` (`457` design px wide) plus `GadgetPanel` sit in the
**bottom-right corner**. On a phone or tablet held in landscape, the player's left and right thumbs
physically occupy those two bottom corners. Leaving `HealthFuelBars` under the left thumb hides low
health and fuel warnings; leaving `LoadoutBar` in the bottom-right corner puts all seven weapon/gadget
slots directly underneath the right aim stick and fire buttons.

### 4.2 Sizing and legibility rules (`shared/hud/TouchHudLayout`)

1. **Physical touch-target floor (`48 dp`).** Every interactive button on the HUD and in menus has a
   minimum hit box of `48 dp × 48 dp` (`48 * density` physical pixels, roughly `7.5–9 mm`), with at
   least `6 dp` gap between adjacent targets.
2. **Font floor (`12 px` physical, `2.2%` viewport height).** `FontManager` already enforces
   `MIN_PHYSICAL_PIXELS = 12` and `MAX_PHYSICAL_PIXELS = 28` via
   `FontManager.targetPhysicalPixels(viewportHeight, density)`. All HUD and console text continues
   to pass through `HudTheme.text(Color)` (`ConsoleTheme.clampedText`) on `HudTheme.panelFill()` so
   the 4-layer contrast guarantee (`CONSOLE_CHAT_PLAN.md` §5.2) holds on every mobile screen.
3. **One batch, two passes preserved.** `TouchControls` does **not** allocate its own `SpriteBatch`
   or `ShapeRenderer`. Instead, `HudStage.render(HudFrame)` draws the virtual stick rings and
   action button plates during its single `ShapeRenderer` pass and draws the button captions during
   its single `SpriteBatch` pass. Draw-call count and state switches stay minimal on tile-based
   mobile GPUs.
4. **Safe-area insets (`SafeAreaInsets`).** Every outer margin in `TouchHudLayout` adds
   `safeInsets.left()`, `safeInsets.right()`, `safeInsets.top()` and `safeInsets.bottom()` so no
   stick, slot box, minimap or kill-feed line is clipped by a camera hole-punch, notch or Android
   gesture navigation bar.

### 4.3 Phone layout (`FormFactor.PHONE` — shortest width `< 600 dp`, typically `19.5:9` to `20:9`)

On a landscape phone (for example `2400 × 1080` px at `3.0×` density = `800 × 360 dp`), vertical
space is tight (`360 dp`) and horizontal space is wide (`800 dp`):

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│ [Minimap 160×106dp]      [HP / FUEL Vitals Bar]      [Chat][Quick][L][||] [Kills]│
│ [Passive chat lines]     [Surveillance Banner / Use]                             │
│                                                                                  │
│                                     ( + )                                        │
│                                 Crosshair along                                  │
│                                   aimAngle                                       │
│                                                                      [JET] [ADS] │
│     (  Left Stick  )                                           [USE] [FIRE]      │
│     (  Move / Fly  )                                           [JUMP]   ( Right )│
│  [CROUCH]             [1][2][3][4][5]  [Q][E]  [VIEW]                   (  Aim  )│
└──────────────────────────────────────────────────────────────────────────────────┘
  ◄── safeLeft                                                          safeRight ──►
```

- **Top-left:** `Minimap` (compact `160 × 106 dp` 3:2 frame) with passive chat lines (`3` lines max
  on phone) stacked directly below it, clear of the left thumb stick.
- **Top-center:** `HealthFuelBars` (`180 dp` wide) anchored along the top edge, with
  `SurveillanceBanner` directly below it when piloting a drone or viewing a camera. Both thumbs
  never obscure HP or fuel.
- **Top-right:** compact utility bar (`[Chat]`, `[Quick Chat]`, `[Loadout]`, `[Pause ||]`, each
  `48 × 40 dp` with `48 × 48 dp` hit area) to the left of `KillFeedWidget`.
- **Bottom-left:** Left movement thumbstick zone (`x: safeLeft .. 30% width`, `y: safeBottom .. 55% height`),
  with the `[CROUCH]` button tucked at the inner-left edge next to the bottom dock.
- **Bottom-center dock:** `LoadoutBar` (`Slots 1–5` + `Q` + `E`, each slot `52 × 48 dp` on phone so
  all 7 boxes span `~394 dp`, fitting cleanly between the two `200 dp` thumb zones on an `800 dp`-wide
  phone). `GadgetPanel` renders as compact durability/cooldown strips directly above `Q` and `E`.
- **Bottom-right:** Right Aim Stick (`radius 64 dp`) anchored in the lower-right thumb zone,
  surrounded by an ergonomic four-button arc (`[FIRE]` primary `64 dp`, `[JUMP]` `52 dp`,
  `[JET]` `52 dp`, `[ADS]` `48 dp`, plus contextual `[USE]`/`[EXIT VIEW]` above the arc).

### 4.4 Tablet layout (`FormFactor.TABLET` — shortest width `>= 600 dp`, typically `16:10` or `4:3`)

On a landscape tablet (for example `2560 × 1600` px at `2.0×` density = `1280 × 800 dp`), the screen
is physically tall (`800 dp`), so placing controls at percentages of screen height would float them
out of thumb reach. `TouchHudLayout` therefore clamps thumb-arc centres in **physical `dp` offsets**
from the bottom-left and bottom-right safe corners (`centerX = safeInset + 130 dp`,
`centerY = safeBottom + 125 dp`):

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│ [Minimap 210×140dp]        [Surveillance Banner]        [Chat][Quick][L][||]     │
│ [DebugPanel if F1]                                              [KillFeedWidget] │
│ [Passive chat strip (up to 6 lines)]                                             │
│                                                                                  │
│                                      ( + )                                       │
│                                                                                  │
│                                                                                  │
│  [HP / FUEL Vitals Bar]                                            [JET]  [ADS]  │
│     (  Left Stick  )                   [GadgetPanel]         [USE] [FIRE]        │
│     (  Move / Fly  )        [1] [2] [3] [4] [5]   [Q] [E]    [JUMP]    ( Right ) │
│  [CROUCH]                         (64×52dp slots)                      (  Aim  ) │
└──────────────────────────────────────────────────────────────────────────────────┘
```

- **Top-left:** full-size `Minimap` (`210 × 140 dp`), `DebugPanel` below it when enabled, and the
  passive chat strip below that (above the left thumb's upper reach).
- **Bottom-left (above stick):** `HealthFuelBars` sits above the left thumb zone (`y = safeBottom + 250 dp`),
  or at top-center if the user selects the phone HUD preset.
- **Bottom-center:** full-size `LoadoutBar` (`64 × 52 dp` slots with weapon names, ammo counts and
  magazine bars) plus the full two-line `GadgetPanel` stacked above `Q` and `E`.
- **Bottom-right:** Right Aim Stick + `[FIRE]`, `[ADS]`, `[JUMP]`, `[JET]`, `[CROUCH]` and
  contextual `[USE]` / `[VIEW]` pinned within comfortable corner thumb reach (`110–240 dp` from the
  bottom-right corner).

### 4.5 Menus, Picker, Chat and Console on Touch

| Screen / Overlay | Touch & mobile changes |
| --- | --- |
| `LoadoutPicker` (`LoadoutScreen` & in-game `L`) | Override `touchDown`, `touchDragged` and `touchUp`: tapping a category on the left rail selects that category (`model.setCategory`); tapping an option row moves the cursor and selects it; dragging the option list scrolls `firstVisibleRow`; add explicit touch **Apply** and **Close** buttons in the footer (`>= 48 dp` tall) so a touch player never needs `Enter` or `Escape`. |
| `ConnectingScreen` | Replace hardcoded `y`-band点击 checks with a responsive layout (updated on `resize`) and two explicit **Retry** and **Cancel** touch buttons (`>= 48 dp` tall), plus Android `Input.Keys.BACK` → `cancel()`. |
| `MainMenuScreen`, `PauseOverlay`, `SettingsScreen`, `GameSettingsDialog` | Scale Scene2D `ScreenViewport` `unitsPerPixel` by `1f / ConsoleTheme.scale(height)` so buttons, labels and scroll panes meet the `48 dp` height floor on high-DPI phones. On `isTouchPrimary()`, `SettingsScreen` and `GameSettingsDialog` hide desktop-only `Resolution` and `Fullscreen` toggles and expose **Touch Controls** settings (Aim scheme, ADS toggle/hold, Left stick floating/fixed, Button scale/opacity, Left-handed flip). |
| `ConsoleDialog` | Add on-screen `[Chat]` HUD button to open; call `keyboardProvider.show(true)` on `open()` and `show(false)` on `close()`; offset the panel's `bottomY` by `keyboardProvider.bottomInsetPixels()` (and clamp height to the remaining top viewport above the IME so the input bar and completion rows are never covered by the soft keyboard); add touch `[Send]`, `[Close]`, and `[▲]`/`[▼]` history buttons to the input bar; support `touchDragged` vertical scrolling in the scrollback area. |
| `QuickChat` (`core/chat/QuickChat.java` + `shared/text/QuickChatPreset.java`) | Tapping `[Quick Chat]` on the HUD opens a compact 8-phrase radial/grid overlay (`"Enemy spotted"`, `"Need backup"`, `"Pushing"`, `"Holding"`, `"Rotating"`, `"Grenade out"`, `"Drone up"`, `"Clear"`). Tapping a phrase sends it through `ChatClient` using the current `ChatTarget` (`ALL` or `TEAM`, with a target toggle button right on the overlay) and closes immediately without raising the soft keyboard (`CONSOLE_CHAT_PLAN.md` §9). |
| System Back (`Input.Keys.BACK`) | Call `Gdx.input.setCatchKey(Input.Keys.BACK, true)` at startup. Route `Input.Keys.BACK` identically to `Input.Keys.ESCAPE` everywhere: closes `ConsoleDialog`, `LoadoutPicker`, `QuickChat`, `GameSettingsDialog` or `PauseOverlay` when open; exits surveillance view when piloting in `GameScreen`; opens `PauseOverlay` in normal play; returns to `MainMenuScreen` from child screens. |

---

## 5. Mobile hardening (Shaders, Effects budget, Lifecycle, Networking, R8)

### 5.1 GLSL ES 2.0 `mediump` fixes (`assets/shaders/`)

Following `docs/EFFECTS_PLAN.md` §3.1, mobile fragment shaders must not overflow or underflow when
`mediump` is 16-bit half-float (`±65504`, min normal `6.1e-5`):

1. **`assets/shaders/lib/header.glsl`:** Guard fragment `highp` with `#ifdef GL_FRAGMENT_PRECISION_HIGH`
   so devices supporting `highp` in fragment shaders (almost all modern Android GPUs) use it for
   world-space distance maths, while also fixing the arithmetic so pure `mediump` GPUs and the
   desktop forced-`mediump` test mode never overflow.
2. **`visibility_cone.frag` and `light_radial.frag`:** Compute distances from normalized or scaled
   vectors (`vec2 delta = (p1 - p0) / u_reach` or camera-relative coordinates scaled by `1.0 / 1024.0`)
   before calling `dot(delta, delta)` or `length()`, keeping all squared intermediates within
   `[0.0, 10.0]` well inside fp16's `65504.0` ceiling.
3. **`sdf.glsl` (`sdfSoftShadow`):** Normalize `dir` via `max(abs(dir.x), abs(dir.y))` pre-scaling
   before `length(dir)` so a 3000-unit ray never squares `3000.0` (`9,000,000 > 65504`) in `mediump`.
4. **`door_occluders.glsl`:** Raise the axis-parallel slab epsilon from `1e-6` (underflows to `0.0`
   in fp16) to `1e-3` (in world units over a `0..3000` arena, `< 0.001` world units is still
   effectively zero).
5. **`blindness.frag` and `noise.glsl` (`hash21`):** Wrap inputs with `mod(p, 256.0)` before
   `dot(p, p + 45.32)` so `v_texCoords * vec2(1920.0, 1080.0)` never produces a `~4.8e6` dot
   product in `mediump`.

### 5.2 Quality tiers and tile-GPU discipline (`FxBudget` & `FxPipeline`)

- At startup, `Main` seeds `settings.qualityTier` on first launch from
  `platform.deviceInfo().recommendedFxTier()` (`MID` for standard Android devices, `LOW` for low-memory
  devices, `HIGH` for desktop) when no saved preference exists yet.
- `FxPipeline` already renders `visibilitySystem` and `lightPass` at half resolution and clears
  `sceneFbo` at the start of `beginScene()`. On `FxBudget.Tier.LOW`, SDF sphere-trace steps drop to
  `12` (`24` on `MID`, `48` on `HIGH`, per `EFFECTS_PLAN.md` §4.4 & §11).

### 5.3 Android lifecycle, audio and networking

- **Pause / Resume (`ApplicationListener.pause()` and `resume()`):**
  - When the Android Activity is backgrounded (`pause()`), `GameScreen` pauses looping audio
    (`tinnitus.stop()` / `AudioSystem`) and zeroes held touch inputs (`TouchControls.reset()`) so a
    backgrounded client never keeps firing or running into a wall.
  - LibGDX's `AndroidApplication` manages managed textures/FBOs/shaders across context loss, and
    `SdfTexture` / `RenderTarget` are verified to re-upload cleanly on `resize` / resume.
- **Manifest & R8 (`android/AndroidManifest.xml`, `android/proguard-rules.pro`):**
  - Add `<uses-permission android:name="android.permission.INTERNET" />`.
  - Set `android:screenOrientation="sensorLandscape"` (Decision **AN5**) and cutout support.
  - Add ProGuard/R8 `-keep` rules for `io.github.skystrike.shared.net.**`,
    `io.github.skystrike.shared.model.**`, `io.github.skystrike.shared.map.**`,
    `io.github.skystrike.shared.text.**`, `io.github.skystrike.shared.effect.**`,
    `com.esotericsoftware.kryo.**` and `com.esotericsoftware.kryonet.**`, and remove the unused
    Box2D rule.

---

## 6. Decisions

**Decided (2026-10-13, by the user):**

- **AN1 — Left movement stick mode and on-foot vertical actions. Decided (2026-10-13): option (a),
  floating-origin horizontal left stick + dedicated Jump, Jetpack and Crouch/Drop buttons.**
  The left stick drives horizontal `moveX` (`[-1, 1]`) on foot and automatically becomes a full 2D
  flight stick while piloting a drone (`SurveillanceView.DRONE`), while on-foot Jump, Jetpack (hold)
  and Crouch / one-way platform drop-through use dedicated right-side buttons. Options (b), a 2D
  left stick on foot, and (c), a fixed-origin left stick, were the alternatives (fixed stick origin
  can still be offered as a toggle in Settings).
- **AN2 — Right-thumb Aim and Fire scheme. Decided (2026-10-13): option (a), ship both Scheme A
  (Right Aim Stick that retains its angle on release + dedicated Fire and ADS buttons, with
  optional outer-ring auto-fire) and Scheme B (Twin-stick Aim-and-Fire with release-to-throw and an
  inner cancel zone) as a user setting in `Settings`, defaulting to Scheme A.** Options (b) Scheme
  A only, (c) Scheme B only, and (d) Scheme C (relative touchpad / virtual cursor) were the
  alternatives.
- **AN3 — ADS (Aim Down Sights) behaviour on touch. Decided (2026-10-13): option (a), Toggle by
  default on touch, switchable to Hold in `Settings`.** Tapping `ADS` scopes in (`adsAlpha → 1`,
  `VisionConfig.REACH_ADS = 1024`, `150 u` camera pan) and tapping again returns to hip-fire, so
  two-thumb players can stay scoped while aiming and firing without a three-finger claw grip.
  Option (b), Hold only, was the alternative.
- **AN4 — HUD layout on phones and tablets. Decided (2026-10-13): option (a), responsive touch
  layout (`TouchHudLayout`).** On touch devices, `LoadoutBar` (Slots 1–5 + Q/E) moves to
  **bottom-center** as direct `>= 48 dp` touch buttons, `HealthFuelBars` moves out from under the
  left thumb (**top-center** on phones, above the left stick on tablets), a top utility bar (`Chat`,
  `Quick Chat`, `Loadout`, `Pause`) is added, and the desktop HUD layout stays unchanged on
  desktop. Option (b), keeping the desktop corner layout underneath the translucent touch sticks,
  was the alternative.

**Still open:** AN5, AN6, AN7 and AN8. Each has a recommendation below, and none is assumed
silently.

- **AN5 — Screen orientation and display cutouts (`AndroidManifest.xml`).**
  - *(a) Recommended:* `android:screenOrientation="sensorLandscape"` with `shortEdges` display-cutout
    mode and `SafeAreaInsets` applied to all HUD/menu margins.
  - *(b)* Single fixed `landscape` orientation and pillarboxed outside the cutout area.
- **AN6 — Mobile chat and `QuickChat` wheel.**
  - *(a) Recommended:* Build both the `QuickChat` 8-phrase wheel (`CONSOLE_CHAT_PLAN.md` §9) **and**
    the soft-keyboard inset shift + touch buttons in `ConsoleDialog` (Slice AN-6).
  - *(b)* Soft-keyboard `ConsoleDialog` support only; defer `QuickChat`.
- **AN7 — Default FX quality tier on Android.**
  - *(a) Recommended:* Auto-select `FxBudget.Tier.MID` on standard Android devices (`LOW` on
    low-memory devices, `HIGH` on desktop) via `DeviceInfo.recommendedFxTier()`, overridable in
    `Settings` and the `quality` cvar.
  - *(b)* Default all Android devices to `LOW` until the player raises it in `Settings`.
- **AN8 — CI compilation of the `android` module.**
  - *(a) Recommended:* Keep `.github/workflows/ci.yml` compiling `shared`, `server`, `core` and
    `tools` (fast, no Android SDK image required), while `static_check.py` checks `android/src/`
    and unit tests in `shared/src/test` pin the shared touch/layout models.
  - *(b)* Add Android SDK setup and `./gradlew :android:assembleDebug --no-daemon` to
    `.github/workflows/ci.yml` in Slice AN-1.

---

## 7. Phased slice order (one slice per PR)

Each slice is one PR, one concern per commit, code commits before docs commits. Before every
commit, run all seven Python checks (`tools/scratch/static_check.py`, `static_api_check.py`,
`static_audio_check.py`, `synth_sfx.py --check`, `phase3_ballistics.py`,
`weapons/generate_weapon_tables.py`, `arena_sdf_check.py`) and quote their real output. Every slice
updates `docs/PHASE7_AUDIT.md` §6 (append-only, bold title, nothing deferred, "written and not
executed", explicit no-Java disclaimer).

```
AN-1 Platform & Manifest ──► AN-2 Shared Touch & Layout Models ──► AN-3 In-Match Touch Controls
                                                                              │
AN-7 Shaders, Tiers & Lifecycle ◄── AN-6 Mobile Chat & QuickChat ◄── AN-5 Menus/Picker ◄── AN-4 Touch HUD Layout
```

### Slice AN-1 — Platform abstraction (`core/platform/`) and Android manifest / ProGuard baseline

- **Objective:** Establish `core/platform/` and its desktop and Android implementations; fix the P0
  manifest (`INTERNET` permission, `sensorLandscape`, cutout config) and R8 (`proguard-rules.pro`)
  bugs; remove direct `ApplicationType.Desktop` checks from `core`.
- **Files:**
  - `core/src/main/java/io/github/skystrike/platform/`: `PlatformServices.java`, `DeviceInfo.java`,
    `SafeAreaInsets.java`, `KeyboardProvider.java`, `StorageProvider.java`, `AuthProvider.java`.
  - `lwjgl3/src/main/java/io/github/skystrike/lwjgl3/platform/`: `DesktopDeviceInfo.java`,
    `DesktopKeyboardProvider.java`, `DesktopStorageProvider.java`, `DesktopAuthProvider.java`; wire
    into `Lwjgl3Launcher.java`.
  - `android/src/main/java/io/github/skystrike/android/platform/`: `AndroidDeviceInfo.java`,
    `AndroidKeyboardProvider.java`, `AndroidStorageProvider.java`, `AndroidAuthProvider.java`; wire
    into `AndroidLauncher.java`.
  - `core/src/main/java/io/github/skystrike/Main.java`, `screens/GameScreen.java`,
    `ui/hud/HudStage.java`, `settings/ClientPreferences.java`.
  - `android/AndroidManifest.xml`, `android/proguard-rules.pro`.
  - `tools/scratch/static_check.py` (forbid `ApplicationType` in `core/src/` outside `platform/`).
- **Dependencies:** AN5, AN7, AN8.
- **Gate:** `static_check.py` and `static_api_check.py` clean across all 6 modules; zero
  `ApplicationType` references in `core` outside `platform/`. Written and not executed.

### Slice AN-2 — Shared touch-control and responsive HUD layout models (`shared/input/`, `shared/hud/`)

- **Objective:** Build and unit-test all pure touch maths, multi-touch pointer ownership, virtual
  stick deadzones, aim-angle retention, drone flight-stick auto-switching, touch crosshair world
  projection and phone/tablet HUD layout geometry in `shared`.
- **Files:**
  - `shared/src/main/java/io/github/skystrike/shared/config/TouchConfig.java`
  - `shared/src/main/java/io/github/skystrike/shared/input/TouchAction.java`
  - `shared/src/main/java/io/github/skystrike/shared/input/VirtualStickState.java`
  - `shared/src/main/java/io/github/skystrike/shared/input/TouchControlModel.java`
  - `shared/src/main/java/io/github/skystrike/shared/hud/TouchHudLayout.java`
  - `shared/src/main/java/io/github/skystrike/shared/hud/CrosshairMath.java` (add
    `touchReticleWorldPoint`)
  - `shared/src/main/java/io/github/skystrike/shared/settings/Settings.java` (persist touch aim
    scheme, ADS toggle/hold, floating stick, button scale/opacity, left-handed flip)
  - Tests in `shared/src/test/java/io/github/skystrike/shared/`: `input/TouchControlModelTest.java`,
    `hud/TouchHudLayoutTest.java`, `hud/CrosshairMathTest.java`, `settings/SettingsTest.java`.
- **Dependencies:** AN-1; decisions AN1–AN4 (decided 2026-10-13).
- **Gate:** Unit tests pin multi-pointer isolation (left thumb never alters `aimAngle`, button tap
  never alters either stick, releasing right stick retains `aimAngle`, modal focus resets all
  pointers, `SurveillanceView.DRONE` maps left stick Y to `jump`/`crouch`, phone vs tablet layout
  boxes never overlap and every interactive box is `>= 48 dp`). Written and not executed.

### Slice AN-3 — In-match touch controls (`core/input/TouchControls` and gameplay controllers)

- **Objective:** Connect `TouchControlModel` to LibGDX touch events and wire it into `InputSampler`,
  `LoadoutController`, `SurveillanceController`, `DoorUseController` and `GameScreen`.
- **Files:**
  - `core/src/main/java/io/github/skystrike/input/TouchControls.java`
  - `core/src/main/java/io/github/skystrike/input/InputSampler.java`
  - `core/src/main/java/io/github/skystrike/gameplay/LoadoutController.java`
  - `core/src/main/java/io/github/skystrike/gameplay/SurveillanceController.java`
  - `core/src/main/java/io/github/skystrike/gameplay/DoorUseController.java`
  - `core/src/main/java/io/github/skystrike/screens/GameScreen.java`
- **Dependencies:** AN-2.
- **Gate:** On touch devices (and in a desktop `--dev` touch-preview toggle `cl_touch_hud` for
  desktop testing!), `InputSampler` merges keyboard/mouse and `TouchControls`; slot taps (including
  1/2 melee quick-swap), Q/E gadget presses, contextual door use `F`, view cycle `6` and surveillance
  exit all travel through the existing birth-sequence retransmission channels without changing
  `PROTOCOL_VERSION` (18). Written and not executed.

### Slice AN-4 — Phone and tablet HUD layout, touch crosshair, and `LoadoutBar` touch slots (`core/ui/hud/`)

- **Objective:** Render the virtual sticks, action arc, top utility buttons (`Chat`, `Quick Chat`,
  `Loadout`, `Pause`), contextual `USE DOOR` / `EXIT VIEW` buttons, bottom-center `LoadoutBar`,
  repositioned `HealthFuelBars`, and world-projected touch crosshair inside `HudStage`'s existing
  two-pass (`ShapeRenderer` then `SpriteBatch`) pipeline.
- **Files:**
  - `core/src/main/java/io/github/skystrike/ui/hud/HudStage.java`
  - `core/src/main/java/io/github/skystrike/ui/hud/HudFrame.java`
  - `core/src/main/java/io/github/skystrike/ui/hud/HudTheme.java`
  - `core/src/main/java/io/github/skystrike/ui/hud/LoadoutBar.java`
  - `core/src/main/java/io/github/skystrike/ui/hud/HealthFuelBars.java`
  - `core/src/main/java/io/github/skystrike/ui/hud/GadgetPanel.java`
  - `core/src/main/java/io/github/skystrike/ui/hud/SurveillanceBanner.java`
  - `core/src/main/java/io/github/skystrike/ui/hud/TouchHudOverlay.java` (package-private widget
    sharing `HudStage`'s `ShapeRenderer` and `SpriteBatch`)
- **Dependencies:** AN-3, AN4.
- **Gate:** `HudStage` still opens `ShapeRenderer` once and `SpriteBatch` once per frame; on
  desktop (with `cl_touch_hud false`) layout is pixel-identical to M14; on touch (or `cl_touch_hud true`),
  phone and tablet layouts apply with `SafeAreaInsets` and the crosshair tracks `aimAngle`. Written
  and not executed.

### Slice AN-5 — Touch-complete overlays, menus and Android Back key

- **Objective:** Make every menu and modal usable by touch alone and wire `Input.Keys.BACK`.
- **Files:**
  - `core/src/main/java/io/github/skystrike/ui/hud/LoadoutPicker.java` (`touchDown`, `touchDragged`,
    `touchUp` for categories, option rows, scroll, Apply and Close buttons)
  - `core/src/main/java/io/github/skystrike/screens/ConnectingScreen.java` (responsive projection +
    Retry and Cancel buttons + `BACK`)
  - `core/src/main/java/io/github/skystrike/screens/MainMenuScreen.java` (DPI scaling + `BACK`)
  - `core/src/main/java/io/github/skystrike/screens/SettingsScreen.java` and
    `GameSettingsDialog.java` (DPI scaling, Touch Controls section, hide desktop-only window controls
    when `isTouchPrimary()`)
  - `core/src/main/java/io/github/skystrike/screens/PauseOverlay.java` (DPI scaling + `BACK`)
  - `shared/src/main/java/io/github/skystrike/shared/hud/LoadoutPickerModel.java` + test pins in
    `LoadoutPickerModelTest.java` for hit-row / category index selection.
- **Dependencies:** AN-4.
- **Gate:** Every screen and modal can be entered, operated and exited using only touch and Android
  Back, with no keyboard attached. Written and not executed.

### Slice AN-6 — Mobile chat, soft-keyboard inset and `QuickChat` wheel

- **Objective:** Complete `CONSOLE_CHAT_PLAN.md` §9: raise/dismiss the soft keyboard when
  `ConsoleDialog` opens/closes, shift `ConsoleDialog` above `KeyboardProvider.bottomInsetPixels()`,
  add touch Send / Close / History buttons and touch-drag scrollback, and add the `QuickChat`
  preset phrase overlay.
- **Files:**
  - `shared/src/main/java/io/github/skystrike/shared/text/QuickChatPreset.java` +
    `shared/src/test/java/io/github/skystrike/shared/text/QuickChatPresetTest.java`
  - `core/src/main/java/io/github/skystrike/chat/QuickChat.java`
  - `core/src/main/java/io/github/skystrike/ui/console/ConsoleDialog.java`
  - `core/src/main/java/io/github/skystrike/screens/GameScreen.java`
- **Dependencies:** AN-1, AN-4, AN6.
- **Gate:** Opening chat on touch raises the IME and positions the input bar above it; tapping
  completion candidates, `ALL`/`TEAM`, Send, Close and History Up/Down works by touch; `QuickChat`
  sends preset messages through `ChatClient` on the active `ChatTarget`. Written and not executed.

### Slice AN-7 — Mobile shader precision (`mediump`), quality-tier auto-detection and lifecycle hardening

- **Objective:** Fix the fp16 `mediump` overflow/underflow sites in `assets/shaders/`, add a desktop
  `r_force_mediump` debug cvar to test `mediump` on desktop (`EFFECTS_PLAN.md` §3.1), wire
  `DeviceInfo.recommendedFxTier()` into initial settings, and harden `pause()`/`resume()` audio and
  input reset.
- **Files:**
  - `assets/shaders/lib/header.glsl`, `assets/shaders/lib/sdf.glsl`,
    `assets/shaders/lib/door_occluders.glsl`, `assets/shaders/lib/noise.glsl`,
    `assets/shaders/light/visibility_cone.frag`, `assets/shaders/light/light_radial.frag`,
    `assets/shaders/post/blindness.frag`
  - `core/src/main/java/io/github/skystrike/fx/gl/ShaderLibrary.java`
  - `core/src/main/java/io/github/skystrike/screens/GameScreen.java` (`pause()` / `resume()` hooks)
- **Dependencies:** AN-1, AN7.
- **Gate:** No shader squares raw world distances (`> 255 u`) or raw pixel coordinates (`1920 × 1080`)
  in unguarded `mediump`; `pause()` stops looping tinnitus and clears held touch state. Written and
  not executed.

---

## 8. What is deliberately out of scope for this plan

- Hosting a dedicated server on the Android device itself (`Main.localTestAvailable` stays false on
  Android because `AndroidLauncher` passes no `LocalHost`; Android is a client that connects to a
  server over LAN or the internet).
- Touch gestures for `MapEditorScreen` (the map editor is a debug-only authoring tool; touch support
  for it can be planned separately after `MAP_BUILDER_V2_PLAN.md` if desired).
- Bluetooth / USB gamepad bindings (`gdx-controllers-android` is already in `android/build.gradle`,
  and controller support is a separate input source from touch).
- Wire protocol changes (`NetConfig.PROTOCOL_VERSION` remains 18 throughout).

---

## 9. Risks and mitigations

| Risk | Impact | Mitigation |
| --- | --- | --- |
| No JDK, Gradle or Android SDK in the authoring sandbox | High | Keep all touch, layout and quick-chat state machines in `shared` with unit tests in `shared/src/test`; add `cl_touch_hud` so the user can test the touch HUD and virtual sticks on desktop before deploying to a phone; run all 7 Python checks before every commit |
| Multi-touch pointer ID reuse on rapid thumb lifts | High | Track explicit pointer IDs (`movePointer`, `aimPointer`, per-button pointer) in `TouchControlModel` and clear all pointers on `touchCancelled` and `InputRouter.hasModalFocus()` |
| Accidental one-way platform drop-through on touch | Medium | Default to a dedicated `CROUCH` button rather than left-stick downward deflection (AN1), because `PlayerMotion` treats any fresh crouch press on a one-way top as a drop-through |
| Soft keyboard covering `ConsoleDialog`'s bottom input bar | Medium | Query live IME bottom inset through `KeyboardProvider.bottomInsetPixels()` and anchor the dialog above the keyboard while open |
| `mediump` fp16 overflow in visibility, light and noise shaders | High | Normalize vectors by reach / wrap hash coordinates before `dot()` and `length()` in Slice AN-7, and provide `r_force_mediump` on desktop |
| R8 stripping Kryo packet classes in release APKs | High | Explicit `-keep` rules for `io.github.skystrike.shared.**` and `com.esotericsoftware.**` in `android/proguard-rules.pro` in Slice AN-1 |

---

## 10. Acceptance checklist (all unchecked; none is executable in this sandbox)

- [ ] **AN-1:** `AndroidManifest.xml` declares `android.permission.INTERNET` and `sensorLandscape`;
  `proguard-rules.pro` keeps Kryo and `shared` wire classes; `core` has zero `ApplicationType`
  checks outside `core/platform/`.
- [ ] **AN-2:** `shared:test` passes `TouchControlModelTest`, `TouchHudLayoutTest` and the new
  `CrosshairMathTest` pins on CI / desktop.
- [ ] **AN-3 (Movement & Jump/Jetpack/Crouch):** Left virtual stick drives analogue `moveX` smoothly;
  Jump, Jetpack and Crouch/Drop buttons work independently while moving; tapping Crouch on a
  one-way platform drops through; strafing with the left stick never accidentally drops through a
  platform.
- [ ] **AN-3 (Aim & Fire):** Right aim stick rotates aim across 360° while moving with the left
  thumb; lifting the right thumb keeps the last `aimAngle` without snapping; tapping/holding Fire
  fires semi, bolt, burst, shotgun and auto weapons; tapping Fire on an empty gun starts reload.
- [ ] **AN-3 (Utilities):** Tapping Slot 4 or 5 equips the throwable; dragging the right aim stick
  sweeps the predictive `TrajectoryRenderer` bounce arc; pressing Fire throws along that arc.
- [ ] **AN-3 (Gadgets, Surveillance & Doors):** Tapping Q/E deploys and pilots the drone, throws and
  views the camera, and toggles the shield front/rear; while piloting a drone, the left stick flies
  the drone in 2D (X and Y); `EXIT VIEW` and Android Back exit surveillance before opening Pause;
  the contextual `USE DOOR` button appears within `DoorConfig.USE_REACH` of a door and opens/closes
  it.
- [ ] **AN-4 (Phone & Tablet HUD + Crosshair):** On phone (`< 600 dp` shortest width) and tablet
  (`>= 600 dp`), `LoadoutBar` sits at bottom-center with `>= 48 dp` touch targets, `HealthFuelBars`
  is never covered by the left thumb, `SafeAreaInsets` keep all widgets clear of cutouts, and the
  crosshair renders along `aimAngle` with live spread/kick bloom.
- [ ] **AN-5 (Menus & Picker):** `LoadoutScreen` and the in-game `LoadoutPicker` allow selecting
  categories, scrolling options, applying and closing by touch alone; `ConnectingScreen`,
  `MainMenuScreen`, `SettingsScreen`, `GameSettingsDialog` and `PauseOverlay` work by touch and
  respond to Android Back.
- [ ] **AN-6 (Chat & QuickChat):** Tapping `[Chat]` opens `ConsoleDialog`, raises the soft keyboard,
  and shifts the input bar above the IME; `ALL`/`TEAM` toggle, completion taps, Send, Close and
  drag-scrolling work by touch; `[Quick Chat]` sends preset callouts on the active target.
- [ ] **AN-7 (Shaders & Lifecycle):** Visibility cones, soft shadows, point lights and flashbang
  grain render without `mediump` overflow artefacts on an Android device (and under desktop
  `r_force_mediump true`); backgrounding the app stops looping tinnitus and zeroes held inputs.

---

## 11. Desktop and Android re-run script (for the user's build, once slices land)

1. On the desktop build, run `./gradlew checkModuleDependencies shared:test server:test core:compileJava tools:compileJava tools:mapCheck tools:mapBakeCheck --no-daemon`.
2. Launch the desktop client in dev mode (`./gradlew lwjgl3:run --args="--dev"`), start **Local
   test**, and enable the touch HUD preview cvar (`/cl_touch_hud true`) and forced-`mediump` shader
   mode (`/r_force_mediump true`) to verify the phone/tablet HUD layouts, virtual sticks, touch
   crosshair projection, `LoadoutPicker` touch buttons and `QuickChat` wheel on desktop first.
3. Build and install the Android debug APK (`./gradlew :android:installDebug`) on an Android phone
   and/or tablet on the same LAN as a running desktop server (`./gradlew server:run`).
4. Connect from the Android client to the LAN server's IP (`54555` / `54777`) and walk the
   acceptance checkboxes in §10 in order.
5. Record any runtime finding against its slice in `docs/PHASE7_AUDIT.md` §6 before changing this
   plan.
