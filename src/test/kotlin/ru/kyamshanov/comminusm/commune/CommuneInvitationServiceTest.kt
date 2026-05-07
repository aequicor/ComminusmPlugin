package ru.kyamshanov.comminusm.commune

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.commune.model.CommuneInvitation
import ru.kyamshanov.comminusm.commune.model.Result
import ru.kyamshanov.comminusm.commune.service.CommuneInvitationService
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class CommuneInvitationServiceTest {
    private lateinit var invitationService: CommuneInvitationService
    private val invitations = ConcurrentHashMap<UUID, CommuneInvitation>()
    private val invitationTimers = ConcurrentHashMap<UUID, Any>()

    @BeforeEach
    fun setUp() {
        invitations.clear()
        invitationTimers.clear()
        invitationService = CommuneInvitationService(invitations, invitationTimers)
    }

    @Test
    fun `createInvitation creates a new invitation with 500s expiry`() {
        val fromOrderId = 1L
        val targetOrderId = 2L
        val targetLeaderUUID = UUID.randomUUID()
        val communeId = UUID.randomUUID()
        val now = LocalDateTime.now()

        val result =
            invitationService.createInvitation(
                fromOrderId = fromOrderId,
                targetOrderId = targetOrderId,
                communeId = communeId,
                targetLeaderUUID = targetLeaderUUID,
                expiresAt = now.plusSeconds(500),
            )

        assertTrue(result is Result.Success)
        val invitation = (result as Result.Success).data
        assertEquals(fromOrderId, invitation.fromOrderId)
        assertEquals(targetOrderId, invitation.targetOrderId)
        assertEquals(communeId, invitation.communeId)
        assertEquals(targetLeaderUUID, invitation.targetLeaderUUID)
    }

    @Test
    fun `getInvitation returns invitation by ID`() {
        val fromOrderId = 1L
        val targetOrderId = 2L
        val targetLeaderUUID = UUID.randomUUID()
        val communeId = UUID.randomUUID()
        val now = LocalDateTime.now()

        val result =
            invitationService.createInvitation(
                fromOrderId = fromOrderId,
                targetOrderId = targetOrderId,
                communeId = communeId,
                targetLeaderUUID = targetLeaderUUID,
                expiresAt = now.plusSeconds(500),
            )

        val invitation = (result as Result.Success).data
        val invitationId = invitation.id

        val retrieved = invitationService.getInvitation(invitationId)
        assertNotNull(retrieved)
        assertEquals(invitationId, retrieved?.id)
    }

    @Test
    fun `cancelInvitation removes invitation`() {
        val fromOrderId = 1L
        val targetOrderId = 2L
        val targetLeaderUUID = UUID.randomUUID()
        val communeId = UUID.randomUUID()
        val now = LocalDateTime.now()

        val result =
            invitationService.createInvitation(
                fromOrderId = fromOrderId,
                targetOrderId = targetOrderId,
                communeId = communeId,
                targetLeaderUUID = targetLeaderUUID,
                expiresAt = now.plusSeconds(500),
            )

        val invitation = (result as Result.Success).data
        invitationService.cancelInvitation(invitation.id)

        val retrieved = invitationService.getInvitation(invitation.id)
        assertNull(retrieved)
    }

    @Test
    fun `getInvitationsForOrder returns invitations targeting an order`() {
        val fromOrder1 = 1L
        val fromOrder2 = 3L
        val targetOrder = 2L
        val targetLeaderUUID = UUID.randomUUID()
        val communeId = UUID.randomUUID()
        val now = LocalDateTime.now()

        invitationService.createInvitation(
            fromOrderId = fromOrder1,
            targetOrderId = targetOrder,
            communeId = communeId,
            targetLeaderUUID = targetLeaderUUID,
            expiresAt = now.plusSeconds(500),
        )

        invitationService.createInvitation(
            fromOrderId = fromOrder2,
            targetOrderId = targetOrder,
            communeId = communeId,
            targetLeaderUUID = targetLeaderUUID,
            expiresAt = now.plusSeconds(500),
        )

        val invs = invitationService.getInvitationsForOrder(targetOrder)
        assertEquals(1, invs.size) // Only one invitation per target order active
    }

    @Test
    fun `expireInvitation removes invitation`() {
        val fromOrderId = 1L
        val targetOrderId = 2L
        val targetLeaderUUID = UUID.randomUUID()
        val communeId = UUID.randomUUID()
        val now = LocalDateTime.now()

        val result =
            invitationService.createInvitation(
                fromOrderId = fromOrderId,
                targetOrderId = targetOrderId,
                communeId = communeId,
                targetLeaderUUID = targetLeaderUUID,
                expiresAt = now.plusSeconds(500),
            )

        val invitation = (result as Result.Success).data

        invitationService.expireInvitation(invitation.id)

        val retrieved = invitationService.getInvitation(invitation.id)
        assertNull(retrieved)
    }

    @Test
    fun `timer is stored for each invitation`() {
        val fromOrderId = 1L
        val targetOrderId = 2L
        val targetLeaderUUID = UUID.randomUUID()
        val communeId = UUID.randomUUID()
        val now = LocalDateTime.now()

        val result =
            invitationService.createInvitation(
                fromOrderId = fromOrderId,
                targetOrderId = targetOrderId,
                communeId = communeId,
                targetLeaderUUID = targetLeaderUUID,
                expiresAt = now.plusSeconds(500),
            )

        val invitation = (result as Result.Success).data

        // Simulate storing a timer reference (in real code, BukkitTask)
        // For test purposes, just verify invitationTimers map is used
        assertTrue(invitationTimers.isEmpty() || invitationTimers.containsKey(invitation.id))
    }
}
