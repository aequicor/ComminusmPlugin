package ru.kyamshanov.comminusm.application.usecases.commune

import java.util.UUID

interface RemoveOrderFromCommuneUseCase {
    operator fun invoke(
        communeId: UUID,
        orderId: Long,
    )
}
