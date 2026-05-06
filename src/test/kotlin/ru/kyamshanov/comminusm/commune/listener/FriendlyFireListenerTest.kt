package ru.kyamshanov.comminusm.commune.listener

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.commune.model.Commune
import ru.kyamshanov.comminusm.commune.service.CommuneService
import ru.kyamshanov.comminusm.commune.service.OrderMembershipService
import ru.kyamshanov.comminusm.service.OrderService
import java.util.UUID
import kotlin.test.assertTrue

/**
 * Unit tests for FriendlyFireListener
 * Addresses CRITICAL issue #2: spec §6.17 - use native orders not owner only
 */
class FriendlyFireListenerTest {

    private lateinit var communeService: CommuneService
    private lateinit var membershipService: OrderMembershipService
    private lateinit var orderService: OrderService
    private lateinit var listener: FriendlyFireListener

    @BeforeEach
    fun setUp() {
        communeService = mockk<CommuneService>()
        membershipService = mockk<OrderMembershipService>()
        orderService = mockk<OrderService>()
        listener = FriendlyFireListener(communeService, membershipService, orderService)
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
     * Test: returns early when damager has no native orders
     */
    @Test
    fun testReturnsEarlyWhenDamagerHasNoOrders() {
        val damagerUuid = UUID.randomUUID()
        val damageeUuid = UUID.randomUUID()

        val damager = mockk<Player>(relaxed = true)
        every { damager.uniqueId } returns damagerUuid

        val damagee = mockk<Player>(relaxed = true)
        every { damagee.uniqueId } returns damageeUuid

        val event = mockk<EntityDamageByEntityEvent>(relaxed = true)
        every { event.entity } returns damagee
        every { event.damager } returns damager

        // Damager has no native orders
        every { membershipService.getNativeOrdersOfPlayer(damagerUuid) } returns emptySet()

        // Act
        listener.onEntityDamageByEntity(event)

        // Assert
        verify(exactly = 0) { event.isCancelled = true }
        assertTrue(true, "Should return early when damager has no orders")
    }

    /**
     * Test: returns early when damagee has no native orders
     */
    @Test
    fun testReturnsEarlyWhenDamageeHasNoOrders() {
        val damagerUuid = UUID.randomUUID()
        val damageeUuid = UUID.randomUUID()

        val damager = mockk<Player>(relaxed = true)
        every { damager.uniqueId } returns damagerUuid

        val damagee = mockk<Player>(relaxed = true)
        every { damagee.uniqueId } returns damageeUuid

        val event = mockk<EntityDamageByEntityEvent>(relaxed = true)
        every { event.entity } returns damagee
        every { event.damager } returns damager

        // Damager has orders
        every { membershipService.getNativeOrdersOfPlayer(damagerUuid) } returns setOf(1L)
        // Damagee has no orders
        every { membershipService.getNativeOrdersOfPlayer(damageeUuid) } returns emptySet()

        // Act
        listener.onEntityDamageByEntity(event)

        // Assert
        verify(exactly = 0) { event.isCancelled = true }
        assertTrue(true, "Should return early when damagee has no orders")
    }

    /**
     * Test: cancels damage when both in same commune
     */
    @Test
    fun testCancelsDamageInSameCommune() {
        val damagerUuid = UUID.randomUUID()
        val damageeUuid = UUID.randomUUID()
        val communeId = UUID.randomUUID()

        val damager = mockk<Player>(relaxed = true)
        every { damager.uniqueId } returns damagerUuid

        val damagee = mockk<Player>(relaxed = true)
        every { damagee.uniqueId } returns damageeUuid

        val event = mockk<EntityDamageByEntityEvent>(relaxed = true)
        every { event.entity } returns damagee
        every { event.damager } returns damager

        // Both have native order 1
        every { membershipService.getNativeOrdersOfPlayer(damagerUuid) } returns setOf(1L)
        every { membershipService.getNativeOrdersOfPlayer(damageeUuid) } returns setOf(1L)

        val commune = mockk<Commune>()
        every { commune.id } returns communeId
        every { communeService.getCommuneOfOrder(1L) } returns commune

        // Act
        listener.onEntityDamageByEntity(event)

        // Assert - damage should be cancelled
        verify { event.isCancelled = true }
        verify { damager.sendMessage(any<String>()) }
    }

    /**
     * Test: does not cancel when orders in different communes
     */
    @Test
    fun testDoesNotCancelDifferentCommunes() {
        val damagerUuid = UUID.randomUUID()
        val damageeUuid = UUID.randomUUID()
        val communeId1 = UUID.randomUUID()
        val communeId2 = UUID.randomUUID()

        val damager = mockk<Player>(relaxed = true)
        every { damager.uniqueId } returns damagerUuid

        val damagee = mockk<Player>(relaxed = true)
        every { damagee.uniqueId } returns damageeUuid

        val event = mockk<EntityDamageByEntityEvent>(relaxed = true)
        every { event.entity } returns damagee
        every { event.damager } returns damager

        // Damager has order 1, damagee has order 2
        every { membershipService.getNativeOrdersOfPlayer(damagerUuid) } returns setOf(1L)
        every { membershipService.getNativeOrdersOfPlayer(damageeUuid) } returns setOf(2L)

        val commune1 = mockk<Commune>()
        every { commune1.id } returns communeId1
        val commune2 = mockk<Commune>()
        every { commune2.id } returns communeId2
        every { communeService.getCommuneOfOrder(1L) } returns commune1
        every { communeService.getCommuneOfOrder(2L) } returns commune2

        // Act
        listener.onEntityDamageByEntity(event)

        // Assert
        verify(exactly = 0) { event.isCancelled = true }
        assertTrue(true, "Should not cancel damage in different communes")
    }
}
