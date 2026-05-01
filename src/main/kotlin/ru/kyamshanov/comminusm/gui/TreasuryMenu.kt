package ru.kyamshanov.comminusm.gui

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import ru.kyamshanov.comminusm.config.PluginConfig
import ru.kyamshanov.comminusm.service.WorkdaysService

class TreasuryMenu(
    private val config: PluginConfig,
    private val workdaysService: WorkdaysService
) : Listener {
    private val submitSlot = 31
    private val backSlot = 39
    private val borderSlots = setOf(0,1,2,3,4,5,6,7,8, 9,17,18,26,27,35, 36,37,38,39,40,41,42,43,44)

    private val submitItem = GuiUtils.namedItem(
        "§aСдать ресурсы в казну",
        Material.EMERALD,
        "§7Партия оценит ваш вклад в общее дело!"
    )

    fun open(player: Player) {
        val inv = Bukkit.createInventory(null, 45, Component.text("§8Казна трудового коллектива"))
        GuiUtils.fillBorder(inv)

        inv.setItem(backSlot, GuiUtils.namedItem("§cНазад", Material.BARRIER))
        inv.setItem(submitSlot, submitItem)

        player.openInventory(inv)
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val title = event.view.title().toString()
        if (!title.contains("Казна")) return

        if (event.slot == submitSlot) {
            event.isCancelled = true
            processDeposit(event.whoClicked as Player, event.inventory)
        } else if (event.slot == backSlot) {
            event.isCancelled = true
            returnItems(event.whoClicked as Player, event.inventory)
            event.whoClicked.closeInventory()
        } else if (event.slot !in borderSlots) {
            event.isCancelled = false
        } else {
            event.isCancelled = true
        }
    }

    private fun processDeposit(player: Player, inv: org.bukkit.inventory.Inventory) {
        val rates = config.resourceRates
        var totalEarned = 0

        for (slot in 0..44) {
            if (slot == submitSlot || slot == backSlot || slot in borderSlots) continue
            val item = inv.getItem(slot) ?: continue
            val rate = rates[item.type.name] ?: continue
            if (rate <= 0) continue

            totalEarned += (rate * item.amount) / 64
            inv.setItem(slot, null)
        }

        if (totalEarned > 0) {
            workdaysService.earn(player.uniqueId, totalEarned)
            player.sendMessage(Component.text("§a☭ Партия благодарит за вклад! Зачислено §e$totalEarned §aтрудодней."))
            val currentBalance = workdaysService.getBalance(player.uniqueId)
            player.sendMessage(Component.text("§7Текущий баланс: §e$currentBalance §7трудодней."))
        } else {
            player.sendMessage(Component.text("§cВ казне нет подходящих ресурсов, товарищ."))
        }
    }

    private fun returnItems(player: Player, inv: org.bukkit.inventory.Inventory) {
        for (slot in 0..44) {
            if (slot == submitSlot || slot == backSlot || slot in borderSlots) continue
            val item = inv.getItem(slot) ?: continue
            if (item.type == Material.GRAY_STAINED_GLASS_PANE) continue
            val excess = player.inventory.addItem(item)
            excess.forEach { (_, overflow) ->
                player.world.dropItem(player.location, overflow)
            }
        }
    }

    @EventHandler
    fun onClose(event: InventoryCloseEvent) {
        val title = event.view.title().toString()
        if (!title.contains("Казна")) return
        returnItems(event.player as Player, event.inventory)
    }
}
