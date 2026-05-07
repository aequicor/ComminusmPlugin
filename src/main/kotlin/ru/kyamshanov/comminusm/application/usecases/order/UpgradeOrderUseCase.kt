package ru.kyamshanov.comminusm.application.usecases.order

import ru.kyamshanov.comminusm.domain.entities.Order
import ru.kyamshanov.comminusm.domain.value_objects.Result
import java.util.UUID

/**
 * Use Case: Upgrade an Order to the next level.
 * Fails if the order doesn't exist, is already at max level, or insufficient workdays.
 */
interface UpgradeOrderUseCase {
    operator fun invoke(ownerUuid: UUID): Result<Order>
}
