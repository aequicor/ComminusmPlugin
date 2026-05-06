package ru.kyamshanov.comminusm.service

import org.bukkit.Bukkit
import org.bukkit.Location
import ru.kyamshanov.comminusm.storage.OrderRepository
import java.util.logging.Logger

/**
 * Concrete implementation of [FlagStabilityManager] backed by the orders DB.
 *
 * Reads flag position from the [OrderRepository] on every call.
 * Thread context: safe to call on the main thread only.
 */
class OrderFlagStabilityManager(
    private val orderRepository: OrderRepository,
    private val logger: Logger,
) : FlagStabilityManager {

    @Suppress("ReturnCount")
    override fun getFlagLocation(orderId: Long): Location? {
        val order = runCatching { orderRepository.findById(orderId) }.getOrElse { e ->
            logger.warning("getFlagLocation DB error for orderId=$orderId: $e")
            return null
        } ?: return null
        val worldName = order.centerWorld ?: return null
        val world = Bukkit.getWorld(worldName) ?: run {
            logger.warning("getFlagLocation: world '$worldName' not loaded for orderId=$orderId")
            return null
        }
        return Location(world, order.centerX.toDouble(), order.centerY.toDouble(), order.centerZ.toDouble())
    }

    @Suppress("ReturnCount")
    override fun isFlagActive(orderId: Long): Boolean {
        val order = runCatching { orderRepository.findById(orderId) }.getOrElse { return false } ?: return false
        return order.centerWorld != null
    }
}
