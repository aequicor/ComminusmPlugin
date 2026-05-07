package ru.kyamshanov.comminusm.domain.entities

import java.util.UUID

/**
 * Domain entity for WorkFront.
 * Represents a large-scale work area without Bukkit dependencies.
 *
 * @param id Unique work front identifier
 * @param ownerUuid UUID of the player who owns the work front
 * @param radius Radius of the work front
 * @param centerWorld Name of the world (null if not activated)
 * @param centerX X coordinate of center
 * @param centerY Y coordinate of center
 * @param centerZ Z coordinate of center
 */
data class WorkFront(
    val id: Long = 0,
    val ownerUuid: UUID,
    val radius: Int = 2,
    val centerWorld: String? = null,
    val centerX: Int = 0,
    val centerY: Int = 0,
    val centerZ: Int = 0,
) {
    /**
     * Checks if this work front is activated (has a location set).
     */
    fun isActivated(): Boolean = centerWorld != null
}
