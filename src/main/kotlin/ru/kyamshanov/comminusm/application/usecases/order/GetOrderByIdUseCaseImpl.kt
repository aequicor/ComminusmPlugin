package ru.kyamshanov.comminusm.application.usecases.order

import ru.kyamshanov.comminusm.domain.entities.Order
import ru.kyamshanov.comminusm.domain.repositories.OrderRepository

/**
 * Implementation of GetOrderByIdUseCase.
 * Retrieves an order by its ID.
 */
class GetOrderByIdUseCaseImpl(
    private val orderRepository: OrderRepository,
) : GetOrderByIdUseCase {
    override fun invoke(orderId: Long): Order? = orderRepository.findById(orderId)
}
