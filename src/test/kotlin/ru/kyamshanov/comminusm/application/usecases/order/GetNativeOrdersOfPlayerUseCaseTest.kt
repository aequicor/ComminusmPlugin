package ru.kyamshanov.comminusm.application.usecases.order

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.commune.service.OrderMembershipService
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetNativeOrdersOfPlayerUseCaseTest {
    private val membershipService = mockk<OrderMembershipService>()
    private val useCase = GetNativeOrdersOfPlayerUseCaseImpl(membershipService)

    private lateinit var playerUuid: UUID

    @BeforeEach
    fun setUp() {
        playerUuid = UUID.randomUUID()
    }

    @Test
    fun `should return empty list when player has no native orders`() {
        // Arrange
        every { membershipService.getNativeOrdersOfPlayer(playerUuid) } returns emptySet()

        // Act
        val result = useCase(playerUuid)

        // Assert
        assertTrue(result.isEmpty())
    }

    @Test
    fun `should return list of order IDs for player`() {
        // Arrange
        val orderIds = setOf(1L, 2L, 3L)
        every { membershipService.getNativeOrdersOfPlayer(playerUuid) } returns orderIds

        // Act
        val result = useCase(playerUuid)

        // Assert
        assertEquals(3, result.size)
        assertTrue(result.containsAll(orderIds))
    }

    @Test
    fun `should preserve order IDs from service`() {
        // Arrange
        val orderIds = setOf(100L, 200L)
        every { membershipService.getNativeOrdersOfPlayer(playerUuid) } returns orderIds

        // Act
        val result = useCase(playerUuid)

        // Assert
        assertTrue(result.contains(100L))
        assertTrue(result.contains(200L))
        assertEquals(2, result.size)
    }
}
