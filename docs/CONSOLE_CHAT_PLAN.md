# Console & Chat System — Build Plan

> One dialog, one key. A button toggles ALL ↔ TEAM.
> The console is not a mode — it is always live for anyone with permission.
> Text must stay readable over a dark, fog-of-war game.

---

## 1. GOALS

1. **One widget, one key.** Chat and console share the same panel, scrollback, input bar, history and completion. A single key opens it. There is no second key to learn.
2. **A button toggles the chat target** between ALL and TEAM. No modal key presses, and it works on touch without a keyboard.
3. **The console is ambient, not modal.** For a player with permission, a leading `/` makes the current line a command. There is no console mode to enter or leave.
4. **Permission gates the console entirely.** Without it, there is no command parsing, no command completion, and no engine channels in the scrollback.
5. **Text is always readable** — over black fog, over a white explosion, over a flashbang whiteout, on a 1080p monitor and a 5-inch phone.
6. **Adding a command is trivial** — declare a spec, write a handler, register it. Nothing else to edit.
7. **The server is authoritative.** Chat is relayed and validated server-side. Privileged commands execute on the server, never on a client's claim.

---

## 2. STATE MODEL

The whole system holds exactly **two pieces of state**:

| State | Values | Owner |
| --- | --- | --- |
| `chatTarget` | ALL or TEAM | Client, toggled by the button, persisted between sessions |
| `consoleAccess` | granted or not | **Server**, pushed to the client on join and whenever it changes |

There is deliberately **no mode enum**. Earlier designs model chat and console as modes you switch between; that creates a state machine, a transition table, and a whole class of bugs where the UI shows one mode while the input routes to another.

Instead, **whether a line is a command is derived from the line itself**:

> The current input is a command if, and only if, the player has console access **and** the text begins with a single `/`.

That is the entire rule. It is recomputed as the player types, it cannot desynchronise from what will actually happen on submit, and it needs no transitions.

---

## 3. THE DIALOG

### 3.1 Opening and closing

- **One key opens it** — a single rebindable "Open Chat" action, default `Enter`. On touch, an on-screen chat button does the same thing.
- `Enter` submits and closes. `Enter` on an empty input closes without sending.
- `Escape` closes without sending, preserving the draft so reopening restores it.
- The opening keystroke is consumed and must never appear in the input field.

### 3.2 The target toggle button

A small button at the left of the input bar, inside the panel, showing the current target:

| Target | Button label | Accent colour | Routes to |
| --- | --- | --- | --- |
| ALL | `ALL` | White | Every connected player |
| TEAM | `TEAM` | The player's team colour | Own team only |

- **Clicking or tapping it toggles** between the two. That is the primary interaction and the only one required.
- **`Tab` is a keyboard accelerator** for the same toggle, because reaching for the mouse mid-match to switch to team chat is unacceptable on desktop. The button is the model; `Tab` is a shortcut to it.
- The choice **persists** across openings and across sessions. A player who lives in team chat should not have to re-select it every time.
- A 3-pixel accent stripe down the left edge of the input bar carries the same colour, so the target is readable peripherally without focusing on the button.

### 3.3 Command styling is live feedback, not a mode

When a player with console access types a leading `/`:

- The target button **dims and is disabled** — the line is going to the dispatcher, not to a chat channel, so the target is irrelevant.
- The accent stripe and the caret turn **amber**.
- A `>` glyph replaces the target button's content.
- The **hint line** appears above the input bar showing the usage of the command being typed, filling in live as arguments are entered.

Delete the leading `/` and everything reverts instantly. Nothing was entered and nothing needs leaving.

**Escape hatch:** a line beginning `//` is sent as chat with a single literal leading slash. Standard convention, and it costs one line of parsing.

### 3.4 Anatomy

Bottom-anchored, left-aligned, height a fraction of the viewport so it scales across devices:

- **Scrollback area** — recent lines, newest nearest the input bar. Scrollable by wheel, `PageUp`/`PageDown`, and touch drag. A position indicator appears when scrolled up, and new lines must not yank the view back down while the player is reading.
- **Input bar** — target button, accent stripe, editable text, caret, and a character counter that appears only within 20 characters of the limit.
- **Completion popup** — above the input bar when the current token has candidates. Only ever present for command lines.
- **Hint line** — one dim line showing live command usage. Only present for command lines.

### 3.5 Passive mode

When closed, the panel does not disappear. Recent chat and system lines stay in the same screen position without a background panel or input bar, fading after a few seconds. Opening restores the panel and un-fades everything.

Players should never have to open a box to discover someone was talking to them.

---

## 4. CONSOLE PERMISSION

### 4.1 The capability

Console access is a **server-granted capability**, resolved from the player's authenticated identity, delivered in the join-accept packet and re-pushed whenever it changes (a promotion mid-match must take effect immediately, with a system line confirming it).

The client **requests nothing**. It receives the flag and reflects it. A modified client that sets the flag locally gains exactly nothing, because every privileged command is authorised again on the server at execution time (§7.5).

### 4.2 What changes without permission

For a player without console access:

- A leading `/` is **ordinary text**. There is no command parsing, no dispatcher call.
- Submitting `/anything` sends it as a chat message like any other line.
- The completion popup and hint line never appear.
- The Console, Command-echo and Debug channels are **absent from the scrollback** — not greyed out, not collapsed, absent.
- The target button never dims, because there is no command state to enter.

The console is not disabled-looking. It is **invisible**. A player without access should have no indication the feature exists — which also means an unauthorised player cannot probe for which commands exist.

### 4.3 Setting the threshold

The capability is derived from the player's permission level against a server-configured threshold. Shipping default: **Moderator and above**, so ordinary players have no console at all, as specified.

Two things worth knowing when tuning this later:

- Dropping the threshold to Player grants everyone a console whose *registry is still filtered by permission* — so they would see only client-side commands (`help`, `clear`, `fps`, `quality`, `bind`, `mute`) and no privileged ones. That is a reasonable future policy for a settings-heavy game, and it needs no code change, only a different threshold.
- Whatever the threshold, the **dedicated server's own terminal always runs at Admin**, since whoever holds that console already controls the machine.

---

## 5. TEXT VISIBILITY

The requirement most likely to be got wrong, because this game is deliberately very dark in places and occasionally pure white.

### 5.1 Render position

**The dialog draws in the HUD pass — last.** After the scene, after fog compositing, after additive overlays, after bloom, after the blindness/flash overlay, after the damage vignette.

Rules that follow:

- Fog of war must never darken chat text.
- A flashbang must never white out chat text. The world whites out; the UI does not. Being blinded is a gameplay state; losing the ability to read "enemy pushing B" is a UI failure. If the blindness needs to feel more total, dim the **panel background** — never the glyphs.
- Camera shake must not move the dialog. It is screen-space and shake-exempt.
- Screen-space distortion (shockwave refraction, heat haze) must not displace it.

### 5.2 Four layers of contrast

Text must survive both a black background and a white one. One technique is not enough:

1. **Backing panel** — a dark rounded rectangle at roughly 65% opacity behind scrollback and input bar. In passive mode, replaced by a short horizontal gradient strip behind each line so text never floats on raw scene.
2. **Glyph outline** — a 1-pixel dark outline baked by the font loader, not faked by drawing the string five times. This alone handles most of the white-on-white case.
3. **Drop shadow** — 1-pixel offset below-right at ~50% opacity, adding separation from busy backgrounds.
4. **Minimum luminance floor** — every text colour, including team colours and name colours, is clamped to a minimum perceptual brightness and contrast-checked against the panel. A dark-blue team colour is lifted before it is ever used for glyphs.

Validate with a debug mode rendering the dialog over four backgrounds: pure black, pure white, mid grey, and high-frequency noise. Readable on all four means readable in the game.

### 5.3 Font

- **Signed-distance-field or runtime-generated bitmap fonts** at the actual target pixel size. Never upscale a bitmap font — outlines degrade into mush.
- **Regenerate on resolution and DPI change.** Both desktop window resizing and mobile rotation must trigger it.
- **Size is a fraction of viewport height, clamped** — roughly 2.2%, floored at 12 physical pixels, capped at 28.
- **Hard floor of 12 physical pixels**, below which outline and antialiasing fight and legibility collapses.
- Full Latin-1 plus the arrow and box glyphs the UI uses. Decide early about CJK; if needed, plan dynamic glyph pages rather than one enormous atlas.

### 5.4 Line formatting

- **Word wrap** at panel width, continuation lines indented to align under the message body so wrapped text reads differently from a new message.
- **Channel colour coding:** All white, Team in team colour, System amber, Error red, Command echo dim grey, Success green.
- **Structured layout:** optional timestamp, channel tag, author name in team colour, body in white. Colour only the name — tinting whole messages by team costs readability for nothing.
- **Never allow markup in user text.** Players will use it to render invisible or screen-filling messages. Strip all formatting codes server-side before broadcast.

---

## 6. INPUT CAPTURE AND FOCUS

The classic failure: open chat, type "wasd", and watch your character run into a wall while the box also fills with letters.

1. The dialog sits at the top of a layered input stack and **consumes** every key event while open — consumed, not observed. Gameplay polling is gated by one "is a modal UI focused" check, not by each system remembering to ask.
2. Gameplay input is **polled**; UI input is **event-driven**. Never let a polled key check run while the dialog has focus.
3. While open, the mouse must not fire and the scroll wheel must not cycle slots — the dialog claims both. Scroll drives scrollback.
4. On open, **explicitly zero the movement intent** sent to the server, so a key held at the moment of opening does not strand the player running.
5. On mobile, opening raises the soft keyboard and the panel repositions above it.

---

## 7. THE TWO SUBSYSTEMS

### 7.1 Shared scrollback

One ring buffer serves both. This is the heart of "same dialog box".

- A ring of **structured line records**, not formatted strings: timestamp, channel, author, raw body, colour override, severity. Formatting happens at draw time, so a resize re-wraps correctly and an accessibility setting can recolour retroactively.
- **Capacity 512**, oldest evicted — long enough to scroll back through a firefight, bounded enough never to leak.
- **Channel visibility follows permission**, per §4.2. Chat, System and Server are always visible. Console, Command echo and Debug require console access.
- A **wrapped-line cache**, invalidated on resize or font regeneration.
- It is the **only** sink for user-visible text — chat, command output, engine logs, kill notifications and system announcements all land here. One sink means one place to filter, colour and test.

### 7.2 Chat

**Flow.** A client never displays its own message directly. It sends to the server; the server validates, sanitises and broadcasts; the client displays what returns. Every transcript is then identical, and the sender sees exactly what everyone else sees, including truncation. Optionally show a dim local echo immediately and reconcile when the authoritative copy arrives, marking it failed on timeout.

**Channels.** All (everyone), Team (sender's team), System (server notices), Server (operator broadcast), Debug (local only, never networked).

**Team scoping is enforced server-side** from the sender's authoritative team. A client must never filter team chat for itself — that leaks enemy comms to anyone running a modified build.

**Validation, all server-side, all before broadcast:**

- Length cap of 120 characters — truncate, do not reject.
- Strip control characters, newlines, tabs, zero-width characters, bidirectional override characters and all markup. Bidi overrides in particular are a known way to garble other players' UI.
- Rate limit via token bucket, roughly 3 messages per 5 seconds, with a short mute on sustained abuse. Limit breaches produce a private system line to the sender only.
- Drop identical consecutive messages from the same sender within a few seconds.
- Drop empty and whitespace-only messages silently.
- Sanitise names at **join** time, not message time.
- Optional profanity masking with a client-side toggle.
- A local, persisted **mute list** that suppresses rendering without notifying anyone.

**Packets.** Client → server: channel plus raw body. Server → client: channel, author id, display name, team, sanitised body, server timestamp. System messages reuse the server-to-client packet with a null author, so there is exactly one display path.

### 7.3 Console

**The registry.** One registry maps names and aliases to command definitions, populated by **explicit registration at startup** — not classpath scanning or annotation reflection. Reflective discovery breaks under native-image builds, needs extra configuration on Android, and hides the command set from static analysis. Explicit registration costs one line and is portable everywhere.

**What a command declares.** A declarative spec plus a handler. The spec carries everything the rest of the system needs:

| Field | Drives |
| --- | --- |
| Name and aliases | Lookup, completion |
| Description | `help` listing |
| Usage string | Hint line, error messages |
| Argument list (name, type, required, default, completion source) | Parsing, validation, per-argument completion, generated usage |
| Permission level | Access control and registry filtering |
| Execution side | Routing |
| Handler | Behaviour |

Because usage, help and completion are **derived from the spec**, they cannot drift from the implementation — the usual failure mode of hand-maintained help text.

**Argument types.** Integer (ranged), float (ranged), boolean (`true/false`, `1/0`, `on/off`, `yes/no`), string, quoted string, greedy string, enum, player (by id or name), team, weapon id, vector2, duration (`30s`, `5m`). Each supplies its own parser, validator, completion source and error message. Adding a domain type like "gadget slot" is itself a registration.

**Adding a command — the three-step recipe**, and the measure of whether this design worked:

1. Create one small file in the owning system's `commands/` folder with the spec and handler.
2. Add one registration line in that system's command module.
3. Done — help, usage, completion, validation, coercion, permission checks and errors all follow from the spec.

No parser edits, no switch statements, no help file, no completion table. If a command ever needs a second file touched, the abstraction has leaked.

**Execution sides.** Client-side commands (rendering toggles, local settings) run immediately and are never networked. Server-side commands (teleport, kick, spawn, rule changes) are sent as a **raw line** and **re-parsed from scratch by the server**. Client-side validation is a convenience for the user, never a security boundary.

**Permissions.** Four levels: Everyone, Player, Moderator, Admin. The server resolves the caller's level from authenticated identity, never a client claim. Unauthorised attempts return a generic refusal — not "you need admin", which merely confirms the command exists. The registry is also **filtered by level for listing and completion**, so a user never sees a command they cannot run.

**Console variables.** Register typed, range-checked cvars alongside commands: name, type, default, range, description, scope, and whether changes apply immediately. They share the registry, completion and help, so there is no distinction for the user to learn. This is where the console earns its keep in development — particle budgets, fog falloff, shader quality tiers, tick rate, debug overlays. Setting one with no value prints current, default and description.

**Starter set.** Client: `help`, `clear`, `bind`, `unbind`, `fps`, `netgraph`, `quality`, `volume`, `mute`/`unmute`, `screenshot`, `disconnect`. Server: `kick`, `ban`/`unban`, `mute`, `teleport`, `give`, `setteam`, `setammo`, `sethealth`, `respawn`, `killall`, `say`, `tickrate`, `players`, `restart`. Debug: `noclip`, `godmode`, `showhitboxes`, `showsdf`, `showfx`, `spawnfx`, `timescale`, `dumpstate`.

**Discoverability.** `help` lists commands grouped by category, filtered to the caller's level. `help <command>` prints description, usage, every argument with type and default, and examples. Tab completion works at every position. An unknown command yields a **"did you mean"** suggestion by edit distance — the single feature that eliminates most "the console is broken" reports. History persists across sessions, deduplicated, navigable with up/down.

### 7.4 One registry, three front-ends

The in-game console, the dedicated server's standard input, and remote admin over the network all drive the **same registry**. A command written once works in all three. The server terminal runs at Admin; the other two authorise per caller.

---

## 8. WHAT IS GENUINELY SHARED

| Shared | Notes |
| --- | --- |
| The dialog widget | Panel, input bar, caret, scroll, animation |
| The scrollback ring | Channel-tagged, permission-filtered |
| Formatting and wrapping | Including the wrapped-line cache |
| Font management | Generation, DPI regeneration, outline, shadow |
| Input capture and focus | One focus owner, one gameplay gate |
| Input history | One ring, chat and command entries tagged |
| Completion popup | The widget; candidate *sources* differ |
| Text sanitisation | Same rules for chat bodies and string arguments |
| Rate limiting | Same token bucket, different budgets |

Separate: the submit route (relay versus dispatcher), the completion sources, and the channel filter.

---

## 9. MOBILE

- The single open key becomes a single on-screen chat button. Because the ALL/TEAM switch is **a button rather than a second key**, touch needs no special case — this is the main practical win of the revised design.
- Raise the soft keyboard on open; reposition the panel above it rather than letting it be covered.
- Completion candidates are tappable; history gets on-screen up/down arrows.
- A **quick-chat wheel** of preset phrases ("Enemy spotted", "Need backup", "Pushing") is close to mandatory on touch. Presets route through the normal chat path and respect the current ALL/TEAM target.
- The 12-physical-pixel font floor matters most here.

---

## 10. FILE STRUCTURE

```
shared/text/
├── ChatChannel              ALL, TEAM, SYSTEM, SERVER, DEBUG
├── ChatTarget               ALL, TEAM — what the toggle button selects
├── ChatMessage              Wire-safe message record
├── TextSanitizer            Strip control chars, markup, bidi overrides; clamp length
├── RateLimiter              Token bucket, reused by chat and commands
└── TextLimits               Length caps, rate budgets, buffer sizes

shared/command/
├── CommandSpec              Name, aliases, description, usage, args, permission, side
├── CommandArg               Name, type, required, default, completion source
├── ArgType / ArgTypes       Built-in types: parse, validate, complete
├── CommandRegistry          Lookup, permission-filtered listing, fuzzy suggestion
├── CommandParser            Quote-aware tokeniser and spec-driven coercion
├── CommandResult            Success/failure plus output lines
├── CommandContext           Caller identity, permission level, side, output sink
├── Permission               EVERYONE, PLAYER, MODERATOR, ADMIN
├── ConsoleAccess            Threshold policy: does this level unlock the console?
└── Cvar / CvarRegistry      Typed variables sharing command lookup

shared/net/
├── PacketChatRequest        Client → server: target + raw body
├── PacketChatMessage        Server → client: channel, author, team, body, timestamp
├── PacketCommandRequest     Client → server: raw command line
├── PacketCommandResponse    Server → client: output lines + severity
└── PacketCapabilities       Server → client: console access and permission level

server/command/
├── ServerCommandService     Parse, authorise, execute
├── ServerConsole            Standard-input front-end, always Admin
├── PermissionResolver       Authenticated identity → permission level
├── CapabilityBroadcaster    Pushes console access on join and on change
└── commands/                One file per command, plus one registration module

server/chat/
├── ChatService              Validate, sanitise, rate-limit, scope, broadcast
├── ChatModeration           Mutes, bans, masking
└── ChatHistory              Optional transcript for moderation

core/ui/console/
├── ConsoleDialog            The shared widget — panel, input bar, scroll, animation
├── ChatTargetButton         The ALL ↔ TEAM toggle, with Tab accelerator
├── ConsoleInputField        Caret, editing, history, live command detection
├── CommandLineDetector      Derives "is this a command?" from text + access
├── ConsoleCompletionPopup   Candidate list, click and tap selection
├── ConsoleHintLine          Live usage for the command being typed
├── ConsoleTheme             Colours, metrics, accent stripes, contrast clamping
└── ConsoleFocus             Focus ownership and the gameplay input gate

core/ui/text/
├── MessageBuffer            The shared 512-line ring
├── MessageLine              Structured record
├── MessageFormatter         Layout, colour coding, contrast clamping
├── TextWrapper              Width-aware wrapping with cache
└── FontManager              Runtime generation, DPI regeneration, outline, shadow

core/chat/
├── ChatClient               Send, receive, local echo reconciliation
├── ChatMuteList             Local, persisted
└── QuickChat                Preset phrase wheel for touch

core/command/
├── ClientCommandService     Execute locally or forward raw line to server
├── ClientCapabilities       Cached console access and permission level
├── CompletionSources        Player names, weapon ids, cvars, enum constants
└── commands/                One file per command, plus one registration module
```

---

## 11. BUILD ORDER

**Phase 0 — Text foundation.** `FontManager` with outline and shadow, `MessageBuffer`, `MessageLine`, `TextWrapper`, `MessageFormatter`, and the four-background contrast test mode.
*Gate:* legible over black, white, grey and noise; re-wraps correctly on resize and DPI change.

**Phase 1 — The dialog.** `ConsoleDialog`, `ConsoleInputField`, `ConsoleFocus`, `ChatTargetButton` with `Tab` accelerator and persistence, passive fade-out, scrolling.
*Gate:* one key opens it; the button toggles ALL ↔ TEAM and the choice survives a restart; no keystroke leaks to gameplay; closing never strands the player moving.

**Phase 2 — Chat.** Packets, `ChatService` with sanitisation, rate limiting and server-side team scoping, `ChatClient` with local echo.
*Gate:* two clients exchange ALL and TEAM chat; team chat is invisible to the other team even with a modified client; spam is throttled; oversized and malformed messages handled.

**Phase 3 — Capabilities.** `Permission`, `ConsoleAccess`, `PermissionResolver`, `PacketCapabilities`, `CapabilityBroadcaster`, `ClientCapabilities`.
*Gate:* a normal player sees no console at all — `/` is literal text, no engine channels in scrollback; promoting them mid-match enables it immediately without a reconnect.

**Phase 4 — Command core.** `CommandSpec`, `ArgType`, `CommandParser`, `CommandRegistry`, `CommandContext`, `CommandLineDetector`, plus `help` and `clear`.
*Gate:* typing `/` live-restyles the input bar and disables the target button; `//` escapes to literal chat; `help` is generated entirely from specs; malformed input gives a precise error.

**Phase 5 — Completion and history.** Per-argument tab completion, live hint line, fuzzy "did you mean", persistent deduplicated history.
*Gate:* completing a player argument lists connected players; a typo suggests the right command; completion never offers a command the caller cannot run.

**Phase 6 — Server commands.** `ServerCommandService`, request/response packets, the server command set, server-side re-parsing.
*Gate:* an unprivileged client cannot execute a privileged command by any means, including hand-crafted packets with a forged capability flag.

**Phase 7 — Cvars.** `Cvar`, `CvarRegistry`, unified lookup, cvars exposed for FX quality tiers and debug overlays.
*Gate:* changing a particle budget or fog parameter live takes effect immediately.

**Phase 8 — Server terminal.** `ServerConsole` driving the same registry at Admin.
*Gate:* every server command behaves identically from the terminal and from an in-game admin console.

**Phase 9 — Mobile and polish.** Soft keyboard handling, quick-chat wheel, touch completion, mute list, profanity masking.
*Gate:* a full chat exchange, including switching to team, is comfortable on a phone with no physical keyboard.

---

## 12. DEFINITION OF DONE

- One key opens the dialog. There is no second key for team chat and no key for the console.
- A button toggles ALL ↔ TEAM, works by touch, has a `Tab` accelerator, and persists across sessions.
- There is **no mode enum** anywhere. Whether a line is a command is derived from the text plus the access flag, recomputed as the player types.
- A player without console access has no console: `/` is literal text, no completion, no hint line, and no engine channels in scrollback.
- Console access is granted by the server, pushed on join and on change, and takes effect mid-match without reconnecting.
- A forged capability flag on a modified client grants nothing, because the server re-parses and re-authorises every command.
- Text is legible over pure black, pure white and high-frequency noise, verified by the test mode.
- A flashbang whites out the world and leaves chat perfectly readable.
- Adding a command takes exactly one new file and one registration line, and yields help, usage, completion, validation and errors automatically.
- Team chat cannot be read by the other team even with a modified client.
- The in-game console and the dedicated server terminal run the same registry.