package ru.kyamshanov.comminusm.commune.listener

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import ru.kyamshanov.comminusm.application.usecases.commune.RemoveOrderFromCommuneWithCascadeUseCase
import ru.kyamshanov.comminusm.event.FlagDeactivatedEvent

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
    private val removeOrderFromCommuneWithCascadeUseCase: RemoveOrderFromCommuneWithCascadeUseCase,
) : Listener {
    @EventHandler
    fun onFlagDeactivated(event: FlagDeactivatedEvent) {
        removeOrderFromCommuneWithCascadeUseCase(event.orderId)
    }
}
