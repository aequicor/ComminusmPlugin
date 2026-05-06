package ru.kyamshanov.comminusm.event

import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Fired when an order flag is deactivated or destroyed.
 *
 * Listeners (e.g. [ru.kyamshanov.comminusm.listener.FlagEventListener]) use
 * this event to cancel any active home-teleport timers for the affected order.
 */
class FlagDeactivatedEvent(val orderId: Long) : Event() {
    companion object {
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }

    override fun getHandlers(): HandlerList = HANDLERS
}
