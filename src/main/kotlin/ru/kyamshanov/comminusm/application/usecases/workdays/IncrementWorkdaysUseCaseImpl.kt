package ru.kyamshanov.comminusm.application.usecases.workdays

import ru.kyamshanov.comminusm.domain.repositories.WorkdaysRepository
import ru.kyamshanov.comminusm.domain.value_objects.Result
import java.util.UUID

/**
 * Implementation of IncrementWorkdaysUseCase.
 * Adds workdays to a player's balance.
 */
class IncrementWorkdaysUseCaseImpl(
    private val workdaysRepository: WorkdaysRepository,
) : IncrementWorkdaysUseCase {
    override fun invoke(
        uuid: UUID,
        amount: Int,
    ): Result<Int> {
        if (amount < 0) {
            return Result.failure("Amount must be non-negative")
        }

        workdaysRepository.add(uuid, amount)
        val newBalance = workdaysRepository.getBalance(uuid)
        return Result.success(newBalance)
    }
}
