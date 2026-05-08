package ru.kyamshanov.comminusm.gui

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import ru.kyamshanov.comminusm.application.usecases.order.CheckOrderLeadershipUseCase
import ru.kyamshanov.comminusm.application.usecases.order.GetOrderByIdUseCase
import ru.kyamshanov.comminusm.commune.service.OrderMembershipService

/**
 * Decorator pattern wrapper around OrderMenu.
 * Adds a "Участники" button at slot 44 to access order members management.
 * Visible only to order leaders and native members (AC-60).
 */
@Suppress("UnusedPrivateProperty")
class CommuneOrderMenu(
    private val checkOrderLeadershipUseCase: CheckOrderLeadershipUseCase,
    private val orderMembershipService: OrderMembershipService,
    private val orderMembersMenu: OrderMembersMenu,
    private val getOrderByIdUseCase: GetOrderByIdUseCase,
    private val plugin: Plugin,
) : Listener {

    private val orderIdKey = NamespacedKey(plugin, OrderMenu.ORDER_ID_PDC_KEY)

    private fun extractOrderId(topInventory: Inventory): Long? =
        topInventory.getItem(OrderMenu.INFO_SLOT)
            ?.itemMeta
            ?.persistentDataContainer
            ?.get(orderIdKey, PersistentDataType.LONG)

    @EventHandler(priority = EventPriority.HIGH)
    @Suppress("ReturnCount")
    fun onInventoryOpen(event: InventoryOpenEvent) {
        val title = event.view.title().toString()
        if (!title.contains("Ордер - ")) return

        val player = event.player as Player
        val inv = event.view.topInventory

        val nativeOrders = orderMembershipService.getNativeOrdersOfPlayer(player.uniqueId)
        val isLeader = checkOrderLeadershipUseCase(player.uniqueId)

        // Only show button to leaders and native members
        if (nativeOrders.isNotEmpty() || isLeader) {
            val skull = buildParticipantsButton(inv)
            inv.setItem(PARTICIPANTS_BUTTON_SLOT, skull)
        }
    }

    private fun buildParticipantsButton(topInventory: Inventory): ItemStack {
        val skull =
            GuiUtils.namedItem(
                "§6Участники",
                Material.PLAYER_HEAD,
                "§7Управление участниками ордера",
                "§8Нажми чтобы открыть",
            )

        val orderId = extractOrderId(topInventory)
        if (orderId != null) {
            val order = getOrderByIdUseCase(orderId)
            if (order != null) {
                val meta = skull.itemMeta as? org.bukkit.inventory.meta.SkullMeta
                if (meta != null) {
                    meta.owningPlayer = org.bukkit.Bukkit.getOfflinePlayer(order.ownerUuid)
                    skull.itemMeta = meta
                }
            }
        }

        return skull
    }

    @EventHandler(priority = EventPriority.LOWEST)
    @Suppress("ReturnCount")
    fun onInventoryClick(event: InventoryClickEvent) {
        val title = event.view.title().toString()
        if (!title.contains("Ордер - ")) return

        // TC-155: Cancel ALL clicks in the menu to prevent item dragging
        event.isCancelled = true

        val slot = event.slot
        if (slot != PARTICIPANTS_BUTTON_SLOT) return
        val player = event.whoClicked as Player

        val nativeOrders = orderMembershipService.getNativeOrdersOfPlayer(player.uniqueId)
        val isLeader = checkOrderLeadershipUseCase(player.uniqueId)

        // TC-121: Allow access if player is a native member OR the order leader
        if (nativeOrders.isEmpty() && !isLeader) {
            player.sendMessage(Component.text("§cВы не член этого ордера"))
            return
        }

        val orderId = extractOrderId(event.view.topInventory)
        if (orderId == null) {
            player.sendMessage(Component.text("§cОшибка при открытии меню участников"))
            return
        }

        openOrderMembersMenu(player, orderId)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onInventoryDrag(event: InventoryDragEvent) {
        val title = event.view.title().toString()
        if (!title.contains("Ордер - ")) return

        // TC-155: Cancel ALL drag operations in the menu to prevent item dragging
        event.isCancelled = true
    }

    private fun openOrderMembersMenu(
        player: Player,
        orderId: Long,
    ) {
        orderMembersMenu.open(player, orderId)
    }

    companion object {
        const val PARTICIPANTS_BUTTON_SLOT = 44
    }
}
