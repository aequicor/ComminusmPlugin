package ru.kyamshanov.comminusm.application.usecases.order

import ru.kyamshanov.comminusm.domain.value_objects.Result
import ru.kyamshanov.comminusm.domain.value_objects.WorldLocation
import java.util.UUID

/**
 * Use Case: Activate an Order at a specific world location.
 * Fails if the order doesn't exist, is already activated, or overlaps with another order.
 */
interface ActivateOrderUseCase {
    operator fun invoke(
        ownerUuid: UUID,
        location: WorldLocation,
    ): Result<Unit>
}
