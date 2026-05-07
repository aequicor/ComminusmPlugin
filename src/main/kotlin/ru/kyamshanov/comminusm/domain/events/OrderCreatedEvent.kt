package ru.kyamshanov.comminusm.domain.events

import java.util.UUID

/**
 * Domain event fired when an order is created.
 */
data class OrderCreatedEvent(
    val orderId: Long,
    val ownerUuid: UUID,
    val timestamp: Long = System.currentTimeMillis(),
)
