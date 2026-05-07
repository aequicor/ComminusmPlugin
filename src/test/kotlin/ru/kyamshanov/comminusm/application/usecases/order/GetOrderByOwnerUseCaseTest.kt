package ru.kyamshanov.comminusm.application.usecases.order

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.domain.entities.Order
import ru.kyamshanov.comminusm.domain.repositories.OrderRepository
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GetOrderByOwnerUseCaseTest {
    private lateinit var orderRepository: OrderRepository
    private lateinit var useCase: GetOrderByOwnerUseCase

    @BeforeEach
    fun setUp() {
        orderRepository = mockk()
        useCase = GetOrderByOwnerUseCaseImpl(orderRepository)
    }

    @Test
    fun `should return order when player has one`() {
        // Arrange
        val uuid = UUID.randomUUID()
        val expectedOrder = Order(id = 1, ownerUuid = uuid, level = 2, radius = 10)
        every { orderRepository.findByOwner(uuid) } returns expectedOrder

        // Act
        val result = useCase(uuid)

        // Assert
        assertEquals(expectedOrder, result)
        verify { orderRepository.findByOwner(uuid) }
    }

    @Test
    fun `should return null when player has no order`() {
        // Arrange
        val uuid = UUID.randomUUID()
        every { orderRepository.findByOwner(uuid) } returns null

        // Act
        val result = useCase(uuid)

        // Assert
        assertNull(result)
        verify { orderRepository.findByOwner(uuid) }
    }

    @Test
    fun `should preserve order details`() {
        // Arrange
        val uuid = UUID.randomUUID()
        val orderId = 42L
        val level = 3
        val radius = 15
        val expectedOrder = Order(id = orderId, ownerUuid = uuid, level = level, radius = radius)
        every { orderRepository.findByOwner(uuid) } returns expectedOrder

        // Act
        val result = useCase(uuid)

        // Assert
        assertEquals(orderId, result?.id)
        assertEquals(level, result?.level)
        assertEquals(radius, result?.radius)
    }
}
