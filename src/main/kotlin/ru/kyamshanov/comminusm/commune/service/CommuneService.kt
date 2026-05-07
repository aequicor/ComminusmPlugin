package ru.kyamshanov.comminusm.commune.service

import ru.kyamshanov.comminusm.commune.model.Commune
import ru.kyamshanov.comminusm.commune.model.Result
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Core service for commune lifecycle management.
 * In-memory source of truth for communes (persisted asynchronously to storage).
 *
 * @param communes Map of UUID -> Commune
 * @param orderToCommuneId Map of Long (orderId) -> UUID (communeId)
 */
class CommuneService(
    private val communes: MutableMap<UUID, Commune>,
    private val orderToCommuneId: MutableMap<Long, UUID>,
) {
    private val lock = ReentrantReadWriteLock()

    /**
     * Create a new commune with a single initial order.
     */
    fun createCommune(
        leadingOrderId: Long,
        createdBy: UUID,
    ): Result<Commune> {
        return lock.write {
            if (orderToCommuneId.containsKey(leadingOrderId)) {
                return@write Result.Failure("Order is already in a commune")
            }

            val communeId = UUID.randomUUID()
            val commune =
                Commune(
                    id = communeId,
                    orderIds = setOf(leadingOrderId),
                    version = 0,
                    createdAt = LocalDateTime.now(),
                    createdBy = createdBy,
                )

            communes[communeId] = commune
            orderToCommuneId[leadingOrderId] = communeId

            Result.Success(commune)
        }
    }

    /**
     * Get a commune by ID.
     */
    fun getCommune(communeId: UUID): Commune? =
        lock.read {
            communes[communeId]
        }

    /**
     * Get the commune containing a specific order.
     */
    fun getCommuneOfOrder(orderId: Long): Commune? =
        lock.read {
            val communeId = orderToCommuneId[orderId]
            communeId?.let { communes[it] }
        }

    /**
     * Add an order to a commune, incrementing the version.
     */
    fun addOrderToCommune(
        communeId: UUID,
        orderId: Long,
    ): Result<Unit> {
        return lock.write {
            if (orderToCommuneId.containsKey(orderId)) {
                return@write Result.Failure("Order is already in a commune")
            }

            val commune =
                communes[communeId]
                    ?: return@write Result.Failure("Commune not found")

            val updatedCommune =
                commune.copy(
                    orderIds = commune.orderIds + orderId,
                    version = commune.version + 1,
                )

            communes[communeId] = updatedCommune
            orderToCommuneId[orderId] = communeId

            Result.Success(Unit)
        }
    }

    /**
     * Remove an order from a commune, incrementing the version.
     */
    fun removeOrderFromCommune(
        communeId: UUID,
        orderId: Long,
    ): Result<Unit> {
        return lock.write {
            val commune =
                communes[communeId]
                    ?: return@write Result.Failure("Commune not found")

            if (!commune.orderIds.contains(orderId)) {
                return@write Result.Failure("Order is not in this commune")
            }

            val updatedOrderIds = commune.orderIds - orderId
            val updatedCommune =
                commune.copy(
                    orderIds = updatedOrderIds,
                    version = commune.version + 1,
                )

            communes[communeId] = updatedCommune
            orderToCommuneId.remove(orderId)

            Result.Success(Unit)
        }
    }

    /**
     * Dissolve a commune, removing it entirely.
     */
    fun dissolveCommune(communeId: UUID): Result<Unit> {
        return lock.write {
            val commune =
                communes.remove(communeId)
                    ?: return@write Result.Failure("Commune not found")

            commune.orderIds.forEach { orderId ->
                orderToCommuneId.remove(orderId)
            }

            Result.Success(Unit)
        }
    }

    /**
     * Increment the version of a commune.
     * Returns the new version.
     */
    fun incrementVersion(communeId: UUID): Long {
        return lock.write {
            val commune =
                communes[communeId]
                    ?: return@write 0L

            val newVersion = commune.version + 1
            communes[communeId] = commune.copy(version = newVersion)
            newVersion
        }
    }

    /**
     * Get all order IDs in a commune.
     */
    fun getCommuneOrders(communeId: UUID): Set<Long> =
        lock.read {
            communes[communeId]?.orderIds ?: emptySet()
        }
}
