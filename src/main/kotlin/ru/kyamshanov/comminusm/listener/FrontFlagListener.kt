package ru.kyamshanov.comminusm.listener

import kotlin.math.abs
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
import ru.kyamshanov.comminusm.gui.FrontMenu
import ru.kyamshanov.comminusm.manager.ActivationCheckResult
import ru.kyamshanov.comminusm.manager.FlagActivationHelper
import ru.kyamshanov.comminusm.manager.FlagCleanupHelper
import ru.kyamshanov.comminusm.manager.FlagStabilityManager
import ru.kyamshanov.comminusm.model.Order
import ru.kyamshanov.comminusm.service.OrderService
import ru.kyamshanov.comminusm.service.WorkFrontService
import java.util.concurrent.locks.ReentrantLock

@Suppress("LongParameterList")
class FrontFlagListener(
    private val workFrontService: WorkFrontService,
    private val orderService: OrderService,
    private val plugin: Plugin,
    private val flagActivationHelper: FlagActivationHelper,
    private val flagCleanupHelper: FlagCleanupHelper,
    private val manager: FlagStabilityManager,
    private val config: PluginConfig
) : Listener {

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        val item = event.itemInHand
        if (item.type != Material.RED_BANNER) return
        val meta = item.itemMeta ?: return
        if (!meta.displayName().toString().contains("Флаг Трудового Фронта")) return

        if (event.block.type == Material.RED_WALL_BANNER) {
            event.isCancelled = true
            event.player.sendMessage(Component.text("§cФлаг нужно устанавливать на горизонтальную поверхность, товарищ!"))
            return
        }

        val player = event.player
        val location = event.block.location
        val world = checkNotNull(location.world) { "World is null" }.name

        // Check: player must have an Order
        val order = orderService.findByOwner(player.uniqueId)
        if (order == null) {
            event.isCancelled = true
            player.sendMessage(Component.text("§cУ вас нет Ордера, товарищ. Сначала получите жилплощадь через §e/партия"))
            return
        }

        // Check: Order must be activated
        if (order.centerWorld == null) {
            event.isCancelled = true
            player.sendMessage(Component.text("§cВаш Ордер ещё не активирован. Установите флаг Ордера на территории."))
            return
        }

        // Check: Front cannot be placed inside someone else's Order zone
        val allOrders = orderService.findAllInWorld(world)
        for (otherOrder in allOrders) {
            if (otherOrder.ownerUuid != player.uniqueId && isInsideOrder(otherOrder, location)) {
                event.isCancelled = true
                player.sendMessage(Component.text("§cНельзя установить Фронт на чужой жилплощади, товарищ!"))
                return
            }
        }

        val bannerBlock = event.block
        val ownerUuid = player.uniqueId

        val oldFront = workFrontService.getByOwner(ownerUuid)
        if (oldFront != null) {
            val oldWorld = Bukkit.getWorld(oldFront.centerWorld)
            if (oldWorld != null) {
                flagCleanupHelper.cleanupFlag(
                    world = oldWorld,
                    supportX = oldFront.centerX,
                    supportY = oldFront.centerY - 1,
                    supportZ = oldFront.centerZ,
                    bannerX = oldFront.centerX,
                    bannerY = oldFront.centerY,
                    bannerZ = oldFront.centerZ,
                    flagId = "front/$ownerUuid",
                    manager = manager,
                    dbDeleteFn = {}
                )
            }
        }

        // Pre-condition checks (world allowlist, air above, chunk limit)
        val checkResult = flagActivationHelper.checkPreconditions(bannerBlock, config, manager)
        if (checkResult is ActivationCheckResult.Denied) {
            event.isCancelled = true
            player.sendMessage(Component.text("§c${checkResult.reason}"))
            return
        }
        val chunkKey = (checkResult as ActivationCheckResult.Ok).chunkKey
        val lock = manager.getChunkLock(chunkKey)

        val isRelocation = oldFront != null
        val successMsg = if (isRelocation) {
            "§6☭ Старый Трудовой Фронт закрыт. Новый Трудовой Фронт активирован! Радиус: §e25 §6блоков."
        } else {
            "§6☭ Трудовой Фронт активирован! Радиус: §e25 §6блоков. Партия ждёт перевыполнения нормы!"
        }

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
                flagId = "front/$ownerUuid",
                ownerUuid = ownerUuid,
                ownerName = ownerName,
                flagType = "Трудовой Фронт",
                config = config,
                manager = manager,
                lock = acquiredLock,
                dbWrite = {
                    workFrontService.activate(ownerUuid, world, location.blockX, location.blockY, location.blockZ)
                },
                onSuccess = { p ->
                    p?.sendMessage(Component.text(successMsg))
                },
                onDbFailure = { p ->
                    p?.sendMessage(Component.text("§cОшибка активации Трудового Фронта. Попробуйте ещё раз."))
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

    private fun isInsideOrder(order: Order, loc: org.bukkit.Location): Boolean {
        if (order.centerWorld == null) return false
        if (loc.world?.name != order.centerWorld) return false
        val dx = abs(order.centerX - loc.blockX)
        val dz = abs(order.centerZ - loc.blockZ)
        return dx <= order.radius && dz <= order.radius
    }

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        val block = event.clickedBlock ?: return
        if (block.type != Material.RED_BANNER) return

        val player = event.player
        val front = workFrontService.getByOwner(player.uniqueId) ?: return

        val loc = block.location
        if (loc.world?.name != front.centerWorld) return
        if (loc.blockX != front.centerX || loc.blockY != front.centerY || loc.blockZ != front.centerZ) return

        event.isCancelled = true
        FrontMenu(workFrontService).open(player, front)
    }

    private companion object {
        const val LOCK_RETRY_TICKS = 100L
    }
}
