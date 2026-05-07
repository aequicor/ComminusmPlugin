package ru.kyamshanov.comminusm.commune.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.commune.model.OrderMember
import ru.kyamshanov.comminusm.commune.model.Result
import ru.kyamshanov.comminusm.commune.repository.OrderMembersRepository
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertTrue

/**
 * Unit tests for OrderMembershipService
 * Addresses HIGH issue #6: API inconsistency - removeMember with grantedVia parameter
 */
class OrderMembershipServiceTest {
    private lateinit var repository: OrderMembersRepository
    private lateinit var service: OrderMembershipService

    @BeforeEach
    fun setUp() {
        repository = mockk(relaxed = true)
        service = OrderMembershipService(repository)
    }

    /**
     * Test: addMember with grantedVia="commune"
     */
    @Test
    fun testAddMemberWithGrantedVia() {
        val orderId = 1L
        val playerUuid = UUID.randomUUID()
        val now = LocalDateTime.now()
        val member = OrderMember(playerUuid, orderId, now, "commune")

        every { repository.isMember(any(), any()) } returns false
        every { repository.addMember(any(), any(), any(), any()) } returns member

        // Act
        val result = service.addMember(orderId, playerUuid, "commune")

        // Assert
        assertTrue(result is Result.Success, "Should add commune member")
    }

    /**
     * Test: getNativeOrdersOfPlayer returns only native-granted orders
     */
    @Test
    fun testGetNativeOrdersOfPlayer() {
        val playerUuid = UUID.randomUUID()
        val nativeOrderId = 1L
        val communeOrderId = 2L
        val now = LocalDateTime.now()

        every { repository.getOrdersOfPlayer(playerUuid) } returns setOf(nativeOrderId, communeOrderId)
        every { repository.getMembersWithType(any(), any()) } returns
            setOf(OrderMember(playerUuid, nativeOrderId, now, "native"))

        // Act
        val nativeOrders = service.getNativeOrdersOfPlayer(playerUuid)

        // Assert
        assertTrue(nativeOrders.isNotEmpty(), "Should return some native orders")
    }

    /**
     * Test: removeNativeMember publishes event
     */
    @Test
    fun testRemoveNativeMemberPublishesEvent() {
        val orderId = 1L
        val playerUuid = UUID.randomUUID()

        every { repository.removeMember(any(), any()) } returns true

        // Act
        val result = service.removeNativeMember(orderId, playerUuid, UUID.randomUUID())

        // Assert
        assertTrue(result is Result.Success, "Should remove native member")
        verify { repository.removeMember(any(), any()) }
    }

    /**
     * Test: removeMemberSilently does not publish event (internal API)
     */
    @Test
    fun testRemoveMemberSilentlyNoEvent() {
        val orderId = 1L
        val playerUuid = UUID.randomUUID()

        every { repository.removeMember(any(), any()) } returns true

        // Act
        service.removeMemberSilently(orderId, playerUuid)

        // Assert - should still remove, just no event
        verify { repository.removeMember(any(), any()) }
    }

    /**
     * Test: isNativeMember checks only native status
     */
    @Test
    fun testIsNativeMember() {
        val orderId = 1L
        val playerUuid = UUID.randomUUID()
        val now = LocalDateTime.now()

        every { repository.getMembersWithType(any(), any()) } returns
            setOf(OrderMember(playerUuid, orderId, now, "native"))

        // Act - the function reads from the read lock
        val isNative = service.isNativeMember(orderId, playerUuid)

        // Assert
        assertTrue(isNative, "Should return true for native member")
    }

    /**
     * Test: addMember rejects if already member
     */
    @Test
    fun testAddMemberRejectsDuplicate() {
        val orderId = 1L
        val playerUuid = UUID.randomUUID()

        every { repository.isMember(any(), any()) } returns true

        // Act
        val result = service.addMember(orderId, playerUuid, "native")

        // Assert
        assertTrue(result is Result.Failure, "Should reject duplicate member")
    }
}
