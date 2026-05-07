package ru.kyamshanov.comminusm.application.usecases.workfront

import ru.kyamshanov.comminusm.domain.repositories.WorkFrontRepository
import ru.kyamshanov.comminusm.infrastructure.adapters.DomainToModelAdapter
import ru.kyamshanov.comminusm.model.WorkFront

class GetWorkFrontsInWorldUseCaseImpl(
    private val workFrontRepository: WorkFrontRepository,
) : GetWorkFrontsInWorldUseCase {
    override fun invoke(world: String): List<WorkFront> =
        workFrontRepository
            .findAllInWorld(world)
            .map { DomainToModelAdapter.toPresentationModel(it) }
}
