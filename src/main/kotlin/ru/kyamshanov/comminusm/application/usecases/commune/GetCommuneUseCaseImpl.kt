package ru.kyamshanov.comminusm.application.usecases.commune

import ru.kyamshanov.comminusm.domain.entities.Commune
import ru.kyamshanov.comminusm.domain.repositories.CommuneRepository
import java.util.UUID

/**
 * Implementation of GetCommuneUseCase.
 * Retrieves a commune by its ID.
 */
class GetCommuneUseCaseImpl(
    private val communeRepository: CommuneRepository,
) : GetCommuneUseCase {
    override fun invoke(communeId: UUID): Commune? = communeRepository.findById(communeId)
}
