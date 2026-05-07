package ru.kyamshanov.comminusm.commune.repository

import ru.kyamshanov.comminusm.commune.model.OrderMember
import java.sql.Connection
import java.time.LocalDateTime
import java.util.UUID

/**
 * In-memory repository for order members with optional SQLite persistence.
 * Serves as the runtime source of truth and persists to DB when connection is provided.
 *
 * @param cache MutableMap keyed by orderId, containing sets of OrderMember
 * @param connection Optional SQLite Connection for persistence. If null, operates in memory-only mode.
 */
class OrderMembersRepository(
    private val cache: MutableMap<Long, MutableSet<OrderMember>>,
    private val connection: Connection? = null,
) {
    /**
     * Add a member to an order.
     * Updates cache and persists to DB if connection is available.
     * Returns the added OrderMember or existing member if it already exists.
     */
    fun addMember(
        orderId: Long,
        playerUuid: UUID,
        grantedVia: String,
        grantedAt: LocalDateTime,
    ): OrderMember {
        require(grantedVia in setOf("native", "commune")) {
            "Invalid grantedVia value: $grantedVia. Must be 'native' or 'commune'"
        }

        val members = cache.getOrPut(orderId) { mutableSetOf() }
        val member =
            OrderMember(
                playerUuid = playerUuid,
                orderId = orderId,
                grantedAt = grantedAt,
                grantedVia = grantedVia,
            )
        members.add(member)

        // Persist to DB if connection is available
        if (connection != null) {
            persistAddMember(orderId, playerUuid, grantedAt, grantedVia)
        }

        return member
    }

    /**
     * Remove a member from an order.
     * Removes from cache and deletes from DB if connection is available.
     * Returns true if member was removed, false if not found.
     */
    fun removeMember(
        orderId: Long,
        playerUuid: UUID,
    ): Boolean {
        val members = cache[orderId] ?: return false
        val removed = members.removeIf { it.playerUuid == playerUuid }

        // Persist deletion to DB if connection is available and member was actually removed
        if (removed && connection != null) {
            persistRemoveMember(orderId, playerUuid)
        }

        return removed
    }

    /**
     * Load all members from the database and populate the cache.
     * If connection is null, returns empty list.
     *
     * Note: calling loadAll() multiple times appends to the existing cache.
     * Intended for single-call use during plugin startup.
     */
    fun loadAll(): List<OrderMember> {
        if (connection == null) {
            return emptyList()
        }

        val result = mutableListOf<OrderMember>()
        val stmt =
            connection.prepareStatement(
                "SELECT order_id, player_uuid, granted_at, granted_via FROM order_members",
            )
        stmt.use {
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    val orderId = rs.getLong("order_id")
                    val playerUuid = UUID.fromString(rs.getString("player_uuid"))
                    val grantedAt = LocalDateTime.parse(rs.getString("granted_at"))
                    val grantedVia = rs.getString("granted_via")

                    val member =
                        OrderMember(
                            playerUuid = playerUuid,
                            orderId = orderId,
                            grantedAt = grantedAt,
                            grantedVia = grantedVia,
                        )

                    result.add(member)

                    // Populate cache
                    val members = cache.getOrPut(orderId) { mutableSetOf() }
                    members.add(member)
                }
            }
        }

        return result
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
     * Get all order IDs where a player holds a membership of a specific type.
     * Snapshot cache entries with .toSet() for thread safety.
     */
    fun getOrdersOfPlayerWithType(
        playerUuid: UUID,
        membershipType: String,
    ): Set<Long> =
        cache.entries
            .toSet()
            .filter { (_, members) ->
                members.any {
                    it.playerUuid == playerUuid && it.grantedVia == membershipType
                }
            }.map { it.key }
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

    private fun persistAddMember(
        orderId: Long,
        playerUuid: UUID,
        grantedAt: LocalDateTime,
        grantedVia: String,
    ) {
        val sql =
            """
            INSERT OR REPLACE INTO order_members
            (order_id, player_uuid, granted_at, granted_via)
            VALUES (?, ?, ?, ?)
            """.trimIndent()
        val conn = requireNotNull(connection) { "DB connection required for persistence" }
        try {
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(PARAM_ORDER_ID_ADD, orderId)
                stmt.setString(PARAM_PLAYER_UUID_ADD, playerUuid.toString())
                stmt.setString(PARAM_GRANTED_AT, grantedAt.toString())
                stmt.setString(PARAM_GRANTED_VIA_ADD, grantedVia)
                stmt.executeUpdate()
            }
        } catch (e: java.sql.SQLException) {
            throw IllegalStateException("DB persistence failed: ${e.message}", e)
        }
    }

    private fun persistRemoveMember(
        orderId: Long,
        playerUuid: UUID,
    ) {
        val sql =
            "DELETE FROM order_members WHERE order_id = ? AND player_uuid = ?"
        val conn = requireNotNull(connection) { "DB connection required for persistence" }
        try {
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(PARAM_ORDER_ID_REMOVE, orderId)
                stmt.setString(PARAM_PLAYER_UUID_REMOVE, playerUuid.toString())
                stmt.executeUpdate()
            }
        } catch (e: java.sql.SQLException) {
            throw IllegalStateException("DB persistence failed: ${e.message}", e)
        }
    }

    companion object {
        private const val PARAM_ORDER_ID_ADD = 1
        private const val PARAM_PLAYER_UUID_ADD = 2
        private const val PARAM_GRANTED_AT = 3
        private const val PARAM_GRANTED_VIA_ADD = 4

        private const val PARAM_ORDER_ID_REMOVE = 1
        private const val PARAM_PLAYER_UUID_REMOVE = 2
    }
}
