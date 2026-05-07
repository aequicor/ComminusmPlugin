@file:Suppress("ReturnCount")

package ru.kyamshanov.comminusm.service

import org.bukkit.Bukkit
import org.bukkit.Location
import ru.kyamshanov.comminusm.config.OrderLevelConfig
import ru.kyamshanov.comminusm.domain.repositories.OrderRepository
import ru.kyamshanov.comminusm.event.FlagDeactivatedEvent
import ru.kyamshanov.comminusm.infrastructure.adapters.DomainToModelAdapter
import ru.kyamshanov.comminusm.manager.FlagCleanupHelper
import ru.kyamshanov.comminusm.manager.FlagStabilityManager
import ru.kyamshanov.comminusm.storage.ChunkCacheManager
import java.util.UUID
import kotlin.math.abs
import ru.kyamshanov.comminusm.domain.entities.Order as DomainOrder
import ru.kyamshanov.comminusm.model.Order as ModelOrder

@Suppress("LongParameterList", "TooManyFunctions")
class OrderService(
    private val orderRepository: OrderRepository,
    private val levels: List<OrderLevelConfig>,
    private val workdaysService: WorkdaysService?,
    private val minDistanceBetweenCenters: Int,
    private val chunkCacheManager: ChunkCacheManager? = null,
    private val flagCleanupHelper: FlagCleanupHelper? = null,
    private val flagStabilityManager: FlagStabilityManager? = null,
    private val plugin: org.bukkit.plugin.Plugin? = null,
) {
    fun create(uuid: UUID): ModelOrder? {
        val existing = orderRepository.findByOwner(uuid)
        if (existing != null) return null

        val level1 = levels.firstOrNull() ?: return null
        val domainOrder = DomainOrder(ownerUuid = uuid, level = level1.level, radius = level1.radius)
        val id = orderRepository.insert(domainOrder)
        val createdOrder = domainOrder.copy(id = id)
        return DomainToModelAdapter.toPresentationModel(createdOrder)
    }

    fun activate(
        uuid: UUID,
        location: Location,
    ): Boolean {
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

    fun findByOwner(uuid: UUID): ModelOrder? {
        val domainOrder = orderRepository.findByOwner(uuid) ?: return null
        return DomainToModelAdapter.toPresentationModel(domainOrder)
    }

    fun findAllInWorld(world: String): List<ModelOrder> =
        DomainToModelAdapter.toPresentationModelOrders(orderRepository.findAllInWorld(world))

    fun getOrderById(id: Long): ModelOrder? {
        val domainOrder = orderRepository.findById(id) ?: return null
        return DomainToModelAdapter.toPresentationModel(domainOrder)
    }

    fun isLeader(uuid: UUID): Boolean = orderRepository.findByOwner(uuid) != null

    @Suppress("UNUSED_PARAMETER")
    fun checkOverlap(
        orders: List<DomainOrder>,
        x: Int,
        y: Int,
        z: Int,
        radius: Int,
    ): Boolean {
        return orders.any { existing ->
            if (existing.centerWorld == null) return@any false
            val dx = abs(existing.centerX - x)
            val dz = abs(existing.centerZ - z)
            val distanceXZ = dx + dz
            distanceXZ <= existing.radius + radius + minDistanceBetweenCenters
        }
    }

    fun getRadiusForLevel(level: Int): Int {
        val found = levels.find { it.level == level }?.radius
        return found ?: levels.lastOrNull()?.radius ?: 2
    }

    fun getCostForLevel(level: Int): Int = levels.find { it.level == level }?.cost ?: 0

    fun getMaxLevel(): Int = levels.maxOfOrNull { it.level } ?: DEFAULT_MAX_LEVEL

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
        val wasActivated = order != null && order.centerWorld != null
        val orderId = order?.id
        if (order != null && order.centerWorld != null) {
            val world = Bukkit.getWorld(order.centerWorld)
            val helper = flagCleanupHelper
            val manager = flagStabilityManager
            if (world != null && helper != null && manager != null) {
                val supportY = order.centerY - 1
                val chunk = world.getChunkAt(order.centerX shr CHUNK_SHIFT, order.centerZ shr CHUNK_SHIFT)
                chunkCacheManager?.removeOrderChunk(chunk)
                helper.cleanupFlag(
                    world = world,
                    supportX = order.centerX,
                    supportY = supportY,
                    supportZ = order.centerZ,
                    bannerX = order.centerX,
                    bannerY = order.centerY,
                    bannerZ = order.centerZ,
                    flagId = "order/$uuid",
                    manager = manager,
                    dbDeleteFn = {
                        orderRepository.deleteByOwner(uuid)
                        if (wasActivated && orderId != null) {
                            val p = plugin
                            if (p != null) {
                                p.server.scheduler.runTask(
                                    p,
                                    Runnable {
                                        Bukkit.getPluginManager().callEvent(FlagDeactivatedEvent(orderId))
                                    },
                                )
                            }
                        }
                    },
                )
                return
            }
            // Fallback: no cleanup helper — clean old chunk cache, delete DB only
            if (world != null) {
                val chunk = world.getChunkAt(order.centerX shr CHUNK_SHIFT, order.centerZ shr CHUNK_SHIFT)
                chunkCacheManager?.removeOrderChunk(chunk)
            }
        }
        // Not activated or no cleanup helper — DB-only delete
        orderRepository.deleteByOwner(uuid)
        if (wasActivated && orderId != null) {
            Bukkit.getPluginManager().callEvent(FlagDeactivatedEvent(orderId))
        }
    }

    companion object {
        private const val DEFAULT_MAX_LEVEL = 5
        private const val CHUNK_SHIFT = 4
    }
}
