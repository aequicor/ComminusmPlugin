package ru.kyamshanov.comminusm.application.usecases.commune

import java.util.UUID

interface BroadcastToCommuneUseCase {
    operator fun invoke(
        communeId: UUID,
        senderUuid: UUID,
        message: String,
    )
}
