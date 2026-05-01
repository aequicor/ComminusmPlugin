package ru.kyamshanov.comminusm.listener

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import ru.kyamshanov.comminusm.config.PluginConfig
import ru.kyamshanov.comminusm.plugin.ComminusmPlugin
import ru.kyamshanov.comminusm.service.WorkdaysService

class PlayerListener(
    private val workdaysService: WorkdaysService,
    private val config: PluginConfig
) : Listener {

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        schedulePassiveIncome(event.player.uniqueId)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        cancelPassiveIncome(event.player.uniqueId)
    }

    private val activeTasks = mutableMapOf<java.util.UUID, Int>()

    private fun schedulePassiveIncome(uuid: java.util.UUID) {
        val intervalTicks = config.passiveIncomeIntervalMinutes * 60L * 20L
        val taskId = ComminusmPlugin.getInstance().server.scheduler.runTaskTimer(
            ComminusmPlugin.getInstance(),
            Runnable {
                val player = ComminusmPlugin.getInstance().server.getPlayer(uuid)
                if (player != null && player.isOnline) {
                    workdaysService.earn(uuid, config.passiveIncomeAmount)
                }
            },
            intervalTicks,
            intervalTicks
        ).taskId
        activeTasks[uuid] = taskId
    }

    private fun cancelPassiveIncome(uuid: java.util.UUID) {
        activeTasks.remove(uuid)?.let { taskId ->
            ComminusmPlugin.getInstance().server.scheduler.cancelTask(taskId)
        }
    }
}
