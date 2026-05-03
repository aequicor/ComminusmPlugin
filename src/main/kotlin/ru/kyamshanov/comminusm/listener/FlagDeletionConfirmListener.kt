package ru.kyamshanov.comminusm.listener

import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import ru.kyamshanov.comminusm.service.OrderService
import ru.kyamshanov.comminusm.service.WorkFrontService

class FlagDeletionConfirmListener(
    private val orderService: OrderService,
    private val workFrontService: WorkFrontService?
) : Listener {

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val title = event.view.title().toString()
        if (!title.contains("Подтверждение удаления")) return
        event.isCancelled = true

        val player = event.whoClicked as Player
        val uuid = player.uniqueId

        when (event.slot) {
            2 -> {
                // Delete order, drop custom flag, break block
                val order = orderService.findByOwner(uuid)
                orderService.deleteByOwner(uuid)
                
                if (order != null && order.centerWorld != null) {
                    val world = org.bukkit.Bukkit.getWorld(order.centerWorld)
                    if (world != null) {
                        // Place AIR at the flag location to break it
                        world.getBlockAt(order.centerX, order.centerY, order.centerZ).type = Material.AIR
                        
                        // Drop custom order flag
                        val flag = org.bukkit.inventory.ItemStack(Material.WHITE_BANNER)
                        val meta = flag.itemMeta
                        meta.displayName(Component.text("§aФлаг Ордера №${order.id}"))
                        meta.lore(listOf(
                            Component.text("§7Установите флаг для активации Ордера"),
                            Component.text("§7Владелец: §e${player.name}")
                        ))
                        flag.itemMeta = meta
                        world.dropItemNaturally(
                            Location(world, order.centerX.toDouble(), order.centerY.toDouble(), order.centerZ.toDouble()),
                            flag
                        )
                    }
                }
                
                player.sendMessage(Component.text("§c☭ Ордер аннулирован. Флаг удалён."))
                player.closeInventory()
            }
            6 -> {
                player.sendMessage(Component.text("§aУдаление отменено, товарищ."))
                player.closeInventory()
            }
        }
    }
}
