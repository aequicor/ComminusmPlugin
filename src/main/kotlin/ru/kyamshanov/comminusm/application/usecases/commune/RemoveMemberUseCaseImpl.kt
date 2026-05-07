@file:Suppress("ReturnCount")

package ru.kyamshanov.comminusm.application.usecases.commune

import ru.kyamshanov.comminusm.domain.repositories.CommuneRepository
import ru.kyamshanov.comminusm.domain.value_objects.Result
import java.util.UUID

/**
 * Implementation of RemoveMemberUseCase.
 * Removes a member from a commune.
 * Authorization: only the owner can remove members.
 */
class RemoveMemberUseCaseImpl(
    private val communeRepository: CommuneRepository,
) : RemoveMemberUseCase {
    override fun invoke(
        communeId: UUID,
        memberId: UUID,
        requesterId: UUID,
    ): Result<Unit> {
        val commune =
            communeRepository.findById(communeId)
                ?: return Result.failure("Commune not found")

        // Authorization: only owner can remove members
        if (commune.ownerId != requesterId) {
            return Result.failure("Only owner can remove members")
        }

        if (!commune.memberIds.contains(memberId)) {
            return Result.failure("Member not in this commune")
        }

        val updatedCommune =
            commune.copy(
                memberIds = commune.memberIds - memberId,
            )

        communeRepository.update(updatedCommune)
        return Result.success(Unit)
    }
}
