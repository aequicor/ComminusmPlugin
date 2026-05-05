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
import ru.kyamshanov.comminusm.service.OrderService
import ru.kyamshanov.comminusm.service.WorkFrontService
import ru.kyamshanov.comminusm.listener.FlagItemProtectionListener
import ru.kyamshanov.comminusm.service.WorkdaysService

class PartyMenu(
    private val config: PluginConfig,
    private val workdaysService: WorkdaysService?,
    private val orderService: OrderService?,
    private val workFrontService: WorkFrontService?
) : Listener {
    private val orderSlot = 20
    private val frontSlot = 24
    private val treasurySlot = 31
    private val balanceSlot = 40

    fun open(player: Player) {
        val inv = Bukkit.createInventory(null, 45, Component.text("\u00a78\u041f\u0430\u0440\u0442\u0438\u0439\u043d\u044b\u0435 \u0443\u0441\u043b\u0443\u0433\u0438"))
        GuiUtils.fillBorder(inv)

        val uuid = player.uniqueId
        val hasOrder = orderService?.findByOwner(uuid) != null
        val hasFront = workFrontService?.getByOwner(uuid) != null

        inv.setItem(orderSlot, GuiUtils.namedItem(
            if (hasOrder) "\u00a7e\u0423\u043f\u0440\u0430\u0432\u043b\u0435\u043d\u0438\u0435 \u041e\u0440\u0434\u0435\u0440\u043e\u043c" else "\u00a7a\u041f\u043e\u043b\u0443\u0447\u0438\u0442\u044c \u041e\u0440\u0434\u0435\u0440",
            Material.WHITE_BANNER,
            if (hasOrder) "\u00a77\u0423\u043f\u0440\u0430\u0432\u043b\u0435\u043d\u0438\u0435 \u0432\u0430\u0448\u0435\u0439 \u0436\u0438\u043b\u043f\u043b\u043e\u0449\u0430\u0434\u044c\u044e" else "\u00a77\u041f\u0430\u0440\u0442\u0438\u044f \u0432\u044b\u0434\u0435\u043b\u0438\u0442 \u0432\u0430\u043c \u0436\u0438\u043b\u043f\u043b\u043e\u0449\u0430\u0434\u044c"
        ))

        inv.setItem(frontSlot, GuiUtils.namedItem(
            "\u00a76\u0422\u0440\u0443\u0434\u043e\u0432\u043e\u0439 \u0444\u0440\u043e\u043d\u0442",
            Material.NETHERITE_PICKAXE,
            if (hasFront) "\u00a77\u0423\u043f\u0440\u0430\u0432\u043b\u0435\u043d\u0438\u0435 \u0442\u0440\u0443\u0434\u043e\u0432\u044b\u043c \u0444\u0440\u043e\u043d\u0442\u043e\u043c" else "\u00a77\u0410\u043a\u0442\u0438\u0432\u0438\u0440\u043e\u0432\u0430\u0442\u044c \u0442\u0440\u0443\u0434\u043e\u0432\u043e\u0439 \u0444\u0440\u043e\u043d\u0442"
        ))

        inv.setItem(treasurySlot, GuiUtils.namedItem(
            "\u00a7e\u041a\u0430\u0437\u043d\u0430",
            Material.CHEST,
            "\u00a77\u0421\u0434\u0430\u0442\u044c \u0440\u0435\u0441\u0443\u0440\u0441\u044b \u0432 \u043e\u0431\u0449\u0443\u044e \u043a\u0430\u0437\u043d\u0443"
        ))

        val balance = workdaysService?.getBalance(uuid) ?: 0
        inv.setItem(balanceSlot, GuiUtils.namedItem(
            "\u00a7f\u0422\u0440\u0443\u0434\u043e\u0434\u043d\u0438: \u00a7e$balance",
            Material.EXPERIENCE_BOTTLE,
            "\u00a77\u0412\u0430\u0448 \u0442\u0440\u0443\u0434\u043e\u0432\u043e\u0439 \u0431\u0430\u043b\u0430\u043d\u0441"
        ))

        player.openInventory(inv)
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val title = event.view.title().toString()
        if (!title.contains("\u041f\u0430\u0440\u0442\u0438\u0439\u043d\u044b\u0435 \u0443\u0441\u043b\u0443\u0433\u0438")) return
        event.isCancelled = true

        val player = event.whoClicked as Player

        when (event.slot) {
            orderSlot -> {
                val orderService = this.orderService
                if (orderService != null) {
                    val order = orderService.findByOwner(player.uniqueId)
                    if (order != null) {
                        OrderMenu(orderService, workdaysService, config, workFrontService).open(player, order)
                    } else {
                        if (FlagItemProtectionListener.hasOrderFlagInInventory(player)) {
                            // Stale flag from a deleted order — remove it and create a new one
                            FlagItemProtectionListener.removeAllOrderFlags(player)
                        }
                        val newOrder = orderService.create(player.uniqueId)
                        if (newOrder != null) {
                            val flag = ItemStack(Material.WHITE_BANNER)
                            val meta = flag.itemMeta
                            meta.displayName(Component.text("\u00a7a\u0424\u043b\u0430\u0433 \u041e\u0440\u0434\u0435\u0440\u0430 \u2116${newOrder.id}"))
                            meta.lore(listOf(
                                Component.text("\u00a77\u0423\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u0435 \u0444\u043b\u0430\u0433 \u0434\u043b\u044f \u0430\u043a\u0442\u0438\u0432\u0430\u0446\u0438\u0438 \u041e\u0440\u0434\u0435\u0440\u0430"),
                                Component.text("\u00a77\u0412\u043b\u0430\u0434\u0435\u043b\u0435\u0446: \u00a7e${player.name}")
                            ))
                            flag.itemMeta = meta
                            if (player.inventory.firstEmpty() == -1) {
                                player.sendMessage(Component.text("\u00a7c\u0422\u043e\u0432\u0430\u0440\u0438\u0449, \u043e\u0441\u0432\u043e\u0431\u043e\u0434\u0438\u0442\u0435 \u0445\u043e\u0442\u044f \u0431\u044b 1 \u0441\u043b\u043e\u0442 \u0432 \u0438\u043d\u0432\u0435\u043d\u0442\u0430\u0440\u0435 \u0434\u043b\u044f \u0444\u043b\u0430\u0433\u0430 \u041e\u0440\u0434\u0435\u0440\u0430!"))
                            } else {
                                player.inventory.addItem(flag)
                                player.sendMessage(Component.text("\u00a7a\u262d \u041f\u0430\u0440\u0442\u0438\u044f \u0432\u044b\u0434\u0435\u043b\u0438\u043b\u0430 \u0432\u0430\u043c \u0436\u0438\u043b\u043f\u043b\u043e\u0449\u0430\u0434\u044c! \u0423\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u0435 \u0444\u043b\u0430\u0433 \u043d\u0430 \u0432\u044b\u0431\u0440\u0430\u043d\u043d\u043e\u0439 \u0442\u0435\u0440\u0440\u0438\u0442\u043e\u0440\u0438\u0438."))
                            }
                            player.closeInventory()
                        } else {
                            player.sendMessage(Component.text("\u00a7c\u0423 \u0432\u0430\u0441 \u0443\u0436\u0435 \u0435\u0441\u0442\u044c \u041e\u0440\u0434\u0435\u0440, \u0442\u043e\u0432\u0430\u0440\u0438\u0449."))
                        }
                    }
                }
            }
            frontSlot -> {
                val workFrontService = this.workFrontService
                if (workFrontService != null) {
                    val front = workFrontService.getByOwner(player.uniqueId)
                    if (front != null) {
                        FrontMenu(workFrontService).open(player, front)
                    } else {
                        if (FlagItemProtectionListener.hasFrontFlagInInventory(player)) {
                            player.sendMessage(Component.text("§cУ вас уже есть флаг Трудового Фронта, товарищ! Установите его в мире."))
                            return
                        }
                        val flag = ItemStack(Material.RED_BANNER)
                        val meta = flag.itemMeta
                        meta.displayName(Component.text("\u00a76\u0424\u043b\u0430\u0433 \u0422\u0440\u0443\u0434\u043e\u0432\u043e\u0433\u043e \u0424\u0440\u043e\u043d\u0442\u0430"))
                        meta.lore(listOf(
                            Component.text("\u00a77\u0423\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u0435 \u0444\u043b\u0430\u0433 \u0434\u043b\u044f \u0430\u043a\u0442\u0438\u0432\u0430\u0446\u0438\u0438"),
                            Component.text("\u00a77\u0420\u0430\u0434\u0438\u0443\u0441 \u0434\u043e\u0431\u044b\u0447\u0438: \u00a7e${config.frontRadius} \u00a77\u0431\u043b\u043e\u043a\u043e\u0432")
                        ))
                        flag.itemMeta = meta
                        if (player.inventory.firstEmpty() == -1) {
                            player.sendMessage(Component.text("\u00a7c\u0422\u043e\u0432\u0430\u0440\u0438\u0449, \u043e\u0441\u0432\u043e\u0431\u043e\u0434\u0438\u0442\u0435 \u0445\u043e\u0442\u044f \u0431\u044b 1 \u0441\u043b\u043e\u0442 \u0432 \u0438\u043d\u0432\u0435\u043d\u0442\u0430\u0440\u0435 \u0434\u043b\u044f \u0444\u043b\u0430\u0433\u0430 \u0424\u0440\u043e\u043d\u0442\u0430!"))
                        } else {
                            player.inventory.addItem(flag)
                            player.sendMessage(Component.text("\u00a76\u262d \u0423\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u0435 \u0444\u043b\u0430\u0433 \u0434\u043b\u044f \u0430\u043a\u0442\u0438\u0432\u0430\u0446\u0438\u0438 \u0422\u0440\u0443\u0434\u043e\u0432\u043e\u0433\u043e \u0424\u0440\u043e\u043d\u0442\u0430, \u0442\u043e\u0432\u0430\u0440\u0438\u0449!"))
                        }
                        player.closeInventory()
                    }
                } else {
                    player.sendMessage(Component.text("\u00a7c\u0422\u0440\u0443\u0434\u043e\u0432\u043e\u0439 \u0444\u0440\u043e\u043d\u0442 \u0432\u0440\u0435\u043c\u0435\u043d\u043d\u043e \u043d\u0435\u0434\u043e\u0441\u0442\u0443\u043f\u0435\u043d, \u0442\u043e\u0432\u0430\u0440\u0438\u0449."))
                }
            }
            treasurySlot -> {
                val wds = workdaysService
                if (wds != null) {
                    TreasuryMenu(config, wds).open(player)
                }
            }
        }
    }
}
