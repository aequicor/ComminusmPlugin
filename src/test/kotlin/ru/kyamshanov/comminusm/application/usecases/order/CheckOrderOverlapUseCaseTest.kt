package ru.kyamshanov.comminusm.application.usecases.order

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.domain.entities.Order
import ru.kyamshanov.comminusm.domain.repositories.OrderRepository
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CheckOrderOverlapUseCaseTest {
    private val orderRepository = mockk<OrderRepository>()
    private val useCase = CheckOrderOverlapUseCaseImpl(orderRepository, minDistanceBetweenCenters = 30)

    @Test
    fun `should return false when no orders exist in world`() {
        // Arrange
        every { orderRepository.findAllInWorld("world") } returns emptyList()

        // Act
        val result = useCase(100, 64, 100, 50, "world")

        // Assert
        assertFalse(result)
    }

    @Test
    fun `should return false when no overlap with existing orders`() {
        // Arrange
        val existingOrder =
            Order(
                id = 1,
                ownerUuid = java.util.UUID.randomUUID(),
                level = 1,
                radius = 50,
                centerWorld = "world",
                centerX = 0,
                centerZ = 0,
            )
        every { orderRepository.findAllInWorld("world") } returns listOf(existingOrder)

        // Act - location far from existing order (distance > radius + radius + minDistance)
        val result = useCase(500, 64, 500, 50, "world")

        // Assert
        assertFalse(result)
    }

    @Test
    fun `should return true when overlap detected`() {
        // Arrange
        val existingOrder =
            Order(
                id = 1,
                ownerUuid = java.util.UUID.randomUUID(),
                level = 1,
                radius = 50,
                centerWorld = "world",
                centerX = 100,
                centerZ = 100,
            )
        every { orderRepository.findAllInWorld("world") } returns listOf(existingOrder)

        // Act - location close to existing order (within overlap threshold)
        val result = useCase(110, 64, 110, 50, "world")

        // Assert
        assertTrue(result)
    }

    @Test
    fun `should ignore orders without center location`() {
        // Arrange
        val unactivatedOrder =
            Order(
                id = 1,
                ownerUuid = java.util.UUID.randomUUID(),
                level = 1,
                radius = 50,
                centerWorld = null,
            )
        every { orderRepository.findAllInWorld("world") } returns listOf(unactivatedOrder)

        // Act
        val result = useCase(100, 64, 100, 50, "world")

        // Assert
        assertFalse(result)
    }
}
