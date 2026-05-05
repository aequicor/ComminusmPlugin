package ru.kyamshanov.comminusm.service

import ru.kyamshanov.comminusm.model.WorkFront
import ru.kyamshanov.comminusm.storage.ChunkCacheManager
import ru.kyamshanov.comminusm.storage.WorkFrontRepository
import java.util.UUID

class WorkFrontService(
    private val repository: WorkFrontRepository,
    private val frontRadius: Int,
    private val chunkCacheManager: ChunkCacheManager? = null
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
        val front = repository.findByOwner(uuid)
        repository.deleteByOwner(uuid)
        if (front != null && chunkCacheManager != null) {
            val bukkitWorld = org.bukkit.Bukkit.getWorld(front.centerWorld)
            if (bukkitWorld != null) {
                // Break the banner block to prevent orphaned flags
                val bannerBlock = bukkitWorld.getBlockAt(front.centerX, front.centerY, front.centerZ)
                if (bannerBlock.type == org.bukkit.Material.RED_BANNER) {
                    bannerBlock.type = org.bukkit.Material.AIR
                }
                // Clean up PDC marker
                val chunk = bukkitWorld.getChunkAt(front.centerX shr 4, front.centerZ shr 4)
                chunkCacheManager.removeFrontChunk(chunk)
            }
        }
    }

    fun getAllInWorld(world: String): List<WorkFront> = repository.findAllInWorld(world)
}
