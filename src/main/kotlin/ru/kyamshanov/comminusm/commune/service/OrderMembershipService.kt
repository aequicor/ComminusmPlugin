package ru.kyamshanov.comminusm.commune.service

import org.bukkit.Bukkit
import ru.kyamshanov.comminusm.commune.event.OrderMemberAddedEvent
import ru.kyamshanov.comminusm.commune.event.OrderMemberRemovedEvent
import ru.kyamshanov.comminusm.commune.model.OrderMember
import ru.kyamshanov.comminusm.commune.model.Result
import ru.kyamshanov.comminusm.commune.repository.OrderMembersRepository
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * High-level service for managing order memberships (both native and commune-granted).
 * Provides atomic operations with per-order locking.
 *
 * In Stage 02+, publishes OrderMemberAddedEvent and OrderMemberRemovedEvent.
 */
class OrderMembershipService(
    private val repository: OrderMembersRepository
) {
    private val orderLocks = ConcurrentHashMap<Long, ReentrantReadWriteLock>()

    /**
     * Add a member to an order.
     * Acquires a write lock on the order to ensure atomicity.
     */
    fun addNativeMember(
        orderId: Long,
        playerUuid: UUID,
        @Suppress("UNUSED_PARAMETER") initiator: UUID,
        grantedAt: LocalDateTime = LocalDateTime.now()
    ): Result<OrderMember> {
        return addMember(orderId, playerUuid, "native", grantedAt, initiator)
    }

    /**
     * Add a member to an order with specified grantedVia type.
     * Internal API used by CrossOrderMembershipService.
     */
    fun addMember(
        orderId: Long,
        playerUuid: UUID,
        grantedVia: String,
        grantedAt: LocalDateTime = LocalDateTime.now(),
        @Suppress("UNUSED_PARAMETER") initiator: UUID = UUID.randomUUID()
    ): Result<OrderMember> {
        val lock = orderLocks.getOrPut(orderId) { ReentrantReadWriteLock() }
        return lock.write {
            // Check if member already exists
            if (repository.isMember(orderId, playerUuid)) {
                return@write Result.Failure("Player is already a member of this order")
            }

            val member = repository.addMember(orderId, playerUuid, grantedVia, grantedAt)

            // Publish OrderMemberAddedEvent
            try {
                Bukkit.getPluginManager().callEvent(OrderMemberAddedEvent(orderId, playerUuid, grantedVia))
            } catch (e: RuntimeException) {
                // Bukkit may not be initialized in tests; ignore event publication errors
            }

            Result.Success(member)
        }
    }

    /**
     * Remove a member from an order (public API, publishes events).
     * In Stage 02+, also triggers cross-order membership recalculation.
     * Wraps removeMember with grantedVia="native" for backward compatibility.
     */
    fun removeNativeMember(
        orderId: Long,
        playerUuid: UUID,
        @Suppress("UNUSED_PARAMETER") initiator: UUID
    ): Result<Boolean> {
        return removeMember(orderId, playerUuid, "native")
    }

    /**
     * Remove a member from an order with specified grantedVia type (public API, publishes events).
     * Honors spec §6.10-§6.11: publishes OrderMemberRemovedEvent with correct grantedVia.
     * HIGH issue #6: add overload for grantedVia parameter.
     */
    fun removeMember(
        orderId: Long,
        playerUuid: UUID,
        grantedVia: String
    ): Result<Boolean> {
        val lock = orderLocks.getOrPut(orderId) { ReentrantReadWriteLock() }
        return lock.write {
            val removed = repository.removeMember(orderId, playerUuid)
            if (!removed) {
                return@write Result.Failure("Player is not a member of this order")
            }

            // Publish OrderMemberRemovedEvent with correct grantedVia
            try {
                Bukkit.getPluginManager().callEvent(OrderMemberRemovedEvent(orderId, playerUuid, grantedVia))
            } catch (e: RuntimeException) {
                // Bukkit may not be initialized in tests; ignore event publication errors
            }

            Result.Success(true)
        }
    }

    /**
     * Check if a player is a native member of an order.
     */
    fun isNativeMember(orderId: Long, playerUuid: UUID): Boolean {
        val lock = orderLocks.getOrPut(orderId) { ReentrantReadWriteLock() }
        return lock.read {
            val members = repository.getMembersWithType(orderId, "native")
            members.any { it.playerUuid == playerUuid }
        }
    }

    /**
     * Get all members of an order (both native and commune-granted).
     */
    fun getMembersOfOrder(orderId: Long): Set<OrderMember> {
        val lock = orderLocks.getOrPut(orderId) { ReentrantReadWriteLock() }
        return lock.read {
            repository.getMembersOfOrder(orderId)
        }
    }

    /**
     * Internal API: Remove a member silently without publishing events.
     * Used during cascade operations and startup checks.
     * Not guarded by lock - caller is responsible for serialization.
     */
    internal fun removeMemberSilently(orderId: Long, playerUuid: UUID) {
        repository.removeMember(orderId, playerUuid)
    }

    /**
     * Internal API: Add a member silently without publishing events.
     * Used during startup load phase.
     */
    internal fun addMemberSilently(
        orderId: Long,
        playerUuid: UUID,
        grantedVia: String,
        grantedAt: LocalDateTime
    ) {
        repository.addMember(orderId, playerUuid, grantedVia, grantedAt)
    }

    /**
     * Get all orders where a player is a member.
     */
    fun getOrdersOfPlayer(playerUuid: UUID): Set<Long> {
        return repository.getOrdersOfPlayer(playerUuid)
    }

    /**
     * Get all orders where a player is a native member.
     */
    fun getNativeOrdersOfPlayer(playerUuid: UUID): Set<Long> {
        val allOrders = repository.getOrdersOfPlayer(playerUuid)
        return allOrders.filter { orderId ->
            repository.getMembersWithType(orderId, "native").any { it.playerUuid == playerUuid }
        }.toSet()
    }
}
