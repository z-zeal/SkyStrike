package io.github.skystrike.shared.vision;

import io.github.skystrike.shared.config.VisionConfig;
import io.github.skystrike.shared.model.UtilityZone;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The live smoke-occlusion field: which clouds' circles actually reach the shader's uniform
 * array and the server's sight queries, and in what order.
 *
 * <p>Both sides fill their smoke list through this one method because the array is bounded
 * ({@link VisionConfig#MAX_SMOKE_VOLUMES}) and truncation is exactly where two implementations
 * quietly disagree: a circle the shader dropped but the server kept is a wall the client can see
 * through and be shot through. The policy is therefore stated once, here:
 *
 * <ul>
 *   <li><b>Oldest-first.</b> Clouds enter in creation order (the zone id the server assigned), so
 *       an established occlusion never pops off because someone threw fresher smoke — a young
 *       cloud waits for room instead.</li>
 *   <li><b>Whole clouds only.</b> A cluster either fits completely or is skipped; a torn
 *       five-circle blob would occlude a shape neither the server nor the picture intends.</li>
 *   <li><b>Identical inputs.</b> The field is a pure function of the zone list — the server feeds
 *       its own zones, the client feeds the snapshot copies — and the sort makes the result
 *       independent of whatever order a caller hands them over in.</li>
 * </ul>
 *
 * <p>With {@code CIRCLES_PER_CLOUD = 5} and a 16-slot array, three simultaneous clouds occlude
 * in full and a fourth waits for one to disperse. That is the deliberate trade against
 * {@code UtilityConfig.MAX_ACTIVE_ZONES = 64}: the array cannot hold every cloud a spam stack
 * can place, so it holds the ones the fight has been living inside.
 */
public final class SmokeField {

    private SmokeField() {
    }

    /**
     * The occlusion circles of every live vision-blocking zone, in the fill order above. Zones
     * that are expired, that do not block vision (fire), or whose cluster has not grown yet
     * contribute nothing. The result is a fresh, mutable list sized for the uniform array.
     */
    public static List<SmokeVolume> volumes(List<UtilityZone> zones) {
        List<SmokeVolume> out = new ArrayList<>(VisionConfig.MAX_SMOKE_VOLUMES);
        if (zones == null || zones.isEmpty()) {
            return out;
        }
        List<UtilityZone> ordered = new ArrayList<>(zones);
        ordered.sort(Comparator.comparingInt(zone -> zone.id));
        for (UtilityZone zone : ordered) {
            if (zone == null || !zone.blocksVision() || zone.remainingSeconds <= 0f) {
                continue;
            }
            List<SmokeVolume> cluster = zone.smokeVolumes();
            if (cluster.isEmpty()) {
                continue;
            }
            if (out.size() + cluster.size() > VisionConfig.MAX_SMOKE_VOLUMES) {
                // Whole clouds only: skip this one rather than tear its cluster mid-way. It
                // keeps its place in line and enters when an older cloud has dispersed.
                continue;
            }
            out.addAll(cluster);
        }
        return out;
    }
}
