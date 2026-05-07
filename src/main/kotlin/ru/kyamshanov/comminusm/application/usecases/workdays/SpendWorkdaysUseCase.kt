package ru.kyamshanov.comminusm.application.usecases.workdays

import ru.kyamshanov.comminusm.domain.value_objects.Result
import java.util.UUID

/**
 * Use Case: Spend workdays from a player's balance.
 * Returns success if enough workdays are available, failure otherwise.
 */
interface SpendWorkdaysUseCase {
    operator fun invoke(
        uuid: UUID,
        amount: Int,
    ): Result<Boolean>
}
