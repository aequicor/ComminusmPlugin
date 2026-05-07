package ru.kyamshanov.comminusm.domain.repositories

import ru.kyamshanov.comminusm.domain.entities.Order
import java.util.UUID

/**
 * Domain repository interface for Order entities.
 * Defines contracts for order persistence operations without exposing implementation details.
 */
interface OrderRepository {
    fun findByOwner(uuid: UUID): Order?

    fun findById(id: Long): Order?

    fun findAllInWorld(world: String): List<Order>

    fun findAllActivated(): List<Order>

    fun insert(order: Order): Long

    fun update(order: Order)

    fun activate(
        uuid: UUID,
        world: String,
        x: Int,
        y: Int,
        z: Int,
    )

    fun updateLevel(
        uuid: UUID,
        level: Int,
        radius: Int,
    )

    fun deleteByOwner(uuid: UUID)

    fun rename(
        id: Long,
        name: String,
    )
}
