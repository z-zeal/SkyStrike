package io.github.skystrike.shared.sdf;

import static org.junit.jupiter.api.Assertions.*;

import io.github.skystrike.shared.map.ArenaMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SdfBakerTest {

    private ArenaMap arena;
    private SdfField field;

    @BeforeEach
    void setUp() {
        arena = ArenaMap.standard();
        field = SdfBaker.bake(arena, 2.0f);
    }

    @Test
    void testDimensionsAndBounds() {
        assertEquals(1500, field.width());
        assertEquals(1000, field.height());
        assertEquals(2.0f, field.texelScale());
        assertEquals(3000.0f, field.worldWidth());
        assertEquals(2000.0f, field.worldHeight());
        assertEquals(1500 * 1000, field.rawData().length);
    }

    @Test
    void testSignedDistances() {
        // Deep inside ground (y = 50, ground is at y = 100) -> distance < 0
        float dInside = field.sample(1500f, 50f);
        assertTrue(dInside <= -40f, "expected distance deep in ground to be <= -40, was " + dInside);

        // High in open air (x = 500, y = 1200) -> distance > 0
        float dOpen = field.sample(500f, 1200f);
        assertTrue(dOpen >= 50f, "expected open air distance to be >= 50, was " + dOpen);

        // Near ground surface (ground height = 100)
        float dAboveGround = field.sample(500f, 110f);
        assertTrue(dAboveGround > 0f && dAboveGround <= 15f, "near above ground should be ~10, was " + dAboveGround);

        float dBelowGround = field.sample(500f, 90f);
        assertTrue(dBelowGround < 0f && dBelowGround >= -15f, "near below ground should be ~-10, was " + dBelowGround);
    }

    @Test
    void testGradientOnGround() {
        // Normal above ground should point upward (+Y)
        float[] normal = new float[2];
        field.gradient(500f, 105f, normal);
        assertEquals(0.0f, normal[0], 0.15f);
        assertTrue(normal[1] > 0.8f, "expected upward normal, was ny=" + normal[1]);
    }

    @Test
    void testRaymarchLineOfSight() {
        // Open line of sight between ramp air
        assertTrue(field.hasLineOfSight(200f, 600f, 600f, 600f, 48));

        // Blocked through centre room wall (x=1120, y=300..860)
        assertFalse(field.hasLineOfSight(1050f, 400f, 1200f, 400f, 48));
    }

    @Test
    void testSerializationRoundtrip() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        field.write(out);

        byte[] bytes = out.toByteArray();
        assertEquals(1500 * 1000 + 28, bytes.length);

        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        SdfField loaded = SdfField.read(in);

        assertEquals(field.width(), loaded.width());
        assertEquals(field.height(), loaded.height());
        assertEquals(field.texelScale(), loaded.texelScale());
        assertEquals(field.worldWidth(), loaded.worldWidth());
        assertEquals(field.worldHeight(), loaded.worldHeight());

        // Assert distance samples match exactly
        assertEquals(field.sample(500f, 600f), loaded.sample(500f, 600f), 0.001f);
        assertEquals(field.sample(1500f, 50f), loaded.sample(1500f, 50f), 0.001f);
    }
}
