package ru.kyamshanov.comminusm.application.usecases.commune

import ru.kyamshanov.comminusm.domain.value_objects.Result
import java.util.UUID

/**
 * Use Case: Disband a commune (delete it).
 * Fails if the commune doesn't exist or the requester is not the owner.
 */
interface DisbandCommuneUseCase {
    /**
     * Disbands a commune (deletes it). Only the owner can disband.
     *
     * @param communeId the ID of the commune to disband
     * @param requesterId **MUST be the UUID of the authenticated player performing this action.**
     *                    Never accept this parameter from untrusted input (command args, form data).
     *                    Extract from Bukkit player context: `player.uniqueId`.
     *                    Passing a guessed/forged UUID is a privilege escalation vulnerability.
     * @return Result.success(Unit) if disband succeeds, Result.failure(message) otherwise
     */
    operator fun invoke(
        communeId: UUID,
        requesterId: UUID,
    ): Result<Unit>
}
