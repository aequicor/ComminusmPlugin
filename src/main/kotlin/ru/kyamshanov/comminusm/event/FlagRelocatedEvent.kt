package ru.kyamshanov.comminusm.event

import org.bukkit.Location
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Fired after an order flag is successfully relocated to a new position.
 *
 * [oldWorld] and [newWorld] allow listeners to determine whether the flag moved
 * to a different world (AC-18a) — in that case all active home-teleport timers
 * for the order must be cancelled.
 */
class FlagRelocatedEvent(
    val orderId: Long,
    val oldWorld: String,
    val newWorld: String,
    val newLocation: Location,
) : Event() {
    companion object {
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }

    override fun getHandlers(): HandlerList = HANDLERS
}
