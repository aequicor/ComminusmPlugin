package ru.kyamshanov.comminusm.gui

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import ru.kyamshanov.comminusm.application.usecases.order.CheckOrderLeadershipUseCase
import ru.kyamshanov.comminusm.application.usecases.order.GetOrderByOwnerUseCase
import ru.kyamshanov.comminusm.commune.service.CommuneService

/**
 * Decorator pattern wrapper around PartyMenu.
 * Adds a "Коммуна" button at slot 13 to access commune management.
 * For leaders: button opens CommuneMenu or creates a new commune.
 * For non-leaders: button is disabled with explanatory lore.
 */
class CommunePartyMenu(
    private val checkOrderLeadershipUseCase: CheckOrderLeadershipUseCase,
    private val getOrderByOwnerUseCase: GetOrderByOwnerUseCase,
    private val communeService: CommuneService,
    private val communeMenu: CommuneMenu,
) : Listener {
    @EventHandler(priority = EventPriority.HIGH)
    @Suppress("ReturnCount")
    fun onInventoryOpen(event: InventoryOpenEvent) {
        val title = event.view.title().toString()
        if (!title.contains("Партийные услуги")) return

        val player = event.player as Player
        val inv = event.view.topInventory

        val isLeader = checkOrderLeadershipUseCase(player.uniqueId)

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

        // TC-155: Cancel ALL clicks in the menu to prevent item dragging
        event.isCancelled = true

        val slot = event.slot
        if (slot != COMMUNE_BUTTON_SLOT) return
        val player = event.whoClicked as Player

        val isLeader = checkOrderLeadershipUseCase(player.uniqueId)
        if (!isLeader) {
            player.sendMessage(Component.text("§cТолько лидер ордера может управлять коммуной"))
            return
        }

        // Open commune menu
        openCommuneMenu(player)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onInventoryDrag(event: InventoryDragEvent) {
        val title = event.view.title().toString()
        if (!title.contains("Партийные услуги")) return

        // TC-155: Cancel ALL drag operations in the menu to prevent item dragging
        event.isCancelled = true
    }

    @Suppress("ReturnCount")
    private fun openCommuneMenu(player: Player) {
        // Get the player's order
        val order = getOrderByOwnerUseCase(player.uniqueId)
        if (order == null) {
            player.sendMessage(Component.text("§cВы не владеете ордером"))
            return
        }

        // Check if order already has a commune
        val existingCommune = communeService.getCommuneOfOrder(order.id)
        if (existingCommune != null) {
            communeMenu.open(player, existingCommune.id)
            return
        }

        // Create a new commune with this order
        val result = communeService.createCommune(order.id, player.uniqueId)
        when (result) {
            is ru.kyamshanov.comminusm.commune.model.Result.Success -> {
                player.sendMessage(Component.text("§aКоммуна создана!"))
                communeMenu.open(player, result.data.id)
            }
            is ru.kyamshanov.comminusm.commune.model.Result.Failure -> {
                player.sendMessage(Component.text("§cОшибка: ${result.error}"))
            }
        }
    }

    companion object {
        const val COMMUNE_BUTTON_SLOT = 13
    }
}
