package ru.kyamshanov.comminusm.infrastructure.adapters

import org.bukkit.Bukkit
import org.bukkit.World

/**
 * Adapter for accessing Bukkit World objects.
 * Provides a centralized bridge for world lookups, keeping framework details
 * out of the domain and application layers.
 */
object BukkitWorldAdapter {
    /**
     * Gets a world by name.
     *
     * @param worldName The name of the world to retrieve
     * @return The Bukkit World object, or null if the world does not exist
     */
    fun getWorld(worldName: String): World? = Bukkit.getWorld(worldName)

    /**
     * Gets all currently loaded worlds on the server.
     *
     * @return List of all Bukkit World objects
     */
    fun getWorlds(): List<World> = Bukkit.getWorlds()

    /**
     * Checks if a world exists on the server.
     *
     * @param worldName The name of the world to check
     * @return true if the world exists, false otherwise
     */
    fun worldExists(worldName: String): Boolean = Bukkit.getWorld(worldName) != null
}
