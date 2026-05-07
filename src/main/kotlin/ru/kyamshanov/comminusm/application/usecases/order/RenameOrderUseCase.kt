package ru.kyamshanov.comminusm.application.usecases.order

import ru.kyamshanov.comminusm.domain.value_objects.Result
import java.util.UUID

/**
 * Use Case: Rename an Order by owner UUID.
 * Validates the new name and updates the order in the repository.
 * Returns a Result indicating success or failure.
 */
interface RenameOrderUseCase {
    operator fun invoke(
        ownerUuid: UUID,
        newName: String,
    ): Result<Unit>
}
