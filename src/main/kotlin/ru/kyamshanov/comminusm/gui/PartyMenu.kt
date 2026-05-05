package ru.kyamshanov.comminusm.gui

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import ru.kyamshanov.comminusm.config.PluginConfig
import ru.kyamshanov.comminusm.service.OrderService
import ru.kyamshanov.comminusm.service.WorkFrontService
import ru.kyamshanov.comminusm.listener.FlagItemProtectionListener
import ru.kyamshanov.comminusm.service.WorkdaysService
import java.util.Base64

class PartyMenu(
    private val config: PluginConfig,
    private val workdaysService: WorkdaysService?,
    private val orderService: OrderService?,
    private val workFrontService: WorkFrontService?,
    private val plugin: Plugin? = null,
) : Listener {
    private val orderSlot = 20
    private val frontSlot = 24
    private val treasurySlot = 31
    private val balanceSlot = 40

    fun open(player: Player) {
        val inv = Bukkit.createInventory(null, 45, Component.text("§8Партийные услуги"))
        GuiUtils.fillBorder(inv)

        val uuid = player.uniqueId
        val hasOrder = orderService?.findByOwner(uuid) != null
        val hasFront = workFrontService?.getByOwner(uuid) != null

        inv.setItem(orderSlot, GuiUtils.namedItem(
            if (hasOrder) "§eУправление Ордером" else "§aПолучить Ордер",
            Material.WHITE_BANNER,
            if (hasOrder) "§7Управление вашей жилплощадью" else "§7Партия выделит вам жилплощадь"
        ))

        inv.setItem(frontSlot, GuiUtils.namedItem(
            "§6Трудовой фронт",
            Material.NETHERITE_PICKAXE,
            if (hasFront) "§7Управление трудовым фронтом" else "§7Активировать трудовой фронт"
        ))

        inv.setItem(treasurySlot, GuiUtils.namedItem(
            "§eКазна",
            Material.CHEST,
            "§7Сдать ресурсы в общую казну"
        ))

        val balance = workdaysService?.getBalance(uuid) ?: 0
        inv.setItem(balanceSlot, GuiUtils.namedItem(
            "§fТрудодни: §e$balance",
            Material.EXPERIENCE_BOTTLE,
            "§7Ваш трудовой баланс"
        ))

        player.openInventory(inv)
    }

    @EventHandler
    @Suppress("ReturnCount", "LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth")
    fun onClick(event: InventoryClickEvent) {
        val title = event.view.title().toString()
        if (!title.contains("Партийные услуги")) return
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
                            meta.displayName(Component.text("§aФлаг Ордера №${newOrder.id}"))
                            meta.lore(listOf(
                                Component.text("§7Установите флаг для активации Ордера"),
                                Component.text("§7Владелец: §e${player.name}")
                            ))
                            flag.itemMeta = meta
                            if (player.inventory.firstEmpty() == -1) {
                                player.sendMessage(Component.text("§cТоварищ, освободите хотя бы 1 слот в инвентаре для флага Ордера!"))
                            } else {
                                player.inventory.addItem(flag)
                                player.sendMessage(Component.text("§a☭ Партия выделила вам жилплощадь! Установите флаг на выбранной территории."))
                            }
                            player.closeInventory()
                        } else {
                            player.sendMessage(Component.text("§cУ вас уже есть Ордер, товарищ."))
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
                        // Check for pending_flag PDC marker before issuing a new flag
                        if (tryDeliverPendingFrontFlag(player)) {
                            player.closeInventory()
                            return
                        }
                        if (FlagItemProtectionListener.hasFrontFlagInInventory(player)) {
                            player.sendMessage(Component.text("§cУ вас уже есть флаг Трудового Фронта, товарищ! Установите его в мире."))
                            return
                        }
                        val flag = ItemStack(Material.RED_BANNER)
                        val meta = flag.itemMeta
                        meta.displayName(Component.text("§6Флаг Трудового Фронта"))
                        meta.lore(listOf(
                            Component.text("§7Установите флаг для активации"),
                            Component.text("§7Радиус добычи: §e${config.frontRadius} §7блоков")
                        ))
                        flag.itemMeta = meta
                        if (player.inventory.firstEmpty() == -1) {
                            player.sendMessage(Component.text("§cТоварищ, освободите хотя бы 1 слот в инвентаре для флага Фронта!"))
                        } else {
                            player.inventory.addItem(flag)
                            player.sendMessage(Component.text("§6☭ Установите флаг для активации Трудового Фронта, товарищ!"))
                        }
                        player.closeInventory()
                    }
                } else {
                    player.sendMessage(Component.text("§cТрудовой фронт временно недоступен, товарищ."))
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

    /**
     * Scans all loaded chunks for a pending_flag PDC marker belonging to [player].
     * If found and the payload is valid, delivers the flag item to the player's inventory.
     *
     * @return `true` if a pending marker was found (delivery succeeded or inventory full);
     *         `false` if no pending marker exists for this player.
     */
    @Suppress("ReturnCount")
    private fun tryDeliverPendingFrontFlag(player: Player): Boolean {
        val pluginInstance = plugin ?: return false
        val ownerUuid = player.uniqueId
        val pendingKeyStr = "pending_flag/front/$ownerUuid"

        for (world in Bukkit.getWorlds()) {
            for (chunk in world.loadedChunks) {
                val pdc = chunk.persistentDataContainer
                val pendingKey = NamespacedKey(pluginInstance, pendingKeyStr)
                val payload = pdc.get(pendingKey, PersistentDataType.STRING) ?: continue

                // Found a pending marker — attempt delivery
                val flagItem = parsePendingFlagPayload(payload, player, pluginInstance) ?: run {
                    // Malformed payload — delete it and fall through to new flag issuance
                    pdc.remove(pendingKey)
                    pluginInstance.logger.severe(
                        "Deleted malformed pending_flag payload for ${player.name} in chunk ${chunk.x},${chunk.z}"
                    )
                    return false
                }

                if (player.inventory.firstEmpty() < 0) {
                    player.sendMessage(
                        Component.text(
                            "§eОсвободите место в инвентаре, чтобы получить ваш флаг."
                        )
                    )
                    // Marker still exists but cannot deliver — block new flag issuance
                    return true
                }

                player.inventory.addItem(flagItem)
                pdc.remove(pendingKey)
                player.sendMessage(
                    Component.text(
                        "§6☭ Ваш флаг Трудового Фронта возвращён!"
                    )
                )
                return true
            }
        }
        return false
    }

    /**
     * Parses the [payload] string stored in a pending_flag PDC entry.
     *
     * Supported formats:
     * - `SENTINEL:FRONT:` — re-creates a standard front flag item from config.
     * - `ITEM:<base64>` — deserializes a previously serialized ItemStack.
     *
     * @return the reconstructed [ItemStack], or `null` if the payload is malformed.
     */
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    private fun parsePendingFlagPayload(
        payload: String,
        player: Player,
        pluginInstance: Plugin,
    ): ItemStack? {
        val colonIdx = payload.indexOf(':')
        if (colonIdx < 0) {
            pluginInstance.logger.severe("Malformed pending_flag payload for ${player.name}: no colon separator")
            return null
        }
        return when (val type = payload.substring(0, colonIdx)) {
            "SENTINEL" -> {
                val rest = payload.substring(colonIdx + 1)
                when {
                    rest.startsWith("FRONT:") -> {
                        val flag = ItemStack(Material.RED_BANNER)
                        val meta = flag.itemMeta
                        meta.displayName(
                            Component.text("§6Флаг Трудового Фронта")
                        )
                        meta.lore(listOf(
                            Component.text("§7Установите в новом месте"),
                            Component.text(
                                "§7Радиус добычи: §e${config.frontRadius} §7блоков"
                            ),
                        ))
                        flag.itemMeta = meta
                        flag
                    }
                    else -> {
                        pluginInstance.logger.severe(
                            "Unknown SENTINEL type in pending_flag for ${player.name}: $rest"
                        )
                        null
                    }
                }
            }
            "ITEM" -> {
                val b64 = payload.substring(colonIdx + 1)
                if (b64.isEmpty()) {
                    pluginInstance.logger.severe("Empty ITEM payload for ${player.name}")
                    return null
                }
                try {
                    ItemStack.deserializeBytes(Base64.getDecoder().decode(b64))
                } catch (e: Exception) {
                    pluginInstance.logger.severe(
                        "Failed to deserialize pending flag for ${player.name}: ${e.message}"
                    )
                    null
                }
            }
            else -> {
                pluginInstance.logger.severe("Unknown pending_flag type for ${player.name}: $type")
                null
            }
        }
    }
}
