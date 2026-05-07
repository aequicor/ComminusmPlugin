package ru.kyamshanov.comminusm.application.usecases.order

import ru.kyamshanov.comminusm.domain.entities.Order

/**
 * Use Case: Retrieve an Order by its ID.
 * Returns null if the order doesn't exist.
 */
interface GetOrderByIdUseCase {
    operator fun invoke(orderId: Long): Order?
}
