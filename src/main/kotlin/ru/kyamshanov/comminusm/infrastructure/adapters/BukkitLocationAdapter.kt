package ru.kyamshanov.comminusm.infrastructure.adapters

import org.bukkit.Bukkit
import org.bukkit.Location
import ru.kyamshanov.comminusm.domain.value_objects.WorldLocation

/**
 * Adapter for converting between Bukkit Location objects and domain WorldLocation value objects.
 * Provides a bridge between the Bukkit API and the Bukkit-free domain layer.
 */
object BukkitLocationAdapter {
    /**
     * Converts a Bukkit Location to a domain WorldLocation.
     *
     * @param location The Bukkit Location to convert
     * @return WorldLocation value object with world name and block coordinates
     */
    fun toDomain(location: Location): WorldLocation {
        val world = requireNotNull(location.world) { "Location world cannot be null" }
        return WorldLocation(
            world = world.name,
            x = location.blockX,
            y = location.blockY,
            z = location.blockZ,
        )
    }

    /**
     * Converts a domain WorldLocation to a Bukkit Location.
     *
     * @param worldLocation The domain WorldLocation to convert
     * @return Bukkit Location, or null if the world does not exist on the server
     */
    fun toBukkit(worldLocation: WorldLocation): Location? {
        val world = Bukkit.getWorld(worldLocation.world) ?: return null
        return Location(
            world,
            worldLocation.x.toDouble(),
            worldLocation.y.toDouble(),
            worldLocation.z.toDouble(),
        )
    }
}
