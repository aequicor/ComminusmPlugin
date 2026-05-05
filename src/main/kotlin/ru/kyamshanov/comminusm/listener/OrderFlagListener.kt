package ru.kyamshanov.comminusm.listener

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.plugin.Plugin
import ru.kyamshanov.comminusm.config.PluginConfig
import ru.kyamshanov.comminusm.gui.OrderMenu
import ru.kyamshanov.comminusm.manager.ActivationCheckResult
import ru.kyamshanov.comminusm.manager.FlagActivationHelper
import ru.kyamshanov.comminusm.manager.FlagStabilityManager
import ru.kyamshanov.comminusm.service.OrderService
import ru.kyamshanov.comminusm.service.WorkFrontService
import ru.kyamshanov.comminusm.service.WorkdaysService
import java.util.concurrent.locks.ReentrantLock

@Suppress("LongParameterList")
class OrderFlagListener(
    private val orderService: OrderService,
    private val workdaysService: WorkdaysService?,
    private val config: PluginConfig,
    private val workFrontService: WorkFrontService? = null,
    private val plugin: Plugin,
    private val flagActivationHelper: FlagActivationHelper,
    private val manager: FlagStabilityManager
) : Listener {

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        val item = event.itemInHand
        if (item.type != Material.WHITE_BANNER) return

        val meta = item.itemMeta ?: return
        if (!meta.displayName().toString().contains("Флаг Ордера")) return

        val player = event.player
        val ownerUuid = player.uniqueId
        val order = orderService.findByOwner(ownerUuid)

        if (order == null) {
            event.isCancelled = true
            player.sendMessage(Component.text("§cУ вас нет Ордера, товарищ. Получите его через §e/партия"))
            return
        }

        if (order.centerWorld != null) {
            event.isCancelled = true
            player.sendMessage(Component.text("§cВаш Ордер уже активирован, товарищ!"))
            return
        }

        val bannerBlock = event.block
        val location = bannerBlock.location

        // Pre-condition checks (world allowlist, air above, chunk limit)
        val checkResult = flagActivationHelper.checkPreconditions(bannerBlock, config, manager)
        if (checkResult is ActivationCheckResult.Denied) {
            event.isCancelled = true
            player.sendMessage(Component.text("§c${checkResult.reason}"))
            return
        }
        val chunkKey = (checkResult as ActivationCheckResult.Ok).chunkKey
        val lock = manager.getChunkLock(chunkKey)

        fun proceedWithActivation(acquiredLock: ReentrantLock) {
            val supportBlock = bannerBlock.world.getBlockAt(bannerBlock.x, bannerBlock.y - 1, bannerBlock.z)
            if (manager.isFlagPosition(supportBlock)) {
                acquiredLock.unlock()
                player.sendMessage(Component.text("§cДанная позиция уже занята флагом."))
                return
            }
            val ownerName = flagActivationHelper.resolveOwnerName(ownerUuid)
            flagActivationHelper.activate(
                bannerBlock = bannerBlock,
                flagId = "order/$ownerUuid",
                ownerUuid = ownerUuid,
                ownerName = ownerName,
                flagType = "Ордер",
                config = config,
                manager = manager,
                lock = acquiredLock,
                dbWrite = {
                    val success = orderService.activate(ownerUuid, location)
                    if (!success) {
                        error("Order activation returned false for $ownerUuid")
                    }
                },
                onSuccess = { p ->
                    p?.sendMessage(
                        Component.text(
                            "§a☭ Ордер №${order.id} активирован! Ваша жилплощадь: §e${order.size}×${order.size} §aблоков."
                        )
                    )
                },
                onDbFailure = { p ->
                    p?.sendMessage(Component.text("§cОшибка активации Ордера. Попробуйте ещё раз."))
                }
            )
        }

        if (!lock.tryLock()) {
            Bukkit.getScheduler().runTaskLater(
                plugin,
                Runnable {
                    if (!lock.tryLock()) {
                        player.sendMessage(Component.text("§cПопробуйте ещё раз."))
                        return@Runnable
                    }
                    proceedWithActivation(lock)
                },
                LOCK_RETRY_TICKS
            )
            return
        }
        proceedWithActivation(lock)
    }

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        val block = event.clickedBlock ?: return
        if (block.type != Material.WHITE_BANNER) return

        val player = event.player
        val order = orderService.findByOwner(player.uniqueId) ?: return

        if (order.centerWorld == null) return
        val loc = block.location

        if (loc.world?.name != order.centerWorld) return
        if (loc.blockX != order.centerX || loc.blockY != order.centerY || loc.blockZ != order.centerZ) return

        event.isCancelled = true
        OrderMenu(orderService, workdaysService, config, workFrontService).open(player, order)
    }

    private companion object {
        const val LOCK_RETRY_TICKS = 100L
    }
}
