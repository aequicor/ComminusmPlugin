package ru.kyamshanov.comminusm.application.usecases.commune

import java.util.UUID

interface RevokeCommuneMemberUseCase {
    operator fun invoke(
        nativeOrderId: Long,
        hostOrderId: Long,
        operationId: UUID,
    )
}
