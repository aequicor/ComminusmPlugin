package ru.kyamshanov.comminusm.application.usecases.workfront

import java.util.UUID

/**
 * Use Case: Deactivate a WorkFront (remove it from the world).
 */
interface DeactivateWorkFrontUseCase {
    operator fun invoke(ownerUuid: UUID)
}
