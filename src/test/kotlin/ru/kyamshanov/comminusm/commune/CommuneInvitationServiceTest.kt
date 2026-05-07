package ru.kyamshanov.comminusm.commune

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
    private val invitationTimers = ConcurrentHashMap<UUID, Int>()

    @BeforeEach
    fun setUp() {
        invitations.clear()
        invitationTimers.clear()
        // plugin=null — test mode, no real Bukkit scheduling
        invitationService = CommuneInvitationService(invitations, invitationTimers, plugin = null)
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

        // In test mode (plugin=null), taskId should be -1
        assertTrue(invitationTimers.containsKey(invitation.id), "Timer should be stored for invitation")
        assertEquals(-1, invitationTimers[invitation.id], "In test mode, taskId should be -1")
    }

    @Test
    fun `createInvitation_withNullPlugin_storesInMemory`() {
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

        // Verify invitation is in map
        assertNotNull(invitationService.getInvitation(invitation.id))
        // Verify timer is stored (as -1 in test mode)
        assertTrue(invitationTimers.containsKey(invitation.id))
    }

    @Test
    fun `cancelInvitation_removesTimerFromMap`() {
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

        // Verify timer is stored before cancel
        assertTrue(invitationTimers.containsKey(invitation.id))

        // Cancel invitation
        invitationService.cancelInvitation(invitation.id)

        // Verify both invitation and timer are removed
        assertNull(invitationService.getInvitation(invitation.id))
        assertFalse(invitationTimers.containsKey(invitation.id), "Timer should be removed after cancel")
    }

    @Test
    fun `expireInvitation_removesTimerFromMap`() {
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

        // Verify timer is stored before expire
        assertTrue(invitationTimers.containsKey(invitation.id))

        // Expire invitation
        invitationService.expireInvitation(invitation.id)

        // Verify both invitation and timer are removed
        assertNull(invitationService.getInvitation(invitation.id))
        assertFalse(invitationTimers.containsKey(invitation.id), "Timer should be removed after expire")
    }

    @Test
    fun `createInvitation_replacesExistingForSameTarget`() {
        val fromOrderId = 1L
        val targetOrderId = 2L
        val targetLeaderUUID = UUID.randomUUID()
        val communeId = UUID.randomUUID()
        val now = LocalDateTime.now()

        // Create first invitation
        val result1 =
            invitationService.createInvitation(
                fromOrderId = fromOrderId,
                targetOrderId = targetOrderId,
                communeId = communeId,
                targetLeaderUUID = targetLeaderUUID,
                expiresAt = now.plusSeconds(500),
            )
        val invitation1 = (result1 as Result.Success).data

        // Create second invitation for same target (should replace)
        val result2 =
            invitationService.createInvitation(
                fromOrderId = fromOrderId + 1,
                targetOrderId = targetOrderId,
                communeId = communeId,
                targetLeaderUUID = targetLeaderUUID,
                expiresAt = now.plusSeconds(600),
            )
        val invitation2 = (result2 as Result.Success).data

        // First should be gone
        assertNull(invitationService.getInvitation(invitation1.id))
        // Second should exist
        assertNotNull(invitationService.getInvitation(invitation2.id))
        // Only one invitation in map (or second one exists)
        assertEquals(1, invitationService.getInvitationsForOrder(targetOrderId).size)
    }
}
