@file:Suppress(
    "MagicNumber",
    "LongMethod",
    "SwallowedException",
)

package ru.kyamshanov.comminusm.gui

import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
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
        private const val CANCEL_KEYWORD = "cancel"
    }

    private val pendingChatInputs = ConcurrentHashMap<UUID, Long>()

    fun open(
        player: Player,
        order: Order,
    ) {
        if (pendingChatInputs.containsKey(player.uniqueId)) return

        pendingChatInputs[player.uniqueId] = order.id
        player.closeInventory()
        player.sendMessage(
            Component.text("Введите новое название ордера в чат", NamedTextColor.YELLOW)
                .append(Component.text(" (", NamedTextColor.GRAY))
                .append(Component.text(CANCEL_KEYWORD, NamedTextColor.RED))
                .append(Component.text(" — отмена):", NamedTextColor.GRAY)),
        )
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
    @EventHandler(priority = EventPriority.LOWEST)
    fun onChat(event: AsyncChatEvent) {
        val playerUuid = event.player.uniqueId
        val orderId = pendingChatInputs.remove(playerUuid) ?: return

        event.isCancelled = true

        val text = (event.message() as? TextComponent)?.content()?.trim() ?: ""

        if (text.equals(CANCEL_KEYWORD, ignoreCase = true)) {
            Bukkit.getScheduler().runTask(
                plugin,
                Runnable {
                    Bukkit.getPlayer(playerUuid)?.sendMessage(
                        Component.text("Переименование отменено", NamedTextColor.GRAY),
                    )
                },
            )
            return
        }

        val validationError = validateTypedName(text)
        if (validationError != null) {
            Bukkit.getScheduler().runTask(
                plugin,
                Runnable { Bukkit.getPlayer(playerUuid)?.sendActionBar(validationError) },
            )
            return
        }

        Bukkit.getScheduler().runTask(
            plugin,
            Runnable {
                val player = Bukkit.getPlayer(playerUuid) ?: return@Runnable
                val domainOrder = getOrderByOwnerUseCase(playerUuid)
                if (domainOrder == null || domainOrder.id != orderId) {
                    player.sendActionBar(Component.text("Этот ордер был расформирован", NamedTextColor.RED))
                    return@Runnable
                }

                val currentOrder = DomainToModelAdapter.toPresentationModel(domainOrder)

                // AC-19: same name = no-op, no DB write and no ArmorStand update
                if (text == domainOrder.name) return@Runnable

                handleSuccessfulValidation(player, currentOrder, text)
            },
        )
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
        val asKey = NamespacedKey(plugin, "armorstand/order/${order.ownerUuid}")

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
    fun onPlayerQuit(event: PlayerQuitEvent) {
        pendingChatInputs.remove(event.player.uniqueId)
    }
}
