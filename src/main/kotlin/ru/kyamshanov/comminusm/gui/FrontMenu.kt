@file:Suppress("MagicNumber", "MaxLineLength")

package ru.kyamshanov.comminusm.gui

import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import ru.kyamshanov.comminusm.application.usecases.workfront.DeactivateWorkFrontUseCase
import ru.kyamshanov.comminusm.application.usecases.workfront.GetWorkFrontByOwnerUseCase
import ru.kyamshanov.comminusm.listener.FlagItemProtectionListener
import ru.kyamshanov.comminusm.model.WorkFront

class FrontMenu(
    private val getWorkFrontByOwnerUseCase: GetWorkFrontByOwnerUseCase,
    private val deactivateWorkFrontUseCase: DeactivateWorkFrontUseCase,
) : Listener {
    private val infoSlot = 20
    private val radiusSlot = 22
    private val moveSlot = 24
    private val backSlot = 39

    fun open(
        player: Player,
        front: WorkFront,
    ) {
        val mm = MiniMessage.miniMessage()
        val inv = Bukkit.createInventory(null, 45, mm.deserialize("<dark_gray>Трудовой Фронт"))
        GuiUtils.fillBorder(inv)

        inv.setItem(
            infoSlot,
            GuiUtils.namedItem(
                "<gold>Трудовой Фронт",
                Material.RED_BANNER,
                "<gray>Владелец: <yellow>${player.name}",
                "<gray>Мир: <yellow>${front.centerWorld}",
            ),
        )

        inv.setItem(
            radiusSlot,
            GuiUtils.namedItem(
                "<green>Радиус добычи",
                Material.COMPASS,
                "<gray>Радиус: <yellow>${front.radius} <gray>блоков",
                "<gray>Размер: <yellow>${front.size}×${front.size}×${front.size}",
            ),
        )

        inv.setItem(
            moveSlot,
            GuiUtils.namedItem(
                "<red>Перенести Фронт",
                Material.TNT,
                "<gray>Выдаст новый флаг для переноса",
                "<gray>Текущий фронт будет закрыт",
            ),
        )

        inv.setItem(backSlot, GuiUtils.namedItem("<red>Назад", Material.BARRIER))

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
                val mm = MiniMessage.miniMessage()
                if (FlagItemProtectionListener.hasFrontFlagInInventory(player)) {
                    player.sendMessage(mm.deserialize("<red>У вас уже есть флаг Трудового Фронта, товарищ! Установите его в мире."))
                    return
                }
                val frontRadius = getWorkFrontByOwnerUseCase(player.uniqueId)?.radius ?: 25
                deactivateWorkFrontUseCase(player.uniqueId)
                val flag = org.bukkit.inventory.ItemStack(Material.RED_BANNER)
                val meta = flag.itemMeta
                meta.displayName(mm.deserialize("<gold>Флаг Трудового Фронта"))
                meta.lore(
                    listOf(
                        mm.deserialize("<gray>Установите в новом месте"),
                        mm.deserialize("<gray>Радиус добычи: <yellow>$frontRadius <gray>блоков"),
                    ),
                )
                flag.itemMeta = meta
                val inv = player.inventory
                when {
                    inv.itemInOffHand.type == Material.AIR -> {
                        inv.setItemInOffHand(flag)
                        player.sendMessage(mm.deserialize("<gold>☭ Старый Фронт закрыт. Установите новый флаг, товарищ!"))
                    }
                    inv.firstEmpty() != -1 -> {
                        inv.addItem(flag)
                        player.sendMessage(mm.deserialize("<gold>☭ Старый Фронт закрыт. Установите новый флаг, товарищ!"))
                    }
                    else -> {
                        player.sendMessage(mm.deserialize("<red>Товарищ, освободите хотя бы 1 слот в инвентаре для флага Фронта!"))
                    }
                }
                player.closeInventory()
            }
            backSlot -> {
                player.closeInventory()
            }
        }
    }
}
