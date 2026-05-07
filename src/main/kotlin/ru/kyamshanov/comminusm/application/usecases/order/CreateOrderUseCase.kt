package ru.kyamshanov.comminusm.application.usecases.order

import ru.kyamshanov.comminusm.domain.entities.Order
import ru.kyamshanov.comminusm.domain.value_objects.Result
import java.util.UUID

/**
 * Use Case: Create a new Order for a player.
 * Fails if the player already has an order or no level configuration exists.
 */
interface CreateOrderUseCase {
    operator fun invoke(ownerUuid: UUID): Result<Order>
}
