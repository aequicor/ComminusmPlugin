package ru.kyamshanov.comminusm.application.usecases.commune

import ru.kyamshanov.comminusm.domain.value_objects.Result
import java.util.UUID

/**
 * Use Case: Accept an invitation to join a commune.
 * Fails if the invitation doesn't exist, the invitee identity doesn't match, or is otherwise invalid.
 */
interface AcceptInvitationUseCase {
    /**
     * Accepts an invitation to join a commune.
     *
     * @param invitationId the ID of the invitation to accept
     * @param inviteeId **MUST be the UUID of the authenticated player accepting the invitation.**
     *                  This prevents privilege escalation where one player accepts on behalf of another.
     *                  Extract from Bukkit player context: `player.uniqueId`.
     * @return Result.success(Unit) if acceptance succeeds, Result.failure(message) otherwise
     */
    operator fun invoke(
        invitationId: UUID,
        inviteeId: UUID,
    ): Result<Unit>
}
