@file:Suppress("MaxLineLength")

package ru.kyamshanov.comminusm.application.usecases.commune

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.kyamshanov.comminusm.domain.entities.Commune
import ru.kyamshanov.comminusm.domain.repositories.CommuneInvitationRepository
import ru.kyamshanov.comminusm.domain.repositories.CommuneRepository
import ru.kyamshanov.comminusm.domain.value_objects.CommuneInvitation
import ru.kyamshanov.comminusm.domain.value_objects.Result
import java.util.UUID

/**
 * Unit tests for AcceptInvitationUseCase.
 * Verifies invitee identity verification and invitation acceptance logic.
 */
class AcceptInvitationUseCaseTest {
    private var updatedCommune: Commune? = null
    private var deletedInvitationId: UUID? = null

    private val communeRepository =
        object : CommuneRepository {
            private val communes = mutableMapOf<UUID, Commune>()

            override fun findById(id: UUID): Commune? = communes[id]

            override fun findByName(name: String): Commune? = communes.values.find { it.name == name }

            override fun findAllByMember(memberId: UUID): List<Commune> = communes.values.filter { it.memberIds.contains(memberId) }

            override fun insert(commune: Commune): UUID {
                communes[commune.id] = commune
                return commune.id
            }

            override fun update(commune: Commune) {
                updatedCommune = commune
                communes[commune.id] = commune
            }

            override fun delete(id: UUID) {
                communes.remove(id)
            }
        }

    private val invitationRepository =
        object : CommuneInvitationRepository {
            private val invitations = mutableMapOf<UUID, CommuneInvitation>()

            override fun findById(id: UUID): CommuneInvitation? = invitations[id]

            override fun findByCommune(communeId: UUID): List<CommuneInvitation> = invitations.values.filter { it.communeId == communeId }

            override fun findByInvitee(inviteeId: UUID): List<CommuneInvitation> = invitations.values.filter { it.inviteeId == inviteeId }

            override fun insert(invitation: CommuneInvitation): UUID {
                invitations[invitation.id] = invitation
                return invitation.id
            }

            override fun delete(id: UUID) {
                deletedInvitationId = id
                invitations.remove(id)
            }
        }

    private val useCase = AcceptInvitationUseCaseImpl(communeRepository, invitationRepository)

    @Test
    fun `should accept invitation when invitee identity matches`() {
        val invitationId = UUID.randomUUID()
        val communeId = UUID.randomUUID()
        val inviteeId = UUID.randomUUID()
        val inviterId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()

        val invitation =
            CommuneInvitation(
                id = invitationId,
                communeId = communeId,
                inviteeId = inviteeId,
                inviterId = inviterId,
            )
        invitationRepository.insert(invitation)

        val commune =
            Commune(
                id = communeId,
                name = "Test Commune",
                ownerId = ownerId,
                memberIds = emptySet(),
            )
        communeRepository.update(commune)

        val result = useCase.invoke(invitationId, inviteeId)

        assertTrue(result is Result.Success)
        assertTrue(updatedCommune?.memberIds?.contains(inviteeId) == true)
        assertTrue(deletedInvitationId == invitationId)
    }

    @Test
    fun `should fail when invitee identity does not match`() {
        val invitationId = UUID.randomUUID()
        val communeId = UUID.randomUUID()
        val inviteeId = UUID.randomUUID()
        val wrongInviteeId = UUID.randomUUID() // Different from invitation's inviteeId
        val inviterId = UUID.randomUUID()

        val invitation =
            CommuneInvitation(
                id = invitationId,
                communeId = communeId,
                inviteeId = inviteeId,
                inviterId = inviterId,
            )
        invitationRepository.insert(invitation)

        val result = useCase.invoke(invitationId, wrongInviteeId)

        assertTrue(result is Result.Failure)
        val failureMsg = (result as? Result.Failure)?.error ?: ""
        assertTrue(
            failureMsg.contains("not for you"),
            "Expected 'not for you' in error message, got: $failureMsg",
        )
    }

    @Test
    fun `should fail when invitation not found`() {
        val invitationId = UUID.randomUUID()
        val inviteeId = UUID.randomUUID()

        val result = useCase.invoke(invitationId, inviteeId)

        assertTrue(result is Result.Failure)
        val failureMsg = (result as? Result.Failure)?.error ?: ""
        assertTrue(
            failureMsg.contains("not found"),
            "Expected 'not found' in error message, got: $failureMsg",
        )
    }

    @Test
    fun `should fail when invitee already member`() {
        val invitationId = UUID.randomUUID()
        val communeId = UUID.randomUUID()
        val inviteeId = UUID.randomUUID()
        val inviterId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()

        val invitation =
            CommuneInvitation(
                id = invitationId,
                communeId = communeId,
                inviteeId = inviteeId,
                inviterId = inviterId,
            )
        invitationRepository.insert(invitation)

        val commune =
            Commune(
                id = communeId,
                name = "Test Commune",
                ownerId = ownerId,
                memberIds = setOf(inviteeId), // Already a member
            )
        communeRepository.update(commune)

        val result = useCase.invoke(invitationId, inviteeId)

        assertTrue(result is Result.Failure)
        val failureMsg = (result as? Result.Failure)?.error ?: ""
        assertTrue(
            failureMsg.contains("member"),
            "Expected 'member' in error message, got: $failureMsg",
        )
    }
}
