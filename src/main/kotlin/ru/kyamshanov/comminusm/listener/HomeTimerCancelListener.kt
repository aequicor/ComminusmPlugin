package ru.kyamshanov.comminusm.listener

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import ru.kyamshanov.comminusm.service.CancelReason
import ru.kyamshanov.comminusm.service.HomeTimerManager
import kotlin.math.sqrt

/**
 * Cancels an active home-teleport timer when the player moves, takes damage,
 * deals damage, disconnects, or dies.
 *
 * Covered acceptance criteria: AC-05, AC-06, AC-07, AC-14, AC-15, AC-16,
 * AC-19, AC-23, AC-24, CC-07, CC-09.
 *
 * @param homeTimerManager Service managing active countdown timers.
 */
class HomeTimerCancelListener(private val homeTimerManager: HomeTimerManager) : Listener {

    /**
     * AC-05, AC-14 — cancels timer on any XYZ movement >= 0.1 blocks.
     *
     * Threshold of 0.1 protects against minimal client-side drift (CC-09).
     * Gravity falls, water drift, and ladder slides all produce a non-zero
     * displacement and will exceed the threshold when physically meaningful.
     * AC-24: Paper fires PlayerMoveEvent when a mount moves, so mounted players
     * are covered automatically.
     * AC-16 exception: teleport-to-self produces delta = 0 → timer is NOT cancelled.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        val from = event.from
        val to = event.to
        val dx = to.x - from.x
        val dy = to.y - from.y
        val dz = to.z - from.z
        val delta = sqrt(dx * dx + dy * dy + dz * dz)
        if (delta >= MOVEMENT_THRESHOLD) {
            homeTimerManager.cancelTimer(event.player.uniqueId, CancelReason.MOVEMENT)
        }
    }

    /**
     * AC-06 — cancels timer when the player receives damage.
     *
     * Only acts when the damaged entity is a [Player].
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        cancelOnDamage(player.uniqueId)
    }

    /**
     * AC-07 — cancels timer when the player deals damage.
     *
     * Only acts when the attacker ([EntityDamageByEntityEvent.damager]) is a [Player].
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? Player ?: return
        cancelOnAttack(attacker.uniqueId)
    }

    /**
     * AC-15 — silently cancels timer when the player disconnects.
     *
     * [ignoreCancelled] is intentionally false: quit events cannot be cancelled,
     * and we must always clean up on disconnect.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        homeTimerManager.cancelTimer(event.player.uniqueId, CancelReason.DISCONNECT, silent = true)
    }

    /**
     * AC-19 — silently cancels timer when the player dies during countdown.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        cancelOnDeath(event.player.uniqueId)
    }

    // -------------------------------------------------------------------------
    // Internal helpers — visible to tests in the same package
    // -------------------------------------------------------------------------

    /** Called when a player entity receives damage (AC-06). */
    internal fun cancelOnDamage(playerUuid: java.util.UUID) {
        homeTimerManager.cancelTimer(playerUuid, CancelReason.DAMAGE)
    }

    /** Called when a player entity deals damage (AC-07). */
    internal fun cancelOnAttack(playerUuid: java.util.UUID) {
        homeTimerManager.cancelTimer(playerUuid, CancelReason.ATTACK)
    }

    /** Called when a player entity dies (AC-19). */
    internal fun cancelOnDeath(playerUuid: java.util.UUID) {
        homeTimerManager.cancelTimer(playerUuid, CancelReason.PLAYER_DIED, silent = true)
    }

    private companion object {
        /** Minimum XYZ displacement (in blocks) required to cancel the timer. */
        const val MOVEMENT_THRESHOLD = 0.1
    }
}
