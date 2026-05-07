@file:Suppress("LongParameterList")

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
import ru.kyamshanov.comminusm.application.usecases.order.GetOrderByOwnerUseCase
import ru.kyamshanov.comminusm.application.usecases.workdays.GetWorkdaysBalanceUseCase
import ru.kyamshanov.comminusm.application.usecases.workfront.GetWorkFrontByOwnerUseCase
import ru.kyamshanov.comminusm.infrastructure.adapters.DomainToModelAdapter
import ru.kyamshanov.comminusm.infrastructure.config.PluginConfig
import ru.kyamshanov.comminusm.listener.FlagItemProtectionListener
import ru.kyamshanov.comminusm.service.OrderService
import ru.kyamshanov.comminusm.service.WorkFrontService
import java.util.Base64

class PartyMenu(
    private val config: PluginConfig,
    private val getWorkdaysBalanceUseCase: GetWorkdaysBalanceUseCase,
    private val getOrderByOwnerUseCase: GetOrderByOwnerUseCase,
    private val getWorkFrontByOwnerUseCase: GetWorkFrontByOwnerUseCase,
    private val orderService: OrderService?,
    private val workFrontService: WorkFrontService?,
    private val plugin: Plugin? = null,
    private val orderMenu: OrderMenu? = null,
    private val frontMenu: FrontMenu? = null,
    private val treasuryMenu: TreasuryMenu? = null,
) : Listener {
    fun open(player: Player) {
        val inv =
            Bukkit.createInventory(
                null,
                GuiConstants.PARTY_MENU_INVENTORY_SIZE,
                Component.text("§8Партийные услуги"),
            )
        GuiUtils.fillBorder(inv)

        val uuid = player.uniqueId
        val hasOrder = getOrderByOwnerUseCase(uuid) != null
        val hasFront = getWorkFrontByOwnerUseCase(uuid) != null

        inv.setItem(
            GuiConstants.PARTY_MENU_ORDER_SLOT,
            GuiUtils.namedItem(
                if (hasOrder) "§eУправление Ордером" else "§aПолучить Ордер",
                Material.WHITE_BANNER,
                if (hasOrder) {
                    "§7Управление вашей жилплощадью"
                } else {
                    "§7Партия выделит вам жилплощадь"
                },
            ),
        )

        inv.setItem(
            GuiConstants.PARTY_MENU_FRONT_SLOT,
            GuiUtils.namedItem(
                "§6Трудовой фронт",
                Material.NETHERITE_PICKAXE,
                if (hasFront) {
                    "§7Управление трудовым фронтом"
                } else {
                    "§7Активировать трудовой фронт"
                },
            ),
        )

        inv.setItem(
            GuiConstants.PARTY_MENU_TREASURY_SLOT,
            GuiUtils.namedItem(
                "§eКазна",
                Material.CHEST,
                "§7Сдать ресурсы в общую казну",
            ),
        )

        val balance = getWorkdaysBalanceUseCase(uuid)
        inv.setItem(
            GuiConstants.PARTY_MENU_BALANCE_SLOT,
            GuiUtils.namedItem(
                "§fТрудодни: §e$balance",
                Material.EXPERIENCE_BOTTLE,
                "§7Ваш трудовой баланс",
            ),
        )

        player.openInventory(inv)
    }

    @EventHandler
    @Suppress("LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth")
    fun onClick(event: InventoryClickEvent) {
        val title = event.view.title().toString()
        if (!title.contains("Партийные услуги")) return
        event.isCancelled = true

        val player = event.whoClicked as Player

        when (event.slot) {
            GuiConstants.PARTY_MENU_ORDER_SLOT -> handleOrderClick(player)
            GuiConstants.PARTY_MENU_FRONT_SLOT -> handleFrontClick(player)
            GuiConstants.PARTY_MENU_TREASURY_SLOT -> {
                val menu = treasuryMenu
                if (menu != null) {
                    menu.open(player)
                }
            }
        }
    }

    private fun handleOrderClick(player: Player) {
        val domainOrder = getOrderByOwnerUseCase(player.uniqueId)
        if (domainOrder != null) {
            val menu = orderMenu ?: return
            val modelOrder = DomainToModelAdapter.toPresentationModel(domainOrder)
            menu.open(player, modelOrder)
        } else {
            val orderService = this.orderService ?: return
            createAndIssueOrderFlag(player, orderService)
        }
    }

    private fun createAndIssueOrderFlag(
        player: Player,
        orderService: OrderService,
    ) {
        if (FlagItemProtectionListener.hasOrderFlagInInventory(player)) {
            FlagItemProtectionListener.removeAllOrderFlags(player)
        }
        val newOrder = orderService.create(player.uniqueId, player.name)
        if (newOrder != null) {
            issueFlagToPlayer(
                player,
                ItemStack(Material.WHITE_BANNER),
                "§aФлаг Ордера №${newOrder.id}",
                listOf(
                    Component.text("§7Установите флаг для активации Ордера"),
                    Component.text("§7Владелец: §e${player.name}"),
                ),
                "§a☭ Партия выделила вам жилплощадь! Установите флаг на выбранной территории.",
            )
        } else {
            player.sendMessage(Component.text("§cУ вас уже есть Ордер, товарищ."))
        }
    }

    private fun handleFrontClick(player: Player) {
        val workFrontService = this.workFrontService
        if (workFrontService == null) {
            player.sendMessage(Component.text("§cТрудовой фронт временно недоступен, товарищ."))
            return
        }
        val front = getWorkFrontByOwnerUseCase(player.uniqueId)
        if (front != null) {
            val menu = frontMenu ?: return
            menu.open(player, front)
        } else {
            createAndIssueFrontFlag(player)
        }
    }

    private fun createAndIssueFrontFlag(player: Player) {
        if (tryDeliverPendingFrontFlag(player)) {
            player.closeInventory()
            return
        }
        if (FlagItemProtectionListener.hasFrontFlagInInventory(player)) {
            player.sendMessage(
                Component.text("§cУ вас уже есть флаг Трудового Фронта, товарищ! Установите его в мире."),
            )
            return
        }
        issueFlagToPlayer(
            player,
            ItemStack(Material.RED_BANNER),
            "§6Флаг Трудового Фронта",
            listOf(
                Component.text("§7Установите флаг для активации"),
                Component.text("§7Радиус добычи: §e${config.frontRadius} §7блоков"),
            ),
            "§6☭ Установите флаг для активации Трудового Фронта, товарищ!",
        )
    }

    private fun issueFlagToPlayer(
        player: Player,
        flag: ItemStack,
        displayName: String,
        lore: List<Component>,
        successMessage: String,
    ) {
        val meta = flag.itemMeta
        meta.displayName(Component.text(displayName))
        meta.lore(lore)
        flag.itemMeta = meta
        if (player.inventory.firstEmpty() == -1) {
            player.sendMessage(
                Component.text(
                    "§cТоварищ, освободите хотя бы 1 слот в инвентаре для флага!",
                ),
            )
        } else {
            player.inventory.addItem(flag)
            player.sendMessage(Component.text(successMessage))
        }
        player.closeInventory()
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
                val flagItem =
                    parsePendingFlagPayload(payload, player, pluginInstance) ?: run {
                        // Malformed payload — delete it and fall through to new flag issuance
                        pdc.remove(pendingKey)
                        pluginInstance.logger.severe(
                            "Deleted malformed pending_flag payload for ${player.name} in chunk ${chunk.x},${chunk.z}",
                        )
                        return false
                    }

                if (player.inventory.firstEmpty() < 0) {
                    player.sendMessage(
                        Component.text(
                            "§eОсвободите место в инвентаре, чтобы получить ваш флаг.",
                        ),
                    )
                    // Marker still exists but cannot deliver — block new flag issuance
                    return true
                }

                player.inventory.addItem(flagItem)
                pdc.remove(pendingKey)
                player.sendMessage(
                    Component.text(
                        "§6☭ Ваш флаг Трудового Фронта возвращён!",
                    ),
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
                            Component.text("§6Флаг Трудового Фронта"),
                        )
                        meta.lore(
                            listOf(
                                Component.text("§7Установите в новом месте"),
                                Component.text(
                                    "§7Радиус добычи: §e${config.frontRadius} §7блоков",
                                ),
                            ),
                        )
                        flag.itemMeta = meta
                        flag
                    }
                    else -> {
                        pluginInstance.logger.severe(
                            "Unknown SENTINEL type in pending_flag for ${player.name}: $rest",
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
                        "Failed to deserialize pending flag for ${player.name}: ${e.message}",
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
