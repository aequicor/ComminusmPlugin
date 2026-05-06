package ru.kyamshanov.comminusm.commune.model

import java.time.LocalDateTime
import java.util.UUID

/**
 * Represents a pending invitation for one order to join a commune.
 *
 * @param id Unique invitation identifier
 * @param fromOrderId Order that sent the invitation
 * @param targetOrderId Order that received the invitation
 * @param targetLeaderUUID UUID of the target order's leader at the time of invitation
 * @param communeId UUID of the commune the target is invited to
 * @param expiresAt When this invitation expires
 */
data class CommuneInvitation(
    val id: UUID,
    val fromOrderId: Long,
    val targetOrderId: Long,
    val targetLeaderUUID: UUID,
    val communeId: UUID?,
    val expiresAt: LocalDateTime,
)
