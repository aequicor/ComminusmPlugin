@file:Suppress("ReturnCount")

package ru.kyamshanov.comminusm.application.usecases.order

import ru.kyamshanov.comminusm.domain.entities.Order
import ru.kyamshanov.comminusm.domain.repositories.OrderRepository
import ru.kyamshanov.comminusm.domain.repositories.WorkdaysRepository
import ru.kyamshanov.comminusm.domain.value_objects.Result
import ru.kyamshanov.comminusm.infrastructure.config.OrderLevelConfig
import java.util.UUID

/**
 * Implementation of UpgradeOrderUseCase.
 * Upgrades an order to the next level if enough workdays are available.
 */
class UpgradeOrderUseCaseImpl(
    private val orderRepository: OrderRepository,
    private val workdaysRepository: WorkdaysRepository,
    private val levels: List<OrderLevelConfig>,
) : UpgradeOrderUseCase {
    override fun invoke(ownerUuid: UUID): Result<Order> {
        val order =
            orderRepository.findByOwner(ownerUuid)
                ?: return Result.failure("Order not found")

        val currentLevel = order.level
        val maxLevel = levels.maxOfOrNull { it.level } ?: return Result.failure("No level configuration")

        if (currentLevel >= maxLevel) {
            return Result.failure("Already at max level")
        }

        val nextLevel = currentLevel + 1
        val cost =
            levels.find { it.level == nextLevel }?.cost
                ?: return Result.failure("Next level configuration not found")

        val balance = workdaysRepository.getBalance(ownerUuid)
        if (balance < cost) {
            return Result.failure("Insufficient workdays")
        }

        val newRadius =
            levels.find { it.level == nextLevel }?.radius
                ?: return Result.failure("Radius configuration not found")

        workdaysRepository.spend(ownerUuid, cost)
        orderRepository.updateLevel(ownerUuid, nextLevel, newRadius)

        return Result.success(order.copy(level = nextLevel, radius = newRadius))
    }
}
