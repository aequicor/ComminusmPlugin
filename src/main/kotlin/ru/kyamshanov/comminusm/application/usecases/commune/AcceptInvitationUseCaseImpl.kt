@file:Suppress("ReturnCount")

package ru.kyamshanov.comminusm.application.usecases.commune

import ru.kyamshanov.comminusm.domain.repositories.CommuneInvitationRepository
import ru.kyamshanov.comminusm.domain.repositories.CommuneRepository
import ru.kyamshanov.comminusm.domain.value_objects.Result
import java.util.UUID

/**
 * Implementation of AcceptInvitationUseCase.
 * Accepts an invitation and adds the invitee to the commune.
 * Authorization: only the intended invitee can accept their own invitation.
 */
class AcceptInvitationUseCaseImpl(
    private val communeRepository: CommuneRepository,
    private val invitationRepository: CommuneInvitationRepository,
) : AcceptInvitationUseCase {
    override fun invoke(
        invitationId: UUID,
        inviteeId: UUID,
    ): Result<Unit> {
        val invitation =
            invitationRepository.findById(invitationId)
                ?: return Result.failure("Invitation not found")

        // Authorization: verify invitee identity matches
        if (invitation.inviteeId != inviteeId) {
            return Result.failure("Invitation is not for you")
        }

        val commune =
            communeRepository.findById(invitation.communeId)
                ?: return Result.failure("Commune not found")

        if (commune.memberIds.contains(invitation.inviteeId)) {
            return Result.failure("Already a member of this commune")
        }

        val updatedCommune =
            commune.copy(
                memberIds = commune.memberIds + invitation.inviteeId,
            )

        communeRepository.update(updatedCommune)
        invitationRepository.delete(invitationId)

        return Result.success(Unit)
    }
}
