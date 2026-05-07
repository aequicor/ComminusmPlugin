@file:Suppress("PackageName")

package ru.kyamshanov.comminusm.domain.value_objects

import java.util.UUID

/**
 * Domain value object representing an invitation to join a commune.
 *
 * @param id Unique invitation identifier
 * @param communeId ID of the commune
 * @param inviteeId UUID of the player being invited
 * @param inviterId UUID of the player who sent the invitation
 * @param createdAt Timestamp of invitation creation (milliseconds since epoch)
 */
data class CommuneInvitation(
    val id: UUID = UUID.randomUUID(),
    val communeId: UUID,
    val inviteeId: UUID,
    val inviterId: UUID,
    val createdAt: Long = System.currentTimeMillis(),
)
