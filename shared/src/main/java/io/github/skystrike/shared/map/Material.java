package io.github.skystrike.shared.map;

/**
 * Surface material assigned to a {@link MapBlock}.
 *
 * <p>Material identity is present in the map format from its first version. Its durability,
 * damage retention, impact effects, sound and render colours will live in the shared material
 * table when breakable behaviour lands; keeping those as data rather than kind checks is an
 * intentional part of the map contract.
 */
public enum Material {
    STONE,
    METAL,
    WOOD,
    GLASS,
    ENERGY
}
