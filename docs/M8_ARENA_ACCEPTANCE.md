# M8 Arena Rework — Desktop Acceptance Checklist

**Status: unverified in the Arena sandbox.** This environment has no JDK, so it cannot compile the
shared tests, run the desktop client, or inspect SDF shadows and particle collision at runtime.
The checks below are intentionally left unchecked for a human with a runnable desktop build.

Start a local match on the rebuilt `assets/data/arena.sdf`. Enable the development console if useful
for `cl_freecam`/noclip inspection, but perform every traversal check below without jetpack unless
it explicitly says otherwise.

## Foot traversal gate

- [ ] From **each spawn**, walk and jump to the room floor through the mid-lane/doorway route.
  Confirm the 50-unit doorway fits a standing player and is not a collision snag.
- [ ] From **each spawn**, use the ground tunnel, jump onto the hatch step, and enter the room
  through both 90-unit floor hatches. Reverse the route without fuel.
- [ ] From the room floor, reach the interior riser, shelf, catwalk, perch ladder, and sniper perch
  without jetpack. Every upward move should feel like a normal chained jump, not a pixel-perfect
  fuel-assisted launch.
- [ ] From the catwalk and perch, return to ground using no fuel. There must be no one-way pocket.
- [ ] Verify that each side has two distinct-height entries to the centre: tunnel/hatch low and
  mid-lane/doorway high.

## Upper arena and symmetry

- [ ] Reach each outer ledge and the high centre platform from either spawn with a jump-first
  jetpack ascent using no more than 40% of a normal fuel tank. Drop back out without fuel.
- [ ] Use `cl_freecam` to inspect both halves. Doorways, hatches, landing shelves, risers, perch
  steps and upper ledges must be exact left/right reflections about x=1500.
- [ ] Check that the upper platforms make y≈900–1000 tactically useful without exposing a sealed
  pocket or an unreachable-looking ledge.

## SDF coherence

- [ ] Toggle shadows / inspect effect collision at both room doorways and both hatches. The SDF
  must agree with collision: no invisible wall in an opening and no light/particle path through a
  solid jamb, floor section or roof lip.
- [ ] Throw or fire an SDF-colliding effect near a hatch edge and the new upper platforms. Confirm
  its collision, shadow and rendered terrain all use the rebuilt geometry.

## Automated follow-up outside this sandbox

- [ ] Run `./gradlew shared:test` with a JDK. This includes `ArenaMapTest`, the new
  `ArenaReachabilityTest`, and `SdfBakerTest`.
- [ ] Run the project compile/module-boundary tasks with a JDK before declaring M8 complete.

The source-only static checks are useful regression screening only; they do not satisfy any item in
this checklist.
