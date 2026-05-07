package ru.kyamshanov.comminusm.application.usecases.commune

import ru.kyamshanov.comminusm.domain.value_objects.Result
import java.util.UUID

/**
 * Use Case: Remove a member from a commune.
 * Only the owner of the commune can remove members.
 * Fails if the commune doesn't exist, the requester is not the owner, or the member is not in the commune.
 */
interface RemoveMemberUseCase {
    /**
     * Removes a member from a commune.
     *
     * @param communeId the ID of the commune
     * @param memberId the ID of the member to remove
     * @param requesterId **MUST be the UUID of the authenticated player performing this action.**
     *                    Must match the commune owner's ID. Passing a forged UUID is a privilege escalation.
     * @return Result.success(Unit) if removal succeeds, Result.failure(message) otherwise
     */
    operator fun invoke(
        communeId: UUID,
        memberId: UUID,
        requesterId: UUID,
    ): Result<Unit>
}
