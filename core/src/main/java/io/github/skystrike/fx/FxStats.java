package io.github.skystrike.fx;

/**
 * A point-in-time snapshot of the FX layer's occupancy, surfaced by the {@code fx_debug} overlay
 * (build plan M7 §8.2 gate: "particle and light counts staying inside the tier budget").
 *
 * @param alphaParticles    live GPU particles in the alpha (scene-pass) batch
 * @param alphaCapacity     that batch's tier cap
 * @param additiveParticles live GPU particles in the additive (post-composite) batch
 * @param additiveCapacity  that batch's tier cap
 * @param cpuParticles      live CPU-tier (colliding) particles
 * @param cpuCapacity       the CPU tier's cap
 * @param effectLights      effect lights currently held in the shared pool
 * @param effectLightCap    the effect-light budget for the current tier
 * @param pendingPhases     timed emitter phases still waiting to fire
 */
public record FxStats(
    int alphaParticles,
    int alphaCapacity,
    int additiveParticles,
    int additiveCapacity,
    int cpuParticles,
    int cpuCapacity,
    int effectLights,
    int effectLightCap,
    int pendingPhases) {
}
