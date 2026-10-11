package io.github.skystrike.shared.map;

/**
 * Surface material assigned to a {@link MapBlock}.
 *
 * <p>Material identity is present in the map format from its first version. Everything survivable
 * about a block lives in its {@link MaterialTable} row — durability, how much of each
 * {@link DamageSource} it accepts, the colours a renderer tints it with and the impact effect and
 * sound a hit on it raises. Keeping those as data rather than kind checks is an intentional part
 * of the map contract: a new surface is a table row, not a new branch in the bullet, blast and
 * melee paths.
 */
public enum Material {
    STONE,
    METAL,
    WOOD,
    GLASS,
    ENERGY
}
