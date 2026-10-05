package io.github.skystrike.server.weapons;

import io.github.skystrike.shared.combat.SpreadMath;
import io.github.skystrike.shared.config.WeaponConfig;
import io.github.skystrike.shared.math.Angles;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.weapons.FireMode;
import java.util.Random;

/**
 * Turns one tick of trigger state into rounds (mechanics §4.5).
 *
 * <p>Owns the cooldown gate, the fire-mode rules and the two volley patterns. It does not own
 * the projectiles: it hands back the angles, and {@code BulletSystem} decides what to launch
 * along them. That split is what lets the fire modes be tested without a world.
 *
 * <p>Deviation is sampled from the spread <i>before</i> the shot's own kick is applied — a round
 * is not punished by the recoil it is in the act of producing.
 */
public final class FireController {

    /** The rounds produced by one trigger event, reused between ticks to avoid allocation. */
    public static final class Volley {

        private final float[] angles = new float[WeaponConfig.MAX_SHOTS_PER_VOLLEY];
        private int count;
        private boolean burst;
        private float recoilMultiplier = 1f;

        public void clear() {
            count = 0;
            burst = false;
            recoilMultiplier = 1f;
        }

        private void add(float angleDegrees) {
            if (count < angles.length) {
                angles[count++] = Angles.wrap(angleDegrees);
            }
        }

        public int count() {
            return count;
        }

        public float angle(int index) {
            if (index < 0 || index >= count) {
                throw new IndexOutOfBoundsException("volley has " + count + " rounds: " + index);
            }
            return angles[index];
        }

        /** True when this volley was a burst, which is what earns the 1.20× recoil. */
        public boolean isBurst() {
            return burst;
        }

        /** The multiplier the recoil channels and the spread kick were scaled by. */
        public float recoilMultiplier() {
            return recoilMultiplier;
        }

        public boolean isEmpty() {
            return count == 0;
        }
    }

    private final Random random;
    private final RecoilService recoilService;

    public FireController(Random random, RecoilService recoilService) {
        this.random = random == null ? new Random() : random;
        this.recoilService = recoilService == null ? new RecoilService() : recoilService;
    }

    /**
     * Resolves the trigger for one player on one tick.
     *
     * @param fireHeld    the trigger is down this tick
     * @param firePressed the trigger went down since the last tick — the edge semi-automatic,
     *                    bolt, burst and shotgun weapons need
     * @param out         filled with the resulting angles; cleared first
     * @return how many rounds were produced, zero when nothing fired
     */
    public int fire(Player player, GunInstance gun, boolean fireHeld, boolean firePressed, Volley out) {
        out.clear();
        if (player == null || gun == null || !player.alive) {
            return 0;
        }
        if (!gun.isReady()) {
            return 0;
        }

        FireMode mode = gun.stats().fireMode();
        boolean triggered = mode.requiresTriggerRelease() ? firePressed : fireHeld;
        if (!triggered) {
            return 0;
        }

        boolean aiming = player.ads;
        boolean burst = mode == FireMode.BURST;
        float aim = player.aimAngle;

        // Recoil is applied once for the whole volley, and its multiplier scales the spread kick.
        float multiplier = recoilService.applyVolley(player, gun, aim, burst);
        out.burst = burst;
        out.recoilMultiplier = multiplier;

        switch (mode) {
            case BURST -> fireBurst(gun, aim, multiplier, out);
            case SHOTGUN -> fireShell(gun, aim, aiming, multiplier, out);
            default -> fireSingle(gun, aim, multiplier, out);
        }

        gun.startCooldown();
        gun.countRounds(out.count);
        return out.count;
    }

    /** One round, deviated across the live cone by a normal distribution. */
    private void fireSingle(GunInstance gun, float aim, float multiplier, Volley out) {
        out.add(aim + SpreadMath.sampleDeviation(gun.currentSpread(), random));
        gun.addSpreadKick(multiplier);
    }

    /**
     * Three rounds in one instant at a fixed 0.55° spacing, each nudged by 20% of the current
     * spread. The fixed part is what makes a burst read as a deliberate group.
     */
    private void fireBurst(GunInstance gun, float aim, float multiplier, Volley out) {
        for (int i = 0; i < WeaponConfig.BURST_ROUNDS; i++) {
            float offset = SpreadMath.burstOffsetDegrees(
                i, WeaponConfig.BURST_ROUNDS, WeaponConfig.BURST_SPACING_DEGREES);
            float jitter = SpreadMath.burstJitterDegrees(gun.currentSpread(), random);
            out.add(aim + offset + jitter);
            // Every round of the burst kicks the cone, even though the recoil fired once.
            gun.addSpreadKick(multiplier);
        }
    }

    /** Pellets spread evenly across the whole cone, each a full damage instance. */
    private void fireShell(GunInstance gun, float aim, boolean aiming, float multiplier, Volley out) {
        int pellets = gun.stats().pelletCount();
        float cone = gun.currentSpread();
        for (int i = 0; i < pellets; i++) {
            float offset = SpreadMath.pelletOffsetDegrees(i, pellets, cone);
            float jitter = SpreadMath.pelletJitterDegrees(aiming, random);
            out.add(aim + offset + jitter);
        }
        // One shell, one kick.
        gun.addSpreadKick(multiplier);
    }
}
