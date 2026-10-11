package io.github.skystrike.shared.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.audio.EffectSoundTable;
import io.github.skystrike.shared.effect.EffectType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The material half of the destruction contract (plan D7).
 *
 * <p>Every material row is a decision that some already-written damage path will read, so the rows
 * are pinned here by name: stone refuses bullets, metal is the tougher surface that chips slowly,
 * wood is what bullets are meant to open, an energy block only answers explosives, and glass's
 * impact look is the shatter effect whose recording the audio checks verify.
 */
class MaterialTableTest {

    @Test
    @DisplayName("every material has exactly one profile")
    void everyMaterialHasAProfile() {
        for (Material material : Material.values()) {
            assertNotNull(MaterialTable.profileFor(material), material.toString());
        }
    }

    @Test
    @DisplayName("the rows match the plan's prose")
    void rowsFollowTheDesign() {
        MaterialTable.Profile stone = MaterialTable.profileFor(Material.STONE);
        MaterialTable.Profile metal = MaterialTable.profileFor(Material.METAL);
        MaterialTable.Profile wood = MaterialTable.profileFor(Material.WOOD);
        MaterialTable.Profile glass = MaterialTable.profileFor(Material.GLASS);
        MaterialTable.Profile energy = MaterialTable.profileFor(Material.ENERGY);

        assertEquals(0f, stone.bulletRetention(), "stone takes no bullet damage");
        assertEquals(0f, stone.meleeRetention(), "a bat does not chip stone either");
        assertTrue(stone.blastRetention() > 0f, "an explosion is the one thing stone cannot ignore");

        assertTrue(metal.durability() > stone.durability(), "metal is the tougher surface");
        assertTrue(metal.bulletRetention() > 0f, "metal is chipped by rounds, unlike stone");
        assertTrue(metal.bulletRetention() < wood.bulletRetention(),
            "metal gives up less of a round's damage than wood does");
        assertTrue(metal.blastRetention() >= metal.bulletRetention(),
            "an explosion is at least as effective against metal as a round");

        assertEquals(1f, wood.bulletRetention(), "wood is the material bullets open");
        assertTrue(wood.durability() < metal.durability(), "wood is the soft one");

        assertEquals(EffectType.GLASS_SHATTER, glass.impactEffect(),
            "a hit on glass reads as the shatter it causes");
        assertEquals(1f, glass.bulletRetention(), "nothing about a pane deflects a round");

        assertEquals(0f, energy.bulletRetention(), "an energy block ignores rounds");
        assertEquals(0f, energy.meleeRetention(), "and bats");
        assertEquals(1f, energy.blastRetention(), "explosives are its whole interaction");
    }

    @Test
    @DisplayName("retention turns raw damage into accepted damage, per source")
    void retentionScalesTheMeasuredDamage() {
        MapBlock wood = block(BlockKind.BREAKABLE, Material.WOOD, 1f);
        MapBlock stone = block(BlockKind.BREAKABLE, Material.STONE, 1f);
        MapBlock energy = block(BlockKind.BREAKABLE, Material.ENERGY, 1f);

        assertEquals(40f, MaterialTable.acceptedDamage(wood, DamageSource.BULLET, 40f));
        assertEquals(0f, MaterialTable.acceptedDamage(stone, DamageSource.BULLET, 40f));
        assertEquals(40f, MaterialTable.acceptedDamage(energy, DamageSource.BLAST, 40f));
        assertEquals(0f, MaterialTable.acceptedDamage(energy, DamageSource.MELEE, 40f));
        assertEquals(0f, MaterialTable.acceptedDamage(wood, DamageSource.BULLET, 0f),
            "no measured damage is no damage, whatever the retention");
        assertEquals(0f, MaterialTable.acceptedDamage(wood, DamageSource.BULLET, -5f));
    }

    @Test
    @DisplayName("a permanent kind is not a damageable block whatever its material")
    void permanenceIsTheKindsRule() {
        MapBlock wall = block(BlockKind.WALL, Material.WOOD, 1f);
        MapBlock platform = block(BlockKind.PLATFORM, Material.WOOD, 1f);

        assertFalse(MaterialTable.takesDamage(wall, DamageSource.BULLET),
            "a wall is permanent because its rule row says so, not because its material is tough");
        assertFalse(MaterialTable.takesDamage(platform, DamageSource.BLAST));
        assertEquals(Float.POSITIVE_INFINITY, MaterialTable.durabilityPool(wall));
        assertEquals(Float.POSITIVE_INFINITY, MaterialTable.durabilityPool(platform));

        assertTrue(MaterialTable.takesDamage(block(BlockKind.GLASS, Material.GLASS, 1f),
            DamageSource.BULLET));
        assertFalse(MaterialTable.takesDamage(block(BlockKind.BREAKABLE, Material.STONE, 1f),
            DamageSource.BULLET));
        assertTrue(MaterialTable.takesDamage(block(BlockKind.BREAKABLE, Material.STONE, 1f),
            DamageSource.BLAST));
    }

    @Test
    @DisplayName("the per-block multiplier scales the pool, not the damage per hit")
    void multiplierScalesThePool() {
        MapBlock plain = block(BlockKind.BREAKABLE, Material.WOOD, 1f);
        MapBlock reinforced = block(BlockKind.BREAKABLE, Material.WOOD, 2.5f);

        assertEquals(120f, MaterialTable.durabilityPool(plain));
        assertEquals(300f, MaterialTable.durabilityPool(reinforced));
        assertEquals(
            MaterialTable.acceptedDamage(plain, DamageSource.BULLET, 40f),
            MaterialTable.acceptedDamage(reinforced, DamageSource.BULLET, 40f),
            "a reinforced crate survives more hits, it does not take less damage per hit");
    }

    @Test
    @DisplayName("every material's impact sound comes from the catalogue, not a second list")
    void impactSoundFollowsTheEffectCatalogue() {
        for (Material material : Material.values()) {
            MaterialTable.Profile profile = MaterialTable.profileFor(material);
            assertNotNull(MaterialTable.impactSound(material), material.toString());
            assertEquals(
                EffectSoundTable.specFor(profile.impactEffect()),
                MaterialTable.impactSound(material),
                material + " must sound like the effect it names");
            assertFalse(MaterialTable.impactSound(material).isSilent(),
                material + " has no reason to be silent");
        }
    }

    private static MapBlock block(BlockKind kind, Material material, float multiplier) {
        return new MapBlock(kind, material, new Rect(100f, 100f, 20f, 20f), multiplier);
    }
}
