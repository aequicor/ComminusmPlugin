package ru.kyamshanov.comminusm.gui

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import ru.kyamshanov.comminusm.config.PluginConfig
import ru.kyamshanov.comminusm.model.Order
import ru.kyamshanov.comminusm.service.OrderService
import ru.kyamshanov.comminusm.service.WorkdaysService

class OrderMenu(
    private val orderService: OrderService,
    private val workdaysService: WorkdaysService?,
    private val config: PluginConfig
) : Listener {
    private val infoSlot = 20
    private val sizeSlot = 22
    private val upgradeSlot = 24
    private val restoreSlot = 31
    private val backSlot = 39

    fun open(player: Player, order: Order) {
        val inv = Bukkit.createInventory(null, 45, Component.text("\u00a78\u041e\u0440\u0434\u0435\u0440 \u2116${order.id}"))
        GuiUtils.fillBorder(inv)

        inv.setItem(infoSlot, GuiUtils.namedItem(
            "\u00a7e\u041e\u0440\u0434\u0435\u0440 \u2116${order.id}",
            Material.WHITE_BANNER,
            "\u00a77\u0423\u0440\u043e\u0432\u0435\u043d\u044c: \u00a7e${order.level}/${orderService.getMaxLevel()}",
            "\u00a77\u0412\u043b\u0430\u0434\u0435\u043b\u0435\u0446: \u00a7e${player.name}"
        ))

        inv.setItem(sizeSlot, GuiUtils.namedItem(
            "\u00a7a\u0422\u0435\u0440\u0440\u0438\u0442\u043e\u0440\u0438\u044f",
            Material.GLASS,
            "\u00a77\u0420\u0430\u0437\u043c\u0435\u0440: \u00a7e${order.size}\u00d7${order.size}",
            "\u00a77\u0420\u0430\u0434\u0438\u0443\u0441: \u00a7e${order.radius} \u00a77\u0431\u043b\u043e\u043a\u043e\u0432",
            if (order.centerWorld != null) "\u00a77\u041c\u0438\u0440: \u00a7e${order.centerWorld}" else "\u00a7c\u041d\u0435 \u0430\u043a\u0442\u0438\u0432\u0438\u0440\u043e\u0432\u0430\u043d"
        ))

        val nextLevel = order.level + 1
        if (nextLevel <= orderService.getMaxLevel()) {
            val cost = orderService.getCostForLevel(nextLevel)
            val newRadius = orderService.getRadiusForLevel(nextLevel)
            val balance = workdaysService?.getBalance(player.uniqueId) ?: 0
            inv.setItem(upgradeSlot, GuiUtils.namedItem(
                "\u00a76\u0423\u043b\u0443\u0447\u0448\u0438\u0442\u044c \u0434\u043e \u0443\u0440\u043e\u0432\u043d\u044f $nextLevel",
                Material.NETHER_STAR,
                "\u00a77\u041d\u043e\u0432\u044b\u0439 \u0440\u0430\u0437\u043c\u0435\u0440: \u00a7e${newRadius * 2 + 1}\u00d7${newRadius * 2 + 1}",
                "\u00a77\u0421\u0442\u043e\u0438\u043c\u043e\u0441\u0442\u044c: \u00a7e$cost \u00a77\u0442\u0440\u0443\u0434\u043e\u0434\u043d\u0435\u0439",
                "\u00a77\u0412\u0430\u0448 \u0431\u0430\u043b\u0430\u043d\u0441: \u00a7e$balance \u00a77\u0442\u0440\u0443\u0434\u043e\u0434\u043d\u0435\u0439"
            ))
        }

        inv.setItem(restoreSlot, GuiUtils.namedItem(
            "\u00a7d\u0412\u043e\u0441\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u044c \u0444\u043b\u0430\u0433",
            Material.PAPER,
            "\u00a77\u0424\u043b\u0430\u0433 \u0432\u0435\u0440\u043d\u0451\u0442\u0441\u044f \u0432 \u0446\u0435\u043d\u0442\u0440 \u0443\u0447\u0430\u0441\u0442\u043a\u0430"
        ))

        inv.setItem(backSlot, GuiUtils.namedItem("\u00a7c\u041d\u0430\u0437\u0430\u0434", Material.BARRIER))

        player.openInventory(inv)
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val title = event.view.title().toString()
        if (!title.contains("\u041e\u0440\u0434\u0435\u0440 \u2116")) return
        event.isCancelled = true

        val player = event.whoClicked as Player

        when (event.slot) {
            upgradeSlot -> {
                val success = orderService.upgrade(player.uniqueId)
                if (success) {
                    val updatedOrder = orderService.findByOwner(player.uniqueId)
                    if (updatedOrder != null) {
                        player.sendMessage(Component.text("\u00a7a\u262d \u041f\u0430\u0440\u0442\u0438\u044f \u0440\u0430\u0441\u0448\u0438\u0440\u0438\u043b\u0430 \u0432\u0430\u0448\u0443 \u0436\u0438\u043b\u043f\u043b\u043e\u0449\u0430\u0434\u044c \u0434\u043e \u0443\u0440\u043e\u0432\u043d\u044f ${updatedOrder.level}. \u0421\u043b\u0430\u0432\u0430 \u0442\u0440\u0443\u0434\u0443!"))
                        open(player, updatedOrder)
                    }
                } else {
                    val order = orderService.findByOwner(player.uniqueId)
                    if (order != null) {
                        val nextLevel = order.level + 1
                        val cost = orderService.getCostForLevel(nextLevel)
                        val balance = workdaysService?.getBalance(player.uniqueId) ?: 0
                        val missing = cost - balance
                        player.sendMessage(Component.text("\u00a7c\u041d\u0435\u0434\u043e\u0441\u0442\u0430\u0442\u043e\u0447\u043d\u043e \u0442\u0440\u0443\u0434\u043e\u0434\u043d\u0435\u0439, \u0442\u043e\u0432\u0430\u0440\u0438\u0449. \u041d\u0435 \u0445\u0432\u0430\u0442\u0430\u0435\u0442: \u00a7e$missing"))
                    }
                }
            }
            restoreSlot -> {
                val order = orderService.findByOwner(player.uniqueId)
                if (order != null && order.centerWorld != null) {
                    val world = Bukkit.getWorld(order.centerWorld)
                    if (world != null) {
                        val flag = ItemStack(Material.WHITE_BANNER)
                        val meta = flag.itemMeta
                        meta.displayName(Component.text("\u00a7a\u0424\u043b\u0430\u0433 \u041e\u0440\u0434\u0435\u0440\u0430 \u2116${order.id}"))
                        meta.lore(listOf(
                            Component.text("\u00a77\u0423\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u0435 \u0444\u043b\u0430\u0433 \u0434\u043b\u044f \u0430\u043a\u0442\u0438\u0432\u0430\u0446\u0438\u0438 \u041e\u0440\u0434\u0435\u0440\u0430"),
                            Component.text("\u00a77\u0412\u043b\u0430\u0434\u0435\u043b\u0435\u0446: \u00a7e${player.name}")
                        ))
                        flag.itemMeta = meta
                        player.inventory.addItem(flag)
                        player.sendMessage(Component.text("\u00a7a\u0424\u043b\u0430\u0433 \u041e\u0440\u0434\u0435\u0440\u0430 \u0432\u043e\u0441\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d, \u0442\u043e\u0432\u0430\u0440\u0438\u0449!"))
                    }
                }
            }
            backSlot -> {
                val wds = workdaysService
                if (wds != null) {
                    PartyMenu(config, wds, orderService, null).open(player)
                }
            }
        }
    }
}
