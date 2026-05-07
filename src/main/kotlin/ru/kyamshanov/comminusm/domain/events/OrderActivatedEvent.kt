package ru.kyamshanov.comminusm.domain.events

/**
 * Domain event fired when an order is activated with a location.
 */
data class OrderActivatedEvent(
    val orderId: Long,
    val world: String,
    val x: Int,
    val y: Int,
    val z: Int,
)
