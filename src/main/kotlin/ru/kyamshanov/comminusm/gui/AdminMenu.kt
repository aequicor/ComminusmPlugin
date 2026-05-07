package ru.kyamshanov.comminusm.gui

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import ru.kyamshanov.comminusm.service.OrderService
import ru.kyamshanov.comminusm.service.WorkFrontService

class AdminMenu(
    private val orderService: OrderService?,
    private val workFrontService: WorkFrontService?
) : Listener {

    fun open(player: Player) {
        val inv = Bukkit.createInventory(
            null,
            GuiConstants.SMALL_INVENTORY_SIZE,
            Component.text(GuiConstants.ADMIN_PANEL_TITLE)
        )
        GuiUtils.fillBorder(inv)

        inv.setItem(GuiConstants.ADMIN_DELETE_ORDERS_SLOT, GuiUtils.namedItem(
            GuiConstants.DELETE_ORDERS_TEXT,
            Material.BARRIER,
            GuiConstants.DELETE_WARNING
        ))

        inv.setItem(GuiConstants.ADMIN_DELETE_FRONTS_SLOT, GuiUtils.namedItem(
            GuiConstants.DELETE_FRONTS_TEXT,
            Material.BARRIER,
            GuiConstants.DELETE_WARNING
        ))

        val orderCount = orderService?.findAllInWorld(player.world.name)?.size ?: 0
        val frontCount = workFrontService?.getAllInWorld(player.world.name)?.size ?: 0
        inv.setItem(GuiConstants.ADMIN_STATS_SLOT, GuiUtils.namedItem(
            GuiConstants.STATS_TEXT,
            Material.BOOK,
            "${GuiConstants.ORDERS_PREFIX}$orderCount",
            "${GuiConstants.FRONTS_PREFIX}$frontCount"
        ))

        inv.setItem(GuiConstants.ADMIN_BACK_SLOT, GuiUtils.namedItem(GuiConstants.BACK_TEXT, Material.BARRIER))

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
            GuiConstants.ADMIN_DELETE_ORDERS_SLOT -> deleteAllOrders(player, world)
            GuiConstants.ADMIN_DELETE_FRONTS_SLOT -> deleteAllFronts(player, world)
            GuiConstants.ADMIN_STATS_SLOT -> showWorldStats(player, world)
            GuiConstants.ADMIN_BACK_SLOT -> player.closeInventory()
        }
    }

    private fun deleteAllOrders(player: Player, world: String) {
        val orders = orderService?.findAllInWorld(world) ?: emptyList()
        for (order in orders) {
            orderService?.deleteByOwner(order.ownerUuid)
        }
        player.sendMessage(Component.text(GuiConstants.ORDERS_DELETED_TEXT))
        player.closeInventory()
    }

    private fun deleteAllFronts(player: Player, world: String) {
        val fronts = workFrontService?.getAllInWorld(world) ?: emptyList()
        for (front in fronts) {
            workFrontService?.deactivate(front.ownerUuid)
        }
        player.sendMessage(Component.text(GuiConstants.FRONTS_DELETED_TEXT))
        player.closeInventory()
    }

    private fun showWorldStats(player: Player, world: String) {
        val orderCount = orderService?.findAllInWorld(world)?.size ?: 0
        val frontCount = workFrontService?.getAllInWorld(world)?.size ?: 0
        player.sendMessage(Component.text("${GuiConstants.STATS_PREFIX}\u00a7f$world\u00a7e:"))
        player.sendMessage(Component.text("\u00a77  \u041e\u0440\u0434\u0435\u0440\u043e\u0432: \u00a7e$orderCount"))
        player.sendMessage(Component.text("\u00a77  \u0424\u0440\u043e\u043d\u0442\u043e\u0432: \u00a7e$frontCount"))
    }
}
