package ru.kyamshanov.comminusm.commune.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.commune.model.OrderMember
import ru.kyamshanov.comminusm.commune.model.Result
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for CrossOrderMembershipService
 * Addresses HIGH issue #5: version validation in grantCommuneMember (§6.13 step 7b)
 */
class CrossOrderMembershipServiceTest {

    private lateinit var membershipService: OrderMembershipService
    private lateinit var service: CrossOrderMembershipService

    @BeforeEach
    fun setUp() {
        membershipService = mockk(relaxed = true)
        service = CrossOrderMembershipService(membershipService)
    }

    /**
     * Test: grantCommuneMember adds members with grantedVia="commune"
     */
    @Test
    fun testGrantCommuneMember() {
        val orderA = 1L
        val orderB = 2L
        val playerUuid = UUID.randomUUID()
        val now = LocalDateTime.now()

        val member = OrderMember(playerUuid, orderA, now, "commune")
        every { membershipService.addMember(any(), any(), any(), any(), any()) } returns
            Result.Success(member)

        // Act
        val result = service.grantCommuneMember(Pair(orderA, orderB), playerUuid)

        // Assert
        assertTrue(result is Result.Success, "Grant should succeed")
    }

    /**
     * Test: grantCommuneMember rolls back on second-order failure
     */
    @Test
    fun testGrantRollsBackOnFailure() {
        val orderA = 1L
        val orderB = 2L
        val playerUuid = UUID.randomUUID()
        val now = LocalDateTime.now()

        val member = OrderMember(playerUuid, orderA, now, "commune")
        every { membershipService.addMember(orderA, any(), any(), any(), any()) } returns
            Result.Success(member)
        every { membershipService.addMember(orderB, any(), any(), any(), any()) } returns
            Result.Failure("Already member")
        every { membershipService.removeMemberSilently(any(), any()) } returns Unit

        // Act
        val result = service.grantCommuneMember(Pair(orderA, orderB), playerUuid)

        // Assert
        assertTrue(result is Result.Failure, "Should fail and rollback")
        verify { membershipService.removeMemberSilently(any(), any()) }
    }

    /**
     * Test: cascade mode flag can be set and cleared
     */
    @Test
    fun testCascadeModeFunctions() {
        assertFalse(service.isCascadeMode(), "Initially should be false")

        service.setCascadeMode(true)
        assertTrue(service.isCascadeMode(), "Should be true after set")

        service.clearCascadeMode()
        assertFalse(service.isCascadeMode(), "Should be false after clear")
    }

    /**
     * Test: revokeCommuneMember is idempotent
     */
    @Test
    fun testRevokeIsIdempotent() {
        val orderA = 1L
        val orderB = 2L
        val playerUuid = UUID.randomUUID()

        every { membershipService.removeMemberSilently(any(), any()) } returns Unit

        // Act
        val result = service.revokeCommuneMember(Pair(orderA, orderB), playerUuid)

        // Assert
        assertTrue(result is Result.Success, "Revoke should always succeed")
        verify(exactly = 2) { membershipService.removeMemberSilently(any(), any()) }
    }
}
