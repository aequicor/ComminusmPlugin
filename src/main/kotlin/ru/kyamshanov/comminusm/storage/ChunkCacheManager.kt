package ru.kyamshanov.comminusm.storage

import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataType
import ru.kyamshanov.comminusm.plugin.ComminusmPlugin

class ChunkCacheManager {

    private val orderKey = NamespacedKey(ComminusmPlugin.getInstance(), "order_owner")
    private val frontKey = NamespacedKey(ComminusmPlugin.getInstance(), "front_owner")

    fun markOrderChunk(chunk: org.bukkit.Chunk, uuid: java.util.UUID) {
        val container = chunk.persistentDataContainer
        container.set(orderKey, PersistentDataType.STRING, uuid.toString())
    }

    fun removeOrderChunk(chunk: org.bukkit.Chunk) {
        chunk.persistentDataContainer.remove(orderKey)
    }

    fun hasOrderMarker(chunk: org.bukkit.Chunk): Boolean {
        return chunk.persistentDataContainer.has(orderKey, PersistentDataType.STRING)
    }

    fun getOrderOwner(chunk: org.bukkit.Chunk): java.util.UUID? {
        val str = chunk.persistentDataContainer.get(orderKey, PersistentDataType.STRING) ?: return null
        return try { java.util.UUID.fromString(str) } catch (_: IllegalArgumentException) { null }
    }

    fun markFrontChunk(chunk: org.bukkit.Chunk, uuid: java.util.UUID) {
        val container = chunk.persistentDataContainer
        container.set(frontKey, PersistentDataType.STRING, uuid.toString())
    }

    fun removeFrontChunk(chunk: org.bukkit.Chunk) {
        chunk.persistentDataContainer.remove(frontKey)
    }

    fun hasFrontMarker(chunk: org.bukkit.Chunk): Boolean {
        return chunk.persistentDataContainer.has(frontKey, PersistentDataType.STRING)
    }
}
