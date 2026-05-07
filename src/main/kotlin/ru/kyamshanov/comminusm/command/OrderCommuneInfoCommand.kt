package ru.kyamshanov.comminusm.command

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import ru.kyamshanov.comminusm.application.usecases.commune.GetCommuneOfOrderUseCase
import ru.kyamshanov.comminusm.commune.service.CommuneService
import ru.kyamshanov.comminusm.domain.entities.Order
import ru.kyamshanov.comminusm.domain.repositories.OrderRepository

/**
 * Command handler for `/order commune <orderId>` — read-only commune info display.
 *
 * Addresses: TC-41, AC-23
 *
 * Usage:
 * - `/order commune <orderId>` — display commune info for order
 *
 * Response:
 * - Order not found: "Ордер не найден"
 * - Order not in commune: "Ордер не состоит ни в одной коммуне"
 * - Order in commune: display order name, owner, all ally orders
 *
 * Startup guard: if CommuneService startup not complete,
 * return "Система коммун инициализируется, попробуйте снова через несколько секунд"
 */
class OrderCommuneInfoCommand(
    private val orderRepository: OrderRepository,
    private val getCommuneOfOrderUseCase: GetCommuneOfOrderUseCase,
    private val communeService: CommuneService,
    private val startupComplete: () -> Boolean = { true }, // Injected startup check
) : CommandExecutor {
    override fun onCommand(
        sender: CommandSender,
        cmd: Command,
        label: String,
        args: Array<String>,
    ): Boolean = handleCommand(sender, args)

    /**
     * Main command handler logic.
     */
    private fun handleCommand(
        sender: CommandSender,
        args: Array<String>,
    ): Boolean {
        val order = lookupOrder(sender, args) ?: return true
        val commune = getCommuneOfOrderUseCase(order.id)
        return if (commune == null) {
            displayNotInCommune(sender, order)
        } else {
            displayCommuneInfo(sender, order, commune)
        }
    }

    /**
     * Validate startup, arguments, and look up order by ID.
     * Sends error messages as needed. Returns null if any validation fails.
     */
    private fun lookupOrder(
        sender: CommandSender,
        args: Array<String>,
    ): Order? {
        val validationError = validateInput(sender, args)
        return if (validationError != null) {
            null
        } else {
            val orderId = parseOrderId(args[0], sender) ?: return null
            orderRepository.findById(orderId).also { order ->
                if (order == null) {
                    sender.sendMessage("§cОрдер не найден")
                }
            }
        }
    }

    /**
     * Validate startup and arguments.
     * Returns error message if validation fails, null if all checks pass.
     */
    private fun validateInput(
        sender: CommandSender,
        args: Array<String>,
    ): String? =
        when {
            !startupComplete() -> {
                sender.sendMessage("§8Система коммун инициализируется, попробуйте снова через несколько секунд")
                "startup_error"
            }
            args.isEmpty() -> {
                sender.sendMessage("§cИспользование: /order commune <id ордера>")
                "empty_args"
            }
            else -> null
        }

    /**
     * Parse order ID from string or send error message if invalid.
     * Returns null if parsing failed.
     */
    private fun parseOrderId(
        orderIdStr: String,
        sender: CommandSender,
    ): Long? =
        try {
            orderIdStr.toLong()
        } catch (e: NumberFormatException) {
            sender.sendMessage("§cОрдер не найден")
            null
        }

    /**
     * Display message when order is not in a commune.
     */
    private fun displayNotInCommune(
        sender: CommandSender,
        order: Order,
    ): Boolean {
        val ownerName = Bukkit.getOfflinePlayer(order.ownerUuid).name ?: "Unknown"
        sender.sendMessage("§7Ордер (ID: ${order.id}, владелец: $ownerName) не состоит ни в одной коммуне")
        return true
    }

    /**
     * Display commune information for the given order.
     */
    private fun displayCommuneInfo(
        sender: CommandSender,
        order: Order,
        commune: ru.kyamshanov.comminusm.commune.model.Commune,
    ): Boolean {
        val communeOrderIds = communeService.getCommuneOrders(commune.id)
        val orderInfos =
            communeOrderIds.mapNotNull { orderId ->
                val orderData = orderRepository.findById(orderId)
                if (orderData != null) {
                    val ownerName = Bukkit.getOfflinePlayer(orderData.ownerUuid).name ?: "Unknown"
                    "#$orderId (владелец: $ownerName)"
                } else {
                    null
                }
            }

        sender.sendMessage("§8═════════════════════")
        sender.sendMessage("§eОрдер ID: §7${order.id}")

        val ownerName = Bukkit.getOfflinePlayer(order.ownerUuid).name ?: "Unknown"
        sender.sendMessage("§eВладелец: §7$ownerName")

        sender.sendMessage("§eВ коммуне: §aДА")
        sender.sendMessage("§eСоюзники: §7${orderInfos.joinToString(", ")}")
        sender.sendMessage("§8═════════════════════")
        return true
    }
}
