# Vision, gunfire and drone feel — acceptance note

**Status: unexecuted.** Manual runtime checks for the three reported issues, written with the
PR #42 source change and not executed in this (or any) source-only pass. Source reading confirms
the code paths exist — the display-only midtone lift in `assets/shaders/post/composite.frag` and
`CompositePass`, `PacketGameState.gunfireEvents` consumed by `GunAudio`, the FIRE voice rollover,
the 500 u/s drone speed with the 16/s lerp in `GadgetConfig`, and `PROTOCOL_VERSION = 13` — but
**none of the five checks below has been observed on a running build**, and none may be treated as
passed until it has.

- **Vision cone:** aim from the center toward the screen edge. The visible midtones should be easier
to distinguish from the peripheral floor. Confirm that this is presentation-only: gameplay vision,
lighting, and the raw visibility mask remain unchanged.
- **Gunfire timing:** with two clients, fire automatic, burst, and pellet weapons at varied distances
and across a wall. Each successful server volley should produce one correctly weapon-specific,
spatialized report from its state-snapshot cue, even if the muzzle flash is visually culled or its
projectile is already gone. The generic muzzle-flash effect must not double the report.
- **Sustained-fire voices:** hold automatic fire until the per-asset pool is full. New FIRE attacks
should roll over the oldest explicitly marked, equal-priority FIRE voice; ordinary sound priorities
and tie behavior remain unchanged.
- **Drone handling:** while piloting, verify 500 world units/s full speed, a responsive but eased
16/s velocity lerp, directional steering, and the existing coast-to-hover behavior. Check that walls
and arena bounds still stop it cleanly for both authority and prediction at 60 Hz.
- **Protocol:** both peers must use protocol version 13 for the expanded state snapshot.
