@file:Suppress("ReturnCount")

package ru.kyamshanov.comminusm.application.usecases.order

import ru.kyamshanov.comminusm.domain.repositories.OrderRepository
import ru.kyamshanov.comminusm.domain.value_objects.Result
import ru.kyamshanov.comminusm.domain.value_objects.WorldLocation
import java.util.UUID

/**
 * Implementation of ActivateOrderUseCase.
 * Activates an order at a location, checking for overlaps with existing orders.
 */
class ActivateOrderUseCaseImpl(
    private val orderRepository: OrderRepository,
    private val overlapChecker: CheckOrderOverlapUseCase,
) : ActivateOrderUseCase {
    override fun invoke(
        ownerUuid: UUID,
        location: WorldLocation,
    ): Result<Unit> {
        val order =
            orderRepository.findByOwner(ownerUuid)
                ?: return Result.failure("Order not found")

        if (order.isActivated()) {
            return Result.failure("Order already activated")
        }

        val hasOverlap = overlapChecker(location.x, location.y, location.z, order.radius, location.world)
        if (hasOverlap) {
            return Result.failure("Order overlaps with another order")
        }

        orderRepository.activate(ownerUuid, location.world, location.x, location.y, location.z)
        return Result.success(Unit)
    }
}
