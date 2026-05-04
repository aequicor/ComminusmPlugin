package ru.kyamshanov.comminusm.listener

import kotlin.math.abs
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import ru.kyamshanov.comminusm.gui.FrontMenu
import ru.kyamshanov.comminusm.model.Order
import ru.kyamshanov.comminusm.service.OrderService
import ru.kyamshanov.comminusm.service.WorkFrontService

class FrontFlagListener(
    private val workFrontService: WorkFrontService,
    private val orderService: OrderService
) : Listener {

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        val item = event.itemInHand
        if (item.type != Material.RED_BANNER) return
        val meta = item.itemMeta ?: return
        val displayName = meta.displayName().toString()
        if (!displayName.contains("Флаг Трудового Фронта")) return

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

        val oldFront = workFrontService.getByOwner(player.uniqueId)
        // Remove old banner block from the world to prevent orphaned banners
        if (oldFront != null) {
            val oldWorld = org.bukkit.Bukkit.getWorld(oldFront.centerWorld)
            if (oldWorld != null) {
                val oldBlock = oldWorld.getBlockAt(oldFront.centerX, oldFront.centerY, oldFront.centerZ)
                if (oldBlock.type == Material.RED_BANNER) {
                    oldBlock.type = Material.AIR
                }
            }
        }
        workFrontService.activate(player.uniqueId, world, location.blockX, location.blockY, location.blockZ)

        val msg = if (oldFront != null) {
            "§6☭ Старый Трудовой Фронт закрыт. Новый Трудовой Фронт активирован! Радиус: §e25 §6блоков."
        } else {
            "§6☭ Трудовой Фронт активирован! Радиус: §e25 §6блоков. Партия ждёт перевыполнения нормы!"
        }
        player.sendMessage(Component.text(msg))
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
}
