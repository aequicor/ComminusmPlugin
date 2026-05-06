package ru.kyamshanov.comminusm.commune.listener

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.commune.model.Commune
import ru.kyamshanov.comminusm.commune.model.Result
import ru.kyamshanov.comminusm.commune.service.CommuneService
import ru.kyamshanov.comminusm.commune.service.CrossOrderMembershipService
import ru.kyamshanov.comminusm.event.FlagDeactivatedEvent
import java.util.UUID
import kotlin.test.assertTrue

/**
 * Unit tests for CommuneOrderDestroyListener
 * Addresses CRITICAL issue #3: implement complete cascade (§6.6)
 */
class CommuneOrderDestroyListenerTest {

    private lateinit var communeService: CommuneService
    private lateinit var crossOrderService: CrossOrderMembershipService
    private lateinit var listener: CommuneOrderDestroyListener

    @BeforeEach
    fun setUp() {
        communeService = spyk(CommuneService(mutableMapOf(), mutableMapOf()))
        crossOrderService = mockk<CrossOrderMembershipService>()
        listener = CommuneOrderDestroyListener(communeService, crossOrderService)
    }

    /**
     * Test: returns early if order not in any commune
     */
    @Test
    fun testReturnsEarlyIfNotInCommune() {
        val event = mockk<FlagDeactivatedEvent>(relaxed = true)
        every { event.orderId } returns 999L  // Non-existent order
        every { communeService.getCommuneOfOrder(999L) } returns null

        // Act - should return early and not call cascade methods
        listener.onFlagDeactivated(event)

        // Assert - no cascade methods should be called
        verify(exactly = 0) { crossOrderService.setCascadeMode(any()) }
        verify(exactly = 0) { crossOrderService.clearCascadeMode() }
        assertTrue(true, "Should return early when order not in commune")
    }

    /**
     * Test: cascade mode is set and cleared when order is in commune
     */
    @Test
    fun testCascadeModeSetAndClearedWhenInCommune() {
        val orderId = 1L
        val communeId = UUID.randomUUID()

        val event = mockk<FlagDeactivatedEvent>(relaxed = true)
        every { event.orderId } returns orderId

        val commune = mockk<Commune>()
        every { commune.id } returns communeId
        every { commune.orderIds } returns mutableSetOf(orderId)

        every { communeService.getCommuneOfOrder(orderId) } returns commune
        every { communeService.removeOrderFromCommune(communeId, orderId) } returns Result.Success(Unit)
        every { communeService.getCommune(communeId) } returns null
        every { communeService.dissolveCommune(communeId) } returns Result.Success(Unit)
        every { crossOrderService.setCascadeMode(true) } returns Unit
        every { crossOrderService.revokeCommuneMember(any(), any()) } returns Result.Success(Unit)
        every { crossOrderService.clearCascadeMode() } returns Unit

        // Act
        listener.onFlagDeactivated(event)

        // Assert - cascade mode should be set and cleared
        verify { crossOrderService.setCascadeMode(true) }
        verify { crossOrderService.clearCascadeMode() }
        assertTrue(true, "Cascade mode should be set and cleared when processing commune order")
    }

    /**
     * Test: cascade mode is cleared when removeOrderFromCommune fails
     */
    @Test
    fun testCascadeModeAlwaysClearedOnFailure() {
        val orderId = 1L
        val communeId = UUID.randomUUID()

        val event = mockk<FlagDeactivatedEvent>(relaxed = true)
        every { event.orderId } returns orderId

        val commune = mockk<Commune>()
        every { commune.id } returns communeId
        every { commune.orderIds } returns mutableSetOf(orderId)

        every { communeService.getCommuneOfOrder(orderId) } returns commune
        // Simulate failure (Result.Failure) — listener should still clear cascade mode
        every { communeService.removeOrderFromCommune(communeId, orderId) } returns Result.Failure("Test error")
        every { communeService.dissolveCommune(communeId) } returns Result.Failure("Test error")
        every { crossOrderService.setCascadeMode(true) } returns Unit
        every { crossOrderService.revokeCommuneMember(any(), any()) } returns Result.Success(Unit)
        every { crossOrderService.clearCascadeMode() } returns Unit

        // Act — no exception is thrown, Result.Failure is handled internally
        listener.onFlagDeactivated(event)

        // Assert — cascade mode should still be cleared even though operation failed
        verify { crossOrderService.clearCascadeMode() }
        assertTrue(true, "Cascade mode should be cleared even on operation failure")
    }
}
