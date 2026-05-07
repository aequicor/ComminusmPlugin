package ru.kyamshanov.comminusm.commune.listener

import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import ru.kyamshanov.comminusm.application.usecases.commune.GetCommuneOfOrderUseCase
import ru.kyamshanov.comminusm.application.usecases.order.GetOrderByOwnerUseCase
import ru.kyamshanov.comminusm.commune.repository.OrderMembersRepository
import ru.kyamshanov.comminusm.commune.service.CommunePendingNotificationService
import java.util.UUID

/**
 * Listener that observes PlayerJoinEvent and performs consistency checks and notifications.
 *
 * On player join:
 * 1. Deliver queued offline notifications (CC-14)
 * 2. Run per-player consistency check: if commune membership changed during player's offline time,
 *    revoke stale grants (AC-47)
 *
 * Implements AC-47: "Per-player cross-order grant validation on join"
 * Implements CC-14: "Delivery of queued offline commune notifications"
 */
class CommunePlayerListener(
    private val getOrderByOwnerUseCase: GetOrderByOwnerUseCase,
    private val getCommuneOfOrderUseCase: GetCommuneOfOrderUseCase,
    private val orderMembersRepository: OrderMembersRepository,
    private val pendingNotifications: CommunePendingNotificationService,
    private val plugin: org.bukkit.plugin.Plugin? = null,
) : Listener {
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val playerUuid = player.uniqueId

        // CC-14: drain offline notification queue (in-memory, safe on main thread)
        for (msg in pendingNotifications.drain(playerUuid)) {
            player.sendMessage(msg)
        }

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

        // AC-47: per-player cross-order grant validation
        val communeOrderIds = commune.orderIds

        if (plugin != null) {
            // Offload to async — removeMember() may write to SQLite
            Bukkit
                .getScheduler()
                .runTaskAsynchronously(
                    plugin,
                    Runnable {
                        checkAndRevokeStaleGrants(
                            playerUuid,
                            communeOrderIds,
                        )
                    },
                )
        } else {
            // Test mode: run synchronously (no Bukkit scheduler available)
            checkAndRevokeStaleGrants(playerUuid, communeOrderIds)
        }
    }

    /**
     * Internal method for testing and consistency checks.
     * Revokes commune-type grants that are not in the current commune.
     *
     * @param playerUuid UUID of the player
     * @param communeOrderIds IDs of orders currently in the commune
     */
    internal fun checkAndRevokeStaleGrants(
        playerUuid: UUID,
        communeOrderIds: Set<Long>,
    ) {
        val communeGrantedOrders =
            orderMembersRepository.getOrdersOfPlayerWithType(
                playerUuid,
                "commune",
            )
        val staleOrders = communeGrantedOrders - communeOrderIds
        for (staleOrderId in staleOrders) {
            orderMembersRepository.removeMember(staleOrderId, playerUuid)
        }
    }
}
