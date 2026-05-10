package ru.kyamshanov.comminusm.gui

import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.plugin.Plugin
import ru.kyamshanov.comminusm.application.usecases.order.CheckOrderLeadershipUseCase
import ru.kyamshanov.comminusm.application.usecases.order.GetOrderByIdUseCase
import ru.kyamshanov.comminusm.commune.service.CommuneInvitationService
import ru.kyamshanov.comminusm.commune.service.CommuneService
import java.util.UUID

/**
 * Main GUI for commune management.
 * Displays member orders, invitations, and management buttons.
 * Layout: 45-slot inventory with border, content area, and footer.
 */
@Suppress("LongParameterList")
class CommuneMenu(
    private val communeService: CommuneService,
    private val checkOrderLeadershipUseCase: CheckOrderLeadershipUseCase,
    private val getOrderByIdUseCase: GetOrderByIdUseCase,
    private val communeInvitationService: CommuneInvitationService,
    private val plugin: Plugin,
    private val partyMenu: PartyMenu,
) : Listener {
    @Suppress("LongMethod", "MagicNumber")
    fun open(
        player: Player,
        communeId: UUID,
    ) {
        val mm = MiniMessage.miniMessage()
        val commune =
            communeService.getCommune(communeId)
                ?: return player.sendMessage(mm.deserialize("<red>Коммуна не найдена"))

        val isLeader = checkOrderLeadershipUseCase(player.uniqueId)

        val inv = Bukkit.createInventory(null, 45, mm.deserialize("<dark_gray>Коммуна"))
        GuiUtils.fillBorder(inv)

        // Header: commune info
        inv.setItem(
            HEADER_SLOT,
            GuiUtils.namedItem(
                "<green>Коммуна",
                Material.PAPER,
                "<gray>Участники: <yellow>${commune.orderIds.size}",
                "<gray>ID: <yellow>${commune.id}",
            ),
        )

        // Orders list (slots 19-34, max 7 orders for simple pagination)
        val orders = commune.orderIds.mapNotNull { getOrderByIdUseCase(it) }
        var slot = ORDERS_START_SLOT
        for (order in orders) {
            if (slot > ORDERS_END_SLOT) {
                // Placeholder: paging not implemented yet
                break
            }

            inv.setItem(
                slot,
                GuiUtils.namedItem(
                    "<gold>Ордер №${order.id}",
                    Material.WHITE_BANNER,
                    "<gray>Владелец: <yellow>${Bukkit.getOfflinePlayer(order.ownerUuid).name}",
                    "<gray>Уровень: <yellow>${order.level}",
                ),
            )
            slot++
        }

        // Incoming invitations block (AC-30) — only for leader
        // Collect all invitations for orders in this commune
        val allInvitations =
            commune.orderIds
                .flatMap { orderId ->
                    communeInvitationService.getInvitationsForOrder(orderId)
                }.toSet()

        if (allInvitations.isNotEmpty() && isLeader) {
            inv.setItem(
                INVITATIONS_SLOT,
                GuiUtils.namedItem(
                    "<red>Входящее приглашение",
                    Material.REDSTONE,
                    "<gray>Количество: <yellow>${allInvitations.size}",
                    "<gray>Нажми для управления",
                ),
            )
        }

        // Management buttons (only for leader)
        if (isLeader) {
            inv.setItem(
                INVITE_BUTTON_SLOT,
                GuiUtils.namedItem(
                    "<yellow>Пригласить ордер",
                    Material.NETHER_STAR,
                    "<gray>Отправить приглашение союзнику",
                ),
            )
            inv.setItem(
                LEAVE_BUTTON_SLOT,
                GuiUtils.namedItem(
                    "<red>Покинуть коммуну",
                    Material.RED_DYE,
                    "<gray>Выйти из альянса",
                    "<dark_gray>Все cross-order права будут отозваны",
                ),
            )
        }

        // Back button
        inv.setItem(BACK_BUTTON_SLOT, GuiUtils.namedItem("<red>Назад", Material.BARRIER))

        Bukkit.getScheduler().runTask(plugin, Runnable { player.openInventory(inv) })
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val title = event.view.title().toString()
        if (!title.contains("Коммуна")) return

        event.isCancelled = true
        val player = event.whoClicked as Player

        when (event.slot) {
            BACK_BUTTON_SLOT -> {
                Bukkit.getScheduler().runTask(plugin, Runnable { partyMenu.open(player) })
            }
            INVITE_BUTTON_SLOT -> {
                player.sendMessage(MiniMessage.miniMessage().deserialize("<green>Пригласить ордер (планируется)"))
            }
            LEAVE_BUTTON_SLOT -> {
                player.sendMessage(MiniMessage.miniMessage().deserialize("<green>Покинуть коммуну (планируется)"))
            }
        }
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        val title = event.view.title().toString()
        if (!title.contains("Коммуна")) return

        // TC-155: Cancel ALL drag operations in the menu to prevent item dragging
        event.isCancelled = true
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
