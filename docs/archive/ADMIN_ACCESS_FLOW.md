# Admin Access Flow — Audit & Preservation Notes

**Date:** 2026-10-07  
**Branch:** `arena/4e3406c6-skystrike` (from `2326d2c` — gun SFX merge)  
**Task:** Redesign `MainMenuScreen`; audit admin capability flow and preserve server authority.

## 1. Authority Model (existing, preserved)

The project enforces **server authority** for every privileged action:

* The client **never grants itself** a level. `ClientCapabilities` starts at `Permission.EVERYONE` / `consoleAccess=false`, changes **only** when a `PacketCapabilities` arrives, and is reset on disconnect (`ClientCapabilities.reset()`).
* The server **resolves** the level (`PermissionResolver.resolve`) and **authorises again** at execution time (`ServerCommandService.execute` re-parses the raw line and checks the resolver). A forged `consoleAccess` flag on the wire buys only a well-formatted refusal.
* The UI only **reflects** the push. A locally flipped boolean cannot execute server commands.

No code change in this branch grants admin via client UI or client capability state. The main-menu redesign touches only visual hierarchy and connection-dialog modality; it does not write to `ClientCapabilities`.

## 2. Server-Side Sources of Truth

### 2.1 `ServerConfig` — deployment configuration only
* **Location:** `server/src/main/java/io/github/skystrike/server/ServerConfig.java`
* **Record fields:** `tcpPort, udpPort, maxPlayers, tickRateHz, profileIntervalSeconds, devMode, grants`
* **CLI parsing (`fromArgs`):** recognises `--tcp-port`, `--udp-port`, `--max-players`, `--tick-rate`, `--profile-interval`, `--dev` (valueless flag), repeatable `--grant name=LEVEL`.
* **Grant syntax:** `name=LEVEL` where `LEVEL ∈ {everyone,player,moderator,admin}` (case-insensitive, `Permission.parse`). Malformed grants throw `IllegalArgumentException` at startup — never a silent admin.
* **Defaults:** from `NetConfig.DEFAULT_*` / `WorldConfig.TICK_RATE_HZ`; `devMode=false`, `grants={}`.
* **Usage string:** exposed via `usage()`.

### 2.2 `Permission` & `ConsoleAccess` — the ladder and threshold
* **Location:** `shared/src/main/java/io/github/skystrike/shared/command/Permission.java`, `ConsoleAccess.java`
* **Order:** `EVERYONE < PLAYER < MODERATOR < ADMIN` (`rank() == ordinal()`). Checks are `atLeast` (>=), not equality — admin outranks moderator.
* **Decode safety:** `fromRank` out of range → `EVERYONE` (least privileged). `parse(null/unknown)` → `null` → startup error for grants.
* **Threshold:** `ConsoleAccess.SHIPPING_THRESHOLD = MODERATOR`. `isGranted(level, threshold)` is true iff `level.atLeast(threshold)`. Changing threshold is a policy change, not a code change (e.g. lowering to `PLAYER` gives everyone a console whose registry is still filtered).

### 2.3 `PermissionResolver` — effective level per joined player
* **Location:** `server/src/main/java/io/github/skystrike/server/command/PermissionResolver.java`
* **Sources, checked in order:**
  1. **Runtime override** by live `playerId` (`overridesByPlayerId`) — what mid-match `promote` writes; scoped to session, cleared by `forget(playerId)` on disconnect so a reused id does not inherit.
  2. **Configured name grant** (`grantsByName`, case-insensitive via `normalise`).
  3. **Fallback:** `DEFAULT_LEVEL = PLAYER`.
* **Dev mode:** when `setDevMode(true)`, `resolve` returns `ADMIN` for every player regardless of grants/overrides. Loud startup announcement in `GameServer`.
* **Console gate:** `hasConsoleAccess(id,name) == ConsoleAccess.isGranted(resolve(id,name), consoleThreshold)`. Threshold is mutable via `setConsoleThreshold`.

### 2.4 `CapabilityBroadcaster` — push, never poll
* **Location:** `server/src/main/java/io/github/skystrike/server/command/CapabilityBroadcaster.java`
* **Sender:** `GameServer` wiring `(playerId, packet) -> endpoint.sendReliable(connection, packet)`.
* **Packets:** `PacketCapabilities { Permission level, boolean consoleAccess }` (`shared/.../s2c/PacketCapabilities.java`). `forLevel(level, threshold)` builds both fields; `matches` deduplicates.
* **Push points:**
  * `pushOnJoin` — unconditional, called from `JoinRequestHandler` **immediately after** `PacketJoinAccept`. Until it arrives the client has no console (correct default).
  * `promote` → `setRuntimeLevel` + `pushIfChanged` — mid-match promotion/demotion takes effect without reconnect; redundant pushes suppressed and last-sent cache cleared by `forget`.
* **Threading:** tick thread only.

### 2.5 `GameServer` composition
* **Location:** `server/src/main/java/io/github/skystrike/server/GameServer.java`
* Constructor: `permissions.setDevMode(config.devMode())` then `config.grants().forEach(permissions::grant)`.
* `debugCommandsEnabled` true when `DebugFlags.enabled()` or `devMode` or any grant is `ADMIN` (so local debug cvars are visible only when host deliberately enabled them; resolver remains authoritative).
* `CapabilityBroadcaster` built from `permissions` and `this::sendToPlayer`; registered on `JoinRequestHandler`.
* On `DISCONNECTED`: `chatService.forget`, `capabilities.forget`, `commandService.forget` — no state leaks.

### 2.6 `ClientCapabilities` & `ClientCommandService` — client reflection
* **Location:** `core/src/main/java/io/github/skystrike/command/ClientCapabilities.java`, `ClientCommandService.java`
* `apply(PacketCapabilities)` is the **only** mutator; `reset()` on disconnect.
* `unlocked() = DebugFlags.enabled() || capabilities.consoleAccess()` — debug flag enables **local** commands only; `localPermission()` stays the last server-granted level, so help/completion never implies remote authority.
* `isCommandLine` vs `isUnavailableCommandAttempt` distinguishes locked slash attempts (UI explains gate, `//` remains explicit chat escape). Forwarded commands send **raw line**, never a capability claim; server re-parses and re-authorises.

## 3. End-to-End Flow

```
launch --dev --grant Nova=ADMIN --grant rook=moderator
  │
  ├─ ServerConfig.fromArgs → {devMode, grants}
  ├─ GameServer ctor → PermissionResolver ← devMode / grants
  └─ Network bind

player joins as "Nova"
  ├─ PacketJoinRequest{name="Nova"}
  ├─ JoinRequestHandler validates → ConnectionRegistry.join → PlayerRegistry.register
  ├─ PacketJoinAccept → client
  └─ CapabilityBroadcaster.pushOnJoin(playerId, "Nova")
        → resolve → ADMIN (via devMode or grant)
        → PacketCapabilities{ADMIN, consoleAccess=true} → reliable

client GameScreen.onCapabilities
  └─ ClientCapabilities.apply → capabilities.consoleAccess==true
  └─ system line: "Console access granted (ADMIN)."

client types "/kick Rook"
  ├─ ClientCommandService.isCommandLine==true → local spec check
  │     SERVER-side spec → forward raw line via sendCommand
  └─ Server ServerCommandService.execute(id, "Nova", "kick Rook", now)
        → resolver.resolve(id,"Nova")==ADMIN → authorise
        → CommandDispatcher → result → PacketCommandResponse → client scrollback

moderator demotion mid-match
  ├─ server operator: /promote Rook PLAYER  (or admin console)
  ├─ CapabilityBroadcaster.promote(RookId, "Rook", PLAYER) → pushIfChanged → PacketCapabilities{PLAYER,false}
  └─ client Rook: apply → system line "Console access is not available..."
```

## 4. Tests Inspected (preserved, no protocol change)

* `ServerConfigTest` — defaults from shared contract, no-args → defaults, individual overrides, unknown/incomplete rejected, out-of-range rejected, `--dev` valueless, repeatable `--grant` case-insensitive, malformed `--grant` throws, snapshot rate helper.
* `PermissionResolverTest` — fallback `PLAYER` no console, grants case-insensitive & revoke, runtime promotion outranks grant and reports change, `forget` drops override, threshold configurable.
* `CapabilityBroadcasterTest` — every join pushes even when `false`, redundant pushes suppressed, promotion/demotion pushes immediately, repeated same level silent, threshold drives flag, `forget` clears cache & promotion.
* `ServerCommandServiceTest` / `ConsoleAccessTest` — ladder ordering, unknown rank → `EVERYONE`, parsing case-insensitive, shipping threshold `MODERATOR`, configurable threshold, null level grants nothing.

All contracts remain satisfied; this branch introduces no new permission level, no new packet field, and no client-side grant path.

## 5. What the Main-Menu Redesign Does and Does Not Do

* **Does:** centre-branded header, separator, card-style menu (`Play/Loadout/Settings/Quit`) using existing `Skin`/`FontManager` assets; hide the four connection fields behind a non-movable modal dialog (dim overlay with `Touchable.enabled`, `InputListener` that consumes background touches, focus trapped to dialog controls, `ESC`/`Cancel` close, `ENTER` confirms, validation preserved). Keeps `MainMenuScreen.Request`/`Connection` and `Main→ConnectingScreen→GameScreen` routing unchanged; `Loadout` does not acquire a second connection form.
* **Does not:** add a second UI framework, parallel auth system, protocol change, or gameplay change. Does not set `ClientCapabilities.consoleAccess` or manufacture a `Permission` on the client.

## 6. Preservation Checklist

- [x] `MainMenuScreen.Request` and `Connection` signatures unchanged.
- [x] `Main` screen transitions and `ConnectingScreen` ownership unchanged.
- [x] `LoadoutScreen` remains a preview-only HUD picker; no connection form added.
- [x] Saved/default connection values still flow via `Request` fields and `Main.menuName/menuHost/menuTcp/menuUdp`.
- [x] Dialog is non-movable (no `Window`; plain `Table` with `window` background), modal (dim overlay, `setVisible` toggling, `toFront`, `setKeyboardFocus`, background touch consumption, `UP/DOWN/TAB/ENTER/ESC` handling).
- [x] Validation (`Name required`, `Host required`, port 1-65535) preserved, now shown in `dialogStatus` with field focus + `selectAll`.
- [x] Server authority preserved: no client UI writes to `PermissionResolver`, `CapabilityBroadcaster`, or `ClientCapabilities` except via `PacketCapabilities`.
