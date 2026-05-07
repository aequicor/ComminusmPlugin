package ru.kyamshanov.comminusm.listener

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import ru.kyamshanov.comminusm.service.OrderService

class FlagDeletionConfirmListener(
    private val orderService: OrderService,
) : Listener {
    companion object {
        private const val CONFIRM_DELETION_TITLE = "Подтверждение удаления"
        private const val CONFIRM_SLOT = 2
        private const val CANCEL_SLOT = 6
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val title = event.view.title().toString()
        if (!title.contains(CONFIRM_DELETION_TITLE)) return
        event.isCancelled = true

        val player = event.whoClicked as Player
        val uuid = player.uniqueId

        when (event.slot) {
            CONFIRM_SLOT -> {
                // Delete order, drop custom flag, break block
                val order = orderService.findByOwner(uuid)
                orderService.deleteByOwner(uuid)

                if (order != null && order.centerWorld != null) {
                    val world = org.bukkit.Bukkit.getWorld(order.centerWorld)
                    if (world != null) {
                        // Place AIR at the flag location to break it
                        world.getBlockAt(order.centerX, order.centerY, order.centerZ).type = Material.AIR
                    }
                }

                player.sendMessage(Component.text("§c☭ Ордер аннулирован."))
                player.closeInventory()
            }
            CANCEL_SLOT -> {
                player.sendMessage(Component.text("§aУдаление отменено, товарищ."))
                player.closeInventory()
            }
        }
    }
}
