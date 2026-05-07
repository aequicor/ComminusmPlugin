package ru.kyamshanov.comminusm.commune.listener

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.application.usecases.commune.RemoveOrderFromCommuneWithCascadeUseCase
import ru.kyamshanov.comminusm.event.FlagDeactivatedEvent
import kotlin.test.assertTrue

/**
 * Unit tests for CommuneOrderDestroyListener
 * Addresses CRITICAL issue #3: implement complete cascade (§6.6)
 */
class CommuneOrderDestroyListenerTest {
    private lateinit var removeOrderFromCommuneWithCascadeUseCase: RemoveOrderFromCommuneWithCascadeUseCase
    private lateinit var listener: CommuneOrderDestroyListener

    @BeforeEach
    fun setUp() {
        removeOrderFromCommuneWithCascadeUseCase = mockk(relaxed = true)
        listener = CommuneOrderDestroyListener(removeOrderFromCommuneWithCascadeUseCase)
    }

    /**
     * Test: Use case is called when flag is deactivated
     */
    @Test
    fun testCallsRemoveOrderFromCommuneUseCase() {
        val orderId = 123L
        val event = mockk<FlagDeactivatedEvent>(relaxed = true)
        every { event.orderId } returns orderId

        // Act
        listener.onFlagDeactivated(event)

        // Assert - use case should be called with the order ID
        verify(exactly = 1) { removeOrderFromCommuneWithCascadeUseCase.invoke(orderId) }
        assertTrue(true, "Should call the use case")
    }
}
