package ru.kyamshanov.comminusm.manager

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.ArmorStand
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock

/**
 * Stateless helper that encapsulates the full flag cleanup flow:
 * ArmorStand removal, support block restoration to original material (or AIR if unknown),
 * banner block removal, PDC key cleanup, cache eviction, and async DB deletion.
 */
class FlagCleanupHelper(private val plugin: Plugin) {

    /**
     * Performs full flag cleanup: removes ArmorStand, sets support + banner blocks to AIR,
     * removes PDC keys, evicts cache, then deletes DB record asynchronously.
     *
     * Must be called on the main thread. Acquires the chunk lock with non-blocking tryLock;
     * retries once after 1 tick if the lock is busy. If both attempts fail, logs a warning.
     *
     * @param world        Bukkit world containing the flag
     * @param supportX     X-coordinate of the support block (block directly below the banner)
     * @param supportY     Y-coordinate of the support block
     * @param supportZ     Z-coordinate of the support block
     * @param bannerX      X-coordinate of the banner block
     * @param bannerY      Y-coordinate of the banner block
     * @param bannerZ      Z-coordinate of the banner block
     * @param flagId       logical flag identifier, e.g. "order/{uuid}" or "front/{uuid}"
     * @param manager      FlagStabilityManager instance
     * @param dbDeleteFn   blocking DB operation — runs on async thread
     */
    @Suppress("LongParameterList")
    fun cleanupFlag(
        world: org.bukkit.World,
        supportX: Int,
        supportY: Int,
        supportZ: Int,
        bannerX: Int,
        bannerY: Int,
        bannerZ: Int,
        flagId: String,
        manager: FlagStabilityManager,
        dbDeleteFn: () -> Unit,
    ) {
        val chunkX = supportX shr CHUNK_SHIFT
        val chunkZ = supportZ shr CHUNK_SHIFT
        val chunkKey = manager.chunkKey(world.name, chunkX, chunkZ)
        val lock = manager.getChunkLock(chunkKey)

        if (!lock.tryLock()) {
            Bukkit.getScheduler().runTaskLater(
                plugin,
                Runnable {
                    if (!lock.tryLock()) {
                        plugin.logger.warning(
                            "Could not acquire lock for cleanup of flag $flagId — will retry on next ChunkLoadEvent"
                        )
                        return@Runnable
                    }
                    doCleanup(
                        lock, world,
                        supportX, supportY, supportZ,
                        bannerX, bannerY, bannerZ,
                        flagId, manager, dbDeleteFn,
                    )
                },
                RETRY_DELAY_TICKS,
            )
            return
        }
        doCleanup(
            lock, world,
            supportX, supportY, supportZ,
            bannerX, bannerY, bannerZ,
            flagId, manager, dbDeleteFn,
        )
    }

    @Suppress("LongParameterList", "TooGenericExceptionCaught", "LongMethod")
    private fun doCleanup(
        acquiredLock: ReentrantLock,
        world: org.bukkit.World,
        supportX: Int,
        supportY: Int,
        supportZ: Int,
        bannerX: Int,
        bannerY: Int,
        bannerZ: Int,
        flagId: String,
        manager: FlagStabilityManager,
        dbDeleteFn: () -> Unit,
    ) {
        try {
            val chunkX = supportX shr CHUNK_SHIFT
            val chunkZ = supportZ shr CHUNK_SHIFT

            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                // Defer to async chunk load, then back to main thread
                acquiredLock.unlock()
                world.getChunkAtAsync(chunkX, chunkZ).thenAccept {
                    Bukkit.getScheduler().runTask(
                        plugin,
                        Runnable {
                            cleanupFlag(
                                world,
                                supportX, supportY, supportZ,
                                bannerX, bannerY, bannerZ,
                                flagId, manager, dbDeleteFn,
                            )
                        },
                    )
                }
                return
            }

            val chunk = world.getChunkAt(chunkX, chunkZ)
            val pdc = chunk.persistentDataContainer

            // Remove ArmorStand
            val asKey = NamespacedKey(plugin, "armorstand/$flagId")
            val asUuidStr = pdc.get(asKey, PersistentDataType.STRING)
            if (asUuidStr != null) {
                val asUuid = runCatching { UUID.fromString(asUuidStr) }.getOrNull()
                if (asUuid != null) {
                    world.getEntity(asUuid)?.remove()
                } else {
                    // UUID parse failed — fallback bounding box search
                    val loc = Location(world, supportX.toDouble(), supportY.toDouble(), supportZ.toDouble())
                    val nearby = world.getNearbyEntities(loc, NEARBY_RADIUS_XZ, NEARBY_RADIUS_Y, NEARBY_RADIUS_XZ)
                    nearby.firstOrNull { it is ArmorStand }?.remove()
                }
            }

            // Restore support block to its original material (saved at activation time), or AIR if unknown
            val supportMaterialKey = NamespacedKey(plugin, "support_material/$flagId")
            val originalMaterialName = pdc.get(supportMaterialKey, PersistentDataType.STRING)
            val originalMaterial = originalMaterialName
                ?.let { runCatching { Material.valueOf(it) }.getOrNull() }
                ?: Material.AIR
            world.getBlockAt(supportX, supportY, supportZ).type = originalMaterial

            // Set banner block to AIR if it is still a banner
            val bannerBlock = world.getBlockAt(bannerX, bannerY, bannerZ)
            if (bannerBlock.type == Material.WHITE_BANNER || bannerBlock.type == Material.RED_BANNER) {
                bannerBlock.type = Material.AIR
            }

            // Remove PDC keys
            pdc.remove(asKey)
            pdc.remove(NamespacedKey(plugin, "flag/$flagId"))
            pdc.remove(NamespacedKey(plugin, "support_material/$flagId"))

            // Evict cache — both support and banner positions
            val supportChunkX = supportX shr CHUNK_SHIFT
            val supportChunkZ = supportZ shr CHUNK_SHIFT
            val bannerChunkX = bannerX shr CHUNK_SHIFT
            val bannerChunkZ = bannerZ shr CHUNK_SHIFT
            manager.removeFromCache(world.name, supportChunkX, supportChunkZ, supportX, supportY, supportZ)
            manager.removeFromCache(world.name, bannerChunkX, bannerChunkZ, bannerX, bannerY, bannerZ)

            // Release lock before async DB step
            acquiredLock.unlock()

            // Async DB delete
            Bukkit.getScheduler().runTaskAsynchronously(
                plugin,
                Runnable {
                    @Suppress("TooGenericExceptionCaught")
                    try {
                        dbDeleteFn()
                    } catch (e: Exception) {
                        plugin.logger.severe("DB deletion failed for flag $flagId: ${e.message}")
                    }
                },
            )
        } catch (e: Exception) {
            if (acquiredLock.isHeldByCurrentThread) acquiredLock.unlock()
            plugin.logger.severe("Cleanup failed for flag $flagId: ${e.message}")
        }
    }

    private companion object {
        const val CHUNK_SHIFT = 4
        const val RETRY_DELAY_TICKS = 1L
        const val NEARBY_RADIUS_XZ = 1.0
        const val NEARBY_RADIUS_Y = 3.0
    }
}
