package ru.kyamshanov.comminusm.application.usecases.commune

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.commune.model.Commune
import ru.kyamshanov.comminusm.commune.service.CommuneService
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GetCommuneOfOrderUseCaseTest {
    private val communeService = mockk<CommuneService>()
    private val useCase = GetCommuneOfOrderUseCaseImpl(communeService)

    @Test
    fun `should return null when order not in commune`() {
        // Arrange
        every { communeService.getCommuneOfOrder(1L) } returns null

        // Act
        val result = useCase(1L)

        // Assert
        assertNull(result)
    }

    @Test
    fun `should return commune when order found`() {
        // Arrange
        val commune =
            Commune(
                id = UUID.randomUUID(),
                orderIds = setOf(1L, 2L, 3L),
                version = 1,
                createdAt = java.time.LocalDateTime.now(),
                createdBy = UUID.randomUUID(),
            )
        every { communeService.getCommuneOfOrder(1L) } returns commune

        // Act
        val result = useCase(1L)

        // Assert
        assertEquals(commune.id, result?.id)
        assertEquals(3, result?.orderIds?.size)
    }

    @Test
    fun `should return correct commune for different orders`() {
        // Arrange
        val commune1 =
            Commune(
                id = UUID.randomUUID(),
                orderIds = setOf(1L, 2L),
                version = 1,
                createdAt = java.time.LocalDateTime.now(),
                createdBy = UUID.randomUUID(),
            )
        val commune2 =
            Commune(
                id = UUID.randomUUID(),
                orderIds = setOf(3L, 4L),
                version = 1,
                createdAt = java.time.LocalDateTime.now(),
                createdBy = UUID.randomUUID(),
            )
        every { communeService.getCommuneOfOrder(1L) } returns commune1
        every { communeService.getCommuneOfOrder(3L) } returns commune2

        // Act
        val result1 = useCase(1L)
        val result2 = useCase(3L)

        // Assert
        assertEquals(commune1.id, result1?.id)
        assertEquals(commune2.id, result2?.id)
    }
}
