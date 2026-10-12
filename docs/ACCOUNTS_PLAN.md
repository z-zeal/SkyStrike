# Accounts and login — plan

**Status: plan only.** Nothing in this document is implemented. Nothing in it has been compiled,
run or observed. This sandbox has no JDK and no Gradle, and it cannot host or reach an external
login service, so every phase is to be written and not executed, with live testing on the user's
own infrastructure. Every acceptance box in §11 is unchecked.

This plan is the prerequisite the multiplayer plan names: `docs/MULTIPLAYER_MENU_PLAN.md` §12,
M2, says **P5 (joining rooms and passwords) cannot start until this plan's first phases exist.**

## 1. Decisions already taken

- **Login method: username and password.** Players create an account with a username and a
  password. The login service stores a hash of the password. The game client sends the password
  only at login, over an encrypted connection.
- **Hosting: self-hosted by the user.** The login service runs on a machine the user controls,
  alongside the game server. The user owns uptime, backups and the stored data.
- **Code location (A3): a separate project inside this repo**, outside the Gradle build.
- **Recovery (A1): operator reset only**, with no email collected in v1.
- **Accounts are per player, not per device.** A player logs in from any client.
- **Per-server room lists** (multiplayer plan M1) and **one game server process running many rooms**
  (server plan L1) are unchanged by accounts.

## 2. Terms

- **Login service** — the new process that creates accounts, checks passwords and issues tokens.
  It is not the game server, and the game server never stores a password.
- **Game server** — the existing server. It accepts a token and decides what the token allows.
- **Access token** — a short-lived, signed statement "this session belongs to account X until
  time T". The game server verifies it without calling the login service for every request.
- **Refresh token** — a longer-lived secret the client keeps to obtain new access tokens without
  asking for the password again. It is revocable.
- **Account** — a username, a password hash, a display name, a creation time, a ban state, and a
  role (player or admin).

## 3. Scope

**In:** registration, login, logout, token issuance and refresh, token verification in the game
server, password change, username and display-name rules, account lockout after repeated failures,
bans and admin roles stored against accounts, and an operator command to reset a password.

**Out (for this plan):** external sign-in providers (not chosen), email addresses and password
recovery by email (see §8, A1), friend lists, payments, age checks, and any account data beyond
what §5 lists.

## 4. Architecture

```
Client ──HTTPS──► Login service ──(issues tokens)──► Client
Client ──game protocol, token in JoinRequest──► Game server
Game server ──verifies token locally with the login service's PUBLIC key──► accept or refuse
```

- **Protocol between client and login service:** HTTPS with a small JSON API. The game's own
  network protocol (Kryo over TCP and UDP) is not used for login, so the login path is separate
  from the protocol version. Login messages have their own version number.
- **Why HTTPS and not the game port:** a standard request/response with TLS, rate limiting and
  logging is the well-trodden path for passwords. Putting passwords on the game's custom protocol
  would mean building that security ourselves.
- **Token signing:** the login service signs access tokens with a **private key it keeps**. The
  game server holds only the **public key** and verifies signatures locally. A compromised game
  server cannot mint tokens. (Decision A2 fixes the algorithm; Ed25519 is the recommendation.)
- **Revocation:** access tokens are short-lived (proposed 15 minutes), so revocation of a banned
  account takes effect within that window. Refresh tokens are stored by the login service and can
  be revoked at once. A ban also closes live game sessions on the next check (§7).

## 5. Data the service keeps

| Field | Why | Kept for |
| --- | --- | --- |
| Username (unique, case-insensitive) | Login and identity | Account lifetime |
| Display name | Shown in rooms | Account lifetime |
| Password hash (Argon2id, or the decided alternative) | Login | Account lifetime |
| Created at, last login at | Support and abuse review | Account lifetime |
| Ban state (until when, reason, by whom) | Enforcement | Ban lifetime, then kept as a record |
| Role (player, admin) | Permissions | Account lifetime |
| Refresh tokens (hashed) | Session management | Until expiry or revocation |
| Failed-login counters | Lockout | The lockout window only |

**Not kept:** email addresses (none collected in v1), IP addresses beyond the rate-limit window,
and any gameplay history. A decision on IP logging for abuse review is A7.

## 6. Account rules

- **Username:** 3 to 20 characters; letters, digits and underscore; unique without regard to case;
  a short list of reserved names (for example names that look like admin commands).
- **Display name:** defaults to the username; 1 to 20 characters; may contain spaces; may be
  changed at most once per day (proposed).
- **Password:** at least 12 characters (proposed); no composition rules that push users to weak
  patterns; checked against a short list of the most common passwords; never stored or logged in
  plain form; never echoed back by any API.
- **Lockout:** 5 failed logins for an account within 15 minutes locks that account for 15 minutes
  (proposed). The lockout message does not say whether the password was right.
- **Rate limits:** per IP and per account on login and registration (proposed: 10 attempts per
  minute per IP). Limits are enforced by the login service before any password check.
- **Account enumeration:** registration and login responses must not reveal whether a username
  exists beyond what registration must say (a taken name is refused at registration, which is
  unavoidable without a larger design; the login response is generic).
- **Logout:** revokes the client's refresh token. Existing access tokens expire on their own.

## 7. Game server integration

- **Join request:** `PacketJoinRequest` (the multiplayer plan's P3/P5 changes) carries the access
  token. The game server verifies the signature, the expiry, and the account's ban state (the ban
  state is in the token's claims, so the check is local; a ban applied after the token was issued
  takes effect when the token expires, or sooner if the server asks the login service, decision A4).
- **Identity in the game:** the account id is the stable identity. The display name is shown; the
  account id is used for bans, host transfer, kicks and admin actions.
- **Admin role:** the existing permission model reads the role from the token's claims, so admin
  commands work only for accounts that the operator has made admins in the login service.
- **Protocol change:** `PacketJoinRequest` gains a token field. This is a breaking change, so the
  protocol version bumps in the same commit, and an old client is refused with a message that says
  it must update. The bump belongs to the multiplayer plan's protocol sequence (§8 there), so the
  two plans must share one bump or two adjacent ones; decision A8.
- **Offline servers:** a game server does not need the login service to be reachable to verify a
  token, because verification is local. It does need the public key, which it loads from a file or
  configuration.

## 8. Decisions

- **A1 — Email and recovery. Decided (2026-10-12): operator reset.** No email is collected in v1,
  so a forgotten password is recovered by the operator (a command that sets a new password and
  revokes the account's sessions). Email-based recovery is a later decision that would add a mail
  provider and a recovery flow.
- **A3 — Where the login service code lives. Decided (2026-10-12): (b), a separate project inside
  this repo**, with its own build outside the Gradle modules, so it stays out of the game's checks
  and module graph. Option (a), a Gradle module, and option (c), a separate repository, were the
  alternatives.
- **A2 — Token algorithm.** *Recommended:* Ed25519 signatures, with access tokens of 15 minutes and
  refresh tokens of 30 days, rotated on each use.
- **A3 — Where the login service code lives.** Three options:
  - (a) a new Gradle module inside this repo, under the module-boundary rules (it must not depend on
    `core` or `server`);
  - (b) a separate project in this repo with its own build, outside the Gradle build;
  - (c) a separate repository.
  *Recommended:* (b) for v1, so it does not enter the game's build, its checks or its module graph.
- **A4 — Ban propagation.** Whether a ban takes effect immediately on live sessions (the game server
  asks the login service on each check) or at token expiry (up to 15 minutes later). *Recommended:*
  token expiry for v1, plus an operator command that closes a banned account's live sessions.
- **A5 — Language and password hashing.** A Java login service can use the JDK's PBKDF2 without a new
  dependency; a Python one would use `argon2-cffi`, which is installable from the package index the
  sandbox can reach. *Recommended:* decide with A3. Argon2id is the choice for either.
- **A6 — TLS.** The login service must be served over HTTPS. The client must trust the user's
  certificate. *Recommended:* a certificate from a public authority if the service has a domain name;
  a pinned certificate if it does not. Plain HTTP is refused in shipping builds.
- **A7 — IP logging for abuse review.** Whether to keep addresses beyond the rate-limit window.
  *Recommended:* no, for v1.
- **A8 — Protocol bumps.** The accounts change and the multiplayer plan's join changes both touch
  `PacketJoinRequest`. *Recommended:* one bump, landed by whichever plan's join change comes first, with
  the other plan's changes appended. This needs the user's agreement on the sequence.

## 9. Phases

Each phase is one PR, one concern per commit. Code before docs. The seven repo checks run before
each commit, with real output quoted. Do not implement two unrelated phases at once.

### A0 — Decisions and skeleton (no behaviour)

- **Objective:** A1–A8 are decided or confirmed, and the login service's location and language are set.
- **Included:** recording the decisions here; choosing A3 and A5; creating an empty skeleton in the
  chosen location with its own README and no logic.
- **Excluded:** every endpoint and every game-server change.
- **Dependencies:** none. The user's answers to A1–A8.
- **Tests:** none.
- **Exit criteria:** every decision is recorded; the skeleton builds nothing that the game depends on.

### A1 — Registration and login

- **Objective:** a player can create an account and log in through the login service.
- **Included:** the registration endpoint with the username and password rules of §6; the login
  endpoint with the generic failure response; password hashing; the failed-login counters and the
  lockout; the rate limits of §6; storage of the fields in §5.
- **Excluded:** tokens for the game (A2 phase), the client, bans.
- **Dependencies:** A0.
- **Tests:** unit tests of the username and password rules, of hashing (a stored hash verifies, a
  wrong password does not), of the lockout's start and expiry with a fake clock, and that the
  failure response is identical for a wrong password and an unknown username. Written and not
  executed.
- **Acceptance:** an account can be created and logged in against a locally run service on the
  user's machine.
- **Risks:** timing differences that reveal whether a username exists. The login path must do the
  same hashing work whether or not the username exists.
- **Exit criteria:** the tests exist, and the enumeration timing has been checked on the user's
  machine.

### A2 — Tokens and key management

- **Objective:** the login service issues access and refresh tokens; the game server can verify an
  access token with the public key alone.
- **Included:** token issuance and signing (A2); the refresh flow with rotation; logout revoking the
  refresh token; the public-key export and its file format; a test key pair generator for tests.
- **Excluded:** the game server's verification in the join path (A4 phase).
- **Dependencies:** A1.
- **Tests:** a signed token verifies with the public key and fails with a different key or a changed
  claim; an expired token is refused; a refresh token is single-use after rotation; logout makes the
  refresh token unusable. Written and not executed.
- **Acceptance:** a token from the login service verifies with the public key on another machine.
- **Risks:** key rotation. Recorded as a later task: the game server must accept the current and the
  previous key during a rotation window.
- **Exit criteria:** the token tests exist, and key handling is written down for the operator.

### A3 — Game server verification (touches the multiplayer join)

- **Objective:** the game server refuses a join without a valid token, and admin commands read the
  role from the token.
- **Included:** the token field on `PacketJoinRequest` (protocol bump, with the sequencing of A8); the
  server's verifier using the public key; the account id as the player's identity; the ban check on
  the claims; admin role from claims; a refusal reason for an invalid or expired token.
- **Excluded:** the client UI for login (A4 phase).
- **Dependencies:** A2; A8 agreed; the multiplayer plan's P3 for the protocol bump's sequence.
- **Tests:** `server/src/test` — a join with no token, an expired token, a token with a changed claim,
  and a banned account are each refused with the right reason; a valid token is admitted; the
  protocol pins for the new field. Written and not executed.
- **Acceptance:** a client without a valid token cannot join; an admin token gets admin commands and a
  player token does not.
- **Risks:** the join path is the most-used path in the game, so this phase is the most likely to
  break existing play. The old-client refusal must be tested.
- **Exit criteria:** the tests exist, and a join with a real token has been run on the desktop build.

### A4 — Client login screens

- **Objective:** a player can register, log in, log out, and change their password from the game.
- **Included:** the login screen, the registration screen, an account line in the multiplayer menu
  (name, logout), the password change screen, the refresh-token storage on the client (protected by
  the platform's secure storage where available; the choice of storage is an open point for A0), and
  the errors of §6 shown as text.
- **Excluded:** recovery (operator only, A1), friends, avatars.
- **Dependencies:** A3; the multiplayer plan's P1 (the menu shell).
- **Tests:** `shared/src/test` — the client's login state model (logged out, logging in, logged in,
  token expired and refreshing, refresh failed) with each transition. Desktop: register, log in, log
  out, log in again. Written and not executed.
- **Acceptance:** every login state in the model can be produced and left on the desktop build.
- **Risks:** a refresh token stored in plain text on a shared machine. The client must say where it
  is stored and offer logout that deletes it.
- **Exit criteria:** the state-model tests exist, and the desktop flow has been run.

### A5 — Bans, roles and operator tools

- **Objective:** the operator can ban accounts, make admins, and reset passwords from the command line.
- **Included:** a command-line tool for the login service (ban with a reason and an end time, unban,
  set role, reset password and revoke sessions, list accounts); the ban applied to the tokens issued
  after it; the live-session closure of A4 (decision).
- **Excluded:** a web admin panel.
- **Dependencies:** A1, A2, A3.
- **Tests:** each operator command against a test database; a banned account cannot refresh; a
  password reset revokes the account's refresh tokens. Written and not executed.
- **Acceptance:** an operator can ban and unban an account and see the effect in the game.
- **Risks:** the operator tool is itself a privileged path. It must run on the service's machine and
  must log every action.
- **Exit criteria:** the tests exist, and the operator commands are documented for the user.

### A6 — Hardening and review

- **Objective:** the login path is reviewed against the threat list in §10.
- **Included:** rate-limit checks under load; a review of every error message for enumeration; the
  TLS configuration; a check that no secret is in a log line; backup and restore of the account store
  tested on the user's machine.
- **Excluded:** new features.
- **Dependencies:** A1–A5.
- **Tests:** a log scan for passwords and tokens; the rate limits under a burst; a restore from backup
  reproduces the accounts. Written and not executed, except where run on the user's machine.
- **Acceptance:** every item in §10 has an entry saying how it is mitigated and how it was checked.
- **Exit criteria:** the review is recorded.

## 10. Threats and mitigations

| Threat | Mitigation | Checked in |
| --- | --- | --- |
| Password guessing | Rate limits per IP and per account; lockout; strong-password minimum | A1, A6 |
| Username enumeration by timing or message | Same hashing work and same message for unknown and wrong | A1 |
| Stolen database | Argon2id (or the decided alternative) hashes; no plaintext; refresh tokens stored hashed | A1, A2 |
| Forged token | Signatures with a private key only the login service holds | A2, A3 |
| Stolen refresh token | Rotation on use; revocation on logout and reset; 30-day lifetime | A2, A5 |
| Banned player keeps playing | Ban in claims; short token lifetime; session closure command | A3, A5 |
| Passwords in logs or crash reports | Logging rules; a log scan | A6 |
| Man in the middle on login | HTTPS only; certificate handling in the client | A6 |
| Operator error on the login machine | Backups; logged operator commands; documented key handling | A5, A6 |
| Credential stuffing from known leaks | Common-password check; rate limits | A1, A6 |

## 11. Acceptance (all unchecked; none is executable in this sandbox)

- [ ] An account can be registered with a valid username and password; an invalid one is refused
  with the rule it broke (A1).
- [ ] A wrong password and an unknown username give the same message (A1).
- [ ] Five failed logins lock an account for the proposed window, and the lock expires (A1).
- [ ] A login returns an access token and a refresh token; the refresh token rotates on use (A2).
- [ ] The game server admits a join with a valid token and refuses one without, expired, or altered
  (A3).
- [ ] A banned account cannot join and cannot refresh (A3, A5).
- [ ] An admin role grants admin commands in the game, and a player role does not (A3).
- [ ] Register, log in, log out and log in again work in the desktop build (A4).
- [ ] The operator can ban, unban, make an admin and reset a password from the command line (A5).
- [ ] A password reset revokes the account's sessions (A5).
- [ ] The account store restores from a backup on the user's machine (A6).

## 12. Desktop and live re-run (per phase, on the user's infrastructure)

1. Run the login service on a machine the user controls, with HTTPS and a test certificate.
2. Create two test accounts with the registration screen; log in with each from a separate client.
3. Join a game server with a valid token; then try a join with an expired token (by waiting out the
   lifetime, or with a token issued in the past by the test tool).
4. Ban one account with the operator tool; confirm the join is refused and the refresh fails.
5. Record each result against its phase. Do not change this plan silently.
