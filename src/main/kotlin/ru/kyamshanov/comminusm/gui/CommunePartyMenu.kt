package ru.kyamshanov.comminusm.gui

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import ru.kyamshanov.comminusm.commune.service.CommuneService
import ru.kyamshanov.comminusm.service.OrderService

/**
 * Decorator pattern wrapper around PartyMenu.
 * Adds a "Коммуна" button at slot 13 to access commune management.
 * For leaders: button opens CommuneMenu.
 * For non-leaders: button is disabled with explanatory lore.
 */
@Suppress("UnusedPrivateProperty")
class CommunePartyMenu(
    @Suppress("UNUSED_PARAMETER")
    private val communeService: CommuneService,
    private val orderService: OrderService,
) : Listener {
    @EventHandler(priority = EventPriority.HIGH)
    @Suppress("ReturnCount")
    fun onInventoryOpen(event: InventoryOpenEvent) {
        val title = event.view.title().toString()
        if (!title.contains("Партийные услуги")) return

        val player = event.player as Player
        val inv = event.view.topInventory

        val isLeader = orderService.isLeader(player.uniqueId)

        if (isLeader) {
            inv.setItem(
                COMMUNE_BUTTON_SLOT,
                GuiUtils.namedItem(
                    "§aКоммуна",
                    Material.PAPER,
                    "§7Управление альянсом ордеров",
                    "§8Нажми чтобы открыть",
                ),
            )
        } else {
            inv.setItem(
                COMMUNE_BUTTON_SLOT,
                GuiUtils.namedItem(
                    "§7Коммуна",
                    Material.PAPER,
                    "§7Коммуну создаёт лидер ордера",
                    "§8(Отключено)",
                ),
            )
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    @Suppress("ReturnCount")
    fun onInventoryClick(event: InventoryClickEvent) {
        val title = event.view.title().toString()
        if (!title.contains("Партийные услуги")) return

        val slot = event.slot
        if (slot != COMMUNE_BUTTON_SLOT) return

        event.isCancelled = true
        val player = event.whoClicked as Player

        val isLeader = orderService.isLeader(player.uniqueId)
        if (!isLeader) {
            player.sendMessage(Component.text("§cТолько лидер ордера может управлять коммуной"))
            return
        }

        // Open commune menu
        openCommuneMenu(player)
    }

    private fun openCommuneMenu(player: Player) {
        // Placeholder: will be wired to CommuneMenu in full implementation
        player.sendMessage(Component.text("§aКоммуна (планируется)"))
    }

    companion object {
        const val COMMUNE_BUTTON_SLOT = 13
    }
}
