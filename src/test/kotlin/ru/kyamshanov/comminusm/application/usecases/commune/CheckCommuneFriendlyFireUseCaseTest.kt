package ru.kyamshanov.comminusm.application.usecases.commune

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.commune.model.Commune
import ru.kyamshanov.comminusm.commune.service.CommuneService
import ru.kyamshanov.comminusm.commune.service.OrderMembershipService
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CheckCommuneFriendlyFireUseCaseTest {
    private val communeService = mockk<CommuneService>()
    private val orderMembershipService = mockk<OrderMembershipService>()
    private val useCase = CheckCommuneFriendlyFireUseCaseImpl(communeService, orderMembershipService)

    private lateinit var damageeUuid: UUID
    private lateinit var damagerUuid: UUID

    @BeforeEach
    fun setUp() {
        damageeUuid = UUID.randomUUID()
        damagerUuid = UUID.randomUUID()
    }

    @Test
    fun `should return false when damager has no orders`() {
        // Arrange
        every { orderMembershipService.getNativeOrdersOfPlayer(damagerUuid) } returns emptySet()
        every { orderMembershipService.getNativeOrdersOfPlayer(damageeUuid) } returns setOf(1L)

        // Act
        val result = useCase(damageeUuid, damagerUuid)

        // Assert
        assertFalse(result)
    }

    @Test
    fun `should return false when damagee has no orders`() {
        // Arrange
        every { orderMembershipService.getNativeOrdersOfPlayer(damagerUuid) } returns setOf(1L)
        every { orderMembershipService.getNativeOrdersOfPlayer(damageeUuid) } returns emptySet()

        // Act
        val result = useCase(damageeUuid, damagerUuid)

        // Assert
        assertFalse(result)
    }

    @Test
    fun `should return false when orders in different communes`() {
        // Arrange
        val damagerOrderId = 1L
        val damageeOrderId = 2L
        val damagerCommune =
            Commune(
                id = UUID.randomUUID(),
                orderIds = setOf(damagerOrderId, 3L),
                version = 1,
                createdAt = java.time.LocalDateTime.now(),
                createdBy = UUID.randomUUID(),
            )

        every { orderMembershipService.getNativeOrdersOfPlayer(damagerUuid) } returns setOf(damagerOrderId)
        every { orderMembershipService.getNativeOrdersOfPlayer(damageeUuid) } returns setOf(damageeOrderId)
        every { communeService.getCommuneOfOrder(damagerOrderId) } returns damagerCommune

        // Act
        val result = useCase(damageeUuid, damagerUuid)

        // Assert
        assertFalse(result)
    }

    @Test
    fun `should return true when players in same commune`() {
        // Arrange
        val damagerOrderId = 1L
        val damageeOrderId = 2L
        val sharedCommune =
            Commune(
                id = UUID.randomUUID(),
                orderIds = setOf(damagerOrderId, damageeOrderId),
                version = 1,
                createdAt = java.time.LocalDateTime.now(),
                createdBy = UUID.randomUUID(),
            )

        every { orderMembershipService.getNativeOrdersOfPlayer(damagerUuid) } returns setOf(damagerOrderId)
        every { orderMembershipService.getNativeOrdersOfPlayer(damageeUuid) } returns setOf(damageeOrderId)
        every { communeService.getCommuneOfOrder(damagerOrderId) } returns sharedCommune

        // Act
        val result = useCase(damageeUuid, damagerUuid)

        // Assert
        assertTrue(result)
    }

    @Test
    fun `should return true when commune not found for damager but damagee in another commune`() {
        // Arrange
        val damagerOrderId = 1L
        val damageeOrderId = 2L

        every { orderMembershipService.getNativeOrdersOfPlayer(damagerUuid) } returns setOf(damagerOrderId)
        every { orderMembershipService.getNativeOrdersOfPlayer(damageeUuid) } returns setOf(damageeOrderId)
        every { communeService.getCommuneOfOrder(damagerOrderId) } returns null

        // Act
        val result = useCase(damageeUuid, damagerUuid)

        // Assert
        assertFalse(result)
    }
}
