package ru.kyamshanov.comminusm.application.usecases.commune

import ru.kyamshanov.comminusm.domain.entities.Commune
import ru.kyamshanov.comminusm.domain.repositories.CommuneRepository
import java.util.UUID

/**
 * Implementation of ListCommunesForMemberUseCase.
 * Retrieves all communes a member belongs to.
 */
class ListCommunesForMemberUseCaseImpl(
    private val communeRepository: CommuneRepository,
) : ListCommunesForMemberUseCase {
    override fun invoke(memberId: UUID): List<Commune> = communeRepository.findAllByMember(memberId)
}
