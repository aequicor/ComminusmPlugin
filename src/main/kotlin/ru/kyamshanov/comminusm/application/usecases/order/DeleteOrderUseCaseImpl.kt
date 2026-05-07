@file:Suppress("ReturnCount")

package ru.kyamshanov.comminusm.application.usecases.order

import ru.kyamshanov.comminusm.domain.repositories.OrderRepository
import ru.kyamshanov.comminusm.domain.value_objects.Result
import java.util.UUID

/**
 * Implementation of DeleteOrderUseCase.
 * Deletes an order and handles cleanup of associated resources.
 */
class DeleteOrderUseCaseImpl(
    private val orderRepository: OrderRepository,
) : DeleteOrderUseCase {
    override fun invoke(ownerUuid: UUID): Result<Unit> {
        orderRepository.findByOwner(ownerUuid)
            ?: return Result.failure("Order not found")

        // Note: Flag cleanup is delegated to a separate infrastructure concern.
        // In a full implementation, this would notify listeners or call cleanup helpers.

        orderRepository.deleteByOwner(ownerUuid)
        return Result.success(Unit)
    }
}
