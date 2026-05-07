@file:Suppress("TooGenericExceptionCaught", "SwallowedException", "NestedBlockDepth", "TooManyFunctions")

package ru.kyamshanov.comminusm.commune.listener

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import ru.kyamshanov.comminusm.commune.model.Commune
import ru.kyamshanov.comminusm.commune.repository.OrderMembersRepository
import ru.kyamshanov.comminusm.commune.service.CommuneService
import java.sql.Connection
import java.time.LocalDateTime
import java.util.UUID

/**
 * Startup task that loads communes from storage and performs consistency checks.
 *
 * Called during plugin.onEnable(), this task:
 * 1. Asynchronously loads all communes, orders, members from storage
 * 2. Performs AC-47 consistency scan: validates that all cross-order members have
 *    at least one native order in the same commune
 * 3. Gracefully degrades if storage is unavailable
 * 4. Populates CommuneService in-memory cache
 *
 * Implements AC-47: "Startup consistency scan - invalidate stale cross-order members"
 * Implements CC-05: "Storage failure at startup - graceful degrade"
 * Implements CC-17: "Consistency check may take time for large datasets"
 */
class CommuneStartupTask(
    private val communeService: CommuneService,
    private val orderMembersRepository: OrderMembersRepository,
    private val connection: Connection? = null,
    private val plugin: Plugin? = null,
) {
    var startupComplete = false
        private set

    var storageLoadFailed = false
        private set

    /**
     * Main startup entry point. Typically called from ComminusmPlugin.onEnable().
     * Runs asynchronously to avoid blocking server startup.
     */
    fun onEnable() {
        val pluginInstance = plugin ?: return
        Bukkit.getScheduler().runTaskAsynchronously(
            pluginInstance,
            Runnable {
                try {
                    loadCommunes()
                    if (!storageLoadFailed) {
                        performConsistencyCheck()
                        startupComplete = true
                    }
                } catch (e: Exception) {
                    storageLoadFailed = true
                    Bukkit.getLogger().severe("Failed to load communes at startup: ${e.javaClass.simpleName}")
                }
            },
        )
    }

    /**
     * Load communes and members from storage.
     * Should populate communeService cache.
     * Implements §8.2 step 2 - Load phase (async).
     * Gracefully handles storage failures (CC-05).
     */
    internal fun loadCommunes() {
        val conn = connection ?: return // null = no-db mode (tests)
        try {
            loadCommunesFromDatabase(conn)
        } catch (e: Exception) {
            storageLoadFailed = true
            logLoadError("Failed to load communes from DB: ${e.javaClass.simpleName}")
            return
        }
        try {
            orderMembersRepository.loadAll()
        } catch (e: Exception) {
            storageLoadFailed = true
            logLoadError("Failed to load order members: ${e.javaClass.simpleName}")
        }
    }

    private fun loadCommunesFromDatabase(conn: Connection) {
        // 1. Load communes and their order associations
        val communeOrders = loadCommuneOrderAssociations(conn)

        // 2. Load communes and restore to service
        loadCommunesWithOrders(conn, communeOrders)
    }

    private fun loadCommuneOrderAssociations(conn: Connection): Map<UUID, Set<Long>> {
        val communeOrders = mutableMapOf<UUID, MutableSet<Long>>()
        val stmt = conn.prepareStatement("SELECT commune_id, order_id FROM commune_orders")
        try {
            val rs = stmt.executeQuery()
            try {
                while (rs.next()) {
                    val communeIdStr = rs.getString("commune_id")
                    val communeId = communeIdStr?.let(::parseUuid)
                    if (communeId != null) {
                        communeOrders
                            .getOrPut(communeId) { mutableSetOf() }
                            .add(rs.getLong("order_id"))
                    }
                }
            } finally {
                rs.close()
            }
        } finally {
            stmt.close()
        }
        return communeOrders
    }

    private fun loadCommunesWithOrders(
        conn: Connection,
        communeOrders: Map<UUID, Set<Long>>,
    ) {
        val stmt = conn.prepareStatement("SELECT id, created_at, version FROM communes")
        try {
            val rs = stmt.executeQuery()
            try {
                while (rs.next()) {
                    val idStr = rs.getString("id")
                    val createdAtStr = rs.getString("created_at")
                    if (idStr != null && createdAtStr != null) {
                        val communeId = parseUuid(idStr)
                        val createdAt = parseDateTime(createdAtStr)
                        if (communeId != null && createdAt != null) {
                            restoreCommuneIfValid(
                                communeId,
                                createdAt,
                                rs.getLong("version"),
                                communeOrders,
                            )
                        }
                    }
                }
            } finally {
                rs.close()
            }
        } finally {
            stmt.close()
        }
    }

    private fun restoreCommuneIfValid(
        communeId: UUID,
        createdAt: LocalDateTime,
        version: Long,
        communeOrders: Map<UUID, Set<Long>>,
    ) {
        val orderIds = communeOrders[communeId] ?: emptySet()
        if (orderIds.isEmpty()) {
            logLoadError("Skipping commune with no orders: $communeId")
        } else {
            communeService.restoreCommune(
                Commune(
                    id = communeId,
                    orderIds = orderIds,
                    version = version,
                    createdAt = createdAt,
                    createdBy = SENTINEL_UUID,
                ),
            )
        }
    }

    private fun parseUuid(idStr: String): UUID? =
        try {
            UUID.fromString(idStr)
        } catch (e: IllegalArgumentException) {
            Bukkit.getLogger().warning("Skipping commune with invalid UUID: $idStr")
            null
        }

    private fun parseDateTime(createdAtStr: String): LocalDateTime? =
        try {
            LocalDateTime.parse(createdAtStr)
        } catch (e: Exception) {
            Bukkit.getLogger().warning("Skipping commune with invalid created_at: $createdAtStr")
            null
        }

    private fun logMessage(
        level: String,
        message: String,
    ) {
        try {
            when (level) {
                "warning" -> Bukkit.getLogger().warning(message)
                "info" -> Bukkit.getLogger().info(message)
                "severe" -> Bukkit.getLogger().severe(message)
            }
        } catch (e: Exception) {
            // Bukkit server not initialized (e.g., in tests)
            System.err.println(message)
        }
    }

    private fun logLoadError(message: String) {
        logMessage("warning", message)
    }

    /**
     * Perform AC-47 consistency check: validate all cross-order members.
     *
     * For each member record with grantedVia="commune":
     * - Check that player still has a native order in the same commune
     * - If not, revoke the cross-order grant
     *
     * This prevents orphaned records after unclean shutdowns or data corruption.
     * Implements AC-47 and CC-05: graceful degradation on storage error
     * Thread safety: Called from async Bukkit scheduler task. CommuneService operations
     * are lock-protected. OrderMembersRepository uses ConcurrentHashMap which is safe
     * for concurrent reads. Called only during plugin startup before gameplay begins.
     */
    internal fun performConsistencyCheck() {
        val allCommunes = communeService.getAllCommunes()
        var removedCount = 0
        for (commune in allCommunes) {
            removedCount += scanCommuneConsistency(commune)
        }
        val message =
            "AC-47 consistency check complete: ${allCommunes.size} communes checked, " +
                "$removedCount orphans removed"
        logMessage("info", message)
    }

    private fun scanCommuneConsistency(commune: Commune): Int {
        // Collect all native members across all orders in the commune once
        val nativeMembersInCommune: Set<UUID> =
            commune.orderIds
                .flatMap { oid -> orderMembersRepository.getMembersWithType(oid, "native") }
                .map { it.playerUuid }
                .toSet()

        // Now check cross-order members
        var removedCount = 0
        for (orderId in commune.orderIds) {
            val crossOrderMembers = orderMembersRepository.getMembersWithType(orderId, "commune")
            for (member in crossOrderMembers) {
                if (member.playerUuid !in nativeMembersInCommune) {
                    orderMembersRepository.removeMember(orderId, member.playerUuid)
                    val message =
                        "AC-47: Removed stale cross-order member " +
                            "${member.playerUuid} from order $orderId"
                    logMessage("info", message)
                    removedCount++
                }
            }
        }
        return removedCount
    }

    companion object {
        private val SENTINEL_UUID = UUID(0L, 0L)
    }
}
