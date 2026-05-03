package ru.kyamshanov.comminusm.gui

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import ru.kyamshanov.comminusm.listener.FlagItemProtectionListener
import ru.kyamshanov.comminusm.model.WorkFront
import ru.kyamshanov.comminusm.service.WorkFrontService

class FrontMenu(
    private val workFrontService: WorkFrontService
) : Listener {
    private val infoSlot = 20
    private val radiusSlot = 22
    private val moveSlot = 24
    private val backSlot = 39

    fun open(player: Player, front: WorkFront) {
        val inv = Bukkit.createInventory(null, 45, Component.text("§8Трудовой Фронт"))
        GuiUtils.fillBorder(inv)

        inv.setItem(infoSlot, GuiUtils.namedItem(
            "§6Трудовой Фронт",
            Material.RED_BANNER,
            "§7Владелец: §e${player.name}",
            "§7Мир: §e${front.centerWorld}"
        ))

        inv.setItem(radiusSlot, GuiUtils.namedItem(
            "§aРадиус добычи",
            Material.COMPASS,
            "§7Радиус: §e${front.radius} §7блоков",
            "§7Размер: §e${front.size}×${front.size}×${front.size}"
        ))

        inv.setItem(moveSlot, GuiUtils.namedItem(
            "§cПеренести Фронт",
            Material.TNT,
            "§7Выдаст новый флаг для переноса",
            "§7Текущий фронт будет закрыт"
        ))

        inv.setItem(backSlot, GuiUtils.namedItem("§cНазад", Material.BARRIER))

        player.openInventory(inv)
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val title = event.view.title().toString()
        if (!title.contains("Трудовой Фронт")) return
        event.isCancelled = true

        val player = event.whoClicked as Player

        when (event.slot) {
            moveSlot -> {
                if (FlagItemProtectionListener.hasFrontFlagInInventory(player)) {
                    player.sendMessage(Component.text("§cУ вас уже есть флаг Трудового Фронта, товарищ! Установите его в мире или удалите из инвентаря."))
                    return
                }
                val frontRadius = workFrontService.getByOwner(player.uniqueId)?.radius ?: 25
                workFrontService.deactivate(player.uniqueId)
                val flag = org.bukkit.inventory.ItemStack(Material.RED_BANNER)
                val meta = flag.itemMeta
                meta.displayName(Component.text("§6Флаг Трудового Фронта"))
                meta.lore(listOf(
                    Component.text("§7Установите в новом месте"),
                    Component.text("§7Радиус добычи: §e${frontRadius} §7блоков")
                ))
                flag.itemMeta = meta
                if (player.inventory.firstEmpty() == -1) {
                    player.sendMessage(Component.text("§cТоварищ, освободите хотя бы 1 слот в инвентаре для флага Фронта!"))
                } else {
                    player.inventory.addItem(flag)
                    player.sendMessage(Component.text("§6☭ Старый Фронт закрыт. Установите новый флаг, товарищ!"))
                }
                player.closeInventory()
            }
            backSlot -> {
                player.closeInventory()
            }
        }
    }
}
