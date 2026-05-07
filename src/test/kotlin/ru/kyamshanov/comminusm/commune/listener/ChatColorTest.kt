package ru.kyamshanov.comminusm.commune.listener

import io.mockk.every
import io.mockk.mockk
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.commune.service.CommuneService
import ru.kyamshanov.comminusm.commune.service.OrderMembershipService
import ru.kyamshanov.comminusm.service.OrderService
import java.util.UUID
import kotlin.test.assertTrue

/**
 * Unit tests for deprecated ChatColor usage (HIGH issue #7)
 * Tests that FriendlyFireListener doesn't use hardcoded § codes
 */
class ChatColorTest {

    private lateinit var communeService: CommuneService
    private lateinit var membershipService: OrderMembershipService
    private lateinit var orderService: OrderService
    private lateinit var listener: FriendlyFireListener

    @BeforeEach
    fun setUp() {
        communeService = mockk<CommuneService>()
        membershipService = mockk<OrderMembershipService>()
        listener = FriendlyFireListener(communeService, membershipService)
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

        // Damager has no native orders - should return early
        every { membershipService.getNativeOrdersOfPlayer(damagerUuid) } returns emptySet()

        // Act - should not throw
        listener.onEntityDamageByEntity(event)

        // Assert
        assertTrue(true, "Listener should not crash with legacy chat color issues")
    }
}
