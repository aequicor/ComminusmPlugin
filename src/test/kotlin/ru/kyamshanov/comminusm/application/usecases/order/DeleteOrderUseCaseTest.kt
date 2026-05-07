package ru.kyamshanov.comminusm.application.usecases.order

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.domain.entities.Order
import ru.kyamshanov.comminusm.domain.repositories.OrderRepository
import ru.kyamshanov.comminusm.domain.value_objects.Result
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeleteOrderUseCaseTest {
    private val orderRepository = mockk<OrderRepository>()
    private val useCase = DeleteOrderUseCaseImpl(orderRepository)

    private lateinit var uuid: UUID

    @BeforeEach
    fun setUp() {
        uuid = UUID.randomUUID()
    }

    @Test
    fun `should delete order successfully`() {
        // Arrange
        val order = Order(id = 1, ownerUuid = uuid, level = 1, radius = 50)
        every { orderRepository.findByOwner(uuid) } returns order
        every { orderRepository.deleteByOwner(uuid) } returns Unit

        // Act
        val result = useCase(uuid)

        // Assert
        assertTrue(result is Result.Success)
        verify { orderRepository.deleteByOwner(uuid) }
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
