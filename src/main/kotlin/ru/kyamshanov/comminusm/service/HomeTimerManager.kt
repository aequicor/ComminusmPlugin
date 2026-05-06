package ru.kyamshanov.comminusm.service

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * In-memory timer state for a single player's home-teleport countdown.
 *
 * All fields except [remainingSeconds] and [cancelled] are immutable after
 * construction. [cancelled] transitions from false → true exactly once
 * (compare-and-set semantic) and is checked inside [HomeTimerManager.tick]
 * immediately after map retrieval to close the within-tick cancellation race.
 */
data class HomeTimerState(
    val playerUuid: UUID,
    val orderId: Long,
    val taskId: Int,
    var remainingSeconds: Int = 30,
    val cancelled: AtomicBoolean = AtomicBoolean(false),
)

/**
 * Reasons a home-teleport timer can be cancelled.
 */
enum class CancelReason {
    MOVEMENT,
    DAMAGE,
    ATTACK,
    DISCONNECT,
    FLAG_DEACTIVATED,
    FLAG_WORLD_CHANGED,
    PLAYER_DIED,
}

/**
 * Result of a teleport attempt returned by [HomeTimerManager.teleportPlayerToFlag].
 */
enum class TeleportResult { SUCCESS, FAILED }

/** Number of Bukkit ticks between timer countdown ticks (20 ticks = 1 second). */
private const val TIMER_PERIOD_TICKS = 20L

/**
 * Manages home-teleport countdown timers for players.
 *
 * Thread-safety: the backing map is a [ConcurrentHashMap]. All public
 * methods that call Bukkit API ([startTimer], [cancelTimer]) must be called
 * from the main thread. [cancelTimersForOrder] is safe to call from any
 * thread — Bukkit API calls inside the cancel path are bounced to the main
 * thread via [mainThreadRunner].
 *
 * @param plugin                Plugin instance used for scheduler and logger access.
 * @param flagStabilityManager  Flag-stability boundary (injected; tests pass a fake).
 * @param taskScheduler         Submits a repeating 20-tick BukkitTask and returns its ID.
 *                              Default: delegates to [plugin]'s Bukkit scheduler.
 * @param taskCanceller         Cancels a BukkitTask by its ID.
 * @param isPlayerOnline        Returns true if the player with the given UUID is currently online.
 * @param sendActionBarToPlayer Sends an ActionBar message (MiniMessage-formatted string)
 *                              to the player identified by UUID.
 * @param sendMessageToPlayer   Sends a chat message (MiniMessage Component) to the player
 *                              identified by UUID. Used for persistent outcome messages
 *                              (success, errors) that should not be overwritten by the ticker.
 * @param getPlayerWorldName    Returns the world name of an online player, or null when offline.
 * @param getFlagWorldName      Returns the world name of the flag location for the given orderId,
 *                              derived from the already-fetched Location's world. Tests may
 *                              override this seam when the Location stub has a null world.
 * @param teleportPlayerToFlag  Attempts to teleport the player to the flag location.
 *                              Returns [TeleportResult.SUCCESS] or [TeleportResult.FAILED].
 * @param mainThreadRunner      Schedules a [Runnable] on the main server thread.
 */
@Suppress("TooManyFunctions", "LongParameterList")
open class HomeTimerManager(
    private val plugin: Plugin,
    private val flagStabilityManager: FlagStabilityManager,
    private val taskScheduler: (action: () -> Unit) -> Int = { action ->
        plugin.server.scheduler.runTaskTimer(
            plugin,
            Runnable { action() },
            TIMER_PERIOD_TICKS,
            TIMER_PERIOD_TICKS,
        ).taskId
    },
    private val taskCanceller: (taskId: Int) -> Unit = { taskId ->
        plugin.server.scheduler.cancelTask(taskId)
    },
    private val isPlayerOnline: (UUID) -> Boolean = { uuid ->
        plugin.server.getPlayer(uuid) != null
    },
    private val sendActionBarToPlayer: (playerUuid: UUID, message: String) -> Unit = { uuid, message ->
        plugin.server.getPlayer(uuid)?.sendActionBar(MiniMessage.miniMessage().deserialize(message))
    },
    private val sendMessageToPlayer: (playerUuid: UUID, message: Component) -> Unit = { uuid, message ->
        plugin.server.getPlayer(uuid)?.sendMessage(message)
    },
    private val getPlayerWorldName: (UUID) -> String? = { uuid ->
        plugin.server.getPlayer(uuid)?.world?.name
    },
    private val getFlagWorldName: (orderId: Long, flagLoc: org.bukkit.Location) -> String? = { _, flagLoc ->
        flagLoc.world?.name
    },
    private val teleportPlayerToFlag: (playerUuid: UUID, orderId: Long) -> TeleportResult = { uuid, oid ->
        val flagLoc = flagStabilityManager.getFlagLocation(oid)
        val player = plugin.server.getPlayer(uuid)
        if (player != null && flagLoc != null) {
            @Suppress("TooGenericExceptionCaught")
            try {
                player.teleport(flagLoc)
                TeleportResult.SUCCESS
            } catch (e: Exception) {
                plugin.logger.warning("Teleport failed for $uuid: ${e.message}")
                TeleportResult.FAILED
            }
        } else {
            TeleportResult.FAILED
        }
    },
    private val mainThreadRunner: (Runnable) -> Unit = { runnable ->
        plugin.server.scheduler.runTask(plugin, runnable)
    },
) {

    /** Backing store for active timers. Keyed by player UUID. */
    internal val timers = ConcurrentHashMap<UUID, HomeTimerState>()

    /**
     * Starts a home-teleport timer for [playerUuid] targeting [orderId].
     *
     * Returns false (AC-12) if a timer is already active for this player.
     * Returns true when the timer was successfully started.
     * Must be called from the main thread.
     */
    fun startTimer(playerUuid: UUID, orderId: Long): Boolean {
        if (timers.containsKey(playerUuid)) return false

        val taskId = taskScheduler { tick(playerUuid) }
        val state = HomeTimerState(playerUuid = playerUuid, orderId = orderId, taskId = taskId)
        timers[playerUuid] = state

        sendActionBarToPlayer(
            playerUuid,
            "<yellow>Возврат домой: <white>30 сек.</white> Не двигайтесь!</yellow>",
        )
        return true
    }

    /**
     * Cancels the active timer for [playerUuid].
     *
     * Idempotent — safe to call even when no timer is active.
     * [silent] = true suppresses the cancellation ActionBar message.
     * Bukkit API calls are bounced to the main thread when [silent] = false.
     */
    open fun cancelTimer(playerUuid: UUID, reason: CancelReason, silent: Boolean = false) {
        val state = timers.remove(playerUuid) ?: return
        state.cancelled.set(true)
        if (!silent) {
            mainThreadRunner(
                Runnable {
                    taskCanceller(state.taskId)
                    sendCancelMessage(playerUuid, reason)
                },
            )
        } else {
            taskCanceller(state.taskId)
        }
    }

    /**
     * Cancels all active timers whose [HomeTimerState.orderId] matches [orderId].
     *
     * Safe to call from any thread. Uses an explicit snapshot to avoid
     * ConcurrentModificationException (spec — cancelTimersForOrder contract).
     */
    fun cancelTimersForOrder(orderId: Long, reason: CancelReason) {
        timers.values.toList()
            .filter { it.orderId == orderId }
            .forEach { cancelTimer(it.playerUuid, reason) }
    }

    /**
     * Returns true when an active timer exists for [playerUuid].
     */
    fun hasActiveTimer(playerUuid: UUID): Boolean = timers.containsKey(playerUuid)

    /**
     * Cancels all active BukkitTasks and clears the timer map.
     *
     * Must be called from [Plugin.onDisable] to prevent task leaks on
     * plugin reload or server shutdown (spec — Q8).
     */
    fun onDisable() {
        timers.values.toList().forEach { state ->
            state.cancelled.set(true)
            taskCanceller(state.taskId)
        }
        timers.clear()
    }

    // -------------------------------------------------------------------------
    // Internal tick / teleport logic
    // -------------------------------------------------------------------------

    @Suppress("TooGenericExceptionCaught")
    private fun tick(playerUuid: UUID) {
        try {
            doTick(playerUuid)
        } catch (e: Exception) {
            plugin.logger.severe("HomeTimer tick error for $playerUuid: ${e.message}")
            cancelTimer(playerUuid, CancelReason.DISCONNECT, silent = true)
        }
    }

    @Suppress("ReturnCount")
    private fun doTick(playerUuid: UUID) {
        val state = timers[playerUuid] ?: return
        if (state.cancelled.get()) {
            return
        }
        if (!isPlayerOnline(playerUuid)) {
            cancelTimer(playerUuid, CancelReason.DISCONNECT, silent = true)
            return
        }
        state.remainingSeconds--
        if (state.remainingSeconds <= 0) {
            executeHomeTP(playerUuid)
            return
        }
        sendActionBarToPlayer(
            playerUuid,
            "<yellow>Возврат домой: <white>${state.remainingSeconds} сек.</white></yellow>",
        )
    }

    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    private fun executeHomeTP(playerUuid: UUID) {
        try {
            val state = timers.remove(playerUuid) ?: return
            state.cancelled.set(true)
            taskCanceller(state.taskId)

            if (!isPlayerOnline(playerUuid)) return

            val mm = MiniMessage.miniMessage()

            // Fresh read at execution time — НЕ кэшировать (CC-06)
            val flagLoc = flagStabilityManager.getFlagLocation(state.orderId)
            if (flagLoc == null) { // CC-02 null check
                sendMessageToPlayer(playerUuid, mm.deserialize("<red>Флаг недоступен. Телепортация отменена.</red>"))
                return
            }
            if (!flagStabilityManager.isFlagActive(state.orderId)) { // CC-02 inactive check
                sendMessageToPlayer(
                    playerUuid,
                    mm.deserialize("<red>Флаг ордера деактивирован. Телепортация отменена.</red>"),
                )
                return
            }

            // Cross-world check at execution time (AC-28) — uses the already-fetched flagLoc (no double read)
            val playerWorld = getPlayerWorldName(playerUuid)
            val flagWorld = getFlagWorldName(state.orderId, flagLoc)
            if (flagWorld != playerWorld) {
                sendMessageToPlayer(playerUuid, mm.deserialize("<red>Телепортация в другой мир недоступна.</red>"))
                return
            }

            when (teleportPlayerToFlag(playerUuid, state.orderId)) {
                TeleportResult.SUCCESS ->
                    sendMessageToPlayer(playerUuid, mm.deserialize("<green>Вы вернулись домой!</green>"))
                TeleportResult.FAILED ->
                    sendMessageToPlayer(playerUuid, mm.deserialize("<red>Ошибка телепортации. Попробуйте снова.</red>"))
            }
        } catch (e: Exception) {
            plugin.logger.severe("executeHomeTP error for $playerUuid: ${e.message}")
        }
    }

    private fun sendCancelMessage(playerUuid: UUID, reason: CancelReason) {
        val mm = MiniMessage.miniMessage()
        val message = when (reason) {
            CancelReason.MOVEMENT ->
                mm.deserialize("<red>Телепортация отменена: вы сдвинулись с места.</red>")
            CancelReason.DAMAGE ->
                mm.deserialize("<red>Телепортация отменена: вы получили урон.</red>")
            CancelReason.ATTACK ->
                mm.deserialize("<red>Телепортация отменена: вы атаковали.</red>")
            CancelReason.FLAG_DEACTIVATED ->
                mm.deserialize("<red>Телепортация отменена: флаг ордера недоступен.</red>")
            CancelReason.FLAG_WORLD_CHANGED ->
                mm.deserialize("<red>Телепортация отменена: флаг перемещён в другой мир.</red>")
            CancelReason.DISCONNECT, CancelReason.PLAYER_DIED -> return // always silent
        }
        sendMessageToPlayer(playerUuid, message)
    }
}
