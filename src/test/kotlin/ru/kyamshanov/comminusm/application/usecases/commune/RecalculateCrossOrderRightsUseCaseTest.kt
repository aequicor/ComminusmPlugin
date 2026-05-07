package ru.kyamshanov.comminusm.application.usecases.commune

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.commune.model.Commune
import ru.kyamshanov.comminusm.commune.service.OrderMembershipService
import java.util.UUID

class RecalculateCrossOrderRightsUseCaseTest {
    private val orderMembershipService = mockk<OrderMembershipService>()
    private val useCase = RecalculateCrossOrderRightsUseCaseImpl(orderMembershipService)

    private lateinit var playerUuid: UUID
    private lateinit var communeId: UUID

    @BeforeEach
    fun setUp() {
        playerUuid = UUID.randomUUID()
        communeId = UUID.randomUUID()
    }

    @Test
    fun `should remove player from all orders when no native orders in commune`() {
        // Arrange
        val commune =
            Commune(
                id = communeId,
                orderIds = setOf(1L, 2L, 3L),
                version = 1,
                createdAt = java.time.LocalDateTime.now(),
                createdBy = UUID.randomUUID(),
            )
        every { orderMembershipService.getNativeOrdersOfPlayer(playerUuid) } returns emptySet()
        every { orderMembershipService.removeMemberSilently(any(), playerUuid) } returns Unit

        // Act
        useCase(playerUuid, commune)

        // Assert
        verify { orderMembershipService.removeMemberSilently(1L, playerUuid) }
        verify { orderMembershipService.removeMemberSilently(2L, playerUuid) }
        verify { orderMembershipService.removeMemberSilently(3L, playerUuid) }
    }

    @Test
    fun `should not remove player when player has native order in commune`() {
        // Arrange
        val commune =
            Commune(
                id = communeId,
                orderIds = setOf(1L, 2L, 3L),
                version = 1,
                createdAt = java.time.LocalDateTime.now(),
                createdBy = UUID.randomUUID(),
            )
        every { orderMembershipService.getNativeOrdersOfPlayer(playerUuid) } returns setOf(1L)

        // Act
        useCase(playerUuid, commune)

        // Assert
        verify(exactly = 0) { orderMembershipService.removeMemberSilently(any(), any()) }
    }

    @Test
    fun `should remove from unowned orders only`() {
        // Arrange
        val commune =
            Commune(
                id = communeId,
                orderIds = setOf(1L, 2L, 3L, 4L),
                version = 1,
                createdAt = java.time.LocalDateTime.now(),
                createdBy = UUID.randomUUID(),
            )
        every { orderMembershipService.getNativeOrdersOfPlayer(playerUuid) } returns setOf(1L, 2L)

        // Act
        useCase(playerUuid, commune)

        // Assert
        verify(exactly = 0) { orderMembershipService.removeMemberSilently(any(), any()) }
    }

    @Test
    fun `should remove player from empty commune`() {
        // Arrange
        val commune =
            Commune(
                id = communeId,
                orderIds = emptySet(),
                version = 1,
                createdAt = java.time.LocalDateTime.now(),
                createdBy = UUID.randomUUID(),
            )
        every { orderMembershipService.getNativeOrdersOfPlayer(playerUuid) } returns emptySet()

        // Act
        useCase(playerUuid, commune)

        // Assert
        verify(exactly = 0) { orderMembershipService.removeMemberSilently(any(), any()) }
    }
}
