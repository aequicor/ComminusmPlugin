package ru.kyamshanov.comminusm.infrastructure.adapters

import org.bukkit.Bukkit
import org.bukkit.Location
import ru.kyamshanov.comminusm.domain.entities.WorkFront

/**
 * Extension properties for domain WorkFront to provide compatibility with presentation layer.
 * These properties bridge the gap between domain entities and presentation models.
 *
 * Gets the Bukkit Location for this work front, or null if not activated.
 * Computes the location on demand from domain coordinates.
 */
val WorkFront.center: Location?
    get() =
        centerWorld?.let { worldName ->
            Bukkit.getWorld(worldName)?.let { world ->
                Location(world, centerX.toDouble(), centerY.toDouble(), centerZ.toDouble())
            }
        }

/**
 * Gets a display size for this work front (radius * 2 + 1).
 */
val WorkFront.size: Int
    get() = radius * 2 + 1

/**
 * Gets the creation timestamp as a string (always empty for domain entity - provided for compatibility).
 */
val WorkFront.createdAt: String
    get() = ""
