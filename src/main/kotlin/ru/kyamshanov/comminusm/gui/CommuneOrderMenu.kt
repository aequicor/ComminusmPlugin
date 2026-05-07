package ru.kyamshanov.comminusm.gui

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import ru.kyamshanov.comminusm.commune.service.OrderMembershipService
import ru.kyamshanov.comminusm.service.OrderService

/**
 * Decorator pattern wrapper around OrderMenu.
 * Adds a "Участники" button at slot 22 to access order members management.
 * Visible only to order leaders and native members (AC-60).
 */
@Suppress("UnusedPrivateProperty")
class CommuneOrderMenu(
    @Suppress("UNUSED_PARAMETER")
    private val orderService: OrderService,
    private val orderMembershipService: OrderMembershipService,
) : Listener {

    @EventHandler(priority = EventPriority.HIGH)
    @Suppress("ReturnCount")
    fun onInventoryOpen(event: InventoryOpenEvent) {
        val title = event.view.title().toString()
        if (!title.contains("Ордер №")) return

        val player = event.player as Player
        val inv = event.view.topInventory

        val nativeOrders = orderMembershipService.getNativeOrdersOfPlayer(player.uniqueId)
        val isLeader = orderService.isLeader(player.uniqueId)

        // Only show button to leaders and native members
        if (nativeOrders.isNotEmpty() || isLeader) {
            inv.setItem(
                PARTICIPANTS_BUTTON_SLOT,
                GuiUtils.namedItem(
                    "§6Участники",
                    Material.PAPER,
                    "§7Управление участниками ордера",
                    "§8Нажми чтобы открыть",
                )
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
        if (nativeOrders.isEmpty()) {
            player.sendMessage(Component.text("§cВы не член этого ордера"))
            return
        }

        // Open order members menu for the first (and typically only) native order
        openOrderMembersMenu(player, nativeOrders.first())
    }

    @Suppress("UnusedParameter")
    private fun openOrderMembersMenu(player: Player, orderId: Long) {
        // Placeholder: will be wired to OrderMembersMenu in full implementation
        player.sendMessage(Component.text("§aУчастники ордера (планируется)"))
    }

    companion object {
        const val PARTICIPANTS_BUTTON_SLOT = 22
    }
}
