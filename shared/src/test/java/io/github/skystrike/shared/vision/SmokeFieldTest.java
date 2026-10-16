package io.github.skystrike.shared.vision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.VisionConfig;
import io.github.skystrike.shared.model.UtilityZone;
import io.github.skystrike.shared.utility.UtilityId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The bounded smoke field: the fill policy the server's sight queries and the client's shader
 * must truncate identically, because a circle one side drops and the other keeps is a wall one
 * side can see through.
 */
class SmokeFieldTest {

    private static final float DURATION = 8f;

    private static UtilityZone smoke(int id, float remainingSeconds) {
        return new UtilityZone(
            id, 1, 0, UtilityId.SMOKE.ordinal(), 760f, 1000f, 250f, 0f, remainingSeconds);
    }

    private static UtilityZone fire(int id, float remainingSeconds) {
        return new UtilityZone(
            id, 1, 0, UtilityId.MOLOTOV.ordinal(), 760f, 1000f, 40f, 21f, remainingSeconds);
    }

    /** Remaining life at which a smoke cloud's cluster is fully grown but not yet dispersing. */
    private static float grownRemaining() {
        return DURATION * (1f - SmokeCloud.GROW_FRACTION);
    }

    @Test
    @DisplayName("an empty field occludes nothing")
    void emptyField() {
        assertTrue(SmokeField.volumes(null).isEmpty());
        assertTrue(SmokeField.volumes(List.of()).isEmpty());
    }

    @Test
    @DisplayName("fire zones never enter the field, and expired clouds leave it")
    void onlyLiveCloudsEnter() {
        assertTrue(SmokeField.volumes(List.of(fire(1, 5f))).isEmpty());
        assertTrue(SmokeField.volumes(List.of(smoke(1, 0f))).isEmpty(),
            "a cloud that has expired occludes nothing");
        assertEquals(SmokeCloud.CIRCLES_PER_CLOUD,
            SmokeField.volumes(List.of(smoke(1, grownRemaining()))).size());
    }

    @Test
    @DisplayName("the field holds three whole clouds and rejects a fourth intact")
    void wholeCloudsOnly() {
        List<UtilityZone> zones = List.of(
            smoke(1, grownRemaining()),
            smoke(2, grownRemaining()),
            smoke(3, grownRemaining()),
            smoke(4, grownRemaining()));
        List<SmokeVolume> field = SmokeField.volumes(zones);
        assertEquals(3 * SmokeCloud.CIRCLES_PER_CLOUD, field.size(),
            "three whole five-circle clusters fit the sixteen-slot array; the fourth waits");
        assertTrue(3 * SmokeCloud.CIRCLES_PER_CLOUD <= VisionConfig.MAX_SMOKE_VOLUMES);
        assertTrue(4 * SmokeCloud.CIRCLES_PER_CLOUD > VisionConfig.MAX_SMOKE_VOLUMES,
            "the array is sized so exactly this policy holds");
    }

    @Test
    @DisplayName("the fill order is creation order regardless of how the list arrives")
    void oldestFirstRegardlessOfInputOrder() {
        UtilityZone first = smoke(1, grownRemaining());
        UtilityZone second = smoke(2, grownRemaining());
        List<SmokeVolume> forward = SmokeField.volumes(List.of(first, second));
        List<SmokeVolume> reversed = SmokeField.volumes(new ArrayList<>(List.of(second, first)));
        assertEquals(forward, reversed,
            "the older cloud's cluster always leads, so truncation is identical on both sides");
    }

    @Test
    @DisplayName("a cloud that has not grown yet contributes no circles")
    void ungrownCloudContributesNothing() {
        List<SmokeVolume> field = SmokeField.volumes(List.of(smoke(1, DURATION)));
        assertTrue(field.isEmpty(), "detonation instant: no smoke has puffed yet");
    }
}
