package ru.kyamshanov.comminusm.commune.listener

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import ru.kyamshanov.comminusm.application.usecases.commune.GetCommuneOfOrderUseCase
import ru.kyamshanov.comminusm.application.usecases.commune.RecalculateCrossOrderRightsUseCase
import ru.kyamshanov.comminusm.commune.event.OrderMemberRemovedEvent

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
    private val getCommuneOfOrderUseCase: GetCommuneOfOrderUseCase,
    private val recalculateCrossOrderRightsUseCase: RecalculateCrossOrderRightsUseCase,
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
        val commune = getCommuneOfOrderUseCase(orderId) ?: return

        // Recalculate cross-order rights (§6.10 step 4, §6.12 algorithm)
        recalculateCrossOrderRightsUseCase(playerUuid, commune)
    }
}
