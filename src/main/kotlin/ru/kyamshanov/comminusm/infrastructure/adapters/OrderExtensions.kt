package ru.kyamshanov.comminusm.infrastructure.adapters

import org.bukkit.Bukkit
import org.bukkit.Location
import ru.kyamshanov.comminusm.domain.entities.Order

/**
 * Extension properties for domain Order to provide compatibility with presentation layer.
 * These properties bridge the gap between domain entities and presentation models.
 *
 * Gets the Bukkit Location for this order, or null if not activated.
 * Computes the location on demand from domain coordinates.
 */
val Order.center: Location?
    get() =
        centerWorld?.let { worldName ->
            Bukkit.getWorld(worldName)?.let { world ->
                Location(world, centerX.toDouble(), centerY.toDouble(), centerZ.toDouble())
            }
        }

/**
 * Gets a display size for this order (radius * 2 + 1).
 */
val Order.size: Int
    get() = radius * 2 + 1

/**
 * Gets the creation timestamp as a string (always empty for domain entity - provided for compatibility).
 */
val Order.createdAt: String
    get() = ""
