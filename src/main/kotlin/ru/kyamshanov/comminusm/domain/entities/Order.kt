package ru.kyamshanov.comminusm.domain.entities

import ru.kyamshanov.comminusm.domain.value_objects.WorldLocation
import java.util.UUID

/**
 * Domain entity for Order.
 * Represents a location-based work order without Bukkit dependencies.
 *
 * @param id Unique order identifier
 * @param ownerUuid UUID of the player who owns the order
 * @param level Work level (affects capabilities)
 * @param radius Radius of effect
 * @param centerWorld Name of the world where order is centered (null if not activated)
 * @param centerX X coordinate of order center
 * @param centerY Y coordinate of order center
 * @param centerZ Z coordinate of order center
 */
data class Order(
    val id: Long = 0,
    val ownerUuid: UUID,
    val name: String = "",
    val level: Int = 1,
    val radius: Int = 2,
    val centerWorld: String? = null,
    val centerX: Int = 0,
    val centerY: Int = 0,
    val centerZ: Int = 0,
) {
    /**
     * Checks if this order is activated (has a location set).
     */
    fun isActivated(): Boolean = centerWorld != null

    /**
     * Gets the location of this order as a domain value object.
     * Returns null if the order is not activated.
     */
    fun getLocation(): WorldLocation? {
        if (centerWorld == null) return null
        return WorldLocation(centerWorld, centerX, centerY, centerZ)
    }
}
