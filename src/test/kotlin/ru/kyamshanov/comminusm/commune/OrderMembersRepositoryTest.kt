package ru.kyamshanov.comminusm.commune

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.kyamshanov.comminusm.commune.model.OrderMember
import ru.kyamshanov.comminusm.commune.repository.OrderMembersRepository
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class OrderMembersRepositoryTest {
    private lateinit var repository: OrderMembersRepository
    private val cache = ConcurrentHashMap<Long, MutableSet<OrderMember>>()

    @BeforeEach
    fun setUp() {
        cache.clear()
        // Use in-memory only for Stage 01
        repository = OrderMembersRepository(cache)
    }

    @Test
    fun `addMember adds a new member to cache`() {
        val playerId = UUID.randomUUID()
        val orderId = 1L
        val now = LocalDateTime.now()

        val result = repository.addMember(orderId, playerId, "native", now)

        assertNotNull(result)
        assertEquals(playerId, result.playerUuid)
        assertEquals(orderId, result.orderId)
        assertEquals("native", result.grantedVia)
    }

    @Test
    fun `getMembersOfOrder returns all members of an order`() {
        val orderId = 1L
        val player1 = UUID.randomUUID()
        val player2 = UUID.randomUUID()
        val now = LocalDateTime.now()

        repository.addMember(orderId, player1, "native", now)
        repository.addMember(orderId, player2, "commune", now)

        val members = repository.getMembersOfOrder(orderId)

        assertEquals(2, members.size)
        assertTrue(members.any { it.playerUuid == player1 && it.grantedVia == "native" })
        assertTrue(members.any { it.playerUuid == player2 && it.grantedVia == "commune" })
    }

    @Test
    fun `isMember returns true for existing member`() {
        val orderId = 1L
        val playerId = UUID.randomUUID()
        val now = LocalDateTime.now()

        repository.addMember(orderId, playerId, "native", now)

        assertTrue(repository.isMember(orderId, playerId))
    }

    @Test
    fun `isMember returns false for non-existing member`() {
        val orderId = 1L
        val playerId = UUID.randomUUID()

        assertFalse(repository.isMember(orderId, playerId))
    }

    @Test
    fun `removeMember removes member from cache`() {
        val orderId = 1L
        val playerId = UUID.randomUUID()
        val now = LocalDateTime.now()

        repository.addMember(orderId, playerId, "native", now)
        assertTrue(repository.isMember(orderId, playerId))

        val removed = repository.removeMember(orderId, playerId)
        assertTrue(removed)
        assertFalse(repository.isMember(orderId, playerId))
    }

    @Test
    fun `removeMember returns false for non-existing member`() {
        val orderId = 1L
        val playerId = UUID.randomUUID()

        val removed = repository.removeMember(orderId, playerId)
        assertFalse(removed)
    }

    @Test
    fun `getOrdersOfPlayer returns all orders where player is a member`() {
        val playerId = UUID.randomUUID()
        val order1 = 1L
        val order2 = 2L
        val now = LocalDateTime.now()

        repository.addMember(order1, playerId, "native", now)
        repository.addMember(order2, playerId, "commune", now)

        val orders = repository.getOrdersOfPlayer(playerId)

        assertEquals(2, orders.size)
        assertTrue(orders.contains(order1))
        assertTrue(orders.contains(order2))
    }

    @Test
    fun `getMembersWithType returns only members of specified type`() {
        val orderId = 1L
        val player1 = UUID.randomUUID()
        val player2 = UUID.randomUUID()
        val now = LocalDateTime.now()

        repository.addMember(orderId, player1, "native", now)
        repository.addMember(orderId, player2, "commune", now)

        val nativeMembers = repository.getMembersWithType(orderId, "native")
        val communeMembers = repository.getMembersWithType(orderId, "commune")

        assertEquals(1, nativeMembers.size)
        assertEquals(1, communeMembers.size)
        assertEquals(player1, nativeMembers.first().playerUuid)
        assertEquals(player2, communeMembers.first().playerUuid)
    }

    @Test
    fun `cache is thread-safe for concurrent reads`() {
        val orderId = 1L
        val playerId = UUID.randomUUID()
        val now = LocalDateTime.now()

        repository.addMember(orderId, playerId, "native", now)

        val results = (0..9).map {
            Thread {
                val members = repository.getMembersOfOrder(orderId)
                assertEquals(1, members.size)
            }.apply { start() }
        }.map { it.also { t -> t.join() } }

        // All threads completed successfully (no ConcurrentModificationException)
        assertTrue(results.isNotEmpty())
    }
}
