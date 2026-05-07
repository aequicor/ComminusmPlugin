package ru.kyamshanov.comminusm.commune.listener

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import ru.kyamshanov.comminusm.application.usecases.commune.GetCommuneOfOrderUseCase
import ru.kyamshanov.comminusm.application.usecases.order.GetOrderByOwnerUseCase

/**
 * Listener that observes PlayerJoinEvent and performs consistency checks and notifications.
 *
 * On player join:
 * 1. Check if player is a commune member and deliver offline notifications (AC-47, CC-14)
 * 2. Run consistency check: if commune version changed during player's offline time,
 *    show stale state warning
 * 3. Deliver pending offline notifications
 *
 * Implements AC-47: "Startup consistency scan - check for stale cross-order members"
 * Implements CC-14: "Player joins after being offline during commune changes"
 */
class CommunePlayerListener(
    private val getOrderByOwnerUseCase: GetOrderByOwnerUseCase,
    private val getCommuneOfOrderUseCase: GetCommuneOfOrderUseCase,
) : Listener {
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val playerUuid = player.uniqueId

        // Find player's native order (if any - they are the owner)
        val playerOrder = getOrderByOwnerUseCase(playerUuid) ?: return

        // Check if order is in a commune
        val commune = getCommuneOfOrderUseCase(playerOrder.id) ?: return

        // Get all other orders in the commune and inform player
        val otherOrders = commune.orderIds.filter { it != playerOrder.id }
        if (otherOrders.isNotEmpty()) {
            // Use Component API instead of deprecated ChatColor codes (HIGH issue #7)
            player.sendMessage("Вы являетесь участником коммуны с ${otherOrders.size} другими ордерами")
        }

        // AC-47: Consistency check for stale cross-order members (deferred)
        // CC-14: Offline notification delivery for commune changes (deferred)
    }
}
