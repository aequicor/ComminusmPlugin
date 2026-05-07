package ru.kyamshanov.comminusm.commune.listener

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.application.usecases.commune.CheckCommuneFriendlyFireUseCase
import java.util.UUID
import kotlin.test.assertTrue

/**
 * Unit tests for FriendlyFireListener
 * Addresses CRITICAL issue #2: spec §6.17 - use native orders not owner only
 */
class FriendlyFireListenerTest {
    private lateinit var checkCommuneFriendlyFireUseCase: CheckCommuneFriendlyFireUseCase
    private lateinit var listener: FriendlyFireListener

    @BeforeEach
    fun setUp() {
        checkCommuneFriendlyFireUseCase = mockk(relaxed = true)
        listener = FriendlyFireListener(checkCommuneFriendlyFireUseCase)
    }

    /**
     * Test: listener returns early if damager is not a player
     */
    @Test
    fun testIgnoresNonPlayerDamage() {
        val damagee = mockk<org.bukkit.entity.Entity>(relaxed = true)
        val damager = mockk<org.bukkit.entity.Entity>(relaxed = true)

        val event = mockk<EntityDamageByEntityEvent>(relaxed = true)
        every { event.entity } returns damagee
        every { event.damager } returns damager

        // Act
        listener.onEntityDamageByEntity(event)

        // Assert - should return early, not cancel
        verify(exactly = 0) { event.isCancelled = true }
        assertTrue(true, "Should ignore non-player damage")
    }

    /**
     * Test: returns early when not in same commune
     */
    @Test
    fun testReturnsEarlyWhenNotInCommune() {
        val damagerUuid = UUID.randomUUID()
        val damageeUuid = UUID.randomUUID()

        val damager = mockk<Player>(relaxed = true)
        every { damager.uniqueId } returns damagerUuid

        val damagee = mockk<Player>(relaxed = true)
        every { damagee.uniqueId } returns damageeUuid

        val event = mockk<EntityDamageByEntityEvent>(relaxed = true)
        every { event.entity } returns damagee
        every { event.damager } returns damager

        // Use case returns false - not in same commune
        every { checkCommuneFriendlyFireUseCase.invoke(damageeUuid, damagerUuid) } returns false

        // Act
        listener.onEntityDamageByEntity(event)

        // Assert
        verify(exactly = 0) { event.isCancelled = true }
        assertTrue(true, "Should not cancel when not in same commune")
    }

    /**
     * Test: cancels damage when both in same commune
     */
    @Test
    fun testCancelsDamageInSameCommune() {
        val damagerUuid = UUID.randomUUID()
        val damageeUuid = UUID.randomUUID()

        val damager = mockk<Player>(relaxed = true)
        every { damager.uniqueId } returns damagerUuid
        every { damager.sendMessage(any<String>()) } returns Unit

        val damagee = mockk<Player>(relaxed = true)
        every { damagee.uniqueId } returns damageeUuid

        val event = mockk<EntityDamageByEntityEvent>(relaxed = true)
        every { event.entity } returns damagee
        every { event.damager } returns damager

        // Use case returns true - in same commune
        every { checkCommuneFriendlyFireUseCase.invoke(damageeUuid, damagerUuid) } returns true

        // Act
        listener.onEntityDamageByEntity(event)

        // Assert - damage should be cancelled
        verify { event.isCancelled = true }
        verify { damager.sendMessage(any<String>()) }
    }
}
