# Multiplayer menu, room browser, rooms and pre-match lobby — plan

> **Archived 2026-10-14 — design/history reference, not an active progress tracker.**
> Outstanding implementation and verification work is tracked in [NOT_DONE.md](../NOT_DONE.md).
> Archiving does not mean every feature is implemented or runtime-verified.

**Status: plan only.** Nothing in this document is implemented. Nothing in it has been compiled,
run or observed: this sandbox has no JDK and no Gradle, so every phase is to be written and not
executed, with the first real run in CI and on the user's desktop build. Every acceptance box in
§13 is unchecked.

## 0. How this plan relates to the others

- `docs/SERVER_LOBBY_PLAN.md` is the **server** design: the split of `GameServer` (its S0), the
  hall and lobby room (S1), teams, loadouts and ready (S2), the map pool and ban phase (S3),
  concurrent instances (S4), the match layer (S5), and return to lobby, reconnect and host
  migration (S6). Of its ten open decisions, **L1 (one process, many rooms) is decided** (§12); L2–L10 are still
  open.
- This plan is the **player-facing layer** on top of that design: the screens, the room model
  a player sees, the browser, the moderation rules, and the order in which everything is built.
  Where a phase here needs a server slice, §11 names it. Where this plan needs a decision the
  server plan does not cover, §12 lists it as M1–M9.
- The map builder plans (`MAP_BUILDER_PLAN.md`, `MAP_BUILDER_V2_PLAN.md`) matter here only
  through the map pool: a room chooses from it, and every client must have the map (their L10
  and V2 D5 are the same question).

## 1. What exists today (audited against the tree)

- **Menu.** `MainMenuScreen` offers `Play`, `Loadout`, `Settings`, `Quit`. In a debug build it
  also offers `Local test` (an embedded host, joined on the same machine) and `Map editor`.
  `Play` connects to a **typed host and port**. There is no other way into a game.
- **Screens.** `ConnectingScreen`, `GameScreen`, `LoadoutScreen`, `SettingsScreen`,
  `GameSettingsDialog`, `PauseOverlay`, `MapEditorScreen`. There is no room list, room, lobby,
  password prompt or match-result screen.
- **Networking defaults.** `NetConfig.DEFAULT_HOST = 127.0.0.1`, TCP `54555`, UDP `54777`,
  `CONNECT_TIMEOUT_MS = 5000`. Kryo over TCP and UDP.
- **Discovery.** None. A server is found only by typing its address. The word "broadcast" in
  the tree refers to effect and capability broadcasts, not server discovery.
- **Identity.** A typed player name. No accounts, tokens or sessions outlive a connection. The
  server assigns a player id per `GameServer`.
- **Admin.** `ServerCommandModule` has `kick` and `mapreset`, gated by permission. There is no
  room host concept and no ban.
- **Liveness.** The client sends `PacketPing` (`ClientSession`, `PingHandler` on the server).
  No idle-timeout rule that removes a silent player was found. The reconnect design does not
  exist.
- **Android.** An `android` module exists. Whether its networking path is the same as the
  desktop's was not verified. This is a P0 audit item.
- **Repo rules that shape the design.** Tests live only in `shared/src/test` and
  `server/src/test`, so room rules, navigation state and validation must be pure code in
  `shared` to be tested. `core` holds rendering and input only. `PROTOCOL_VERSION` is 18; packets
  are append-only, with the version bumped in the same commit as the first packet.

## 2. Definitions: which kind of multiplayer is which

The user's request separates these. Today only the first two exist, and only in a limited form.

| Mode | What it means | Today | Planned |
| --- | --- | --- | --- |
| **Local test** | Embedded host on this machine, debug build only | Exists | Kept for development; joins a room, not the world directly |
| **Direct connect** | Type a host and port (LAN or internet) | Exists as `Play` | Kept as a menu entry, unchanged in behaviour |
| **LAN** | Find servers on the same network without typing an address | Not built | Added in P10 (UDP discovery on a separate port) |
| **Internet** | A server reached by public address (port forward or a hosted server) | Possible by typing an address; no list | Room browser shows only what a registry supplies (M1); no NAT traversal planned |
| **Regional** | Servers grouped by region, with connection quality | Not built | Deferred (§14). Needs a registry; the room entry schema carries a region field from P2 so no migration is needed later |

"Online" in the menu means "a room reachable through a server list" (internet or LAN after P10).
"Direct connect" stays visible for both, because it is the only path that works without a list.

## 3. Identity and trust (decision M2)

Everything in §5–§9 depends on who a player is. Today a player is a typed name, which the
server accepts without checking. Options:

- **Guest names (recommended for the first phases).** The name is accepted as typed, validated
  for length and characters, and made unique per server by a suffix. A session token is issued
  per connection. Hosts, bans and invitations work **per server session**, and a determined
  player can evade a ban by reconnecting under another name or from another address.
- **Accounts.** A login service, persistent identities and friend lists. This is a large new
  system with its own backend, security obligations and data handling. It is out of scope for
  this plan's phases and would need its own plan.

The plan proceeds on accounts, as decided (M2 in §12). This section's guest-name options remain as
the record of what was considered. What accounts require is set out in §12.

## 4. Navigation and screen flow

```
Main menu
 ├─ Multiplayer ──► Multiplayer menu
 │                   ├─ Quick match ──► Searching ──► (match found) Room lobby
 │                   ├─ Browse rooms ──► Room browser ──► [password prompt if private] ──► Room lobby
 │                   ├─ Create room ──► Create room form ──► Room lobby (as host)
 │                   ├─ Join by code ──► Code entry ──► [password prompt] ──► Room lobby
 │                   └─ Direct connect ──► Connecting ──► Room lobby (or the server's hall, S1)
 ├─ Loadout (existing, also reachable from the room lobby)
 ├─ Settings (existing)
 ├─ Local test (debug only) ──► Connecting ──► Room lobby
 ├─ Map editor (debug only, unchanged)
 └─ Quit

Room lobby ──► Match starting (countdown) ──► Game screen
Game screen ──► Pause ──► Leave match ──► Room lobby   (or Multiplayer menu if the room closed)
Room lobby ──► Leave room ──► Multiplayer menu
Any connected screen ──► connection lost ──► Reconnecting overlay ──► back to the same screen,
                                              or Multiplayer menu with a reason
```

**Rules that apply to every screen:**

- Every screen has a visible **Back** (or **Cancel**) action and the Escape key does the same.
  Back never leaves a request half-sent: a pending join or create is cancelled by a
  `Cancel` request, and any late reply is ignored by its request id.
- Every **transitional** state (connecting, searching, joining, creating, starting, reconnecting)
  has (a) a cancel action, and (b) a timeout that moves the player to a stable screen with a
  message. No state waits forever.
- A screen never shows another room's data. A stale reply for a room the player has left is
  dropped.

## 5. Screen specifications

Each screen lists its purpose, its elements, the actions a player can take, its states, and
where it goes. Layout uses the same Scene2D `Stage`, `Table` and `Skin` as the existing menus.
The styling is the game's own; the reference game's flow is copied in structure (browser →
details → join; lobby with roster, ready and host start), not in art or wording.

### 5.1 Main menu (changed)

- **Purpose:** the entry point. `Play` becomes `Multiplayer`; the typed-address path moves under
  it as **Direct connect**.
- **Elements:** Multiplayer, Loadout, Settings, Quit; Local test and Map editor only in debug.
- **Actions:** open Multiplayer; the others as today.
- **States:** none new.
- **Note:** `MainMenuScreen.Connection` (the validated Play request) is reused by Direct connect.

### 5.2 Multiplayer menu (new)

- **Purpose:** choose how to play online.
- **Elements:** Quick match, Browse rooms, Create room, Join by code, Direct connect, Back. A
  line shows the player's name (with the guest-name rule of §3) and an edit link.
- **Actions:** each button opens its screen. Quick match is disabled with a reason when no
  server is reachable.
- **States:** normal; "no server selected" (only when there is no default server and no list
  entry); a connection error banner that clears on the next attempt.
- **Navigation:** every child screen returns here on Back.

### 5.3 Room browser (new)

- **Purpose:** find a room to join.
- **Elements:**
  - A list. Each row: room name, game mode, map (or "pool: 3 maps" when the room has a pool),
    players (`3/8`), state (open, in match, starting), a lock icon for password rooms, and a
    region tag when M9 applies.
  - A search box (name match) and filters: mode, map, not full, open only, has password, region.
  - Refresh (manual, plus an automatic refresh every 10 seconds while the screen is open).
  - A detail panel for the selected row: host name, mode, map pool, player limit, match
    duration, ping to the server, and a **Join** button.
  - Join is disabled with a reason for full, closed, in-match (unless M6 allows it), expired,
    version-incompatible, and banned-from-this-room rooms.
- **Actions:** select, search, filter, refresh, join, back.
- **States:**
  - *Loading* — skeleton rows; Back cancels the request.
  - *Populated* — the list, with the selected row kept after refresh if the room still exists.
  - *Empty* — "No rooms match these filters" with a button to clear filters, and a button to
    create a room.
  - *Connection failure* — "Could not reach the server" with Retry and a link to Direct connect.
  - *Server unavailable* — when the server reports maintenance or a version mismatch, the reason
    text from the server.
  - *Stale list* — "Last updated 42 s ago" when a refresh is failing but a previous list exists.
- **Navigation:** a row's Join goes to the password prompt (private) or straight to the room
  lobby (public). Back returns to the multiplayer menu.

### 5.4 Create room (new)

- **Purpose:** host a room.
- **Elements:**
  - Room name (1–32 characters, letters, digits, spaces and a short punctuation set).
  - Visibility: **Public** (in the browser) or **Private** (not listed; joined by code, and a
    password is optional).
  - Password, shown only for private rooms, with a Show toggle.
  - Game mode (from the modes the server reports).
  - Map pool: one or more maps from the server's pool.
  - Player limit (within the mode's range).
  - Match duration (within the mode's range).
  - Team size rule (the server plan's default: equal teams).
  - Create and Back.
- **Actions:** edit fields, create, back.
- **Validation:** every field is checked on the client for immediate feedback, and the server
  checks it again and is the only authority. Messages name the field and the rule ("Name must
  be 1 to 32 characters"). Create is disabled until the form is valid.
- **States:**
  - *Editing* — normal.
  - *Creating* — the button shows progress; Back cancels the request, and a late success is
    left in place and joined on the next room list refresh (so cancel never half-creates).
  - *Rejected by server* — the server's reason, for example a name already in use or the
    server's room cap reached.
  - *Created* — goes straight to the room lobby as host.
- **Navigation:** success to the room lobby; Back to the multiplayer menu.

### 5.5 Password prompt (new)

- **Purpose:** join a private room.
- **Elements:** the room name, a password field with Show, Join and Cancel.
- **Actions:** submit, cancel.
- **States:**
  - *Waiting for the server's answer* — the button is disabled; Cancel is available.
  - *Wrong password* — "Wrong password" with the attempts remaining, and the field cleared and
    focused.
  - *Locked out* — after too many wrong attempts for this room and this session: "Too many
    attempts. Try again in 4 minutes" with a live countdown. Rate-limit defaults are in §9.
  - *Room gone* — "This room is no longer available" with Back to the browser.
- **Navigation:** success to the room lobby; cancel or back to the browser.

### 5.6 Join by code (new)

- **Purpose:** join a private room with the code the host shared.
- **Elements:** a code field (formatted in groups, case-insensitive, with an alphabet that has no
  look-alike characters, see §9), Join, Back.
- **States:** *Looking up* (Cancel available); *not found* ("No room with this code. Check the
  code with the host."); *expired* ("This code has expired. Ask the host for a new one."); *rate
  limited* (the same lockout as §5.5, shared across the two prompts); *found* → the password
  prompt if the room has one, otherwise the room lobby.

### 5.7 Quick match (new)

- **Purpose:** get into a game with the least input.
- **Elements:** the chosen mode (a dropdown with the modes the server reports), a Cancel
  button, an elapsed timer, and a status line.
- **Behaviour (proposed; §12 M7):** join the best open public room of that mode that is not
  full and not in a match. If none is open, create a public room with the defaults and wait for
  others to join it.
- **States:** *Searching* (timer, Cancel); *Found — joining* (the join is in flight; Cancel
  still works); *No room found after N seconds* — the defaults creates one (if that is the rule)
  or the screen says so and offers Browse rooms; *Failed* (the server or connection reason).
- **Navigation:** success to the room lobby; cancel or failure to the multiplayer menu.

### 5.8 Room lobby (new; the pre-match lobby)

- **Purpose:** the place where the group gathers, configures and readies up before the match.
  The server plan's `Setup` state is the server side of this screen.
- **Layout:** a roster on the left, a settings-and-map panel in the middle, and chat along the
  bottom. A bottom bar holds the ready and start controls.
- **Roster row:** name, a host mark, the team, a loadout summary (the primary weapon name),
  a ready tick, a connection mark (connected, away, reconnecting), and a placeholder avatar
  (§12 M4). The local player's own row is marked.
- **Settings and map panel:** room name, mode, map (or the pool and the ban phase result), player
  limit, match duration. Read-only for everyone except the host, who can edit them until the
  match starts. Each editable field says why it is locked when it is.
- **Actions:**
  - *Everyone:* change team (within the team's limit), change loadout (opens the existing
    Loadout screen and returns here), ready and not ready, chat, leave room.
  - *Host only:* edit settings, kick a member, ban a member (M6), start the match, close the room.
- **Start button:** enabled when the minimum player rule is met (server plan L9) and every
  member is ready (or the host allows starting with unready members — §12 M5). When disabled, the
  button text or a tooltip states the first unmet requirement ("Waiting for ready: 2 players").
- **States:**
  - *Waiting for players* — the roster shows empty slots.
  - *Ready / not ready* — the local button toggles; other rows update within a tick of the
    server's confirmation.
  - *Start pending* — the host pressed Start; the button shows "Starting…" until the server
    confirms or refuses.
  - *Countdown* — an overlay with the seconds left; the host can cancel it; settings and teams
    are locked.
  - *Member away* — a row is greyed after a silence threshold; the seat is held (§7).
  - *Connection lost* — the reconnecting overlay (§5.10).
  - *Host left* — §5.11.
  - *Settings changed by the host* — a short notice in the chat log ("Host changed the map").
- **Navigation:** Start → countdown → game screen (for all members at the same time). Leave →
  multiplayer menu. Loadout → back to this screen.

### 5.9 Match starting (new transitional screen)

- **Purpose:** move every member into the same match together.
- **Elements:** the countdown, the map name, the team list, and a line for each member not yet
  loaded ("Waiting for 1 player").
- **Actions:** the host may cancel during the countdown. Other players may only leave the room.
- **States:** *Countdown*; *Waiting for loading* (a member is slow, with a timeout after which
  that member is removed from the start and the match begins with the rest, §9); *Loaded*;
  *Start cancelled*; *Failed to start* (the server's reason, with Back to the room lobby).
- **Navigation:** the game screen, or back to the room lobby when cancelled or failed.

### 5.10 Connection lost and reconnecting (new overlay)

- **Purpose:** explain a dropped connection without losing the player's place.
- **Elements:** a message ("Connection lost. Reconnecting…"), an attempt counter, an elapsed
  timer, and a Cancel button.
- **Behaviour:** the client retries with backoff (§9). A successful reconnect resumes the same
  screen. The server holds the player's seat for the grace period (§7).
- **States:** *Reconnecting* (attempt n of N); *Reconnected* (the overlay closes, with a brief
  "Reconnected" message); *Gave up* ("Could not reconnect" with Back to the multiplayer menu);
  *Seat lost* ("Your place in the room was released" with Back).
- **Navigation:** back to the screen the player was on, or to the multiplayer menu.

### 5.11 Notices: kicked, banned, room closed (new)

- **Kicked:** "The host removed you from the room." The player returns to the multiplayer menu,
  and may join another room unless the kick was a ban.
- **Banned:** "You cannot join this room." With the time remaining, or "permanently" when set.
  The room browser hides the room for a banned player and refuses it on join. Ban scope is M6.
- **Room closed:** "The room was closed." (host left with no successor, host closed it, or the
  room expired). The player returns to the multiplayer menu.
- **Host left, room kept:** "The host left. {name} is the new host." The room lobby stays.

### 5.12 Game screen integration

- The existing `GameScreen` is reused. The additions are a **Leave match** action in the pause
  overlay, which returns to the room lobby if the room still exists, and otherwise to the
  multiplayer menu with a notice.
- Whether a player who leaves mid-match may rejoin is server-plan decision L6.

## 6. Room model and lifecycle

These rules belong in `shared` as pure code, so `shared/src/test` can test them without a
server or a GPU.

**Room fields.** `roomId` (server-internal, stable), `code` (the join code), `name`,
`visibility` (public or private), `passwordHash` (salted; never sent to a client), `mode`, `mapPool`,
`playerLimit`, `matchDuration`, `teamSizeRule`, `region` (M9; empty until registries exist),
`hostSessionId`, `state` (`OPEN`, `STARTING`, `IN_MATCH`, `CLOSED`), `version` (a counter that
increases on every change), `createdAt`, `emptySince`.

**Member fields.** `sessionId`, `name`, `team`, `loadoutId`, `ready`, `isHost`, `connection`
(`CONNECTED`, `AWAY`, `RECONNECTING`), `seatExpiresAt` (when `RECONNECTING`), `joinedAt`.

**Join rules.** A join is accepted only when all of these hold, checked in this order and refused
with the first failing reason:

1. the client's protocol version matches the server's;
2. the room exists and is not `CLOSED`;
3. the player is not banned from the room (M6);
4. the room is not `IN_MATCH` or `STARTING` (unless M6 allows a mid-match join);
5. the room is not full (members, including seats held for reconnecting players, count);
6. the password matches, when the room has one (and the attempt limit is not reached);
7. the player is not already in this room (a duplicate request returns the existing seat).

**Host rules.** Only the host may edit settings, kick, ban, start, or close. The host is the
first member to join a new room. When the host leaves or times out:

- If another member is connected, the **oldest connected member** becomes host, and every
  member receives a notice (M3 may choose a different rule).
- If no member is connected, the room is `CLOSED` at once, or after a short grace period so a
  host who briefly disconnects can return (§9).

**Start rules.** The host can start only when the room is `OPEN`, the minimum player rule holds
(server plan L9), every settings value is valid, every member has a valid loadout and team, and
at least one map is selected. The server re-checks these on the start request, because a member
may have changed state a moment earlier.

**Cleanup rules.** An empty room is removed after a grace period (§9). A `CLOSED` room is removed
once its members are gone. An `IN_MATCH` room with no connected members ends its match and closes.

**Versioning.** Every room change increments `version`. A client applies a room snapshot only
when its `version` is newer than the one it holds, so a late or duplicated broadcast cannot
roll the screen back.

## 7. Players joining, leaving, disconnecting and reconnecting

- **Joining during setup:** the new member gets the full room snapshot, then the deltas. The
  snapshot is the room state at one version, so the client never builds state from a partial set.
- **Leaving during setup:** the seat is freed at once, and the roster shows the change.
- **Disconnecting:** the member is `AWAY` after the silence threshold, and their seat is held as
  `RECONNECTING` until `seatExpiresAt`. A reconnect within the grace period restores the seat,
  the team, the loadout and the ready state.
- **Holding a seat in a match:** a member who drops during `IN_MATCH` keeps their place in the
  match for the grace period (server plan L6 decides whether this applies at all).
- **Unresponsive:** a member who is connected but silent is treated as `AWAY`, not removed.
  Removal happens only at the end of the grace period, so a slow network does not kick a player
  who is still there.

## 8. Networking and protocol impact

Packet names are proposals; each slice registers its own packets append-only, and bumps
`PROTOCOL_VERSION` in the same commit as the first one. Requests carry a `requestId`; every
reply echoes it.

| Packet (proposed) | Direction | Used by | Notes |
| --- | --- | --- | --- |
| `ServerInfoRequest` / `ServerInfo` | C→S / S→C | Multiplayer menu | version, modes, map pool summary, player counts, maintenance text |
| `RoomListRequest` / `RoomList` | C→S / S→C | Room browser | paged or capped; filters applied on the client; includes `version` per room |
| `CreateRoomRequest` / `CreateRoomResult` | C→S / S→C | Create room | server validates every field; returns the room's code or a reason |
| `JoinRoomRequest` / `JoinRoomResult` | C→S / S→C | Browser, prompt, code, quick match | password in the request, never in a broadcast; result carries a refusal reason |
| `LeaveRoomRequest` | C→S | Any room screen | idempotent |
| `RoomSnapshot` | S→C | Room lobby | full state at a `version`; sent on join and after a reconnect |
| `RoomDelta` (or the snapshot alone) | S→C | Room lobby | Decision in P6: deltas are an optimisation; a full snapshot is correct and simpler first |
| `RoomSettingsRequest` | C→S | Host | the server applies only from the host |
| `SetTeamRequest`, `SetReadyRequest`, `SetLoadoutRequest` | C→S | Room lobby | the server answers with a refusal reason or the new snapshot |
| `KickRequest`, `BanRequest` | C→S | Host | host or admin only |
| `StartMatchRequest` / `MatchStarting` | C→S / S→C | Host, all members | countdown with a server-side end time |
| `CancelStartRequest` | C→S | Host | during countdown only |
| `QuickMatchRequest` / `QuickMatchCancel` / `QuickMatchStatus` | C→S / S→C | Quick match | server-side queue; see P9 |
| `RoomClosed` / `KickedNotice` | S→C | Any room screen | reason enum; the client returns to the menu |

Existing packets this changes:

- **`PacketJoinRequest`** currently enters the world directly. After P3 it enters the server's
  hall (server plan S1). That is a breaking change, so the protocol bump and a refusal of the
  old version must land together.
- **`PacketJoinReject`** already carries a reason; the new refusal reasons extend that enum
  append-only.

**Ordering and idempotency:** the server serialises all room mutations on the tick or host thread
(the queue-then-tick model of `NetworkEndpoint`), so two simultaneous joins for the last seat are
resolved by queue order: one succeeds, and the other gets a "room is full" refusal. Duplicate
requests with the same `requestId` return the original reply.

## 9. Defaults this plan proposes

Starting values, all changeable. They are not decisions.

| Setting | Proposed default |
| --- | --- |
| Room name length | 1–32 characters |
| Room code | 8 characters from an alphabet without look-alikes (no `0 O 1 I L`) |
| Room code lifetime | 24 hours, renewable by the host |
| Wrong password attempts | 5 per room per session, then 5-minute lockout |
| Join-by-code attempts | 10 per minute per session |
| Create-room rate | 3 per 5 minutes per session |
| Browser auto-refresh | 10 seconds while open |
| Connect timeout | 5 seconds (`CONNECT_TIMEOUT_MS`, existing) |
| Join reply timeout | 10 seconds, then the screen returns with a message |
| Silence before `AWAY` | 15 seconds (proposed, after the ping rate is confirmed in P0) |
| Seat grace period | 60 seconds |
| Reconnect attempts | 3, with backoff of 1, 2 and 4 seconds |
| Host grace before closing an empty room | 30 seconds |
| Empty room removal | 60 seconds after the last member leaves |
| Countdown | 10 seconds |
| Loading timeout before start | 20 seconds per slow member, then they are dropped from the start |

## 10. Security and abuse

- **Passwords:** salted hash on the server; never in a broadcast or a snapshot; compared in
  constant time; the attempt limits in §9 apply per room and per session.
- **Room codes:** random, from the alphabet in §9, so they are not guessable at useful rates
  with the join-by-code limit in place.
- **Input:** names and room names are length-checked, trimmed and filtered for control
  characters on the server, which is the only place these checks count.
- **Authority:** every host action (settings, kick, ban, start, close) is checked on the server
  against the sender's session. A client that sends a host action as a non-host is refused, and
  the refusal is logged with the session.
- **Flooding:** create, join and browser requests have the limits in §9. Above the limit, the
  server refuses with a reason and does not process the request.
- **Bans:** under guest names, a ban is per session, plus the name and the address as a weaker
  second key (§12 M6). A ban is never the only defence against a disruptive player: the kick and
  the room limits exist for the case where a ban is evaded.
- **Admin override:** the existing permission model applies. An admin may act in any room, and
  every such action is logged.
- **Trust boundary:** the server decides who is in a room, what the room's settings are, and
  whether a match starts. The client renders the server's state and sends requests.

## 11. Phases

Each phase is one PR (or a small set), one concern per commit. Code commits come before docs
commits. The seven repo checks (`tools/scratch/static_check.py`, `static_api_check.py`,
`static_audio_check.py`, `synth_sfx.py --check`, `phase3_ballistics.py`,
`weapons/generate_weapon_tables.py`, `arena_sdf_check.py`) run before every commit, with real
output quoted. **Do not implement two unrelated phases at once.** Related changes can share a
phase; a UI phase and a networking phase are never combined.

The phases below are a starting point. They can be reordered or merged once P0 has run. The
mapping to the server plan's slices is in each phase's "Dependencies" line.

### P0 — Existing-system audit and technical decisions (no code)

- **Objective:** confirm the facts in §1 and record the decisions the later phases need.
- **Included:** read the menu, connecting, game and settings code paths end to end; confirm the
  Android networking path; measure the ping rate and whether any server-side silence rule
  exists; confirm that Scene2D menus use one shared style; answer M1–M9 or record them as open;
  decide the packet-naming convention and the delta-versus-snapshot question for P6.
- **Excluded:** any change to code.
- **Files:** none changed. Output is a decision record appended to this document.
- **Dependencies:** none.
- **Steps:** (1) trace `MainMenuScreen` → `Main.connectToGame` → `ConnectingScreen` → the game
  screen, and list every transition; (2) trace the Android entry point and its network calls;
  (3) read `ClientSession` for the ping interval and `PingHandler` for what the server does with
  it; (4) write the answers into §12 and the open list.
- **Tests:** none.
- **Acceptance:** every fact in §1 is marked confirmed or corrected; every decision in §12 is
  marked decided or open.
- **Risks:** the Android path may differ enough to need its own phase. If so, it moves out of
  P10 into a separate phase.
- **Exit criteria:** P1 needs no decision from §12. The later phases name the decisions they
  need: M1 before P4, and M2 before P5.

### P1 — Multiplayer menu and navigation shell (client only, no networking)

- **Objective:** a clear entry point, the screen flow of §4, and the Back and Cancel rules, with
  every screen stubbed, so the flow can be walked before the backend exists.
- **Included:** the Multiplayer menu (§5.2); a `Multiplayer` entry replacing `Play` in the main
  menu, with `Play`'s old behaviour under Direct connect; stub screens for the browser, create
  room, password prompt, code entry, quick match, the room lobby, match starting, and the
  reconnect overlay, each with its Back action; a **screen-flow state model** in `shared`
  (a pure state machine: current screen, pending transitional state, timeout, cancel) so the
  navigation rules are testable.
- **Excluded:** any network call; any real room data; any protocol change.
- **Files:** `core/.../screens/MainMenuScreen.java`, `core/.../Main.java` (screen routing only),
  new screens in `core/.../screens/`, the flow model in `shared/.../shared/ui/` (new package).
- **Dependencies:** P0.
- **Steps:** (1) add the flow model and its tests; (2) add the stub screens; (3) wire the main
  menu; (4) keep `Play`'s connection path unchanged under Direct connect.
- **Tests:** `shared/src/test` — the flow model: every transitional state has a cancel and a
  timeout; Back from every stable screen reaches a defined screen; a late reply for an abandoned
  request is ignored. Written and not executed.
- **Acceptance:** from the main menu, every screen in §4 can be reached and left, and no screen is
  a dead end.
- **Risks:** Back behaviour is easy to get subtly wrong in a stateful UI. The flow model exists so
  the risk is in tests, not in play-testing alone.
- **Exit criteria:** the flow model's tests are written, every §4 path is covered by a test, and
  Direct connect behaves as before.

### P2 — Room data model and lifecycle (pure model, no networking)

- **Objective:** the room, member, join, host, start and cleanup rules of §6 as pure code.
- **Included:** `Room`, `RoomMember`, `RoomState`, the join-rule evaluator with its ordered refusal
  reasons, the host-transfer rule, the start-rule evaluator, the version counter, the code
  generator and the password hasher interface.
- **Excluded:** sockets, the server's threading, UI.
- **Files:** `shared/.../shared/room/` (new package).
- **Dependencies:** P0 (M1, M2, M3).
- **Steps:** (1) the room and member types; (2) the join evaluator with the ordered reasons; (3)
  the host and start evaluators; (4) the code generator; (5) the version rule.
- **Tests:** `shared/src/test` — each join refusal in its order; the duplicate join returns the
  existing seat; host transfer to the oldest connected member; the start rule with each unmet
  condition; the code alphabet has no look-alikes and the right length; a snapshot with an older
  version is refused. Written and not executed.
- **Acceptance:** every rule in §6 has a test that fails if the rule is removed.
- **Risks:** the model can drift from what the server does. P3 must use this model, not a copy.
- **Exit criteria:** the tests above exist and the model has no dependency on `server` or `core`.

### P3 — Backend room creation and discovery (server)

- **Objective:** the server holds rooms, creates them, and lists them.
- **Included:** the room registry on the server, `CreateRoomRequest` and its result, `RoomListRequest`
  and `RoomList`, `ServerInfoRequest` and `ServerInfo`, the room cleanup timers of §6, and the
  protocol bump with the new packets registered append-only.
- **Excluded:** joining (P5), the lobby roster (P6), host actions (P7), reconnect (P8).
- **Files:** `server/.../server/` (room registry, the hall from server plan S1), `shared/.../shared/net/`
  (packets, `NetworkRegistration`, `PROTOCOL_VERSION`), `server/src/test`.
- **Dependencies:** P2; server plan **S0** (the `ServerHost` split) and **S1** (the hall).
- **Steps:** (1) packets and registration with pins in `NetworkRegistrationTest`; (2) the registry
  using the P2 model; (3) create and list handlers; (4) the cleanup timers.
- **Tests:** `server/src/test` — create with valid and invalid fields; list contents and version;
  duplicate create requests; the room cap; the cleanup timers with a fake clock; the
  `NetworkRegistrationTest` pins. Written and not executed.
- **Acceptance:** a client can create a room and a second client's list shows it.
- **Risks:** the join path changes in the same protocol bump (§8). The old client's refusal must
  be tested, not assumed.
- **Exit criteria:** the server tests exist, the protocol version is bumped in the commit that adds
  the first packet, and the old-client refusal is tested.

### P4 — Room browser and create-room UI (client, wired to P3)

- **Objective:** the browser of §5.3 and the create form of §5.4, against the real server.
- **Included:** the browser list, search, filters, refresh, the detail panel, and every browser
  state (loading, populated, empty, failure, unavailable, stale); the create form with client-side
  validation and the server's rejection reasons.
- **Excluded:** joining (P5).
- **Files:** `core/.../screens/` (browser, create room), a client room-list model in `shared`
  for the filter and search logic (tested in `shared`).
- **Dependencies:** P1, P2, P3.
- **Steps:** (1) the filter and search model in `shared` with tests; (2) the browser screen; (3)
  the create form; (4) the state handling.
- **Tests:** `shared/src/test` — filter and search; "room still exists" selection retention;
  stale-list rule. Desktop: manual, multi-window (§14).
- **Acceptance:** a browser on one client lists a room created on another, and every browser
  state in §5.3 can be produced and left.
- **Risks:** list freshness under refresh — a race between a list and a create. The version rule
  of P2 applies to the list too.
- **Exit criteria:** every state in §5.3 and §5.4 is reachable in the desktop run.

### P5 — Joining rooms and private-room validation

- **Objective:** joining a public room, joining a private room with a password, joining by code,
  and every refusal reason of §6.
- **Included:** `JoinRoomRequest` and `JoinRoomResult`; the password prompt (§5.5); join by code
  (§5.6); the attempt limits (§9); refusal reasons on the client; cancellation of a pending join
  (the request id rule of §4).
- **Excluded:** the roster contents (P6), host actions (P7).
- **Files:** `server/.../server/` (join handler, attempt limiter), `shared/.../shared/room/` (the
  attempt limiter model), `core/.../screens/` (prompt and code entry), protocol registrations.
- **Dependencies:** P2, P3; M2 decided (accounts). **An accounts plan must exist before this phase
  starts** (§12, M2 consequence 1).
- **Steps:** (1) the join packets; (2) the server join handler using the P2 evaluator; (3) the
  attempt limiter; (4) the client prompt and code entry; (5) cancellation.
- **Tests:** `server/src/test` — each refusal reason in order; wrong password counts; lockout and
  its expiry with a fake clock; a duplicate join; two simultaneous joins for the last seat, one
  success and one "full". `shared/src/test` — the limiter model. Desktop: two clients, one wrong
  password then the right one. Written and not executed.
- **Acceptance:** a private room cannot be entered without its password, and a wrong password
  gives the attempts remaining.
- **Risks:** the password must never leak through logs or a snapshot. A test checks that the
  request is not logged in full.
- **Exit criteria:** the server tests above exist, and the "password never in a broadcast" test
  exists.

### P6 — Player roster and synchronised lobby state

- **Objective:** every client in a room sees the same roster, settings and ready states, and the
  room lobby of §5.8 works.
- **Included:** `RoomSnapshot` (and deltas if P0 decided them); `SetTeamRequest`,
  `SetReadyRequest`, `SetLoadoutRequest` and their refusals; the room lobby screen; the connection
  marks on the roster; the placeholder avatar (M4); the chat log for room events; the late-join
  snapshot.
- **Excluded:** host settings and start (P7), reconnect and host transfer (P8).
- **Files:** `server/.../server/` (room state broadcast), `shared/.../shared/room/` (snapshot
  codec and version apply), `core/.../screens/` (room lobby), protocol registrations.
- **Dependencies:** P5; server plan **S2** (teams, loadouts, ready).
- **Steps:** (1) the snapshot packet and its codec with tests; (2) the server broadcast on every
  change; (3) the team, ready and loadout requests; (4) the room lobby screen; (5) the
  version-ordered apply on the client.
- **Tests:** `server/src/test` — a change produces one snapshot per change, and a late joiner gets
  the full state; a team over its limit is refused; an unknown loadout is refused.
  `shared/src/test` — the snapshot's version rule. Desktop: three clients, ready toggles seen on
  all three. Written and not executed.
- **Acceptance:** three clients show the same roster, the same teams and the same ready marks
  within one tick.
- **Risks:** broadcast volume if a snapshot is sent on every change for a large room. The room
  limit in §9 keeps this small, and P0's decision on deltas is the mitigation.
- **Exit criteria:** the multi-client check in §14 passes on the desktop build.

### P7 — Host permissions, settings and match start

- **Objective:** the host controls the room, and a match starts for everyone at once.
- **Included:** `RoomSettingsRequest` (host only); the start and cancel-start requests; the
  countdown (§5.9); the loading timeout; the match-start handoff to the instance (server plan S3
  and S4); the host-only controls on the room lobby; the notices of §5.11 for settings changes.
- **Excluded:** kick and ban (P8), host transfer on departure (P8), the match layer itself (server
  plan S5).
- **Files:** `server/.../server/` (start handler, instance handoff), `shared/.../shared/room/`
  (start rule, already in P2), `core/.../screens/` (room lobby host controls, match starting).
- **Dependencies:** P6; server plan **S3** (map pool and vote) and **S4** (instances).
- **Steps:** (1) host-only settings with server checks; (2) the start request and the start rule
  re-check; (3) the countdown with a server-side end time; (4) the loading timeout; (5) the
  handoff to one instance per started room.
- **Tests:** `server/src/test` — a non-host settings change is refused; a start with an unmet
  requirement is refused with the requirement named; the countdown ends at the same server time for
  all members; a member who never loads is dropped at the timeout. Desktop: start from the host,
  all three clients enter together. Written and not executed.
- **Acceptance:** only the host can change settings or start; the start is refused with the unmet
  requirement shown; all members enter the match together.
- **Risks:** a race between a settings change and a start. The start re-checks every rule of §6 on
  the server, so a change that lands first is respected.
- **Exit criteria:** the tests above exist and the desktop start with three clients has been run.

### P8 — Connection loss, reconnect, host departure, room cleanup, kick and ban

- **Objective:** a dropped player does not lose their place, a departing host does not strand the
  room, and disruptive players can be removed.
- **Included:** the silence rule (`AWAY`) and the seat hold (§7); the reconnect path and the
  reconnect overlay (§5.10); the host transfer rule and its notice; room closure and cleanup (§6);
  `KickRequest` and `BanRequest` with the ban model (M6); the notices of §5.11.
- **Excluded:** quick match (P9), invitations (P10).
- **Files:** `server/.../server/` (silence watcher, seat hold, moderation), `shared/.../shared/room/`
  (ban model), `core/.../screens/` (reconnect overlay, notices), protocol registrations.
- **Dependencies:** P7; server plan **S6** (reconnect and host migration).
- **Steps:** (1) the silence watcher with a fake clock in tests; (2) the seat hold and the reconnect
  handshake; (3) the host transfer; (4) the room closure paths; (5) kick and ban; (6) the client
  overlay and notices.
- **Tests:** `server/src/test` — silent member becomes `AWAY` then is removed only at the end of the
  grace period; a reconnect within the grace restores team, loadout and ready; the host transfers
  to the oldest connected member; the room closes when nobody is connected; a kick removes the
  member; a ban refuses the rejoin; the cleanup removes an empty room. `shared/src/test` — the
  ban model's scope rules. Desktop: pull the network cable or kill one client, reconnect it. Written
  and not executed.
- **Acceptance:** each row of §5.10 and §5.11 can be produced and left in the desktop run.
- **Risks:** a silence rule that is too aggressive kicks real players on a slow network; too lenient
  leaves seats held for minutes. The §9 defaults are proposals to tune after P0 measures the ping.
- **Exit criteria:** the tests exist and the disconnect, reconnect and host-departure cases have been
  run on the desktop build with real clients.

### P9 — Quick matchmaking

- **Objective:** a player reaches a game with one press.
- **Included:** `QuickMatchRequest`, `QuickMatchCancel`, `QuickMatchStatus`; the server queue per
  mode; the join-or-create rule (§5.7, M7); the searching screen; the cancel and failure paths.
- **Excluded:** skill-based matching (no rating system exists; a later plan would need one),
  region-based matching (M9).
- **Files:** `server/.../server/` (queue), `shared/.../shared/room/` (the queue's pure rule),
  `core/.../screens/` (quick match screen), protocol registrations.
- **Dependencies:** P5, P6; M7 decided.
- **Steps:** (1) the pure queue rule in `shared` with tests; (2) the server queue; (3) the screen; (4)
  cancellation during the join.
- **Tests:** `shared/src/test` — the rule picks the open, not-full, not-in-match room of the mode,
  and creates one when none is open; a cancelled search never joins a room. `server/src/test` — two
  players searching together are placed in the same room; a cancel during the join leaves the player
  in no room. Desktop: three clients search, two join one room, the third creates the next one.
  Written and not executed.
- **Acceptance:** quick match places players in a room of the chosen mode, and cancel always
  returns to the menu.
- **Risks:** two searches for the last seat, which P5's ordering rule already resolves; and a queue
  that holds players forever if no room ever forms, which the timeout in §5.7 covers.
- **Exit criteria:** the tests exist and the three-client desktop run has been done.

### P10 — Friend invitations, LAN discovery and optional local network polish

- **Objective:** a player can find a LAN server without typing an address, and share a private room
  with a friend.
- **Included:** LAN discovery (a UDP broadcast on a separate port answered by servers, with a short
  list on the client); the invite flow for private rooms (share the code, copy to clipboard where the
  platform allows); the Direct connect screen's saved-server list (local to the client).
- **Excluded:** an invite inside the game for players who are not in the same room (needs identity,
  M2, and is not planned for this phase); accounts and friend lists.
- **Files:** `server/.../server/` (discovery responder), `core/.../net/` (discovery client),
  `shared/.../shared/net/` (discovery packet or datagram format), `core/.../screens/` (LAN list,
  invite).
- **Dependencies:** P3, P5; M8 for Android.
- **Steps:** (1) the discovery datagram format and its tests in `shared`; (2) the responder; (3) the
  client scan with a timeout; (4) the LAN list UI; (5) the invite text and copy.
- **Tests:** `shared/src/test` — the discovery datagram round trip and its size limit; `server/src/test`
  — the responder answers only the discovery format and ignores other traffic. Desktop: two machines
  on the same network, one hosts, the other finds it without typing. Written and not executed.
- **Acceptance:** LAN discovery finds a host on the same network within the scan timeout; a private
  code can be copied and used.
- **Risks:** some networks block UDP broadcast; the screen must say so, and Direct connect stays as
  the fallback. Discovery is never a security boundary; the join rules still apply.
- **Exit criteria:** the two-machine LAN run has been done, and the no-discovery fallback is shown on
  a network where discovery fails.

### P11 — Integration testing, performance and polish

- **Objective:** the whole flow works together, and it holds up under load and disconnects.
- **Included:** a **multi-client integration harness** (§14), the performance measurements of §14,
  the copy and layout pass, the accessibility pass (focus order, keyboard navigation for every menu
  action), and fixes from the desktop runs of P4–P10.
- **Excluded:** regional servers and any new feature.
- **Files:** `server/src/test` (the harness, if it lives there), the screens as needed.
- **Dependencies:** P1–P10.
- **Steps:** (1) build the harness; (2) run the scenarios of §14; (3) measure; (4) fix and re-run; (5)
  polish.
- **Tests:** the full list in §14.
- **Acceptance:** every checkbox in §13 is ticked by a human on the desktop build, or marked as not
  attempted with the reason.
- **Risks:** the harness is new infrastructure, and the repo has no test that boots a server today
  (the map plan records this). Building it is a real piece of work, not a formality.
- **Exit criteria:** the §14 suites pass in CI, and the desktop runs are recorded.

**Deferred, not in P0–P11:** regional servers and connection-quality display (§14), a server list
service, accounts and friends, skill-based matchmaking, and in-game invitations to non-members.

## 12. Decisions

**Decided (2026-10-12, by the user):**

- **M1 — per-server room list.** Each server lists its own rooms. The browser shows the rooms
  of the server the player is connected to. There is no registry in v1, and reaching another
  server is done through Direct connect. Regional servers stay deferred (M9). The P4 decision
  is taken.
- **M2 — accounts.** Players log in with an account, not a typed guest name. Consequences,
  which the phases must respect:
  1. **P5 cannot start until an accounts plan exists.** That plan is written as
     `docs/ACCOUNTS_PLAN.md`. Its first two inputs are decided: username and password login, and
     a login service the user self-hosts. Its phases A0–A6 come before P5. The rest of the
     paragraph is the original consequence:
     part of this one. Until it exists, the guest-name rules in §3, §5.5 and §10 do not apply;
     they are kept only as the record of what was considered.
  2. **A login service is new and sits outside the game server.** It is a separate process with
     its own storage and secrets. The game server accepts session tokens that the login service
     issues. Who hosts it, and where, is for the accounts plan and the user's infrastructure.
  3. **Password storage, token issuance and expiry, account recovery, and what personal data the
     service keeps** are decisions for the accounts plan. None is taken here.
  4. **Bans become account-keyed**, which is much stronger than the session-and-name key of §10.
     Friends and invitations to named players become possible; P10's invitations can use
     accounts.
  5. **This sandbox cannot host or reach an external login service**, so any account integration
     test needs the user's infrastructure. The automated tests in §14 can cover the game server's
     token handling with a stub issuer.
- **M5 — everyone ready to start.** The host can start only when every member is ready. There is
  no host override. The start rule in §5.8 and §6 applies as written.
- **L1 (server plan) — one process, many rooms.** This is the server plan's Option A. Its slices
  S0 to S7 apply unchanged.

**Still open:** M3, M4, M6, M7, M8, M9, and the nine open decisions L2–L10 in the server plan.
The recommendations below are unchanged for those.

- **M1 — (decided; see above) Where the public room list comes from.** *Original recommendation:* each server lists its own rooms
  (the browser shows the rooms of the server the player is connected to, and direct connect is the
  way to reach another server). The alternative is a registry service that aggregates many servers.
  Blocks P4.
- **M2 — (decided: accounts; superseded) Identity.** *Original recommendation:* guest names with per-session tokens, as §3. Accounts are a
  separate plan. Blocks P5 onward.
- **M3 — Host transfer.** *Recommended:* the oldest connected member becomes host, with a notice. The
  alternative: the room closes whenever the host leaves. Blocks P8.
- **M4 — Avatars.** *Recommended:* a placeholder (initials on a coloured tile). There is no avatar
  system, and adding one needs assets and storage. Blocks P6.
- **M5 — (decided; see above) Starting with unready members.** *Original recommendation:* the host can start only when everyone is
  ready. The alternative: the host can start with unready members after a warning. Blocks P7.
- **M6 — Bans and mid-match joins.** *Recommended:* a ban is per session, plus the name as a weaker
  key; the address is not used as a key (it is shared and changes). Mid-match joins are refused
  (server plan L6). Blocks P8.
- **M7 — Quick match when no room is open.** *Recommended:* create a public room with the mode's
  defaults and wait. The alternative: show "no room found" and offer Browse rooms. Blocks P9.
- **M8 — Android in scope now.** *Recommended:* decide after P0 confirms the Android networking path.
  Blocks P10.
- **M9 — Regional servers.** *Recommended:* not in v1. The room entry carries an empty `region`
  field so a later registry needs no migration. Blocks nothing in P0–P11.

## 13. Acceptance (all unchecked; none is executable in this sandbox)

- [ ] The main menu leads to the multiplayer menu, and every screen in §4 can be reached and left
  (P1).
- [ ] Back and Cancel work on every screen, and no transitional state is a dead end (P1).
- [ ] The room browser lists rooms created on another client, shows the fields of §5.3, and
  refreshes (P4).
- [ ] Every browser state in §5.3 can be produced: loading, populated, empty, connection failure,
  unavailable, and stale (P4).
- [ ] A room can be created as public or private with a password, and every field is validated
  before and by the server (P4).
- [ ] A public room is joined from the browser; a private room is joined with its password, and a
  wrong password shows the attempts remaining (P5).
- [ ] A private room is joined by code, and an unknown or expired code is refused with its reason
  (P5).
- [ ] A full room, a closed room, a room in a match and an incompatible version are each refused with
  the right reason (P5).
- [ ] Three clients show the same roster, teams, ready marks and settings (P6).
- [ ] Only the host can change settings, kick, ban, start or close, and a non-host attempt is refused
  (P7, P8).
- [ ] The start is refused with the unmet requirement named, and all members enter the match together
  (P7).
- [ ] A player who disconnects keeps their seat for the grace period and returns to the same team,
  loadout and ready state (P8).
- [ ] The host leaving transfers the host to the next member with a notice, or closes the room when
  nobody is left (P8).
- [ ] An empty room is removed after its grace period (P8).
- [ ] Quick match places three players in a room of the chosen mode, and Cancel always returns to the
  menu (P9).
- [ ] LAN discovery finds a host on the same network, and the fallback is shown where discovery
  fails (P10).
- [ ] The whole flow, from the main menu through a lobby into a match and back, runs on the desktop
  build with three real clients (P11).

## 14. Testing and completion criteria

The test plan separates four kinds of test. None of them has been run.

**Automated, in CI** (`shared/src/test` and `server/src/test`, written and not executed here):

- Room rules: every join refusal in order, the duplicate join, host transfer, the start rule, the
  code alphabet, the version rule, and the ban scope (P2, P8).
- Flow model: every transitional state has a cancel and a timeout, and every §4 path is covered
  (P1).
- Protocol: round trips for every new packet, the `NetworkRegistrationTest` pins, and the
  old-client refusal (P3, P5).
- Concurrency: two simultaneous joins for the last seat, duplicate requests returning the original
  reply, and a late broadcast with an older version being refused (P2, P5, P6).
- Security: wrong-password counting, lockout expiry with a fake clock, the password never appearing
  in a broadcast or a log line, and a non-host action being refused (P5, P7).
- Lifecycle: the silence rule, the seat hold, the reconnect restore, and room cleanup with a fake
  clock (P8).
- Matchmaking: the queue rule, two players placed together, and a cancelled search leaving the
  player in no room (P9).
- Discovery: the datagram round trip, its size limit, and the responder ignoring other traffic (P10).

**Multi-client integration (new infrastructure; P11).** The repo has no test that boots a server and
drives clients over sockets, and the map plan records this. The harness is a headless server in
`server/src/test` with real Kryo clients over loopback. It covers the flows of §13 that do not need
a window: create, browse, join, password, roster, ready, start, disconnect and reconnect, host
departure, and quick match. It runs in CI and is written and not executed here.

**Manual, desktop (real client instances):** the user runs the desktop build with three or more
windows against one server and works through §13 in order, recording each result against the phase
that produced it. Every window is a separate client process, not a mock.

**Manual, local network:** two machines on the same network. One hosts; the other finds it through
LAN discovery (P10) and, separately, by typing the address (Direct connect). Also: a network with UDP
broadcast blocked, to see the fallback.

**Manual, internet:** a server reachable from outside the local network (port forwarding or a hosted
machine), joined from another network. This is not automated and is not claimed to work until it has
been run. NAT traversal is not planned.

**Performance:** room list response time with a populated server (a number of rooms set in the test
plan), roster broadcast volume for a full room, and the memory and CPU of one idle room against one
in a match. No figures are claimed here; the measurements are P11's output.

**Completion:** the phases are complete when each phase's acceptance criteria hold, the §14 suites
pass in CI, §13 is ticked by a human on the desktop build (or each unticked box has a recorded
reason), and the open decisions in §12 that a shipped phase depends on are recorded as decided.

## 15. Risks and failure cases

| Risk | Where it bites | Mitigation |
| --- | --- | --- |
| The join path changes shape (enters the hall, not the world) | P3 | Protocol bump in the same commit; old-client refusal tested |
| Back and Cancel leave a request half-done | P1, P4, P5 | Request ids; late replies ignored; flow-model tests |
| A stale room list shows a room that is gone | P4 | Version rule; join refuses with "room no longer available" |
| Two players take the last seat at once | P5 | Serialised mutations; one success, one "full" |
| The password leaks through a log or a snapshot | P5 | Hashing; log redaction; a test that scans the output |
| Silence rule kicks real players on a slow network | P8 | Conservative default; tuned after P0's ping measurement |
| A host leaves and strands the room | P8 | Transfer rule; closure when nobody is connected |
| Guest names let a banned player return | P8 | Session key plus name; the room limit as the backstop; admin override |
| The browser is slow with many rooms | P4, P11 | Server-side cap and paging in P3; measured in P11 |
| The room lobby's broadcast volume grows with room size | P6, P11 | Small room limit; delta decision in P0 |
| UDP broadcast is blocked, so LAN discovery fails | P10 | Visible fallback; Direct connect remains |
| The harness is new and hard to build | P11 | It is scoped to flows without a window; it is the first thing P11 builds |
| A phase is too big to review | all | One concern per commit; no two unrelated phases at once |
| The multiplayer screens drift from the server's rules | P4–P8 | The shared model is the one place the rules live; screens render it |

## 16. Desktop re-run script (per phase, on the user's build)

1. Pull the phase's branch. Confirm the seven checks' output in the commit message.
2. Start the desktop client with debug on if the phase needs a debug entry (`SKYSTRIKE_DEBUG=1`,
   `-Dskystrike.debug=true`, or `--dev`), using the usual run task. Start a server with the usual
   server task.
3. Open two or three more client windows (or machines, for P10) connected to the same server.
4. Run the phase's acceptance boxes from §13 in order, and record each result.
5. Record every difference from this plan as a finding against the phase. Do not change the plan
   silently.
