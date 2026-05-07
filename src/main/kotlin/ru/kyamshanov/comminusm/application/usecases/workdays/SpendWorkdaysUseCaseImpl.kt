@file:Suppress("ReturnCount")

package ru.kyamshanov.comminusm.application.usecases.workdays

import ru.kyamshanov.comminusm.domain.repositories.WorkdaysRepository
import ru.kyamshanov.comminusm.domain.value_objects.Result
import java.util.UUID

/**
 * Implementation of SpendWorkdaysUseCase.
 * Deducts workdays from a player's balance if enough are available.
 */
class SpendWorkdaysUseCaseImpl(
    private val workdaysRepository: WorkdaysRepository,
) : SpendWorkdaysUseCase {
    override fun invoke(
        uuid: UUID,
        amount: Int,
    ): Result<Boolean> {
        if (amount < 0) {
            return Result.failure("Amount must be non-negative")
        }

        val success = workdaysRepository.spend(uuid, amount)
        if (!success) {
            return Result.failure("Insufficient workdays")
        }

        return Result.success(true)
    }
}
