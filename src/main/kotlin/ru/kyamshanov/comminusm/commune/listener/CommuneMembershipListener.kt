package ru.kyamshanov.comminusm.commune.listener

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import ru.kyamshanov.comminusm.commune.event.OrderMemberRemovedEvent
import ru.kyamshanov.comminusm.commune.service.CommuneService
import ru.kyamshanov.comminusm.commune.service.CrossOrderMembershipService
import ru.kyamshanov.comminusm.commune.service.OrderMembershipService

/**
 * Listener that observes OrderMemberRemovedEvent and recalculates cross-order memberships.
 *
 * When a player is removed from an order as a native member (grantedVia="native"),
 * we check if they still have native membership in any other order of the same commune.
 * If not, we revoke their cross-order memberships in that commune.
 *
 * Implements AC-25: "Player leaves order, cross-order member-status automatically revoked"
 */
class CommuneMembershipListener(
    private val communeService: CommuneService,
    private val membershipService: OrderMembershipService? = null
) : Listener {

    @EventHandler
    fun onOrderMemberRemoved(event: OrderMemberRemovedEvent) {
        // Only process native member removals (§6.10, AC-25)
        // Commune-granted removals (from cascade) do not trigger recalculation
        if (event.grantedVia != "native") {
            return
        }

        val orderId = event.orderId
        val playerUuid = event.playerUUID

        // Find the commune containing this order (if any)
        val commune = communeService.getCommuneOfOrder(orderId) ?: return

        // Call recalculateCrossOrderRights (§6.10 step 4, §6.12 algorithm)
        // This implements the snapshot-iteration pattern to validate cross-order memberships
        recalculateCrossOrderRights(playerUuid, commune)
    }

    /**
     * Recalculate cross-order rights for a player after native membership change (§6.12).
     *
     * Algorithm (snapshot-iteration pattern - CC-S04, CC-S06):
     * 1. Create immutable snapshot of player's cross-order memberships
     * 2. Collect all native orders for the player in the same commune
     * 3. For each cross-order membership: if player has no native order in that commune, revoke
     * 4. Revocations use Internal API (removeMemberSilently) to avoid event loops
     */
    private fun recalculateCrossOrderRights(
        playerUuid: java.util.UUID,
        commune: ru.kyamshanov.comminusm.commune.model.Commune
    ) {
        // Step 1: Collect all native orders of the player in this commune
        val nativeOrdersInCommune = commune.orderIds.filter { orderId ->
            membershipService?.isNativeMember(orderId, playerUuid) ?: false ||
            // Also check if player owns this order
            orderId in membershipService?.getNativeOrdersOfPlayer(playerUuid) ?: emptySet()
        }

        // Step 2: If player has at least one native order in commune, keep cross-order rights
        //         If player has NO native orders in commune, revoke all cross-order rights
        if (nativeOrdersInCommune.isEmpty()) {
            // Revoke cross-order memberships in all orders of this commune
            commune.orderIds.forEach { hostOrderId ->
                // Use Internal API to revoke without triggering events or re-entering recalculation
                membershipService?.removeMemberSilently(hostOrderId, playerUuid)
            }
        }
    }
}
