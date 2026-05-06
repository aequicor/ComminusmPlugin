package ru.kyamshanov.comminusm.commune.model

import java.time.LocalDateTime
import java.util.UUID

/**
 * Represents a player's membership in an order (native or commune-granted).
 *
 * @param playerUuid UUID of the player
 * @param orderId ID of the order
 * @param grantedAt Timestamp when the membership was granted
 * @param grantedVia "native" or "commune" to distinguish membership type
 */
data class OrderMember(
    val playerUuid: UUID,
    val orderId: Long,
    val grantedAt: LocalDateTime,
    val grantedVia: String, // "native" or "commune"
)
