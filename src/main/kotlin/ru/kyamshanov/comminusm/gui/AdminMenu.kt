package ru.kyamshanov.comminusm.gui

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import ru.kyamshanov.comminusm.config.PluginConfig
import ru.kyamshanov.comminusm.service.OrderService
import ru.kyamshanov.comminusm.service.WorkFrontService
import ru.kyamshanov.comminusm.service.WorkdaysService

class AdminMenu(
    private val config: PluginConfig,
    private val orderService: OrderService?,
    private val workFrontService: WorkFrontService?,
    private val workdaysService: WorkdaysService?
) : Listener {
    private val deleteOrdersSlot = 11
    private val deleteFrontsSlot = 15
    private val statsSlot = 22
    private val backSlot = 26

    fun open(player: Player) {
        val inv = Bukkit.createInventory(null, 27, Component.text("\u00a7c\u0410\u0434\u043c\u0438\u043d-\u043f\u0430\u043d\u0435\u043b\u044c"))
        GuiUtils.fillBorder(inv)

        inv.setItem(deleteOrdersSlot, GuiUtils.namedItem(
            "\u00a7c\u0423\u0434\u0430\u043b\u0438\u0442\u044c \u0432\u0441\u0435 \u041e\u0440\u0434\u0435\u0440\u0430",
            Material.BARRIER,
            "\u00a77\u0412\u043d\u0438\u043c\u0430\u043d\u0438\u0435: \u044d\u0442\u043e \u0434\u0435\u0439\u0441\u0442\u0432\u0438\u0435 \u043d\u0435\u043e\u0431\u0440\u0430\u0442\u0438\u043c\u043e!"
        ))

        inv.setItem(deleteFrontsSlot, GuiUtils.namedItem(
            "\u00a7c\u0423\u0434\u0430\u043b\u0438\u0442\u044c \u0432\u0441\u0435 \u0424\u0440\u043e\u043d\u0442\u044b",
            Material.BARRIER,
            "\u00a77\u0412\u043d\u0438\u043c\u0430\u043d\u0438\u0435: \u044d\u0442\u043e \u0434\u0435\u0439\u0441\u0442\u0432\u0438\u0435 \u043d\u0435\u043e\u0431\u0440\u0430\u0442\u0438\u043c\u043e!"
        ))

        val orderCount = orderService?.findAllInWorld(player.world.name)?.size ?: 0
        val frontCount = workFrontService?.getAllInWorld(player.world.name)?.size ?: 0
        inv.setItem(statsSlot, GuiUtils.namedItem(
            "\u00a7e\u0421\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043a\u0430 \u043c\u0438\u0440\u0430",
            Material.BOOK,
            "\u00a77\u041e\u0440\u0434\u0435\u0440\u043e\u0432: \u00a7e$orderCount",
            "\u00a77\u0424\u0440\u043e\u043d\u0442\u043e\u0432: \u00a7e$frontCount"
        ))

        inv.setItem(backSlot, GuiUtils.namedItem("\u00a7c\u041d\u0430\u0437\u0430\u0434", Material.BARRIER))

        player.openInventory(inv)
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val title = event.view.title().toString()
        if (!title.contains("\u0410\u0434\u043c\u0438\u043d-\u043f\u0430\u043d\u0435\u043b\u044c")) return
        event.isCancelled = true

        val player = event.whoClicked as Player
        val world = player.world.name

        when (event.slot) {
            deleteOrdersSlot -> {
                val orders = orderService?.findAllInWorld(world) ?: emptyList()
                for (order in orders) {
                    orderService?.deleteByOwner(order.ownerUuid)
                }
                player.sendMessage(Component.text("\u00a7c\u262d \u0412\u0441\u0435 \u041e\u0440\u0434\u0435\u0440\u0430 \u0432 \u043c\u0438\u0440\u0435 \u0443\u0434\u0430\u043b\u0435\u043d\u044b."))
                player.closeInventory()
            }
            deleteFrontsSlot -> {
                val fronts = workFrontService?.getAllInWorld(world) ?: emptyList()
                for (front in fronts) {
                    workFrontService?.deactivate(front.ownerUuid)
                }
                player.sendMessage(Component.text("\u00a7c\u262d \u0412\u0441\u0435 \u0424\u0440\u043e\u043d\u0442\u044b \u0432 \u043c\u0438\u0440\u0435 \u0443\u0434\u0430\u043b\u0435\u043d\u044b."))
                player.closeInventory()
            }
            statsSlot -> {
                val orderCount = orderService?.findAllInWorld(world)?.size ?: 0
                val frontCount = workFrontService?.getAllInWorld(world)?.size ?: 0
                player.sendMessage(Component.text("\u00a7e\u262d \u0421\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043a\u0430 \u043c\u0438\u0440\u0430 \u00a7f$world\u00a7e:"))
                player.sendMessage(Component.text("\u00a77  \u041e\u0440\u0434\u0435\u0440\u043e\u0432: \u00a7e$orderCount"))
                player.sendMessage(Component.text("\u00a77  \u0424\u0440\u043e\u043d\u0442\u043e\u0432: \u00a7e$frontCount"))
            }
            backSlot -> {
                player.closeInventory()
            }
        }
    }
}
