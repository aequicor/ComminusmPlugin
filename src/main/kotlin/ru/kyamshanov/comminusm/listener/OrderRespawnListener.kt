package ru.kyamshanov.comminusm.listener

import org.bukkit.Location
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerRespawnEvent
import ru.kyamshanov.comminusm.model.Order
import ru.kyamshanov.comminusm.service.FlagStabilityManager
import ru.kyamshanov.comminusm.service.OrderService
import java.util.UUID
import java.util.logging.Logger

/**
 * Listener that overrides a player's respawn location on death.
 *
 * Priority: order flag > bed > world spawn (AC-27).
 *
 * Registered at [EventPriority.HIGH] so that it fires after the default
 * bed/anchor respawn assignment (NORMAL priority) but before HIGHEST,
 * allowing the flag location to override the bed spawn point.
 *
 * The [findOrderByOwner] seam allows injecting an in-memory stub in tests,
 * bypassing the concrete [OrderService] class (which is non-open).
 */
class OrderRespawnListener(
    private val flagStabilityManager: FlagStabilityManager,
    private val logger: Logger,
    private val findOrderByOwner: (UUID) -> Order?,
) : Listener {

    /**
     * Convenience constructor for production use — delegates [findOrderByOwner]
     * to [OrderService.findByOwner].
     */
    constructor(
        orderService: OrderService,
        flagStabilityManager: FlagStabilityManager,
        logger: Logger,
    ) : this(
        flagStabilityManager = flagStabilityManager,
        logger = logger,
        findOrderByOwner = { uuid -> orderService.findByOwner(uuid) },
    )

    /**
     * Intercepts respawn and redirects the player to their order flag when:
     * - The player owns an active order (AC-11)
     * - The flag has a resolvable location (AC-10)
     * - The flag is currently active — not destroyed or deactivated (AC-22, CC-01, CC-02)
     *
     * Falls back to bed / world spawn silently on any exception (CC-01 guard).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    @Suppress("TooGenericExceptionCaught")
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        try {
            val flagLoc = resolveFlagLocation(event.player.uniqueId)
            if (flagLoc != null) {
                event.respawnLocation = flagLoc
            }
        } catch (e: Exception) {
            // Never re-throw — Bukkit will apply the standard respawn fallback (CC-01 safety net)
            logger.severe("OrderRespawnListener error for player ${event.player.uniqueId}: ${e}")
        }
    }

    /**
     * Resolves the active flag location for the given player.
     *
     * Returns null (fallback) when:
     * - player has no order (AC-11)
     * - flag location is unresolvable (AC-10/AC-22)
     * - flag is not active (CC-01, CC-02)
     * - flag world is unloaded (CC-03 guard)
     *
     * Side effect: loads the flag chunk synchronously (CC-04) before returning
     * a non-null value so that [PlayerRespawnEvent.respawnLocation] is set to
     * a loaded chunk.
     */
    @Suppress("ReturnCount")
    private fun resolveFlagLocation(playerUuid: UUID): Location? {
        val order = findOrderByOwner(playerUuid) ?: return null // AC-11
        val flagLoc = flagStabilityManager.getFlagLocation(order.id) ?: return null // AC-10/AC-22
        if (!flagStabilityManager.isFlagActive(order.id)) return null // CC-01/CC-02
        val world = flagLoc.world ?: return null // unloaded world guard
        // CC-04: load chunk synchronously before setting respawnLocation
        world.getChunkAt(flagLoc).load(true)
        return flagLoc
    }
}
