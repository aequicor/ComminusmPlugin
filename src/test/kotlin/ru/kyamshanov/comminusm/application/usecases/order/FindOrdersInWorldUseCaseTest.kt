package ru.kyamshanov.comminusm.application.usecases.order

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.domain.entities.Order
import ru.kyamshanov.comminusm.domain.repositories.OrderRepository
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FindOrdersInWorldUseCaseTest {
    private val orderRepository = mockk<OrderRepository>()
    private val useCase = FindOrdersInWorldUseCaseImpl(orderRepository)

    @Test
    fun `should return empty list when no orders in world`() {
        // Arrange
        every { orderRepository.findAllInWorld("world") } returns emptyList()

        // Act
        val result = useCase("world")

        // Assert
        assertTrue(result.isEmpty())
    }

    @Test
    fun `should return list of orders in world`() {
        // Arrange
        val order1 = Order(id = 1, ownerUuid = UUID.randomUUID(), level = 1, radius = 50)
        val order2 = Order(id = 2, ownerUuid = UUID.randomUUID(), level = 2, radius = 75)
        every { orderRepository.findAllInWorld("world") } returns listOf(order1, order2)

        // Act
        val result = useCase("world")

        // Assert
        assertEquals(2, result.size)
        assertTrue(result.contains(order1))
        assertTrue(result.contains(order2))
    }

    @Test
    fun `should return orders only from specified world`() {
        // Arrange
        val order1 = Order(id = 1, ownerUuid = UUID.randomUUID(), level = 1, radius = 50)
        every { orderRepository.findAllInWorld("nether") } returns listOf(order1)
        every { orderRepository.findAllInWorld("end") } returns emptyList()

        // Act
        val netherResult = useCase("nether")
        val endResult = useCase("end")

        // Assert
        assertEquals(1, netherResult.size)
        assertTrue(endResult.isEmpty())
    }
}
