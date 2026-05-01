package ru.kyamshanov.comminusm.gui

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import ru.kyamshanov.comminusm.model.WorkFront
import ru.kyamshanov.comminusm.service.WorkFrontService

class FrontMenu(
    private val workFrontService: WorkFrontService
) : Listener {

    fun open(player: Player, front: WorkFront) {
        val inv = Bukkit.createInventory(null, 45, Component.text("\u00a78\u0422\u0440\u0443\u0434\u043e\u0432\u043e\u0439 \u0424\u0440\u043e\u043d\u0442"))
        GuiUtils.fillBorder(inv)

        player.openInventory(inv)
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val title = event.view.title().toString()
        if (!title.contains("\u0422\u0440\u0443\u0434\u043e\u0432\u043e\u0439 \u0424\u0440\u043e\u043d\u0442")) return
        event.isCancelled = true
    }
}
