package ru.kyamshanov.comminusm.application.usecases.workfront

import ru.kyamshanov.comminusm.domain.repositories.WorkFrontRepository
import ru.kyamshanov.comminusm.infrastructure.adapters.DomainToModelAdapter
import ru.kyamshanov.comminusm.model.WorkFront
import java.util.UUID

class GetWorkFrontByOwnerUseCaseImpl(
    private val workFrontRepository: WorkFrontRepository,
) : GetWorkFrontByOwnerUseCase {
    override fun invoke(ownerUuid: UUID): WorkFront? {
        val domainWorkFront = workFrontRepository.findByOwner(ownerUuid) ?: return null
        return DomainToModelAdapter.toPresentationModel(domainWorkFront)
    }
}
