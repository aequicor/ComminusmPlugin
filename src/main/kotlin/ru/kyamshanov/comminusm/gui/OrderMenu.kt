@file:Suppress("MagicNumber", "MaxLineLength", "LongMethod")

package ru.kyamshanov.comminusm.gui

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
import ru.kyamshanov.comminusm.application.usecases.order.GetMaxOrderLevelUseCase
import ru.kyamshanov.comminusm.application.usecases.order.GetOrderByOwnerUseCase
import ru.kyamshanov.comminusm.application.usecases.order.GetOrderCostForLevelUseCase
import ru.kyamshanov.comminusm.application.usecases.order.GetRadiusForLevelUseCase
import ru.kyamshanov.comminusm.application.usecases.order.UpgradeOrderUseCase
import ru.kyamshanov.comminusm.application.usecases.workdays.GetWorkdaysBalanceUseCase
import ru.kyamshanov.comminusm.application.usecases.workfront.GetWorkFrontByOwnerUseCase
import ru.kyamshanov.comminusm.infrastructure.adapters.DomainToModelAdapter
import ru.kyamshanov.comminusm.infrastructure.config.PluginConfig
import ru.kyamshanov.comminusm.model.Order
import ru.kyamshanov.comminusm.service.FlagStabilityManager
import ru.kyamshanov.comminusm.service.HomeTimerManager
import ru.kyamshanov.comminusm.service.OrderService
import ru.kyamshanov.comminusm.service.WorkFrontService
import java.util.UUID

@Suppress("LongParameterList")
class OrderMenu(
    private val getMaxOrderLevelUseCase: GetMaxOrderLevelUseCase,
    private val getOrderCostForLevelUseCase: GetOrderCostForLevelUseCase,
    private val getRadiusForLevelUseCase: GetRadiusForLevelUseCase,
    private val getWorkdaysBalanceUseCase: GetWorkdaysBalanceUseCase,
    private val getOrderByOwnerUseCase: GetOrderByOwnerUseCase,
    private val upgradeOrderUseCase: UpgradeOrderUseCase,
    private val getWorkFrontByOwnerUseCase: GetWorkFrontByOwnerUseCase,
    private val config: PluginConfig,
    private val orderService: OrderService,
    private val workFrontService: WorkFrontService? = null,
    private val homeTimerManager: HomeTimerManager? = null,
    private val flagStabilityManager: FlagStabilityManager? = null,
    private val plugin: Plugin? = null,
    private val orderRenameMenu: OrderRenameMenu? = null,
) : Listener {
    private val infoSlot = 20
    private val sizeSlot = 22
    private val renameSlot = 13
    private val upgradeSlot = 24
    private val restoreSlot = 31
    private val backSlot = 36
    private val homeSlot = 40

    fun open(
        player: Player,
        order: Order,
    ) {
        val displayName = order.name.ifBlank { "${order.id}" }
        val mm = MiniMessage.miniMessage()
        val inv = Bukkit.createInventory(null, 45, mm.deserialize("<dark_gray>Ордер - $displayName"))
        GuiUtils.fillBorder(inv)

        val infoItem =
            GuiUtils.namedItem(
                "<yellow>$displayName",
                Material.WHITE_BANNER,
                "<gray>Уровень: <yellow>${order.level}/${getMaxOrderLevelUseCase()}",
                "<gray>Владелец: <yellow>${player.name}",
            )
        plugin?.let { p ->
            val meta = infoItem.itemMeta
            meta?.persistentDataContainer?.set(
                NamespacedKey(p, ORDER_ID_PDC_KEY),
                PersistentDataType.LONG,
                order.id,
            )
            infoItem.itemMeta = meta
        }
        inv.setItem(infoSlot, infoItem)

        inv.setItem(
            sizeSlot,
            GuiUtils.namedItem(
                "<green>Территория",
                Material.GLASS,
                "<gray>Размер: <yellow>${order.size}×${order.size}",
                "<gray>Радиус: <yellow>${order.radius} <gray>блоков",
                if (order.centerWorld != null) "<gray>Мир: <yellow>${order.centerWorld}" else "<red>Не активирован",
            ),
        )

        // Rename button — enabled for owner, disabled for non-owner (AC-11, AC-12)
        val isOwner = order.ownerUuid == player.uniqueId
        if (isOwner) {
            inv.setItem(
                renameSlot,
                GuiUtils.namedItem(
                    "<yellow>Переименовать ордер",
                    Material.ANVIL,
                    "<gray>Изменить название ордера",
                    "<gray>Текущее: <white>${order.name.ifBlank { "${order.id}" }}",
                ),
            )
        } else {
            inv.setItem(
                renameSlot,
                GuiUtils.namedItem(
                    "<gray>Переименовать ордер",
                    Material.ANVIL,
                    "<red>Только лидер может менять название ордера",
                ),
            )
        }

        val nextLevel = order.level + 1
        if (nextLevel <= getMaxOrderLevelUseCase()) {
            val cost = getOrderCostForLevelUseCase(nextLevel)
            val newRadius = getRadiusForLevelUseCase(nextLevel)
            val balance = getWorkdaysBalanceUseCase(player.uniqueId)
            inv.setItem(
                upgradeSlot,
                GuiUtils.namedItem(
                    "<gold>Улучшить до уровня $nextLevel",
                    Material.NETHER_STAR,
                    "<gray>Новый размер: <yellow>${newRadius * 2 + 1}×${newRadius * 2 + 1}",
                    "<gray>Стоимость: <yellow>$cost <gray>трудодней",
                    "<gray>Ваш баланс: <yellow>$balance <gray>трудодней",
                ),
            )
        }

        inv.setItem(
            restoreSlot,
            GuiUtils.namedItem(
                "<light_purple>Восстановить флаг",
                Material.PAPER,
                "<gray>Флаг вернётся в центр участка",
            ),
        )

        inv.setItem(backSlot, GuiUtils.namedItem("<red>Назад", Material.BARRIER))

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
            HomeButtonState.ACTIVE ->
                GuiUtils.namedItem(
                    "<green>Вернуться домой",
                    Material.COMPASS,
                    "<gray>Нажмите, чтобы начать телепортацию",
                    "<gray>Стойте неподвижно 30 сек.",
                )
            HomeButtonState.DISABLED_DIFFERENT_WORLD ->
                GuiUtils.namedItem(
                    "<gray>Вернуться домой",
                    Material.COMPASS,
                    "<red>Флаг в другом мире — телепорт недоступен",
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
        if (!title.contains("Ордер - ")) return
        event.isCancelled = true

        val player = event.whoClicked as Player
        if (event.rawSlot != event.slot) return // skip player-inventory / hotbar clicks

        when (event.slot) {
            renameSlot -> {
                val orm = orderRenameMenu ?: return
                val domainOrder = getOrderByOwnerUseCase(player.uniqueId) ?: return
                // Permission re-check: only the owner can open rename (AC-11, AC-12)
                if (domainOrder.ownerUuid != player.uniqueId) {
                    player.sendActionBar(MiniMessage.miniMessage().deserialize("<red>Только лидер может менять название ордера"))
                    return
                }
                val presentationOrder = DomainToModelAdapter.toPresentationModel(domainOrder)
                player.closeInventory()
                orm.open(player, presentationOrder)
            }
            upgradeSlot -> {
                val result = upgradeOrderUseCase(player.uniqueId)
                val mm = MiniMessage.miniMessage()
                if (result is ru.kyamshanov.comminusm.domain.value_objects.Result.Success) {
                    val updatedOrder = DomainToModelAdapter.toPresentationModel(result.data)
                    player.sendMessage(
                        mm.deserialize(
                            "<green>☭ Партия расширила вашу жилплощадь до уровня ${updatedOrder.level}. Слава труду!",
                        ),
                    )
                    open(player, updatedOrder)
                } else if (result is ru.kyamshanov.comminusm.domain.value_objects.Result.Failure) {
                    val order = getOrderByOwnerUseCase(player.uniqueId)
                    if (order != null) {
                        val nextLevel = order.level + 1
                        val cost = getOrderCostForLevelUseCase(nextLevel)
                        val balance = getWorkdaysBalanceUseCase(player.uniqueId)
                        val missing = cost - balance
                        player.sendMessage(
                            mm.deserialize(
                                "<red>Недостаточно трудодней, товарищ. Не хватает: <yellow>$missing",
                            ),
                        )
                    }
                }
            }
            restoreSlot -> {
                val mmRestore = MiniMessage.miniMessage()
                val order = getOrderByOwnerUseCase(player.uniqueId)
                if (order == null) {
                    player.sendMessage(mmRestore.deserialize("<red>У вас нет активного Ордера, товарищ."))
                    player.closeInventory()
                    return
                }
                if (order.centerWorld == null) {
                    player.sendMessage(mmRestore.deserialize("<red>Ваш Ордер ещё не активирован. Установите флаг на территории, товарищ."))
                    player.closeInventory()
                    return
                }
                val world = Bukkit.getWorld(order.centerWorld)
                if (world == null) {
                    player.sendMessage(
                        mmRestore.deserialize("<red>Мир <yellow>${order.centerWorld} <red>не найден. Обратитесь к администратору."),
                    )
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

                player.sendMessage(mmRestore.deserialize("<green>☭ Флаг Ордера восстановлен на вашем участке, товарищ!"))
            }
            homeSlot -> {
                val clickedItem = event.currentItem ?: return
                if (clickedItem.type != Material.COMPASS) return

                val pluginInstance = plugin ?: return
                val fsm = flagStabilityManager ?: return
                val htm = homeTimerManager ?: return

                val orderId =
                    clickedItem.itemMeta
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
                    checkOwner = { uuid -> getOrderByOwnerUseCase(uuid)?.id == orderId },
                )
            }
            backSlot -> {
                PartyMenu(
                    config,
                    getWorkdaysBalanceUseCase,
                    getOrderByOwnerUseCase,
                    getWorkFrontByOwnerUseCase,
                    orderService,
                    workFrontService,
                ).open(player)
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

        /** PDC key stored on the info item (slot [INFO_SLOT]) — lets other menus read orderId. */
        const val ORDER_ID_PDC_KEY = "menu_order_id"

        /** Index of the banner info item inside the order menu inventory. */
        const val INFO_SLOT = 20
    }

    /** Decision result for home-button rendering. No Bukkit objects — fully testable. */
    enum class HomeButtonState { ACTIVE, DISABLED_DIFFERENT_WORLD, HIDDEN }
}
