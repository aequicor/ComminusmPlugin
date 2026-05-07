@file:Suppress("ReturnCount")

package ru.kyamshanov.comminusm.application.usecases.commune

import ru.kyamshanov.comminusm.domain.repositories.CommuneInvitationRepository
import ru.kyamshanov.comminusm.domain.repositories.CommuneRepository
import ru.kyamshanov.comminusm.domain.value_objects.CommuneInvitation
import ru.kyamshanov.comminusm.domain.value_objects.Result
import java.util.UUID

/**
 * Implementation of InviteMemberUseCase.
 * Creates an invitation for a player to join a commune.
 */
class InviteMemberUseCaseImpl(
    private val communeRepository: CommuneRepository,
    private val invitationRepository: CommuneInvitationRepository,
) : InviteMemberUseCase {
    override fun invoke(
        communeId: UUID,
        inviteeId: UUID,
        inviterId: UUID,
    ): Result<CommuneInvitation> {
        val commune =
            communeRepository.findById(communeId)
                ?: return Result.failure("Commune not found")

        if (commune.ownerId != inviterId && !commune.memberIds.contains(inviterId)) {
            return Result.failure("Not permitted to invite members")
        }

        if (commune.memberIds.contains(inviteeId)) {
            return Result.failure("Already a member of this commune")
        }

        val invitation =
            CommuneInvitation(
                communeId = communeId,
                inviteeId = inviteeId,
                inviterId = inviterId,
            )

        invitationRepository.insert(invitation)
        return Result.success(invitation)
    }
}
