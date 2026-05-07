package ru.kyamshanov.comminusm.commune.service

import ru.kyamshanov.comminusm.commune.model.CommuneInvitation
import ru.kyamshanov.comminusm.commune.model.Result
import java.time.LocalDateTime
import java.util.UUID

/**
 * Service for managing commune invitations.
 * Handles creation, cancellation, and expiry of invitations.
 *
 * @param invitations Map of invitation UUID -> CommuneInvitation
 * @param invitationTimers Map of invitation UUID -> BukkitTask (for Stage 02+)
 */
class CommuneInvitationService(
    private val invitations: MutableMap<UUID, CommuneInvitation>,
    private val invitationTimers: MutableMap<UUID, Any>,
) {
    /**
     * Create a new invitation.
     * If an invitation for the target order already exists, it is replaced.
     */
    fun createInvitation(
        fromOrderId: Long,
        targetOrderId: Long,
        communeId: UUID,
        targetLeaderUUID: UUID,
        expiresAt: LocalDateTime,
    ): Result<CommuneInvitation> {
        // Check if an invitation already exists for this target
        val existingInvitation = invitations.values.find { it.targetOrderId == targetOrderId }
        if (existingInvitation != null) {
            // Cancel the old invitation's timer
            invitationTimers.remove(existingInvitation.id)
            invitations.remove(existingInvitation.id)
        }

        val invitationId = UUID.randomUUID()
        val invitation =
            CommuneInvitation(
                id = invitationId,
                fromOrderId = fromOrderId,
                targetOrderId = targetOrderId,
                targetLeaderUUID = targetLeaderUUID,
                communeId = communeId,
                expiresAt = expiresAt,
            )

        invitations[invitationId] = invitation

        // In Stage 02, a BukkitTask will be scheduled here
        // For now, just reserve the map entry
        invitationTimers[invitationId] = Unit

        return Result.Success(invitation)
    }

    /**
     * Cancel an invitation by ID.
     */
    fun cancelInvitation(invitationId: UUID): Result<Unit> {
        invitationTimers.remove(invitationId)
        invitations.remove(invitationId)
        return Result.Success(Unit)
    }

    /**
     * Get all invitations targeting a specific order.
     */
    fun getInvitationsForOrder(targetOrderId: Long): Set<CommuneInvitation> =
        invitations.values
            .filter { it.targetOrderId == targetOrderId }
            .toSet()

    /**
     * Get an invitation by ID.
     */
    fun getInvitation(invitationId: UUID): CommuneInvitation? = invitations[invitationId]

    /**
     * Mark an invitation as expired and remove it.
     */
    fun expireInvitation(invitationId: UUID) {
        invitationTimers.remove(invitationId)
        invitations.remove(invitationId)
    }
}
