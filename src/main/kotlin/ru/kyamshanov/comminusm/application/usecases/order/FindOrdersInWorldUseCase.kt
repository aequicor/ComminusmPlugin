package ru.kyamshanov.comminusm.application.usecases.order

import ru.kyamshanov.comminusm.domain.entities.Order

/**
 * Use Case: Find all activated Orders in a specific world.
 * Returns empty list if no orders exist in the world.
 */
interface FindOrdersInWorldUseCase {
    operator fun invoke(world: String): List<Order>
}
