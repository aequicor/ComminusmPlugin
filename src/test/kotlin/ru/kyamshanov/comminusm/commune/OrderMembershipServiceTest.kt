package ru.kyamshanov.comminusm.commune

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import ru.kyamshanov.comminusm.commune.model.OrderMember
import ru.kyamshanov.comminusm.commune.model.Result
import ru.kyamshanov.comminusm.commune.repository.OrderMembersRepository
import ru.kyamshanov.comminusm.commune.service.OrderMembershipService
import java.time.LocalDateTime
import java.util.UUID

class OrderMembershipServiceTest {
    private lateinit var membershipService: OrderMembershipService
    private val cache = mutableMapOf<Long, MutableSet<OrderMember>>()
    private val repository = OrderMembersRepository(cache)
    private val publishedEvents = mutableListOf<String>()

    @BeforeEach
    fun setUp() {
        cache.clear()
        publishedEvents.clear()
        membershipService = OrderMembershipService(repository)
    }

    @Test
    fun `addNativeMember adds a native member successfully`() {
        val orderId = 1L
        val playerUuid = UUID.randomUUID()
        val initiator = UUID.randomUUID()
        val now = LocalDateTime.now()

        val result = membershipService.addNativeMember(orderId, playerUuid, initiator, now)

        assertTrue(result is Result.Success)
        val member = (result as Result.Success).data
        assertEquals(playerUuid, member.playerUuid)
        assertEquals("native", member.grantedVia)
    }

    @Test
    fun `removeNativeMember removes a native member successfully`() {
        val orderId = 1L
        val playerUuid = UUID.randomUUID()
        val initiator = UUID.randomUUID()
        val now = LocalDateTime.now()

        membershipService.addNativeMember(orderId, playerUuid, initiator, now)
        assertTrue(membershipService.isNativeMember(orderId, playerUuid))

        val result = membershipService.removeNativeMember(orderId, playerUuid, initiator)

        assertTrue(result is Result.Success)
        assertFalse(membershipService.isNativeMember(orderId, playerUuid))
    }

    @Test
    fun `isNativeMember returns true for native member`() {
        val orderId = 1L
        val playerUuid = UUID.randomUUID()
        val initiator = UUID.randomUUID()
        val now = LocalDateTime.now()

        membershipService.addNativeMember(orderId, playerUuid, initiator, now)

        assertTrue(membershipService.isNativeMember(orderId, playerUuid))
    }

    @Test
    fun `isNativeMember returns false for non-native member`() {
        val orderId = 1L
        val playerUuid = UUID.randomUUID()

        assertFalse(membershipService.isNativeMember(orderId, playerUuid))
    }

    @Test
    fun `getMembersOfOrder returns all members`() {
        val orderId = 1L
        val player1 = UUID.randomUUID()
        val player2 = UUID.randomUUID()
        val initiator = UUID.randomUUID()
        val now = LocalDateTime.now()

        membershipService.addNativeMember(orderId, player1, initiator, now)
        membershipService.addNativeMember(orderId, player2, initiator, now)

        val members = membershipService.getMembersOfOrder(orderId)

        assertEquals(2, members.size)
        assertTrue(members.any { it.playerUuid == player1 })
        assertTrue(members.any { it.playerUuid == player2 })
    }

    @Test
    fun `addNativeMember rejects duplicate member`() {
        val orderId = 1L
        val playerUuid = UUID.randomUUID()
        val initiator = UUID.randomUUID()
        val now = LocalDateTime.now()

        membershipService.addNativeMember(orderId, playerUuid, initiator, now)
        val result2 = membershipService.addNativeMember(orderId, playerUuid, initiator, now)

        assertTrue(result2 is Result.Failure)
    }

    @Test
    fun `removeNativeMember for non-existing member returns failure`() {
        val orderId = 1L
        val playerUuid = UUID.randomUUID()
        val initiator = UUID.randomUUID()

        val result = membershipService.removeNativeMember(orderId, playerUuid, initiator)

        assertTrue(result is Result.Failure)
    }

    @Test
    fun `concurrent addNativeMember calls are serialized`() {
        val orderId = 1L
        val players = (0..4).map { UUID.randomUUID() }
        val initiator = UUID.randomUUID()
        val now = LocalDateTime.now()

        val threads = players.map { player ->
            Thread {
                membershipService.addNativeMember(orderId, player, initiator, now)
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        val members = membershipService.getMembersOfOrder(orderId)
        assertEquals(5, members.size)
    }
}
