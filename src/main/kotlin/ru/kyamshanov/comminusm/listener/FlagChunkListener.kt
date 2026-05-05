package ru.kyamshanov.comminusm.listener

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.ArmorStand
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import ru.kyamshanov.comminusm.manager.FlagStabilityManager
import ru.kyamshanov.comminusm.storage.OrderRepository
import ru.kyamshanov.comminusm.storage.WorkFrontRepository
import java.util.UUID

/**
 * Handles crash recovery, passive verification, dirty_armorstand cleanup,
 * and cache rebuilding on [ChunkLoadEvent] and [ChunkUnloadEvent].
 */
class FlagChunkListener(
    private val plugin: Plugin,
    private val manager: FlagStabilityManager,
    private val orderRepository: OrderRepository,
    private val workFrontRepository: WorkFrontRepository,
) : Listener {

    @EventHandler
    fun onChunkLoad(event: ChunkLoadEvent) {
        val chunk = event.chunk

        // Step A (main thread): rebuild cache from PDC
        manager.rebuildCacheFromPdc(chunk)

        // Collect PDC state synchronously on main thread
        val pdc = chunk.persistentDataContainer
        val flagKeys = pdc.keys.filter { it.namespace == "comminusm" && it.key.startsWith("flag/") }
        val dirtyAsKeys = pdc.keys.filter { it.namespace == "comminusm" && it.key.startsWith("dirty_armorstand/") }

        if (flagKeys.isEmpty() && dirtyAsKeys.isEmpty()) return

        // Dispatch single combined async task
        Bukkit.getScheduler().runTaskAsynchronously(
            plugin,
            Runnable { processChunk(chunk, flagKeys, dirtyAsKeys, pdc) },
        )
    }

    @EventHandler
    fun onChunkUnload(event: ChunkUnloadEvent) {
        val chunk = event.chunk
        manager.evictChunkCache(chunk.world.name, chunk.x, chunk.z)
        manager.evictChunkLock(manager.chunkKey(chunk.world.name, chunk.x, chunk.z))
    }

    private enum class RepairDecision {
        /** No armorstand PDC key but DB record exists — activation crash. */
        REPAIR_AS,

        /** No DB record — deletion crash (or orphan). */
        COMPLETE_DELETION,

        /** ArmorStand key present + DB present — verify support block. */
        RESTORE_SUPPORT,

        /** DB threw — fail-safe: do nothing. */
        DB_UNAVAILABLE,
    }

    private data class FlagRepairAction(val flagId: String, val decision: RepairDecision)

    @Suppress("CyclomaticComplexMethod")
    private fun processChunk(
        chunk: org.bukkit.Chunk,
        flagKeys: List<NamespacedKey>,
        dirtyAsKeys: List<NamespacedKey>,
        pdc: PersistentDataContainer,
    ) {
        // Step B: collect dirty_armorstand cleanup actions
        data class DirtyAsAction(val uuid: UUID?, val dirtyKey: NamespacedKey)

        val dirtyActions = dirtyAsKeys.mapNotNull { key ->
            val uuidStr = pdc.get(key, PersistentDataType.STRING) ?: return@mapNotNull null
            val uuid = runCatching { UUID.fromString(uuidStr) }.getOrNull()
            DirtyAsAction(uuid, key)
        }

        // Step C/D: classify each flag
        val repairActions = mutableListOf<FlagRepairAction>()
        for (nsKey in flagKeys) {
            val flagId = nsKey.key.removePrefix("flag/")
            val asKey = NamespacedKey(plugin, "armorstand/$flagId")
            val hasAsKey = pdc.has(asKey, PersistentDataType.STRING)

            @Suppress("TooGenericExceptionCaught")
            val dbRecord = try {
                lookupFlagInDb(flagId)
            } catch (e: Exception) {
                plugin.logger.warning("DB unavailable during ChunkLoadEvent for flag $flagId: ${e.message}")
                repairActions += FlagRepairAction(flagId, RepairDecision.DB_UNAVAILABLE)
                continue
            }

            val decision = when {
                !hasAsKey && dbRecord -> RepairDecision.REPAIR_AS
                !hasAsKey && !dbRecord -> RepairDecision.COMPLETE_DELETION
                hasAsKey && dbRecord -> RepairDecision.RESTORE_SUPPORT
                else -> RepairDecision.COMPLETE_DELETION
            }
            repairActions += FlagRepairAction(flagId, decision)
        }

        // Step F: apply all decisions on main thread
        Bukkit.getScheduler().runTask(
            plugin,
            Runnable {
                // Apply dirty_armorstand cleanup first
                for (da in dirtyActions) {
                    if (da.uuid != null) {
                        chunk.world.getEntity(da.uuid)?.remove()
                    }
                    pdc.remove(da.dirtyKey)
                }

                for (action in repairActions) {
                    applyRepairAction(chunk, pdc, flagKeys, action)
                }
            },
        )
    }

    private fun lookupFlagInDb(flagId: String): Boolean {
        return try {
            when {
                flagId.startsWith("order/") -> {
                    val uuid = UUID.fromString(flagId.removePrefix("order/"))
                    orderRepository.findByOwner(uuid) != null
                }
                flagId.startsWith("front/") -> {
                    val uuid = UUID.fromString(flagId.removePrefix("front/"))
                    workFrontRepository.findByOwner(uuid) != null
                }
                else -> false
            }
        } catch (e: IllegalArgumentException) {
            plugin.logger.warning("Invalid flag ID format '$flagId' in PDC — treating as orphan: ${e.message}")
            false
        }
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod", "ReturnCount")
    private fun applyRepairAction(
        chunk: org.bukkit.Chunk,
        pdc: PersistentDataContainer,
        flagKeys: List<NamespacedKey>,
        action: FlagRepairAction,
    ) {
        val nsKey = flagKeys.firstOrNull { it.key == "flag/${action.flagId}" } ?: return
        val coords = pdc.get(nsKey, PersistentDataType.LONG_ARRAY) ?: return
        if (coords.size != COORD_ARRAY_SIZE) return

        val bx = coords[0].toInt()
        val by = coords[1].toInt()
        val bz = coords[2].toInt()
        val bannerBlock = chunk.world.getBlockAt(bx, by, bz)
        val supportBlock = chunk.world.getBlockAt(bx, by - 1, bz)
        val flagId = action.flagId
        val asKey = NamespacedKey(plugin, "armorstand/$flagId")
        val supportMatKey = NamespacedKey(plugin, "support_material/$flagId")

        when (action.decision) {
            RepairDecision.REPAIR_AS -> {
                // Double-spawn guard: re-check PDC key hasn't been written by another path
                if (!pdc.has(asKey, PersistentDataType.STRING)) {
                    spawnRepairArmorStand(bannerBlock, flagId, pdc, asKey)
                }
            }
            RepairDecision.COMPLETE_DELETION -> {
                // Finish partial deletion
                val asUuidStr = pdc.get(asKey, PersistentDataType.STRING)
                if (asUuidStr != null) {
                    runCatching { UUID.fromString(asUuidStr) }.getOrNull()
                        ?.let { chunk.world.getEntity(it)?.remove() }
                }
                supportBlock.type = Material.AIR
                if (bannerBlock.type == Material.WHITE_BANNER || bannerBlock.type == Material.RED_BANNER) {
                    bannerBlock.type = Material.AIR
                }
                pdc.remove(nsKey)
                pdc.remove(asKey)
                pdc.remove(supportMatKey)
                manager.removeFromCache(chunk.world.name, chunk.x, chunk.z, bx, by, bz)
                manager.removeFromCache(chunk.world.name, chunk.x, chunk.z, bx, by - 1, bz)
                plugin.logger.warning("Completed partial deletion for flag $flagId at $bx,$by,$bz")
            }
            RepairDecision.RESTORE_SUPPORT -> {
                // Passive verification: check if support block is correct material
                val storedMatName = pdc.get(supportMatKey, PersistentDataType.STRING)
                if (storedMatName == null) {
                    plugin.logger.warning(
                        "Missing support_material PDC for flag $flagId — skipping passive verification",
                    )
                } else {
                    val storedMat = runCatching { Material.valueOf(storedMatName) }.getOrNull()
                    if (storedMat != null && supportBlock.type != storedMat) {
                        supportBlock.type = storedMat
                        plugin.logger.warning(
                            "Passive verification: restored support block for $flagId at $bx,${by - 1},$bz" +
                                " — was ${supportBlock.type}",
                        )
                    }
                }

                // Check ArmorStand entity still exists (double-spawn guard)
                val asUuidStr = pdc.get(asKey, PersistentDataType.STRING)
                val asUuid = asUuidStr?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                if (asUuid == null || chunk.world.getEntity(asUuid) == null) {
                    spawnRepairArmorStand(bannerBlock, flagId, pdc, asKey)
                }
            }
            RepairDecision.DB_UNAVAILABLE -> {
                // Fail-safe: do nothing, retry on next ChunkLoadEvent
            }
        }
    }

    private fun spawnRepairArmorStand(
        bannerBlock: org.bukkit.block.Block,
        flagId: String,
        pdc: PersistentDataContainer,
        asKey: NamespacedKey,
    ) {
        val ownerUuid = extractOwnerUuid(flagId)
        val ownerName = ownerUuid?.let {
            runCatching { Bukkit.getOfflinePlayer(it).name }.getOrNull() ?: it.toString()
        } ?: "Unknown"
        val flagType = if (flagId.startsWith("order/")) "Ордер" else "Трудовой Фронт"
        val asLocation = bannerBlock.location.clone().add(AS_OFFSET_XZ, AS_OFFSET_Y, AS_OFFSET_XZ)

        @Suppress("TooGenericExceptionCaught")
        try {
            val armorStand = bannerBlock.world.spawn(asLocation, ArmorStand::class.java) { stand ->
                stand.setVisible(false)
                stand.setGravity(false)
                stand.setMarker(true)
                stand.customName(Component.text("§6$flagType — §f$ownerName"))
                stand.isCustomNameVisible = true
            }
            pdc.set(asKey, PersistentDataType.STRING, armorStand.uniqueId.toString())
        } catch (e: Exception) {
            plugin.logger.severe("Repair spawn failed for flag $flagId: ${e.message}")
        }
    }

    private fun extractOwnerUuid(flagId: String): UUID? {
        val uuidStr = when {
            flagId.startsWith("order/") -> flagId.removePrefix("order/")
            flagId.startsWith("front/") -> flagId.removePrefix("front/")
            else -> return null
        }
        return runCatching { UUID.fromString(uuidStr) }.getOrNull()
    }

    private companion object {
        const val COORD_ARRAY_SIZE = 3

        /** Horizontal offset for ArmorStand spawn relative to banner block center. */
        const val AS_OFFSET_XZ = 0.5

        /** Vertical offset for ArmorStand spawn above the banner block. */
        const val AS_OFFSET_Y = 1.0
    }
}
