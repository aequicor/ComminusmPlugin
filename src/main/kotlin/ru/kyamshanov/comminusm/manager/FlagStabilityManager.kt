package ru.kyamshanov.comminusm.manager

import org.bukkit.Chunk
import org.bukkit.block.Block
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

@Suppress("UnusedPrivateProperty")
class FlagStabilityManager(private val plugin: Plugin) {

    private companion object {
        /** Bit-shift to convert block X/Z coordinate to chunk coordinate. */
        const val CHUNK_SHIFT = 4

        /** Bit mask for 26-bit signed X/Z component in packed Long. */
        const val XZ_MASK = 0x3FF_FFFFL

        /** Bit mask for 12-bit Y component in packed Long. */
        const val Y_MASK = 0xFFFL

        /** Bit offset for X component in packed Long (38 bits). */
        const val X_BIT_OFFSET = 38

        /** Bit offset for Y component in packed Long (26 bits). */
        const val Y_BIT_OFFSET = 26

        /** Expected number of coordinate elements in a PDC LONG_ARRAY entry. */
        const val COORD_ARRAY_SIZE = 3
    }

    // In-memory cache: chunkKey → set of encoded block positions
    private val flagPositionCache = ConcurrentHashMap<String, MutableSet<Long>>()

    // Chunk lock map — keyed by canonical chunk key string
    private val chunkLocks = ConcurrentHashMap<String, ReentrantLock>()

    fun chunkKey(world: String, chunkX: Int, chunkZ: Int): String = "$world:$chunkX:$chunkZ"

    fun chunkKeyOf(block: Block): String = chunkKey(
        block.world.name,
        block.x shr CHUNK_SHIFT,
        block.z shr CHUNK_SHIFT
    )

    fun getChunkLock(key: String): ReentrantLock =
        chunkLocks.getOrPut(key) { ReentrantLock() }

    fun evictChunkLock(key: String) {
        chunkLocks.remove(key)
    }

    private fun blockPos(x: Int, y: Int, z: Int): Long {
        // Encode x (signed 26 bits), y (12 bits), z (signed 26 bits) into a Long
        val xBits = (x.toLong() and XZ_MASK) shl X_BIT_OFFSET
        val yBits = (y.toLong() and Y_MASK) shl Y_BIT_OFFSET
        val zBits = z.toLong() and XZ_MASK
        return xBits or yBits or zBits
    }

    @Suppress("LongParameterList")
    fun addToCache(worldName: String, chunkX: Int, chunkZ: Int, x: Int, y: Int, z: Int) {
        val key = chunkKey(worldName, chunkX, chunkZ)
        flagPositionCache.getOrPut(key) { ConcurrentHashMap.newKeySet() }.add(blockPos(x, y, z))
    }

    @Suppress("LongParameterList")
    fun removeFromCache(worldName: String, chunkX: Int, chunkZ: Int, x: Int, y: Int, z: Int) {
        val key = chunkKey(worldName, chunkX, chunkZ)
        flagPositionCache[key]?.remove(blockPos(x, y, z))
    }

    fun isFlagPosition(block: Block): Boolean {
        val key = chunkKeyOf(block)
        val cached = flagPositionCache[key]
        if (cached != null) return blockPos(block.x, block.y, block.z) in cached
        // Cold-start fallback — read PDC (rare; only before ChunkLoadEvent fires)
        return block.chunk.persistentDataContainer.keys
            .any { it.namespace == "comminusm" && it.key.startsWith("flag/") }
    }

    fun evictChunkCache(worldName: String, chunkX: Int, chunkZ: Int) {
        flagPositionCache.remove(chunkKey(worldName, chunkX, chunkZ))
    }

    fun rebuildCacheFromPdc(chunk: Chunk) {
        val key = chunkKey(chunk.world.name, chunk.x, chunk.z)
        val positions = ConcurrentHashMap.newKeySet<Long>()
        val pdc = chunk.persistentDataContainer
        pdc.keys
            .filter { it.namespace == "comminusm" && it.key.startsWith("flag/") }
            .forEach { nsKey ->
                val coords = pdc.get(nsKey, PersistentDataType.LONG_ARRAY) ?: return@forEach
                if (coords.size == COORD_ARRAY_SIZE) {
                    positions.add(blockPos(coords[0].toInt(), coords[1].toInt(), coords[2].toInt()))
                }
            }
        flagPositionCache[key] = positions
    }

}
