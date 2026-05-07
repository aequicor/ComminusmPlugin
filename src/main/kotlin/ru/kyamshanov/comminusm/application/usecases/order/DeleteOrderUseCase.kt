package ru.kyamshanov.comminusm.application.usecases.order

import ru.kyamshanov.comminusm.domain.value_objects.Result
import java.util.UUID

/**
 * Use Case: Delete an Order by owner UUID.
 * Handles cleanup of flags if the order is activated.
 * Fails if the order doesn't exist.
 */
interface DeleteOrderUseCase {
    operator fun invoke(ownerUuid: UUID): Result<Unit>
}
