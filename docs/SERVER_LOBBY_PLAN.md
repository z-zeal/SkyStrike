# Server, lobbies and match instances — plan

**Status: plan only.** Nothing in this document is implemented. Nothing in it has been compiled,
run or observed: this sandbox has no JDK and no Gradle, so every slice is to be written and not
executed, with the first real run in CI and on the user's desktop build. Every acceptance box in
§11 is unchecked.

This plan changes how a session starts. It does not change the map builder plans
(`MAP_BUILDER_PLAN.md`, `MAP_BUILDER_V2_PLAN.md`), but it depends on their map-distribution
decision (V2 D5) and on the map pool that V2-7 introduces. Where it does, §8 says so.

## 1. What the user asked for

The session flow becomes:

1. **Start a server** (a local server, or a dedicated one).
2. **Create a lobby** on it.
3. **Select a map** (or vote on one from a pool).
4. **Start the game.**

Inside a lobby, players choose their **team**, their **initial loadout**, and **vote on maps**,
in the manner of a competitive shooter such as Rainbow Six Siege.

Several lobbies and several running games must exist at once, on the same server. Example the
user gave:

| Lobby | Players | State |
| --- | --- | --- |
| Lobby 1 | 4 | playing |
| Lobby 2 | 3 | in the lobby, not yet playing |
| Lobby 3 | 4 | playing |

Two game instances run (lobbies 1 and 3). Lobby 2 exists, has players, and is in setup. Nothing
about lobby 2's players depends on lobbies 1 and 3, and a lobby that has finished a match returns
to setup rather than closing.

## 2. What the code does today

These are the facts this plan is built on, each checked against the tree.

- **One `GameServer` is one match.** `server/.../GameServer.java` (908 lines) owns one `ArenaMap`,
  one `TickLoop`, one `NetworkEndpoint`, one `ConnectionRegistry`, one `PlayerRegistry`, and one
  set of world systems (bullets, utilities, doors, drones, cameras, damage, loadouts, chat,
  commands). Every one of those is per-server, not per-match.
- **Join is a single step, straight into the world.** `JoinRequestHandler` registers the player
  and spawns them. Teams are chosen by the server (`SpawnService.selectBalancedTeam` returns team
  0 or 1 from the counts). The client cannot pick a team, and there is no step before spawning.
- **Loadouts exist only in play.** `LoadoutSystem` runs in-game, and `PacketLoadoutUpdate` is a
  client-to-server packet (`shared/.../net/c2s/`). Nothing chooses a loadout before the first spawn.
- **There is no match layer.** `PacketRoundReset` and the `/mapreset` command are the only
  round-related hooks. The first plan's §11.2 records that "the match layer that decides a round
  has ended is still later work". There are no rounds, scores, win conditions, or round timers.
- **There is no lobby, no map vote, no ready state.** A search of the tree for "lobby" finds
  nothing.
- **The client menu** (`MainMenuScreen`, `Main`) offers **Play**, which connects to a typed
  host, and, in a debug build only, **Local test**, which starts an embedded host and joins it.
  As `MAP_BUILDER_PLAN.md` records, `Main.startLocalTest` joins an existing server when its own
  host cannot bind, which is the "two processes on different maps" hazard the first live run hit.
- **Packets are queued, then drained on the tick.** `NetworkEndpoint` holds inbound events in a
  `ConcurrentLinkedQueue`. The world is mutated on the tick, not on the network thread. An
  instance can therefore own its own inbound queue, and routing a packet to an instance is a
  queue choice rather than a locking problem.
- **The only mutable static state in `server` and `shared` is `DebugFlags`.** Two world
  instances in one process do not share gameplay state. (`ConnectionRegistry`, `PlayerRegistry`
  and the chat and command services are per-`GameServer` instances, not statics.)
- **Protocol 18 is the current wire.** `NetworkRegistration` registers 40 types, append-only.
  Any new lobby packet appends and bumps `PROTOCOL_VERSION`, in the same commit as the first
  packet.
- **Maps are one embedded text, loaded in six places** (see the V2 plan). Each instance will
  need a map; the server must hold a pool of them.

## 3. Reference behaviour (Rainbow Six Siege)

The user named Rainbow Six Siege as the model. The mechanisms this plan borrows, with sources:

- **Map ban at the start of a lobby.** "A Map Ban phase lets players vote out maps at the start
  of a lobby" ([2](https://www.wikihow.com/Siege-Ranks)). Ranked draws "5 random maps in the
  rotation" and players vote which to ban ([3](https://rainbowsix.fandom.com/wiki/Ranked)).
- **Alternating ban turns, with a default pick.** A tournament format bans in alternation,
  then the last remaining map is played, with the first team choosing its starting side
  ([1](https://help.generationesports.com/hc/en-us/articles/360060622832-HSEL-Rainbow-Six-Siege-Rules)).
- **Side swaps.** Ranked "teams will swap Attacker and Defender roles at the fourth round"
  ([3](https://rainbowsix.fandom.com/wiki/Ranked)); the tournament rules set a swap every six
  rounds ([1](https://help.generationesports.com/hc/en-us/articles/360060622832-HSEL-Rainbow-Six-Siege-Rules)).
- **Round-based matches with a round count and a round timer.** Ranked rounds are three
  minutes ([3](https://rainbowsix.fandom.com/wiki/Ranked)).
- **Pre-match pick.** Both teams choose and ban an attacker and a defender operator before the
  match ([3](https://rainbowsix.fandom.com/wiki/Ranked)). This plan does **not** include an operator pick:
  the equivalent here is the loadout (§5.3), which is an open decision (L4).
- **Abandon penalties.** Leaving a ranked match gives a 30-minute ban that escalates
  ([3](https://rainbowsix.fandom.com/wiki/Ranked)). Out of scope for v1 (L8).

The numbers above are references, not defaults. The plan sets its own (§9) and asks for the
user's values where it matters.

## 4. Concepts

- **Server** — one process, listening for clients. It holds the lobby registry, the map pool,
  the player sessions, and the running instances.
- **Session** — one connected client, with a stable identity across lobbies (§7). A session is
  in the server's hall, in one lobby, or in one instance. A session is never in two.
- **Lobby** — a persistent room with a name, a map pool, a team setup, a list of members and
  their loadouts, and a state (§6). A lobby outlives its matches.
- **Instance** — one running match for one lobby: an `ArenaMap` (shared, read-only), a tick
  loop, a world, and per-match mutable state (`MapDamageState`, doors, bullets, drones, cameras,
  the match layer). An instance exists only while its lobby is in the `InGame` state.
- **Hall** — the list of lobbies a session sees before it joins one.

## 5. Lobby features

### 5.1 Creating and finding a lobby

- The hall lists lobbies with their name, map (or map pool), player count, max players, and
  state (`Setup`, `Starting`, `InGame`).
- **Create lobby** takes a name, a maximum player count, a team size rule, a map pool, and a
  round count. Visibility (public or invite-only) is decision L7.
- **Join lobby** is refused when the lobby is full, is `InGame`, or is `Starting`. A player
  joining a lobby that is `InGame` waits in the hall (L6).

### 5.2 Teams

- Each member chooses team 0 or team 1 in `Setup`. Team sizes are capped by the lobby's team
  size rule.
- The server still owns the outcome. A team choice is a request; the server refuses it when the
  team is full and says so.
- A member who has not chosen is placed on the smaller team when the lobby starts, which is the
  rule `SpawnService.selectBalancedTeam` already uses.
- Teams lock when the lobby starts. A team switch during `Starting` is refused.

### 5.3 Loadouts

- Each member picks a loadout in `Setup`: the weapons and melee the loadout carries, drawn from
  the generated weapon tables (89 guns, 21 melee at the last check). The client sends an id, and
  the server validates it against the same catalog before storing it.
- The stored loadout is the one `LoadoutSystem` uses at spawn. The "initial loadout" the user
  asked for is therefore the loadout chosen in `Setup`, applied at the first spawn of the match.
- A loadout that refers to an unknown weapon is refused, never silently replaced.
- Whether loadouts are free choice or budgeted is decision L4.

### 5.4 Map selection and voting

- The server has a **map pool** (a list of map ids from its configuration). A lobby chooses
  from the pool, and the pool is the only source of maps a lobby may play.
- Voting is decision L2. The recommended form is a Rainbow Six-style ban phase at the start of
  the lobby: members ban maps in turns, and the last map remains (or, with more than one map
  left when time runs out, a vote decides). A simple majority vote over the pool is the
  alternative.
- A map that is not in the pool, or that a client does not have, is refused by the same join
  gate that refuses a mismatched map today (protocol 16, `JoinRequestHandler.mapRefusal`).

### 5.5 Ready and start

- Each member toggles **ready**. The lobby's host (or an admin) may **start** once the minimum
  player rule (L9) is met.
- Starting moves the lobby to `Starting`, runs a short countdown, then creates the instance and
  moves every member into it at their team's spawn.

### 5.6 Chat and commands in the lobby

- Lobby chat goes to the lobby's members only. Match chat goes to the instance's players.
- The command layer runs per scope: lobby commands in a lobby, match commands in an instance.
  Permissions stay as they are (`PermissionResolver`), and admin remains global.

## 6. Lobby and instance states

```
             create                    host starts
   (none) ─────────► Setup ───────────────────────► Starting ──► InGame ──┐
                       ▲   members join/leave,          countdown        │ match ends
                       │   teams, loadouts, map vote,                      │
                       │   ready                                           ▼
                       └─────────────────────────────────────────── PostMatch
                                 (returns to Setup, same members,
                                  optional side swap, same or new map)
```

- **Setup** — members come and go; teams, loadouts, map and ready are editable.
- **Starting** — teams and loadouts are locked; a countdown runs; the instance is created.
- **InGame** — the instance runs its rounds (§8). No one joins; a member who leaves is out
  of the match (L6 decides whether they may rejoin later).
- **PostMatch** — the result is shown; the lobby returns to Setup with the same members.

**The user's example, as states:** lobby 1 and lobby 3 are `InGame` with an instance each;
lobby 2 is `Setup` with three members and no instance. A lobby in `Setup` costs no tick loop.

## 7. Architecture

### 7.1 Topology (decision L1)

- **Option A (recommended for v1): one process, many instances.** One listener (TCP and UDP, as
  today). A `ServerHost` owns sessions, the hall and the lobby registry. Each `InGame` lobby owns
  one `GameInstance`, which owns its world and its tick loop. Packets are routed to the instance
  through its own inbound queue. Broadcasts are scoped to the instance's sessions, not the whole
  server.
- **Option B (later): one process per instance.** A lobby host process starts child processes
  per match and gives them ports. It isolates crashes and scales across cores, at the cost of
  port management and a second protocol between the host and the children.

Option A is chosen because it keeps today's single-process model and reuses the existing queue.
It is also the option with a real risk (§10): one bad instance must not stop the others, which
Option B gets for free.

### 7.2 The split

The main refactor is to separate `GameServer` into two parts:

- **`ServerHost`** — the network endpoint, the session table, the hall, the lobby registry, the
  map pool, and the host-level services (permissions, admin commands, global chat, capability
  broadcast).
- **`GameInstance`** — everything `GameServer` does per match today: the `ArenaMap` reference,
  `MapDamageState`, the tick loop, the world systems, the per-match player registry, the
  instance's chat and kill feed, and the match layer (§8).

`ArenaMap` is shared and immutable; each instance gets a reference, never a copy of the
mutable state. `MapDamageState` and the door machine are per instance, which is what makes
destruction "per round" and per match, as the first plan's §11.2 decided.

The split is slice S0, and it is a refactor with no behaviour change: one lobby, one instance,
the same packets. Its gate is that the existing server tests still describe the same behaviour.

### 7.3 Ticking many instances

- Each `GameInstance` has its own `TickLoop` state. Whether they share one scheduler thread or
  each have a thread (L1, and §10) is decided by a measurement, not by this plan. The profiling
  hook already in `ServerConfig.profileIntervalSeconds` is the instrument.
- An exception in one instance's tick is caught at the instance boundary: the instance is closed
  with a message to its members, and every other instance keeps running. This is the one
  property Option A has to earn.

### 7.4 Sessions and identity

- Player ids are per `GameServer` today. A session needs an id that is stable across lobbies and
  matches, so a member can move from `Setup` to `InGame` and back without re-registering.
- A lost connection holds the session's seat for a grace period (L6 decides the length, and
  whether a seat in an `InGame` lobby is held at all).

## 8. The match layer (a dependency, not optional)

Without rounds, a lobby can only run a free-play instance. The lobby's `InGame` state needs:

- A **round** with a timer, a start (spawn all members at their team's spawn with their loadouts),
  and an end (a win condition is met or the timer runs out).
- A **win condition** per round (for example, eliminate the other team, or a bomb-style objective;
  the choice of objective is decision L5 and is not implied by the Rainbow Six reference).
- A **score** and a **match end** after the configured round count, with an overtime rule if
  wanted.
- **Side swaps** at configured rounds, for lobbies that use attacker and defender.
- The existing **round reset** (`PacketRoundReset`, protocol 18) becomes the instance's per-round
  reset, so destruction and doors reset each round, as decided.

The match layer is built per instance, so the S5 slice (below) can be tested with fake clients
without the lobby work.

## 9. Defaults this plan proposes

These are starting values for the user to change, not decisions:

| Setting | Default | Source of the default |
| --- | --- | --- |
| Max players per lobby | 8 (4 per team) | Mirrors the 4 + 4 example |
| Team size rule | Equal teams, auto-fill to the smaller | `SpawnService.selectBalancedTeam` |
| Round count | 5 (first to 3) | Reference only; the tournament rule is 12 rounds |
| Round length | 3 minutes | Reference only |
| Minimum players to start | 2 (one per team) | Keeps the example runnable with 3 |
| Map ban phase | Alternating, last map remains | §5.4 |
| Countdown before start | 10 seconds | Plan default |
| Reconnect grace | 60 seconds | Plan default |

## 10. Risks

- **The refactor is the largest risk.** `GameServer` is 908 lines and owns about two dozen
  systems. S0 must be a pure move with the existing tests as its proof.
- **A bad instance must not stop the others** (§7.3). This is a property to test, with a fake
  instance that throws, not an assumption.
- **Per-instance cost is unknown.** Nothing in the tree measures how many instances one process
  can tick at the target rate. The number has to come from the profiling hook on the user's
  hardware. This plan does not claim a limit.
- **Threading.** The queue-then-tick model is the reason routing is cheap. Any slice that lets a
  packet touch world state directly, instead of through the queue, breaks the model and must say
  so.
- **Map distribution.** Every client in a lobby must have the lobby's map. The join gate refuses
  a mismatch, so a missing map fails loudly. Whether the server ships maps (V2 D5 option c) or
  each side has the files (option b) is shared with the map builder plan.
- **Protocol churn.** Lobby packets change the join sequence: today a join enters the world, and
  after S1 it enters the hall. That is a breaking change, so the protocol bump and the old
  client's refusal must be in the same commit as the first packet.
- **Local test.** "Local test" becomes "start a server and create a lobby". Its old
  join-on-bind-failure behaviour must go with it, not survive beside the new flow.
- **Scope.** This plan is large. The slices below are ordered so that each one is usable on its
  own, and the lobby can ship before the match layer is finished.

## 11. Slices

Each slice is one PR, one concern per commit. Code commits come before docs commits. Each slice
runs the seven repo checks (`tools/scratch/static_check.py`, `static_api_check.py`,
`static_audio_check.py`, `synth_sfx.py --check`, `phase3_ballistics.py`,
`weapons/generate_weapon_tables.py`, `arena_sdf_check.py`) before each commit, with real output
quoted. Protocol changes come with `NetworkRegistrationTest` pins updated in the same commit.

- **S0 — Split `GameServer` into `ServerHost` and `GameInstance`.** Pure refactor, one instance,
  no packet change, no protocol change. Gate: existing server tests unchanged and green in CI.
- **S1 — Hall and lobby room (no match).** Connect to a server, list lobbies, create one, join,
  leave, chat in the lobby, and see the member list. Lobby packets append; protocol 19; the join
  sequence now enters the hall. Gate: server tests for the lobby state machine's `Setup` state,
  and `NetworkRegistrationTest` pins for the new packets.
- **S2 — Teams, loadouts and ready.** Choose a team, choose and validate a loadout, ready up,
  and host start. Start creates one instance with the chosen teams and loadouts. Gate: tests
  that a team over its limit is refused, that an unknown weapon id is refused, and that the
  instance spawns members at their team's spawn with their loadout.
- **S3 — Map pool and vote.** The pool from configuration, the ban phase (L2), and the chosen
  map passed to the instance. Depends on the map builder's map-file work (V2-7) for more than
  one map; with one map in the pool it works on the standard arena alone. Gate: the ban
  sequence in a test, and the join gate refusing a map outside the pool.
- **S4 — Concurrent instances.** Several lobbies `InGame` at once in one process, each on its own
  instance, with broadcasts scoped per instance. Gate: a server test that runs the user's
  example, two instances and one `Setup` lobby, with fake clients, and checks that a packet for
  one instance never reaches another. The crash-isolation test (§7.3) is part of this slice.
- **S5 — Match layer.** Rounds, the round timer, win conditions, score, match end, and side
  swap (§8). Gate: round-state tests with fake clients; the desktop run for feel and timing.
- **S6 — PostMatch and return to lobby, reconnect, host migration, kick.** Gate: tests for the
  return to `Setup` with the same members, and for a reconnect within the grace period.
- **S7 — Separate-process instances (Option B), only if the measurement in §10 asks for it.**

S0 and S4 carry most of the risk. S1 and S2 are the first parts the user will see, and they can be
tested in a running game before any match layer exists.

## 12. Acceptance (all unchecked; none is executable in this sandbox)

- [ ] A server starts with no game running and shows an empty hall (S1).
- [ ] A client creates a lobby, and a second client sees it in the hall and joins (S1).
- [ ] Members choose teams; a full team refuses a new member; a switch is reflected to everyone
  in the lobby (S2).
- [ ] Members choose a loadout; an unknown weapon is refused; the chosen loadout is what spawns
  (S2).
- [ ] Members ready up; the host starts; the instance begins with the chosen teams and the
  chosen map (S2, S3).
- [ ] The map ban phase runs in order and leaves one map; a map outside the pool is refused
  (S3).
- [ ] Two lobbies can be `InGame` at the same time on one server, with chat and packets kept
  apart (S4).
- [ ] A lobby in `Setup` with three members coexists with two `InGame` lobbies without starting
  an instance (S4).
- [ ] A crash in one instance closes that instance and leaves the others running (S4).
- [ ] A round ends by its win condition or timer; the score carries to the match end; destruction
  and doors reset at the next round (S5).
- [ ] A match ends and the lobby returns to `Setup` with the same members (S6).

## 13. Desktop re-run script (per slice, on the user's build)

1. Pull the slice's branch. Confirm the seven checks' output in its commit message.
2. Start the desktop client with debug on (`SKYSTRIKE_DEBUG=1`, `-Dskystrike.debug=true`, or
   `--dev`), using the usual run task. Start a server with the usual server task.
3. Connect a second client to the same server (a second window, or a second machine on the same
   network).
4. Run the slice's boxes from §12 in order. For S4, create the three lobbies from the example
   and confirm the states shown in the hall match the table in §1.
5. Record any difference from this plan as a finding against the slice, not as a silent change.

## 14. Open decisions

- **L1 — Topology.** One process with many instances (recommended), or one process per instance?
- **L2 — Map voting.** A ban phase in turns (recommended, Rainbow Six style), or a single
  majority vote over the pool?
- **L3 — Team sizes and max players.** The defaults in §9 are proposals. What should they be?
- **L4 — Loadouts.** Free choice from the catalog (recommended for v1), or a budget per member?
- **L5 — Win conditions.** Eliminate the other team per round, or an objective? The current
  game has no objective, so this is a design choice, not a port.
- **L6 — Joining and rejoining mid-match.** Refused (recommended for v1), or allowed as a
  spectator or a late join? And how long is a seat held after a disconnect?
- **L7 — Visibility.** Public hall only, or invite-only lobbies as well?
- **L8 — Abandon penalties.** Out of scope for v1, or a basic version from the start?
- **L9 — Minimum players to start.** Two (one per team, as in §9), or a full lobby?
- **L10 — Shared map files.** Does the server ship maps to clients (V2 D5 option c), or does each
  side have the files (option b)? This plan's S3 assumes the second until the first is chosen.

## 15. References

1. [HSEL Rainbow Six Siege rules: map veto and match settings](https://help.generationesports.com/hc/en-us/articles/360060622832-HSEL-Rainbow-Six-Siege-Rules)
2. [Rainbow Six Siege ranks guide: map ban and pick-and-ban phases](https://www.wikihow.com/Siege-Ranks)
3. [Rainbow Six Siege wiki: Ranked differences from Quick Match](https://rainbowsix.fandom.com/wiki/Ranked)
