package ru.kyamshanov.comminusm.application.usecases.workdays

import ru.kyamshanov.comminusm.domain.value_objects.Result
import java.util.UUID

/**
 * Use Case: Increment a player's workdays balance.
 * Returns the new balance.
 */
interface IncrementWorkdaysUseCase {
    operator fun invoke(
        uuid: UUID,
        amount: Int,
    ): Result<Int>
}
