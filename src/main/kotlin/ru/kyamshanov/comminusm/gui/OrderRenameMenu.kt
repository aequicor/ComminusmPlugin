@file:Suppress(
    "MagicNumber",
    "LongMethod",
    "SwallowedException",
    "Deprecation",
)

package ru.kyamshanov.comminusm.gui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.inventory.PrepareAnvilEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.view.AnvilView
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import ru.kyamshanov.comminusm.application.usecases.order.GetOrderByOwnerUseCase
import ru.kyamshanov.comminusm.application.usecases.order.RenameOrderUseCase
import ru.kyamshanov.comminusm.domain.repositories.OrderRepository
import ru.kyamshanov.comminusm.domain.value_objects.Result
import ru.kyamshanov.comminusm.infrastructure.adapters.DomainToModelAdapter
import ru.kyamshanov.comminusm.model.Order
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class OrderRenameMenu(
    private val renameOrderUseCase: RenameOrderUseCase,
    private val getOrderByOwnerUseCase: GetOrderByOwnerUseCase,
    private val orderRepository: OrderRepository,
    private val plugin: Plugin,
) : Listener {
    companion object {
        private const val MAX_NAME_LENGTH = 20
        private const val ANVIL_OUTPUT_SLOT = 2
    }

    private val inProgressRenames = ConcurrentHashMap<UUID, Long>()
    private val renameTexts = ConcurrentHashMap<UUID, String>()

    fun open(
        player: Player,
        order: Order,
    ) {
        if (inProgressRenames.containsKey(player.uniqueId)) return

        val title = Component.text("Название ордера")
        val anvilInv = Bukkit.createInventory(null, InventoryType.ANVIL, title)
        val inputItem = ItemStack(Material.PAPER)
        inputItem.editMeta { meta ->
            meta.displayName(Component.text(order.name))
        }
        anvilInv.setItem(0, inputItem)
        inProgressRenames[player.uniqueId] = order.id
        renameTexts[player.uniqueId] = order.name
        player.openInventory(anvilInv)
    }

    @Suppress("ReturnCount")
    @EventHandler
    fun onPrepareAnvil(event: PrepareAnvilEvent) {
        val text = event.inventory.renameText ?: return
        val player = event.view.player as? Player ?: return
        // Only cache non-blank text — Paper fires PrepareAnvilEvent with "" in some
        // lifecycle phases; overwriting the cache with blank would erase the typed name.
        if (text.isNotBlank()) {
            renameTexts[player.uniqueId] = text
        }
        val result = ItemStack(Material.PAPER)
        result.editMeta { meta ->
            meta.displayName(Component.text(text))
        }
        event.result = result
    }

    private fun validateTypedName(typedName: String): Component? =
        when {
            typedName.isBlank() || typedName.all { it.isWhitespace() } ->
                Component.text("Название не может быть пустым", NamedTextColor.RED)
            typedName.length > MAX_NAME_LENGTH ->
                Component.text("Максимум $MAX_NAME_LENGTH символов", NamedTextColor.RED)
            !typedName.matches(Regex("[A-Za-zА-Яа-яЁё0-9\\-_]+")) ->
                Component.text(
                    "Недопустимые символы. Используйте: буквы, цифры, дефис (-), подчёркивание (_)",
                    NamedTextColor.RED,
                )
            else -> null
        }

    @Suppress("ReturnCount")
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.inventory.type != InventoryType.ANVIL) {
            return
        }

        val playerUuid = (event.whoClicked as? Player)?.uniqueId ?: return
        val orderId = inProgressRenames[playerUuid] ?: return

        if (event.rawSlot != ANVIL_OUTPUT_SLOT) {
            event.isCancelled = true
            return
        }

        event.isCancelled = true

        val player = event.whoClicked as Player

        val anvilView = event.view as? AnvilView
        val anvilViewText = anvilView?.renameText
        val displayNameText = (event.currentItem?.itemMeta?.displayName() as? TextComponent)?.content()
        val cachedText = renameTexts[playerUuid]
        plugin.logger.info(
            "[OrderRename] view=${event.view.javaClass.simpleName} " +
                "AnvilView=${if (anvilView != null) "ok" else "null"} " +
                "AnvilView.renameText='$anvilViewText' " +
                "slot2DisplayName='$displayNameText' " +
                "cache='$cachedText'",
        )

        val typedName = anvilViewText?.takeIf { it.isNotBlank() }
            ?: displayNameText?.takeIf { it.isNotBlank() }
            ?: cachedText?.takeIf { it.isNotBlank() }
            ?: ""
        plugin.logger.info("[OrderRename] resolved typedName='$typedName'")

        val validationError = validateTypedName(typedName)
        if (validationError != null) {
            player.sendActionBar(validationError)
            inProgressRenames.remove(playerUuid)
            player.closeInventory()
            return
        }

        val domainOrder = getOrderByOwnerUseCase(playerUuid)
        if (domainOrder == null || domainOrder.id != orderId) {
            player.sendActionBar(Component.text("Этот ордер был расформирован", NamedTextColor.RED))
            inProgressRenames.remove(playerUuid)
            player.closeInventory()
            return
        }

        val currentOrder = DomainToModelAdapter.toPresentationModel(domainOrder)

        // AC-19: same name = no-op, no DB write and no ArmorStand update
        if (typedName == domainOrder.name) {
            inProgressRenames.remove(playerUuid)
            player.closeInventory()
            return
        }

        inProgressRenames.remove(playerUuid)
        player.closeInventory()
        handleSuccessfulValidation(player, currentOrder, typedName)
    }

    private fun handleSuccessfulValidation(
        player: Player,
        currentOrder: Order,
        typedName: String,
    ) {
        updateArmorStand(currentOrder, typedName)
        val oldName = currentOrder.name
        val orderId = currentOrder.id
        performAsyncRename(player.uniqueId, orderId, typedName, currentOrder, oldName)
    }

    @Suppress("ReturnCount")
    private fun performAsyncRename(
        playerUuid: UUID,
        orderId: Long,
        typedName: String,
        currentOrder: Order,
        oldName: String,
    ) {
        val asyncTask =
            Runnable {
                try {
                    // Ownership re-check in async context — avoids DB call on main thread
                    val ownershipResult = renameOrderUseCase(playerUuid, typedName)
                    if (ownershipResult is Result.Failure) {
                        plugin.logger.warning(
                            "Rename rejected in async check: player=$playerUuid, error=${ownershipResult.error}",
                        )
                        val errorMsg =
                            when (ownershipResult.error) {
                                "unauthorized" ->
                                    Component.text("Вы больше не лидер этого ордера", NamedTextColor.RED)
                                "not_found" ->
                                    Component.text("Этот ордер был расформирован", NamedTextColor.RED)
                                else ->
                                    Component.text(
                                        "Ошибка при переименовании. Попробуйте позже",
                                        NamedTextColor.RED,
                                    )
                            }
                        Bukkit.getScheduler().runTask(
                            plugin,
                            Runnable {
                                val actualPlayer = Bukkit.getPlayer(playerUuid) ?: return@Runnable
                                updateArmorStand(currentOrder, oldName)
                                actualPlayer.sendActionBar(errorMsg)
                            },
                        )
                        return@Runnable
                    }

                    orderRepository.rename(orderId, typedName)
                    val mainTask =
                        Runnable {
                            val actualPlayer = Bukkit.getPlayer(playerUuid) ?: return@Runnable
                            actualPlayer.sendActionBar(
                                Component.text("Название ордера изменено на '$typedName'", NamedTextColor.GREEN),
                            )
                            plugin.logger.info(
                                "Order renamed: player=$playerUuid, orderId=$orderId, from='$oldName' to='$typedName'",
                            )
                        }
                    Bukkit.getScheduler().runTask(plugin, mainTask)
                } catch (e: SQLException) {
                    plugin.logger.severe("rename DB failed for order $orderId: ${e.message}")
                    val rollbackTask =
                        Runnable {
                            val actualPlayer = Bukkit.getPlayer(playerUuid) ?: return@Runnable
                            updateArmorStand(currentOrder, oldName)
                            actualPlayer.sendActionBar(
                                Component.text("Ошибка при сохранении названия. Попробуйте позже", NamedTextColor.RED),
                            )
                        }
                    Bukkit.getScheduler().runTask(plugin, rollbackTask)
                }
            }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, asyncTask)
    }

    @Suppress("ReturnCount")
    private fun updateArmorStand(
        order: Order,
        newName: String,
    ) {
        if (!order.isActivated) return

        val world = Bukkit.getWorld(order.centerWorld ?: return) ?: return
        val chunk = world.getChunkAt(order.centerX shr 4, order.centerZ shr 4)
        val asKey = NamespacedKey(plugin, "armorstand/${order.id}")

        val asUuidStr =
            chunk.persistentDataContainer.get(asKey, PersistentDataType.STRING) ?: run {
                plugin.logger.warning("ArmorStand PDC entry not found for order ${order.id}")
                return
            }

        val entity =
            try {
                world.getEntity(UUID.fromString(asUuidStr))
            } catch (e: IllegalArgumentException) {
                plugin.logger.warning("Invalid ArmorStand UUID for order ${order.id}: $asUuidStr")
                return
            }

        if (entity == null || !entity.isValid) {
            plugin.logger.warning("ArmorStand entity not found or invalid for order ${order.id}")
            return
        }

        (entity as? ArmorStand)?.customName(Component.text(newName))
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.inventory.type != InventoryType.ANVIL) return
        val playerUuid = (event.player as? Player)?.uniqueId ?: return
        inProgressRenames.remove(playerUuid)
        renameTexts.remove(playerUuid)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        inProgressRenames.remove(event.player.uniqueId)
        renameTexts.remove(event.player.uniqueId)
    }
}
