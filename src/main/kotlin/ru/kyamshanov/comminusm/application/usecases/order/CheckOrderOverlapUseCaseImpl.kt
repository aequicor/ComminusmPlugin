package ru.kyamshanov.comminusm.application.usecases.order

import ru.kyamshanov.comminusm.domain.repositories.OrderRepository
import kotlin.math.abs

/**
 * Implementation of CheckOrderOverlapUseCase.
 * Checks if a new order would overlap with existing orders in the world.
 */
class CheckOrderOverlapUseCaseImpl(
    private val orderRepository: OrderRepository,
    private val minDistanceBetweenCenters: Int = DEFAULT_MIN_DISTANCE,
) : CheckOrderOverlapUseCase {
    override fun invoke(
        x: Int,
        y: Int,
        z: Int,
        radius: Int,
        worldName: String,
    ): Boolean {
        val allInWorld = orderRepository.findAllInWorld(worldName)
        return allInWorld.any { existing ->
            if (existing.centerWorld == null) return@any false
            val dx = abs(existing.centerX - x)
            val dz = abs(existing.centerZ - z)
            val distanceXZ = dx + dz
            distanceXZ <= existing.radius + radius + minDistanceBetweenCenters
        }
    }

    companion object {
        private const val DEFAULT_MIN_DISTANCE = 30
    }
}
