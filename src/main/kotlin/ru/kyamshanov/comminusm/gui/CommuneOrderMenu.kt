package ru.kyamshanov.comminusm.gui

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import ru.kyamshanov.comminusm.application.usecases.order.CheckOrderLeadershipUseCase
import ru.kyamshanov.comminusm.commune.service.OrderMembershipService

/**
 * Decorator pattern wrapper around OrderMenu.
 * Adds a "Участники" button at slot 22 to access order members management.
 * Visible only to order leaders and native members (AC-60).
 */
@Suppress("UnusedPrivateProperty")
class CommuneOrderMenu(
    private val checkOrderLeadershipUseCase: CheckOrderLeadershipUseCase,
    private val orderMembershipService: OrderMembershipService,
    private val orderMembersMenu: OrderMembersMenu,
) : Listener {
    @EventHandler(priority = EventPriority.HIGH)
    @Suppress("ReturnCount")
    fun onInventoryOpen(event: InventoryOpenEvent) {
        val title = event.view.title().toString()
        if (!title.contains("Ордер №")) return

        val player = event.player as Player
        val inv = event.view.topInventory

        val nativeOrders = orderMembershipService.getNativeOrdersOfPlayer(player.uniqueId)
        val isLeader = checkOrderLeadershipUseCase(player.uniqueId)

        // Only show button to leaders and native members
        if (nativeOrders.isNotEmpty() || isLeader) {
            inv.setItem(
                PARTICIPANTS_BUTTON_SLOT,
                GuiUtils.namedItem(
                    "§6Участники",
                    Material.PAPER,
                    "§7Управление участниками ордера",
                    "§8Нажми чтобы открыть",
                ),
            )
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    @Suppress("ReturnCount")
    fun onInventoryClick(event: InventoryClickEvent) {
        val title = event.view.title().toString()
        if (!title.contains("Ордер №")) return

        val slot = event.slot
        if (slot != PARTICIPANTS_BUTTON_SLOT) return

        event.isCancelled = true
        val player = event.whoClicked as Player

        val nativeOrders = orderMembershipService.getNativeOrdersOfPlayer(player.uniqueId)
        val isLeader = checkOrderLeadershipUseCase(player.uniqueId)

        // TC-121: Allow access if player is a native member OR the order leader
        if (nativeOrders.isEmpty() && !isLeader) {
            player.sendMessage(Component.text("§cВы не член этого ордера"))
            return
        }

        // Extract order ID from title (e.g., "§8Ордер №123" -> 123)
        val orderIdMatch = """Ордер №(\d+)""".toRegex().find(title)
        val orderId = orderIdMatch?.groupValues?.getOrNull(1)?.toLongOrNull()
        if (orderId == null) {
            player.sendMessage(Component.text("§cОшибка при открытии меню участников"))
            return
        }

        openOrderMembersMenu(player, orderId)
    }

    private fun openOrderMembersMenu(
        player: Player,
        orderId: Long,
    ) {
        orderMembersMenu.open(player, orderId)
    }

    companion object {
        const val PARTICIPANTS_BUTTON_SLOT = 23
    }
}
