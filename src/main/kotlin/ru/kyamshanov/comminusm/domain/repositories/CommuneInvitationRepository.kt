package ru.kyamshanov.comminusm.domain.repositories

import ru.kyamshanov.comminusm.domain.value_objects.CommuneInvitation
import java.util.UUID

/**
 * Domain repository interface for CommuneInvitation value objects.
 * Defines contracts for invitation persistence operations.
 */
interface CommuneInvitationRepository {
    fun findById(id: UUID): CommuneInvitation?

    fun findByCommune(communeId: UUID): List<CommuneInvitation>

    fun findByInvitee(inviteeId: UUID): List<CommuneInvitation>

    fun insert(invitation: CommuneInvitation): UUID

    fun delete(id: UUID)
}
