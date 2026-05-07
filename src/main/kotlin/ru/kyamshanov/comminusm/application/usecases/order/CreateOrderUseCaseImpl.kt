@file:Suppress("ReturnCount")

package ru.kyamshanov.comminusm.application.usecases.order

import ru.kyamshanov.comminusm.domain.entities.Order
import ru.kyamshanov.comminusm.domain.repositories.OrderRepository
import ru.kyamshanov.comminusm.domain.value_objects.Result
import ru.kyamshanov.comminusm.infrastructure.config.OrderLevelConfig
import java.util.UUID

/**
 * Implementation of CreateOrderUseCase.
 * Creates a new Order at the first level for a player.
 */
class CreateOrderUseCaseImpl(
    private val orderRepository: OrderRepository,
    private val levels: List<OrderLevelConfig>,
) : CreateOrderUseCase {
    override fun invoke(ownerUuid: UUID): Result<Order> {
        val existing = orderRepository.findByOwner(ownerUuid)
        if (existing != null) {
            return Result.failure("Order already exists for $ownerUuid")
        }

        val firstLevel =
            levels.firstOrNull()
                ?: return Result.failure("No level configuration available")

        val order =
            Order(
                ownerUuid = ownerUuid,
                level = firstLevel.level,
                radius = firstLevel.radius,
            )

        val id = orderRepository.insert(order)
        return Result.success(order.copy(id = id))
    }
}
