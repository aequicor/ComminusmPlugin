package ru.kyamshanov.comminusm.commune.listener

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import ru.kyamshanov.comminusm.commune.service.CommuneService
import ru.kyamshanov.comminusm.commune.service.CrossOrderMembershipService
import ru.kyamshanov.comminusm.event.FlagDeactivatedEvent
import java.util.UUID

/**
 * Listener that observes FlagDeactivatedEvent and cascades commune exit.
 *
 * When an order's flag is destroyed, the order should be removed from any commune it's in.
 * This triggers a cascade: all cross-order members of that order are revoked, and if the
 * commune becomes empty, it's dissolved.
 *
 * Implements CC-01: "Order destroyed, cascade exit from commune"
 * Also implements AC-10: "Leave commune, revoke cross-order rights both ways"
 */
class CommuneOrderDestroyListener(
    private val communeService: CommuneService,
    private val crossOrderService: CrossOrderMembershipService
) : Listener {

    @EventHandler
    fun onFlagDeactivated(event: FlagDeactivatedEvent) {
        val orderId = event.orderId

        // Find the commune containing this order (if any)
        val commune = communeService.getCommuneOfOrder(orderId) ?: return

        // Enable cascade mode to prevent O(N²) recalculation (CC-Q5, §6.6)
        try {
            crossOrderService.setCascadeMode(true)

            // Step 1: Revoke all cross-order memberships in both directions (§6.6 step 1, spec §6.5 step 5)
            // This treats the destroyed order as the "leaving order" in the cascade logic
            // For each order in the commune, revoke cross-order members where:
            // - nativeOrderId == destroyedOrderId OR
            // - hostOrderId == destroyedOrderId
            commune.orderIds.forEach { otherOrderId ->
                if (otherOrderId != orderId) {
                    // Revoke both directions: (orderId, otherOrderId) and (otherOrderId, orderId)
                    crossOrderService.revokeCommuneMember(Pair(orderId, otherOrderId), UUID.randomUUID())
                    crossOrderService.revokeCommuneMember(Pair(otherOrderId, orderId), UUID.randomUUID())
                }
            }

            // Step 2: Remove destroyed order from commune (§6.6 step 2)
            communeService.removeOrderFromCommune(commune.id, orderId)

            // Step 3: Dissolve commune if empty (§6.6 step 3)
            val updatedCommune = communeService.getCommune(commune.id)
            if (updatedCommune != null && updatedCommune.orderIds.isEmpty()) {
                communeService.dissolveCommune(commune.id)
            }

        } finally {
            // Always reset cascade mode (§6.6 step 5, CC-Q5)
            // This ensures inCascadeMode is cleared even if an exception occurs
            crossOrderService.clearCascadeMode()
        }
    }
}
