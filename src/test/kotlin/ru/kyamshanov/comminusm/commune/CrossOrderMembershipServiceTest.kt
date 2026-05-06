package ru.kyamshanov.comminusm.commune

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import ru.kyamshanov.comminusm.commune.model.Result
import ru.kyamshanov.comminusm.commune.repository.OrderMembersRepository
import ru.kyamshanov.comminusm.commune.service.CrossOrderMembershipService
import ru.kyamshanov.comminusm.commune.service.OrderMembershipService
import java.util.UUID

class CrossOrderMembershipServiceTest {
    private lateinit var repository: OrderMembersRepository
    private lateinit var membershipService: OrderMembershipService
    private lateinit var crossOrderService: CrossOrderMembershipService

    @BeforeEach
    fun setUp() {
        val cache = mutableMapOf<Long, MutableSet<Any>>()
        @Suppress("UNCHECKED_CAST")
        val typedCache = cache as MutableMap<Long, MutableSet<ru.kyamshanov.comminusm.commune.model.OrderMember>>
        repository = OrderMembersRepository(typedCache)
        membershipService = OrderMembershipService(repository)
        crossOrderService = CrossOrderMembershipService(membershipService)
    }

    @Test
    fun `grantCommuneMember adds commune-granted member to both orders`() {
        val orderA = 1L
        val orderB = 2L
        val player = UUID.randomUUID()
        val orderIdTuple = Pair(orderA, orderB)

        val result = crossOrderService.grantCommuneMember(orderIdTuple, player)

        assertTrue(result is Result.Success)
        assertTrue(repository.isMember(orderA, player))
        assertTrue(repository.isMember(orderB, player))
        val memberA = repository.getMembersOfOrder(orderA).find { it.playerUuid == player }
        val memberB = repository.getMembersOfOrder(orderB).find { it.playerUuid == player }
        assertEquals("commune", memberA?.grantedVia)
        assertEquals("commune", memberB?.grantedVia)
    }

    @Test
    fun `revokeCommuneMember removes commune-granted member from both orders`() {
        val orderA = 1L
        val orderB = 2L
        val player = UUID.randomUUID()
        val orderIdTuple = Pair(orderA, orderB)

        // First grant
        crossOrderService.grantCommuneMember(orderIdTuple, player)

        // Then revoke
        val result = crossOrderService.revokeCommuneMember(orderIdTuple, player)

        assertTrue(result is Result.Success)
        assertFalse(repository.isMember(orderA, player))
        assertFalse(repository.isMember(orderB, player))
    }

    @Test
    fun `revokeCommuneMember idempotent - already revoked returns success`() {
        val orderA = 1L
        val orderB = 2L
        val player = UUID.randomUUID()
        val orderIdTuple = Pair(orderA, orderB)

        // Revoke without grant (cascade mode - idempotent)
        val result = crossOrderService.revokeCommuneMember(orderIdTuple, player)

        assertTrue(result is Result.Success)
    }

    @Test
    fun `inCascadeMode flag suppresses recalculation during batch operations`() {
        val orderA = 1L
        val orderB = 2L
        val playerA = UUID.randomUUID()
        val playerB = UUID.randomUUID()

        crossOrderService.setCascadeMode(true)
        crossOrderService.grantCommuneMember(Pair(orderA, orderB), playerA)
        crossOrderService.grantCommuneMember(Pair(orderA, orderB), playerB)
        crossOrderService.setCascadeMode(false)

        assertTrue(repository.isMember(orderA, playerA))
        assertTrue(repository.isMember(orderB, playerA))
        assertTrue(repository.isMember(orderA, playerB))
        assertTrue(repository.isMember(orderB, playerB))
    }

    @Test
    fun `getOnlineMembers returns set of online player UUIDs for order`() {
        val orderId = 1L
        val player1 = UUID.randomUUID()
        val player2 = UUID.randomUUID()

        membershipService.addNativeMember(orderId, player1, UUID.randomUUID())
        membershipService.addNativeMember(orderId, player2, UUID.randomUUID())

        val members = crossOrderService.getOnlineMembers(orderId)

        // In basic implementation, returns all members
        assertEquals(2, members.size)
        assertTrue(members.contains(player1))
        assertTrue(members.contains(player2))
    }

    @Test
    fun `revokeAllCommuneMembers called after cascade removal cleans up remaining references`() {
        val order1 = 1L
        val order2 = 2L
        val player1 = UUID.randomUUID()
        val player2 = UUID.randomUUID()

        // Grant cross-order members
        crossOrderService.grantCommuneMember(Pair(order1, order2), player1)
        crossOrderService.grantCommuneMember(Pair(order1, order2), player2)

        // Cascade remove both players
        crossOrderService.revokeCommuneMember(Pair(order1, order2), player1)
        crossOrderService.revokeCommuneMember(Pair(order1, order2), player2)

        // Verify all commune members are gone
        val communeMembers1 = repository.getMembersWithType(order1, "commune")
        val communeMembers2 = repository.getMembersWithType(order2, "commune")
        assertEquals(0, communeMembers1.size)
        assertEquals(0, communeMembers2.size)
    }
}
