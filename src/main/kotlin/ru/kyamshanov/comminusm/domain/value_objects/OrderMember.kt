@file:Suppress("PackageName")

package ru.kyamshanov.comminusm.domain.value_objects

import java.util.UUID

/**
 * Domain value object representing membership of a player in an order.
 *
 * @param uuid UUID of the player
 * @param orderId ID of the order they are a member of
 * @param joinedAt Timestamp of when they joined (milliseconds since epoch)
 */
data class OrderMember(
    val uuid: UUID,
    val orderId: Long,
    val joinedAt: Long = System.currentTimeMillis(),
)
