package ru.kyamshanov.comminusm.application.usecases.order

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.domain.entities.Order
import ru.kyamshanov.comminusm.domain.repositories.OrderRepository
import ru.kyamshanov.comminusm.domain.value_objects.Result
import ru.kyamshanov.comminusm.domain.value_objects.WorldLocation
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ActivateOrderUseCaseTest {
    private val orderRepository = mockk<OrderRepository>()
    private val overlapChecker = mockk<CheckOrderOverlapUseCase>()
    private val useCase = ActivateOrderUseCaseImpl(orderRepository, overlapChecker)

    private lateinit var uuid: UUID
    private lateinit var location: WorldLocation

    @BeforeEach
    fun setUp() {
        uuid = UUID.randomUUID()
        location = WorldLocation(world = "world", x = 100, y = 64, z = 100)
    }

    @Test
    fun `should activate order successfully when no overlap`() {
        // Arrange
        val order = Order(id = 1, ownerUuid = uuid, level = 1, radius = 50)
        every { orderRepository.findByOwner(uuid) } returns order
        every { overlapChecker(100, 64, 100, 50, "world") } returns false
        every { orderRepository.activate(uuid, "world", 100, 64, 100) } returns Unit

        // Act
        val result = useCase(uuid, location)

        // Assert
        assertTrue(result is Result.Success)
        verify { orderRepository.activate(uuid, "world", 100, 64, 100) }
    }

    @Test
    fun `should fail when order not found`() {
        // Arrange
        every { orderRepository.findByOwner(uuid) } returns null

        // Act
        val result = useCase(uuid, location)

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Order not found", (result as Result.Failure).error)
    }

    @Test
    fun `should fail when order already activated`() {
        // Arrange
        val order = Order(id = 1, ownerUuid = uuid, level = 1, radius = 50, centerWorld = "world")
        every { orderRepository.findByOwner(uuid) } returns order

        // Act
        val result = useCase(uuid, location)

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Order already activated", (result as Result.Failure).error)
    }

    @Test
    fun `should fail when overlap detected`() {
        // Arrange
        val order = Order(id = 1, ownerUuid = uuid, level = 1, radius = 50)
        every { orderRepository.findByOwner(uuid) } returns order
        every { overlapChecker(100, 64, 100, 50, "world") } returns true

        // Act
        val result = useCase(uuid, location)

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Order overlaps with another order", (result as Result.Failure).error)
        verify(exactly = 0) { orderRepository.activate(any(), any(), any(), any(), any()) }
    }
}
