package ru.kyamshanov.comminusm.commune.repository

import ru.kyamshanov.comminusm.commune.model.OrderMember
import java.time.LocalDateTime
import java.util.UUID

/**
 * In-memory repository for order members.
 * Serves as the runtime source of truth before Stage 02 persistence.
 *
 * @param cache ConcurrentHashMap keyed by orderId, containing sets of OrderMember
 */
class OrderMembersRepository(
    private val cache: MutableMap<Long, MutableSet<OrderMember>>,
) {
    /**
     * Add a member to an order.
     * Returns the added OrderMember or existing member if it already exists.
     */
    fun addMember(
        orderId: Long,
        playerUuid: UUID,
        grantedVia: String,
        grantedAt: LocalDateTime,
    ): OrderMember {
        val members = cache.getOrPut(orderId) { mutableSetOf() }
        val member =
            OrderMember(
                playerUuid = playerUuid,
                orderId = orderId,
                grantedAt = grantedAt,
                grantedVia = grantedVia,
            )
        members.add(member)
        return member
    }

    /**
     * Remove a member from an order.
     * Returns true if member was removed, false if not found.
     */
    fun removeMember(
        orderId: Long,
        playerUuid: UUID,
    ): Boolean {
        val members = cache[orderId] ?: return false
        return members.removeIf { it.playerUuid == playerUuid }
    }

    /**
     * Get all members of an order.
     */
    fun getMembersOfOrder(orderId: Long): Set<OrderMember> = cache[orderId]?.toSet() ?: emptySet()

    /**
     * Get all order IDs where a player is a member.
     */
    fun getOrdersOfPlayer(playerUuid: UUID): Set<Long> =
        cache.entries
            .toSet()
            .filter { (_, members) -> members.any { it.playerUuid == playerUuid } }
            .map { it.key }
            .toSet()

    /**
     * Check if a player is a member of an order.
     */
    fun isMember(
        orderId: Long,
        playerUuid: UUID,
    ): Boolean = cache[orderId]?.any { it.playerUuid == playerUuid } ?: false

    /**
     * Get members of a specific type ("native" or "commune").
     */
    fun getMembersWithType(
        orderId: Long,
        grantedVia: String,
    ): Set<OrderMember> = cache[orderId]?.filter { it.grantedVia == grantedVia }?.toSet() ?: emptySet()
}
