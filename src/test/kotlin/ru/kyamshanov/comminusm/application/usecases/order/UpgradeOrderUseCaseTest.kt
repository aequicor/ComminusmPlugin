package ru.kyamshanov.comminusm.application.usecases.order

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.domain.entities.Order
import ru.kyamshanov.comminusm.domain.repositories.OrderRepository
import ru.kyamshanov.comminusm.domain.repositories.WorkdaysRepository
import ru.kyamshanov.comminusm.domain.value_objects.Result
import ru.kyamshanov.comminusm.infrastructure.config.OrderLevelConfig
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpgradeOrderUseCaseTest {
    private val orderRepository = mockk<OrderRepository>()
    private val workdaysRepository = mockk<WorkdaysRepository>()
    private val levels =
        listOf(
            OrderLevelConfig(level = 1, cost = 0, radius = 5),
            OrderLevelConfig(level = 2, cost = 100, radius = 10),
            OrderLevelConfig(level = 3, cost = 200, radius = 15),
        )
    private val useCase = UpgradeOrderUseCaseImpl(orderRepository, workdaysRepository, levels)

    private lateinit var uuid: UUID

    @BeforeEach
    fun setUp() {
        uuid = UUID.randomUUID()
    }

    @Test
    fun `should upgrade order when enough workdays available`() {
        // Arrange
        val currentOrder = Order(id = 1, ownerUuid = uuid, level = 1, radius = 5)
        every { orderRepository.findByOwner(uuid) } returns currentOrder
        every { workdaysRepository.getBalance(uuid) } returns 150
        every { workdaysRepository.spend(uuid, 100) } returns true
        every { orderRepository.updateLevel(uuid, 2, 10) } returns Unit

        // Act
        val result = useCase(uuid)

        // Assert
        assertTrue(result is Result.Success)
        assertEquals(2, (result as Result.Success).data.level)
        assertEquals(10, result.data.radius)
    }

    @Test
    fun `should fail when not enough workdays`() {
        // Arrange
        val currentOrder = Order(id = 1, ownerUuid = uuid, level = 1, radius = 5)
        every { orderRepository.findByOwner(uuid) } returns currentOrder
        every { workdaysRepository.getBalance(uuid) } returns 50

        // Act
        val result = useCase(uuid)

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Insufficient workdays", (result as Result.Failure).error)
    }

    @Test
    fun `should fail when order not found`() {
        // Arrange
        every { orderRepository.findByOwner(uuid) } returns null

        // Act
        val result = useCase(uuid)

        // Assert
        assertTrue(result is Result.Failure)
        assertEquals("Order not found", (result as Result.Failure).error)
    }
}
