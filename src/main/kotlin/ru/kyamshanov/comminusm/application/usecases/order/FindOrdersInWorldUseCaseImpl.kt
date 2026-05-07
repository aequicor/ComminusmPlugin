package ru.kyamshanov.comminusm.application.usecases.order

import ru.kyamshanov.comminusm.domain.entities.Order
import ru.kyamshanov.comminusm.domain.repositories.OrderRepository

/**
 * Implementation of FindOrdersInWorldUseCase.
 * Finds all activated orders in a specific world.
 */
class FindOrdersInWorldUseCaseImpl(
    private val orderRepository: OrderRepository,
) : FindOrdersInWorldUseCase {
    override fun invoke(world: String): List<Order> = orderRepository.findAllInWorld(world)
}
