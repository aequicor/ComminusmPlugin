package ru.kyamshanov.comminusm.gui

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import ru.kyamshanov.comminusm.infrastructure.config.PluginConfig
import ru.kyamshanov.comminusm.service.WorkdaysService

class TreasuryMenu(
    private val config: PluginConfig,
    private val workdaysService: WorkdaysService,
) : Listener {
    private val submitItem =
        GuiUtils.namedItem(
            "§aСдать ресурсы в казну",
            Material.EMERALD,
            "§7Партия оценит ваш вклад в общее дело!",
        )

    fun open(player: Player) {
        val inv =
            Bukkit.createInventory(
                null,
                GuiConstants.TREASURY_MENU_INVENTORY_SIZE,
                Component.text("§8Казна трудового коллектива"),
            )
        GuiUtils.fillBorder(inv)

        inv.setItem(GuiConstants.TREASURY_MENU_BACK_SLOT, GuiUtils.namedItem("§cНазад", Material.BARRIER))
        inv.setItem(GuiConstants.TREASURY_MENU_SUBMIT_SLOT, submitItem)

        player.openInventory(inv)
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val title = event.view.title().toString()
        if (!title.contains("Казна")) return

        event.isCancelled =
            when (event.slot) {
                GuiConstants.TREASURY_MENU_SUBMIT_SLOT -> {
                    processDeposit(event.whoClicked as Player, event.inventory)
                    true
                }
                GuiConstants.TREASURY_MENU_BACK_SLOT -> {
                    returnItems(event.whoClicked as Player, event.inventory)
                    event.whoClicked.closeInventory()
                    true
                }
                in GuiConstants.TREASURY_MENU_BORDER_SLOTS -> true
                else -> false
            }
    }

    @Suppress("LoopWithTooManyJumpStatements")
    private fun processDeposit(
        player: Player,
        inv: org.bukkit.inventory.Inventory,
    ) {
        val rates = config.resourceRates
        var totalEarned = 0

        for (slot in 0..GuiConstants.TREASURY_MENU_INVENTORY_MAX_SLOT) {
            if (shouldSkipSlot(slot)) continue
            val item = inv.getItem(slot) ?: continue
            val rate = rates[item.type.name] ?: continue
            if (rate <= 0) continue

            totalEarned += (rate * item.amount) / GuiConstants.INVENTORY_SPLIT_FACTOR
            inv.setItem(slot, null)
        }

        if (totalEarned > 0) {
            workdaysService.earn(player.uniqueId, totalEarned)
            player.sendMessage(
                Component.text(
                    "§a☭ Партия благодарит за вклад! Зачислено §e$totalEarned §aтрудодней.",
                ),
            )
            val currentBalance = workdaysService.getBalance(player.uniqueId)
            player.sendMessage(Component.text("§7Текущий баланс: §e$currentBalance §7трудодней."))
        } else {
            player.sendMessage(Component.text("§cВ казне нет подходящих ресурсов, товарищ."))
        }
    }

    @Suppress("LoopWithTooManyJumpStatements")
    private fun returnItems(
        player: Player,
        inv: org.bukkit.inventory.Inventory,
    ) {
        for (slot in 0..GuiConstants.TREASURY_MENU_INVENTORY_MAX_SLOT) {
            if (shouldSkipSlot(slot)) continue
            val item = inv.getItem(slot) ?: continue
            if (item.type == Material.GRAY_STAINED_GLASS_PANE) continue
            val excess = player.inventory.addItem(item)
            excess.forEach { (_, overflow) ->
                player.world.dropItem(player.location, overflow)
            }
        }
    }

    private fun shouldSkipSlot(slot: Int): Boolean =
        slot == GuiConstants.TREASURY_MENU_SUBMIT_SLOT ||
            slot == GuiConstants.TREASURY_MENU_BACK_SLOT ||
            slot in GuiConstants.TREASURY_MENU_BORDER_SLOTS

    @EventHandler
    fun onClose(event: InventoryCloseEvent) {
        val title = event.view.title().toString()
        if (!title.contains("Казна")) return
        returnItems(event.player as Player, event.inventory)
    }
}
