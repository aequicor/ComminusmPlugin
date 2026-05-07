package ru.kyamshanov.comminusm.application.usecases.order

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.domain.entities.Order
import ru.kyamshanov.comminusm.domain.repositories.OrderRepository
import ru.kyamshanov.comminusm.domain.value_objects.Result
import ru.kyamshanov.comminusm.infrastructure.config.OrderLevelConfig
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CreateOrderUseCaseTest {
    private lateinit var orderRepository: OrderRepository
    private lateinit var levels: List<OrderLevelConfig>
    private lateinit var useCase: CreateOrderUseCase

    @BeforeEach
    fun setUp() {
        orderRepository = mockk()
        levels =
            listOf(
                OrderLevelConfig(level = 1, cost = 0, radius = 5),
                OrderLevelConfig(level = 2, cost = 100, radius = 10),
            )
        useCase = CreateOrderUseCaseImpl(orderRepository, levels)
    }

    @Test
    fun `should create order successfully for new player`() {
        // Arrange
        val uuid = UUID.randomUUID()
        val expectedId = 1L
        every { orderRepository.findByOwner(uuid) } returns null
        every { orderRepository.insert(any()) } returns expectedId

        // Act
        val result = useCase(uuid)

        // Assert
        assertTrue(result is Result.Success)
        val order = (result as Result.Success).data
        assertEquals(uuid, order.ownerUuid)
        assertEquals(1, order.level)
        assertEquals(5, order.radius)
        assertEquals(expectedId, order.id)

        verify { orderRepository.findByOwner(uuid) }
        verify { orderRepository.insert(any()) }
    }

    @Test
    fun `should fail when order already exists for player`() {
        // Arrange
        val uuid = UUID.randomUUID()
        val existingOrder = Order(id = 1, ownerUuid = uuid, level = 1, radius = 5)
        every { orderRepository.findByOwner(uuid) } returns existingOrder

        // Act
        val result = useCase(uuid)

        // Assert
        assertTrue(result is Result.Failure)
        val failure = (result as Result.Failure)
        assertTrue(failure.error.contains("already exists"))

        verify { orderRepository.findByOwner(uuid) }
        verify(exactly = 0) { orderRepository.insert(any()) }
    }

    @Test
    fun `should fail when no level configuration available`() {
        // Arrange
        val uuid = UUID.randomUUID()
        val useCaseWithoutLevels = CreateOrderUseCaseImpl(orderRepository, emptyList())
        every { orderRepository.findByOwner(uuid) } returns null

        // Act
        val result = useCaseWithoutLevels(uuid)

        // Assert
        assertTrue(result is Result.Failure)
        val failure = (result as Result.Failure)
        assertTrue(failure.error.contains("No level configuration"))

        verify { orderRepository.findByOwner(uuid) }
        verify(exactly = 0) { orderRepository.insert(any()) }
    }

    @Test
    fun `should use first level configuration`() {
        // Arrange
        val uuid = UUID.randomUUID()
        val expectedId = 42L
        every { orderRepository.findByOwner(uuid) } returns null
        every { orderRepository.insert(any()) } returns expectedId

        // Act
        val result = useCase(uuid)

        // Assert
        assertTrue(result is Result.Success)
        val order = (result as Result.Success).data
        assertEquals(1, order.level)
        assertEquals(5, order.radius)

        verify { orderRepository.insert(match { o -> o.level == 1 && o.radius == 5 }) }
    }

    @Test
    fun `should return order with assigned ID`() {
        // Arrange
        val uuid = UUID.randomUUID()
        val assignedId = 999L
        every { orderRepository.findByOwner(uuid) } returns null
        every { orderRepository.insert(any()) } returns assignedId

        // Act
        val result = useCase(uuid)

        // Assert
        assertTrue(result is Result.Success)
        val order = (result as Result.Success).data
        assertEquals(assignedId, order.id)
    }

    @Test
    fun `should preserve owner UUID in created order`() {
        // Arrange
        val uuid = UUID.randomUUID()
        every { orderRepository.findByOwner(uuid) } returns null
        every { orderRepository.insert(any()) } returns 1L

        // Act
        val result = useCase(uuid)

        // Assert
        assertTrue(result is Result.Success)
        val order = (result as Result.Success).data
        assertEquals(uuid, order.ownerUuid)
    }
}
