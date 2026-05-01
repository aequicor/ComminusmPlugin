package ru.kyamshanov.comminusm.listener

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import ru.kyamshanov.comminusm.config.PluginConfig
import ru.kyamshanov.comminusm.gui.OrderMenu
import ru.kyamshanov.comminusm.service.OrderService
import ru.kyamshanov.comminusm.service.WorkdaysService

class OrderFlagListener(
    private val orderService: OrderService,
    private val workdaysService: WorkdaysService?,
    private val config: PluginConfig
) : Listener {

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        val item = event.itemInHand
        if (item.type != Material.WHITE_BANNER) return

        val meta = item.itemMeta ?: return
        val displayName = meta.displayName().toString()
        if (!displayName.contains("Флаг Ордера")) return

        val player = event.player
        val order = orderService.findByOwner(player.uniqueId)

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

        val location = event.block.location
        val success = orderService.activate(player.uniqueId, location)

        if (!success) {
            event.isCancelled = true
            player.sendMessage(Component.text("§cДанная территория уже распределена партией или слишком близка к соседскому наделу."))
            return
        }

        player.sendMessage(Component.text("§a☭ Ордер №${order.id} активирован! Ваша жилплощадь: §e${order.size}×${order.size} §aблоков."))
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
        OrderMenu(orderService, workdaysService, config).open(player, order)
    }
}
