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
    companion object {
        private const val MAX_NAME_LENGTH = 20

        /**
         * Sanitizes a player nickname into a valid order name.
         *
         * Replaces invalid characters with underscores. If the result is blank or all underscores,
         * falls back to "Order". Truncates to 20 characters.
         */
        fun sanitizeNickname(nickname: String): String {
            val result = nickname.replace(Regex("[^A-Za-zА-Яа-яЁё0-9\\-_]"), "_")
            return if (result.isBlank() || result.all { it == '_' }) {
                "Order"
            } else {
                result.take(MAX_NAME_LENGTH)
            }
        }
    }

    override fun invoke(
        ownerUuid: UUID,
        playerName: String,
    ): Result<Order> {
        val existing = orderRepository.findByOwner(ownerUuid)
        if (existing != null) {
            return Result.failure("Order already exists for $ownerUuid")
        }

        val firstLevel =
            levels.firstOrNull()
                ?: return Result.failure("No level configuration available")

        val sanitizedName = sanitizeNickname(playerName)

        val order =
            Order(
                ownerUuid = ownerUuid,
                name = sanitizedName,
                level = firstLevel.level,
                radius = firstLevel.radius,
            )

        val id = orderRepository.insert(order)
        return Result.success(order.copy(id = id))
    }
}
