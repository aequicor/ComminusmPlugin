package ru.kyamshanov.comminusm.service

import ru.kyamshanov.comminusm.manager.FlagCleanupHelper
import ru.kyamshanov.comminusm.manager.FlagStabilityManager
import ru.kyamshanov.comminusm.model.WorkFront
import ru.kyamshanov.comminusm.storage.ChunkCacheManager
import ru.kyamshanov.comminusm.storage.WorkFrontRepository
import java.util.UUID

class WorkFrontService(
    private val repository: WorkFrontRepository,
    private val frontRadius: Int,
    private val chunkCacheManager: ChunkCacheManager? = null,
    private val plugin: org.bukkit.plugin.Plugin? = null,
    private val flagCleanupHelper: FlagCleanupHelper? = null,
    private val flagStabilityManager: FlagStabilityManager? = null,
) {
    fun activate(uuid: UUID, world: String, x: Int, y: Int, z: Int): Boolean {
        repository.deleteByOwner(uuid)
        val front = WorkFront(uuid, world, x, y, z, frontRadius)
        repository.upsert(front)

        // PDC caching — only if available (not in tests)
        chunkCacheManager?.let { cache ->
            val bukkitWorld = org.bukkit.Bukkit.getWorld(world)
            if (bukkitWorld != null) {
                val chunk = bukkitWorld.getChunkAt(x shr 4, z shr 4)
                cache.markFrontChunk(chunk, uuid)
            }
        }
        return true
    }

    fun getByOwner(uuid: UUID): WorkFront? = repository.findByOwner(uuid)

    fun deactivate(uuid: UUID) {
        val front = repository.findByOwner(uuid) ?: return
        val helper = flagCleanupHelper
        val manager = flagStabilityManager
        val world = if (helper != null || chunkCacheManager != null) {
            org.bukkit.Bukkit.getWorld(front.centerWorld)
        } else {
            null
        }

        if (world != null && helper != null && manager != null) {
            val chunk = world.getChunkAt(front.centerX shr CHUNK_SHIFT, front.centerZ shr CHUNK_SHIFT)
            chunkCacheManager?.removeFrontChunk(chunk)
            helper.cleanupFlag(
                world = world,
                supportX = front.centerX,
                supportY = front.centerY - 1,
                supportZ = front.centerZ,
                bannerX = front.centerX,
                bannerY = front.centerY,
                bannerZ = front.centerZ,
                flagId = "front/${front.ownerUuid}",
                manager = manager,
                dbDeleteFn = { repository.deleteByOwner(uuid) },
            )
        } else {
            // Legacy fallback
            repository.deleteByOwner(uuid)
            if (world != null) {
                val bannerBlock = world.getBlockAt(front.centerX, front.centerY, front.centerZ)
                if (bannerBlock.type == org.bukkit.Material.RED_BANNER) {
                    bannerBlock.type = org.bukkit.Material.AIR
                }
                val chunk = world.getChunkAt(front.centerX shr CHUNK_SHIFT, front.centerZ shr CHUNK_SHIFT)
                chunkCacheManager?.removeFrontChunk(chunk)
            }
        }
    }

    fun getAllInWorld(world: String): List<WorkFront> = repository.findAllInWorld(world)

    private companion object {
        const val CHUNK_SHIFT = 4
    }
}
