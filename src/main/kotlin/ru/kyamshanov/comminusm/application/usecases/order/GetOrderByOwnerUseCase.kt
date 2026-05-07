package ru.kyamshanov.comminusm.application.usecases.order

import ru.kyamshanov.comminusm.domain.entities.Order
import java.util.UUID

/**
 * Use Case: Retrieve an Order by its owner UUID.
 * Returns null if the order doesn't exist.
 */
interface GetOrderByOwnerUseCase {
    operator fun invoke(ownerUuid: UUID): Order?
}
