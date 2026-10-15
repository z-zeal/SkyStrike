package io.github.skystrike.shared.vision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The shared smoke-occlusion cluster: the one geometry both the server's gameplay sight queries
 * and every client's visibility shader derive from a zone, so these tests pin the contract the
 * two sides must never drift apart on.
 */
class SmokeCloudTest {

    private static final float X = 760f;
    private static final float Y = 1000f;
    private static final float RADIUS = 250f;

    @Test
    @DisplayName("growth is 0 at detonation and 1 once the grow window has elapsed")
    void growthFollowsTheZoneLife() {
        float duration = 8f;
        assertEquals(0f, SmokeCloud.growth(duration, duration), 0.0001f,
            "a freshly detonated cloud occludes nothing yet");
        float grownRemaining = duration * (1f - SmokeCloud.GROW_FRACTION);
        assertEquals(1f, SmokeCloud.growth(grownRemaining, duration), 0.0001f,
            "the cloud is fully grown at the end of its grow window");
        assertEquals(1f, SmokeCloud.growth(0f, duration), 0.0001f);
        assertEquals(1f, SmokeCloud.growth(0f, 0f), 0.0001f,
            "a cloud with no recorded duration counts as fully grown");

        // Halfway through the grow window the smoothstep sits at its midpoint.
        float halfWindowRemaining = duration * (1f - SmokeCloud.GROW_FRACTION * 0.5f);
        assertEquals(0.5f, SmokeCloud.growth(halfWindowRemaining, duration), 0.0001f);

        // And it is monotonic across the whole window.
        float previous = -1f;
        for (int step = 0; step <= 20; step++) {
            float remaining = duration * (1f - (float) step / 20f);
            float growth = SmokeCloud.growth(remaining, duration);
            assertTrue(growth >= previous, "growth never shrinks as the cloud ages");
            assertTrue(growth >= 0f && growth <= 1f, "growth stays in [0, 1]");
            previous = growth;
        }
    }

    @Test
    @DisplayName("dispersal holds through the cloud's life and thins to nothing over the tail")
    void dispersalThinsOverTheTail() {
        float duration = 8f;
        float tail = duration * SmokeCloud.DISPERSE_FRACTION;
        assertEquals(1f, SmokeCloud.dispersal(duration, duration), 0.0001f);
        assertEquals(1f, SmokeCloud.dispersal(tail + 1f, duration), 0.0001f,
            "no thinning before the dispersal window opens");
        assertEquals(0f, SmokeCloud.dispersal(0f, duration), 0.0001f,
            "the occlusion is gone exactly when the zone expires");
        float midTail = tail * 0.5f;
        assertEquals(0.5f, SmokeCloud.dispersal(midTail, duration), 0.0001f,
            "the smoothstep midpoint inside the dispersal window");
        assertEquals(1f, SmokeCloud.dispersal(0f, 0f), 0.0001f);

        // Monotonic as the zone ages toward expiry.
        float previous = 2f;
        for (int step = 0; step <= 20; step++) {
            float remaining = duration * (1f - (float) step / 20f);
            float dispersal = SmokeCloud.dispersal(remaining, duration);
            assertTrue(dispersal <= previous, "dispersal never grows as the zone ages");
            assertTrue(dispersal >= 0f && dispersal <= 1f, "dispersal stays in [0, 1]");
            previous = dispersal;
        }
    }

    @Test
    @DisplayName("the envelope grows with the billow and disperses with the cloud")
    void envelopeCombinesGrowthAndDispersal() {
        float duration = 8f;
        assertEquals(0f, SmokeCloud.envelope(duration, duration), 0.0001f);
        float grownRemaining = duration * (1f - SmokeCloud.GROW_FRACTION);
        assertEquals(1f, SmokeCloud.envelope(grownRemaining, duration), 0.0001f,
            "mid-life the cluster is fully out");
        assertEquals(0f, SmokeCloud.envelope(0f, duration), 0.0001f,
            "at expiry nothing occludes");
    }

    @Test
    @DisplayName("a cloud that has not grown yields no occlusion circles at all")
    void ungrownCloudOccludesNothing() {
        assertTrue(SmokeCloud.volumes(X, Y, RADIUS, 0f).isEmpty());
        assertTrue(SmokeCloud.volumes(X, Y, 0f, 1f).isEmpty());
    }

    @Test
    @DisplayName("the fully grown cluster is a five-circle blob: core, sagging lobes, rising crown")
    void grownClusterLayout() {
        List<SmokeVolume> volumes = SmokeCloud.volumes(X, Y, RADIUS, 1f);
        assertEquals(SmokeCloud.CIRCLES_PER_CLOUD, volumes.size());

        SmokeVolume core = volumes.get(0);
        assertEquals(X, core.x(), 0.0001f);
        assertEquals(Y + SmokeCloud.CORE_OFFSET_Y * RADIUS, core.y(), 0.0001f);
        assertEquals(SmokeCloud.CORE_RADIUS * RADIUS, core.radius(), 0.0001f);
        assertEquals(1f, core.density(), 0.0001f);

        for (int side = -1; side <= 1; side += 2) {
            SmokeVolume lobe = volumes.get(side < 0 ? 1 : 2);
            assertEquals(X + side * SmokeCloud.LOBE_OFFSET_X * RADIUS, lobe.x(), 0.0001f);
            assertEquals(Y + SmokeCloud.LOBE_OFFSET_Y * RADIUS, lobe.y(), 0.0001f);
            assertEquals(SmokeCloud.LOBE_RADIUS * RADIUS, lobe.radius(), 0.0001f);
        }

        for (int side = -1; side <= 1; side += 2) {
            SmokeVolume crown = volumes.get(side < 0 ? 3 : 4);
            assertEquals(X + side * SmokeCloud.CROWN_OFFSET_X * RADIUS, crown.x(), 0.0001f);
            assertEquals(Y + SmokeCloud.CROWN_OFFSET_Y * RADIUS, crown.y(), 0.0001f);
            assertEquals(SmokeCloud.CROWN_RADIUS * RADIUS, crown.radius(), 0.0001f);
        }

        // The cluster's bulk sags below the detonation point like the cloud does under gravity —
        // the core and the side lobes — while only the crown pair rises into the billow.
        for (int i = 0; i < 3; i++) {
            assertTrue(volumes.get(i).y() <= Y + 0.0001f,
                "the occlusion blob's mass sags, it does not float");
        }
        for (int i = 3; i < SmokeCloud.CIRCLES_PER_CLOUD; i++) {
            assertTrue(volumes.get(i).y() >= Y - 0.0001f,
                "the crown pair is the part that billows upward");
        }

        // The widest lobe reaches furthest, and no circle reaches beyond it from the detonation
        // point: the cluster stays inside the cloud's drift envelope.
        float envelope = (SmokeCloud.LOBE_OFFSET_X + SmokeCloud.LOBE_RADIUS) * RADIUS;
        for (SmokeVolume volume : volumes) {
            float reach = Math.abs(volume.x() - X) + volume.radius();
            assertTrue(reach <= envelope + 0.0001f,
                "the cluster stays within the rendered cloud's envelope");
        }

        // The detonation point itself is occluded once the cloud has grown over it.
        boolean coversDetonation = false;
        for (SmokeVolume volume : volumes) {
            if (volume.contains(X, Y)) {
                coversDetonation = true;
            }
        }
        assertTrue(coversDetonation, "the grown cloud occludes its own centre");
    }

    @Test
    @DisplayName("the cluster swells out of the detonation point with growth and is deterministic")
    void clusterScalesWithGrowth() {
        List<SmokeVolume> half = SmokeCloud.volumes(X, Y, RADIUS, 0.5f);
        List<SmokeVolume> full = SmokeCloud.volumes(X, Y, RADIUS, 1f);
        assertEquals(full.size(), half.size());
        for (int i = 0; i < full.size(); i++) {
            assertEquals(full.get(i).radius() * 0.5f, half.get(i).radius(), 0.0001f);
            assertEquals(X + (full.get(i).x() - X) * 0.5f, half.get(i).x(), 0.0001f,
                "growth swells the whole cluster out of the detonation point");
            assertEquals(Y + (full.get(i).y() - Y) * 0.5f, half.get(i).y(), 0.0001f);
        }

        List<SmokeVolume> again = SmokeCloud.volumes(X, Y, RADIUS, 1f);
        assertEquals(full, again, "the cluster is a pure function of its inputs");
    }
}
