package ru.kyamshanov.comminusm.application.usecases.order

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.domain.repositories.OrderRepository
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CheckOrderLeadershipUseCaseTest {
    private lateinit var orderRepository: OrderRepository
    private lateinit var useCase: CheckOrderLeadershipUseCase

    @BeforeEach
    fun setUp() {
        orderRepository = mockk()
        useCase = CheckOrderLeadershipUseCaseImpl(orderRepository)
    }

    @Test
    fun `should return true when player has order`() {
        // Arrange
        val uuid = UUID.randomUUID()
        every { orderRepository.findByOwner(uuid) } returns mockk()

        // Act
        val result = useCase(uuid)

        // Assert
        assertTrue(result)
        verify { orderRepository.findByOwner(uuid) }
    }

    @Test
    fun `should return false when player has no order`() {
        // Arrange
        val uuid = UUID.randomUUID()
        every { orderRepository.findByOwner(uuid) } returns null

        // Act
        val result = useCase(uuid)

        // Assert
        assertFalse(result)
        verify { orderRepository.findByOwner(uuid) }
    }

    @Test
    fun `should check leadership for different UUIDs independently`() {
        // Arrange
        val uuid1 = UUID.randomUUID()
        val uuid2 = UUID.randomUUID()
        every { orderRepository.findByOwner(uuid1) } returns mockk()
        every { orderRepository.findByOwner(uuid2) } returns null

        // Act
        val result1 = useCase(uuid1)
        val result2 = useCase(uuid2)

        // Assert
        assertTrue(result1)
        assertFalse(result2)
    }
}
