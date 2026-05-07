package ru.kyamshanov.comminusm.application.usecases.commune

import ru.kyamshanov.comminusm.domain.value_objects.CommuneInvitation
import ru.kyamshanov.comminusm.domain.value_objects.Result
import java.util.UUID

/**
 * Use Case: Send an invitation to join a commune.
 * Fails if commune doesn't exist, inviter lacks permission, or invitee is already a member.
 */
interface InviteMemberUseCase {
    operator fun invoke(
        communeId: UUID,
        inviteeId: UUID,
        inviterId: UUID,
    ): Result<CommuneInvitation>
}
