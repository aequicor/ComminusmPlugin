package ru.kyamshanov.comminusm.service

import org.bukkit.Location
import ru.kyamshanov.comminusm.config.OrderLevelConfig
import ru.kyamshanov.comminusm.model.Order
import ru.kyamshanov.comminusm.storage.ChunkCacheManager
import ru.kyamshanov.comminusm.storage.OrderRepository
import java.util.UUID
import kotlin.math.abs

class OrderService(
    private val orderRepository: OrderRepository,
    private val levels: List<OrderLevelConfig>,
    private val workdaysService: WorkdaysService?,
    private val minDistanceBetweenCenters: Int,
    private val chunkCacheManager: ChunkCacheManager? = null
) {

    fun create(uuid: UUID): Order? {
        val existing = orderRepository.findByOwner(uuid)
        if (existing != null) return null

        val level1 = levels.firstOrNull() ?: return null
        val order = Order(ownerUuid = uuid, level = level1.level, radius = level1.radius)
        val id = orderRepository.insert(order)
        return order.copy(id = id)
    }

    fun activate(uuid: UUID, location: Location): Boolean {
        val order = orderRepository.findByOwner(uuid) ?: return false
        if (order.centerWorld != null) return false

        val world = checkNotNull(location.world) { "Мир не может быть null" }.name

        val allInWorld = orderRepository.findAllInWorld(world)
        if (checkOverlap(allInWorld, location.blockX, location.blockY, location.blockZ, order.radius)) {
            return false
        }

        orderRepository.activate(uuid, world, location.blockX, location.blockY, location.blockZ)

        chunkCacheManager?.markOrderChunk(location.chunk, uuid)
        return true
    }

    fun findByOwner(uuid: UUID): Order? = orderRepository.findByOwner(uuid)

    fun findAllInWorld(world: String): List<Order> = orderRepository.findAllInWorld(world)

    fun checkOverlap(orders: List<Order>, x: Int, y: Int, z: Int, radius: Int): Boolean {
        return orders.any { existing ->
            if (existing.centerWorld == null) return@any false
            val dx = abs(existing.centerX - x)
            val dz = abs(existing.centerZ - z)
            val distanceXZ = dx + dz
            distanceXZ <= existing.radius + radius + minDistanceBetweenCenters
        }
    }

    fun getRadiusForLevel(level: Int): Int {
        return levels.find { it.level == level }?.radius ?: levels.lastOrNull()?.radius ?: 2
    }

    fun getCostForLevel(level: Int): Int {
        return levels.find { it.level == level }?.cost ?: 0
    }

    fun getMaxLevel(): Int = levels.maxOfOrNull { it.level } ?: 5

    fun upgrade(uuid: UUID): Boolean {
        val order = orderRepository.findByOwner(uuid) ?: return false
        val currentLevel = order.level
        if (currentLevel >= getMaxLevel()) return false

        val nextLevel = currentLevel + 1
        val cost = getCostForLevel(nextLevel)

        val wds = workdaysService ?: return false
        if (!wds.spend(uuid, cost)) return false

        val newRadius = getRadiusForLevel(nextLevel)
        orderRepository.updateLevel(uuid, nextLevel, newRadius)

        return true
    }

    fun deleteByOwner(uuid: UUID) {
        val order = orderRepository.findByOwner(uuid)
        if (order != null && order.centerWorld != null) {
            val world = org.bukkit.Bukkit.getWorld(order.centerWorld)
            if (world != null) {
                val chunk = world.getChunkAt(order.centerX shr 4, order.centerZ shr 4)
                chunkCacheManager?.removeOrderChunk(chunk)
            }
        }
        orderRepository.deleteByOwner(uuid)
    }
}
