package ru.kyamshanov.comminusm.gui

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import ru.kyamshanov.comminusm.application.usecases.order.CheckOrderLeadershipUseCase
import ru.kyamshanov.comminusm.commune.service.OrderMembershipService

/**
 * GUI for viewing and managing native and cross-order members of an order.
 * Accessible from CommuneMenu or CommuneOrderMenu.
 * Leader-only: can add/remove members.
 */
@Suppress("MagicNumber")
class OrderMembersMenu(
    private val checkOrderLeadershipUseCase: CheckOrderLeadershipUseCase,
    private val orderMembershipService: OrderMembershipService,
) : Listener {
    private val occupiedMemberSlots = mutableSetOf<Int>()

    fun open(
        player: Player,
        orderId: Long,
    ) {
        val isLeader = checkOrderLeadershipUseCase(player.uniqueId)

        val inv = Bukkit.createInventory(null, 45, Component.text("§8Участники ордера №$orderId"))
        GuiUtils.fillBorder(inv)

        // Header: member count
        val members = orderMembershipService.getMembersOfOrder(orderId)
        inv.setItem(
            HEADER_SLOT,
            GuiUtils.namedItem(
                "§6Участники",
                Material.PAPER,
                "§7Количество: §e${members.size}",
            ),
        )

        // List members (slots 19-34, max 7 members for simple pagination)
        // Track which slots have actual members for click event handling
        var slot = MEMBERS_START_SLOT
        occupiedMemberSlots.clear()
        for (member in members) {
            if (slot > MEMBERS_END_SLOT) {
                // Placeholder: paging not implemented yet
                break
            }

            val grantedViaText =
                when (member.grantedVia) {
                    "native" -> "§aНативный"
                    "commune" -> "§eКоммунный"
                    else -> "§7Неизвестно"
                }

            val playerName = Bukkit.getOfflinePlayer(member.playerUuid).name ?: "Неизвестный игрок"
            inv.setItem(
                slot,
                GuiUtils.namedItem(
                    "§7$playerName",
                    Material.PLAYER_HEAD,
                    grantedViaText,
                    if (isLeader) "§8Нажми для исключения" else "§8(Только для лидера)",
                ),
            )
            occupiedMemberSlots.add(slot)

            slot++
        }

        // Invite button (only for leader)
        if (isLeader) {
            inv.setItem(
                INVITE_BUTTON_SLOT,
                GuiUtils.namedItem(
                    "§eПригласить участника",
                    Material.NETHER_STAR,
                    "§7Пригласить игрока в этот ордер",
                ),
            )
        }

        // Back
        inv.setItem(BACK_BUTTON_SLOT, GuiUtils.namedItem("§cНазад", Material.BARRIER))

        player.openInventory(inv)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val title = event.view.title().toString()
        if (!title.startsWith("§8Участники ордера")) return

        // TC-155: Cancel ALL clicks in the menu to prevent item dragging
        event.isCancelled = true

        val player = event.whoClicked as Player

        when {
            event.slot == BACK_BUTTON_SLOT -> {
                // Back to previous menu
                player.sendMessage(Component.text("§aВозврат в меню (планируется)"))
            }
            event.slot == INVITE_BUTTON_SLOT -> {
                // Show invite player menu
                player.sendMessage(Component.text("§aПригласить участника (планируется)"))
            }
            event.slot in occupiedMemberSlots -> {
                // If leader: remove member
                player.sendMessage(Component.text("§aУдалить участника (планируется)"))
            }
        }
    }

    companion object {
        const val HEADER_SLOT = 10
        const val MEMBERS_START_SLOT = 19
        const val MEMBERS_END_SLOT = 34
        const val INVITE_BUTTON_SLOT = 37
        const val BACK_BUTTON_SLOT = 39
    }
}
