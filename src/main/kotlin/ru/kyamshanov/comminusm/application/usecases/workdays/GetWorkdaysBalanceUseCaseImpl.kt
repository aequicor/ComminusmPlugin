package ru.kyamshanov.comminusm.application.usecases.workdays

import ru.kyamshanov.comminusm.domain.repositories.WorkdaysRepository
import java.util.UUID

/**
 * Implementation of GetWorkdaysBalanceUseCase.
 * Retrieves the current workdays balance for a player.
 */
class GetWorkdaysBalanceUseCaseImpl(
    private val workdaysRepository: WorkdaysRepository,
) : GetWorkdaysBalanceUseCase {
    override fun invoke(uuid: UUID): Int = workdaysRepository.getBalance(uuid)
}
