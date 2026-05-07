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

class GetOrderByIdUseCaseTest {
    private lateinit var orderRepository: OrderRepository
    private lateinit var useCase: GetOrderByIdUseCase

    @BeforeEach
    fun setUp() {
        orderRepository = mockk()
        useCase = GetOrderByIdUseCaseImpl(orderRepository)
    }

    @Test
    fun `should return order when found by ID`() {
        // Arrange
        val orderId = 42L
        val expectedOrder = Order(id = orderId, ownerUuid = UUID.randomUUID(), level = 2, radius = 10)
        every { orderRepository.findById(orderId) } returns expectedOrder

        // Act
        val result = useCase(orderId)

        // Assert
        assertEquals(expectedOrder, result)
        verify { orderRepository.findById(orderId) }
    }

    @Test
    fun `should return null when order not found`() {
        // Arrange
        val orderId = 99L
        every { orderRepository.findById(orderId) } returns null

        // Act
        val result = useCase(orderId)

        // Assert
        assertNull(result)
    }
}
