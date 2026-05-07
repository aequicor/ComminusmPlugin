@file:Suppress("PackageName")

package ru.kyamshanov.comminusm.domain.value_objects

/**
 * Domain value object representing a location in a Minecraft world.
 * Bukkit-free representation of coordinates.
 *
 * @param world Name of the Minecraft world
 * @param x X coordinate (block)
 * @param y Y coordinate (block)
 * @param z Z coordinate (block)
 */
data class WorldLocation(
    val world: String,
    val x: Int,
    val y: Int,
    val z: Int,
)
