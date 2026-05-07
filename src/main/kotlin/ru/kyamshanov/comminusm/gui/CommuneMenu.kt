package ru.kyamshanov.comminusm.gui

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import ru.kyamshanov.comminusm.commune.service.CommuneInvitationService
import ru.kyamshanov.comminusm.commune.service.CommuneService
import ru.kyamshanov.comminusm.commune.service.OrderMembershipService
import ru.kyamshanov.comminusm.service.OrderService
import java.util.UUID

/**
 * Main GUI for commune management.
 * Displays member orders, invitations, and management buttons.
 * Layout: 45-slot inventory with border, content area, and footer.
 */
@Suppress("UnusedPrivateProperty")
class CommuneMenu(
    private val communeService: CommuneService,
    private val orderService: OrderService,
    @Suppress("UNUSED_PARAMETER")
    private val communeInvitationService: CommuneInvitationService,
    @Suppress("UNUSED_PARAMETER")
    private val orderMembershipService: OrderMembershipService,
) : Listener {

    @Suppress("LongMethod", "MagicNumber")
    fun open(player: Player, communeId: UUID) {
        val commune = communeService.getCommune(communeId)
            ?: return player.sendMessage(Component.text("§cКоммуна не найдена"))

        val isLeader = orderService.isLeader(player.uniqueId)

        val inv = Bukkit.createInventory(null, 45, Component.text("§8Коммуна"))
        GuiUtils.fillBorder(inv)

        // Header: commune info
        inv.setItem(
            HEADER_SLOT,
            GuiUtils.namedItem(
                "§aКоммуна",
                Material.PAPER,
                "§7Участники: §e${commune.orderIds.size}",
                "§7ID: §e${commune.id}",
            )
        )

        // Orders list (slots 19-34, max 7 orders for simple pagination)
        val orders = commune.orderIds.mapNotNull { orderService.getOrderById(it) }
        var slot = ORDERS_START_SLOT
        for (order in orders) {
            if (slot > ORDERS_END_SLOT) {
                // Placeholder: paging not implemented yet
                break
            }

            inv.setItem(
                slot,
                GuiUtils.namedItem(
                    "§6Ордер №${order.id}",
                    Material.WHITE_BANNER,
                    "§7Владелец: §e${Bukkit.getOfflinePlayer(order.ownerUuid).name}",
                    "§7Уровень: §e${order.level}",
                )
            )
            slot++
        }

        // Incoming invitations block (AC-30) — only for leader
        // Collect all invitations for orders in this commune
        val allInvitations = commune.orderIds.flatMap { orderId ->
            communeInvitationService.getInvitationsForOrder(orderId)
        }.toSet()

        if (allInvitations.isNotEmpty() && isLeader) {
            inv.setItem(
                INVITATIONS_SLOT,
                GuiUtils.namedItem(
                    "§cВходящее приглашение",
                    Material.REDSTONE,
                    "§7Количество: §e${allInvitations.size}",
                    "§7Нажми для управления",
                )
            )
        }

        // Management buttons (only for leader)
        if (isLeader) {
            inv.setItem(
                INVITE_BUTTON_SLOT,
                GuiUtils.namedItem(
                    "§eПригласить ордер",
                    Material.NETHER_STAR,
                    "§7Отправить приглашение союзнику",
                )
            )
            inv.setItem(
                LEAVE_BUTTON_SLOT,
                GuiUtils.namedItem(
                    "§cПокинуть коммуну",
                    Material.RED_DYE,
                    "§7Выйти из альянса",
                    "§8Все cross-order права будут отозваны",
                )
            )
        }

        // Back button
        inv.setItem(BACK_BUTTON_SLOT, GuiUtils.namedItem("§cНазад", Material.BARRIER))

        player.openInventory(inv)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val title = event.view.title().toString()
        if (!title.contains("Коммуна")) return

        event.isCancelled = true
        val player = event.whoClicked as Player

        when (event.slot) {
            BACK_BUTTON_SLOT -> {
                // Return to party menu
                player.sendMessage(Component.text("§aВозврат в меню партии (планируется)"))
            }
            INVITE_BUTTON_SLOT -> {
                player.sendMessage(Component.text("§aПригласить ордер (планируется)"))
            }
            LEAVE_BUTTON_SLOT -> {
                player.sendMessage(Component.text("§aПокинуть коммуну (планируется)"))
            }
        }
    }

    companion object {
        const val HEADER_SLOT = 10
        const val INVITATIONS_SLOT = 11
        const val ORDERS_START_SLOT = 19
        const val ORDERS_END_SLOT = 34
        const val INVITE_BUTTON_SLOT = 37
        const val LEAVE_BUTTON_SLOT = 40
        const val BACK_BUTTON_SLOT = 39
    }
}
