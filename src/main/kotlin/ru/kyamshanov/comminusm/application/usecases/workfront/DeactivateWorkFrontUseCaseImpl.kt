package ru.kyamshanov.comminusm.application.usecases.workfront

import ru.kyamshanov.comminusm.service.WorkFrontService
import java.util.UUID

class DeactivateWorkFrontUseCaseImpl(
    private val workFrontService: WorkFrontService,
) : DeactivateWorkFrontUseCase {
    override fun invoke(ownerUuid: UUID) {
        workFrontService.deactivate(ownerUuid)
    }
}
