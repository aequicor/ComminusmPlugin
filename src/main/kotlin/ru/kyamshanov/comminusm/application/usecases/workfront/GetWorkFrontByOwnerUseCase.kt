package ru.kyamshanov.comminusm.application.usecases.workfront

import ru.kyamshanov.comminusm.model.WorkFront
import java.util.UUID

interface GetWorkFrontByOwnerUseCase {
    operator fun invoke(ownerUuid: UUID): WorkFront?
}
