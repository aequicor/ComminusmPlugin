package ru.kyamshanov.comminusm.commune.service

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import ru.kyamshanov.comminusm.commune.model.CommuneInvitation
import ru.kyamshanov.comminusm.commune.model.Result
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

/**
 * Service for managing commune invitations.
 * Handles creation, cancellation, and expiry of invitations.
 *
 * @param invitations Map of invitation UUID -> CommuneInvitation
 * @param invitationTimers Map of invitation UUID -> BukkitTask ID (Int). -1 if test mode (plugin=null).
 * @param plugin Bukkit plugin instance for scheduling tasks. null = test mode (no real scheduling).
 */
class CommuneInvitationService(
    private val invitations: MutableMap<UUID, CommuneInvitation>,
    private val invitationTimers: MutableMap<UUID, Int>,
    private val plugin: Plugin? = null,
) {
    companion object {
        private const val TICKS_PER_SECOND = 20L
    }

    /**
     * Create a new invitation.
     * If an invitation for the target order already exists, it is replaced.
     * Schedules a BukkitTask to auto-expire the invitation.
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
            cancelTimer(existingInvitation.id)
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

        // Schedule expiry task
        scheduleExpiry(invitationId, expiresAt)

        return Result.Success(invitation)
    }

    /**
     * Cancel an invitation by ID.
     * Cancels the scheduled expiry task.
     */
    fun cancelInvitation(invitationId: UUID): Result<Unit> {
        cancelTimer(invitationId)
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
     * Cancels the scheduled expiry task.
     */
    fun expireInvitation(invitationId: UUID) {
        cancelTimer(invitationId)
        invitations.remove(invitationId)
    }

    /**
     * Schedule an expiry task for the invitation.
     * In test mode (plugin=null), stores -1. In real mode, schedules via Bukkit.
     */
    private fun scheduleExpiry(
        invitationId: UUID,
        expiresAt: LocalDateTime,
    ) {
        if (plugin != null) {
            val ticksUntilExpiry = calculateExpiryTicks(expiresAt)
            if (ticksUntilExpiry > 0) {
                val scheduler = Bukkit.getScheduler()
                val runnable = Runnable { expireInvitation(invitationId) }
                val taskId = scheduler.runTaskLater(plugin, runnable, ticksUntilExpiry).taskId
                invitationTimers[invitationId] = taskId
            } else {
                // Already expired or expires immediately
                invitationTimers[invitationId] = -1
                expireInvitation(invitationId)
            }
        } else {
            // Test mode: store -1 to indicate no real task scheduled
            invitationTimers[invitationId] = -1
        }
    }

    /**
     * Cancel a scheduled expiry task.
     * Removes the task from Bukkit scheduler if plugin is available.
     */
    private fun cancelTimer(invitationId: UUID) {
        val taskId = invitationTimers.remove(invitationId)
        if (taskId != null && taskId != -1 && plugin != null) {
            Bukkit.getScheduler().cancelTask(taskId)
        }
    }

    /**
     * Calculate the number of ticks until expiry.
     * Bukkit uses 20 ticks per second.
     */
    private fun calculateExpiryTicks(expiresAt: LocalDateTime): Long {
        val now = LocalDateTime.now()
        val duration = Duration.between(now, expiresAt)
        val seconds = duration.seconds
        return if (seconds > 0) seconds * TICKS_PER_SECOND else 0L
    }
}
