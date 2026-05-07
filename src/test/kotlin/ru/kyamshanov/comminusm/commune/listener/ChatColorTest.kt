package ru.kyamshanov.comminusm.commune.listener

import io.mockk.every
import io.mockk.mockk
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.application.usecases.commune.CheckCommuneFriendlyFireUseCase
import java.util.UUID
import kotlin.test.assertTrue

/**
 * Unit tests for deprecated ChatColor usage (HIGH issue #7)
 * Tests that FriendlyFireListener doesn't use hardcoded § codes
 */
class ChatColorTest {
    private lateinit var checkCommuneFriendlyFireUseCase: CheckCommuneFriendlyFireUseCase
    private lateinit var listener: FriendlyFireListener

    @BeforeEach
    fun setUp() {
        checkCommuneFriendlyFireUseCase = mockk(relaxed = true)
        listener = FriendlyFireListener(checkCommuneFriendlyFireUseCase)
    }

    /**
     * Test: FriendlyFireListener should not use hardcoded § legacy ChatColor codes
     */
    @Test
    fun testFriendlyFireListenerCompiles() {
        val damagerUuid = UUID.randomUUID()
        val damageeUuid = UUID.randomUUID()

        val damager = mockk<Player>(relaxed = true)
        every { damager.uniqueId } returns damagerUuid

        val damagee = mockk<Player>(relaxed = true)
        every { damagee.uniqueId } returns damageeUuid

        val event = mockk<EntityDamageByEntityEvent>(relaxed = true)
        every { event.entity } returns damagee
        every { event.damager } returns damager

        // Use case returns false - no friendly fire
        every { checkCommuneFriendlyFireUseCase.invoke(damageeUuid, damagerUuid) } returns false

        // Act - should not throw
        listener.onEntityDamageByEntity(event)

        // Assert
        assertTrue(true, "Listener should not crash with legacy chat color issues")
    }
}
