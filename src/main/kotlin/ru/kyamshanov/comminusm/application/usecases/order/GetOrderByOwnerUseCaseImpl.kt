package ru.kyamshanov.comminusm.application.usecases.order

import ru.kyamshanov.comminusm.domain.entities.Order
import ru.kyamshanov.comminusm.domain.repositories.OrderRepository
import java.util.UUID

/**
 * Implementation of GetOrderByOwnerUseCase.
 * Retrieves an order by the owner's UUID.
 */
class GetOrderByOwnerUseCaseImpl(
    private val orderRepository: OrderRepository,
) : GetOrderByOwnerUseCase {
    override fun invoke(ownerUuid: UUID): Order? = orderRepository.findByOwner(ownerUuid)
}
