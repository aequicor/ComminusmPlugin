@file:Suppress("ReturnCount")

package ru.kyamshanov.comminusm.application.usecases.commune

import ru.kyamshanov.comminusm.domain.repositories.CommuneRepository
import ru.kyamshanov.comminusm.domain.value_objects.Result
import java.util.UUID

/**
 * Implementation of DisbandCommuneUseCase.
 * Disbands a commune (deletes it).
 */
class DisbandCommuneUseCaseImpl(
    private val communeRepository: CommuneRepository,
) : DisbandCommuneUseCase {
    override fun invoke(
        communeId: UUID,
        requesterId: UUID,
    ): Result<Unit> {
        val commune =
            communeRepository.findById(communeId)
                ?: return Result.failure("Commune not found")

        if (commune.ownerId != requesterId) {
            return Result.failure("Only the owner can disband the commune")
        }

        communeRepository.delete(communeId)
        return Result.success(Unit)
    }
}
