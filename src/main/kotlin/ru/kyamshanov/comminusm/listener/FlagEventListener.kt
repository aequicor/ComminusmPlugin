package ru.kyamshanov.comminusm.listener

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import ru.kyamshanov.comminusm.event.FlagDeactivatedEvent
import ru.kyamshanov.comminusm.event.FlagRelocatedEvent
import ru.kyamshanov.comminusm.service.CancelReason
import ru.kyamshanov.comminusm.service.HomeTimerManager

/**
 * Listens for flag-stability custom events and cancels home-teleport timers
 * accordingly.
 *
 * AC-13: flag destroyed/deactivated → cancel all timers for that order.
 * AC-18 / AC-18a: flag relocated to a different world → cancel timers;
 *                  same-world relocation → timers are NOT cancelled.
 */
class FlagEventListener(private val homeTimerManager: HomeTimerManager) : Listener {

    /**
     * AC-13: flag was deactivated or destroyed — cancel all active timers for
     * this order with [CancelReason.FLAG_DEACTIVATED].
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onFlagDeactivated(event: FlagDeactivatedEvent) {
        homeTimerManager.cancelTimersForOrder(event.orderId, CancelReason.FLAG_DEACTIVATED)
    }

    /**
     * AC-18a: flag relocated to a different world — cancel timers.
     * AC-18: same-world relocation — timers continue; [HomeTimerManager] will
     * read the updated coordinates from the DB at teleport time.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onFlagRelocated(event: FlagRelocatedEvent) {
        if (event.oldWorld != event.newWorld) {
            homeTimerManager.cancelTimersForOrder(event.orderId, CancelReason.FLAG_WORLD_CHANGED)
        }
    }
}
