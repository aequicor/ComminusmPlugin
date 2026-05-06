package ru.kyamshanov.comminusm.gui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
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
import ru.kyamshanov.comminusm.model.Order
import ru.kyamshanov.comminusm.service.FlagStabilityManager
import ru.kyamshanov.comminusm.service.HomeTimerManager
import ru.kyamshanov.comminusm.service.OrderService
import ru.kyamshanov.comminusm.service.WorkFrontService
import ru.kyamshanov.comminusm.service.WorkdaysService
import java.util.UUID

@Suppress("LongParameterList")
class OrderMenu(
    private val orderService: OrderService,
    private val workdaysService: WorkdaysService?,
    private val config: PluginConfig,
    private val workFrontService: WorkFrontService? = null,
    private val homeTimerManager: HomeTimerManager? = null,
    private val flagStabilityManager: FlagStabilityManager? = null,
    private val plugin: Plugin? = null,
) : Listener {
    private val infoSlot = 20
    private val sizeSlot = 22
    private val upgradeSlot = 24
    private val restoreSlot = 31
    private val backSlot = 39
    private val homeSlot = 4

    fun open(player: Player, order: Order) {
        val inv = Bukkit.createInventory(null, 45, Component.text("§8Ордер №${order.id}"))
        GuiUtils.fillBorder(inv)

        inv.setItem(
            infoSlot,
            GuiUtils.namedItem(
                "§eОрдер №${order.id}",
                Material.WHITE_BANNER,
                "§7Уровень: §e${order.level}/${orderService.getMaxLevel()}",
                "§7Владелец: §e${player.name}",
            ),
        )

        inv.setItem(
            sizeSlot,
            GuiUtils.namedItem(
                "§aТерритория",
                Material.GLASS,
                "§7Размер: §e${order.size}×${order.size}",
                "§7Радиус: §e${order.radius} §7блоков",
                if (order.centerWorld != null) "§7Мир: §e${order.centerWorld}" else "§cНе активирован",
            ),
        )

        val nextLevel = order.level + 1
        if (nextLevel <= orderService.getMaxLevel()) {
            val cost = orderService.getCostForLevel(nextLevel)
            val newRadius = orderService.getRadiusForLevel(nextLevel)
            val balance = workdaysService?.getBalance(player.uniqueId) ?: 0
            inv.setItem(
                upgradeSlot,
                GuiUtils.namedItem(
                    "§6Улучшить до уровня $nextLevel",
                    Material.NETHER_STAR,
                    "§7Новый размер: §e${newRadius * 2 + 1}×${newRadius * 2 + 1}",
                    "§7Стоимость: §e$cost §7трудодней",
                    "§7Ваш баланс: §e$balance §7трудодней",
                ),
            )
        }

        inv.setItem(
            restoreSlot,
            GuiUtils.namedItem(
                "§dВосстановить флаг",
                Material.PAPER,
                "§7Флаг вернётся в центр участка",
            ),
        )

        inv.setItem(backSlot, GuiUtils.namedItem("§cНазад", Material.BARRIER))

        // Home button — shown only to the order owner when the flag is active (AC-01, AC-02)
        val fsm = flagStabilityManager
        val pluginInstance = plugin
        if (fsm != null && pluginInstance != null && order.ownerUuid == player.uniqueId) {
            val homeButton = buildHomeButton(fsm, order.id, player.world.name)
            if (homeButton != null) {
                val meta = homeButton.itemMeta
                meta?.persistentDataContainer?.set(
                    NamespacedKey(pluginInstance, HOME_ORDER_ID_KEY),
                    PersistentDataType.LONG,
                    order.id,
                )
                homeButton.itemMeta = meta
                inv.setItem(homeSlot, homeButton)
            }
        }

        player.openInventory(inv)
    }

    private fun buildHomeButton(
        fsm: FlagStabilityManager,
        orderId: Long,
        playerWorld: String,
    ): ItemStack? {
        val buttonState = resolveHomeButtonState(fsm, orderId, playerWorld) { loc -> loc.world?.name }
        @Suppress("MaxLineLength")
        return when (buttonState) {
            HomeButtonState.ACTIVE -> GuiUtils.namedItem(
                "§aВернуться домой",
                Material.COMPASS,
                "§7Нажмите, чтобы начать телепортацию",
                "§7Стойте неподвижно 30 сек.",
            )
            HomeButtonState.DISABLED_DIFFERENT_WORLD -> GuiUtils.namedItem(
                "§7Вернуться домой",
                Material.COMPASS,
                "§cФлаг в другом мире — телепорт недоступен",
            )
            HomeButtonState.HIDDEN -> null
        }
    }

    /**
     * Pure decision function — no Bukkit item creation, fully testable.
     * Determines which home-button state should be rendered for the given flag and world context.
     *
     * @param getFlagWorldName Extracts the world name from the flag location (injected for testability).
     *                         Defaults to reading `flagLoc.world?.name`.
     */
    @Suppress("LongParameterList", "ReturnCount")
    internal fun resolveHomeButtonState(
        fsm: FlagStabilityManager,
        orderId: Long,
        playerWorld: String,
        getFlagWorldName: (org.bukkit.Location) -> String? = { loc -> loc.world?.name },
    ): HomeButtonState {
        val flagLoc = fsm.getFlagLocation(orderId) ?: return HomeButtonState.HIDDEN
        if (!fsm.isFlagActive(orderId)) return HomeButtonState.HIDDEN
        return if (getFlagWorldName(flagLoc) == playerWorld) {
            HomeButtonState.ACTIVE
        } else {
            HomeButtonState.DISABLED_DIFFERENT_WORLD
        }
    }

    @EventHandler
    @Suppress("LongMethod", "CyclomaticComplexMethod", "ReturnCount", "NestedBlockDepth")
    fun onClick(event: InventoryClickEvent) {
        val title = event.view.title().toString()
        if (!title.contains("Ордер №")) return
        event.isCancelled = true

        val player = event.whoClicked as Player
        if (event.rawSlot != event.slot) return  // skip player-inventory / hotbar clicks

        when (event.slot) {
            upgradeSlot -> {
                val success = orderService.upgrade(player.uniqueId)
                if (success) {
                    val updatedOrder = orderService.findByOwner(player.uniqueId)
                    if (updatedOrder != null) {
                        player.sendMessage(
                            Component.text(
                                "§a☭ Партия расширила вашу жилплощадь до уровня ${updatedOrder.level}. Слава труду!",
                            ),
                        )
                        open(player, updatedOrder)
                    }
                } else {
                    val order = orderService.findByOwner(player.uniqueId)
                    if (order != null) {
                        val nextLevel = order.level + 1
                        val cost = orderService.getCostForLevel(nextLevel)
                        val balance = workdaysService?.getBalance(player.uniqueId) ?: 0
                        val missing = cost - balance
                        player.sendMessage(
                            Component.text(
                                "§cНедостаточно трудодней, товарищ. Не хватает: §e$missing",
                            ),
                        )
                    }
                }
            }
            restoreSlot -> {
                val order = orderService.findByOwner(player.uniqueId)
                if (order == null) {
                    player.sendMessage(Component.text("§cУ вас нет активного Ордера, товарищ."))
                    player.closeInventory()
                    return
                }
                if (order.centerWorld == null) {
                    player.sendMessage(Component.text("§cВаш Ордер ещё не активирован. Установите флаг на территории, товарищ."))
                    player.closeInventory()
                    return
                }
                val world = Bukkit.getWorld(order.centerWorld)
                if (world == null) {
                    player.sendMessage(Component.text("§cМир §e${order.centerWorld} §cне найден. Обратитесь к администратору."))
                    player.closeInventory()
                    return
                }

                // Place the banner block at the center coordinates directly
                val bannerBlock = world.getBlockAt(order.centerX, order.centerY, order.centerZ)
                bannerBlock.type = Material.WHITE_BANNER
                // Set banner direction via BlockState - clear all patterns for a blank white banner
                val state = bannerBlock.state
                if (state is org.bukkit.block.Banner) {
                    state.setPatterns(listOf())
                    state.update()
                }

                player.sendMessage(Component.text("§a☭ Флаг Ордера восстановлен на вашем участке, товарищ!"))
            }
            homeSlot -> {
                val clickedItem = event.currentItem ?: return
                if (clickedItem.type != Material.COMPASS) return

                val pluginInstance = plugin ?: return
                val fsm = flagStabilityManager ?: return
                val htm = homeTimerManager ?: return

                val orderId = clickedItem.itemMeta
                    ?.persistentDataContainer
                    ?.get(NamespacedKey(pluginInstance, HOME_ORDER_ID_KEY), PersistentDataType.LONG)
                    ?: return

                handleHomeClick(
                    playerUuid = player.uniqueId,
                    playerWorldName = player.world.name,
                    orderId = orderId,
                    fsm = fsm,
                    htm = htm,
                    sendActionBar = { msg -> player.sendActionBar(MiniMessage.miniMessage().deserialize(msg)) },
                    sendMessage = { msg -> player.sendMessage(MiniMessage.miniMessage().deserialize(msg)) },
                    closeInventory = { player.closeInventory() },
                    getFlagWorldName = { loc -> loc.world?.name },
                    checkOwner = { uuid -> orderService.findByOwner(uuid)?.id == orderId },
                )
            }
            backSlot -> {
                val wds = workdaysService
                if (wds != null) {
                    PartyMenu(config, wds, orderService, workFrontService).open(player)
                }
            }
        }
    }

    /**
     * Pure-logic home-click handler — all Bukkit side-effects injected via lambdas.
     * Testable without a running server.
     *
     * @param playerUuid      UUID of the clicking player.
     * @param playerWorldName World name of the player at click time.
     * @param orderId         orderId read from button PDC.
     * @param fsm             Flag stability boundary.
     * @param htm             Home timer manager.
     * @param sendActionBar   Side-effect: send action bar string to player.
     * @param sendMessage     Side-effect: send chat message string to player.
     * @param closeInventory  Side-effect: close player's inventory.
     * @param checkOwner      Predicate: returns true if the given UUID is the owner of orderId.
     */
    @Suppress("LongParameterList", "ReturnCount")
    internal fun handleHomeClick(
        playerUuid: UUID,
        playerWorldName: String,
        orderId: Long,
        fsm: FlagStabilityManager,
        htm: HomeTimerManager,
        sendActionBar: (String) -> Unit,
        sendMessage: (String) -> Unit = {},
        closeInventory: () -> Unit,
        getFlagWorldName: (org.bukkit.Location) -> String? = { loc -> loc.world?.name },
        checkOwner: (UUID) -> Boolean = { true },
    ) {
        if (!checkOwner(playerUuid)) {
            sendMessage("<red>Вы не являетесь владельцем этого Ордера, товарищ.</red>")
            return
        }
        val flagLoc = fsm.getFlagLocation(orderId)
        if (flagLoc == null) {
            sendActionBar("<red>Флаг недоступен.</red>")
            return
        }
        val flagWorldName = getFlagWorldName(flagLoc)
        if (flagWorldName != playerWorldName) {
            sendActionBar("<red>Возврат домой недоступен — флаг находится в другом мире.</red>")
            return
        }
        if (!fsm.isFlagActive(orderId)) {
            sendActionBar("<red>Флаг ордера недоступен.</red>")
            return
        }
        // AC-12: ignore silently if a timer is already active
        if (htm.hasActiveTimer(playerUuid)) return
        htm.startTimer(playerUuid, orderId)
        closeInventory()
    }

    companion object {
        /** PDC key used to store orderId on the home button ItemStack (avoids DB call on click). */
        const val HOME_ORDER_ID_KEY = "home_order_id"
    }

    /** Decision result for home-button rendering. No Bukkit objects — fully testable. */
    enum class HomeButtonState { ACTIVE, DISABLED_DIFFERENT_WORLD, HIDDEN }
}
