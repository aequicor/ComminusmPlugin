package ru.kyamshanov.comminusm.listener

import net.kyori.adventure.text.Component
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
                orderService.deleteByOwner(uuid)
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
