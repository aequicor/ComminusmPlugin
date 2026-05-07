package ru.kyamshanov.comminusm.infrastructure.adapters

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

/**
 * Adapter for accessing Bukkit Player objects.
 * Provides a centralized bridge for player lookups, keeping framework details
 * out of the domain and application layers.
 */
object BukkitPlayerAdapter {
    /**
     * Gets a player by UUID.
     *
     * @param uuid The UUID of the player to retrieve
     * @return The Bukkit Player object if online, or null if not found
     */
    fun getPlayer(uuid: UUID): Player? = Bukkit.getPlayer(uuid)

    /**
     * Gets a player by exact name.
     *
     * @param name The exact name of the player
     * @return The Bukkit Player object if online, or null if not found
     */
    fun getPlayer(name: String): Player? = Bukkit.getPlayer(name)

    /**
     * Gets all currently online players on the server.
     *
     * @return List of all online Bukkit Player objects
     */
    fun getOnlinePlayers(): List<Player> = Bukkit.getOnlinePlayers().toList()

    /**
     * Checks if a player is currently online.
     *
     * @param uuid The UUID of the player to check
     * @return true if the player is online, false otherwise
     */
    fun isOnline(uuid: UUID): Boolean = Bukkit.getPlayer(uuid) != null

    /**
     * Gets the count of currently online players.
     *
     * @return Number of online players
     */
    fun getOnlinePlayerCount(): Int = Bukkit.getOnlinePlayers().size
}
