# Debug local-test acceptance

## Scope

The main menu may expose a **Local test** action for a desktop build running with the debug master
switch enabled (`--dev`, `-Dskystrike.debug=true`, or the existing debug environment switch). Other
platforms use `Main()` without a desktop host adapter, so the action is absent there. This is a
local-development convenience; it does not change the wire protocol or ordinary Play flow.

## Acceptance checklist

- [ ] With debug mode off, the desktop menu shows no Local test action. With debug mode on, it is
      directly below Play.
- [ ] Selecting Local test starts a `GameServer` using the default `ServerConfig` and waits no more
      than five seconds for the server to report that both transports have bound.
- [ ] Readiness is checked through the embedded server's bound state, not by opening a throwaway
      TCP connection, so the readiness check does not enqueue a synthetic client connection.
- [ ] The client then uses the ordinary `ConnectingScreen`, join handshake, and `GameScreen` path
      against `127.0.0.1` and the default TCP/UDP ports.
- [ ] If those ports are already occupied, the client still tries the normal local connection; it
      may join the server that already owns the defaults. If no compatible server is present, the
      normal connection screen reports the failure.
- [ ] Disposing the desktop application requests shutdown of its embedded host. Shutdown is
      idempotent and the host worker is a daemon thread.
- [ ] `ServerConfig.fromArgs(new String[0])` continues to select default settings; no packet or
      protocol registration changes are introduced.

## Verification note

The code path has been reviewed by inspection only. The game, desktop build, Gradle, and a JDK were
not run in this environment; the acceptance checklist still needs runtime verification.
