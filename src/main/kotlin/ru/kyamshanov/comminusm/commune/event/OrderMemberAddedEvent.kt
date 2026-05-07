package ru.kyamshanov.comminusm.commune.event

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

/**
 * Custom Bukkit event published when a player is added as a member of an order.
 * This event is published by OrderMembershipService after a successful addMember operation.
 *
 * @param orderId The order the player is now a member of
 * @param playerUUID The UUID of the player added
 * @param grantedVia The type of membership: "native" or "commune"
 */
class OrderMemberAddedEvent(
    val orderId: Long,
    val playerUUID: UUID,
    val grantedVia: String,
) : Event() {
    override fun getHandlers(): HandlerList = handlerList

    companion object {
        private val handlerList = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = handlerList
    }
}
